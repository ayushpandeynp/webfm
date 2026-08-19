package com.ayush.quietlib;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.Visibility;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.ayush.quietlib.list.Adapter;
import com.ayush.quietlib.list.Item;
import com.ayush.quietlib.rfm.helper.ProgressDialog;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;

import java.io.FileNotFoundException;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;

public class Browser extends Fragment {

    private EditText urlBar;
    private ImageButton sendButton;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private Adapter adapter;

    private DatabaseHelper dbHelper;
    private List<Item> items;

    private final Pattern URL_PATTERN = Pattern.compile(
            "^(https?://)([\\w-]+\\.[\\w.-]+)(:[0-9]+)?(/.*)?$"
    );

    String receiverPhone;

    private boolean isValidURL(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            return false;
        }

        if (!URL_PATTERN.matcher(urlString).matches() || urlString.contains(" ")) {
            return false;
        }

        try {
            new URL(urlString);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private Runnable runnable;
    private Handler handler;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_browser, container, false);
        urlBar = view.findViewById(R.id.browserURLBar);
        sendButton = view.findViewById(R.id.browserSendButton);
        progressBar = view.findViewById(R.id.browserSubmitProgress);
        recyclerView = view.findViewById(R.id.browserRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setItemAnimator(null);

        sendButton.setOnClickListener(v -> {
            // check quota
            int quota = dbHelper.getQuota();

            SharedPreferences prefs = getContext().getSharedPreferences("com.ayush.quietlib", Context.MODE_PRIVATE);
            int totalQuota = prefs.getInt("quota", Constants.QUOTA);

            if (quota <= 0) {
                Toast.makeText(getContext(), "You have reached your quota limit. Please try again later.", Toast.LENGTH_LONG).show();
                return;
            }

            String url = urlBar.getText().toString().toLowerCase();
            if (url.isEmpty()) {
                Toast.makeText(getContext(), "Please enter a valid URL.", Toast.LENGTH_LONG).show();
                return;
            }

            final String finalURL = (!url.startsWith("http") ? ("http://" + url) : url).trim();

            // validation
            if (isValidURL(finalURL)) {
                // check knowledge hub, if it's there, just add it.
                List<Map<String, String>> khub_items = dbHelper.getAll_A_NOT_IN_B("metadata", "requests", "");
                for (Map<String, String> item : khub_items) {
                    String u = item.get("url");
                    String timestamp = item.get("timestamp");

                    if (u != null && u.equals(finalURL)) {
                        Date date = new Date(Long.parseLong(timestamp));
                        if (date.getDate() == new Date().getDate()) {
                            ContentValues insertValues = new ContentValues();
                            insertValues.put("url", finalURL);
                            insertValues.put("type", "img");
                            insertValues.put("request_timestamp", String.valueOf(new Date().getTime()));
                            insertValues.put("ready", 1);
                            dbHelper.insert("requests", insertValues);

                            items.clear();
                            items.addAll(dbHelper.getAllItems("requests", "img"));

                            urlBar.setText("");

                            adapter.notifyDataSetChanged();

                            return;
                        }
                    }
                }

                Utils.showAlertDialog(getContext(), "You have " + quota + "/" + totalQuota + " requests remaining. Are you sure you want to use it?", (dialog, id) -> {
                    // make a request
                    sendButton.setVisibility(View.GONE);
                    progressBar.setVisibility(View.VISIBLE);

                HashMap<String, String> body = new HashMap<>();
                body.put("text", "REQ:" + finalURL);

                    String requestTimestamp = String.valueOf(new Date().getTime());

                    try {
                        receiverPhone = prefs.getString("receiverPhone", "");

                        SMSHelper.sendSMS(getContext(), receiverPhone, "REQ:" + finalURL, new SMSHelper.SMSCallback() {
                            @Override
                            public void onSuccess() {

                                ContentValues insertValues = new ContentValues();
                                insertValues.put("url", finalURL);
                                insertValues.put("type", "img");
                                insertValues.put("request_timestamp", requestTimestamp);
                                dbHelper.insert("requests", insertValues);

                                items.clear();
                                items.addAll(dbHelper.getAllItems("requests", "img"));

                                urlBar.setText("");

                                adapter.notifyDataSetChanged();

                                sendButton.setVisibility(View.VISIBLE);
                                progressBar.setVisibility(View.GONE);
                            }

                            @Override
                            public void onError(Exception e) {
                                e.printStackTrace();

                                sendButton.setVisibility(View.VISIBLE);
                                progressBar.setVisibility(View.GONE);

                                Toast.makeText(getContext(), "Could not connect. Please try again.", Toast.LENGTH_LONG).show();
                            }
                        });


//                    Requests.post(Endpoints.NEW_SMS.getUrl(), body, new Requests.RequestCallback() {
//                        @SuppressLint("NotifyDataSetChanged")
//                        @Override
//                        public void onSuccess(String response) {
//                            Gson gson = new Gson();
//                            HashMap<String, Object> data = gson.fromJson(response, HashMap.class);
//                            if (Objects.requireNonNull(data.get("success")).toString().equalsIgnoreCase("true")) {
//                                ContentValues insertValues = new ContentValues();
//                                insertValues.put("url", finalURL);
//                                insertValues.put("type", "img");
//                                insertValues.put("request_timestamp", requestTimestamp);
//                                dbHelper.insert("requests", insertValues);
//
//                                items.clear();
//                                items.addAll(dbHelper.getAllItems("requests", "img"));
//
//                                urlBar.setText("");
//
//                                adapter.notifyDataSetChanged();
//
//                                sendButton.setVisibility(View.VISIBLE);
//                                progressBar.setVisibility(View.GONE);
//                            }
//                        }
//
//                        @Override
//                        public void onError(Exception e) {
//                            sendButton.setVisibility(View.VISIBLE);
//                            progressBar.setVisibility(View.GONE);
//
//                            if (e instanceof FileNotFoundException) {
//                                Toast.makeText(getContext(), "Please enter a valid URL.", Toast.LENGTH_LONG).show();
//                            } else {
//                                Toast.makeText(getContext(), "Could not connect. Please try again.", Toast.LENGTH_LONG).show();
//                            }
//                        }
//                    });
                    } catch (Exception e) {
                        sendButton.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.GONE);
                        e.printStackTrace();
                    }
                });

            } else {
                Toast.makeText(getContext(), "Please enter a valid URL.", Toast.LENGTH_LONG).show();
            }
        });

        dbHelper = new DatabaseHelper(getContext());

        String[] browserColumns = {
                "id INTEGER PRIMARY KEY AUTOINCREMENT",
                "url TEXT NOT NULL",
                "type TEXT NOT NULL",
                "request_timestamp INTEGER NOT NULL",
                "ready INTEGER DEFAULT 0"
        };

        dbHelper.createTable("requests", browserColumns);

        items = dbHelper.getAllItems("requests", "img");

        adapter = new Adapter(getContext(), items, "browser");
        recyclerView.setAdapter(adapter);

        handler = new Handler();
        runnable = new Runnable() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void run() {
                items.clear();
                items.addAll(dbHelper.getAllItems("requests", "img"));

                adapter.notifyDataSetChanged();
                handler.postDelayed(this, 2000);
            }
        };

        handler.postDelayed(runnable, 2000);

        return view;
    }
}
