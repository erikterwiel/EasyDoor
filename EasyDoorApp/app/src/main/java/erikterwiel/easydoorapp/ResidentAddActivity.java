package erikterwiel.easydoorapp;

import android.content.Context;
import android.media.Image;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
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

    private TransferUtility mTransferUtility;
    private EditText mNameInput;
    private ImageView mAddPhotoButton;

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
                beginUpload("/sdcard/Pictures/photo_1484877721173.jpg");
            }
        });
    }

    private void beginUpload(String filePath) {
        File file = new File(filePath);
        TransferObserver observer = mTransferUtility.upload(BUCKET_NAME, file.getName(), file);
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
}
