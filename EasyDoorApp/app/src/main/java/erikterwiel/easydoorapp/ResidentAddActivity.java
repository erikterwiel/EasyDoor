package erikterwiel.easydoorapp;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.util.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ResidentAddActivity extends AppCompatActivity {

    private static final String TAG = "ResidentAddActivity";
    private static final int REQUEST_IMAGE_CAPTURE = 100;
    private static final int REQUEST_PERMISSIONS = 101;
    private static final String S3_BUCKET = "picturedatabase";

    private AmazonS3Client mS3;
    private TransferUtility mTransferUtility;
    private String mFilePath;
    private int mPictureCount = 0;
    private EditText mNameInput;
    private ImageView mAddPhotoButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Layout initialization
        Log.i(TAG, "onCreate() called");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_add);

        // Connects to AWS Rekognition
        CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                getApplicationContext(),
                "us-east-1:a0f4188d-f4cd-45f5-920b-a6a942b4721c", // Identity pool ID
                Regions.US_EAST_1 // Region
        );
        mS3 = new AmazonS3Client(credentialsProvider);
        mS3.setRegion(Region.getRegion(Regions.US_EAST_1));
        mTransferUtility = new TransferUtility(mS3, this);

        // Links XML attributes to Java
        mNameInput = (EditText) findViewById(R.id.add_name_input);
        mAddPhotoButton = (ImageView) findViewById(R.id.add_add_photo_button);
        mAddPhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePicture();
            }
        });
    }

    private void takePicture() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File file = createFile();
        if (file != null) {
            Log.i(TAG, "File is not null");
            Uri fileUri = FileProvider.getUriForFile(
                    this, "erikterwiel.easydoorapp.fileprovider", file);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        } else Log.i(TAG, "File is null");

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_PERMISSIONS);
            File toSend = new File("sdcard/Pictures/photo_1484877721173.jpg");
            Log.i(TAG, Long.toString(toSend.getTotalSpace()));
            TransferObserver transferObserver = mTransferUtility.upload(
                    S3_BUCKET,
                    toSend.getName(),
                    toSend);
            transferObserver.setTransferListener(new TransferListener() {
                @Override
                public void onStateChanged(int id, TransferState state) {
                    Log.i(TAG, state + "");
                }
                @Override
                public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                    int percentage = (int) (bytesCurrent/bytesTotal * 100);
                    Log.i(TAG, Integer.toString(percentage) + "% uploaded");
                }
                @Override
                public void onError(int id, Exception ex) {
                    ex.printStackTrace();
                    Log.i(TAG, "Error detected");
                }
            });

         //   mPictureCount += 1;
            //  if (mPictureCount != 5) takePicture();
        }
    }

    private File createFile() {
        File folder = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File file = new File(folder, mNameInput.getText() + Integer.toString(mPictureCount));
        mFilePath = file.getPath();
        Log.i(TAG, "File path is: " + mFilePath);
        return file;
    }
}
