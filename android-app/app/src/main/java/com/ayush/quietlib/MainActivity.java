package com.ayush.quietlib;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.ayush.quietlib.rfm.controller.RadioController;
import com.ayush.quietlib.rfm.controller.RadioState;
import com.ayush.quietlib.rfm.controller.RadioStateUpdater;
import com.ayush.quietlibrary.FrameReceiver;
import com.ayush.quietlibrary.FrameReceiverConfig;
import com.ayush.quietlibrary.FrameTransmitter;
import com.ayush.quietlibrary.FrameTransmitterConfig;
import com.ayush.quietlibrary.ModemException;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener, RadioStateUpdater.TunerStateListener {
    private FrameReceiverConfig receiverConfig = null;
    private FrameTransmitterConfig transmitterConfig = null;

    private FrameReceiver receiver = null;
    private FrameTransmitter transmitter = null;

    private Button recButton;
    private TextView receivedText;
    private EditText transmitText;
    private RelativeLayout layout;
    private Spinner spinner;
    private ProgressBar progressBar;
    private ImageView imgView;
    private LinearLayout imgLayout;
    private EditText frameLengthInput;
    private EditText partitionSizeInput;
    private TextView configText;

    private JSONObject profilesObject;

    private String profileSelected;
    private boolean shuffle;

    Thread thread;
    Timer timer;
    private byte[][] images;

    private ActivityResultLauncher<Intent> arl;

    private final int frameIndexSize = 5;
    private final int partitionIndexSize = 3;
    private int customFrameLength = 100;
    private int customPartitionSize = 35;

    private long lastReceived = -1;

    private long receiveStartTime;

    private int imagePartitionLength;
    private boolean setWhite = true;

    private RadioController mRadioController;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        arl = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
            if (res.getResultCode() == Activity.RESULT_OK) {
                Intent data = res.getData();
                Uri selectedImage = Objects.requireNonNull(data).getData();

                try {
                    JSONObject profile = (JSONObject) profilesObject.get(profileSelected);
                    int frameLength = profile.getInt("frame_length");

                    InputStream imageStream = getContentResolver().openInputStream(selectedImage);

                    if (imageStream != null) {
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImage);
                        int numberOfParts = this.customPartitionSize;
                        int partWidth = bitmap.getWidth() / numberOfParts;

                        HashMap<Integer, byte[]>[] imgList = new HashMap[numberOfParts];

                        int totalBytes = 0;
                        String[] payloadPerPartition = new String[numberOfParts];

                        for (int i = 0; i < numberOfParts; i++) {
                            int frameIndex = 0;

                            imgList[i] = new HashMap<Integer, byte[]>();

                            int startX = i * partWidth;
                            Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, startX, 0, partWidth, bitmap.getHeight());

                            // saving output in a temp file with ParcelFileDescriptor, cuz without saving it gave an error
                            File tempFile;
                            try {
                                tempFile = File.createTempFile("temp", null, getCacheDir());
                            } catch (IOException e) {
                                e.printStackTrace();
                                continue;
                            }
                            ParcelFileDescriptor parcelFileDescriptor = null;
                            try {
                                parcelFileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_WRITE);
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                                continue;
                            }
                            FileOutputStream fileOutputStream = new FileOutputStream(parcelFileDescriptor.getFileDescriptor());
                            croppedBitmap.compress(Bitmap.CompressFormat.WEBP, 60, fileOutputStream);

                            try {
                                fileOutputStream.flush();
                                fileOutputStream.close();
                                parcelFileDescriptor.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            int bytesRead = 0;
                            int framePayloadLength = frameLength - (this.frameIndexSize + this.partitionIndexSize);

                            FileInputStream iStream = new FileInputStream(tempFile);
                            byte[] contentBuf = new byte[framePayloadLength];

                            while (bytesRead < tempFile.length()) {
                                bytesRead += iStream.read(contentBuf);

                                imgList[i].put(frameIndex, contentBuf);

                                if (tempFile.length() - bytesRead < framePayloadLength)
                                    framePayloadLength = (int) tempFile.length() - bytesRead;

                                contentBuf = new byte[framePayloadLength];
                                frameIndex++;
                            }

                            iStream.close();

                            totalBytes += tempFile.length();
                            payloadPerPartition[i] = String.valueOf(tempFile.length());

                            tempFile.delete();
                        }

                        imageStream.close();
                        Toast.makeText(this, "Transmitted bytes: " + totalBytes, Toast.LENGTH_SHORT).show();

                        while (true) {
                            // sending metadata in the beginning
                            byte[] metadata = ("img" + String.join(",", payloadPerPartition) + "eom").getBytes();

                            int start = 0;
                            while (start < metadata.length) {
                                transmitter.send(Arrays.copyOfRange(metadata, start, Math.min(start + frameLength, metadata.length)));
                                start += frameLength;
                            }

                            // send queue for payload with their frame and partition info
                            ArrayList<String> sendQueue = new ArrayList<>();

                            for (int i = 0; i < numberOfParts; i++) {
                                for (Integer j : imgList[i].keySet()) {
                                    sendQueue.add(i + "-" + j);
                                }
                            }

                            // shuffle send queue in case shuffle is ON
                            if (shuffle) {
                                Collections.shuffle(sendQueue);
                            }

                            transmitter.send("data".getBytes());

                            for (String item : sendQueue) {
                                String[] pair = item.split("-");
                                int partition = Integer.parseInt(pair[0]);
                                int frame = Integer.parseInt(pair[1]);

                                byte[] b = imgList[partition].get(frame);

                                String i = pair[1];
                                i = "0".repeat(this.frameIndexSize - i.getBytes().length) + i;
                                i += "0".repeat(this.partitionIndexSize - pair[0].getBytes().length) + pair[0];
                                int iLen = i.getBytes().length;

                                if (b != null) {
                                    byte[] send = new byte[iLen + b.length];
                                    System.arraycopy(i.getBytes(), 0, send, 0, iLen);

                                    System.arraycopy(b, 0, send, iLen, b.length);

                                    try {
                                        transmitter.send(send);
                                    } catch (java.io.IOException e) {
                                        Log.d("TransmissionError", e.toString());
                                    }
                                }
                            }

                            transmitter.send("PEACE".getBytes());

                        }


                    }

                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }

        });

        // profilesObject is the JSON file content about receiver configurations
        try {
            profilesObject = new JSONObject(FrameReceiverConfig.getDefaultProfiles(getApplicationContext(), customFrameLength));
        } catch (JSONException | IOException e) {
            throw new RuntimeException(e);
        }

        spinner = (Spinner) findViewById(R.id.profile);
        ArrayList<String> profileKeys = new ArrayList<>();
        profilesObject.keys().forEachRemaining(profileKeys::add);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, profileKeys);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);
        spinner.setSelection(profileKeys.indexOf("14k-channel-9.2KHz"));
//        profileSelected = profileKeys.get(0);
        profileSelected = "14k-channel-9.2KHz";
        layout = (RelativeLayout) findViewById(R.id.rlayout);

        transmitText = (EditText) findViewById(R.id.transmitText);
        transmitText.setVisibility(View.GONE);

        receivedText = (TextView) findViewById(R.id.receivedText);

        imgView = (ImageView) findViewById(R.id.receivedImage);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setProgress(0);

        CheckBox shuffleCheckBox = (CheckBox) findViewById(R.id.shuffle);
        shuffleCheckBox.setChecked(false);

        imgLayout = (LinearLayout) findViewById(R.id.imgLayout);

        frameLengthInput = (EditText) findViewById(R.id.frameLength);
        partitionSizeInput = (EditText) findViewById(R.id.partition_size);

        configText = (TextView) findViewById(R.id.configText);
        configText.setText(String.valueOf("Frame Length: " + customFrameLength + "\nPartition Size: " + customPartitionSize));

        Button frameLengthButton = (Button) findViewById(R.id.frameLengthButton);
        frameLengthButton.setOnClickListener(view -> setCustomFrameLength(Integer.parseInt(frameLengthInput.getText().toString())));

        Button partitionSizeButton = (Button) findViewById(R.id.partitionSizeButton);
        partitionSizeButton.setOnClickListener(view -> setCustomPartitionSize(Integer.parseInt(partitionSizeInput.getText().toString())));

        shuffleCheckBox.setOnCheckedChangeListener((compoundButton, b) -> shuffle = b);

        // Transmit and Receive Buttons (text, image)
        Button transButton = (Button) findViewById(R.id.transmitButton);
        transButton.setVisibility(View.GONE);
        Button transImgButton = (Button) findViewById(R.id.imgTransmitButton);

        recButton = (Button) findViewById(R.id.receiveButton);

        transButton.setOnClickListener(view -> transmit());
        transImgButton.setOnClickListener(view -> selectImage());

        recButton.setOnClickListener(view -> {
            imgView.setVisibility(View.GONE);
            progressBar.setProgress(0);
            if (thread != null) {
                destroyThread();

                recButton.setText("Receive");
                spinner.setEnabled(true);
            } else {
                initReceiver();
                receive();
            }
        });

        initTransmitter();

        // RADIO CONTROLLER
        Button startRadio = (Button) findViewById(R.id.startRadio);
        startRadio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mRadioController = new RadioController(MainActivity.this);
                mRadioController.requestForCurrentState(MainActivity.this);
                mRadioController.registerForUpdates(MainActivity.this);
            }
        });

        Button exitRadio = (Button) findViewById(R.id.exitRadio);
        exitRadio.setOnClickListener(v -> {
            mRadioController.disable();
            mRadioController.kill();

        });

        Button sampleTextTransmitButton = (Button) findViewById(R.id.sampleTransmitTextButton);
        sampleTextTransmitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    transmitter.send("C137".getBytes());
                } catch (Exception e) {
                    Log.d("TRANSMITTER ERROR", "Sample Transmission Failed");
                }
            }
        });
    }

    //    private final int FREQUENCY = 98500;
    private final int FREQUENCY = 96800;

    private void initTransmitter() {
        try {
            transmitterConfig = new FrameTransmitterConfig(this, profileSelected, customFrameLength);
            receiverConfig = new FrameReceiverConfig(this, profileSelected, customFrameLength);

        } catch (IOException | JSONException e) {
            Snackbar.make(layout, "initConfigs error: " + e, Snackbar.LENGTH_SHORT).show();
        }

        try {
            transmitter = new FrameTransmitter(transmitterConfig);
            transmitter.setBlocking(0, 0);
        } catch (ModemException e) {
            Snackbar.make(layout, "Modem Error: " + e, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void initReceiver() {
        try {
            receiverConfig = new FrameReceiverConfig(this, profileSelected, customFrameLength);

        } catch (IOException | JSONException e) {
            Snackbar.make(layout, "initReceiver error: " + e, Snackbar.LENGTH_SHORT).show();
        }

        try {
            receiver = new FrameReceiver(receiverConfig);
        } catch (ModemException e) {
            Snackbar.make(layout, "Modem Error: " + e, Snackbar.LENGTH_SHORT).show();
        }
    }

    // dropdown on item selected
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        profileSelected = parent.getItemAtPosition(position).toString();

        if (transmitter != null) {
            transmitter.close();
            initTransmitter();
        }
        if (receiver != null) {
            receiver.close();
            initReceiver();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        Toast.makeText(this, "Please select an item from the dropdown list.", Toast.LENGTH_SHORT).show();
    }

    private void setCustomPartitionSize(int partitionSize) {
        this.customPartitionSize = partitionSize;

        configText.setText("Frame Length: " + customFrameLength + "\nPartition Size: " + customPartitionSize);

        Toast.makeText(this, "Partition Size Updated", Toast.LENGTH_SHORT).show();
    }

    private void setCustomFrameLength(int frameLength) {
        this.customFrameLength = frameLength;

        try {
            profilesObject = new JSONObject(FrameReceiverConfig.getDefaultProfiles(getApplicationContext(), frameLength));
        } catch (JSONException | IOException e) {
            throw new RuntimeException(e);
        }

        configText.setText("Frame Length: " + frameLength + "\nPartition Size: " + customPartitionSize);

        if (transmitter != null) {
            transmitter.close();
            initTransmitter();
        }
        if (receiver != null) {
            receiver.close();
            initReceiver();
        }

        Toast.makeText(this, "Frame Length Updated", Toast.LENGTH_SHORT).show();
    }

    private String getCurrentTimestamp() {
        long timestamp = System.currentTimeMillis();
        Date date = new Date(timestamp);

        SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy h:m:s:SSS");
        return dateFormat.format(date);
    }

    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        arl.launch(intent);
    }

    private void transmit() {
        String payload = transmitText.getText().toString();
        String payloadLength = String.valueOf(payload.length());

        if (payload.length() == 0) {
            Toast.makeText(this, "Empty text not allowed", Toast.LENGTH_SHORT).show();
            return;
        }

        payload = "txt" + payloadLength + payload;

        try {
            transmitter.send(Arrays.copyOfRange(payload.getBytes(), 0, 3 + payloadLength.length()));

            int start = 3 + payloadLength.length();

            JSONObject profile = (JSONObject) profilesObject.get(profileSelected);
            int frameLength = profile.getInt("frame_length");

            Snackbar.make(layout, "Timestamp: " + getCurrentTimestamp(), Snackbar.LENGTH_INDEFINITE).show();
            while (start < payload.getBytes().length) {
                transmitter.send(Arrays.copyOfRange(payload.getBytes(), start, Math.min(payload.getBytes().length, start + frameLength)));
                start += frameLength;
            }

            Toast.makeText(this, "Transmitted bytes: " + payload.getBytes().length, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Snackbar.make(layout, "Error parsing profile: " + e, Snackbar.LENGTH_SHORT).show();
            throw new RuntimeException(e);
        }
    }

    private void receive() {
        // temp audioBuf
        short[] audioBuf = new short[customFrameLength * 100];

        receiveStartTime = System.currentTimeMillis();
        lastReceived = -1;

        // save image every 2 seconds
        timer = new Timer();

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                saveImage();
            }
        };
        timer.scheduleAtFixedRate(task, 0, 2000);

        thread = new Thread(() -> {
            receiver.setBlocking(0, 0);
            try {
                long recLen = 0;

                JSONObject profile = (JSONObject) profilesObject.get(profileSelected);
                int frameLength = profile.getInt("frame_length");

                StringBuilder txt = new StringBuilder();
                byte[] buf = new byte[frameLength];

                StringBuilder metadata = new StringBuilder();
                while (!metadata.toString().contains("e")) {
                    recLen += receiver.receive(buf, audioBuf);
                    metadata.append(new String(buf));

                    Log.d("Content", metadata.toString());

                    buf = new byte[frameLength];
                }

                lastReceived = System.currentTimeMillis();

                int metadataSize = (int) recLen;

                runOnUiThread(() -> {
                    receivedText.setText("Receiving...");
                });

                int noOfFrames = 0;
                int numberOfParts;
                int payloadLength = 0;
                int framePayloadLength = frameLength - this.frameIndexSize - this.partitionIndexSize;

                String[] partitionPayloadSizes;
                String contentType;

                try {
                    contentType = metadata.substring(0, 3);

                    String s = metadata.substring(3, metadata.indexOf("e"));
                    partitionPayloadSizes = s.split(",");
                    numberOfParts = partitionPayloadSizes.length;

                    imagePartitionLength = numberOfParts;

                    for (String partitionPayloadSize : partitionPayloadSizes) {
                        int size = Integer.parseInt(partitionPayloadSize);
                        int framesRequired = (int) Math.ceil((double) size / framePayloadLength);
                        noOfFrames += framesRequired;

                        payloadLength += size + framesRequired * (this.frameIndexSize + this.partitionIndexSize);
                    }
                } catch (NumberFormatException e) {
                    runOnUiThread(() -> {
                        receivedText.setText("Error occurred while parsing metadata. Please try again." + "\n");
                        spinner.setEnabled(true);
                        recButton.setText("Receive");
                        timer.cancel();
                    });

                    receiver.close();
                    destroyThread();

                    return;
                }

                // limit is 64k
                images = new byte[numberOfParts][];

                for (int i = 0; i < numberOfParts; i++) {
                    images[i] = new byte[Integer.parseInt(partitionPayloadSizes[i])];
                }

                int imageSize = 0;
                int recFrameCount = 0;

                recLen = 0;
                while (recLen < payloadLength) {
                    buf = new byte[frameLength];

                    long ln = receiver.receive(buf, audioBuf);
                    lastReceived = System.currentTimeMillis();

                    int partitionIndex = 0;

                    if (contentType.equals("txt")) {
                        txt.append(new String(buf));
                    } else {
                        int frameIndex = Integer.parseInt(new String(Arrays.copyOfRange(buf, 0, this.frameIndexSize)));
                        partitionIndex = Integer.parseInt(new String(Arrays.copyOfRange(buf, this.frameIndexSize, this.frameIndexSize + this.partitionIndexSize)));

                        byte[] content = new byte[(int) ln - this.frameIndexSize - this.partitionIndexSize];
                        System.arraycopy(buf, this.frameIndexSize + this.partitionIndexSize, content, 0, content.length);

                        recFrameCount++;

                        System.arraycopy(content, 0, images[partitionIndex], frameIndex * framePayloadLength, content.length);

                        imageSize += ln - (this.frameIndexSize + this.partitionIndexSize);
                    }

                    recLen += ln;

                    // runOnUiThread needs final variables
                    final String finalTxt = txt.toString();
                    final long finalRecLen = recLen;
                    final int finalRecFrameCount = recFrameCount;
                    final int finalImageSize = imageSize;

                    final int finalPayloadLength = payloadLength;
                    final int finalNoOfFrames = noOfFrames;

                    runOnUiThread(() -> {
                        if (contentType.equals("img")) {
                            this.renderImages();

                            progressBar.setProgress((int) Math.ceil(((double) finalRecLen / finalPayloadLength) * 100));
                            receivedText.setText("Data Rate: " + (finalRecLen / ((System.currentTimeMillis() - receiveStartTime) / 1000)) + "bytes/sec\n" + "Initial Metadata Size: " + metadataSize + " bytes\n" + "Payload Size: " + (finalRecLen - metadataSize) + " bytes\n" + "Total: " + finalRecLen + "/" + finalPayloadLength + " bytes\n" + "Image Size: " + finalImageSize + " bytes\n" + "Received Frames: " + finalRecFrameCount + "/" + finalNoOfFrames);
                        } else {
                            progressBar.setProgress((int) Math.ceil(((double) finalTxt.length() / finalPayloadLength) * 100));
                            receivedText.setText("Received Payload: " + finalTxt.length() + "/" + finalPayloadLength + " bytes");
                        }
                    });
                }

                // runOnUiThread needs final variables
                final String finalTxt = txt.toString();
                final int finalImageSize = imageSize;

                runOnUiThread(() -> {
                    Snackbar.make(layout, "Timestamp: " + getCurrentTimestamp(), Snackbar.LENGTH_INDEFINITE).show();

                    if (contentType.equals("txt")) {
                        receivedText.setText("Received bytes: " + finalTxt.length() + "\n\n" + finalTxt);
                    } else {
                        Toast.makeText(this, "Received image of " + finalImageSize + " bytes", Toast.LENGTH_SHORT).show();
                    }

                    spinner.setEnabled(true);
                    recButton.setText("Receive");

                    receiver.close();
                    destroyThread();
                });

            } catch (IOException e) {
                runOnUiThread(() -> {
                    recButton.setText("Receive");
                    spinner.setEnabled(true);

                    receiver.close();
                    timer.cancel();
                });
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        });

        thread.start();
        recButton.setText("Listening... Stop?");
        spinner.setEnabled(false);
    }

    private void saveImage() {
        if (lastReceived == -1) {
            return;
        }

        int imageWidth = 0;
        int imageHeight = 0;

        for (byte[] image : images) {
            Bitmap bmp = BitmapFactory.decodeByteArray(image, 0, image.length);

            if (bmp != null) {
                imageWidth += bmp.getWidth();
                imageHeight = bmp.getHeight();
            }
        }
        if (imageWidth > 0 && imageHeight > 0) {
            Bitmap resultBitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(resultBitmap);

            int currentX = 0;

            for (byte[] image : images) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length);

                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, currentX, 0, null);
                    currentX += bitmap.getWidth();
                }
            }

            String filename = "SONIC_RECEIVED.webp";

            File file = new File(Environment.getExternalStorageDirectory(), filename);
            if (file.exists()) {
                boolean deleted = file.delete();
                if (!deleted) {
                    Log.d("DeletionError", "Error deleting existing file on saveImage");
                }
            }

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/webp");

            Uri imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            try {
                OutputStream outputStream = getContentResolver().openOutputStream(imageUri);
                resultBitmap.compress(Bitmap.CompressFormat.WEBP, 100, outputStream); // Save as JPEG with maximum quality
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void renderImages() {
        if (setWhite) {
            imgLayout.removeAllViews();

            for (int i = 0; i < imagePartitionLength; i++) {
                ImageView img = new ImageView(this);
                img.setLayoutParams(new LinearLayout.LayoutParams((int) (350 / customPartitionSize), 622));
                img.setBackgroundColor(Color.WHITE);
                imgLayout.addView(img);
            }

            setWhite = false;
        }

        int i = 0;
        for (byte[] image : images) {
            if (image != null && image.length > 0) {
                ImageView img = new ImageView(this);
                img.setScaleType(ImageView.ScaleType.CENTER);
                Bitmap bmp = BitmapFactory.decodeByteArray(image, 0, image.length);

                if (bmp != null) {
                    img.setImageBitmap(bmp);
                    img.setLayoutParams(new LinearLayout.LayoutParams((int) (350 / customPartitionSize), 622));
                    imgLayout.removeViewAt(i);
                    imgLayout.addView(img, i);
                }
            }
            i++;
        }
    }

    private void destroyThread() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }

    }

    @Override
    public void onStateUpdated(RadioState state, int mode) {
        if ((mode & RadioStateUpdater.SET_STATUS) > 0) {
            switch (state.getStatus()) {
                case IDLE:
                    mRadioController.setup();
                    break;
                case ENABLED:
                    mRadioController.setFrequency(FREQUENCY);
                    break;
            }
        }
    }
}