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

import com.example.parchapp.domain.CampusArea;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

/** Group screen shell. Group membership and Firestore behavior are restored on its feature branch. */
public class GroupDetailActivity extends AppCompatActivity {
    public static final String EXTRA_GROUP_ID = "extra_group_id";
// f1: code to know which permission answer is ours
    private static final int LOCATION_REQUEST = 10;

// f1: text that shows if the user is on campus or not
    private TextView campusText;

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
