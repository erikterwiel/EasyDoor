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
/*
    private void sendEmail(){
        AWSCredentials credentials;
        try {
            credentials = new BasicAWSCredentials("public key","private key");
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
*/
}
