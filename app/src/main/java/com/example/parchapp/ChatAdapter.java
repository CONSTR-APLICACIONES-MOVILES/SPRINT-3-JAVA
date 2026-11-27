package com.example.parchapp;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.parchapp.domain.model.ChatMessage;

/* The rows of the chat. Micro-optimizations of the list:
 * - ListAdapter + DiffUtil: when a snapshot arrives only the rows that changed are drawn again (a new message,
 *   a pending one that reached the server), instead of rebuilding every bubble; the diff runs off the main thread.
 * - RecyclerView recycles the row views, so a long chat keeps only the rows on screen in memory.
 * - stable ids: the same message keeps its view, so the list does not blink when the snapshot arrives.
 * - the time and date texts come ready from the chat thread, binding only sets them. */
final class ChatAdapter extends ListAdapter<ChatMessage, RecyclerView.ViewHolder> {
    private static final int TYPE_DATE = 0;
    private static final int TYPE_IN = 1;
    private static final int TYPE_OUT = 2;
    private static final int TYPE_SLOT = 3;
    // one color per person, picked from the id so it is the same in every chat
    private static final int[] AVATAR_COLORS = {0xFF7C3AED, 0xFFD97706, 0xFF4F46E5, 0xFF059669, 0xFFDB2777, 0xFF0891B2};

    private static final DiffUtil.ItemCallback<ChatMessage> DIFF = new DiffUtil.ItemCallback<ChatMessage>() {
        @Override
        public boolean areItemsTheSame(@NonNull ChatMessage oldItem, @NonNull ChatMessage newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull ChatMessage oldItem, @NonNull ChatMessage newItem) {
            return oldItem.equals(newItem);
        }
    };

    ChatAdapter() {
        super(DIFF);
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getId().hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = getItem(position);
        if (message.isDate()) return TYPE_DATE;
        if (message.isSlot()) return TYPE_SLOT;
        return message.isMine() ? TYPE_OUT : TYPE_IN;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_DATE:
                return new DateHolder(inflater.inflate(R.layout.item_chat_date, parent, false));
            case TYPE_SLOT:
                return new SlotHolder(inflater.inflate(R.layout.item_chat_slot, parent, false));
            case TYPE_OUT:
                return new OutHolder(inflater.inflate(R.layout.item_chat_out, parent, false));
            default:
                return new InHolder(inflater.inflate(R.layout.item_chat_in, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = getItem(position);
        if (holder instanceof DateHolder) {
            ((DateHolder) holder).text.setText(message.getText());
        } else if (holder instanceof SlotHolder) {
            ((SlotHolder) holder).bind(message);
        } else if (holder instanceof OutHolder) {
            ((OutHolder) holder).bind(message);
        } else {
            ((InHolder) holder).bind(message);
        }
    }

    static int avatarColor(String id) {
        return AVATAR_COLORS[Math.abs(id == null ? 0 : id.hashCode() % AVATAR_COLORS.length)];
    }

    // the views of a row are found once when the row is created, not every time it is bound
    static final class DateHolder extends RecyclerView.ViewHolder {
        final TextView text;

        DateHolder(View view) {
            super(view);
            text = view.findViewById(R.id.chat_date_text);
        }
    }

    static final class InHolder extends RecyclerView.ViewHolder {
        final TextView initials;
        final TextView name;
        final TextView text;
        final TextView time;

        InHolder(View view) {
            super(view);
            initials = view.findViewById(R.id.chat_in_initials);
            name = view.findViewById(R.id.chat_in_name);
            text = view.findViewById(R.id.chat_in_text);
            time = view.findViewById(R.id.chat_in_time);
        }

        void bind(ChatMessage message) {
            initials.setText(message.getSenderInitials());
            initials.setBackgroundTintList(ColorStateList.valueOf(avatarColor(message.getSenderId())));
            name.setText(message.getSenderName());
            text.setText(message.getText());
            time.setText(message.getTimeLabel());
        }
    }

    static final class OutHolder extends RecyclerView.ViewHolder {
        final TextView text;
        final TextView time;
        final ImageView state;

        OutHolder(View view) {
            super(view);
            text = view.findViewById(R.id.chat_out_text);
            time = view.findViewById(R.id.chat_out_time);
            state = view.findViewById(R.id.chat_out_state);
        }

        void bind(ChatMessage message) {
            text.setText(message.getText());
            time.setText(message.isPending() ? "Sending…" : message.getTimeLabel());
            // eventual connectivity: a clock while the message only lives on the phone, a check once it is on the server
            state.setImageResource(message.isPending() ? R.drawable.ic_clock : R.drawable.ic_check);
            state.setImageTintList(ColorStateList.valueOf(state.getContext().getColor(
                    message.isPending() ? R.color.brand_slate : R.color.brand_primary)));
        }
    }

    static final class SlotHolder extends RecyclerView.ViewHolder {
        final TextView text;
        final TextView meta;

        SlotHolder(View view) {
            super(view);
            text = view.findViewById(R.id.chat_slot_text);
            meta = view.findViewById(R.id.chat_slot_meta);
        }

        // the slot text is "range · details", the first part goes big
        void bind(ChatMessage message) {
            String body = message.getText();
            int split = body.indexOf(" · ");
            text.setText(split < 0 ? body : body.substring(0, split));
            String details = split < 0 ? "" : body.substring(split + 3);
            String who = message.isMine() ? "You" : message.getSenderName();
            String time = message.isPending() ? "sending…" : message.getTimeLabel();
            meta.setText(details.isEmpty() ? who + " · " + time : details + " · " + who + " · " + time);
        }
    }
}
