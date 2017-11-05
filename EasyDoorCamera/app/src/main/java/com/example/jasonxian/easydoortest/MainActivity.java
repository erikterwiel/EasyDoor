package com.example.jasonxian.easydoortest;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.rekognition.AmazonRekognitionClient;
import com.amazonaws.services.rekognition.model.BoundingBox;
import com.amazonaws.services.rekognition.model.CompareFacesMatch;
import com.amazonaws.services.rekognition.model.CompareFacesRequest;
import com.amazonaws.services.rekognition.model.CompareFacesResult;
import com.amazonaws.services.rekognition.model.ComparedFace;
import com.amazonaws.services.rekognition.model.DetectLabelsRequest;
import com.amazonaws.services.rekognition.model.DetectLabelsResult;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.Label;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.util.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static android.bluetooth.BluetoothAdapter.STATE_CONNECTED;
import static android.bluetooth.BluetoothAdapter.STATE_DISCONNECTED;
import static java.lang.Thread.sleep;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String COGNITO_POOL_ID = "us-east-1:a0f4188d-f4cd-45f5-920b-a6a942b4721c";
    private static final String COGNITO_POOL_REGION = "us-east-1";
    private static final String BUCKET_NAME = "picturedatabase";
    private static final String BUCKET_REGION = "us-east-1";
    private static final String PROVIDER_AUTHORITY = "erikterwiel.easydoorcamera.fileprovider";
    private final UUID mUUID = UUID.fromString("19B10010-E8F2-537E-4F6C-D104768A1214");
    private final UUID mCharUUID = UUID.fromString("19B10011-E8F2-537E-4F6C-D104768A1214");
    private static final int REQUEST_CAMERA = 100;
    private static final int REQUEST_ENABLE_BT = 101;
    private static final float SIMILARITY_THRESHOLD = 70F;

    private TransferUtility mTransferUtility;
    private List<TransferObserver> mObservers;
    private ArrayList<HashMap<String, Object>> mTransferRecordMaps;
    private AmazonRekognitionClient mAmazonRekognitionClient;
    private AWSCredentialsProvider mCredentialsProvider;
    private AmazonS3Client mS3Client;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGattCallback mGattCallback;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattService Service;
    private BluetoothGattCharacteristic mWriteCharacteristic;
    private BluetoothDevice mDevice;
    private String mCameraPath;
    private int mInputIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupBluetooth();

        mTransferUtility = getTransferUtility(this);
        mTransferRecordMaps = new ArrayList<HashMap<String, Object>>();
        mAmazonRekognitionClient = new AmazonRekognitionClient(mCredentialsProvider);
        mTransferRecordMaps.clear();

        new GetFileListTask().execute();

        launchCamera();
    }

    private void setupBluetooth() {
        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mDevice = mBluetoothAdapter.getRemoteDevice("98:4F:EE:0F:38:5F");
        mGattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                if (newState == STATE_CONNECTED) {
                    Log.i(TAG, "Device connected");
                    try {
                        sleep(600);
                    } catch (Exception ex) {}
                    mBluetoothGatt.discoverServices();
                }
            }
            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                Log.i(TAG, "Service discovered");
                Service = mBluetoothGatt.getService(mUUID);
            }
        };
        mBluetoothGatt = mDevice.connectGatt(this, false, mGattCallback);
        if (mBluetoothGatt == null) Log.i(TAG, " not found");
    }

    private class GetFileListTask extends AsyncTask<Void, Void, Void> {
        private ProgressDialog dialog;
        @Override
        protected void onPreExecute() {
            Log.i(TAG, "GetFileListTask onPreExecute() called");
            dialog = ProgressDialog.show(MainActivity.this,
                    getString(R.string.loading),
                    getString(R.string.please_wait));
        }
        @Override
        protected Void doInBackground(Void... inputs) {
            Log.i(TAG, "GetFileListTask doInBackground() called");
            ObjectListing objectListing = mS3Client.listObjects(BUCKET_NAME);
            List<S3ObjectSummary> s3ObjList = objectListing.getObjectSummaries();
            for (S3ObjectSummary summary : s3ObjList) {
                HashMap<String, Object> map = new HashMap<String, Object>();
                map.put("key", summary.getKey());
                mTransferRecordMaps.add(map);
                Log.i(TAG, "Map added to ArrayList of all keys: " + summary.getKey());
            }
            for (int i = 0; i < mTransferRecordMaps.size(); i++) {
                beginDownload((String) mTransferRecordMaps.get(i).get("key"));
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            dialog.dismiss();
        }
    }

    private void beginDownload(String key) {
        File folder = new File("sdcard/Pictures/EasyDoor");
        File file = new File(folder, "input" + mInputIndex + ".jpg");
        mInputIndex += 1;
        Log.i(TAG, "Downloading: " + key);
        TransferObserver observer = mTransferUtility.download(BUCKET_NAME, key, file);
        observer.setTransferListener(new DownloadListener());
    }

    private void launchCamera() {
        File file = getFile();
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(
                MainActivity.this, PROVIDER_AUTHORITY, file));
        startActivityForResult(cameraIntent, REQUEST_CAMERA);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        new CompareFace().execute();
    }

    private class CompareFace extends AsyncTask<Void, Void, Void> {
        private ProgressDialog dialog;
        @Override
        protected void onPreExecute() {
            dialog = ProgressDialog.show(MainActivity.this,
                    getString(R.string.matching),
                    getString(R.string.please_wait));
        }
        @Override
        protected Void doInBackground(Void... inputs) {
            boolean isFace = false;
            boolean isIntruder = true;
            try {
                InputStream inputStream = new FileInputStream(mCameraPath);
                ByteBuffer imageBytes = ByteBuffer.wrap(IOUtils.toByteArray(inputStream));
                Image targetImage = new Image().withBytes(imageBytes);

                DetectLabelsRequest detectLabelsRequest = new DetectLabelsRequest()
                        .withImage(targetImage)
                        .withMinConfidence(SIMILARITY_THRESHOLD);
                DetectLabelsResult detectLabelsResult =
                        mAmazonRekognitionClient.detectLabels(detectLabelsRequest);
                List<Label> labels = detectLabelsResult.getLabels();

                for (int i = 0; i < labels.size(); i++) {
                    String label = labels.get(i).getName();
                    if (label.equals("People") || label.equals("Person") || label.equals("Human")) isFace = true;
                    Log.i(TAG, labels.get(i).getName() + ":" + labels.get(i).getConfidence().toString());
                }

                if (isFace) {
                    for (int i = 0; i < mInputIndex; i++) {
                        InputStream inputStream2 = new FileInputStream(
                                "sdcard/Pictures/EasyDoor/input" + i + ".jpg");
                        ByteBuffer imageBytes2 = ByteBuffer.wrap(IOUtils.toByteArray(inputStream2));
                        Image sourceImage = new Image().withBytes(imageBytes2);

                        Log.i(TAG, "Attempting to compare faces");
                        CompareFacesRequest compareFaceRequest = new CompareFacesRequest()
                                .withSourceImage(sourceImage)
                                .withTargetImage(targetImage)
                                .withSimilarityThreshold(SIMILARITY_THRESHOLD);

                        CompareFacesResult compareFacesResult =
                                mAmazonRekognitionClient.compareFaces(compareFaceRequest);
                        List<CompareFacesMatch> faceDetails = compareFacesResult.getFaceMatches();
                        for (int j = 0; j < faceDetails.size(); j++) {
                            ComparedFace face = faceDetails.get(j).getFace();
                            BoundingBox position = face.getBoundingBox();
                            Log.i(TAG, "Face at " + position.getLeft().toString()
                                    + " " + position.getTop()
                                    + " matches with " + face.getConfidence().toString()
                                    + "% confidence.");
                            isIntruder = false;
                            break;
                        }
                        if (!isIntruder) {
                            unlockDoor();
                            break;
                        }
                    }
                    if (isIntruder) alertIntruder();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            dialog.dismiss();
        }
    }

    private void unlockDoor() {
        Log.i(TAG, "unlockDoor() called");
        byte[] value = new byte[1];
        value[0] = (byte) 0;
        mWriteCharacteristic = Service.getCharacteristic(mCharUUID);
        mWriteCharacteristic.setValue(value);
        boolean status = mBluetoothGatt.writeCharacteristic(mWriteCharacteristic);
        Log.i(TAG, "Command sent to Arduino: " + status);
    }

    private void alertIntruder() {
        Log.i(TAG, "alertIndtruder() called");

        byte[] value = new byte[1];
        value[0] = (byte) 1;
        mWriteCharacteristic = Service.getCharacteristic(mCharUUID);
        mWriteCharacteristic.setValue(value);
        boolean status = mBluetoothGatt.writeCharacteristic(mWriteCharacteristic);

        AmazonSNSClient snsClient = new AmazonSNSClient(mCredentialsProvider);
        snsClient.setRegion(Region.getRegion(Regions.US_EAST_1));
        String msg = "EasyDoor has detected an unknown individual outside your door.";
        PublishRequest publishRequest = new PublishRequest(
                "arn:aws:sns:us-east-1:953923891640:email-erik", msg);
        PublishResult publishResult = snsClient.publish(publishRequest);
    }

    private File getFile() {
        File folder = new File("sdcard/Pictures/EasyDoor");
        if (!folder.exists()) folder.mkdir();
        File image = new File(
                folder, "inputCamera.jpg");
        mCameraPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mObservers != null && !mObservers.isEmpty()) {
            for (TransferObserver observer : mObservers) {
                observer.cleanTransferListener();
            }
        }
    }

    public TransferUtility getTransferUtility(Context context) {
        TransferUtility sTransferUtility = new TransferUtility(
                getS3Client(context.getApplicationContext()), context.getApplicationContext());
        return sTransferUtility;
    }

    public AmazonS3Client getS3Client(Context context) {
        mS3Client = new AmazonS3Client(getCredProvider(context.getApplicationContext()));
        return mS3Client;
    }

    private AWSCredentialsProvider getCredProvider(Context context) {
        mCredentialsProvider = new CognitoCachingCredentialsProvider(
                context.getApplicationContext(),
                COGNITO_POOL_ID,
                Regions.fromName(COGNITO_POOL_REGION));
        return mCredentialsProvider;
    }

    private class DownloadListener implements TransferListener {
        @Override
        public void onStateChanged(int id, TransferState state) {
            Log.i(TAG, state + "");
        }
        @Override
        public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
            if (bytesTotal != 0) {
                int percentage = (int) (bytesCurrent / bytesTotal * 100);
                Log.i(TAG, Integer.toString(percentage) + "% downloaded");
            }
        }
        @Override
        public void onError(int id, Exception ex) {
            ex.printStackTrace();
            Log.i(TAG, "Error detected");
        }
    }
}
