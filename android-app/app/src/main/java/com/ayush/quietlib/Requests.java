package com.ayush.quietlib;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.Gson;

public class Requests {
    private static final Gson gson = new Gson();
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static final Handler handler = new Handler(Looper.getMainLooper());

    public static void post(String endpoint, HashMap<String, String> body, RequestCallback callback) {
        executorService.execute(() -> {
            try {
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String jsonInputString = gson.toJson(body);
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                String response = getResponse(conn);
                handler.post(() -> callback.onSuccess(response));
            } catch (Exception e) {
                handler.post(() -> callback.onError(e));
            }
        });
    }

    public static void get(String endpoint, RequestCallback callback) {
        executorService.execute(() -> {
            try {
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Content-Type", "application/json");

                String response = getResponse(conn);
                handler.post(() -> callback.onSuccess(response));
            } catch (Exception e) {
                handler.post(() -> callback.onError(e));
            }
        });
    }

    private static String getResponse(HttpURLConnection conn) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return response.toString();
        }
    }

    public interface RequestCallback {
        void onSuccess(String response);
        void onError(Exception e);
    }
}
