package com.example.parchapp;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.parchapp.analytics.AnalyticsEvents;
import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.domain.model.Group;
import com.example.parchapp.domain.model.Member;
import com.example.parchapp.util.Callback;

import java.util.Collections;
import java.util.List;

/* Groups tab. Home's "View all" opens this same screen, so both show the groups the same way. */
public class GroupsActivity extends AppCompatActivity {

    private static final String SCREEN = "groups";
    private static final int MAX_AVATARS = 3;
    private static final int[] AVATAR_COLORS = {
            R.color.brand_primary, R.color.brand_indigo, R.color.brand_success, R.color.brand_purple};

    private final AnalyticsTracker tracker = AnalyticsTracker.getInstance();
    private LinearLayout groupsList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_groups);
        groupsList = findViewById(R.id.groups_list);

        UserProfile user = UserSession.getCurrentUser();
        ((TextView) findViewById(R.id.header_user_initials)).setText(user == null ? "" : user.getInitials());
        findViewById(R.id.header_search_btn).setOnClickListener(v -> toast("Search coming soon"));
        findViewById(R.id.header_notif_btn).setOnClickListener(v -> {
            tracker.trackFeature(AnalyticsEvents.FEATURE_ALERTS, SCREEN);
            startActivity(new Intent(this, AlertsActivity.class));
        });
        findViewById(R.id.header_user_initials).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

// shared bottom bar, groups is the active tab
        BottomNav.setup(this, BottomNav.Tab.GROUPS);
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadGroups();
    }

    private void loadGroups() {
        UserProfile user = UserSession.getCurrentUser();
        if (user == null) {
            bindGroups(Collections.emptyList());
            return;
        }
        Repositories.groups().getGroups(user.getId(), new Callback<List<Group>>() {
            @Override
            public void onSuccess(List<Group> groups) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                bindGroups(groups);
            }

            @Override
            public void onError(Exception error) {
                bindGroups(Collections.emptyList());
                toast("Could not load your groups");
            }
        });
    }

    private void bindGroups(List<Group> groups) {
        groupsList.removeAllViews();
        findViewById(R.id.groups_empty).setVisibility(groups.isEmpty() ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.groups_title)).setText(
                getString(R.string.your_active_groups) + " (" + groups.size() + ")");
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Group group : groups) {
            View card = inflater.inflate(R.layout.item_group_card, groupsList, false);
            bindCard(card, group);
            groupsList.addView(card);
        }
    }

    private void bindCard(View card, Group group) {
        ((TextView) card.findViewById(R.id.group_card_title)).setText(group.getName());
        ((TextView) card.findViewById(R.id.group_card_members)).setText("• " + group.getSize() + " members");
        ((TextView) card.findViewById(R.id.group_card_next)).setText(group.getPlanTitle().isEmpty()
                ? "No upcoming plan"
                : "Next: " + group.getPlanTitle());
        // Every member's schedule is stored on the group, so all of them count as synced, like in the detail screen.
        ((TextView) card.findViewById(R.id.group_card_synced)).setText(group.getSize() + " calendars synced");
        bindActivityLevel(card, group);
        bindAvatars(card.findViewById(R.id.group_card_avatars), group.getMembers());
        card.setOnClickListener(v -> {
            tracker.trackFeature(AnalyticsEvents.FEATURE_GROUPS, SCREEN);
            startActivity(new Intent(this, GroupDetailActivity.class)
                    .putExtra(GroupDetailActivity.EXTRA_GROUP_ID, group.getId()));
        });
    }

    /* The share of members who said they are going decides how active the group looks. */
    private void bindActivityLevel(View card, Group group) {
        float ratio = group.getGoingCount() / (float) Math.max(1, group.getRsvpTotal());
        String label;
        int text;
        int background;
        int dot;
        if (ratio >= 0.75f) {
            label = "Most Active";
            text = R.color.brand_success_dark;
            background = R.color.brand_success_background;
            dot = R.color.brand_success;
        } else if (ratio >= 0.4f) {
            label = "Active";
            text = R.color.brand_primary;
            background = R.color.brand_chip_background;
            dot = R.color.brand_primary;
        } else {
            label = "Low Activity";
            text = R.color.brand_slate;
            background = R.color.brand_card_stroke;
            dot = R.color.brand_border;
        }
        TextView chip = card.findViewById(R.id.group_card_activity);
        chip.setText(label);
        chip.setTextColor(getColor(text));
        chip.setBackgroundResource(R.drawable.bg_light_blue);
        chip.setBackgroundTintList(ColorStateList.valueOf(getColor(background)));
        card.findViewById(R.id.group_card_dot).setBackgroundTintList(ColorStateList.valueOf(getColor(dot)));
    }

    private void bindAvatars(LinearLayout container, List<Member> members) {
        int size = dp(30);
        int overlap = -dp(8);
        int shown = Math.min(MAX_AVATARS, members.size());
        for (int i = 0; i < shown; i++) {
            container.addView(avatar(members.get(i).getInitials(),
                    getColor(AVATAR_COLORS[i % AVATAR_COLORS.length]), android.R.color.white),
                    avatarParams(size, i == 0 ? 0 : overlap));
        }
        int remaining = members.size() - shown;
        if (remaining > 0) {
            TextView more = avatar("+" + remaining, getColor(R.color.brand_chip_background), R.color.brand_dark);
            container.addView(more, avatarParams(size, overlap));
        }
    }

    private TextView avatar(String text, int background, int textColor) {
        TextView view = new TextView(this);
        view.setGravity(Gravity.CENTER);
        view.setText(text);
        view.setTextSize(10);
        view.setTypeface(view.getTypeface(), Typeface.BOLD);
        view.setTextColor(getColor(textColor));
        view.setBackgroundResource(R.drawable.bg_oval_solid);
        view.setBackgroundTintList(ColorStateList.valueOf(background));
        return view;
    }

    private LinearLayout.LayoutParams avatarParams(int size, int marginStart) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMarginStart(marginStart);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
