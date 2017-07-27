package com.example.annizhang.bitlyapp;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.SyncStateContract;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.FileProvider;
import android.util.Base64;

import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.bumptech.glide.Glide;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import android.util.Log;
import android.util.StringBuilderPrinter;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Region;
import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;

import org.apache.http.ExceptionLogger;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

import static com.example.annizhang.bitlyapp.Constants.DEFAULT_FONT;


/** ScanLink uses the device's camera app to take a picture,
    then that picture is saved to a file. The file is sent to
 MS Azure to parse the image for text. That text is then returned
 and parsed to get the URL. */
@TargetApi(Build.VERSION_CODES.N)
public class ScanLink extends Activity {
    private String imageText; //
    private String linkText;
    private String mCurrentPhotoPath; //send to MS Azure
    private static Context ctx;
    private static File imageFile;
    private static String possible_url;
    private static String scanned_link;

    static final int REQUEST_IMAGE_CAPTURE = 1;
    static final int REQUEST_TAKE_PHOTO = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_link);
        try {
            ctx = this.getApplicationContext();
            dispatchTakePictureIntent();
        }
        catch (IOException e){
            System.out.println("Error opening camera");
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();

        System.out.println("CURRENT PATH IN CREATED IMAGE IS: " + mCurrentPhotoPath);

        return image;
    }

    private void getTextFromStringWrapper() {
        try {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try  {
                        possible_url = getTextFromImage(imageFile.getName());
                        if(possible_url != "") {
                            if(!possible_url.contains("http://") && !possible_url.contains("https://")) {
                                scanned_link = "http://" + possible_url.toLowerCase();
                            } else {
                                scanned_link = possible_url;
                            }
                        }else {
                            scanned_link = "whoops, no url found in image";
                        }
                        Intent myIntent = new Intent(ScanLink.this, CreateLink.class);
                        myIntent.putExtra("scanned_link", scanned_link); //Optional parameters
                        ScanLink.this.startActivity(myIntent);
                    } catch (Exception e) {
                        System.out.println("ERROR IN THREAD: " + e);
                        e.printStackTrace();
                    }
                }
            });
            thread.start();

        }
        catch (Exception e) {
            System.out.println("Error calling getTextFromImage");
        }
    }

    // UPLOAD FILE TO S3
    private void uploadToS3Bucket(File image) {
        try {
            // Initialize the Amazon Cognito credentials provider
            CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                    ctx,
                    Constants.COGNITO_POOL_ID, // Identity pool ID
                    Constants.COGNITO_POOL_REGION // Region
            );

            // Create an S3 client
            AmazonS3 s3 = new AmazonS3Client(credentialsProvider);

            TransferUtility transferUtility = new TransferUtility(s3, ctx);

            String file_name = image.getName();

            System.out.println("IMAGE NAME: " + image.getName());

            final TransferObserver observer = transferUtility.upload(
                    Constants.BUCKET_NAME,     /* The bucket to upload to */
                    "readonly/" + file_name,    /* The key for the uploaded object */
                    image       /* The file where the data to upload exists */
            );

            observer.setTransferListener(new TransferListener(){
                @Override
                public void onStateChanged(int id, TransferState state) {

                    if (state.COMPLETED.equals(observer.getState())) {
                        System.out.println("UPLOAD COMPLETE");
                        getTextFromStringWrapper();
                    }
                }
                @Override
                public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                    long _bytesCurrent = bytesCurrent;
                    long _bytesTotal = bytesTotal;

                    Log.d("current bytes: ","" +_bytesCurrent);
                    Log.d("total bytes: ","" +_bytesTotal);

                    float percentage =  ((float)_bytesCurrent /(float)_bytesTotal * 100);
                    Log.d("percentage completed","" +percentage);
                }

                @Override
                public void onError(int id, Exception ex) {
                    System.out.println("ERROR uploading file");
                }
            });
        }
        catch (Exception e){
            System.out.println("Error uploading to s3 bucket: " + e);
        }
    }

    public static boolean isJSONValid(String test) {
        try {
            new JSONObject(test);
        } catch (JSONException ex) {
                return false;
        }
        return true;
    }

    // call ms azure api & get back image text
    private String getTextFromImage(String fileName) throws IOException, JSONException {
        String api_endpoint = getString(R.string.api_endpoint);
        final String url_parameters = "?language=unk&detectOrientation=true";
        final String url = api_endpoint + url_parameters;

        String aws_file_name = Constants.BUCKET_LOCATION + fileName;


        String json = String.format("{\"url\":\"%s\"}", aws_file_name);

        if(!isJSONValid(json)){
            System.out.println("JSON NOT VALID!");
        }

        HttpsURLConnection connection = null;
        try {
            URL u = new URL(url);
            connection = (HttpsURLConnection) u.openConnection();
            connection.setRequestMethod("POST");

            //set the sending type and receiving type to json
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Ocp-Apim-Subscription-Key", getString(R.string.subscription_key));


            connection.setAllowUserInteraction(false);

            if (json != null) {
                //start spinning

                //set the content length of the body
                connection.setDoInput(true);
                connection.setDoOutput(true);
                connection.setUseCaches(false);

                //send the json as body of the request
                OutputStream outputStream = connection.getOutputStream();
                outputStream.write(json.getBytes("UTF-8"));
                outputStream.close();
            }

            //Connect to the server
            connection.connect();

            int status = connection.getResponseCode();
            Log.i("HTTP Client", "MS HTTP status code : " + status);
            Log.i("HTTP Client", "MS HTTP status message : " + connection.getResponseMessage());
            switch (status) {
                case 200:
                case 201:
                    try{
                        JsonReader reader = new JsonReader(new InputStreamReader(connection.getInputStream()));
                        Gson gson = new GsonBuilder().create();
                        reader.beginObject();
                        while(reader.hasNext()){
                            String name = reader.nextName();
                            if (name.equals("regions")){
                                reader.beginArray();
                                while(reader.hasNext()){
                                    final MSRegion region = gson.fromJson(reader, MSRegion.class);
                                    String foundLink = searchForLink(region);
                                    Log.i("Found Link", "Link is: " + foundLink);
                                    if (foundLink != ""){
                                        return foundLink;
                                    }
                                }
                                reader.endArray();
                            } else {
                                reader.skipValue();
                            }
                        }
                        reader.endObject();
                        reader.close();
                    } catch (UnsupportedEncodingException ex){
                        System.out.println("unsupported encoding exception");
                    } catch (IOException ex){
                        System.out.println("io exception");
                    }
            }
        } catch (MalformedURLException ex) {
            Log.e("1 HTTP Client", "Error in http connection" + ex.toString());
        } catch (IOException ex) {
            Log.e("2 HTTP Client", "Error in http connection" + ex.toString());
        } catch (Exception ex) {
            Log.e("3 HTTP Client", "Error in http connection" + ex.toString());
        } finally {
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Exception ex) {
                    Log.e("4 HTTP Client", "Error in http connection" + ex.toString());
                }
            }
        }
        return "";
    }

    //takes in REGIONS
    private static String searchForLink(MSRegion region){
        List<Line> lines = region.getLines();
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            List<Word> words = line.getWords();
            for (int j = 0; j < words.size(); j++) {
                Word word = words.get(j);
                String text = word.toString().toLowerCase();
                Pattern r = Pattern.compile(Constants.URL_REGEXP);
                Matcher m = r.matcher(text);
                if(m.find( )){
                    System.out.println("FOUND VALUE IN REGEX CHECK: " + m.group(0));
                    return text;
                }else if (text.contains("www") || text.contains(".com") || text.contains("http") || text.contains("https")) {
                    System.out.println("FOUND LINK IN NAIVE CHECK! " + text);
                    return text;
                }
            }
        }
        return "";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            uploadToS3Bucket(imageFile); // Once the file is uploaded, this will make the call to MS Azure
        }
    }

    public void dispatchTakePictureIntent() throws IOException{
        setContentView(R.layout.activity_scan_progress);
        ImageView p = (ImageView) findViewById(R.id.parrot);
        Typeface typeface = Typeface.createFromAsset(this.getAssets(), DEFAULT_FONT);
        TextView myTextView = (TextView) findViewById(R.id.progress_text);
        myTextView.setTypeface(typeface);
        Glide.with(this)
                .load("https://railsgirlssummerofcode.org/img/blog/2016/l1ghtsab3r-partyparrot.gif")
                .into(p);
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            //File photoFile = null;
            try {
                imageFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
            }
            // Continue only if the File was successfully created
            if (imageFile.exists()) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.android.fileprovider",
                        imageFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }
}


