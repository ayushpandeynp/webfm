package com.ayush.quietlib;

public enum Endpoints {
    NEW_SMS("/new-sms"),
    CONNECT_ACK("/connect-ack");

    private final String path;

    Endpoints(String path) {
        this.path = path;
    }

    public String getUrl() {
        return Constants.API_URL + path;
    }
}