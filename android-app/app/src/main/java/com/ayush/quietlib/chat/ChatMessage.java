package com.ayush.quietlib.chat;

import android.content.Context;

import com.ayush.quietlib.DatabaseHelper;
import com.ayush.quietlib.Utils;

import java.util.Map;

public class ChatMessage {
    private boolean isUser;
    private String text;
    private String time;
    public ChatMessage(String text, String time, boolean isUser) {
        this.text = text;
        this.time = time;
        this.isUser = isUser;
    }

    public String getText() {
        return this.text;
    }

    public String getTime() {
        return this.time;
    }

    public String getTimeFormatted() {
        return Utils.timeAgo(time);
    }

    public boolean getIsUser() {
        return this.isUser;
    }
}
