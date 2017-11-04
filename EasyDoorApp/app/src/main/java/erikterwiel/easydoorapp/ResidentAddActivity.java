package erikterwiel.easydoorapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import java.util.ArrayList;

public class ResidentAddActivity extends AppCompatActivity {

    private static final String TAG = "ResidentAddActivity.class";
    private static final int REQUEST_IMAGE_CAPTURE = 100;

    private ArrayList<Bitmap> mBitmaps = new ArrayList<>();
    private ImageView mAddPhotoButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        Log.i(TAG, "onCreate() called");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resident_add);

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
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = intent.getExtras();
            mBitmaps.add((Bitmap) extras.get("data"));
            if (mBitmaps.size() != 5) {
                takePicture();
            }
        }
    }



}
