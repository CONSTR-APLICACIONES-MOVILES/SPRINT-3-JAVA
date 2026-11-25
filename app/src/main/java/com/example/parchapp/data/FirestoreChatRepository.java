package com.example.parchapp.data;

import com.example.parchapp.domain.model.ChatMessage;
import com.example.parchapp.util.ChatThread;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/* The chats live in Firestore: groups/{groupId}/messages for the House Chat and directChats/{a_b}/messages for
 * two people, where a_b are both uids sorted. The rules only let the members (or the two people) read and write.
 * Eventual connectivity: Firestore keeps the messages on the phone, so offline the chat opens with what was saved,
 * and a message sent offline is written locally at once and sent when the network returns. */
class FirestoreChatRepository implements ChatRepository {
    // micro-optimization: only the last messages are read and kept on screen, an old chat does not load everything
    static final int LIMIT = 100;

    private final FirebaseFirestore db;
    // only used on the chat thread, so these formats are never shared between threads
    private final SimpleDateFormat time = new SimpleDateFormat("h:mm a", Locale.US);
    private final SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat dayTitle = new SimpleDateFormat("EEEE, MMMM d", Locale.US);

    FirestoreChatRepository(FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public Registration listen(ChatRef chat, String myId, MessagesListener listener) {
        Query query = messages(chat, myId).orderBy("createdAt", Query.Direction.ASCENDING).limitToLast(LIMIT);
        // the listener runs on the chat thread (ChatThread.EXECUTOR), not on the main thread
        // MetadataChanges.INCLUDE: we also hear when a pending message reaches the server, to change its icon
        ListenerRegistration registration = query.addSnapshotListener(ChatThread.EXECUTOR, MetadataChanges.INCLUDE,
                (snapshot, error) -> {
                    if (error != null || snapshot == null) {
                        ChatThread.toMain(() -> listener.onFailed(error));
                        return;
                    }
                    List<ChatMessage> rows = toRows(snapshot, myId);
                    boolean fromCache = snapshot.getMetadata().isFromCache();
                    ChatThread.toMain(() -> listener.onMessages(rows, fromCache));
                });
        return registration::remove;
    }

    @Override
    public void send(ChatRef chat, String myId, String myName, String myInitials, String kind, String text,
                     SendFailure onRejected) {
        Map<String, Object> message = new HashMap<>();
        message.put("senderId", myId);
        message.put("senderName", myName);
        message.put("senderInitials", myInitials);
        message.put("text", text);
        message.put("kind", kind);
        // the server puts the time, so a wrong clock on the phone does not change the order of the chat
        message.put("createdAt", FieldValue.serverTimestamp());
        // we do not wait for the answer: the listener already shows it as pending and then as sent
        // offline the task just waits; it only fails when the server refuses the message, then Firestore removes it
        messages(chat, myId).add(message).addOnFailureListener(error -> ChatThread.toMain(() -> onRejected.onRejected(error)));
    }

    private CollectionReference messages(ChatRef chat, String myId) {
        if (!chat.isDirect()) {
            return db.collection("groups").document(chat.groupId).collection("messages");
        }
        return db.collection("directChats").document(pairId(myId, chat.friendId)).collection("messages");
    }

    // both people get the same id whoever opens the chat first
    static String pairId(String a, String b) {
        return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
    }

    /* runs on the chat thread: turns the documents into rows and adds a date divider when the day changes */
    private List<ChatMessage> toRows(QuerySnapshot snapshot, String myId) {
        List<ChatMessage> rows = new ArrayList<>(snapshot.size() + 4);
        String today = day.format(new Date());
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        String yesterdayKey = day.format(yesterday.getTime());
        String lastDay = null;
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            // a message that is still pending has no server time yet, Firestore gives its estimate
            Timestamp createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
            Date when = createdAt == null ? new Date() : createdAt.toDate();
            String dayKey = day.format(when);
            if (!dayKey.equals(lastDay)) {
                String label = dayKey.equals(today) ? "Today" : dayKey.equals(yesterdayKey) ? "Yesterday"
                        : dayTitle.format(when);
                rows.add(ChatMessage.dateDivider(dayKey, label));
                lastDay = dayKey;
            }
            String senderId = doc.getString("senderId");
            rows.add(new ChatMessage(doc.getId(), valueOr(doc.getString("kind"), ChatMessage.KIND_TEXT), senderId,
                    valueOr(doc.getString("senderName"), "Member"), valueOr(doc.getString("senderInitials"), "?"),
                    valueOr(doc.getString("text"), ""), time.format(when), myId.equals(senderId),
                    doc.getMetadata().hasPendingWrites()));
        }
        return rows;
    }

    private static String valueOr(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
