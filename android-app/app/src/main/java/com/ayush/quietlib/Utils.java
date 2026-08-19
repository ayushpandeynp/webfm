package com.ayush.quietlib;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Environment;

import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;

public class Utils {
    public static String timeAgo(String dateString) {
        try {
            long unixTimestamp = Long.parseLong(dateString);
            LocalDateTime inputDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(unixTimestamp), ZoneId.systemDefault());
            Duration duration = Duration.between(inputDateTime, LocalDateTime.now());

            long sec = duration.getSeconds();
            long min = duration.toMinutes();
            long hrs = duration.toHours();
            long days = duration.toDays();

            if (sec < 60) return "Just now";
            if (min < 60) return min + "m ago";
            if (hrs < 24) return hrs + "h ago";
            return days + "d ago";
        } catch (Exception e) {
            return "";
        }
    }

    public static long getFileSize(Context context, String fileName) {
        File file = new File(context.getFilesDir(), fileName);

        if (file.exists()) {
            return file.length();
        } else {
            return -1;
        }
    }

    public static boolean exportDatabase(Context context, String databaseName) {
        File dbFile = context.getDatabasePath(databaseName);
        File exportDir = new File(Environment.getExternalStorageDirectory(), "SONIC");

        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }

        File backupFile = new File(exportDir, databaseName);

        try (FileInputStream fis = new FileInputStream(dbFile);
             FileOutputStream fos = new FileOutputStream(backupFile)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }

            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void saveInternalFileToExternalStorage(Context context, String fileName) {
        File internalFile = new File(context.getFilesDir(), fileName);
        File externalFile = new File(Environment.getExternalStorageDirectory(), fileName);

        try (FileInputStream fis = new FileInputStream(internalFile);
             FileOutputStream fos = new FileOutputStream(externalFile)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }

        } catch (IOException e) {

        }
    }

    public static void showAlertDialog(Context context, String text, DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(text)
                .setPositiveButton("Yes", listener).setNegativeButton("Cancel", (dialog, id) -> {
                    dialog.cancel();
                })
                .show();
    }

    public static boolean isTimeToBroadcast(Context c) {
        Calendar calendar = Calendar.getInstance();
        SharedPreferences prefs = c.getSharedPreferences("com.ayush.quietlib", Context.MODE_PRIVATE);
        int startHour = prefs.getInt("startTime", Constants.BROADCAST_START_HOUR);
        int startMin = prefs.getInt("startMinute", Constants.BROADCAST_START_MINUTE);
        int endHour = prefs.getInt("endTime", Constants.BROADCAST_END_HOUR);
        int endMin = prefs.getInt("endMinute", Constants.BROADCAST_END_MINUTE);

        int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
        int currentMinute = calendar.get(Calendar.MINUTE);

        int startTimeInMinutes = startHour * 60 + startMin;
        int endTimeInMinutes = endHour * 60 + endMin;
        int currentTimeInMinutes = currentHour * 60 + currentMinute;

        boolean isBroadcastTime;

        if (startTimeInMinutes <= endTimeInMinutes) {
            isBroadcastTime = currentTimeInMinutes >= startTimeInMinutes && currentTimeInMinutes < endTimeInMinutes;
        } else {
            isBroadcastTime = currentTimeInMinutes >= startTimeInMinutes || currentTimeInMinutes < endTimeInMinutes;
        }

        return isBroadcastTime;
    }
}
