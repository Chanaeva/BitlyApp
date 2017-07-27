package com.example.annizhang.bitlyapp;

import android.content.Intent;
import android.graphics.Typeface;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TabHost;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import android.util.Base64;

import static com.example.annizhang.bitlyapp.Constants.DEFAULT_FONT;


public class MainActivity extends AppCompatActivity {

    public static String ACCESSCODE = "com.example.myfirstapp.MESSAGE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ACCESSCODE = "";

        Typeface typeface = Typeface.createFromAsset(this.getAssets(), DEFAULT_FONT);
        TextView myTextView = (TextView) findViewById(R.id.textView2);
        myTextView.setTypeface(typeface);

        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setLogo(R.drawable.ic_action_b_logo_2000);
        getSupportActionBar().setDisplayUseLogoEnabled(true);

    }

    public void login(View view) {
        final Intent intent = new Intent(this, CreateLink.class);

        final EditText name = (EditText)findViewById(R.id.editText1);
        final EditText pass = (EditText)findViewById(R.id.editText2);
        //auto populate for testing
        name.setText("anniblue");
        pass.setText("blueparakeet");
        final String username = name.getText().toString();
        final String password = pass.getText().toString();
        final TextView mTextView = (TextView) findViewById(R.id.textView2);
        mTextView.setVisibility(View.INVISIBLE);
        RequestQueue queue = Volley.newRequestQueue(this);
        String url = "https://api-ssl.bitly.com/oauth/access_token";

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                 new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
//                        mTextView.setText("Response is: " + response);
                        if (response.substring(0,1).equals("{")){
                            mTextView.setVisibility(View.VISIBLE);
                            mTextView.setText("That didn't work! Try again.");
                        } else {
                            mTextView.setVisibility(View.INVISIBLE);
                            String accessCode = response;
                            System.out.println("accesscode is " + accessCode);
                            Constants.ACCESSCODE = accessCode;
                            intent.putExtra(ACCESSCODE, accessCode);
                            startActivity(intent);
                        }
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error){
                        mTextView.setText("That didn't work! Try again.");
                    }
                }){
                    protected Map<String, String> getParams() throws com.android.volley.AuthFailureError {
                        Map<String, String> params = new HashMap<>();
                        params.put("username", username);
                        params.put("password", password);
//                        String loginData = username +  ":" + password;
//                        byte[] data = loginData.getBytes();
//                        String base64 = Base64.encodeToString(data, Base64.DEFAULT);
//                        params.put("Authorization", "Basic " + base64);
                        return params;
                    };

                    public Map<String, String> getHeaders(){
                        Map<String, String> headers = new HashMap<String, String>();
                        String loginData = username +  ":" + password;
                        byte[] data = loginData.getBytes();
                        String base64 = Base64.encodeToString(data, Base64.DEFAULT);
                        headers.put("Authorization", "Basic " + base64);
//                      params.put("username", username);
//                      params.put("password", password);
                        return headers;
                    };
        };
// Add the request to the RequestQueue.
        queue.add(stringRequest);
    }

}
