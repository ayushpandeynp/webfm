package com.ayush.quietlib.list;

import android.content.Context;
import android.util.Log;

import com.ayush.quietlib.DatabaseHelper;
import com.ayush.quietlib.Utils;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class Item {
    private String title;
    private String checksum;
    private String timestamp;
    private String type;
    private boolean ready;
    private DatabaseHelper dbHelper;
    private final int ELLIPSIS_LENGTH = 30;

    public Item(Context context, String url, String timestamp, String type, boolean ready, String checksum) {
        this.type = type;
        this.ready = ready;

        this.title = url;


        this.timestamp = timestamp;

        this.dbHelper = new DatabaseHelper(context);

        if (!checksum.isEmpty()) {
            this.checksum = checksum;
        } else {
            this.checksum = null;
            this.updateChecksum();
        }
    }

    public void updateChecksum() {
        if (this.title != null) {
            Map<String, String> metadata = dbHelper.getMapById("metadata", "url", this.title);
            if (metadata != null) {
                this.checksum = metadata.get("checksum");
            }
        }
    }

    public String getTitle() {
        return this.title.length() > ELLIPSIS_LENGTH ?
                this.title.substring(0, ELLIPSIS_LENGTH) + "..." : this.title
                ;
    }

    public String getDescription() {
        return this.updatedDescription();
    }

    private String updatedDescription() {
        return Utils.timeAgo(this.timestamp);
    }

    public String getChecksum() {
        return this.checksum;
    }

    public boolean isReady() {
        return this.ready;
    }

    public String getType() {
        return this.type;
    }

    public String getTimestamp() {
        return this.timestamp;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Item item = (Item) obj;
        return ready == item.ready &&
                Objects.equals(title, item.title) &&
                Objects.equals(timestamp, item.timestamp) &&
                Objects.equals(type, item.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, timestamp, type, ready);
    }
}
