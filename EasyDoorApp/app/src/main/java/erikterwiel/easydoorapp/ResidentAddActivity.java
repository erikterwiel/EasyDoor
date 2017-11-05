package erikterwiel.easydoorapp;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import com.amazonaws.services.s3.model.S3ObjectSummary;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ResidentAddActivity extends AppCompatActivity {

    private static final String TAG = "ResidentAddActivity";
    private static final String COGNITO_POOL_ID = "us-east-1:a0f4188d-f4cd-45f5-920b-a6a942b4721c";
    private static final String COGNITO_POOL_REGION = "us-east-1";
    private static final String BUCKET_NAME = "picturedatabase";
    private static final String BUCKET_REGION = "us-east-1";
    private static final String PROVIDER_AUTHORITY = "erikterwiel.easydoorapp.fileprovider";
    private static final int REQUEST_CAMERA = 100;

    private TransferUtility mTransferUtility;
    private AmazonS3Client mS3Client;
    private ArrayList<Drawable> mImageList = new ArrayList<Drawable>();
    private EditText mNameInput;
    private Button mAddPhotoButton;
    private RecyclerView mPictureView;
    private PictureAdapter mPictureAdapter;
    private MenuItem mDoneButton;
    private int mPictureCount = 0;
    private String mImagePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_add);

        mTransferUtility = getTransferUtility(this);

        mNameInput = (EditText) findViewById(R.id.add_name_input);
        mAddPhotoButton = (Button) findViewById(R.id.add_add_photo_button);
        mPictureView = (RecyclerView) findViewById(R.id.add_picture_view);

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
        mPictureView.setLayoutManager(new LinearLayoutManager(
                this, LinearLayoutManager.HORIZONTAL, false));
        mPictureAdapter = new PictureAdapter(mImageList);
        mPictureView.setAdapter(mPictureAdapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_resident_done, menu);

        mDoneButton = menu.findItem(R.id.done_button);
        mDoneButton.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                finish();
                return false;
            }
        });

        return super.onCreateOptionsMenu(menu);
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
        mImageList.add(Drawable.createFromPath(filePath));
        mPictureAdapter.notifyItemInserted(mImageList.size());
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
                folder, mNameInput.getText() + Integer.toString(mPictureCount) + ".jpg");
        mImagePath = image.getAbsolutePath();
        return image;
    }

    public TransferUtility getTransferUtility(Context context) {
        mS3Client = getS3Client(context.getApplicationContext());
        TransferUtility sTransferUtility = new TransferUtility(
                mS3Client, context.getApplicationContext());
        return sTransferUtility;
    }

    public static AmazonS3Client getS3Client(Context context) {
        AmazonS3Client sS3Client = new AmazonS3Client(getCredProvider(context.getApplicationContext()));
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

    private class PictureAdapter extends RecyclerView.Adapter<PictureHolder> {
        private ArrayList<Drawable> imageList;

        public PictureAdapter(ArrayList<Drawable> incomingList) {
            imageList = incomingList;
        }

        @Override
        public PictureHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater layoutInflater = LayoutInflater.from(ResidentAddActivity.this);
            View view = layoutInflater.inflate(R.layout.item_picture, parent, false);
            return new ResidentAddActivity.PictureHolder(view);
        }

        @Override
        public void onBindViewHolder(PictureHolder holder, int position) {
            Drawable drawable = imageList.get(position);
            holder.bindPicture(drawable);
        }

        @Override
        public int getItemCount() {
            return imageList.size();
        }

        public void itemAdded(int position) {
            notifyItemInserted(position);
        }
    }

    private class PictureHolder extends RecyclerView.ViewHolder {
        private ImageView mPicture;

        public PictureHolder(View itemView) {
            super(itemView);
            mPicture = (ImageView) itemView.findViewById(R.id.item_picture);
        }

        public void bindPicture(Drawable drawable) {
            mPicture.setImageDrawable(drawable);
        }
    }
}
