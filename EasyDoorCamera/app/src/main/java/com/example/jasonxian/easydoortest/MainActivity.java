package com.example.jasonxian.easydoortest;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
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

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String COGNITO_POOL_ID = "us-east-1:a0f4188d-f4cd-45f5-920b-a6a942b4721c";
    private static final String COGNITO_POOL_REGION = "us-east-1";
    private static final String BUCKET_NAME = "picturedatabase";
    private static final String BUCKET_REGION = "us-east-1";
    private static final String PROVIDER_AUTHORITY = "erikterwiel.easydoorcamera.fileprovider";
    private final UUID mUUID = UUID.fromString("19B10010-E8F2-537E-4F6C-D104768A1214");
    private static final int REQUEST_CAMERA = 100;
    private static final int REQUEST_ENABLE_BT = 101;
    private static final float SIMILARITY_THRESHOLD = 70F;

    private TransferUtility mTransferUtility;
    private List<TransferObserver> mObservers;
    private ArrayList<HashMap<String, Object>> mTransferRecordMaps;
    private AmazonRekognitionClient mAmazonRekognitionClient;
    private AWSCredentialsProvider mCredentialsProvider;
    private AmazonS3Client mS3Client;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothSocket mBluetoothSocket;
    private BluetoothGatt mBluetoothGatt;
    private String mCameraPath;
    private int mInputIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Connected to GATT server.");
                    Log.i(TAG, "Attempting to start service discovery:" +
                            mBluetoothGatt.discoverServices());
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "Disconnected from GATT server.");
                }
            }
            @Override
            // New services discovered
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {} else {
                    Log.w(TAG, "onServicesDiscovered received: " + status);
                }
            }
            @Override
            // Result of a characteristic read operation
            public void onCharacteristicRead(BluetoothGatt gatt,
                                             BluetoothGattCharacteristic characteristic,
                                             int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {}
            }
        }
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice("98:4F:EE:0F:38:5F");
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        new ConnectBluetooth().execute();

        mTransferUtility = getTransferUtility(this);
        mTransferRecordMaps = new ArrayList<HashMap<String, Object>>();
        mAmazonRekognitionClient = new AmazonRekognitionClient(mCredentialsProvider);
        mTransferRecordMaps.clear();
        new GetFileListTask().execute();
        launchCamera();
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // This is special handling for the Heart Rate Measurement profile. Data
        // parsing is carried out as per profile specifications.
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            int flag = characteristic.getProperties();
            int format = -1;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d(TAG, "Heart rate format UINT16.");
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d(TAG, "Heart rate format UINT8.");
            }
            final int heartRate = characteristic.getIntValue(format, 1);
            Log.d(TAG, String.format("Received heart rate: %d", heartRate));
            intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" +
                        stringBuilder.toString());
            }
        }
        sendBroadcast(intent);
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
        MainActivity.this.mWrite
        /*
        while (mBluetoothSocket == null) {}
        while (!mBluetoothSocket.isConnected()) {}
        try {
            mBluetoothSocket.getOutputStream().write("0".getBytes());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        */
    }

    private void alertIntruder() {
        Log.i(TAG, "alertIndtruder() called");
        String email = "jasonxixan0@gmail.com";
        AmazonSNSClient snsClient = new AmazonSNSClient(mCredentialsProvider);
        snsClient.setRegion(Region.getRegion(Regions.US_EAST_1));
        String msg = "EasyDoor has detected an unknown individual outside your door.";
        PublishRequest publishRequest = new PublishRequest("arn:aws:sns:us-east-1:953923891640:EasyDoorInfo", msg);
        PublishResult publishResult = snsClient.publish(publishRequest);
        /*
        while (mBluetoothSocket == null) {}
        while (!mBluetoothSocket.isConnected()) {}
        try {
            mBluetoothSocket.getOutputStream().write("1".getBytes());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        */
    }

    private File getFile() {
        File folder = new File("sdcard/Pictures/EasyDoor");
        if (!folder.exists()) folder.mkdir();
        File image = new File(
                folder, "inputCamera.jpg");
        mCameraPath = image.getAbsolutePath();
        return image;
    }

    private class ConnectBluetooth extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... devices) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[] {
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 101);
            try {
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice("98:4F:EE:0F:38:5F");
                mBluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(mUUID);
                mBluetoothSocket.connect();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            return null;
        }
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
