package erikterwiel.easydoorapp;

import android.content.Context;
import android.content.Intent;
import android.media.Image;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;

import java.io.File;

public class ResidentAddActivity extends AppCompatActivity {

    private static final String TAG = "ResidentAddActivity";
    private static final String COGNITO_POOL_ID = "us-east-1:a0f4188d-f4cd-45f5-920b-a6a942b4721c";
    private static final String COGNITO_POOL_REGION = "us-east-1";
    private static final String BUCKET_NAME = "picturedatabase";
    private static final String BUCKET_REGION = "us-east-1";
    private static final String PROVIDER_AUTHORITY = "erikterwiel.easydoorapp.fileprovider";
    private static final int REQUEST_CAMERA = 100;

    private TransferUtility mTransferUtility;
    private EditText mNameInput;
    private ImageView mAddPhotoButton;
    private int mPictureCount = 0;
    private String mImagePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_add);

        mTransferUtility = getTransferUtility(this);

        mNameInput = (EditText) findViewById(R.id.add_name_input);
        mAddPhotoButton = (ImageView) findViewById(R.id.add_add_photo_button);
        mAddPhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mNameInput.getText().toString().equals("")) {
                    beginCamera();
                } else {
                    Toast.makeText(ResidentAddActivity.this,
                            "Please enter a name for the resident", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        beginUpload(mImagePath);
    }

    private void beginCamera() {
        File file = getFile();
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(
                ResidentAddActivity.this, PROVIDER_AUTHORITY, file));
        startActivityForResult(cameraIntent, REQUEST_CAMERA);
    }

    private void beginUpload(String filePath) {
        File file = new File(filePath);
        TransferObserver observer = mTransferUtility.upload(BUCKET_NAME, file.getName(), file);
        observer.setTransferListener(new UploadListener());
        mPictureCount += 1;
        if (mPictureCount < 5) beginCamera();
    }

    private File getFile() {
        File folder = new File("sdcard/Pictures/EasyDoor");
        if (!folder.exists()) folder.mkdir();
        File image = new File(
                folder, mNameInput.getText() + Integer.toString(mPictureCount) + ".png");
        mImagePath = image.getAbsolutePath();
        return image;
    }

    public static TransferUtility getTransferUtility(Context context) {
        TransferUtility sTransferUtility = new TransferUtility(
                getS3Client(context.getApplicationContext()), context.getApplicationContext());
        return sTransferUtility;
    }

    public static AmazonS3Client getS3Client(Context context) {
        AmazonS3Client sS3Client = new AmazonS3Client(
                getCredProvider(context.getApplicationContext()));
        return sS3Client;
    }

    private static CognitoCachingCredentialsProvider getCredProvider(Context context) {
        CognitoCachingCredentialsProvider sCredProvider = new CognitoCachingCredentialsProvider(
                context.getApplicationContext(),
                COGNITO_POOL_ID,
                Regions.fromName(COGNITO_POOL_REGION));
        return sCredProvider;
    }

    private class UploadListener implements TransferListener {

        @Override
        public void onStateChanged(int id, TransferState state) {
            Log.i(TAG, state + "");
        }

        @Override
        public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
            int percentage = (int) (bytesCurrent / bytesTotal * 100);
            Log.i(TAG, Integer.toString(percentage) + "% uploaded");
        }

        @Override
        public void onError(int id, Exception ex) {
            ex.printStackTrace();
            Log.i(TAG, "Error detected");
        }
    }
}
