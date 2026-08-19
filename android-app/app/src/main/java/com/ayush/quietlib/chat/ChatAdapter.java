package com.ayush.quietlib.chat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ayush.quietlib.R;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private List<ChatMessage> chatList;
    private Context context;

    public ChatAdapter(Context context, List<ChatMessage> chatList) {
        this.context = context;
        this.chatList = chatList;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.chat_item_layout, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage message = chatList.get(position);

        if (message.getIsUser()) {
            holder.userText.setText(message.getText());
            holder.userTime.setText(message.getTimeFormatted());

            holder.userLayout.setVisibility(View.VISIBLE);
            holder.gptLayout.setVisibility(View.GONE);
        }else{
            holder.gptText.setText(message.getText());
            holder.gptTime.setText(message.getTimeFormatted());

            holder.gptLayout.setVisibility(View.VISIBLE);
            holder.userLayout.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    public static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView gptText, userText, gptTime, userTime;
        LinearLayout gptLayout, userLayout;
        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            userText = itemView.findViewById(R.id.chat_question_text);
            gptText = itemView.findViewById(R.id.chat_answer_text);

            userTime = itemView.findViewById(R.id.chat_timestamp_user);
            gptTime = itemView.findViewById(R.id.chat_timestamp_bot);

            userLayout = itemView.findViewById(R.id.userMessageLayout);
            gptLayout = itemView.findViewById(R.id.responseMessageLayout);
        }
    }
}
