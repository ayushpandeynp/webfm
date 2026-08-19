package com.ayush.quietlib;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.ayush.quietlib.rfm.service.audio.LightAudioService;

public class Settings extends AppCompatActivity {

    EditText frequency, startTime, endTime, receiverPhone, quota;
    Button saveButton;
    CheckBox check;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        frequency = findViewById(R.id.frequency);
        startTime = findViewById(R.id.startTime);
        endTime = findViewById(R.id.endTime);
        receiverPhone = findViewById(R.id.receiverNumber);
        quota = findViewById(R.id.quota);
//        check = findViewById(R.id.hearGood);

        SharedPreferences p = this.getSharedPreferences("com.ayush.quietlib", Context.MODE_PRIVATE);

        frequency.setText(String.valueOf((float) p.getInt("frequency", Constants.FREQUENCY) / 1000));
        startTime.setText(String.valueOf(p.getInt("startTime", Constants.BROADCAST_START_HOUR)));
        endTime.setText(String.valueOf(p.getInt("endTime", Constants.BROADCAST_END_HOUR)));
        receiverPhone.setText(p.getString("receiverPhone", ""));
        quota.setText(String.valueOf(p.getInt("quota", Constants.QUOTA)));

//        check.setOnCheckedChangeListener((buttonView, isChecked) -> {
//            Intent i = new Intent("com.ayush.quietlib.RADIO_FREQ_UPDATE");
//            i.putExtra("type", "hearGood");
//            if (isChecked) {
//                i.putExtra("hearGood", true);
//                sendBroadcast(i);
//            } else {
//                i.putExtra("hearGood", false);
//                sendBroadcast(i);
//            }
//        });

        saveButton = findViewById(R.id.saveButton);
        saveButton.setOnClickListener(v -> {
            SharedPreferences prefs = this.getSharedPreferences("com.ayush.quietlib", Context.MODE_PRIVATE);

            int freq = Constants.FREQUENCY;
            int start = Constants.BROADCAST_START_HOUR;
            int end = Constants.BROADCAST_END_HOUR;
            int q = Constants.QUOTA;
            String receiver = "";

            SharedPreferences.Editor editor = prefs.edit();
            try {
                freq = (int) (1000 * Float.parseFloat(frequency.getText().toString()));
                start = Integer.parseInt(startTime.getText().toString());
                end = Integer.parseInt(endTime.getText().toString());
                q = Integer.parseInt(quota.getText().toString());
                receiver = receiverPhone.getText().toString();
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }

            editor.putInt("frequency", freq);
            editor.putInt("startTime", start);
            editor.putInt("endTime", end);
            editor.putInt("quota", q);
            editor.putString("receiverPhone", receiver);
            editor.apply();

            Intent intent = new Intent("com.ayush.quietlib.RADIO_FREQ_UPDATE");
            intent.putExtra("type", "freq");
            intent.putExtra("frequency", freq);
            sendBroadcast(intent);

            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        });
    }
}