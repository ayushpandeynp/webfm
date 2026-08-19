package com.ayush.quietlib;

import android.content.ContentValues;

public class Telemetry {

    public static final String CLICKED = "clicked";
    public static final String SWIPED = "swiped";
    public static final String OPENED = "opened";

    public static void insert(DatabaseHelper dbHelper, String type, String data) {
        ContentValues insertValues = new ContentValues();
        insertValues.put("type", type);
        insertValues.put("data", data);
        insertValues.put("timestamp", String.valueOf(new java.util.Date().getTime()));

        dbHelper.insert("telemetry", insertValues);
    }
}
