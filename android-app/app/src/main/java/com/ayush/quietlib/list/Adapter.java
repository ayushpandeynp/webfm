package com.ayush.quietlib.list;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


import com.ayush.quietlib.DatabaseHelper;
import com.ayush.quietlib.R;
import com.ayush.quietlib.Telemetry;
import com.ayush.quietlib.Utils;
import com.ayush.quietlib.Webpage;

import java.util.List;
import java.util.Objects;

public class Adapter extends RecyclerView.Adapter<Adapter.CustomViewHolder> {

    private List<Item> itemList;
    private String parent;
    private Context context;

    private DatabaseHelper dbHelper;

    public Adapter(Context context, List<Item> itemList, String parent) {
        this.context = context;
        this.itemList = itemList;
        this.dbHelper = new DatabaseHelper(context);
        this.parent = parent;
    }

    @NonNull
    @Override
    public CustomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_layout, parent, false);
        return new CustomViewHolder(view);
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    @Override
    public void onBindViewHolder(@NonNull CustomViewHolder holder, int position) {
        Item item = itemList.get(position);
        holder.title.setText(item.getTitle().toLowerCase());
        holder.description.setText(item.getDescription());
        holder.ready.setVisibility(item.isReady() ? View.VISIBLE : View.GONE);

        holder.listItem.setOnClickListener(v -> {
            item.updateChecksum();
            Telemetry.insert(dbHelper, this.parent + "_url", Telemetry.OPENED);

            Intent i = new Intent(this.context, Webpage.class);
            i.putExtra("checksum", item.getChecksum());
            this.context.startActivity(i);
        });

        if (Objects.equals(item.getType(), "gpt")) {
            holder.imgView.setImageDrawable(context.getDrawable(R.drawable.chatgpt_black));
        }
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    public static class CustomViewHolder extends RecyclerView.ViewHolder {
        TextView title, description;
        TextView ready;
        LinearLayout listItem;
        ImageView imgView;

        public CustomViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.item_title);
            description = itemView.findViewById(R.id.item_description);
            ready = itemView.findViewById(R.id.ready);
            imgView = itemView.findViewById(R.id.icon);
            listItem = itemView.findViewById(R.id.listItem);
        }
    }
}
