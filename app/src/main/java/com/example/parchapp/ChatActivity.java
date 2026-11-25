package com.example.parchapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.parchapp.data.ChatRepository;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.domain.availability.AvailabilityEngine;
import com.example.parchapp.domain.model.AlertItem;
import com.example.parchapp.domain.model.ChatMessage;
import com.example.parchapp.domain.model.Group;
import com.example.parchapp.domain.model.Member;
import com.example.parchapp.domain.model.TimeSlot;
import com.example.parchapp.util.Callback;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/* Sprint 4 view: the House Chat of a group, or the chat between me and one member (opened from Group Details).
 * The messages arrive in real time on the chat thread (ChatThread) and only the rows that changed are drawn.
 * The calendar button shares in the chat the best common free slot of today, computed by AvailabilityEngine
 * (F4) with the busy blocks of the people of this chat. */
public class ChatActivity extends AppCompatActivity {
    static final String EXTRA_GROUP_ID = "chat_group_id";
    static final String EXTRA_GROUP_NAME = "chat_group_name";
    static final String EXTRA_FRIEND_ID = "chat_friend_id";
    static final String EXTRA_FRIEND_NAME = "chat_friend_name";
    static final String EXTRA_FRIEND_INITIALS = "chat_friend_initials";

    private final ChatAdapter adapter = new ChatAdapter();
    private final ConnectivityWatcher offlineBanner = new ConnectivityWatcher(null);
    private ChatRepository chats;
    private ChatRepository.ChatRef chat;
    private ChatRepository.Registration registration;
    private UserProfile me;
    private RecyclerView list;
    private LinearLayoutManager layout;
    private EditText input;
    // null only for a direct chat opened from an alert when both people share no group
    @Nullable
    private String groupId;
    // the people of this chat with their busy blocks, for the common slot; null until the group arrives
    @Nullable
    private List<Member> people;

    static Intent forGroup(Context context, String groupId, String groupName) {
        return new Intent(context, ChatActivity.class)
                .putExtra(EXTRA_GROUP_ID, groupId)
                .putExtra(EXTRA_GROUP_NAME, groupName);
    }

    static Intent forMember(Context context, String groupId, String groupName, Member member) {
        return forGroup(context, groupId, groupName)
                .putExtra(EXTRA_FRIEND_ID, member.getId())
                .putExtra(EXTRA_FRIEND_NAME, member.getName())
                .putExtra(EXTRA_FRIEND_INITIALS, member.getDisplayInitials());
    }

    // from the notifications screen: the chat of a new message alert
    static Intent forAlert(Context context, AlertItem alert) {
        String groupId = alert.getGroupId() == null || alert.getGroupId().isEmpty() ? null : alert.getGroupId();
        Intent intent = new Intent(context, ChatActivity.class)
                .putExtra(EXTRA_GROUP_ID, groupId)
                .putExtra(EXTRA_GROUP_NAME, alert.getChatGroupName());
        if (alert.getChatFriendId() != null) {
            intent.putExtra(EXTRA_FRIEND_ID, alert.getChatFriendId())
                    .putExtra(EXTRA_FRIEND_NAME, alert.getChatFriendName())
                    .putExtra(EXTRA_FRIEND_INITIALS, alert.getChatFriendInitials());
        }
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        me = UserSession.getCurrentUser();
        chats = Repositories.chats();
        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        String friendId = getIntent().getStringExtra(EXTRA_FRIEND_ID);
        if (me == null || chats == null || (groupId == null && friendId == null)) {
            Toast.makeText(this, "The chat needs an account and a connection to ParchApp", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        chat = new ChatRepository.ChatRef(groupId, friendId);
        bindHeader(getIntent().getStringExtra(EXTRA_GROUP_NAME));

        list = findViewById(R.id.chat_list);
        layout = new LinearLayoutManager(this);
        // the newest message is at the bottom, like any chat
        layout.setStackFromEnd(true);
        list.setLayoutManager(layout);
        // micro-optimization: the list size does not depend on its rows, so a new message does not measure it again
        list.setHasFixedSize(true);
        list.setAdapter(adapter);

        input = findViewById(R.id.chat_input);
        findViewById(R.id.chat_back_button).setOnClickListener(v -> finish());
        findViewById(R.id.chat_send_button).setOnClickListener(v -> sendText());
        findViewById(R.id.chat_slot_button).setOnClickListener(v -> shareCommonSlot());
        if (groupId != null) {
            loadPeople(groupId, friendId);
        }
    }

    private void bindHeader(String groupName) {
        String group = groupName == null ? "Group" : groupName;
        TextView avatar = findViewById(R.id.chat_avatar);
        if (chat.isDirect()) {
            avatar.setText(getIntent().getStringExtra(EXTRA_FRIEND_INITIALS));
            avatar.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    ChatAdapter.avatarColor(getIntent().getStringExtra(EXTRA_FRIEND_ID))));
            ((TextView) findViewById(R.id.chat_title)).setText(getIntent().getStringExtra(EXTRA_FRIEND_NAME));
            ((TextView) findViewById(R.id.chat_subtitle)).setText(
                    groupName == null ? "Direct chat" : "Direct chat · " + groupName);
        } else {
            avatar.setText(group.isEmpty() ? "G" : group.substring(0, 1).toUpperCase());
            ((TextView) findViewById(R.id.chat_title)).setText(group);
            ((TextView) findViewById(R.id.chat_subtitle)).setText("House Chat");
        }
    }

    // the busy blocks of the people of this chat, for the common slot button
    private void loadPeople(String groupId, @Nullable String friendId) {
        Repositories.groups().getGroup(groupId, new Callback<Group>() {
            @Override
            public void onSuccess(Group group) {
                List<Member> chosen = new ArrayList<>();
                for (Member member : group.getMembers()) {
                    if (friendId == null || member.getId().equals(friendId) || member.getId().equals(me.getId())) {
                        chosen.add(member);
                    }
                }
                people = chosen;
                if (!chat.isDirect() && !isFinishing()) {
                    ((TextView) findViewById(R.id.chat_subtitle)).setText("House Chat · " + group.getSize()
                            + (group.getSize() == 1 ? " member" : " members"));
                }
            }

            @Override
            public void onError(Exception error) {
                // the chat still works, only the common slot needs the group
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (chat == null) {
            return;
        }
        offlineBanner.start(findViewById(R.id.offline_banner));
        registration = chats.listen(chat, me.getId(), new ChatRepository.MessagesListener() {
            @Override
            public void onMessages(List<ChatMessage> messages, boolean fromCache) {
                boolean atBottom = !list.canScrollVertically(1);
                findViewById(R.id.chat_empty).setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
                // the diff is computed off the main thread, then only the changed rows are drawn
                adapter.submitList(messages, () -> {
                    if (atBottom && !messages.isEmpty()) {
                        list.scrollToPosition(messages.size() - 1);
                    }
                });
            }

            @Override
            public void onFailed(Exception error) {
                Toast.makeText(ChatActivity.this, "Could not load the chat", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onStop() {
        // the listener is removed with the screen, so a closed chat does not keep receiving messages
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        offlineBanner.stop();
        super.onStop();
    }

    private void sendText() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        send(ChatMessage.KIND_TEXT, text);
        input.setText("");
    }

    // eventual connectivity: offline the message is kept on the phone, shown with a clock, and sent later by Firestore
    private void send(String kind, String text) {
        chats.send(chat, me.getId(), me.getFullName(), me.getInitials(), kind, text, error ->
                Toast.makeText(this, "Your message could not be sent", Toast.LENGTH_LONG).show());
        list.scrollToPosition(Math.max(0, adapter.getItemCount() - 1));
    }

    // F4 in the chat: the best common free slot left today for the people of this chat
    private void shareCommonSlot() {
        if (groupId == null) {
            Toast.makeText(this, "Open this chat from a group to share a common slot", Toast.LENGTH_SHORT).show();
            return;
        }
        if (people == null || people.size() < 2) {
            Toast.makeText(this, "Loading the group schedules, try again in a moment", Toast.LENGTH_SHORT).show();
            return;
        }
        Calendar now = Calendar.getInstance();
        // the slots of the engine start at 8:00, what already passed is cut: the slot starts at the next half hour
        int minuteNow = (now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE) + 29) / 30 * 30;
        // the engine computes on its own thread (parch-availability) and answers on the main thread
        new AvailabilityEngine().findCommonSlotsAsync(people, "chat", new Callback<List<TimeSlot>>() {
            @Override
            public void onSuccess(List<TimeSlot> slots) {
                for (TimeSlot slot : slots) {
                    int start = Math.max(slot.getStartMinute(), minuteNow);
                    // like the engine, a slot is only useful if at least an hour of it is left
                    if (slot.getEndMinute() - start >= 60) {
                        send(ChatMessage.KIND_SLOT, TimeSlot.formatMinute(start) + " – "
                                + TimeSlot.formatMinute(slot.getEndMinute()) + " · Today · "
                                + slot.getFreeCount() + "/" + slot.getGroupSize() + " free");
                        return;
                    }
                }
                Toast.makeText(ChatActivity.this, "No common free slot left today", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Exception error) {
                Toast.makeText(ChatActivity.this, "Could not compute the common slot", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
