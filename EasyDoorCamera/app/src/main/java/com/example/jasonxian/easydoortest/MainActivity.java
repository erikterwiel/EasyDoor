package com.example.jasonxian.easydoortest;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ImageView;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
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
import com.amazonaws.util.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String COGNITO_POOL_ID = "us-east-1:a0f4188d-f4cd-45f5-920b-a6a942b4721c";
    private static final String COGNITO_POOL_REGION = "us-east-1";
    private static final String BUCKET_NAME = "picturedatabase";
    private static final String BUCKET_REGION = "us-east-1";
    private static final String PROVIDER_AUTHORITY = "erikterwiel.easydoorcamera.fileprovider";
    private static final int REQUEST_CAMERA = 100;
    private static final float SIMILARITY_THRESHOLD = 70F;

    private TransferUtility mTransferUtility;
    private AmazonRekognitionClient mAmazonRekognitionClient;
    private AWSCredentialsProvider mCredentialsProvider;
    private String mImagePath;
    private String mImagePathDownload;
    private ImageView mImageView1;
    private ImageView mImageView2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mImageView1 = (ImageView) findViewById(R.id.image1);
        mImageView2 = (ImageView) findViewById(R.id.image2);

        ActivityCompat.requestPermissions(this, new String[] {
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE}, 101);

        mTransferUtility = getTransferUtility(this);
        mAmazonRekognitionClient = new AmazonRekognitionClient(mCredentialsProvider);

        File file = getFileDownload();
        TransferObserver observer = mTransferUtility.download(
                BUCKET_NAME,
                "faustin_adiceam.png",
                file);
        observer.setTransferListener(new TransferListener() {
            @Override
            public void onStateChanged(int id, TransferState state) {
                Log.i(TAG, state + "");
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                int percentage = (int) (bytesCurrent / bytesTotal * 100);
                Log.i(TAG, Integer.toString(percentage) + "% downloaded");
            }

            @Override
            public void onError(int id, Exception ex) {
                ex.printStackTrace();
                Log.i(TAG, "Error detected");
            }
        });
        Log.i(TAG, file.getAbsolutePath());

        launchCamera();
    }

    private void launchCamera() {
        File file = getFile();
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(
                MainActivity.this, PROVIDER_AUTHORITY, file));
        startActivityForResult(cameraIntent, REQUEST_CAMERA);
    }

    private File getFile() {
        File folder = new File("sdcard/Pictures/EasyDoor");
        if (!folder.exists()) folder.mkdir();
        File image = new File(
                folder, "input.jpg");
        mImagePath = image.getAbsolutePath();
        return image;
    }

    private File getFileDownload() {
        File folder = new File("sdcard/Pictures/EasyDoor/Download");
        if (!folder.exists()) folder.mkdir();
        File image = new File(
                folder, "input.jpg");
        mImagePathDownload = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mImageView1.setImageDrawable(Drawable.createFromPath(mImagePath));
        mImageView2.setImageDrawable(Drawable.createFromPath(mImagePathDownload));
        new CompareFace().execute();
    }

    public TransferUtility getTransferUtility(Context context) {
        TransferUtility sTransferUtility = new TransferUtility(
                getS3Client(context.getApplicationContext()), context.getApplicationContext());
        return sTransferUtility;
    }

    public AmazonS3Client getS3Client(Context context) {
        AmazonS3Client sS3Client = new AmazonS3Client(getCredProvider(context.getApplicationContext()));
        return sS3Client;
    }

    private AWSCredentialsProvider getCredProvider(Context context) {
        mCredentialsProvider = new CognitoCachingCredentialsProvider(
                context.getApplicationContext(),
                COGNITO_POOL_ID,
                Regions.fromName(COGNITO_POOL_REGION));
        return mCredentialsProvider;
    }

    private class CompareFace extends AsyncTask<Void, Void, Void> {
        private ProgressDialog dialog;

        @Override
        protected void onPreExecute() {
            dialog = ProgressDialog.show(MainActivity.this,
                    getString(R.string.refreshing),
                    getString(R.string.please_wait));
        }

        @Override
        protected Void doInBackground(Void... inputs) {
            try {
                InputStream inputStream = new FileInputStream(mImagePath);
                ByteBuffer imageBytes = ByteBuffer.wrap(IOUtils.toByteArray(inputStream));
                Image targetImage = new Image().withBytes(imageBytes);

                InputStream inputStream2 = new FileInputStream(mImagePathDownload);
                ByteBuffer imageBytes2 = ByteBuffer.wrap(IOUtils.toByteArray(inputStream2));
                Image sourceImage = new Image().withBytes(imageBytes2);

                DetectLabelsRequest detectLabelsRequest = new DetectLabelsRequest()
                        .withImage(targetImage)
                        .withMaxLabels(10)
                        .withMinConfidence(75F);
                DetectLabelsResult detectLabelsResult =
                        mAmazonRekognitionClient.detectLabels(detectLabelsRequest);
                List<Label> labels = detectLabelsResult.getLabels();
                for (int i = 0; i < labels.size(); i++) {
                    Log.i(TAG, labels.get(i).getName() + ":" + labels.get(i).getConfidence().toString());
                }

                Log.i(TAG, "Attempting to compare faces");
                CompareFacesRequest compareFaceRequest = new CompareFacesRequest()
                        .withSourceImage(sourceImage)
                        .withTargetImage(targetImage)
                        .withSimilarityThreshold(SIMILARITY_THRESHOLD);

                CompareFacesResult compareFacesResult =
                        mAmazonRekognitionClient.compareFaces(compareFaceRequest);
                List<CompareFacesMatch> faceDetails = compareFacesResult.getFaceMatches();
                for (int i = 0; i < faceDetails.size(); i++) {
                    ComparedFace face = faceDetails.get(i).getFace();
                    BoundingBox position = face.getBoundingBox();
                    Log.i(TAG, "Face at " + position.getLeft().toString()
                            + " " + position.getTop()
                            + " matches with " + face.getConfidence().toString()
                            + "% confidence.");
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
}
