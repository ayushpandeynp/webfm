package com.ayush.quietlib;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.ayush.quietlib.chat.ChatAdapter;
import com.ayush.quietlib.chat.ChatMessage;
import com.google.gson.Gson;

import java.io.FileNotFoundException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class ChatGPT extends Fragment {
    private EditText queryInput;
    private ImageButton querySubmitButton;
    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    DatabaseHelper dbHelper;
    ChatAdapter adapter;
    List<ChatMessage> items;


    private Runnable runnable;
    private Handler handler;

    String receiverPhone;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat_g_p_t, container, false);
        queryInput = view.findViewById(R.id.chatgptQuery);
        querySubmitButton = view.findViewById(R.id.chatgptQueryButton);
        progressBar = view.findViewById(R.id.chatgptSubmitProgress);
        recyclerView = view.findViewById(R.id.chatgptRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setItemAnimator(null);

        querySubmitButton.setOnClickListener(v -> {
            int quota = dbHelper.getQuota();

            SharedPreferences prefs = getContext().getSharedPreferences("com.ayush.quietlib", Context.MODE_PRIVATE);
            int totalQuota = prefs.getInt("quota", Constants.QUOTA);

            if (quota <= 0) {
                Toast.makeText(getContext(), "You have reached your daily quota. Please try again tomorrow.", Toast.LENGTH_LONG).show();
                return;
            }

            String query = queryInput.getText().toString().trim();
            if (query.isEmpty()) {
                return;
            }

//            HashMap<String, String> body = new HashMap<>();
//            body.put("text", "GPT:" + query);

            String requestTimestamp = String.valueOf(new Date().getTime());
            Utils.showAlertDialog(getContext(), "You have " + quota + "/" + totalQuota + " requests remaining. Are you sure you want to use it?", (dialog, id) -> {

                querySubmitButton.setVisibility(View.GONE);
                progressBar.setVisibility(View.VISIBLE);
                try {
                    receiverPhone = prefs.getString("receiverPhone", "");

                    SMSHelper.sendSMS(getContext(), receiverPhone, "GPT:" + query, new SMSHelper.SMSCallback() {
                        @Override
                        public void onSuccess() {
                            ContentValues insertValues = new ContentValues();
                            insertValues.put("url", query);
                            insertValues.put("type", "gpt");
                            insertValues.put("request_timestamp", requestTimestamp);
                            dbHelper.insert("requests", insertValues);

                            ChatMessage item = new ChatMessage(query, requestTimestamp, true);
                            items.add(item);

                            adapter.notifyItemInserted(items.size() - 1);
                            recyclerView.smoothScrollToPosition(items.size() - 1);

                            querySubmitButton.setVisibility(View.VISIBLE);
                            progressBar.setVisibility(View.GONE);
                            queryInput.setText("");
                        }


                        @Override
                        public void onError(Exception e) {
                            e.printStackTrace();
                            querySubmitButton.setVisibility(View.VISIBLE);
                            progressBar.setVisibility(View.GONE);

                            Toast.makeText(getContext(), "Could not connect. Please try again.", Toast.LENGTH_LONG).show();
                        }
                    });

//                final String q = query;
//                Requests.post(Endpoints.NEW_SMS.getUrl(), body, new Requests.RequestCallback() {
//                    @Override
//                    public void onSuccess(String response) {
//                        Gson gson = new Gson();
//                        HashMap<String, Object> data = gson.fromJson(response, HashMap.class);
//                        if (Objects.requireNonNull(data.get("success")).toString().equalsIgnoreCase("true")) {
//                            ContentValues insertValues = new ContentValues();
//                            insertValues.put("url", q);
//                            insertValues.put("type", "gpt");
//                            insertValues.put("request_timestamp", requestTimestamp);
//                            dbHelper.insert("requests", insertValues);
//
//                            ChatMessage item = new ChatMessage(q, requestTimestamp, true);
//                            items.add(item);
//
//                            adapter.notifyItemInserted(items.size() - 1);
//                            recyclerView.smoothScrollToPosition(items.size() - 1);
//
//                            querySubmitButton.setVisibility(View.VISIBLE);
//                            progressBar.setVisibility(View.GONE);
//                            queryInput.setText("");
//                        }
//                    }
//
//                    @Override
//                    public void onError(Exception e) {
//                        querySubmitButton.setVisibility(View.VISIBLE);
//                        progressBar.setVisibility(View.GONE);
//
//                        if (e instanceof FileNotFoundException) {
//                            Toast.makeText(getContext(), "Please enter a valid URL.", Toast.LENGTH_LONG).show();
//                        } else {
//                            Toast.makeText(getContext(), "Could not connect. Please try again.", Toast.LENGTH_LONG).show();
//                        }
//                    }
//                });
                } catch (Exception e) {
                    querySubmitButton.setVisibility(View.VISIBLE);
                    progressBar.setVisibility(View.GONE);
                    throw new RuntimeException(e);
                }
            });
        });

        dbHelper = new DatabaseHelper(getContext());
        items = dbHelper.getAllChatItems();
        adapter = new ChatAdapter(getContext(), items);
        recyclerView.setAdapter(adapter);

        if (!items.isEmpty()) {
            recyclerView.scrollToPosition(items.size() - 1);
        }

        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                List<ChatMessage> newItems = dbHelper.getAllChatItems();
                boolean updated = newItems.size() > items.size();

                items.clear();
                items.addAll(newItems);
                adapter.notifyDataSetChanged();

                if (updated) {
                    recyclerView.scrollToPosition(items.size() - 1);
                }
                handler.postDelayed(this, 2000);
            }
        };

        handler.postDelayed(runnable, 2000);


        return view;
    }
}
