package com.example.parchapp;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.example.parchapp.analytics.AnalyticsEvents;
import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.data.FirebaseRecommendationDecisionRepository;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.domain.model.ActivityDetails;
import com.example.parchapp.util.Callback;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.HashMap;
import java.util.Map;

public class ActivityCreatedActivity extends AppCompatActivity {
    public static final String EXTRA_TITLE = "extra_activity_title";
    public static final String EXTRA_DATE = "extra_activity_date";
    public static final String EXTRA_TIME = "extra_activity_time";
    public static final String EXTRA_LOCATION = "extra_activity_location";
    public static final String EXTRA_ACTIVITY_ID = "extra_activity_id";
    public static final String EXTRA_RECOMMENDATION_ID = "extra_recommendation_id";
    public static final String EXTRA_WAS_MODIFIED = "extra_was_modified";
    public static final String EXTRA_SELECTED_START_TIME = "extra_selected_start_time";
    public static final String EXTRA_SELECTED_END_TIME = "extra_selected_end_time";
    public static final String EXTRA_SLOT_POSITION = "extra_slot_position";
    public static final String EXTRA_GROUP_SIZE = "extra_group_size";

    private ActivityDetails activity;
    private AppCompatButton reminderButton;
    private TextView reminderStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_created);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String date = getIntent().getStringExtra(EXTRA_DATE);
        String time = getIntent().getStringExtra(EXTRA_TIME);
        String location = getIntent().getStringExtra(EXTRA_LOCATION);
        if (title == null || title.trim().isEmpty()) {
            findViewById(R.id.activity_summary_card).setVisibility(View.GONE);
        } else {
            ((TextView) findViewById(R.id.created_activity_title)).setText(title);
            ((TextView) findViewById(R.id.created_activity_time)).setText(joinNonEmpty(date, time));
            ((TextView) findViewById(R.id.created_activity_duration)).setText("Duration not specified");
            ((TextView) findViewById(R.id.created_activity_location)).setText(
                    location == null || location.trim().isEmpty() ? "Location not specified" : location);
            ((TextView) findViewById(R.id.created_activity_location_detail)).setText("");
        }
        findViewById(R.id.attendees_card).setVisibility(View.GONE);
        if (savedInstanceState == null) persistRecommendationOutcome();
        reminderButton = findViewById(R.id.reminder_button);
        reminderStatus = findViewById(R.id.reminder_status);
        reminderButton.setEnabled(false);
        reminderButton.setText("Loading reminder setting…");

        findViewById(R.id.close_created_button).setOnClickListener(v -> finish());
        findViewById(R.id.copy_invite_button).setEnabled(false);
        findViewById(R.id.whatsapp_button).setEnabled(false);
        findViewById(R.id.calendar_toggle).setEnabled(false);
        findViewById(R.id.view_schedule_button).setEnabled(false);
        // back to the dashboard closing the screens opened on top of it, finishAffinity closed the whole app
        findViewById(R.id.back_home_button).setOnClickListener(v -> {
            startActivity(new android.content.Intent(this, DashboardActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });

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

    /** Persists was_modified as the backend's authoritative BQ3 recommendation outcome. */
    private void persistRecommendationOutcome() {
        String activityId = getIntent().getStringExtra(EXTRA_ACTIVITY_ID);
        String recommendationId = getIntent().getStringExtra(EXTRA_RECOMMENDATION_ID);
        if (activityId == null || recommendationId == null) return;

        TextView outcomeView = findViewById(R.id.recommendation_outcome_text);
        outcomeView.setVisibility(View.VISIBLE);
        if (!Repositories.isFirebaseAvailable()) {
            outcomeView.setText("Recommendation outcome requires the backend integration.");
            return;
        }

        boolean wasModified = getIntent().getBooleanExtra(EXTRA_WAS_MODIFIED, false);
        String startTime = getIntent().getStringExtra(EXTRA_SELECTED_START_TIME);
        String endTime = getIntent().getStringExtra(EXTRA_SELECTED_END_TIME);
        int slotPosition = getIntent().getIntExtra(EXTRA_SLOT_POSITION, -1);
        int groupSize = getIntent().getIntExtra(EXTRA_GROUP_SIZE, 0);
        outcomeView.setText("Saving recommendation decision…");

        FirebaseRecommendationDecisionRepository decisions = new FirebaseRecommendationDecisionRepository(
                FirebaseFunctions.getInstance("us-central1"));
        decisions.recordDecision(activityId, recommendationId, wasModified, startTime, endTime,
                new Callback<Void>() {
                    @Override public void onSuccess(Void ignored) {
                        outcomeView.setText(wasModified
                                ? "The suggested time was modified"
                                : "The suggested time was kept");
                        Map<String, Object> event = new HashMap<>();
                        event.put(AnalyticsEvents.PARAM_RECOMMENDATION_ID, recommendationId);
                        event.put(AnalyticsEvents.PARAM_MODIFIED, wasModified);
                        if (slotPosition >= 0) event.put(AnalyticsEvents.PARAM_SLOT_POSITION, slotPosition);
                        if (groupSize > 0) event.put(AnalyticsEvents.PARAM_GROUP_SIZE, groupSize);
                        AnalyticsTracker.getInstance().track(AnalyticsEvents.SLOT_ACCEPTED, event);
                    }

                    @Override public void onError(Exception error) {
                        outcomeView.setText("Could not save the recommendation decision");
                        Toast.makeText(ActivityCreatedActivity.this,
                                "Could not save the recommendation decision", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private String joinNonEmpty(String first, String second) {
        String value = ((first == null ? "" : first) + " " + (second == null ? "" : second)).trim();
        return value.isEmpty() ? "Schedule not specified" : value;
    }
}
