package com.example.parchapp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.parchapp.data.Repositories;
import com.example.parchapp.domain.InviteCode;
import com.example.parchapp.domain.model.Group;
import com.example.parchapp.domain.model.GroupInvite;
import com.example.parchapp.util.Callback;

import java.util.ArrayList;
import java.util.List;

/* Group invitations: the owner shares a code that lasts 7 days and the friend pastes it to join.
 * Home and Groups use the same flow. The backend checks who owns the group and when the code expires. */
final class InviteActions {

    private InviteActions() {
    }

// only the owner of a group can invite, so the list has only the groups the user created
    static void share(AppCompatActivity activity, List<Group> groups, View button) {
        if (OfflineGuard.blockIfOffline(activity, activity.getString(R.string.invite_action_share))) {
            return;
        }
        UserProfile user = UserSession.getCurrentUser();
        if (user == null) {
            return;
        }
        List<Group> owned = new ArrayList<>();
        for (Group group : groups) {
            if (group.isOwner(user.getId())) {
                owned.add(group);
            }
        }
        if (owned.isEmpty()) {
            toast(activity, R.string.invite_no_groups);
        } else if (owned.size() == 1) {
            create(activity, owned.get(0), user.getId(), button);
        } else {
            String[] names = new String[owned.size()];
            for (int i = 0; i < names.length; i++) {
                names[i] = owned.get(i).getName();
            }
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.invite_choose_group)
                    .setItems(names, (dialog, which) ->
                            create(activity, owned.get(which), user.getId(), button))
                    .show();
        }
    }

    private static void create(AppCompatActivity activity, Group group, String userId, View button) {
        button.setEnabled(false);
        Repositories.groups().createGroupInvite(group.getId(), userId, new Callback<String>() {
            @Override
            public void onSuccess(String code) {
                button.setEnabled(true);
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                String message = activity.getString(R.string.invite_message, group.getName(), code);
                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.invite_friends_title), message));
                toast(activity, R.string.invite_copied);
                Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message);
                activity.startActivity(Intent.createChooser(send, activity.getString(R.string.invite_friends_title)));
            }

            @Override
            public void onError(Exception error) {
                button.setEnabled(true);
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    toast(activity, OfflineGuard.isNetworkError(error)
                            ? R.string.invite_no_connection : R.string.invite_create_error);
                }
            }
        });
    }

// the friend pastes the message or only the code
    static void join(AppCompatActivity activity) {
        EditText input = new EditText(activity);
        input.setHint(R.string.invite_paste_hint);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.invite_join_title)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.invite_continue, (dialog, which) ->
                        preview(activity, InviteCode.find(input.getText().toString())))
                .show();
    }

    private static void preview(AppCompatActivity activity, String code) {
        if (code == null) {
            toast(activity, R.string.invite_invalid);
            return;
        }
        if (OfflineGuard.blockIfOffline(activity, activity.getString(R.string.invite_action_join))) {
            return;
        }
        Repositories.groups().previewInvite(code, new Callback<GroupInvite>() {
            @Override
            public void onSuccess(GroupInvite invite) {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                if (invite.isAlreadyMember()) {
                    toast(activity, R.string.invite_already_member);
                    open(activity, invite.getGroupId());
                    return;
                }
                new AlertDialog.Builder(activity)
                        .setTitle(activity.getString(R.string.invite_confirm_title, invite.getGroupName()))
                        .setMessage(R.string.invite_confirm_body)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.invite_join_button, (dialog, which) -> confirm(activity, code))
                        .show();
            }

            @Override
            public void onError(Exception error) {
                joinFailed(activity, error);
            }
        });
    }

    private static void confirm(AppCompatActivity activity, String code) {
        UserProfile user = UserSession.getCurrentUser();
        if (user == null) {
            return;
        }
// the backend can take some seconds, so the user knows the tap worked
        toast(activity, R.string.invite_joining);
        Repositories.groups().joinWithInvite(code, user.getId(), new Callback<String>() {
            @Override
            public void onSuccess(String groupId) {
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    open(activity, groupId);
                }
            }

            @Override
            public void onError(Exception error) {
                joinFailed(activity, error);
            }
        });
    }

    private static void joinFailed(AppCompatActivity activity, Exception error) {
        if (!activity.isFinishing() && !activity.isDestroyed()) {
            toast(activity, OfflineGuard.isNetworkError(error) ? R.string.invite_no_connection : R.string.invite_invalid);
        }
    }

    private static void open(AppCompatActivity activity, String groupId) {
        activity.startActivity(new Intent(activity, GroupDetailActivity.class)
                .putExtra(GroupDetailActivity.EXTRA_GROUP_ID, groupId));
    }

    private static void toast(Context context, int message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}
