package com.example.jasonxian.easydoortest;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
<<<<<<< HEAD
import com.amazonaws.regions.Region;
=======
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
>>>>>>> e8d31467a7a4b5419e046ff8941e7bbd4a564698
import com.amazonaws.regions.Regions;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClient;
import com.amazonaws.services.rekognition.model.Attribute;
import com.amazonaws.services.rekognition.model.CompareFacesMatch;
import com.amazonaws.services.rekognition.model.CompareFacesRequest;
import com.amazonaws.services.rekognition.model.CompareFacesResult;
import com.amazonaws.services.rekognition.model.DetectFacesRequest;
import com.amazonaws.services.rekognition.model.DetectLabelsRequest;
import com.amazonaws.services.rekognition.model.DetectLabelsResult;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.Label;
import com.amazonaws.services.rekognition.model.S3Object;
<<<<<<< HEAD
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.amazonaws.services.sns.model.SubscribeRequest;
=======
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.util.IOUtils;
>>>>>>> e8d31467a7a4b5419e046ff8941e7bbd4a564698

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, new String[] {
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE}, 101);

        mTransferUtility = getTransferUtility(this);
        mAmazonRekognitionClient = new AmazonRekognitionClient(mCredentialsProvider);

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
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
                Image image = new Image().withBytes(imageBytes);
                DetectLabelsRequest detectLabelsRequest = new DetectLabelsRequest()
                        .withImage(image)
                        .withMaxLabels(10)
                        .withMinConfidence(75F);
                DetectLabelsResult detectLabelsResult =
                        mAmazonRekognitionClient.detectLabels(detectLabelsRequest);
                List<Label> labels = detectLabelsResult.getLabels();
                for (int i = 0; i < labels.size(); i++) {
                    Log.i(TAG, labels.get(i).getName() + ":" + labels.get(i).getConfidence().toString());
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

    private void sendEmail(){
        AWSCredentials credentials;
        try {
            credentials = new BasicAWSCredentials("AKIAJTWTGYFXX5WUF3WA","Ve49J6xtSAMFzIJxdKvIv+4J3HAncpY3ljC5RMeo");
        } catch (Exception e) {
            throw new AmazonClientException("Pleases check for valid credentials", e);
        }
        String email = "name@gmail.com";
        AmazonSNSClient snsClient = new AmazonSNSClient(credentials);
        snsClient.setRegion(Region.getRegion(Regions.US_EAST_1));
        SubscribeRequest subRequest = new SubscribeRequest("arn:aws:sns:us-east-1:953923891640:EasyDoorInfo", "email", email);
        snsClient.subscribe(subRequest);
        System.out.println("SubscribeRequest - " + snsClient.getCachedResponseMetadata(subRequest));
        System.out.println("Check your email and confirm subscription.");
        String msg = "My text published to SNS topic with email endpoint";
        PublishRequest publishRequest = new PublishRequest("arn:aws:sns:us-east-1:953923891640:EasyDoorInfo", msg);
        PublishResult publishResult = snsClient.publish(publishRequest);
        System.out.println("MessageId - " + publishResult.getMessageId());
    }
}
