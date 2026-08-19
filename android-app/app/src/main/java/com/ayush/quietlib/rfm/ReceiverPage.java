package com.ayush.quietlib.rfm;

import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ayush.quietlib.R;
import com.ayush.quietlib.rfm.controller.RadioController;
import com.ayush.quietlib.rfm.controller.RadioState;
import com.ayush.quietlib.rfm.controller.RadioStateUpdater;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class ReceiverPage extends AppCompatActivity implements RadioStateUpdater.TunerStateListener {

    private Button startReceiver, restartReceiver, clearCache, changeFrequency;
    private LinearLayout receivedImage;
    private EditText freqEditor;
    private TextView currentStatus;
    private RelativeLayout buttonLayout;

    private RadioController mRadioController;

    private enum ResponseState {
        NONE,
        TESTING,
        METADATA,
        DATA,
        DONE
    }

    private ResponseState resState;

    private StringBuilder metadata;
    private byte[][] images;

    private int numImgParts;
    private int totalPayloadLength, framePayloadLength;
    private int receivedPayloadLength, receivedFrameCount, receivedImageSize;

    private boolean setWhite = true;
    private boolean radioStarted = false;

    // constants
    private final int frameIndexSize = 5;
    private final int partitionIndexSize = 3;
    private final int frameLength = 100;
    private final int partitionSize = 35;
    private final double initialFrequency = 98.6;
    private int imgWidth = 675;
    private final int imgHeight = 1200;

    private BroadcastReceiver byteArrayReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // avoid crash
            try {
                if (intent != null && intent.getAction().equals("com.ayush.quietlib.BROADCAST_RESPONSE")) {
                    byte[] byteArray = intent.getByteArrayExtra("response");
                    int byteArrayLen = intent.getIntExtra("responseLength", 69);


                    if (resState == ResponseState.NONE) {
                        try {
                            String byteStr = new String(byteArray);
                            if (byteStr.contains("C137")) {
                                resState = ResponseState.TESTING;
                            } else if (byteStr.contains("img") && resState != ResponseState.DONE) {
                                resState = ResponseState.METADATA;
                                metadata.setLength(0);
                            } else if (byteStr.contains("data") && resState != ResponseState.DONE) {
                                resState = ResponseState.DATA;
                                receivedPayloadLength = 0;

                                return;
                            }
                        } catch (Exception e) {

                        }
                    }

                    if (resState == ResponseState.TESTING) {
                        currentStatus.setText("TEST SUCCESSFUL");
                        resState = ResponseState.NONE;
                    }

                    if (resState == ResponseState.METADATA) {
                        currentStatus.setText("Receiving Metadata...");

                        metadata.append(new String(byteArray));

                        // keep reading metadata
                        if (!metadata.toString().contains("eom")) {
                            return;
                        }

                        framePayloadLength = frameLength - frameIndexSize - partitionIndexSize;

                        int noOfFramesRequired = 0;
                        totalPayloadLength = 0;

                        String[] partitionPayloadSizes;
                        String contentType;

                        try {
                            contentType = metadata.substring(0, 3);

                            String s = metadata.substring(3, metadata.indexOf("eom"));
                            partitionPayloadSizes = s.split(",");
                            numImgParts = partitionPayloadSizes.length;

                            for (String partitionPayloadSize : partitionPayloadSizes) {
                                int size = Integer.parseInt(partitionPayloadSize);
                                int framesRequired = (int) Math.ceil((double) size / framePayloadLength);
                                noOfFramesRequired += framesRequired;

                                totalPayloadLength += size + framesRequired * (frameIndexSize + partitionIndexSize);
                            }
                        } catch (NumberFormatException e) {
                            Log.d("NumberFormatException", e.toString());
                            return;
                        }

                        // images array with all partitions
                        // limit is 64k
                        images = new byte[numImgParts][];

                        for (int i = 0; i < numImgParts; i++) {
                            images[i] = new byte[Integer.parseInt(partitionPayloadSizes[i])];
                        }

                        resState = ResponseState.NONE;
                    }

                    if (resState == ResponseState.DATA) {
                        currentStatus.setText("Receiving Data...");

                        if (new String(byteArray).contains("PEACE")) {
                            resState = ResponseState.DONE;
                            currentStatus.setText("Data Received!");
                            addLinks();

                            return;
                        }

                        int frameIndex = Integer.parseInt(new String(Arrays.copyOfRange(byteArray, 0, frameIndexSize)));
                        int partitionIndex = Integer.parseInt(new String(Arrays.copyOfRange(byteArray, frameIndexSize, frameIndexSize + partitionIndexSize)));

                        int ln = byteArrayLen;
                        byte[] content = new byte[ln - frameIndexSize - partitionIndexSize];
                        System.arraycopy(byteArray, frameIndexSize + partitionIndexSize, content, 0, content.length);

                        receivedFrameCount++;

                        System.arraycopy(content, 0, images[partitionIndex], frameIndex * framePayloadLength, content.length);

                        receivedImageSize += ln - (frameIndexSize + partitionIndexSize);

                        receivedPayloadLength += ln;

                        if (receivedPayloadLength >= totalPayloadLength) {
                            resState = ResponseState.DONE;
                            currentStatus.setText("Data Received.");
                            addLinks();
                        }

                        renderImages();
                    }
                }
            } catch (Exception e) {
                Log.d("ERROR", e.toString());
            }

        }
    };

    private void renderImages() {
        if (setWhite) {
            receivedImage.removeAllViews();

            for (int i = 0; i < numImgParts; i++) {
                ImageView img = new ImageView(this);
                img.setLayoutParams(new LinearLayout.LayoutParams((int) (imgWidth / partitionSize), imgHeight));
                img.setBackgroundColor(Color.WHITE);
                receivedImage.addView(img);
            }

            setWhite = false;
        }

        int i = 0;
        for (byte[] image : images) {
            if (image != null && image.length > 0) {
                ImageView img = new ImageView(this);
                img.setScaleType(ImageView.ScaleType.FIT_XY);
                Bitmap bmp = BitmapFactory.decodeByteArray(image, 0, image.length);

                if (bmp != null) {
                    img.setImageBitmap(bmp);
                    img.setLayoutParams(new LinearLayout.LayoutParams((int) (imgWidth / partitionSize), imgHeight));
                    receivedImage.removeViewAt(i);
                    receivedImage.addView(img, i);
                }
            }
            i++;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(byteArrayReceiver);

        Log.d("ACTIVITY_LOG", "onDestroy was called on ReceiverPage");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiver_page);

        // imgWidth to screenWidth
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        imgWidth = screenWidth;

        // Broadcast Receiver Setup
        IntentFilter filter = new IntentFilter("com.ayush.quietlib.BROADCAST_RESPONSE");
        registerReceiver(byteArrayReceiver, filter);

        // Response Status Setup
        resState = ResponseState.NONE;

        // Buttons
        startReceiver = findViewById(R.id.startRadioButton);
        startReceiver.setOnClickListener(view -> {
            if (radioStarted) {
                mRadioController.disable();
                mRadioController.kill();
            }

            mRadioController = new RadioController(this);
            mRadioController.requestForCurrentState(this);
            mRadioController.registerForUpdates(this);
            radioStarted = true;
        });

        restartReceiver = findViewById(R.id.restartRadioButton);
        restartReceiver.setOnClickListener(view -> {
            mRadioController.disable();
            mRadioController.kill();
        });

        clearCache = findViewById(R.id.clearCacheButton);
        clearCache.setOnClickListener(view -> {
            resState = ResponseState.NONE;
            metadata.setLength(0);
            images = null;
            setWhite = true;
            receivedImage.removeAllViews();
            currentStatus.setText("IDLE");
        });

        changeFrequency = findViewById(R.id.changeFrequencyButton);
        changeFrequency.setOnClickListener(view -> {
            int freq = (int) (Float.parseFloat(freqEditor.getText().toString()) * 1000);
            if (radioStarted) mRadioController.setFrequency(freq);
            Toast.makeText(this, "Frequency changed", Toast.LENGTH_LONG).show();
        });

        // ImageLayout
        receivedImage = findViewById(R.id.radioReceivedImageLayout);

        // EditText
        freqEditor = findViewById(R.id.frequencyEditor);
        freqEditor.setText(initialFrequency + "");

        // TextView
        currentStatus = findViewById(R.id.responseStatus);

        // RelativeLayout
        buttonLayout = findViewById(R.id.buttonLayout);

        // Utils
        metadata = new StringBuilder();
    }

    @Override
    public void onStateUpdated(RadioState state, int mode) {
        if ((mode & RadioStateUpdater.SET_STATUS) > 0) {
            switch (state.getStatus()) {
                case IDLE:
                    mRadioController.setup();
                    break;
                case ENABLED:
                    mRadioController.setFrequency((int) (Float.parseFloat(freqEditor.getText().toString()) * 1000));
                    break;
            }
        }
    }

    private JSONObject loadJSONFromAsset(Context context, String fileName) {
        String json;
        try {
            InputStream is = context.getAssets().open(fileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
            return new JSONObject(json);
        } catch (IOException | JSONException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private void addLinks() throws JSONException {
        buttonLayout.removeAllViews();
        JSONObject links = loadJSONFromAsset(this, "links.json");
        JSONArray homepage = links.getJSONArray("homepage");

        for (int i = 0; i < homepage.length(); i++) {
            FrameLayout button = new FrameLayout(this);
            JSONObject link = homepage.getJSONObject(i);

            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(link.getInt("w"), link.getInt("h"));
            params.leftMargin = link.getInt("x");
            params.topMargin = link.getInt("y");

            button.setLayoutParams(params);
            button.setOnClickListener(view -> {
                try {
                    Intent intent = new Intent(this, SubPage.class);
                    intent.putExtra("id", link.getString("id"));
                    startActivity(intent);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            });
            buttonLayout.addView(button);
        }
    }
}