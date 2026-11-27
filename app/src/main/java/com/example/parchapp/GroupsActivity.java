package com.example.parchapp;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.parchapp.analytics.AnalyticsEvents;
import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.data.SyncedCalendars;
import com.example.parchapp.domain.GroupPlans;
import com.example.parchapp.domain.PlanContext;
import com.example.parchapp.domain.ResponseTimeEstimator;
import com.example.parchapp.domain.model.Group;
import com.example.parchapp.domain.model.Member;
import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.domain.model.ResponseTimeEstimate;
import com.example.parchapp.util.BackgroundThreads;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.IconButtons;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/* Groups tab. Home's "View all" opens this same screen, so both show the groups the same way. */
public class GroupsActivity extends AppCompatActivity {

    private static final String SCREEN = "groups";
    private static final int MAX_AVATARS = 3;
    private static final int[] AVATAR_COLORS = {
            R.color.brand_primary, R.color.brand_indigo, R.color.brand_success, R.color.brand_purple};

    private final AnalyticsTracker tracker = AnalyticsTracker.getInstance();
    private LinearLayout groupsList;
    // eventual connectivity: banner while offline, and the list loads again when the network returns
    private final ConnectivityWatcher connectivity = new ConnectivityWatcher(this::loadGroups);
    // the plans of the cards: saved ones at once (also offline), then fresh ones, like the Dashboard
    private final com.example.parchapp.data.DashboardLoader plansLoader = new com.example.parchapp.data.DashboardLoader();
    // micro-optimization 2: the cards are kept by group id and reused, so coming back to the screen inflates nothing
    private final Map<String, CardViews> cards = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_groups);
        groupsList = findViewById(R.id.groups_list);

        // shared top bar with the logo, search, alerts and the user initials
        TopHeader.setup(this);

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
        findViewById(R.id.join_with_invite_button).setOnClickListener(v -> InviteActions.join(this));
    }

    @Override
    protected void onStart() {
        super.onStart();
        connectivity.start(findViewById(R.id.offline_banner));
        loadGroups();
    }

    @Override
    protected void onStop() {
        connectivity.stop();
        super.onStop();
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
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                // offline Firestore answers from the phone; it only fails when nothing was ever saved here.
                // the cards already on screen stay, and the message says what happened
                if (groupsList.getChildCount() == 0) {
                    bindGroups(Collections.emptyList());
                }
                toast(OfflineGuard.failureMessage(error, "load your groups", "Could not load your groups"));
            }
        });
    }

    private void bindGroups(List<Group> groups) {
        // profiling: this section shows in the CPU profiler (System Trace) as "Groups.bindGroups"
        BackgroundThreads.beginSection("Groups.bindGroups");
        Map<String, Group> groupsById = new HashMap<>();
        try {
            findViewById(R.id.groups_empty).setVisibility(groups.isEmpty() ? View.VISIBLE : View.GONE);
            ((TextView) findViewById(R.id.groups_title)).setText(
                    getString(R.string.your_active_groups) + " (" + groups.size() + ")");
            // micro-optimization 3: today's date is built once here, not once per card
            String today = todayKey();
            Set<String> gone = new HashSet<>(cards.keySet());
            for (int i = 0; i < groups.size(); i++) {
                Group group = groups.get(i);
                gone.remove(group.getId());
                CardViews card = cards.get(group.getId());
                boolean isNew = card == null;
                if (isNew) {
                    card = new CardViews(inflateCard());
                    cards.put(group.getId(), card);
                }
                if (groupsList.getChildAt(i) != card.root) { // a card moves only when the order changed
                    groupsList.removeView(card.root);
                    groupsList.addView(card.root, i);
                }
                bindCard(card, group, isNew, today);
                groupsById.put(group.getId(), group);
            }
            for (String id : gone) {
                groupsList.removeView(cards.remove(id).root);
            }
        } finally {
            BackgroundThreads.endSection();
        }
        loadPlans(groupsById);
    }

    // micro-optimization 1: the views of a card are found once, when it is inflated, not on every bind
    private static final class CardViews {
        final View root;
        final View dot;
        final View rsvp;
        final TextView title;
        final TextView members;
        final TextView next;
        final TextView synced;
        final TextView activity;
        final TextView rsvpLabel;
        final TextView rsvpCount;
        final TextView hint;
        final ProgressBar progress;
        final LinearLayout avatars;

        CardViews(View root) {
            this.root = root;
            dot = root.findViewById(R.id.group_card_dot);
            rsvp = root.findViewById(R.id.group_card_rsvp);
            title = root.findViewById(R.id.group_card_title);
            members = root.findViewById(R.id.group_card_members);
            next = root.findViewById(R.id.group_card_next);
            synced = root.findViewById(R.id.group_card_synced);
            activity = root.findViewById(R.id.group_card_activity);
            rsvpLabel = root.findViewById(R.id.group_card_rsvp_label);
            rsvpCount = root.findViewById(R.id.group_card_rsvp_count);
            hint = root.findViewById(R.id.group_card_response_time);
            progress = root.findViewById(R.id.group_card_rsvp_progress);
            avatars = root.findViewById(R.id.group_card_avatars);
        }
    }

    private String todayKey() {
        java.text.SimpleDateFormat key = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
        key.setTimeZone(PlanContext.bogota());
        return key.format(new java.util.Date());
    }

    private View inflateCard() {
        BackgroundThreads.beginSection("Groups.inflateCard");
        try {
            return LayoutInflater.from(this).inflate(R.layout.item_group_card, groupsList, false);
        } finally {
            BackgroundThreads.endSection();
        }
    }

// the next plan and the activity of each card come from the real plans, like group details
    private void loadPlans(Map<String, Group> groupsById) {
        UserProfile user = UserSession.getCurrentUser();
        if (user == null || cards.isEmpty()) {
            return;
        }
        plansLoader.loadPlans(user.getId(), new com.example.parchapp.data.DashboardLoader.Listener<PlannedActivity>() {
            @Override
            public void onLoaded(List<PlannedActivity> plans, boolean fromCache) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                Calendar now = Calendar.getInstance(PlanContext.bogota());
                Map<String, PlannedActivity> latest = new HashMap<>();
                BackgroundThreads.beginSection("Groups.bindPlans");
                try {
                    // micro-optimization 3: one pass splits the plans by group, instead of three passes
                    // over all the plans for every card
                    Map<String, List<PlannedActivity>> byGroup = new HashMap<>();
                    for (PlannedActivity plan : plans) {
                        byGroup.computeIfAbsent(plan.getGroupId(), id -> new ArrayList<>()).add(plan);
                    }
                    for (Map.Entry<String, CardViews> entry : cards.entrySet()) {
                        List<PlannedActivity> own = byGroup.getOrDefault(entry.getKey(), Collections.emptyList());
                        bindNextPlan(entry.getValue(), GroupPlans.nextFor(entry.getKey(), own, now));
                        bindActivityLevel(entry.getValue(), GroupPlans.activityOf(entry.getKey(), own));
                        PlannedActivity newest = latestActivityFor(own);
                        if (newest != null) {
                            latest.put(entry.getKey(), newest);
                        } else {
                            // a reused card may still show the RSVP of a plan that is gone
                            entry.getValue().rsvp.setTag(null);
                            entry.getValue().rsvp.setVisibility(View.GONE);
                        }
                    }
                } finally {
                    BackgroundThreads.endSection();
                }
                Repositories.responseTimeInsights().getEstimates(new Callback<List<ResponseTimeEstimate>>() {
                    @Override
                    public void onSuccess(List<ResponseTimeEstimate> estimates) {
                        if (isFinishing() || isDestroyed()) return;
                        for (Map.Entry<String, PlannedActivity> entry : latest.entrySet()) {
                            CardViews card = cards.get(entry.getKey());
                            Group group = groupsById.get(entry.getKey());
                            if (card != null && group != null) {
                                loadActivityRsvp(card, group, entry.getValue(), estimates);
                            }
                        }
                    }

                    @Override
                    public void onError(Exception error) {
                        // BQ5 is optional context; groups and their plans remain usable without it.
                    }
                });
            }

// without plans we can not say what is next; offline the card says so instead of "no upcoming plan"
            @Override
            public void onFailed(Exception error) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                boolean offline = OfflineGuard.isNetworkError(error) || !tracker.isOnline();
                for (CardViews card : cards.values()) {
                    if (offline) {
                        card.next.setText(R.string.group_plans_offline);
                    } else {
                        bindNextPlan(card, null);
                    }
                }
            }
        });
    }

    // plans is already the list of one group
    private PlannedActivity latestActivityFor(List<PlannedActivity> plans) {
        PlannedActivity latest = null;
        for (PlannedActivity plan : plans) {
            if (latest == null || plan.getCreatedAt() > latest.getCreatedAt()) latest = plan;
        }
        return latest;
    }

    private void loadActivityRsvp(CardViews card, Group group, PlannedActivity plan,
                                  List<ResponseTimeEstimate> estimates) {
        View section = card.rsvp;
        if (plan.getId() == null || plan.getId().trim().isEmpty()) {
            section.setTag(null);
            section.setVisibility(View.GONE);
            return;
        }
        // the card is reused, so the answer is drawn only if the card still waits for this plan.
        // the RSVP that was already on screen stays until the new answer comes, so the card does not blink
        final String planId = plan.getId();
        section.setTag(planId);
        Repositories.activities().getActivityDetails(planId, new Callback<com.example.parchapp.domain.model.ActivityDetails>() {
            @Override
            public void onSuccess(com.example.parchapp.domain.model.ActivityDetails details) {
                if (isFinishing() || isDestroyed() || !planId.equals(section.getTag())) return;
                if (details == null || details.isCancelled() || details.getTotalParticipants() <= 0) {
                    section.setVisibility(View.GONE);
                    return;
                }
                int accepted = details.getAcceptedParticipants();
                int total = details.getTotalParticipants();
                card.rsvpLabel.setText("Activity RSVP");
                card.rsvpCount.setText(accepted + " of " + total + " accepted");
                ProgressBar progress = card.progress;
                progress.setProgress(Math.min(100, Math.round(100f * accepted / total)));
                ResponseTimeEstimate estimate = ResponseTimeEstimator.forGroupSize(
                        plan.getGroupSizeAtCreation() > 0 ? plan.getGroupSizeAtCreation()
                                : (group.getSize() > 0 ? group.getSize() : total), estimates);
                TextView hint = card.hint;
                hint.setVisibility(estimate == null ? View.GONE : View.VISIBLE);
                if (estimate != null) {
                    boolean complete = accepted >= total;
                    String message = estimate.getKind() == ResponseTimeEstimate.Kind.ALL_RESPONDED
                            ? "Groups of " + estimate.getGroupSize() + " usually all reply within ~"
                                    + estimate.getDurationLabel()
                            : "Members of groups of " + estimate.getGroupSize() + " usually reply in ~"
                                    + estimate.getDurationLabel();
                    String detail = null;
                    long sentAt = plan.getCreatedAt();
                    long expectedAt = sentAt > 0 ? sentAt + estimate.getDurationMillis() : 0;
                    boolean late = expectedAt > 0 && System.currentTimeMillis() > expectedAt;
                    if (!complete && estimate.getKind() == ResponseTimeEstimate.Kind.ALL_RESPONDED
                            && expectedAt > 0) {
                        if (late) {
                            detail = "Taking longer than usual — a reminder may help";
                        } else {
                            java.text.SimpleDateFormat time = new java.text.SimpleDateFormat("h:mm a", Locale.US);
                            time.setTimeZone(PlanContext.bogota());
                            detail = "Everyone should have replied by " + time.format(new java.util.Date(expectedAt));
                        }
                    }
                    hint.setText(detail == null ? message : message + "\n" + detail);
                    hint.setTextColor(getColor(late ? R.color.brand_amber : R.color.brand_slate));
                }
                section.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(Exception error) {
                // The RSVP summary is optional and the card still shows the plan.
            }
        });
    }

    private void bindNextPlan(CardViews card, PlannedActivity plan) {
        TextView next = card.next;
        if (plan == null) {
            next.setText(R.string.group_no_plan);
        } else if (plan.getLocation().isEmpty()) {
            next.setText(getString(R.string.group_next_plan, plan.getTitle()));
        } else {
            next.setText(getString(R.string.group_next_plan_place, plan.getTitle(), plan.getLocation()));
        }
    }

    private void bindCard(CardViews card, Group group, boolean isNew, String today) {
        BackgroundThreads.beginSection("Groups.bindCard");
        try {
            card.title.setText(group.getName());
            int size = group.getSize();
            card.members.setText(
                    "• " + getResources().getQuantityString(R.plurals.group_member_count, size, size));
            if (isNew) {
                // the plan and the activity wait for the plans, so we do not show a wrong value first.
                // a reused card keeps what it shows until the new plans arrive, so it does not blink
                card.next.setText("");
                card.activity.setVisibility(View.INVISIBLE);
                card.rsvp.setVisibility(View.GONE);
            }
            bindSynced(card.synced, group, isNew, today);
            card.avatars.removeAllViews();
            bindAvatars(card.avatars, group.getMembers());
            card.root.setOnClickListener(v -> startActivity(new Intent(this, GroupDetailActivity.class)
                    .putExtra(GroupDetailActivity.EXTRA_GROUP_ID, group.getId())));
        } finally {
            BackgroundThreads.endSection();
        }
    }

    /* Real number of members whose calendar for today the backend knows, the same count as Group Detail. */
    private void bindSynced(TextView synced, Group group, boolean isNew, String today) {
        UserProfile user = UserSession.getCurrentUser();
        if (user == null) {
            synced.setText("Sync unknown");
            return;
        }
        if (isNew) synced.setText("Checking sync\u2026"); // a reused card keeps its last count until the new one comes
        SyncedCalendars.count(Repositories.schedules(), today, group.getMembers(),
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
    private void bindActivityLevel(CardViews card, GroupPlans.Activity level) {
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
        TextView chip = card.activity;
        chip.setVisibility(View.VISIBLE);
        chip.setText(label);
        chip.setTextColor(getColor(text));
        chip.setBackgroundResource(R.drawable.bg_light_blue);
        chip.setBackgroundTintList(ColorStateList.valueOf(getColor(background)));
        card.dot.setBackgroundTintList(ColorStateList.valueOf(getColor(dot)));
    }

    private void bindAvatars(LinearLayout container, List<Member> members) {
        int size = dp(30);
        int overlap = -dp(8);
        int shown = Math.min(MAX_AVATARS, members.size());
        for (int i = 0; i < shown; i++) {
            container.addView(avatar(members.get(i).getDisplayInitials(),
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
