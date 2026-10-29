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
import com.example.parchapp.data.SyncedCalendars;
import com.example.parchapp.domain.GroupPlans;
import com.example.parchapp.domain.PlanContext;
import com.example.parchapp.domain.model.Group;
import com.example.parchapp.domain.model.Member;
import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.IconButtons;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        findViewById(R.id.header_notif_btn).setOnClickListener(v ->
                startActivity(new Intent(this, AlertsActivity.class)));
        findViewById(R.id.header_user_initials).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        // bq8: groups counts as used when this screen opens (not again on rotation)
        if (savedInstanceState == null) {
            tracker.trackFeature(AnalyticsEvents.FEATURE_GROUPS, "groups");
        }
// shared bottom bar, groups is the active tab
        BottomNav.setup(this, BottomNav.Tab.GROUPS);
// below the cards, also when the user has no groups yet, so the first group can be created from here
        IconButtons.centerIconWithText(findViewById(R.id.create_group_from_list));
        findViewById(R.id.create_group_from_list).setOnClickListener(v ->
                startActivity(new Intent(this, CreateGroupActivity.class)));
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
        Map<String, View> cards = new HashMap<>();
        for (Group group : groups) {
            View card = inflater.inflate(R.layout.item_group_card, groupsList, false);
            bindCard(card, group);
            groupsList.addView(card);
            cards.put(group.getId(), card);
        }
        loadPlans(cards);
    }

// the next plan and the activity of each card come from the real plans, like group details
    private void loadPlans(Map<String, View> cards) {
        UserProfile user = UserSession.getCurrentUser();
        if (user == null || cards.isEmpty()) {
            return;
        }
        Repositories.activities().getActivities(user.getId(), new Callback<List<PlannedActivity>>() {
            @Override
            public void onSuccess(List<PlannedActivity> plans) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                Calendar now = Calendar.getInstance(PlanContext.bogota());
                for (Map.Entry<String, View> entry : cards.entrySet()) {
                    bindNextPlan(entry.getValue(), GroupPlans.nextFor(entry.getKey(), plans, now));
                    bindActivityLevel(entry.getValue(), GroupPlans.activityOf(entry.getKey(), plans));
                }
            }

// without plans we can not say what is next, so the card says there is no plan
            @Override
            public void onError(Exception error) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                for (View card : cards.values()) {
                    bindNextPlan(card, null);
                }
            }
        });
    }

    private void bindNextPlan(View card, PlannedActivity plan) {
        TextView next = card.findViewById(R.id.group_card_next);
        if (plan == null) {
            next.setText(R.string.group_no_plan);
        } else if (plan.getLocation().isEmpty()) {
            next.setText(getString(R.string.group_next_plan, plan.getTitle()));
        } else {
            next.setText(getString(R.string.group_next_plan_place, plan.getTitle(), plan.getLocation()));
        }
    }

    private void bindCard(View card, Group group) {
        ((TextView) card.findViewById(R.id.group_card_title)).setText(group.getName());
        int size = group.getSize();
        ((TextView) card.findViewById(R.id.group_card_members)).setText(
                "• " + getResources().getQuantityString(R.plurals.group_member_count, size, size));
// the plan and the activity wait for the plans, so we do not show a wrong value first
        ((TextView) card.findViewById(R.id.group_card_next)).setText("");
        card.findViewById(R.id.group_card_activity).setVisibility(View.INVISIBLE);
        bindSynced(card.findViewById(R.id.group_card_synced), group);
        bindAvatars(card.findViewById(R.id.group_card_avatars), group.getMembers());
        card.setOnClickListener(v -> startActivity(new Intent(this, GroupDetailActivity.class)
                .putExtra(GroupDetailActivity.EXTRA_GROUP_ID, group.getId())));
    }

    /* Real number of members whose calendar for today the backend knows, the same count as Group Detail. */
    private void bindSynced(TextView synced, Group group) {
        UserProfile user = UserSession.getCurrentUser();
        if (user == null) {
            synced.setText("Sync unknown");
            return;
        }
        synced.setText("Checking sync\u2026");
        java.text.SimpleDateFormat key = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
        key.setTimeZone(PlanContext.bogota());
        SyncedCalendars.count(Repositories.schedules(), key.format(new java.util.Date()), group.getMembers(),
                user.getId(), new Callback<Integer>() {
                    @Override
                    public void onSuccess(Integer known) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        int size = group.getSize();
                        synced.setText(known == size
                                ? (size == 1 ? "1 calendar synced" : size + " calendars synced")
                                : known + " of " + size + " calendars synced");
                    }

                    @Override
                    public void onError(Exception error) {
                        if (!isFinishing() && !isDestroyed()) {
                            synced.setText("Sync unknown");
                        }
                    }
                });
    }

// how many plans the group has decides how active it looks, the going answers are reset for every new plan
    private void bindActivityLevel(View card, GroupPlans.Activity level) {
        String label;
        int text;
        int background;
        int dot;
        if (level == GroupPlans.Activity.MOST_ACTIVE) {
            label = getString(R.string.group_activity_most);
            text = R.color.brand_success_dark;
            background = R.color.brand_success_background;
            dot = R.color.brand_success;
        } else if (level == GroupPlans.Activity.ACTIVE) {
            label = getString(R.string.group_activity_active);
            text = R.color.brand_primary;
            background = R.color.brand_chip_background;
            dot = R.color.brand_primary;
        } else {
            label = getString(R.string.group_activity_low);
            text = R.color.brand_slate;
            background = R.color.brand_card_stroke;
            dot = R.color.brand_border;
        }
        TextView chip = card.findViewById(R.id.group_card_activity);
        chip.setVisibility(View.VISIBLE);
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
