package erikterwiel.easydoorapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
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
    private static final String COLLECTION_ID = "collectionID";
    private static final String S3_BUCKET = "easydoor2";

    private AmazonS3 mS3;
    private TransferUtility mTransferUtility;
    private TransferListener mTransferListener;
    private String mFileName;
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
        CognitoCachingCredentialsProvider credentials = new CognitoCachingCredentialsProvider(
                getApplicationContext(),
                "us-east-1:a6f60d91-90d3-429f-a625-50ef21ad7ebb",
                Regions.US_EAST_1);
        mS3 = new AmazonS3Client(credentials);
        mS3.setRegion(Region.getRegion(Regions.US_EAST_1));
        mTransferUtility = new TransferUtility(mS3, getApplicationContext());

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
        File file = null;
        try {
            file = createFile();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
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

            TransferObserver transferObserver =
                    mTransferUtility.upload(S3_BUCKET, mFileName, new File(mFilePath));
            transferObserver.setTransferListener(mTransferListener);

            mPictureCount += 1;
            if (mPictureCount != 5) takePicture();
        }
    }

    private File createFile() throws IOException {
        String timeStamp =
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CANADA).format(new Date());
        String fileName =
                mNameInput.getText() + "_" + Integer.toString(mPictureCount) + "_" + timeStamp;
        File tempStorage = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File file = File.createTempFile(fileName, ".jpg", tempStorage);
        mFileName = fileName;
        mFilePath = file.getAbsolutePath();
        Log.i(TAG, mFileName + " ||| " + mFilePath);
        return file;
    }
}
