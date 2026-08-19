package com.ayush.quietlib;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.ayush.quietlib.rfm.controller.RadioController;
import com.ayush.quietlib.rfm.controller.RadioState;
import com.ayush.quietlib.rfm.controller.RadioStateUpdater;
import com.ayush.quietlib.rfm.controller.TunerStatus;
import com.ayush.quietlibrary.FrameReceiver;
import com.ayush.quietlibrary.FrameReceiverConfig;
import com.ayush.quietlibrary.ModemException;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Navigator extends AppCompatActivity implements RadioStateUpdater.TunerStateListener {
    private ViewPager2 viewPager;

    private ImageButton tabButton1, tabButton2, tabButton3;

    private final String selectedColor = "#0B192C";
    private final String unselectedColor = "#1E3E62";
    private RadioController mRadioController;
    private Handler handler;
    private DatabaseHelper dbHelper;
    private final int REQUEST_CODE_PERMISSIONS = 100;
    private boolean browserSeen = false;

    TextView radioStatusTxt;
    RelativeLayout radioStatus;

    @Override
    public void onStateUpdated(RadioState state, int mode) {
        if ((mode & RadioStateUpdater.SET_STATUS) > 0) {
            if (Objects.requireNonNull(state.getStatus()) == TunerStatus.IDLE) {
                mRadioController.setup();
                mRadioController.unregisterForUpdates();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigator);

        radioStatus = (RelativeLayout) findViewById(R.id.radioStatus);
        radioStatusTxt = (TextView) findViewById(R.id.radioStausTxt);

        String[] permissions = {
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                android.Manifest.permission.INTERNET,
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.MODIFY_AUDIO_SETTINGS,
                android.Manifest.permission.READ_PHONE_STATE,
                android.Manifest.permission.FOREGROUND_SERVICE,
        };

        boolean allPermissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_PERMISSIONS);
        }

        // TODO: permission check
        dbHelper = new DatabaseHelper(this);

        String[] telemetryColumns = {
                "id INTEGER PRIMARY KEY AUTOINCREMENT",
                "type TEXT",
                "data TEXT",
                "timestamp INTEGER",
        };

        dbHelper.createTable("telemetry", telemetryColumns);
        dbHelper.createTable("metrics", telemetryColumns);

        String[] configColumns = {
                "lastSignalReceived INTEGER NOT NULL",
        };
        dbHelper.createTable("config", configColumns);

        String[] metadataColumns = {
                "id INTEGER PRIMARY KEY AUTOINCREMENT",
                "type TEXT",
                "partitionSizesCSV TEXT",
                "url TEXT",
                "checksum TEXT",
                "width INTEGER",
                "height INTEGER",
                "timestamp INTEGER",
                "ready INTEGER DEFAULT 0",
        };
        dbHelper.createTable("metadata", metadataColumns);

        String[] transmissionColumns = {
                "id INTEGER PRIMARY KEY AUTOINCREMENT",
                "checksum TEXT",
                "filename TEXT",
                "bps REAL",
                "timestamp INTEGE",
        };
        dbHelper.createTable("transmissions", transmissionColumns);

        // RadioController init
        mRadioController = new RadioController(this);
        mRadioController.requestForCurrentState(this);
        mRadioController.registerForUpdates(this);

        // until C137 message is received, keep restarting the tuner
        // keep checking in db.config (check the last time we received if it is less than 5 seconds)
//        Thread radioCheckerThread = new Thread() {
//            private volatile boolean running = true;
//
//            @Override
//            public void run() {
//                try {
//                    while (running) {
//                        Thread.sleep(15000);
//
//                        List<Map<String, String>> config = dbHelper.getAllMap("config");
//                        if (!config.isEmpty()) {
//                            String lastC137 = config.get(0).get("lastSignalReceived");
//
//                            if (!lastC137.isEmpty()) {
//                                try {
//                                    long unixTimestamp = Long.parseLong(lastC137);
//                                    LocalDateTime inputDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(unixTimestamp), ZoneId.systemDefault());
//                                    Duration duration = Duration.between(inputDateTime, LocalDateTime.now());
//
//                                    if (duration.getSeconds() < 30) {
//                                        running = false;
//                                        this.interrupt();
//                                    } else {
//                                        mRadioController.disable();
////                                        mRadioController.kill();
//                                    }
//                                } catch (NumberFormatException e) {
//                                    Log.e("msg", "Error parsing timestamp", e);
//                                }
//                            } else {
//                                mRadioController.disable();
////                                mRadioController.kill();
//                            }
//                        } else {
//                            mRadioController.disable();
////                            mRadioController.kill();
//                        }
//                    }
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        };
//
//        radioCheckerThread.start();

        viewPager = findViewById(R.id.viewPager);
        ViewPagerAdapter viewPagerAdapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(viewPagerAdapter);

        tabButton1 = findViewById(R.id.tabButton1);
        tabButton1.setBackgroundColor(Color.parseColor(selectedColor));
        tabButton2 = findViewById(R.id.tabButton2);
        tabButton3 = findViewById(R.id.tabButton3);

        tabButton1.setOnClickListener(v -> {
            Telemetry.insert(dbHelper, "browser", Telemetry.CLICKED);
            viewPager.setCurrentItem(0, true);
        });

        tabButton2.setOnClickListener(v -> {
            Telemetry.insert(dbHelper, "knowledge_hub", Telemetry.CLICKED);
            viewPager.setCurrentItem(1, true);
        });

        tabButton3.setOnClickListener(v -> {
            Telemetry.insert(dbHelper, "chat_gpt", Telemetry.CLICKED);
            viewPager.setCurrentItem(2, true);
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);

                switch (position) {
                    case 0:
                        if (browserSeen) {
                            Telemetry.insert(dbHelper, "browser", Telemetry.SWIPED);
                        } else {
                            Telemetry.insert(dbHelper, "app", Telemetry.OPENED);
                            browserSeen = true;
                        }
                        tabButton1.setBackgroundColor(Color.parseColor(selectedColor));
                        tabButton2.setBackgroundColor(Color.parseColor(unselectedColor));
                        tabButton3.setBackgroundColor(Color.parseColor(unselectedColor));
                        break;
                    case 1:
                        Telemetry.insert(dbHelper, "knowledge_hub", Telemetry.SWIPED);
                        tabButton1.setBackgroundColor(Color.parseColor(unselectedColor));
                        tabButton2.setBackgroundColor(Color.parseColor(selectedColor));
                        tabButton3.setBackgroundColor(Color.parseColor(unselectedColor));
                        break;
                    case 2:
                        Telemetry.insert(dbHelper, "chat_gpt", Telemetry.SWIPED);
                        tabButton1.setBackgroundColor(Color.parseColor(unselectedColor));
                        tabButton2.setBackgroundColor(Color.parseColor(unselectedColor));
                        tabButton3.setBackgroundColor(Color.parseColor(selectedColor));
                        break;
                }
            }
        });

        Thread connectionStatusThread = new Thread(new Runnable() {
            @Override
            public void run() {
                long startRun = new Date().getTime();
                String offlineTxt = "FM Station Offline.\nYour requests will be delivered between " + Constants.BROADCAST_START_HOUR + ":" + Constants.BROADCAST_START_MINUTE + "0 - " + Constants.BROADCAST_END_HOUR + ":00.";
                while (true) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    // if current hour is < broadcast hour or > broadcast hour,
                    int currentHour = LocalDateTime.now().getHour();
                    if (currentHour < Constants.BROADCAST_START_HOUR || currentHour > Constants.BROADCAST_END_HOUR) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                radioStatusTxt.setText(offlineTxt);
                                radioStatus.setBackgroundColor(Color.BLACK);
                            }
                        });
                        continue;
                    }

                    List<Map<String, String>> config = dbHelper.getAllMap("config", "");
                    if (!config.isEmpty()) {
                        String lastC137 = config.get(0).get("lastSignalReceived");
                        if (!lastC137.isEmpty()) {
                            try {
                                long unixTimestamp = Long.parseLong(lastC137);
                                LocalDateTime inputDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(unixTimestamp), ZoneId.systemDefault());
                                Duration duration = Duration.between(inputDateTime, LocalDateTime.now());

                                long sec = duration.getSeconds();
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (sec > 300 && new Date().getTime() - startRun > 30000) {
                                            radioStatusTxt.setText(offlineTxt);
                                            radioStatus.setBackgroundColor(Color.BLACK);
                                        } else if (sec > 30) {
                                            radioStatusTxt.setText("Connecting...");
                                            radioStatus.setBackgroundColor(Color.rgb(199, 103, 8));
                                        } else {
                                            radioStatusTxt.setText("Connected");
                                            radioStatus.setBackgroundColor(Color.rgb(38, 128, 43));
                                        }
                                    }
                                });

                            } catch (RuntimeException e) {
                                throw new RuntimeException(e);
                            }

                        }
                    } else if (new Date().getTime() - startRun > 30000) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                radioStatusTxt.setText(offlineTxt);
                                radioStatus.setBackgroundColor(Color.BLACK);
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                radioStatusTxt.setText("Connecting...");
                                radioStatus.setBackgroundColor(Color.rgb(199, 103, 8));
                            }
                        });
                    }
                }
            }
        });

        connectionStatusThread.start();


    }

    @Override
    public void onBackPressed() {

    }
}
