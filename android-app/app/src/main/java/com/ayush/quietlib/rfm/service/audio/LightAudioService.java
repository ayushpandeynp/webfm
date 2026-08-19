package com.ayush.quietlib.rfm.service.audio;

import static androidx.core.content.ContextCompat.getSystemService;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.ayush.quietlib.Constants;
import com.ayush.quietlib.DatabaseHelper;
import com.ayush.quietlib.Metadata;
import com.ayush.quietlib.SMSHelper;
import com.ayush.quietlib.Utils;
import com.ayush.quietlib.rfm.controller.RadioController;
import com.ayush.quietlib.rfm.controller.RadioState;
import com.ayush.quietlib.rfm.controller.RadioStateUpdater;
import com.ayush.quietlib.rfm.service.FMService;
import com.ayush.quietlib.rfm.service.fm.RecordError;
import com.ayush.quietlib.rfm.service.recording.IAudioRecordable;
import com.ayush.quietlib.rfm.service.recording.IFMRecorder;
import com.ayush.quietlibrary.FrameReceiver;
import com.ayush.quietlibrary.FrameReceiverConfig;
import com.ayush.quietlibrary.ModemException;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public class LightAudioService extends AudioService implements IAudioRecordable, RadioStateUpdater.TunerStateListener {
    private Context c;
    private Thread mThread;

    private AudioTrack mAudioTrack;
    private AudioRecord mAudioRecorder;
    private IFMRecorder mRecorder;

    private FrameReceiver receiver = null;
    private FrameReceiverConfig receiverConfig = null;
    private boolean mIsActive = false;
    private DatabaseHelper dbHelper;

    private RadioController radioController;
    private Handler handler;
    private volatile boolean running = false;
    private boolean freqSet = false;
    private boolean hearGood = false;

    private short step = 0;
    private long lastSetTime = -1;

    String recentURL = "";

    TelephonyManager telephonyManager;
    PhoneStateListener phoneStateListener;
    public int cellularSignal = -1;
    int batLevel = 0;
    boolean isCharging = false;
    String receiverPhone;

    @SuppressLint("MissingPermission")
    public LightAudioService(final Context context) {
        super(context);

        context.registerReceiver(new Receiver(), new IntentFilter("com.ayush.quietlib.RADIO_FREQ_UPDATE"));

        radioController = new RadioController(context);
        radioController.requestForCurrentState(this);
        radioController.registerForUpdates(this);

        phoneStateListener = new myPhoneStateListener();
        telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

        handler = new Handler();
        dbHelper = new DatabaseHelper(context);

        Thread batteryPercentageThread = new Thread(new Runnable() {
            @Override
            public void run() {
                BatteryManager manager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
                batLevel = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                boolean isCharging = manager.isCharging();

                ContentValues values = new ContentValues();
                values.put("type", "battery");
                values.put("data", (isCharging ? "charging_" : "uncharged_") + batLevel);
                values.put("timestamp", String.valueOf(new Date().getTime()));
                dbHelper.insert("metrics", values);

                values.clear();
                values.put("type", "cellularSignal");
                values.put("data", String.valueOf(cellularSignal));
                values.put("timestamp", String.valueOf(new Date().getTime()));
                dbHelper.insert("metrics", values);

                SharedPreferences prefs = context.getSharedPreferences("com.ayush.quietlib", Context.MODE_PRIVATE);
                int frequency = prefs.getInt("frequency", Constants.FREQUENCY);

//                if (lastSetTime != -1 && (new Date().getTime() - lastSetTime) < 15000) {
                values.clear();
                values.put("type", "rssi");
                values.put("data", radioController.getState().getRssi() + "____" + frequency);
                values.put("timestamp", String.valueOf(new Date().getTime()));
                dbHelper.insert("metrics", values);
//                }

                handler.postDelayed(this, 1000 * 60);
            }
        });

        handler.postDelayed(batteryPercentageThread, 1000);

        try {
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            LocationListener locationListener = new LocationListener() {

                long lastSet = -1;

                @Override
                public void onLocationChanged(@NonNull Location location) {
                    if (lastSet == -1 || (new Date().getTime() - lastSet) > 300000) {
                        ContentValues values = new ContentValues();
                        values.put("type", "gps");
                        values.put("data", location.getLatitude() + "," + location.getLongitude());
                        values.put("timestamp", String.valueOf(new Date().getTime()));
                        dbHelper.insert("metrics", values);

                        lastSet = new Date().getTime();
                    }
                }

                @Override
                public void onProviderEnabled(@NonNull String provider) {

                }

                @Override
                public void onProviderDisabled(@NonNull String provider) {

                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {

                }
            };

            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListener);
            // locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, locationListener);

        } catch (Exception e) {
            e.printStackTrace();
        }

        // heartbeat
        Thread heartbeat = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000 * 60 * 60);

                    AudioManager audioManager = (AudioManager) this.c.getSystemService(Context.AUDIO_SERVICE);
                    boolean isHeadsetOn = audioManager.isWiredHeadsetOn();

                    SharedPreferences prefs = context.getSharedPreferences("com.ayush.quietlib", Context.MODE_PRIVATE);
                    receiverPhone = prefs.getString("receiverPhone", "");

//                    String data = batLevel + "_" + (isCharging ? "charging" : "not_charging") + "_" + (isHeadsetOn ? "headset_on" : "headset_off");
//                    SMSHelper.sendSMS(this.c, receiverPhone, "HBT:" + data, new SMSHelper.SMSCallback() {
//                        @Override
//                        public void onSuccess() {
//                        }
//
//                        @Override
//                        public void onError(Exception e) {
//                            e.printStackTrace();
//                        }
//                    });

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        heartbeat.start();
    }

    @Override
    public void startAudio(Context c) {
        if (mIsActive) {
            return;
        }

        // Context
        this.c = c;

        try {
            receiverConfig = new FrameReceiverConfig(c, "14k-channel-9.2KHz", Constants.frameLength);
            receiverConfig.setSampleRate(44100);
        } catch (IOException | JSONException e) {
            Log.d("RECEIVER_ERROR", "ISSUE CREATING RECEIVER CONFIG");
        }

        try {
            receiver = new FrameReceiver(receiverConfig);
            // receiver.setBlocking(0, 0);
        } catch (ModemException e) {
            Log.d("RECEIVER_ERROR", "ISSUE CREATING RECEIVER");
        }

        mIsActive = true;
        if (!running) {
            running = true;
            mThread = new Thread(mReadWrite);
            mThread.start();

            Thread radioCheckerThread = new Thread() {
                @Override
                public void run() {
                    try {
                        while (running) {
                            Thread.sleep(15000);
                            Log.d("msg", "I WOKE UP");

                            if (Utils.isTimeToBroadcast(c)) {
                                List<Map<String, String>> config = dbHelper.getAllMap("config", "");
                                if (!config.isEmpty()) {
                                    String lastC137 = config.get(0).get("lastSignalReceived");
                                    if (!lastC137.isEmpty()) {
                                        try {
                                            long unixTimestamp = Long.parseLong(lastC137);
                                            LocalDateTime inputDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(unixTimestamp), ZoneId.systemDefault());
                                            Duration duration = Duration.between(inputDateTime, LocalDateTime.now());

                                            if (duration.getSeconds() >= 30) {
                                                step = 0;
                                                radioController.disable();

                                            }
                                        } catch (NumberFormatException e) {
                                            Log.e("msg", "Error parsing timestamp", e);
                                        }
                                    } else {
                                        try {
                                            step = 0;
                                            radioController.disable();
                                        } catch (Exception e) {

                                        }
                                    }
                                } else {
                                    try {
                                        step = 0;
                                        radioController.disable();
                                    } catch (Exception e) {

                                    }
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        running = false;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            radioCheckerThread.start();
        }
    }

    @Override
    public void stopAudio() {
        if (mThread != null) {
            mThread.interrupt();
        }

//        if (!mIsActive) {
//            return;
//        }

//        mIsActive = false;
//        closeAll();
    }

    private void closeAll() {
        mIsActive = false;
        if (mAudioTrack != null) {
            mAudioTrack.release();
            mAudioTrack = null;
        }

        if (mAudioRecorder != null) {
            if (mAudioRecorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                mAudioRecorder.stop();
            }
            mAudioRecorder.release();
            mAudioRecorder = null;
        }
    }

    private short[] readWavFileFromAssets(String fileName) {
        AssetManager assetManager = this.c.getAssets();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (InputStream inputStream = assetManager.open(fileName)) {
            byte[] header = new byte[44];
            inputStream.read(header);

            StringBuilder headerHex = new StringBuilder();
            for (byte b : header) {
                headerHex.append(String.format("%02X ", b));
            }
            Log.d("WavFileStats", "Raw header: " + headerHex.toString());

            String chunkID = new String(header, 0, 4);
            int fileSize = bytesToInt(header, 4);
            String format = new String(header, 8, 4);
            int sampleRate = bytesToInt(header, 24);
            int bitsPerSample = bytesToShort(header, 34);
            int numChannels = bytesToShort(header, 22);

            String TAG = "WavFileStats";
            Log.d(TAG, "File: " + fileName);
            Log.d(TAG, "Chunk ID: " + chunkID);
            Log.d(TAG, "File Size: " + fileSize + " bytes");
            Log.d(TAG, "Format: " + format);
            Log.d(TAG, "Sample Rate: " + sampleRate + " Hz");
            Log.d(TAG, "Bits per Sample: " + bitsPerSample);
            Log.d(TAG, "Number of Channels: " + numChannels);

            if (bitsPerSample != 16) {
                Log.e("WavFileStats", "Unsupported bits per sample: " + bitsPerSample);
                return null;
            }

            List<Short> pcmData = new ArrayList<>();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                for (int i = 0; i < length; i += 2) {
                    short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
                    pcmData.add(sample);
                }
            }

            short[] pcmArray = new short[pcmData.size()];
            for (int i = 0; i < pcmData.size(); i++) {
                pcmArray[i] = pcmData.get(i);
            }

            return pcmArray;
        } catch (IOException e) {
            Log.e("WavFileStats", "Error reading WAV file", e);
            return null;
        }
    }

    private int bytesToInt(byte[] bytes, int offset) {
        return ((bytes[offset + 3] & 0xFF) << 24) | ((bytes[offset + 2] & 0xFF) << 16) | ((bytes[offset + 1] & 0xFF) << 8) | (bytes[offset] & 0xFF);
    }

    private int bytesToShort(byte[] bytes, int offset) {
        return ((bytes[offset + 1] & 0xFF) << 8) | (bytes[offset] & 0xFF);
    }

    public static void saveToFile(Context context, String fileName, ByteArrayOutputStream data) {
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = context.openFileOutput(fileName, Context.MODE_PRIVATE);
            fileOutputStream.write(data.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public class myPhoneStateListener extends PhoneStateListener {
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            super.onSignalStrengthsChanged(signalStrength);
            if (signalStrength.isGsm()) {
                if (signalStrength.getGsmSignalStrength() != 99)
                    cellularSignal = signalStrength.getGsmSignalStrength() * 2 - 113;
                else
                    cellularSignal = signalStrength.getGsmSignalStrength();
            } else {
                cellularSignal = signalStrength.getCdmaDbm();
            }

        }
    }

    @Override
    public void onStateUpdated(RadioState state, int mode) {
        if ((mode & RadioStateUpdater.SET_STATUS) > 0) {
            Log.d("STATUS", state.getStatus().toString());
            switch (state.getStatus()) {
                case IDLE:
                    radioController.setup();
                    step = 1;
                    break;

                case INSTALLING:
                    step = 2;
                    break;

                case INSTALLED:
                    step = 3;
                    break;

                case LAUNCHING:
                    step = 4;
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (step == 4) {
                                step = 0;
                                radioController.disable();
                            }
                        }
                    }, 5000);
                    break;

                case LAUNCHED:
                    step = 5;
                    break;

                case ENABLED:
                    if (!freqSet) {
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                SharedPreferences prefs = c.getSharedPreferences("com.ayush.quietlib", Context.MODE_PRIVATE);
                                int freq = prefs.getInt("frequency", Constants.FREQUENCY);
                                radioController.setFrequency(freq);
                            }
                        }, 2000);

                        freqSet = true;
                    }
                    break;
                case DISABLING:
                    step = 6;
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (step == 6) {
                                step = 0;
                                radioController.disable();
                                radioController.launch();
                            }
                        }
                    }, 5000);
                    break;
            }

        }
    }

    private final Runnable mReadWrite = () -> {
        final int bufferSize = 4096;

        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM);

        mAudioRecorder = getAudioRecorder();
        mAudioRecorder.startRecording();
        mAudioTrack.play();

        // FM
        final short[] buffer = new short[bufferSize];

        byte[] buf = new byte[Constants.frameLength];

        ByteArrayOutputStream response = new ByteArrayOutputStream();
        int sdtaIndex = -1;
        int lnksIndex = -1;

        float avgDataRate = -1;

        while (mIsActive) {
            long now = new Date().getTime();
            mAudioRecorder.read(buffer, 0, bufferSize);

            try {
                long ln = receiver.receive(buf, buffer);
                long after = new Date().getTime();

                if (ln < Constants.frameLength) {
                    continue;
                }

                long diff = (after - now);
                float dataRate_bps = (float) (ln * 8 * 1000) / diff;

                if (avgDataRate == -1) {
                    avgDataRate = dataRate_bps;
                } else {
                    avgDataRate = (avgDataRate + dataRate_bps) / 2;
                }


                String first4Chars = new String(buf, 0, 4, StandardCharsets.UTF_8);
                Log.d("first4", first4Chars);
                if (!(first4Chars.equals("MDTA") || first4Chars.equals("SDTA") || first4Chars.equals("C137") || first4Chars.equals("CKSM") || first4Chars.equals("LNKS"))) {
                    buf = new byte[Constants.frameLength];
                    continue;
                }

                response.write(buf, 0, (int) Constants.frameLength);

                // C137 message for trigger
                if (first4Chars.equals("C137") && (lastSetTime == -1 || (new Date().getTime() - lastSetTime) > 15000)) {
                    ContentValues values = new ContentValues();
                    values.put("lastSignalReceived", String.valueOf(new Date().getTime()));

                    if (dbHelper.getAllMap("config", "").isEmpty()) {
                        dbHelper.insert("config", values);
                    } else {
                        dbHelper.update("config", values, "1", null);
                    }

                    lastSetTime = new Date().getTime();
                }

                if (first4Chars.equals("MDTA")) {
                    StringBuilder metadataResponse = new StringBuilder(new String(response.toByteArray(), StandardCharsets.UTF_8));
                    response.reset();

                    int readTimes = 0;
                    while (!metadataResponse.toString().contains("EOMD")) {
                        buf = new byte[Constants.frameLength];
                        ln = 0;

                        try {
                            mAudioRecorder.read(buffer, 0, bufferSize);
                            ln = receiver.receive(buf, buffer);
                            if (ln < Constants.frameLength) {
                                continue;
                            }
                        } catch (Exception e) {
                        }

                        metadataResponse.append(new String(buf, 0, (int) ln, StandardCharsets.UTF_8));

                        readTimes++;

                        if (readTimes >= 5 || (!metadataResponse.toString().contains("EOMD") && (metadataResponse.indexOf("SDTA") != -1 || metadataResponse.indexOf("EOF") != -1 || metadataResponse.indexOf("LNKS") != -1))) {
                            metadataResponse = new StringBuilder();
                            break;
                        }
                    }


                    if (metadataResponse.length() > 0) {
                        int endIndex = new String(buf, 0, (int) ln, StandardCharsets.UTF_8).indexOf("EOMD") + 4;
                        response.write(buf, endIndex, (int) (ln - endIndex));
                        metadataResponse = new StringBuilder(metadataResponse.substring(0, metadataResponse.indexOf("EOMD") + 4));

                        // get url, type, csvPayloadSizes, checksum. add to db table "metadata"
                        Pattern pattern = Pattern.compile("MDTA([a-zA-Z]+)(\\d+(?:,\\d+)*)URL(.*?)CKSM(.*?)W(.*?)H(.*?)EOMD");
                        Matcher matcher = pattern.matcher(metadataResponse.toString());

                        if (matcher.find()) {
                            ContentValues values = new ContentValues();
                            values.put("type", matcher.group(1));
                            values.put("partitionSizesCSV", matcher.group(2));
                            values.put("url", matcher.group(3));
                            recentURL = matcher.group(3);
                            values.put("checksum", matcher.group(4));
                            values.put("width", matcher.group(5));
                            values.put("height", matcher.group(6));
                            values.put("timestamp", String.valueOf(new Date().getTime()));

                            if (dbHelper.getMapById("metadata", "checksum", matcher.group(4)) == null) {
                                dbHelper.insert("metadata", values);

                                try {
                                    SharedPreferences prefs = this.c.getSharedPreferences("com.ayush.quietlib", Context.MODE_PRIVATE);
                                    receiverPhone = prefs.getString("receiverPhone", "");

//                                    SMSHelper.sendSMS(this.c, receiverPhone, "ACK:" + matcher.group(3), new SMSHelper.SMSCallback() {
//                                        @Override
//                                        public void onSuccess() {
//                                        }
//
//                                        @Override
//                                        public void onError(Exception e) {
//                                            e.printStackTrace();
//                                        }
//                                    });
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            } else {
                                dbHelper.update("metadata", values, "checksum = ?", new String[]{matcher.group(4)});
                            }
                        }
                    }

                }

                if (first4Chars.equals("LNKS")) {
                    lnksIndex = response.size() - Constants.frameLength;
                }

                if (first4Chars.equals("SDTA")) {
                    sdtaIndex = response.size() - Constants.frameLength;
                }

                if (first4Chars.equals("CKSM")) {
                    byte[] responseBytes = response.toByteArray();
                    String responseStr = new String(buf, StandardCharsets.UTF_8);

                    int endIndex = responseBytes.length - Constants.frameLength + responseStr.indexOf("EOF") + 3;

                    response.reset();

                    if (sdtaIndex == -1) {
                        continue;
                    }

                    ByteArrayOutputStream links = new ByteArrayOutputStream();
                    if (lnksIndex != -1) {
                        links.write(responseBytes, lnksIndex, sdtaIndex - lnksIndex);
                    }

                    response.write(responseBytes, sdtaIndex, endIndex - sdtaIndex);

                    // save to file and db "transmissions"
                    String checksum = responseStr.substring(4, responseStr.indexOf("EOF"));
                    int existingRows = dbHelper.getCount("transmissions", "checksum = ?", new String[]{checksum});

                    String filename = checksum + (existingRows + 1) + ".sonic";
                    saveToFile(this.c, filename, response);

                    if (lnksIndex != -1) {
                        saveToFile(this.c, filename.replace(".sonic", ".json"), links);
                    }
                    links.close();
                    lnksIndex = -1;

                    ContentValues values = new ContentValues();
                    values.put("checksum", checksum);
                    values.put("filename", filename);
                    values.put("bps", avgDataRate);
                    values.put("timestamp", String.valueOf(new Date().getTime()));
                    dbHelper.insert("transmissions", values);

                    // update requests
                    values.clear();
                    values.put("ready", 1);
                    dbHelper.update("requests", values, "url = ?", new String[]{recentURL});
                    dbHelper.update("metadata", values, "checksum = ?", new String[]{checksum});

                    response.reset();
                    avgDataRate = -1;
                    sdtaIndex = -1;
//                    response.write(responseBytes, endIndex, responseBytes.length - endIndex);
                }

                buf = new byte[Constants.frameLength];
            } catch (Exception e) {
                if (!(e instanceof IOException)) {
                    e.printStackTrace();
                }
            }

            if (mIsActive) {
                mAudioTrack.write(buffer, 0, bufferSize);
            }
        }


        // AIR
//        short[] buffer = new short[bufferSize];
//        byte[] buf = new byte[100];
//
//        while (mIsActive) {
//            try {
//                long ln = receiver.receive(buf, buffer);
//                if (ln > 0) {
//                    Intent intent = new Intent("com.ayush.quietlib.BROADCAST_RESPONSE");
//                    intent.putExtra("response", buf);
//                    intent.putExtra("responseLength", (int) ln);
//                    this.c.sendBroadcast(intent);
//                }
//
//                buf = new byte[100];
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        }

        // WAV
//        int bytes;
//        final short[] buffer = new short[bufferSize];
//
//        long recLen = 0;
//        byte[] buf = new byte[100];
//
//        short[] wavBytes = readWavFileFromAssets("output.wav");
//
//        if (wavBytes != null) {
//            int offset = 0;
//            int length = wavBytes.length;
//
//
//            while (mIsActive && offset < length) {
//                int bytesToRead = Math.min(bufferSize, length - offset);
//
//                System.arraycopy(wavBytes, offset, buffer, 0, bytesToRead);
//
//                try {
//                    long ln = receiver.receive(buf, buffer);
//
//
//                    Intent intent = new Intent("com.ayush.quietlib.BROADCAST_RESPONSE");
//                    intent.putExtra("response", buf);
//                    intent.putExtra("responseLength", (int) ln);
//                    this.c.sendBroadcast(intent);
//
//                    buf = new byte[100];
//                } catch (Exception e) {
//                    Log.d("RECEIVER ERROR", "ERR: " + e.getMessage());
//                }
//
//                if (mIsActive) {
//                    mAudioTrack.write(buffer, 0, bytesToRead);
//                }
//
//                offset += bytesToRead;
//            }
//
//        } else {
//            Log.e("ERROR", "Could not read wav file from assets.");
//        }
    };

    private class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            if (i.getExtras() != null) {
                String type = i.getStringExtra("type");
                if (type != null && type.equals("freq")) {
                    int freq = i.getIntExtra("frequency", Constants.FREQUENCY);
                    radioController.setFrequency(freq);
                } else {
                    hearGood = i.getBooleanExtra("hearGood", false);
                }
            }
        }
    }

    @Override
    public void startRecord(final IFMRecorder recorder) throws RecordError {
        mRecorder = recorder;
        recorder.startRecord();
    }

    @Override
    public void stopRecord() {
        if (mRecorder != null) {
            mRecorder.stopRecord();
        }
        mRecorder = null;
    }
}
