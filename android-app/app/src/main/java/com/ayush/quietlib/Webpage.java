package com.ayush.quietlib;

import static java.security.AccessController.getContext;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.opengl.Visibility;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.ayush.quietlib.rfm.C;
import com.ayush.quietlib.rfm.SubPage;
import com.google.gson.Gson;

import org.apache.http.Consts;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class Webpage extends AppCompatActivity {

    private byte[][] images;
    private DatabaseHelper dbHelper;
    private String filename1, filename2;
    private String[] chatResponses;
    private Gson linksJSON;
    private String checksum;
    private Metadata metadata;
    private LinearLayout imgLayout, queryResult;
    private TextView queryTitle, queryText;
    private boolean setWhite = true;

    private int viewportWidth = 0;
    private RelativeLayout buttonLayout;

    String receiverPhone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webpage);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        viewportWidth = displayMetrics.widthPixels;

        dbHelper = new DatabaseHelper(this);
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            this.checksum = extras.getString("checksum");
        }

        imgLayout = (LinearLayout) findViewById(R.id.imageLayout);
        buttonLayout = (RelativeLayout) findViewById(R.id.buttonLayout);
        queryResult = (LinearLayout) findViewById(R.id.queryResult);
        queryTitle = (TextView) findViewById(R.id.queryTitle);
        queryText = (TextView) findViewById(R.id.queryText);

        if (checksum != null) {
            linksJSON = new Gson();

            List<Map<String, String>> rows = dbHelper.getAllMap("transmissions", "checksum = '" + checksum + "'");
            int total = rows.size();

            filename1 = checksum + (total + 1) + ".sonic";
            filename2 = checksum + total + ".sonic";

            Map<String, String> row = dbHelper.getMapById("metadata", "checksum", checksum);
            this.metadata = new Metadata(row.get("type"), row.get("url"), row.get("checksum"), row.get("partitionSizesCSV"), Integer.parseInt(row.get("width")), Integer.parseInt(row.get("height")));

            if (this.metadata.type.equals("img")) {
                this.initImages();
                this.renderImages();
                this.renderLinks();
            } else if (this.metadata.type.equals("gpt")) {
                this.readGPTResponse();

                // index of the one which has highest length in chatResponses
                int maxIndex = 0;
                for (int i = 1; i < chatResponses.length; i++) {
                    if (chatResponses[i].length() > chatResponses[maxIndex].length()) {
                        maxIndex = i;
                    }
                }

                // chatResponses[maxIndex] is the longest response
                imgLayout.setVisibility(View.GONE);
                queryResult.setVisibility(View.VISIBLE);

                String text = chatResponses[maxIndex];
                queryText.setText(text.trim());
                queryTitle.setText(metadata.url);
            }
        } else {
            Toast.makeText(this, "Not received", Toast.LENGTH_LONG).show();
        }
    }

    private void renderLinks() {
        String[] jsonFilenames = new String[]{checksum + "1.json", checksum + "2.json", checksum + "3.json"};
        String[] fileContent = new String[]{"", "", ""};
        for (int i = 0; i < jsonFilenames.length; i++) {
            try {
                FileInputStream is = this.openFileInput(jsonFilenames[i]);

                int read = 0;
                byte[] buffer = new byte[16384];
                while ((read = is.read(buffer)) != -1) {
                    fileContent[i] += new String(buffer, 0, read);
                }
                is.close();
            } catch (Exception e) {
            }
        }

        // get max fileContent index
        int maxIndex = 0;
        for (int i = 1; i < fileContent.length; i++) {
            if (fileContent[i].length() > fileContent[maxIndex].length()) {
                maxIndex = i;
            }
        }

        String jsonFilename = checksum + (maxIndex + 1) + ".json";
        List<Map<String, Object>> links = Collections.emptyList();

        try {
            FileInputStream is = this.openFileInput(jsonFilename);

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();

            String json = new String(buffer.toByteArray(), Consts.UTF_8).replaceAll(" ", "").replaceAll("C137", "");
            links = linksJSON.fromJson(json.substring(4), List.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (links.size() == 0) {
            return;
        }

        buttonLayout.removeAllViews();

        for (int i = 0; i < links.size(); i++) {
            FrameLayout button = new FrameLayout(this);
            Map<String, Object> link = links.get(i);

            int height = this.metadata.height * (viewportWidth / this.metadata.width);

            float widthScale = (float) this.viewportWidth / this.metadata.width;
            float heightScale = (float) height / this.metadata.height;

            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                    (int) (Float.parseFloat(link.get("w").toString()) * widthScale),
                    (int) (Float.parseFloat(link.get("h").toString()) * heightScale)
            );
            params.leftMargin = (int) (Float.parseFloat(link.get("x").toString()) * widthScale);
            params.topMargin = (int) (Float.parseFloat(link.get("y").toString()) * heightScale);

            button.setLayoutParams(params);
            button.setOnClickListener(view -> {
                Telemetry.insert(dbHelper, "hyperlink", Telemetry.CLICKED + "__" + link.get("href"));

                Map<String, String> record = dbHelper.getMapById("metadata", "url", link.get("href") + "");
                if (record != null) {
                    Intent intent = new Intent(this, Webpage.class);
                    intent.putExtra("checksum", record.get("checksum"));
                    startActivity(intent);
                    return;
                }

                Utils.showAlertDialog(this, "Link has not been received yet. Do you want to request it? It will count towards your quota.", (dialog, id) -> {
                    try {
                        SharedPreferences prefs = getApplicationContext().getSharedPreferences("com.ayush.quietlib", Context.MODE_PRIVATE);

                        int quota = dbHelper.getQuota();

                        int totalQuota = prefs.getInt("quota", Constants.QUOTA);

                        if (quota <= 0) {
                            Toast.makeText(this, "You have reached your daily quota. Please try again tomorrow.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        receiverPhone = prefs.getString("receiverPhone", "");

                        SMSHelper.sendSMS(getApplicationContext(), receiverPhone, "REQ:" + link.get("href"), new SMSHelper.SMSCallback() {
                            @Override
                            public void onSuccess() {
                                ContentValues insertValues = new ContentValues();
                                insertValues.put("url", link.get("href").toString());
                                insertValues.put("type", "img");
                                insertValues.put("request_timestamp", String.valueOf(new Date().getTime()));
                                dbHelper.insert("requests", insertValues);

                                Toast.makeText(Webpage.this, "Request sent.", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(Exception e) {
                                e.printStackTrace();

                                Toast.makeText(getApplicationContext(), "Could not connect. Please try again.", Toast.LENGTH_LONG).show();
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

            });
            buttonLayout.addView(button);
        }

    }

    private void readGPTResponse() {
        FileInputStream fileInputStream1 = null;
        FileInputStream fileInputStream2 = null;

        try {
            try {
                fileInputStream1 = this.openFileInput(filename1);
                fileInputStream1.skip(Constants.frameLength);
            } catch (FileNotFoundException e) {
                Log.e("readGPTResponse", "filename1 not found");
            }

            try {
                fileInputStream2 = this.openFileInput(filename2);
                fileInputStream2.skip(Constants.frameLength);
            } catch (FileNotFoundException e) {
                Log.e("readGPTResponse", "filename2 not found");
            }

            FileInputStream[] streams = {fileInputStream1, fileInputStream2};

            chatResponses = new String[]{"", ""};
            while (true) {
                boolean dataAvailable = false;

                int i = 0;
                for (FileInputStream stream : streams) {
                    if (stream == null) continue;

                    byte[] buffer = new byte[Constants.frameLength];
                    int read = stream.read(buffer);
                    if (read == -1) continue;
                    dataAvailable = true;

                    try {
                        String bufferText = new String(buffer);
                        if (bufferText.startsWith("C137")) {
                            chatResponses[i] += bufferText.substring(4);
                        }
                    } catch (NumberFormatException | IndexOutOfBoundsException e) {
                        Log.e("initImages", "Error parsing frame/partition index or copying data" + e.toString());
                    }

                    i++;
                }

                if (!dataAvailable) break;
            }
        } catch (IOException e) {
            Log.e("readGPTResponse", "File read error", e);
            images = null;
        } finally {
            try {
                if (fileInputStream1 != null) fileInputStream1.close();
                if (fileInputStream2 != null) fileInputStream2.close();
            } catch (IOException e) {
                Log.e("readGPTResponse", "Error closing file streams", e);
            }
        }
    }

    private void initImages() {
        int[] partitionPayloadSizes = this.metadata.partitionSizes;
        int numberOfParts = partitionPayloadSizes.length;

        images = new byte[numberOfParts][];
        boolean[][] framesFilled = new boolean[numberOfParts][];

        for (int i = 0; i < numberOfParts; i++) {
            images[i] = new byte[partitionPayloadSizes[i]];
            int frameCount = (int) Math.ceil((double) partitionPayloadSizes[i] / (Constants.frameLength - 12));
            framesFilled[i] = new boolean[frameCount];
        }

        FileInputStream fileInputStream1 = null;
        FileInputStream fileInputStream2 = null;

        try {
            try {
                fileInputStream1 = this.openFileInput(filename1);
                fileInputStream1.skip(Constants.frameLength);
            } catch (FileNotFoundException e) {
                Log.e("initImages", "filename1 not found");
            }

            try {
                fileInputStream2 = this.openFileInput(filename2);
                fileInputStream2.skip(Constants.frameLength);
            } catch (FileNotFoundException e) {
                Log.e("initImages", "filename2 not found");
            }

            FileInputStream[] streams = {fileInputStream1, fileInputStream2};
            byte[] buffer = new byte[Constants.frameLength];

            while (true) {
                boolean dataAvailable = false;

                for (FileInputStream stream : streams) {
                    if (stream == null) continue;

                    int read = stream.read(buffer);
                    if (read == -1) continue;
                    dataAvailable = true;

                    try {
                        int frameIndex = Integer.parseInt(new String(buffer, 4, 5).trim());
                        int partitionIndex = Integer.parseInt(new String(buffer, 9, 3).trim());

                        int startPos = frameIndex * (Constants.frameLength - 12);
                        int length = Math.min(Constants.frameLength - 12, partitionPayloadSizes[partitionIndex] - startPos);

                        if (!framesFilled[partitionIndex][frameIndex] && length > 0) {
                            System.arraycopy(buffer, 12, images[partitionIndex], startPos, length);
                            framesFilled[partitionIndex][frameIndex] = true;
                        }
                    } catch (NumberFormatException | IndexOutOfBoundsException e) {
                        Log.e("initImages", "Error parsing frame/partition index or copying data");
                    }
                }

                if (!dataAvailable) break;
            }
        } catch (IOException e) {
            Log.e("initImages", "File read error", e);
            images = null;
        } finally {
            try {
                if (fileInputStream1 != null) fileInputStream1.close();
                if (fileInputStream2 != null) fileInputStream2.close();
            } catch (IOException e) {
                Log.e("initImages", "Error closing file streams", e);
            }
        }
    }

    private void renderImages() {
        int height = this.metadata.height * (viewportWidth / this.metadata.width);
        if (setWhite) {
            imgLayout.removeAllViews();

            for (int i = 0; i < images.length; i++) {
                ImageView img = new ImageView(this);
                img.setLayoutParams(new LinearLayout.LayoutParams((int) (viewportWidth / images.length), height));
                img.setBackgroundColor(Color.WHITE);
                imgLayout.addView(img);
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
                    img.setLayoutParams(new LinearLayout.LayoutParams((int) (viewportWidth / images.length), height));
                    imgLayout.removeViewAt(i);
                    imgLayout.addView(img, i);
                }
            }
            i++;
        }
    }
}