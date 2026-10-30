package com.example.parchapp;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.analytics.ConfirmationTelemetry;
import com.example.parchapp.data.DashboardLoader;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.data.TravelTimeService;
import com.example.parchapp.domain.PlanContext;
import com.example.parchapp.domain.StatusService;
import com.example.parchapp.domain.model.Group;
import com.example.parchapp.domain.model.Member;
import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.util.Callback;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity implements StatusService.Listener {

    private static final int LOCATION_REQUEST = 41;

    private TextView displayFreeUntil;
    // f6: one retry when a new account has no groups yet
    private static final long GROUPS_RETRY_MS = 4000;
    private boolean retriedGroups;
    private TextView displayNextActivity;
    private final OfflineBannerController offlineBanner = new OfflineBannerController();
    private final DashboardLoader dashboardLoader = new DashboardLoader();
    // eventual connectivity (#43): when the network comes back the saved data is replaced by fresh data, without reopening
    private boolean wasOnline = true;
    private final AnalyticsTracker.ConnectivityListener reconnectRefresh = online ->
            findViewById(R.id.offline_banner).post(() -> {
                boolean cameBack = online && !wasOnline;
                wasOnline = online;
                if (cameBack && !isFinishing() && !isDestroyed()) {
                    loadDashboard();
                }
            });
    // f6: what the dashboard read from firestore, the cards are drawn from these lists
    private final List<Group> loadedGroups = new ArrayList<>();
    private final List<PlannedActivity> loadedActivities = new ArrayList<>();
    private PlannedActivity latestActivity;
    private TravelTimeService travelTime;
    private boolean askedLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        bindUser(UserSession.getCurrentUser());
        travelTime = new TravelTimeService(this);

        displayFreeUntil = findViewById(R.id.display_free_until);
        displayNextActivity = findViewById(R.id.display_next_activity);

        // shared top bar with the logo, search, alerts and the user initials
        TopHeader.setup(this);

        // invite links are not built yet: the box says so and the button looks disabled
        View copyLink = findViewById(R.id.copy_link_button);
        copyLink.setEnabled(false);
        copyLink.setAlpha(0.5f);
        findViewById(R.id.btn_edit_free_until).setOnClickListener(v -> openQuickStatusSheet());
        findViewById(R.id.btn_edit_next_activity).setOnClickListener(v -> openQuickStatusSheet());
        // f1: tapping the status opens Set Availability Status, saving there updates this card right away
        findViewById(R.id.status_title).setOnClickListener(v ->
                new AvailabilityStatusSheet().show(getSupportFragmentManager(), "availability_status"));
        // f4: the compare button opens the best-slot recommender for my first group
        findViewById(R.id.compare_button).setOnClickListener(v -> openCompareAvailability());
        findViewById(R.id.calendar_button).setOnClickListener(v -> {
            startActivity(new Intent(this, ScheduleActivity.class));
        });
// new activity opens the same form of schedule, from home there is no group yet so the user picks one
// when the form closes, onStart loads the plans again and the new plan shows on this screen
        findViewById(R.id.new_activity_button).setOnClickListener(v -> {
            if (getSupportFragmentManager().findFragmentByTag("create_activity") == null) {
                new CreateActivitySheet()
                        .withEntryPoint(com.example.parchapp.analytics.AnalyticsEvents.ENTRY_DASHBOARD)
                        .show(getSupportFragmentManager(), "create_activity");
            }
        });

        findViewById(R.id.plan_card_study).setOnClickListener(v -> openPlanDetails());
        findViewById(R.id.plan_details_button).setOnClickListener(v -> openPlanDetails());
        findViewById(R.id.dashboard_suggested_times_button).setOnClickListener(v -> openSuggestedTimes());

        findViewById(R.id.going_button).setEnabled(false);
        findViewById(R.id.maybe_button).setEnabled(false);
        findViewById(R.id.group_card_eng).setOnClickListener(v -> openGroupByIndex(0));
        findViewById(R.id.roomies_group_card).setOnClickListener(v -> openGroupByIndex(1));
// view all and the groups tab open the same groups screen
        findViewById(R.id.view_all_groups_button).setOnClickListener(v -> openGroups());
// shared bottom bar, home is the active tab
        BottomNav.setup(this, BottomNav.Tab.HOME);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // the cards stay hidden until firestore answers, so we never show made up data
        hideGroupCards();
        findViewById(R.id.plan_card_study).setVisibility(View.GONE);
        findViewById(R.id.plans_empty).setVisibility(View.GONE);
        loadDashboard();
        StatusService status = Repositories.status();
        status.addListener(this);
        onStatusChanged(status);
        offlineBanner.start(findViewById(R.id.offline_banner));
        wasOnline = AnalyticsTracker.getInstance().isOnline();
        AnalyticsTracker.getInstance().addConnectivityListener(reconnectRefresh);
    }

    @Override
    protected void onStop() {
        AnalyticsTracker.getInstance().removeConnectivityListener(reconnectRefresh);
        offlineBanner.stop();
        Repositories.status().removeListener(this);
        super.onStop();
    }

    @Override
    public void onStatusChanged(StatusService status) {
        // f1: the live status card. StatusService notifies every change, so an accepted commuting suggestion shows up here without reopening the screen
        String availability = status.getAvailability();
        boolean free = "Available now".equalsIgnoreCase(availability);
        ((TextView) findViewById(R.id.status_title)).setText(free ? getString(R.string.available_now) : availability);
        ((TextView) findViewById(R.id.status_source_chip)).setText(
                status.isFromMotion() ? "◎ Motion sensor" : "● Live Sync");
        ((TextView) findViewById(R.id.status_header_chip)).setText(
                free ? "● FREE" : "● " + availability.toUpperCase(Locale.ROOT));
        // a new user has no quick status yet, so the box invites to set it instead of staying blank
        String freeUntil = status.getFreeUntil();
        displayFreeUntil.setText(freeUntil.isEmpty() ? getString(R.string.free_until_not_set) : freeUntil);
        // the next activity comes from my plans when there is one, the quick status is the fallback
        if (latestActivity == null) {
            displayNextActivity.setText(status.getNextActivity());
        }
    }

    private void openQuickStatusSheet() {
        QuickStatusSheet.newInstance((freeUntil, nextActivity) -> {
            Repositories.status().updateQuickStatus(freeUntil, nextActivity);
            toast("Status updated");
        }).show(getSupportFragmentManager(), "quick_status");
    }

    // ---------- F6: groups and today's plans from Firestore ----------

    private String currentUserId() {
        UserProfile user = UserSession.getCurrentUser();
        return user == null ? null : user.getId();
    }

    // f6 and multithreading (#34): groups and plans load at the same time on worker threads, each one is drawn when it arrives
    private void loadDashboard() {
        String userId = currentUserId();
        if (userId == null) {
            ((TextView) findViewById(R.id.active_plans_text)).setText("");
            return;
        }
        dashboardLoader.load(userId, new DashboardLoader.Listener<Group>() {
            @Override
            public void onLoaded(List<Group> groups, boolean fromCache) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                loadedGroups.clear();
                loadedGroups.addAll(groups);
                bindGroups();
                // a brand new account is added to the demo groups by the backend a moment after sign up, so we look again once
                if (!fromCache && groups.isEmpty() && !retriedGroups) {
                    retriedGroups = true;
                    findViewById(R.id.groups_empty_dash).postDelayed(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            loadDashboard();
                        }
                    }, GROUPS_RETRY_MS);
                }
            }

            @Override
            public void onFailed(Exception error) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                hideGroupCards();
                TextView empty = findViewById(R.id.groups_empty_dash);
                empty.setText(R.string.groups_load_error);
                empty.setVisibility(View.VISIBLE);
            }
        }, new DashboardLoader.Listener<PlannedActivity>() {
            @Override
            public void onLoaded(List<PlannedActivity> activities, boolean fromCache) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                loadedActivities.clear();
                loadedActivities.addAll(activities);
                // bq9: plans confirmed on the backend are logged once as activity_confirmed, only from fresh data
                if (!fromCache) {
                    ConfirmationTelemetry.logNewlyConfirmed(DashboardActivity.this, activities);
                }
                bindActivities();
            }

            @Override
            public void onFailed(Exception error) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                findViewById(R.id.plan_card_study).setVisibility(View.GONE);
                findViewById(R.id.plans_empty).setVisibility(View.GONE);
                ((TextView) findViewById(R.id.active_plans_text)).setText("Could not load your plans");
            }
        });
    }

    private void bindActivities() {
        Calendar now = Calendar.getInstance(PlanContext.bogota());
        int nowMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int today = PlanContext.countToday(loadedActivities, now);
        ((TextView) findViewById(R.id.active_plans_text)).setText(today == 1
                ? "You have 1 active plan today" : "You have " + today + " active plans today");

        // the card shows the next plan that has not ended, or the newest one when nothing is coming
        PlannedActivity next = PlanContext.nextPlan(loadedActivities, now, nowMinute);
        PlannedActivity shown = next != null ? next : loadedActivities.isEmpty() ? null : loadedActivities.get(0);
        displayNextActivity.setText(next != null ? next.getTitle() : "No upcoming plans");
        latestActivity = shown;
        findViewById(R.id.plans_empty).setVisibility(shown == null ? View.VISIBLE : View.GONE);
        if (shown == null) {
            findViewById(R.id.plan_card_study).setVisibility(View.GONE);
            return;
        }
        findViewById(R.id.plan_card_study).setVisibility(View.VISIBLE);
        View suggestedTimes = findViewById(R.id.dashboard_suggested_times_button);
        suggestedTimes.setVisibility(shown.getId() == null || shown.getId().trim().isEmpty()
                ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.plan_status_text)).setText(shown.getStatus());
        ((TextView) findViewById(R.id.plan_title_text)).setText(shown.getTitle());
        ((TextView) findViewById(R.id.plan_time_text)).setText(joinNonEmpty(shown.getDate(), shown.getTime()));
        ((TextView) findViewById(R.id.plan_location_text)).setText(
                shown.getLocation().isEmpty() ? "Location not specified" : shown.getLocation());
        findViewById(R.id.plan_details_button).setEnabled(true);
        bindPlanAvatars(shown);
        updatePlanContext(shown);
    }

    // ---------- F3: context-aware plan card (location + current time) ----------

    private void updatePlanContext(PlannedActivity activity) {
        TextView travel = findViewById(R.id.plan_travel_text);
        travel.setVisibility(View.VISIBLE);
        findViewById(R.id.plan_late_warning).setVisibility(View.GONE);
        travel.setText("\u279C  Finding your travel time\u2026");
        travelTime.travelTo(activity, new TravelTimeService.Listener() {
            @Override
            public void onTravel(TravelTimeService.Result result) {
                // the answer can come after the card already shows another plan, then we ignore it
                if (isFinishing() || isDestroyed() || activity != latestActivity) {
                    return;
                }
                travel.setText(TravelTimeService.describe(result));
                findViewById(R.id.plan_late_warning).setVisibility(result.late ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onProblem(TravelTimeService.Problem problem) {
                if (!isFinishing() && !isDestroyed() && activity == latestActivity) {
                    // Ask only after TravelTimeService has had a chance to serve a cached Maps result.
                    if (problem == TravelTimeService.Problem.NO_PERMISSION && !askedLocation) {
                        askedLocation = true;
                        ActivityCompat.requestPermissions(DashboardActivity.this, new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
                    }
                    travel.setText(TravelTimeService.describe(problem));
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST && latestActivity != null) {
            updatePlanContext(latestActivity);
        }
    }

    /* The people of the plan's group, from the group document: up to three initials and "+N". */
    private void bindPlanAvatars(PlannedActivity activity) {
        int[] ids = {R.id.plan_avatar_1, R.id.plan_avatar_2, R.id.plan_avatar_3};
        Group group = groupById(activity.getGroupId());
        List<Member> members = group == null ? new ArrayList<>() : group.getMembers();
        for (int i = 0; i < ids.length; i++) {
            TextView avatar = findViewById(ids[i]);
            if (i < members.size()) {
                avatar.setVisibility(View.VISIBLE);
                avatar.setText(members.get(i).getInitials());
            } else {
                avatar.setVisibility(View.GONE);
            }
        }
        TextView more = findViewById(R.id.plan_avatar_more);
        more.setVisibility(members.size() > ids.length ? View.VISIBLE : View.GONE);
        more.setText("+" + (members.size() - ids.length));
    }

    private Group groupById(String id) {
        for (Group group : loadedGroups) {
            if (group.getId().equals(id)) {
                return group;
            }
        }
        return null;
    }

    private void bindGroups() {
        bindGroupCard(0, R.id.group_card_eng_title, R.id.group_card_eng_subtitle,
                R.id.group_card_eng_icon, R.id.group_card_eng);
        bindGroupCard(1, R.id.group_card_roomies_title, R.id.group_card_roomies_subtitle,
                R.id.group_card_roomies_icon, R.id.roomies_group_card);
        TextView empty = findViewById(R.id.groups_empty_dash);
        empty.setText(R.string.no_groups_dashboard);
        empty.setVisibility(loadedGroups.isEmpty() ? View.VISIBLE : View.GONE);
        TextView viewAll = findViewById(R.id.view_all_groups_button);
// the count comes from the real groups, not a fixed number
        viewAll.setText(getString(R.string.view_all_groups, loadedGroups.size()));
        viewAll.setVisibility(loadedGroups.isEmpty() ? View.GONE : View.VISIBLE);
        viewAll.setEnabled(!loadedGroups.isEmpty());
        if (latestActivity != null) {
            bindPlanAvatars(latestActivity);
        }
    }

    private void bindGroupCard(int index, int titleId, int subtitleId, int iconId, int cardId) {
        View card = findViewById(cardId);
        if (index >= loadedGroups.size()) {
            card.setVisibility(View.GONE);
            return;
        }
        Group group = loadedGroups.get(index);
        card.setVisibility(View.VISIBLE);
        card.setEnabled(true);
        ((TextView) findViewById(titleId)).setText(group.getName());
        int size = group.getSize();
        ((TextView) findViewById(subtitleId)).setText(size == 1 ? "1 member" : size + " members");
// the cards show only the name, without the emoji of the group
        findViewById(iconId).setVisibility(View.GONE);
    }

    private void hideGroupCards() {
        findViewById(R.id.group_card_eng).setVisibility(View.GONE);
        findViewById(R.id.roomies_group_card).setVisibility(View.GONE);
        findViewById(R.id.plan_card_rsvp).setVisibility(View.GONE);
        findViewById(R.id.view_all_groups_button).setVisibility(View.GONE);
        findViewById(R.id.groups_empty_dash).setVisibility(View.GONE);
    }

    private void openGroupByIndex(int index) {
        if (index >= loadedGroups.size()) {
            toast("No group is available");
            return;
        }
        startActivity(new Intent(this, GroupDetailActivity.class)
                .putExtra(GroupDetailActivity.EXTRA_GROUP_ID, loadedGroups.get(index).getId()));
    }

    private void openGroups() {
        startActivity(new Intent(this, GroupsActivity.class));
    }

    // f4: the full Compare Availability screen, starting on my first group; it handles having no groups
    private void openCompareAvailability() {
        startActivity(CompareAvailabilityActivity.intent(this,
                loadedGroups.isEmpty() ? null : loadedGroups.get(0).getId()));
    }

    private void openPlanDetails() {
        if (latestActivity == null) {
            toast("No activity data is available");
            return;
        }
        PlanDetailsSheet.newInstance(latestActivity.getTitle(), latestActivity.getStatus(),
                latestActivity.getDate(), latestActivity.getTime(), latestActivity.getLocation())
                .show(getSupportFragmentManager(), "plan_details");
    }

    private void openSuggestedTimes() {
        if (latestActivity == null || latestActivity.getId() == null || latestActivity.getId().trim().isEmpty()) {
            toast("No activity data is available");
            return;
        }
        startActivity(SuggestedTimesActivity.intent(this, latestActivity.getId()));
    }

    private String joinNonEmpty(String first, String second) {
        String value = (first + " " + second).trim();
        return value.isEmpty() ? "Schedule not specified" : value;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void bindUser(UserProfile user) {
        String initials = user == null ? "" : user.getInitials();
        String name = user == null ? "" : user.getFirstName();
        ((TextView) findViewById(R.id.header_user_initials)).setText(initials);
        ((TextView) findViewById(R.id.greeting_text)).setText(name.isEmpty() ? "Home" : "Hello, " + name + "!");
        ((TextView) findViewById(R.id.active_plans_text)).setText(user == null ? "" : "Loading your plans…");
        ((TextView) findViewById(R.id.invite_link_text)).setText(R.string.invite_link_soon);
    }
}
