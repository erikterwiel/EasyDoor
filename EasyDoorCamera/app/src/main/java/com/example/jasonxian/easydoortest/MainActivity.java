package com.example.jasonxian.easydoortest;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.CompareFacesMatch;
import com.amazonaws.services.rekognition.model.CompareFacesRequest;
import com.amazonaws.services.rekognition.model.CompareFacesResult;
import com.amazonaws.services.rekognition.model.DetectLabelsRequest;
import com.amazonaws.services.rekognition.model.DetectLabelsResult;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.Label;
import com.amazonaws.services.rekognition.model.S3Object;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        float similarityThreshold = 70F;
        String sourceImage = "doge.jpg";
        String targetImage = "mansour2.jpg";
        String bucket = "easydoor2";
        int numSuspects = 6;
        boolean foundSuspect = false;
        boolean possibleDanger = false;

        AWSCredentials credentials;
        try {
            credentials = new BasicAWSCredentials("AKIAIDS2JKXNRSWWJG4A",",wR+MzQU3SW7eDKK0s5uodUEvFPP8lE0B8BcDSDGe");
        } catch (Exception e) {
            throw new AmazonClientException("Pleases check for valid credentials", e);
        }

         DetectLabelsRequest labelsRequest = new DetectLabelsRequest()
                .withImage(new Image()
                        .withS3Object(new S3Object()
                                .withName(sourceImage).withBucket(bucket)))
                .withMaxLabels(15)
                .withMinConfidence(50F);

        AmazonRekognition rekognitionClient = AmazonRekognitionClientBuilder
                .standard()
                .withRegion(Regions.US_EAST_1)
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .build();
        try {
            DetectLabelsResult result = rekognitionClient.detectLabels(labelsRequest);
            List<Label> labels = result.getLabels();
            Log.i("info","Detected labels for " + sourceImage);
            for (Label label: labels) {
                Log.i("info",label.getName() + ": " + label.getConfidence().toString());
                if((label.getName().equals("Fork") || label.getName().equals("Gun"))) possibleDanger = true;
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
        Image source = new Image().withS3Object(new S3Object().withBucket(bucket).withName(sourceImage));
        Image target = new Image().withS3Object(new S3Object().withBucket(bucket).withName(targetImage));

        CompareFacesRequest request = new CompareFacesRequest()
                .withSourceImage(source)
                .withTargetImage(target)
                .withSimilarityThreshold(similarityThreshold);
        try{
            CompareFacesResult compareFacesResult = rekognitionClient.compareFaces(request);
            List <CompareFacesMatch> faceDetails = compareFacesResult.getFaceMatches();
            if(!faceDetails.isEmpty()){
                System.out.println("Match, opening door.");
            }else{
                for(int i = 1; i < numSuspects; i++){
                    targetImage = "suspect" + i + ".jpg";
                    target = new Image().withS3Object(new S3Object().withBucket(bucket).withName(targetImage));
                    request = new CompareFacesRequest()
                            .withSourceImage(source)
                            .withTargetImage(target)
                            .withSimilarityThreshold(similarityThreshold);
                    compareFacesResult = rekognitionClient.compareFaces(request);
                    faceDetails = compareFacesResult.getFaceMatches();
                    if(!faceDetails.isEmpty()){
                        foundSuspect = true;
                        break;
                    }
                }
                if(foundSuspect){
                    System.out.println("Calling police...");
                }else if(possibleDanger){
                    System.out.println("Visitor could have dangerous weapons ...");
                }else{
                    System.out.println("Retake photo or nothing is there.");
                }
            }
        }catch(Exception e){
            System.out.println("No face comparsion found, take a new picture.");
        }
    }
}
