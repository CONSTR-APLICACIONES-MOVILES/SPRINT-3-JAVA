package com.example.parchapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.parchapp.data.Repositories;
import com.example.parchapp.data.TravelTimeService;
import com.example.parchapp.domain.CampusArea;
import com.example.parchapp.domain.PlanContext;
import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.util.Callback;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Group screen shell. Group membership and Firestore behavior are restored on its feature branch. */
public class GroupDetailActivity extends AppCompatActivity {
    public static final String EXTRA_GROUP_ID = "extra_group_id";
// f1: code to know which permission answer is ours
    private static final int LOCATION_REQUEST = 10;

// f1: text that shows if the user is on campus or not
    private TextView campusText;
// f3: service that gives the travel time to the place of the plan
    private TravelTimeService travelTime;
// f3: next plan of this group, null when the group has no plan
    private PlannedActivity groupPlan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_detail);
        findViewById(R.id.back_button).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.group_category_text)).setText("GROUP");
        ((TextView) findViewById(R.id.top_group_name_text)).setText("Group details");
        ((TextView) findViewById(R.id.top_group_subtitle_text)).setText("");
        ((TextView) findViewById(R.id.group_name_text)).setText("Group feature not included in this base branch");
        ((TextView) findViewById(R.id.group_description_text)).setText("");
        ((TextView) findViewById(R.id.group_synced_badge)).setText("Not connected");
        ((TextView) findViewById(R.id.common_free_text)).setText("Availability feature not included in this base branch");
        findViewById(R.id.common_free_badge).setVisibility(View.GONE);
        findViewById(R.id.group_plan_section).setVisibility(View.GONE);
        findViewById(R.id.group_members_header).setVisibility(View.GONE);
        findViewById(R.id.member_card_0).setVisibility(View.GONE);
        findViewById(R.id.member_card_1).setVisibility(View.GONE);
        findViewById(R.id.member_card_2).setVisibility(View.GONE);
        findViewById(R.id.member_card_3).setVisibility(View.GONE);
        findViewById(R.id.group_recent_activity).setVisibility(View.GONE);

        findViewById(R.id.share_button).setEnabled(false);
        findViewById(R.id.more_options_button).setEnabled(false);
        findViewById(R.id.compare_roommates_button).setEnabled(false);
        findViewById(R.id.plan_details_button).setEnabled(false);
        findViewById(R.id.add_calendar_button).setEnabled(false);
        findViewById(R.id.refresh_roommates_button).setEnabled(false);
        findViewById(R.id.propose_plan_button).setEnabled(false);
        findViewById(R.id.sync_schedules_button).setEnabled(false);
        findViewById(R.id.house_chat_button).setEnabled(false);
        findViewById(R.id.view_all_activities).setEnabled(false);
        findViewById(R.id.create_plan_button).setEnabled(false);

        findViewById(R.id.nav_home_group).setOnClickListener(v ->
                startActivity(new Intent(this, DashboardActivity.class)));
        findViewById(R.id.nav_schedule_group).setOnClickListener(v ->
                startActivity(new Intent(this, ScheduleActivity.class)));

//f1: we look for the text and then we check the location
        campusText = findViewById(R.id.campus_status_text);
        loadPlanContext();

// f3: we look for the next plan of this group to show it with the travel time
        travelTime = new TravelTimeService(this);
        loadGroupPlan();
    }

// f3: we read the plans of the user and keep only the ones of this group
    private void loadGroupPlan() {
        String groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        UserProfile user = UserSession.getCurrentUser();
        if (groupId == null || user == null) {
            return;
        }
        Repositories.activities().getActivities(user.getId(), new Callback<List<PlannedActivity>>() {
            @Override
            public void onSuccess(List<PlannedActivity> activities) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                List<PlannedActivity> groupPlans = new ArrayList<>();
                for (PlannedActivity activity : activities) {
                    if (groupId.equals(activity.getGroupId())) {
                        groupPlans.add(activity);
                    }
                }
                Calendar now = Calendar.getInstance(PlanContext.bogota());
                int nowMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
                PlannedActivity next = PlanContext.nextPlan(groupPlans, now, nowMinute);
// f3: when no plan is coming we show the newest one, like the dashboard card
                if (next == null && !groupPlans.isEmpty()) {
                    next = groupPlans.get(0);
                }
                if (next != null) {
                    showGroupPlan(next);
                }
            }

// f3: without plans the card stays hidden
            @Override
            public void onError(Exception error) {
            }
        });
    }

// f3: shows the plan card with the title, the time and the place
    private void showGroupPlan(PlannedActivity plan) {
        groupPlan = plan;
        findViewById(R.id.group_plan_section).setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.next_plan_header)).setText("Next Group Plan");
        ((TextView) findViewById(R.id.next_plan_status)).setText(plan.getStatus());
        ((TextView) findViewById(R.id.plan_title_text)).setText(plan.getTitle());
        String time = (plan.getDate() + " " + plan.getTime()).trim();
        ((TextView) findViewById(R.id.next_plan_time)).setText(time.isEmpty() ? "Schedule not specified" : time);
        ((TextView) findViewById(R.id.next_plan_location)).setText(
                plan.getLocation().isEmpty() ? "Location not specified" : plan.getLocation());
// f3: the rsvp of the plan is not part of this card yet
        ((TextView) findViewById(R.id.rsvp_ready_text)).setText("");
        findViewById(R.id.rsvp_progress_fill).setVisibility(View.GONE);
        updateTravelTime(plan);
    }

// f3: asks for the travel time to the place and shows the warning when the user will be late
    private void updateTravelTime(PlannedActivity plan) {
        TextView travel = findViewById(R.id.travel_time_text);
        View lateWarning = findViewById(R.id.late_warning_text);
        travel.setVisibility(View.VISIBLE);
        lateWarning.setVisibility(View.GONE);
        travel.setText("➜  Finding your travel time…");
        travelTime.travelTo(plan, new TravelTimeService.Listener() {
            @Override
            public void onTravel(TravelTimeService.Result result) {
// f3: the answer can arrive late, so we check that the screen and the plan are the same
                if (isFinishing() || isDestroyed() || plan != groupPlan) {
                    return;
                }
                travel.setText(TravelTimeService.describe(result));
                lateWarning.setVisibility(result.late ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onProblem(TravelTimeService.Problem problem) {
                if (!isFinishing() && !isDestroyed() && plan == groupPlan) {
                    travel.setText(TravelTimeService.describe(problem));
                }
            }
        });
    }

//f1: if we have the location permission we read the location, if not we ask the user for it
    private void loadPlanContext() {
        if (hasLocationPermission()) {
            readLocation();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
        }
    }

//f1: true when the user already gave the location permission
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

//f1: here we get the answer of the user after we ask for the permission
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_REQUEST) {
            return;
        }
        if (hasLocationPermission()) {
            readLocation();
        } else {
            campusText.setText("◎  Location permission denied");
        }
// f3: after the answer we ask again for the travel time of the plan
        if (groupPlan != null) {
            updateTravelTime(groupPlan);
        }
    }

//f1: we ask the fused location provider for the current location
//if there is no location or there is an error we show a message
    @SuppressLint("MissingPermission")
    private void readLocation() {
        LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, new CancellationTokenSource().getToken())
                .addOnSuccessListener(this, location -> {
                    if (location == null) {
                        campusText.setText("◎  Could not get your location");
                        return;
                    }
                    showCampusStatus(location);
                })
                .addOnFailureListener(this, e -> campusText.setText("◎  Could not get your location"));
    }

//f1: shows if the user is inside or outside the campus
    private void showCampusStatus(Location location) {
        boolean onCampus = CampusArea.isOnCampus(location.getLatitude(), location.getLongitude());
        campusText.setText(onCampus ? "◎  You are on campus" : "◎  You are off campus");
    }
}
