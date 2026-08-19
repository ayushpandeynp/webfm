package com.ayush.quietlib;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.ayush.quietlib.list.Adapter;
import com.ayush.quietlib.list.Item;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class KnowledgeHub extends Fragment {
    EditText searchBar;
    RecyclerView recyclerView;
    DatabaseHelper dbHelper;
    Adapter adapter;
    Handler listHandler;
    Runnable listRunnable;

    List<Item> items;

    private boolean searching;

    private Handler searchHandler = new Handler();
    private Runnable searchRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_knowledge_hub, container, false);

        searchBar = view.findViewById(R.id.khubSearchBar);
        recyclerView = view.findViewById(R.id.khubRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setItemAnimator(null);

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = s.toString();
                if (text.isEmpty()) {
                    searching = false;
                }

                searching = true;
                if (text.equals("exportDBSonic")) {
                    Utils.exportDatabase(getContext(), "sonic.db");
                    Toast.makeText(getContext(), "Database exported", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (text.equals("experiment")) {
                    Intent i = new Intent(getContext(), Settings.class);
                    startActivity(i);
                }

                if (text.startsWith("rec") && text.endsWith("set")) {
                    String[] parts = text.split(" ");
                    if (parts.length == 3) {
                        try {
                            SharedPreferences prefs = getContext().getSharedPreferences("com.ayush.quietlib", Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString("receiverPhone", parts[1]);
                            editor.apply();
                            Toast.makeText(getContext(), "Receiver set to " + parts[1], Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                        }
                    }
                    return;
                }
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
                searchRunnable = () -> {
                    items = getItems(text);
                    adapter = new Adapter(getContext(), items, "knowledge_hub");
                    recyclerView.setAdapter(adapter);
                };
                searchHandler.postDelayed(searchRunnable, 300);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        dbHelper = new DatabaseHelper(getContext());
        items = this.getItems("");
        adapter = new Adapter(getContext(), items, "knowledge_hub");

        recyclerView.setAdapter(adapter);

        listHandler = new Handler();
        listRunnable = new Runnable() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void run() {
                if (!searching) {
                    List<Item> newItems = getItems("");
                    if (!newItems.equals(items)) {
                        Log.d("KnowledgeHub", "Updating list");
                        items.clear();
                        items.addAll(newItems);
                        adapter.notifyDataSetChanged();
                    }
                }

                listHandler.postDelayed(this, 2000);
            }
        };

        listHandler.postDelayed(listRunnable, 2000);

        return view;
    }

    private List<Item> getItems(String query) {
        List<Map<String, String>> res = dbHelper.getAll_A_NOT_IN_B("metadata", "requests", query);
        return res.stream()
                .map(map -> new Item(
                        getContext(),
                        map.get("url"),
                        map.get("timestamp"),
                        map.get("type"),
                        Objects.equals(map.get("ready"), "1"),
                        map.get("checksum")
                ))
                .sorted(Comparator.comparing(Item::getTimestamp, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }
}
