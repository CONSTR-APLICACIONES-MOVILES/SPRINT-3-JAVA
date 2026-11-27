package com.example.parchapp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.data.ParticipantLocalStore;
import com.example.parchapp.data.ParticipantOutboxEntity;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.domain.model.ActivityParticipant;
import com.example.parchapp.domain.model.ActivityParticipants;
import com.example.parchapp.domain.model.Group;
import com.example.parchapp.domain.model.ScheduleFriend;
import com.example.parchapp.util.Callback;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;

import java.util.Date;
import java.util.List;

/** Participants of one activity, with the same behavior as the Flutter Activity participants view. */
public class ActivityParticipantsActivity extends AppCompatActivity {
    public static final String EXTRA_ACTIVITY_ID = "extra_activity_id";

    private String activityId;
    private ActivityParticipants participants;
    private boolean busy;
    private boolean remoteLoaded;
    private ParticipantLocalStore localStore;
    private final Runnable syncListener = () -> {
        loadPending();
        load();
    };

    private TextView feedback;
    private TextView intro;
    private TextView cancelled;
    private TextView savedNotice;
    private TextView pendingTitle;
    private AppCompatButton joinButton;
    private AppCompatButton viewInvitationButton;
    private AppCompatButton inviteFriendsButton;
    private AppCompatButton reloadButton;
    private LinearLayout membersContainer;
    private LinearLayout friendsContainer;
    private LinearLayout pendingContainer;
    private View manageSection;
    private ProgressBar loading;
    private View errorBox;
    private TextView errorMessage;

    public static Intent intent(Context context, String activityId) {
        return new Intent(context, ActivityParticipantsActivity.class)
                .putExtra(EXTRA_ACTIVITY_ID, activityId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_participants);
        activityId = getIntent().getStringExtra(EXTRA_ACTIVITY_ID);

        feedback = findViewById(R.id.participants_feedback);
        intro = findViewById(R.id.participants_intro);
        cancelled = findViewById(R.id.participants_cancelled);
        savedNotice = findViewById(R.id.participants_saved_at);
        pendingTitle = findViewById(R.id.participants_pending_title);
        joinButton = findViewById(R.id.participants_join);
        viewInvitationButton = findViewById(R.id.participants_view_invitation);
        inviteFriendsButton = findViewById(R.id.participants_invite_friends);
        reloadButton = findViewById(R.id.participants_reload);
        membersContainer = findViewById(R.id.participants_members);
        friendsContainer = findViewById(R.id.participants_friends);
        pendingContainer = findViewById(R.id.participants_pending);
        manageSection = findViewById(R.id.participants_manage_section);
        loading = findViewById(R.id.participants_loading);
        errorBox = findViewById(R.id.participants_error);
        errorMessage = findViewById(R.id.participants_error_message);
        localStore = new ParticipantLocalStore(this);

        findViewById(R.id.participants_back).setOnClickListener(v -> finish());
        reloadButton.setOnClickListener(v -> load());
        findViewById(R.id.participants_retry).setOnClickListener(v -> load());
        joinButton.setOnClickListener(v -> change("join", null));
        viewInvitationButton.setOnClickListener(v ->
                startActivity(new Intent(this, AlertsActivity.class)));
        inviteFriendsButton.setOnClickListener(v -> shareGroup());

        if (activityId == null || activityId.trim().isEmpty()) {
            showError("Activity not found.");
            return;
        }
        loadLocal();
        loadPending();
        load();
    }

    @Override
    protected void onStart() {
        super.onStart();
        ParticipantSyncEvents.addListener(syncListener);
    }

    @Override
    protected void onStop() {
        ParticipantSyncEvents.removeListener(syncListener);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (localStore != null) localStore.shutdown();
        super.onDestroy();
    }

    private void loadLocal() {
        localStore.loadSnapshot(activityId, new Callback<ParticipantLocalStore.Snapshot>() {
            @Override public void onSuccess(ParticipantLocalStore.Snapshot snapshot) {
                if (isFinishing() || isDestroyed() || remoteLoaded || snapshot == null) return;
                participants = snapshot.participants;
                render();
                String when = DateFormat.getTimeFormat(ActivityParticipantsActivity.this)
                        .format(new Date(snapshot.savedAt));
                savedNotice.setText("Saved copy from " + when + " - offline");
                savedNotice.setVisibility(View.VISIBLE);
            }

            @Override public void onError(Exception error) { }
        });
    }

    private void load() {
        if (busy) return;
        setLoading(true);
        Repositories.activities().getActivityParticipants(activityId, new Callback<ActivityParticipants>() {
            @Override public void onSuccess(ActivityParticipants result) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                remoteLoaded = true;
                participants = result;
                savedNotice.setVisibility(View.GONE);
                localStore.saveSnapshot(result);
                render();
                loadPending();
            }

            @Override public void onError(Exception error) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                if (participants != null) {
                    showFeedback("Could not refresh. Showing the saved copy.");
                } else {
                    showError(OfflineGuard.failureMessage(error, "load participants",
                            "Could not load participants."));
                }
            }
        });
    }

    private void render() {
        if (participants == null) return;
        errorBox.setVisibility(View.GONE);
        intro.setText(participants.canManage()
                ? "Invite group members or manage existing participants."
                : "Join this activity to confirm your attendance.");
        cancelled.setVisibility(participants.isCancelled() ? View.VISIBLE : View.GONE);
        joinButton.setVisibility(participants.canJoin() && !participants.isCancelled()
                ? View.VISIBLE : View.GONE);
        viewInvitationButton.setVisibility(participants.isJoined() && !participants.canManage()
                && !participants.isCancelled() ? View.VISIBLE : View.GONE);

        membersContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (ActivityParticipant member : participants.getMembers()) {
            View row = inflater.inflate(R.layout.item_activity_participant, membersContainer, false);
            bindMember(row, member);
            membersContainer.addView(row);
        }
        if (participants.getMembers().isEmpty()) {
            feedback.setText("No participants yet.");
            feedback.setVisibility(View.VISIBLE);
        }

        manageSection.setVisibility(participants.canManage() ? View.VISIBLE : View.GONE);
        inviteFriendsButton.setVisibility(participants.canInviteFriends() ? View.VISIBLE : View.GONE);
        if (participants.canInviteFriends()) loadFriends();
    }

    private void bindMember(View row, ActivityParticipant member) {
        TextView initials = row.findViewById(R.id.participant_initials);
        ImageView avatar = row.findViewById(R.id.participant_avatar);
        initials.setText(initials(member.getName()));
        bindAvatar(avatar, initials, member.getPhotoUrl());
        ((TextView) row.findViewById(R.id.participant_name)).setText(member.getName());
        TextView status = row.findViewById(R.id.participant_status);
        AppCompatButton action = row.findViewById(R.id.participant_action);
        action.setVisibility(View.GONE);

        if (member.isOrganizer()) {
            status.setText("Organizer");
            return;
        }
        if (!member.isInvited()) {
            status.setText("Not invited");
            if (participants.canManage() && !participants.isCancelled()) {
                action.setText("Invite to activity");
                action.setVisibility(View.VISIBLE);
                action.setOnClickListener(v -> change("invite", member.getId()));
            }
            return;
        }

        status.setText(member.getResponseLabel());
        if (participants.canManage() && !participants.isCancelled()) {
            action.setText("Remove participant");
            action.setVisibility(View.VISIBLE);
            action.setOnClickListener(v -> confirmRemove(member));
        }
    }

    private void bindAvatar(ImageView avatar, TextView initials, String photoUrl) {
        if (photoUrl == null || photoUrl.trim().isEmpty()) {
            avatar.setVisibility(View.GONE);
            initials.setVisibility(View.VISIBLE);
            return;
        }
        avatar.setVisibility(View.VISIBLE);
        initials.setVisibility(View.GONE);
        com.squareup.picasso.RequestCreator request = Picasso.get()
                .load(photoUrl).fit().centerCrop();
        if (!AnalyticsTracker.getInstance().isOnline()) {
            request.networkPolicy(NetworkPolicy.OFFLINE);
        }
        request.into(avatar, new com.squareup.picasso.Callback() {
            @Override public void onSuccess() {
                avatar.setVisibility(View.VISIBLE);
                initials.setVisibility(View.GONE);
            }

            @Override public void onError(Exception error) {
                avatar.setVisibility(View.GONE);
                initials.setVisibility(View.VISIBLE);
            }
        });
    }

    private static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "";
        String[] words = name.trim().split("\\s+");
        return (words[0].substring(0, 1) + (words.length > 1
                ? words[words.length - 1].substring(0, 1) : "")).toUpperCase();
    }

    private void confirmRemove(ActivityParticipant member) {
        new AlertDialog.Builder(this)
                .setTitle("Remove participant?")
                .setMessage("Their activity invitation will be removed. They will remain in the group.")
                .setNegativeButton("Keep participant", null)
                .setPositiveButton("Remove", (dialog, which) -> change("remove", member.getId()))
                .show();
    }

    private void change(String action, String userId) {
        if (busy || participants == null) return;
        String label = "join".equals(action) ? "join the activity"
                : "invite".equals(action) ? "invite a participant" : "remove a participant";
        String clientUserId = currentUserId();
        if (clientUserId == null) {
            Toast.makeText(this, "Sign in to continue.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!AnalyticsTracker.getInstance().isOnline()) {
            enqueue(action, userId, clientUserId);
            return;
        }
        setBusy(true);
        Repositories.activities().changeActivityParticipants(activityId, action, userId,
                participants.getVersion(), clientUserId, new Callback<Void>() {
                    @Override public void onSuccess(Void ignored) {
                        if (isFinishing() || isDestroyed()) return;
                        setBusy(false);
                        showFeedback("Saved.");
                        load();
                    }

                    @Override public void onError(Exception error) {
                        if (isFinishing() || isDestroyed()) return;
                        setBusy(false);
                        if (OfflineGuard.isNetworkError(error)
                                || !AnalyticsTracker.getInstance().isOnline()) {
                            enqueue(action, userId, clientUserId);
                        } else {
                            showFeedback(OfflineGuard.failureMessage(error, label,
                                    "Could not update participants."));
                        }
                    }
                });
    }

    private void enqueue(String action, String userId, String clientUserId) {
        if (participants == null) return;
        String version = participants.getVersion();
        setBusy(true);
        localStore.enqueue(activityId, action, userId, version, new Callback<Long>() {
            @Override public void onSuccess(Long id) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                ParticipantActionWorker.enqueue(ActivityParticipantsActivity.this, id, activityId,
                        action, userId, version, clientUserId);
                showFeedback("Saved here. It will sync when the connection returns.");
                loadPending();
            }

            @Override public void onError(Exception error) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                showFeedback("Could not save the action on this device.");
            }
        });
    }

    private void loadPending() {
        localStore.loadPending(activityId, new Callback<List<ParticipantOutboxEntity>>() {
            @Override public void onSuccess(List<ParticipantOutboxEntity> pending) {
                if (isFinishing() || isDestroyed()) return;
                pendingContainer.removeAllViews();
                pendingTitle.setVisibility(pending.isEmpty() ? View.GONE : View.VISIBLE);
                pendingContainer.setVisibility(pending.isEmpty() ? View.GONE : View.VISIBLE);
                for (ParticipantOutboxEntity entry : pending) {
                    View row = LayoutInflater.from(ActivityParticipantsActivity.this)
                            .inflate(R.layout.item_activity_pending, pendingContainer, false);
                    TextView text = row.findViewById(R.id.pending_text);
                    StringBuilder message = new StringBuilder(actionLabel(entry.action))
                            .append(" - ").append(statusLabel(entry.status));
                    if (entry.error != null && !entry.error.isEmpty()) {
                        message.append(" - ").append(entry.error);
                    }
                    text.setText(message.toString());
                    AppCompatButton action = row.findViewById(R.id.pending_action);
                    if (ParticipantOutboxEntity.FAILED.equals(entry.status)) {
                        action.setText("Retry");
                        action.setVisibility(View.VISIBLE);
                        action.setOnClickListener(v -> {
                            localStore.delete(entry.id);
                            change(entry.action, entry.userId);
                        });
                    } else if (ParticipantOutboxEntity.CONFLICT.equals(entry.status)) {
                        action.setText("Reload");
                        action.setVisibility(View.VISIBLE);
                        action.setOnClickListener(v -> load());
                    }
                    pendingContainer.addView(row);
                }
            }

            @Override public void onError(Exception error) { }
        });
    }

    private static String actionLabel(String action) {
        if ("join".equals(action)) return "Join activity";
        if ("invite".equals(action)) return "Invite participant";
        return "Remove participant";
    }

    private static String statusLabel(String status) {
        if (ParticipantOutboxEntity.SYNCING.equals(status)) return "syncing";
        if (ParticipantOutboxEntity.FAILED.equals(status)) return "failed";
        if (ParticipantOutboxEntity.CONFLICT.equals(status)) return "reload needed";
        return "waiting for connection";
    }

    private void loadFriends() {
        friendsContainer.removeAllViews();
        Repositories.schedules().getFriends(new Callback<List<ScheduleFriend>>() {
            @Override public void onSuccess(List<ScheduleFriend> friends) {
                if (isFinishing() || isDestroyed() || participants == null) return;
                LayoutInflater inflater = LayoutInflater.from(ActivityParticipantsActivity.this);
                for (ScheduleFriend friend : friends) {
                    if (participants.containsMember(friend.getId())) continue;
                    View row = inflater.inflate(R.layout.item_activity_friend, friendsContainer, false);
                    ((TextView) row.findViewById(R.id.friend_name)).setText(friend.getName());
                    AppCompatButton action = row.findViewById(R.id.friend_action);
                    action.setOnClickListener(v -> inviteFriend(friend, action));
                    friendsContainer.addView(row);
                }
            }

            @Override public void onError(Exception error) {
                if (isFinishing() || isDestroyed()) return;
                showFeedback(OfflineGuard.failureMessage(error, "load friends",
                        "Could not load friends."));
            }
        });
    }

    private void inviteFriend(ScheduleFriend friend, AppCompatButton action) {
        if (busy || participants == null) return;
        if (OfflineGuard.blockIfOffline(this, "invite a friend")) return;
        String clientUserId = currentUserId();
        if (clientUserId == null) {
            Toast.makeText(this, "Sign in to continue.", Toast.LENGTH_SHORT).show();
            return;
        }
        action.setEnabled(false);
        Repositories.activities().inviteGroupFriend(participants.getGroupId(), friend.getId(),
                clientUserId, new Callback<Void>() {
                    @Override public void onSuccess(Void ignored) {
                        if (isFinishing() || isDestroyed()) return;
                        action.setText("Sent");
                        showFeedback("Invitation sent. They must accept before joining the activity.");
                    }

                    @Override public void onError(Exception error) {
                        if (isFinishing() || isDestroyed()) return;
                        action.setEnabled(true);
                        showFeedback(OfflineGuard.failureMessage(error, "invite a friend",
                                "Could not send the invitation."));
                    }
                });
    }

    private void shareGroup() {
        if (participants == null) return;
        Repositories.groups().getGroup(participants.getGroupId(), new Callback<Group>() {
            @Override public void onSuccess(Group group) {
                if (isFinishing() || isDestroyed()) return;
                InviteActions.shareGroup(ActivityParticipantsActivity.this, group, inviteFriendsButton);
            }

            @Override public void onError(Exception error) {
                if (isFinishing() || isDestroyed()) return;
                showFeedback(OfflineGuard.failureMessage(error, "open the group",
                        "Could not open the group."));
            }
        });
    }

    private String currentUserId() {
        UserProfile user = UserSession.getCurrentUser();
        return user == null ? null : user.getId();
    }

    private void setBusy(boolean value) {
        busy = value;
        reloadButton.setEnabled(!value);
        joinButton.setEnabled(!value);
        viewInvitationButton.setEnabled(!value);
        inviteFriendsButton.setEnabled(!value);
    }

    private void setLoading(boolean visible) {
        loading.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) errorBox.setVisibility(View.GONE);
    }

    private void showError(String message) {
        errorMessage.setText(message);
        errorBox.setVisibility(View.VISIBLE);
        manageSection.setVisibility(View.GONE);
    }

    private void showFeedback(String message) {
        feedback.setText(message);
        feedback.setVisibility(View.VISIBLE);
    }
}
