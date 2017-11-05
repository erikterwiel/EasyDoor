package com.example.jasonxian.easydoortest;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;

public class CameraActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        final Intent email = new Intent(Intent.ACTION_SEND_MULTIPLE);
        email.setType("message/rfc822");
        email.putExtra(Intent.EXTRA_EMAIL, new String[]{"jasonxian0@gmail.com"});
        email.putExtra(Intent.EXTRA_SUBJECT, "Test Email");
        email.putExtra(Intent.EXTRA_TEXT, "I really hope this works");
        try{
            startActivity(email);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
