package com.example.parchapp;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.example.parchapp.data.ActivityRepository;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.domain.model.ActivityDetails;
import com.example.parchapp.util.Callback;

public class ActivityCreatedActivity extends AppCompatActivity {
    public static final String EXTRA_ACTIVITY_ID = "extra_activity_id";

    private ActivityDetails activity;
    private AppCompatButton reminderButton;
    private TextView reminderStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_created);
        reminderButton = findViewById(R.id.reminder_button);
        reminderStatus = findViewById(R.id.reminder_status);
        reminderButton.setEnabled(false);
        reminderButton.setText("Loading reminder setting…");

        findViewById(R.id.close_created_button).setOnClickListener(v -> finish());
        findViewById(R.id.copy_invite_button).setEnabled(false);
        findViewById(R.id.whatsapp_button).setEnabled(false);
        findViewById(R.id.calendar_toggle).setEnabled(false);
        findViewById(R.id.view_schedule_button).setEnabled(false);
        findViewById(R.id.back_home_button).setOnClickListener(v -> finishAffinity());

        String activityId = getIntent().getStringExtra(EXTRA_ACTIVITY_ID);
        if (activityId == null || activityId.trim().isEmpty()) {
            showActivityUnavailable();
        } else {
            loadActivity(activityId);
        }
    }

    private void loadActivity(String activityId) {
        Repositories.activities().getActivityDetails(activityId, new Callback<ActivityDetails>() {
            @Override public void onSuccess(ActivityDetails details) {
                activity = details;
                bindActivity(details);
            }

            @Override public void onError(Exception error) {
                showActivityUnavailable();
                Toast.makeText(ActivityCreatedActivity.this,
                        "Could not load the saved activity", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindActivity(ActivityDetails details) {
        findViewById(R.id.activity_summary_card).setVisibility(View.VISIBLE);
        findViewById(R.id.attendees_card).setVisibility(View.GONE);
        ((TextView) findViewById(R.id.created_activity_title)).setText(details.getTitle());
        ((TextView) findViewById(R.id.created_activity_time)).setText(joinNonEmpty(details.getDate(), details.getTime()));
        ((TextView) findViewById(R.id.created_activity_duration)).setText(
                details.hasScheduledTime() ? "Scheduled activity" : "Schedule not specified");
        ((TextView) findViewById(R.id.created_activity_location)).setText(
                details.getLocation().trim().isEmpty() ? "Location not specified" : details.getLocation());
        ((TextView) findViewById(R.id.created_activity_location_detail)).setText("");

        if (!details.hasScheduledTime()) {
            reminderButton.setText("Reminder unavailable");
            reminderStatus.setText("A scheduled activity time is required for a reminder.");
            reminderStatus.setVisibility(View.VISIBLE);
            return;
        }
        reminderButton.setEnabled(true);
        updateReminderLabel(details.getReminderMinutesBefore());
        reminderButton.setOnClickListener(v -> showReminderOptions());
        reminderStatus.setVisibility(View.GONE);
    }

    private void showReminderOptions() {
        String[] labels = {"No reminder", "15 minutes before", "30 minutes before", "1 hour before"};
        int[] values = {0, 15, 30, 60};
        new AlertDialog.Builder(this)
                .setTitle("Activity reminder")
                .setItems(labels, (dialog, selected) -> saveReminder(values[selected]))
                .show();
    }

    private void saveReminder(int minutesBefore) {
        reminderButton.setEnabled(false);
        reminderStatus.setText("Saving reminder setting…");
        reminderStatus.setVisibility(View.VISIBLE);
        Repositories.activities().setActivityReminder(activity.getId(), minutesBefore, new Callback<Void>() {
            @Override public void onSuccess(Void ignored) {
                reminderButton.setEnabled(true);
                updateReminderLabel(minutesBefore);
                reminderStatus.setText(minutesBefore == 0 ? "Reminder disabled" : "Reminder saved");
            }

            @Override public void onError(Exception error) {
                reminderButton.setEnabled(true);
                reminderStatus.setText("Could not save reminder setting");
                Toast.makeText(ActivityCreatedActivity.this,
                        "Could not save reminder setting", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateReminderLabel(Integer minutesBefore) {
        if (minutesBefore == null) reminderButton.setText("Set reminder  ⌄");
        else if (minutesBefore == 0) reminderButton.setText("No reminder  ⌄");
        else reminderButton.setText(minutesBefore + " min before  ⌄");
    }

    private void showActivityUnavailable() {
        findViewById(R.id.activity_summary_card).setVisibility(View.GONE);
        findViewById(R.id.attendees_card).setVisibility(View.GONE);
        reminderButton.setEnabled(false);
        reminderButton.setText("Reminder unavailable");
        reminderStatus.setText("Activity details are unavailable.");
        reminderStatus.setVisibility(View.VISIBLE);
    }

    private String joinNonEmpty(String first, String second) {
        String value = ((first == null ? "" : first) + " " + (second == null ? "" : second)).trim();
        return value.isEmpty() ? "Schedule not specified" : value;
    }
}
