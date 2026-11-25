package com.example.parchapp.domain.model;

import androidx.annotation.Nullable;

import java.util.Objects;

/* One row of the chat screen: a message or the date divider before the first message of a day.
 * The texts the screen shows (time, date) are made on the chat thread, so binding a row only sets them. */
public final class ChatMessage {
    public static final String KIND_TEXT = "text";
    // a common free slot shared from the chat with the calendar button
    public static final String KIND_SLOT = "slot";
    public static final String KIND_DATE = "date";

    private final String id;
    private final String kind;
    private final String senderId;
    private final String senderName;
    private final String senderInitials;
    private final String text;
    private final String timeLabel;
    private final boolean mine;
    // written on this phone and not yet on the server (offline or on its way)
    private final boolean pending;

    public ChatMessage(String id, String kind, String senderId, String senderName, String senderInitials,
                       String text, String timeLabel, boolean mine, boolean pending) {
        this.id = id;
        this.kind = kind;
        this.senderId = senderId;
        this.senderName = senderName;
        this.senderInitials = senderInitials;
        this.text = text;
        this.timeLabel = timeLabel;
        this.mine = mine;
        this.pending = pending;
    }

    public static ChatMessage dateDivider(String dayKey, String label) {
        return new ChatMessage("date-" + dayKey, KIND_DATE, null, null, null, label, null, false, false);
    }

    public String getId() {
        return id;
    }

    public String getKind() {
        return kind;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getSenderInitials() {
        return senderInitials;
    }

    public String getText() {
        return text;
    }

    @Nullable
    public String getTimeLabel() {
        return timeLabel;
    }

    public boolean isMine() {
        return mine;
    }

    public boolean isPending() {
        return pending;
    }

    public boolean isDate() {
        return KIND_DATE.equals(kind);
    }

    public boolean isSlot() {
        return KIND_SLOT.equals(kind);
    }

    // the list compares rows with this to redraw only the ones that changed (a pending message that arrived)
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ChatMessage)) return false;
        ChatMessage that = (ChatMessage) other;
        return mine == that.mine && pending == that.pending && id.equals(that.id) && kind.equals(that.kind)
                && Objects.equals(senderName, that.senderName) && Objects.equals(text, that.text)
                && Objects.equals(timeLabel, that.timeLabel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, kind, text, timeLabel, pending);
    }
}
