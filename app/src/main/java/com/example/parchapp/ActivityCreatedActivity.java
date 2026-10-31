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
import com.example.parchapp.util.IconButtons;
import com.google.firebase.functions.FirebaseFunctions;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

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
    private String activityId;
    private AppCompatButton reminderButton;
    private TextView reminderStatus;
// key to keep the bq3 message when the phone turns
    private static final String STATE_OUTCOME = "recommendation_outcome";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_created);

        reminderButton = findViewById(R.id.reminder_button);
        reminderStatus = findViewById(R.id.reminder_status);
        findViewById(R.id.suggested_times_button).setVisibility(View.GONE);
        findViewById(R.id.close_created_button).setOnClickListener(v -> finish());
        IconButtons.centerIconWithText(findViewById(R.id.copy_invite_button));
        IconButtons.centerIconWithText(findViewById(R.id.whatsapp_button));
        findViewById(R.id.copy_invite_button).setOnClickListener(v -> InviteActions.inviteToPlan(this, activity, false, v));
        findViewById(R.id.whatsapp_button).setOnClickListener(v -> InviteActions.inviteToPlan(this, activity, true, v));
        findViewById(R.id.retry_activity_button).setOnClickListener(v -> {
            if (!isBlank(activityId)) loadActivity(activityId);
        });
        // back to the dashboard closing the screens opened on top of it, finishAffinity closed the whole app
        findViewById(R.id.back_home_button).setOnClickListener(v -> {
            startActivity(new android.content.Intent(this, DashboardActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });

        activityId = getIntent().getStringExtra(EXTRA_ACTIVITY_ID);
        if (savedInstanceState == null) {
            persistRecommendationOutcome();
        } else {
// bq3: the decision is saved only one time, after the phone turns we only show its message again
            String outcome = savedInstanceState.getString(STATE_OUTCOME);
            if (outcome != null) {
                TextView outcomeView = findViewById(R.id.recommendation_outcome_text);
                outcomeView.setText(outcome);
                outcomeView.setVisibility(View.VISIBLE);
            }
        }
        if (isBlank(activityId)) {
            String title = getIntent().getStringExtra(EXTRA_TITLE);
            if (isBlank(title)) {
                showActivityUnavailable(getString(R.string.activity_missing_id), false);
            } else {
                showLegacyActivity(title, getIntent().getStringExtra(EXTRA_DATE),
                        getIntent().getStringExtra(EXTRA_TIME), getIntent().getStringExtra(EXTRA_LOCATION));
            }
        } else {
            loadActivity(activityId);
        }
    }

    // eventual connectivity: banner while offline; if the activity could not load, it loads again when the network returns
    private final ConnectivityWatcher connectivity = new ConnectivityWatcher(() -> {
        String id = getIntent().getStringExtra(EXTRA_ACTIVITY_ID);
        if (activity == null && id != null && !id.trim().isEmpty()) {
            loadActivity(id);
        }
    });

    @Override
    protected void onStart() {
        super.onStart();
        connectivity.start(findViewById(R.id.offline_banner));
    }

    @Override
    protected void onStop() {
        connectivity.stop();
        super.onStop();
    }

    private void loadActivity(String activityId) {
        showLoading();
        Repositories.activities().getActivityDetails(activityId, new Callback<ActivityDetails>() {
            @Override public void onSuccess(ActivityDetails details) {
                if (isFinishing() || isDestroyed()) return;
                if (details == null || isBlank(details.getTitle())) {
                    showActivityUnavailable(getString(R.string.activity_incomplete_details), true);
                    return;
                }
                activity = details;
                bindActivity(details);
            }

            @Override public void onError(Exception error) {
                if (!isFinishing() && !isDestroyed()) {
                    showActivityUnavailable(OfflineGuard.failureMessage(error, "load this activity",
                            getString(R.string.activity_load_error)), true);
                }
            }
        });
    }

    private void bindActivity(ActivityDetails details) {
        showSuccessContent();
        bindSummary(details.getTitle(), details.getDate(), details.getTime(), details.getLocation(),
                categoryLabel(details.getCategory()), statusLabel(details.getStatus()), durationLabel(details));
        View suggestedTimes = findViewById(R.id.suggested_times_button);
        UserProfile currentUser = UserSession.getCurrentUser();
        boolean canSuggest = !details.isCancelled() && currentUser != null
                && currentUser.getId().equals(details.getCreatedBy())
                && Repositories.recommendationDecisions() != null;
        suggestedTimes.setVisibility(canSuggest ? View.VISIBLE : View.GONE);
        suggestedTimes.setOnClickListener(v -> startActivity(
                SuggestedTimesActivity.intent(this, details.getId())));
        if (!hasCompleteSchedule(details)) {
            reminderButton.setText("Reminder unavailable");
            reminderStatus.setText(R.string.activity_reminder_requires_schedule);
            reminderStatus.setVisibility(View.VISIBLE);
            reminderButton.setEnabled(false);
            return;
        }
        reminderButton.setEnabled(true);
        updateReminderLabel(details.getReminderMinutesBefore());
        reminderButton.setOnClickListener(v -> showReminderOptions());
        reminderStatus.setVisibility(View.GONE);
    }

    private void showLegacyActivity(String title, String date, String time, String location) {
        showSuccessContent();
        bindSummary(title, date, time, location, "Activity", "Created",
                getString(R.string.activity_schedule_unspecified));
        reminderButton.setEnabled(false);
        reminderButton.setText("Reminder unavailable");
        reminderStatus.setText(R.string.activity_reminder_requires_schedule);
        reminderStatus.setVisibility(View.VISIBLE);
    }

    private void bindSummary(String title, String date, String time, String location,
                             String category, String status, String duration) {
        ((TextView) findViewById(R.id.created_activity_title)).setText(title);
        ((TextView) findViewById(R.id.created_activity_category)).setText(category);
        ((TextView) findViewById(R.id.created_activity_status)).setText(status);
        ((TextView) findViewById(R.id.created_activity_time)).setText(joinNonEmpty(date, time));
        ((TextView) findViewById(R.id.created_activity_duration)).setText(duration);
        String safeLocation = location == null ? "" : location.trim();
        ((TextView) findViewById(R.id.created_activity_location)).setText(
                safeLocation.isEmpty() ? "Location not specified" : safeLocation);
        findViewById(R.id.created_activity_location_detail).setVisibility(View.GONE);
    }

    private void showLoading() {
        findViewById(R.id.activity_loading_state).setVisibility(View.VISIBLE);
        findViewById(R.id.activity_error_state).setVisibility(View.GONE);
        findViewById(R.id.activity_success_header).setVisibility(View.GONE);
        findViewById(R.id.activity_summary_card).setVisibility(View.GONE);
        findViewById(R.id.activity_utilities_card).setVisibility(View.GONE);
        reminderButton.setEnabled(false);
        reminderButton.setText(R.string.activity_loading_details);
        reminderStatus.setVisibility(View.GONE);
    }

    private void showSuccessContent() {
        findViewById(R.id.activity_loading_state).setVisibility(View.GONE);
        findViewById(R.id.activity_error_state).setVisibility(View.GONE);
        findViewById(R.id.activity_success_header).setVisibility(View.VISIBLE);
        findViewById(R.id.activity_summary_card).setVisibility(View.VISIBLE);
        findViewById(R.id.activity_utilities_card).setVisibility(View.VISIBLE);
    }

    private void showActivityUnavailable(String message, boolean retryable) {
        findViewById(R.id.activity_loading_state).setVisibility(View.GONE);
        findViewById(R.id.activity_success_header).setVisibility(View.GONE);
        findViewById(R.id.activity_summary_card).setVisibility(View.GONE);
        findViewById(R.id.activity_utilities_card).setVisibility(View.GONE);
        findViewById(R.id.activity_error_state).setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.activity_error_message)).setText(message);
        findViewById(R.id.retry_activity_button).setVisibility(retryable ? View.VISIBLE : View.GONE);
        reminderButton.setEnabled(false);
        reminderButton.setText("Reminder unavailable");
        reminderStatus.setVisibility(View.GONE);
    }

    private boolean hasCompleteSchedule(ActivityDetails details) {
        return !isBlank(details.getStartTime()) && !isBlank(details.getEndTime());
    }

    private String durationLabel(ActivityDetails details) {
        if (!hasCompleteSchedule(details)) {
            return getString(R.string.activity_schedule_unspecified);
        }
        Date start = parseUtc(details.getStartTime());
        Date end = parseUtc(details.getEndTime());
        if (start == null || end == null || end.getTime() <= start.getTime()) {
            return getString(R.string.activity_duration_unavailable);
        }
        long minutes = (end.getTime() - start.getTime()) / 60_000L;
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        if (hours == 0) {
            return getString(R.string.activity_duration_minutes, minutes);
        }
        if (remainingMinutes == 0) {
            return getString(R.string.activity_duration_hours, hours);
        }
        return getString(R.string.activity_duration_hours_minutes, hours, remainingMinutes);
    }

    private Date parseUtc(String value) {
        String[] patterns = {"yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX"};
        for (String pattern : patterns) {
            SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
            parser.setLenient(false);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            ParsePosition position = new ParsePosition(0);
            Date parsed = parser.parse(value, position);
            if (parsed != null && position.getIndex() == value.length()) {
                return parsed;
            }
        }
        return null;
    }

    private String categoryLabel(String category) {
        return isBlank(category) ? "Activity" : displayLabel(category);
    }

    private String statusLabel(String status) {
        return isBlank(status) ? "Saved" : displayLabel(status);
    }

    private String displayLabel(String value) {
        String[] words = value.trim().replace('_', ' ').replace('-', ' ')
                .toLowerCase(Locale.US).split("\\s+");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (label.length() > 0) label.append(' ');
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
        // the reminder is scheduled by the backend, so offline we say it instead of failing
        if (OfflineGuard.blockIfOffline(this, "change the reminder")) {
            return;
        }
        reminderButton.setEnabled(false);
        reminderStatus.setText("Saving reminder setting…");
        reminderStatus.setVisibility(View.VISIBLE);
        Repositories.activities().setActivityReminder(activity.getId(), minutesBefore, new Callback<Void>() {
            @Override public void onSuccess(Void ignored) {
                if (isFinishing() || isDestroyed()) return;
                reminderButton.setEnabled(true);
                updateReminderLabel(minutesBefore);
                reminderStatus.setText(minutesBefore == 0 ? "Reminder disabled" : "Reminder saved");
            }

            @Override public void onError(Exception error) {
                if (isFinishing() || isDestroyed()) return;
                reminderButton.setEnabled(true);
                String message = OfflineGuard.failureMessage(error, "change the reminder", "Could not save reminder setting");
                reminderStatus.setText(message);
                Toast.makeText(ActivityCreatedActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateReminderLabel(Integer minutesBefore) {
        if (minutesBefore == null) reminderButton.setText("Set reminder  ⌄");
        else if (minutesBefore == 0) reminderButton.setText("No reminder  ⌄");
        else reminderButton.setText(minutesBefore + " min before  ⌄");
    }

    /** Persists was_modified as the backend's authoritative BQ3 recommendation outcome. */
// keeps the bq3 message when the phone turns
    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        TextView outcomeView = findViewById(R.id.recommendation_outcome_text);
        if (outcomeView.getVisibility() == View.VISIBLE && outcomeView.getText().length() > 0) {
            outState.putString(STATE_OUTCOME, outcomeView.getText().toString());
        }
    }

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
                        if (isFinishing() || isDestroyed()) return;
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
                        if (isFinishing() || isDestroyed()) return;
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
