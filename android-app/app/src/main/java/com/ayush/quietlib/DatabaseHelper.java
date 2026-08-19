package com.ayush.quietlib;


import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.ayush.quietlib.chat.ChatMessage;
import com.ayush.quietlib.list.Item;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "sonic.db";
    private static final int DATABASE_VERSION = 1;
    private Context context;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public void createTable(String tableName, String[] columns) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = this.getWritableDatabase();
            cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", new String[]{tableName});
            if (cursor.getCount() > 0) {
                // Log.d("DatabaseHelper", "Table " + tableName + " already exists.");
                return;
            }

            StringBuilder createTableSQL = new StringBuilder("CREATE TABLE " + tableName + " (");
            for (String column : columns) {
                createTableSQL.append(column).append(", ");
            }
            createTableSQL.setLength(createTableSQL.length() - 2);
            createTableSQL.append(")");

            db.execSQL(createTableSQL.toString());
            // Log.d("DatabaseHelper", "Table " + tableName + " created.");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            if (db != null) db.close();
        }
    }

    public long insert(String tableName, ContentValues values) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            long id = db.insert(tableName, null, values);
            if (id == -1) {
                // Log.d("DatabaseHelper", "Insert failed into " + tableName);
            } else {
                // Log.d("DatabaseHelper", "Inserted row with ID: " + id + " into " + tableName);
            }
            return id;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            if (db != null) db.close();
        }
    }

    public List<Item> getAllItems(String tableName, String type) {
        List<Item> dataList = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = this.getReadableDatabase();
            cursor = db.rawQuery("SELECT t1.url, t1.request_timestamp, t1.ready FROM requests t1 JOIN (SELECT url, MAX(request_timestamp) AS latest_timestamp FROM requests WHERE type = ? GROUP BY url) AS t2 ON t1.url = t2.url AND t1.request_timestamp = t2.latest_timestamp WHERE t1.type = ? ORDER BY t1.id DESC", new String[]{type, type});
            if (cursor.moveToFirst()) {
                do {
                    Item item = new Item(this.context, cursor.getString(0), cursor.getString(1), type, cursor.getInt(2) == 1, "");
                    dataList.add(item);
                } while (cursor.moveToNext());
            } else {
                // Log.d("DatabaseHelper", "No rows found in " + tableName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            if (db != null) db.close();
        }
        return dataList;
    }

    public List<ChatMessage> getAllChatItems() {
        List<ChatMessage> dataList = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = this.getReadableDatabase();

            cursor = db.rawQuery("SELECT * FROM requests WHERE type = 'gpt'", null);
            if (cursor.moveToFirst()) {
                do {
                    int urlIndex = cursor.getColumnIndex("url");
                    int requestTimestampIndex = cursor.getColumnIndex("request_timestamp");

                    ChatMessage item = new ChatMessage(cursor.getString(urlIndex), cursor.getString(requestTimestampIndex), true);
                    dataList.add(item);
                } while (cursor.moveToNext());
            } else {
                // Log.d("DatabaseHelper", "No rows found in requests");
            }

            if (dataList.isEmpty()) {
                return dataList;
            }

            String[] urls = new String[dataList.size()];
            for (int i = 0; i < dataList.size(); i++) {
                urls[i] = dataList.get(i).getText();
            }

            cursor = db.rawQuery("SELECT * FROM metadata WHERE url IN (" + new String(new char[urls.length - 1]).replace("\0", "?,") + "?)", urls);
            if (cursor.moveToFirst()) {
                do {
                    int checksumIndex = cursor.getColumnIndex("checksum");

                    Cursor tCursor = db.rawQuery("SELECT * FROM transmissions WHERE checksum = ?", new String[]{cursor.getString(checksumIndex)});
                    String maxFilename = "";
                    String maxTimestamp = "";
                    int maxFileSize = 0;

                    if (!tCursor.moveToFirst()) {
                        continue;
                    }

                    do {
                        int responseTimestampIndex = tCursor.getColumnIndex("timestamp");
                        int filenameIndex = tCursor.getColumnIndex("filename");

                        if (maxTimestamp.isEmpty() && maxFilename.isEmpty()) {
                            maxTimestamp = tCursor.getString(responseTimestampIndex);
                            maxFilename = tCursor.getString(filenameIndex);
                        }

                        String filename = tCursor.getString(filenameIndex);
                        long fileSize = Utils.getFileSize(this.context, filename);
                        if (fileSize > maxFileSize) {
                            maxFileSize = (int) fileSize;
                            maxFilename = filename;
                            maxTimestamp = tCursor.getString(responseTimestampIndex);
                        }
                    } while (tCursor.moveToNext());
                    tCursor.close();

                    FileInputStream fis = null;
                    try {
                        fis = this.context.openFileInput(maxFilename);
                        fis.skip(Constants.frameLength);

                        byte[] buffer = new byte[Constants.frameLength];
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            outputStream.write(buffer, 4, bytesRead - 4);
                        }

                        byte[] fullBuffer = outputStream.toByteArray();
                        byte[] truncatedBuffer = new byte[fullBuffer.length - 11];
                        System.arraycopy(fullBuffer, 0, truncatedBuffer, 0, fullBuffer.length - 11);
                        buffer = truncatedBuffer;

                        String answer = new String(buffer).trim().replaceAll("C137", "");
                        ChatMessage item = new ChatMessage(answer, maxTimestamp, false);
                        dataList.add(item);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    fis.close();
                } while (cursor.moveToNext());
            }
        } catch (FileNotFoundException e) {
            // Log.d("DatabaseHelper", "File not found.");
        } catch (Exception e) {
//            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            if (db != null) db.close();
        }

        // sort using timestamp of each item
        dataList.sort(Comparator.comparing(ChatMessage::getTime));
        return dataList;
    }

    public List<Map<String, String>> getAllMap(String tableName, String filter) {
        List<Map<String, String>> dataList = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = this.getReadableDatabase();
            cursor = db.rawQuery("SELECT * FROM " + tableName + (!filter.isEmpty() ? (" WHERE " + filter): ""), null);
            if (cursor.moveToFirst()) {
                do {
                    Map<String, String> data = new HashMap<>();
                    for (int i = 0; i < cursor.getColumnCount(); i++) {
                        data.put(cursor.getColumnName(i), cursor.getString(i));
                    }
                    dataList.add(data);
                } while (cursor.moveToNext());
            } else {
                // Log.d("DatabaseHelper", "No rows found in " + tableName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            if (db != null) db.close();
        }
        return dataList;
    }

    /*
    SELECT
    m.url,
    t.checksum,
    t.type,
    t.ready,
    t.timestamp
        FROM
            metadata m
        LEFT JOIN
            requests r ON m.url = r.url
        JOIN
            transmissions t ON m.url = t.url
        WHERE
            r.url IS NULL
            AND t.ready = 1
            AND t.timestamp = (
                SELECT MAX(timestamp)
                FROM transmissions
                WHERE url = m.url
            )
        GROUP BY
            m.url;

    */

    public List<Map<String, String>> getAll_A_NOT_IN_B(String tableA, String tableB, String query) {
        List<Map<String, String>> dataList = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = this.getReadableDatabase();
            if (query.isEmpty()) {
                cursor = db.rawQuery(
                        "SELECT m.url, m.checksum, m.type, m.ready, m.timestamp FROM metadata m WHERE m.ready = 1 AND m.timestamp = (SELECT MAX(timestamp) FROM metadata WHERE url = m.url) AND NOT EXISTS (SELECT 1 FROM requests r WHERE r.url = m.url) ORDER BY m.timestamp DESC LIMIT 100",
                        null
                );
            } else {
                String queryParam = "%" + query.replace("http://", "").replace("https://", "") + "%";
                cursor = db.rawQuery(
                        "SELECT m.url, m.checksum, m.type, m.ready, m.timestamp FROM metadata m WHERE m.ready = 1 AND m.timestamp = (SELECT MAX(timestamp) FROM metadata WHERE url = m.url) AND NOT EXISTS (SELECT 1 FROM requests r WHERE r.url = m.url) AND m.url LIKE ? ORDER BY m.timestamp DESC LIMIT 100",
                        new String[]{queryParam}
                );
            }

            if (cursor.moveToFirst()) {
                do {
                    Map<String, String> data = new HashMap<>();
                    for (int i = 0; i < cursor.getColumnCount(); i++) {
                        if (cursor.getColumnName(i).equals("ready")) {
                            data.put(cursor.getColumnName(i), cursor.getInt(i) + "");
                        } else {
                            data.put(cursor.getColumnName(i), cursor.getString(i));
                        }
                    }
                    dataList.add(data);
                } while (cursor.moveToNext());
            } else {
                // Log.d("DatabaseHelper", "No rows found in " + tableA);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            if (db != null) db.close();
        }
        return dataList;
    }

    public Map<String, String> getMapById(String tableName, String idColumnName, String idValue) {
        Map<String, String> data = null;
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = this.getReadableDatabase();
            cursor = db.rawQuery("SELECT * FROM " + tableName + " WHERE " + idColumnName + " = ? ORDER BY id DESC LIMIT 1", new String[]{idValue});
            if (cursor.moveToFirst()) {
                data = new HashMap<>();
                for (int i = 0; i < cursor.getColumnCount(); i++) {
                    data.put(cursor.getColumnName(i), cursor.getString(i));
                }
            } else {
                // Log.d("DatabaseHelper", "No row found in " + tableName + " for " + idColumnName + " = " + idValue);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            if (db != null) db.close();
        }
        return data;
    }

    public int getCount(String tableName, String whereClause, String[] selectionArgs) {
        int count = 0;
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = this.getReadableDatabase();
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + tableName + " WHERE " + whereClause, selectionArgs);
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            } else {
                // Log.d("DatabaseHelper", "No rows found in " + tableName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            if (db != null) db.close();
        }
        return count;
    }

    public int update(String tableName, ContentValues values, String whereClause, String[] whereArgs) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            int rowsAffected = db.update(tableName, values, whereClause, whereArgs);
            if (rowsAffected == 0) {
                // Log.d("DatabaseHelper", "No rows updated in " + tableName + " for condition: " + whereClause);
            } else {
                // Log.d("DatabaseHelper", "Updated " + rowsAffected + " rows in " + tableName);
            }
            return rowsAffected;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        } finally {
            if (db != null) db.close();
        }
    }

    public int getQuota() {
        SharedPreferences prefs = this.context.getSharedPreferences("com.ayush.quietlib", Context.MODE_PRIVATE);
        int quota = prefs.getInt("quota", Constants.QUOTA);
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = this.getReadableDatabase();

            long currentTime = System.currentTimeMillis();

            Calendar startCalendar = Calendar.getInstance();
            startCalendar.set(Calendar.HOUR_OF_DAY, Constants.BROADCAST_END_HOUR);
            startCalendar.set(Calendar.MINUTE, 0);
            startCalendar.set(Calendar.SECOND, 0);
            startCalendar.set(Calendar.MILLISECOND, 0);

            long startTimestamp = startCalendar.getTimeInMillis();

            Calendar endCalendar = (Calendar) startCalendar.clone();
            endCalendar.add(Calendar.DAY_OF_MONTH, 1);

            long endTimestamp = endCalendar.getTimeInMillis();

            if (currentTime < startTimestamp) {
                startCalendar.add(Calendar.DAY_OF_MONTH, -1);
                startTimestamp = startCalendar.getTimeInMillis();
                endCalendar.add(Calendar.DAY_OF_MONTH, -1);
                endTimestamp = endCalendar.getTimeInMillis();
            }

            cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM requests WHERE request_timestamp >= ? AND request_timestamp < ?",
                    new String[]{String.valueOf(startTimestamp), String.valueOf(endTimestamp)}
            );

            if (cursor.moveToFirst()) {
                quota -= cursor.getInt(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
            if (db != null) db.close();
        }
        return quota;
    }


    public void delete(String tableName, String whereClause, String[] whereArgs) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            int rowsDeleted = db.delete(tableName, whereClause, whereArgs);
            if (rowsDeleted == 0) {
                // Log.d("DatabaseHelper", "No rows deleted from " + tableName + " for condition: " + whereClause);
            } else {
                // Log.d("DatabaseHelper", "Deleted " + rowsDeleted + " rows from " + tableName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (db != null) db.close();
        }
    }
}
