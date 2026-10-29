package com.example.parchapp;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.example.parchapp.data.RecommendationDecisionRepository;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.domain.PlanContext;
import com.example.parchapp.domain.model.ActivityDetails;
import com.example.parchapp.domain.model.RecommendedSlot;
import com.example.parchapp.domain.model.SlotRecommendationBatch;
import com.example.parchapp.util.Callback;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/** Native Java counterpart of Flutter's SuggestedTimesView. */
public class SuggestedTimesActivity extends AppCompatActivity {
    public static final String EXTRA_ACTIVITY_ID = "extra_suggested_activity_id";

    private static final int PERIOD_ALL = 0;
    private static final int PERIOD_MORNING = 1;
    private static final int PERIOD_AFTERNOON = 2;

    private final TimeZone bogota = PlanContext.bogota();
    private final Set<String> presented = new HashSet<>();
    private final Set<String> presenting = new HashSet<>();
    private final Map<String, View> slotViews = new HashMap<>();
    private final Map<String, Runnable> afterPresentation = new HashMap<>();

    private String activityId;
    private ActivityDetails activity;
    private SlotRecommendationBatch batch;
    private RecommendationDecisionRepository recommendationRepository;
    private ScrollView scroll;
    private EditText durationInput;
    private LinearLayout durationControls;
    private LinearLayout slotsContainer;
    private LinearLayout filterRow;
    private TextView message;
    private TextView feedbackError;
    private TextView emptyMessage;
    private TextView dateLabel;
    private View card;
    private View loading;
    private View noMatchingSlots;
    private AppCompatButton findButton;
    private AppCompatButton retryButton;
    private AppCompatButton changeDurationButton;
    private AppCompatButton retryFeedbackButton;
    private TextView[] filterButtons;
    private int selectedPeriod = PERIOD_ALL;
    private int durationMinutes;
    private boolean busy;
    private boolean selectionSaved;
    private String pendingFeedbackMessage;

    public static Intent intent(Context context, String activityId) {
        return new Intent(context, SuggestedTimesActivity.class)
                .putExtra(EXTRA_ACTIVITY_ID, activityId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_suggested_times);
        activityId = getIntent().getStringExtra(EXTRA_ACTIVITY_ID);
        bindViews();
        findViewById(R.id.suggested_times_back).setOnClickListener(v -> finish());
        findViewById(R.id.suggested_times_retry_activity).setOnClickListener(v -> loadActivity());
        scroll.setOnScrollChangeListener((View view, int x, int y, int oldX, int oldY) -> checkVisibleSlots());
        findButton.setOnClickListener(v -> requestRecommendationsFromInput());
        retryButton.setOnClickListener(v -> requestRecommendations(durationMinutes));
        changeDurationButton.setOnClickListener(v -> showDurationInput());
        retryFeedbackButton.setOnClickListener(v -> checkVisibleSlots());
        setupFilters();
        loadActivity();
    }

    private void bindViews() {
        scroll = findViewById(R.id.suggested_times_scroll);
        durationInput = findViewById(R.id.suggested_duration_input);
        durationControls = findViewById(R.id.suggested_duration_controls);
        slotsContainer = findViewById(R.id.suggested_slots_container);
        filterRow = findViewById(R.id.suggested_filter_row);
        message = findViewById(R.id.suggested_times_message);
        feedbackError = findViewById(R.id.suggested_feedback_error);
        emptyMessage = findViewById(R.id.suggested_no_slots);
        dateLabel = findViewById(R.id.suggested_date_label);
        card = findViewById(R.id.suggested_times_card);
        loading = findViewById(R.id.suggested_times_loading);
        noMatchingSlots = findViewById(R.id.suggested_no_matching_times);
        findButton = findViewById(R.id.suggested_find_times);
        retryButton = findViewById(R.id.suggested_retry);
        changeDurationButton = findViewById(R.id.suggested_change_duration);
        retryFeedbackButton = findViewById(R.id.suggested_retry_feedback);
        filterButtons = new TextView[]{
                findViewById(R.id.suggested_filter_all),
                findViewById(R.id.suggested_filter_morning),
                findViewById(R.id.suggested_filter_afternoon)
        };
    }

    private void setupFilters() {
        for (int i = 0; i < filterButtons.length; i++) {
            final int period = i;
            filterButtons[i].setOnClickListener(v -> {
                selectedPeriod = period;
                updateFilterStyles();
                renderSlots();
            });
        }
        updateFilterStyles();
    }

    private void updateFilterStyles() {
        for (int i = 0; i < filterButtons.length; i++) {
            boolean selected = i == selectedPeriod;
            filterButtons[i].setBackgroundResource(selected
                    ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            filterButtons[i].setTextColor(getColor(selected ? R.color.brand_primary : R.color.brand_dark));
            filterButtons[i].setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void loadActivity() {
        if (activityId == null || activityId.trim().isEmpty()) {
            showMessage("Activity not found. Return to the group and try again.");
            return;
        }
        message.setVisibility(View.GONE);
        findViewById(R.id.suggested_times_retry_activity).setVisibility(View.GONE);
        card.setVisibility(View.GONE);
        setLoading(true);
        Repositories.activities().getActivityDetails(activityId, new Callback<ActivityDetails>() {
            @Override
            public void onSuccess(ActivityDetails result) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                if (result == null || result.getTitle() == null || result.getTitle().trim().isEmpty()) {
                    showMessage("Activity details are unavailable. Please try again.");
                    findViewById(R.id.suggested_times_retry_activity).setVisibility(View.VISIBLE);
                    return;
                }
                activity = result;
                ((TextView) findViewById(R.id.suggested_times_activity_title)).setText(result.getTitle());
                if (result.isCancelled()) {
                    showMessage("This activity is cancelled. Suggested times are unavailable.");
                    return;
                }
                UserProfile currentUser = UserSession.getCurrentUser();
                if (currentUser == null || !currentUser.getId().equals(result.getCreatedBy())) {
                    showMessage("Only the organizer can request or choose suggested times.");
                    return;
                }
                recommendationRepository = Repositories.recommendationDecisions();
                if (recommendationRepository == null) {
                    showMessage("Suggested times require a connection to the activity service.");
                    return;
                }
                card.setVisibility(View.VISIBLE);
                durationMinutes = suggestedDuration(result);
                durationInput.setText(durationMinutes > 0 ? Integer.toString(durationMinutes) : "");
                durationControls.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(Exception error) {
                if (!isFinishing() && !isDestroyed()) {
                    setLoading(false);
                    showMessage(errorMessage(error, "Unable to load this activity. Please retry."));
                    findViewById(R.id.suggested_times_retry_activity).setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private int suggestedDuration(ActivityDetails details) {
        if (details.getDurationMinutes() != null
                && details.getDurationMinutes() >= 15 && details.getDurationMinutes() <= 720) {
            return details.getDurationMinutes();
        }
        Long start = parseIso(details.getStartTime());
        Long end = parseIso(details.getEndTime());
        if (start != null && end != null) {
            long minutes = (end - start) / 60_000L;
            if (minutes >= 15 && minutes <= 720) return (int) minutes;
        }
        return 0;
    }

    private void requestRecommendationsFromInput() {
        String raw = durationInput.getText() == null ? "" : durationInput.getText().toString().trim();
        int minutes;
        try {
            minutes = Integer.parseInt(raw);
        } catch (NumberFormatException error) {
            durationInput.setError("Enter a duration from 15 to 720 minutes.");
            return;
        }
        if (minutes < 15 || minutes > 720) {
            durationInput.setError("Enter a duration from 15 to 720 minutes.");
            return;
        }
        durationInput.setError(null);
        requestRecommendations(minutes);
    }

    private void requestRecommendations(int minutes) {
        if (busy || recommendationRepository == null || activity == null || selectionSaved) return;
        durationMinutes = minutes;
        busy = true;
        setLoading(true);
        message.setVisibility(View.GONE);
        slotsContainer.setVisibility(View.GONE);
        filterRow.setVisibility(View.GONE);
        emptyMessage.setVisibility(View.GONE);
        noMatchingSlots.setVisibility(View.GONE);
        feedbackError.setVisibility(View.GONE);
        retryFeedbackButton.setVisibility(View.GONE);
        recommendationRepository.getRecommendedSlots(activityId, minutes,
                new Callback<SlotRecommendationBatch>() {
                    @Override
                    public void onSuccess(SlotRecommendationBatch result) {
                        if (isFinishing() || isDestroyed()) return;
                        busy = false;
                        setLoading(false);
                        if (result == null || !activityId.equals(result.getActivityId())) {
                            showMessage("The activity service returned invalid recommendations.");
                            return;
                        }
                        batch = result;
                        presented.clear();
                        presenting.clear();
                        afterPresentation.clear();
                        pendingFeedbackMessage = null;
                        selectedPeriod = PERIOD_ALL;
                        updateFilterStyles();
                        durationControls.setVisibility(View.GONE);
                        dateLabel.setText(result.getDate() + " · Bogotá (UTC−05:00)");
                        dateLabel.setVisibility(View.VISIBLE);
                        filterRow.setVisibility(View.VISIBLE);
                        retryButton.setVisibility(View.VISIBLE);
                        changeDurationButton.setVisibility(View.VISIBLE);
                        emptyMessage.setVisibility(result.getSlots().isEmpty() ? View.VISIBLE : View.GONE);
                        slotsContainer.setVisibility(View.VISIBLE);
                        renderSlots();
                    }

                    @Override
                    public void onError(Exception error) {
                        if (isFinishing() || isDestroyed()) return;
                        busy = false;
                        setLoading(false);
                        durationControls.setVisibility(View.VISIBLE);
                        showMessage(errorMessage(error, "Unable to load recommended times. Please try again."));
                        retryButton.setVisibility(View.VISIBLE);
                        changeDurationButton.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void renderSlots() {
        slotsContainer.removeAllViews();
        slotViews.clear();
        if (batch == null) return;
        int matching = 0;
        for (int i = 0; i < batch.getSlots().size(); i++) {
            RecommendedSlot slot = batch.getSlots().get(i);
            if (!matchesPeriod(slot)) continue;
            matching++;
            View slotView = buildSlotView(slot, i);
            slotsContainer.addView(slotView);
            slotViews.put(slot.getId(), slotView);
        }
        noMatchingSlots.setVisibility(!batch.getSlots().isEmpty() && matching == 0 ? View.VISIBLE : View.GONE);
        emptyMessage.setVisibility(batch.getSlots().isEmpty() ? View.VISIBLE : View.GONE);
        slotsContainer.setVisibility(matching > 0 ? View.VISIBLE : View.GONE);
        scroll.post(this::checkVisibleSlots);
    }

    private boolean matchesPeriod(RecommendedSlot slot) {
        if (selectedPeriod == PERIOD_ALL) return true;
        Calendar start = Calendar.getInstance(bogota);
        start.setTimeInMillis(slot.getStartTimeMillis());
        int hour = start.get(Calendar.HOUR_OF_DAY);
        return selectedPeriod == PERIOD_MORNING ? hour < 12 : hour >= 12;
    }

    private View buildSlotView(RecommendedSlot slot, int position) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(2), dp(14), dp(2), dp(14));
        if (position > 0) {
            View divider = new View(this);
            divider.setBackgroundColor(getColor(R.color.brand_divider));
            container.addView(divider, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        }

        TextView title = text(position == 0 ? "Best recommended slot" : "Alternative " + position,
                13, R.color.brand_dark, true);
        container.addView(title);
        TextView time = text(formatRange(slot), 13, R.color.brand_dark, true);
        time.setPadding(0, dp(7), 0, dp(2));
        container.addView(time);
        container.addView(text(slot.getAvailableParticipants() + " / " + slot.getTotalParticipants()
                + " participants available", 11, R.color.brand_slate, false));
        for (String reason : slot.getReasons()) {
            TextView reasonView = text("• " + reason, 11, R.color.brand_slate, false);
            reasonView.setPadding(0, dp(3), 0, 0);
            container.addView(reasonView);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(9), 0, 0);
        AppCompatButton accept = new AppCompatButton(this);
        accept.setText(position == 0 ? "Accept recommended slot" : "Select this slot");
        accept.setAllCaps(false);
        accept.setTextSize(11);
        accept.setTextColor(getColor(android.R.color.white));
        accept.setBackgroundResource(R.drawable.bg_primary_pill);
        accept.setEnabled(!selectionSaved);
        LinearLayout.LayoutParams acceptParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        actions.addView(accept, acceptParams);
        AppCompatButton modify = new AppCompatButton(this);
        modify.setText("Modify time");
        modify.setAllCaps(false);
        modify.setTextSize(11);
        modify.setTextColor(getColor(R.color.brand_primary));
        modify.setBackgroundResource(R.drawable.bg_light_blue);
        modify.setEnabled(!selectionSaved);
        LinearLayout.LayoutParams modifyParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        modifyParams.setMarginStart(dp(8));
        actions.addView(modify, modifyParams);
        container.addView(actions);

        accept.setOnClickListener(v -> withPresented(slot, () -> saveDecision(slot, false, null, null)));
        modify.setOnClickListener(v -> withPresented(slot, () -> openModifyDialog(slot)));
        container.setTag(slot.getId());
        return container;
    }

    private void checkVisibleSlots() {
        if (batch == null || selectionSaved || isFinishing() || isDestroyed()) return;
        Rect viewport = new Rect();
        if (!scroll.getGlobalVisibleRect(viewport)) return;
        for (Map.Entry<String, View> entry : slotViews.entrySet()) {
            if (presented.contains(entry.getKey()) || presenting.contains(entry.getKey())) continue;
            View slot = entry.getValue();
            Rect visibleSlot = new Rect();
            int[] location = new int[2];
            slot.getLocationOnScreen(location);
            int centerX = location[0] + slot.getWidth() / 2;
            int centerY = location[1] + slot.getHeight() / 2;
            if (slot.getGlobalVisibleRect(visibleSlot) && viewport.contains(centerX, centerY)) {
                markShown(entry.getKey(), null);
            }
        }
    }

    private void withPresented(RecommendedSlot slot, Runnable action) {
        if (selectionSaved || busy) return;
        markShown(slot.getId(), action);
    }

    private void markShown(String id, Runnable after) {
        if (presented.contains(id)) {
            if (after != null) after.run();
            return;
        }
        if (presenting.contains(id)) {
            if (after != null) afterPresentation.put(id, after);
            return;
        }
        presenting.add(id);
        recommendationRepository.markRecommendationsShown(activityId,
                java.util.Collections.singletonList(id), new Callback<Void>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        if (isFinishing() || isDestroyed()) return;
                        presenting.remove(id);
                        presented.add(id);
                        Runnable pending = afterPresentation.remove(id);
                        if (after != null) after.run();
                        else if (pending != null) pending.run();
                        if (pendingFeedbackMessage != null && after == null) {
                            pendingFeedbackMessage = null;
                            updateFeedbackMessage();
                        }
                        scroll.post(SuggestedTimesActivity.this::checkVisibleSlots);
                    }

                    @Override
                    public void onError(Exception error) {
                        if (isFinishing() || isDestroyed()) return;
                        presenting.remove(id);
                        pendingFeedbackMessage = errorMessage(error,
                                "Recommendation feedback could not be recorded.");
                        updateFeedbackMessage();
                    }
                });
    }

    private void saveDecision(RecommendedSlot slot, boolean modified, Calendar start, Calendar end) {
        if (busy || selectionSaved) return;
        busy = true;
        setLoading(true);
        String startIso = modified ? toUtcIso(start) : null;
        String endIso = modified ? toUtcIso(end) : null;
        recommendationRepository.recordDecision(activityId, slot.getId(), modified, startIso, endIso,
                new Callback<Void>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        if (isFinishing() || isDestroyed()) return;
                        busy = false;
                        selectionSaved = true;
                        setLoading(false);
                        showMessage("Selected time saved.");
                        renderSlots();
                    }

                    @Override
                    public void onError(Exception error) {
                        if (isFinishing() || isDestroyed()) return;
                        busy = false;
                        setLoading(false);
                        showMessage(errorMessage(error,
                                "Unable to save this time. Refresh recommendations and try again."));
                    }
                });
    }

    private void openModifyDialog(RecommendedSlot slot) {
        Calendar start = Calendar.getInstance(bogota);
        start.setTimeInMillis(slot.getStartTimeMillis());
        Calendar end = Calendar.getInstance(bogota);
        end.setTimeInMillis(slot.getEndTimeMillis());
        int duration = Math.max(15, (int) ((slot.getEndTimeMillis() - slot.getStartTimeMillis()) / 60_000L));
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(4));
        TextView note = text("Today in Bogotá (UTC−05:00). Choose a time when everyone is available.",
                12, R.color.brand_slate, false);
        form.addView(note);
        AppCompatButton startButton = new AppCompatButton(this);
        startButton.setAllCaps(false);
        AppCompatButton endButton = new AppCompatButton(this);
        endButton.setAllCaps(false);
        form.addView(startButton);
        form.addView(endButton);
        Runnable updateLabels = () -> {
            startButton.setText("Start: " + formatTime(start.getTimeInMillis()));
            endButton.setText("End: " + formatTime(end.getTimeInMillis()));
        };
        updateLabels.run();
        startButton.setOnClickListener(v -> new TimePickerDialog(this, (picker, hour, minute) -> {
            start.set(Calendar.HOUR_OF_DAY, hour);
            start.set(Calendar.MINUTE, minute);
            start.set(Calendar.SECOND, 0);
            if (end.getTimeInMillis() <= start.getTimeInMillis()) {
                end.setTimeInMillis(start.getTimeInMillis() + duration * 60_000L);
            }
            updateLabels.run();
        }, start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE), true).show());
        endButton.setOnClickListener(v -> new TimePickerDialog(this, (picker, hour, minute) -> {
            end.set(Calendar.HOUR_OF_DAY, hour);
            end.set(Calendar.MINUTE, minute);
            end.set(Calendar.SECOND, 0);
            updateLabels.run();
        }, end.get(Calendar.HOUR_OF_DAY), end.get(Calendar.MINUTE), true).show());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Modify recommended time")
                .setView(form)
                .setNegativeButton("Dismiss", null)
                .setPositiveButton("Save Changes", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (end.getTimeInMillis() <= start.getTimeInMillis()) {
                Toast.makeText(this, "End time must be after start time.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (start.getTimeInMillis() == slot.getStartTimeMillis()
                    && end.getTimeInMillis() == slot.getEndTimeMillis()) {
                Toast.makeText(this, "Choose a different time to modify this suggestion.", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            saveDecision(slot, true, start, end);
        }));
        dialog.show();
    }

    private void showDurationInput() {
        batch = null;
        slotViews.clear();
        slotsContainer.removeAllViews();
        presented.clear();
        presenting.clear();
        afterPresentation.clear();
        dateLabel.setVisibility(View.GONE);
        filterRow.setVisibility(View.GONE);
        slotsContainer.setVisibility(View.GONE);
        emptyMessage.setVisibility(View.GONE);
        noMatchingSlots.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        changeDurationButton.setVisibility(View.GONE);
        durationInput.setText(durationMinutes > 0 ? Integer.toString(durationMinutes) : "");
        durationControls.setVisibility(View.VISIBLE);
        message.setVisibility(View.GONE);
    }

    private void setLoading(boolean loadingNow) {
        loading.setVisibility(loadingNow ? View.VISIBLE : View.GONE);
        findButton.setEnabled(!loadingNow && !selectionSaved);
        retryButton.setEnabled(!loadingNow && !selectionSaved);
        changeDurationButton.setEnabled(!loadingNow && !selectionSaved);
    }

    private void showMessage(String text) {
        message.setText(text);
        message.setVisibility(View.VISIBLE);
    }

    private void updateFeedbackMessage() {
        if (pendingFeedbackMessage == null) {
            feedbackError.setVisibility(View.GONE);
            retryFeedbackButton.setVisibility(View.GONE);
        } else {
            feedbackError.setText(pendingFeedbackMessage);
            feedbackError.setVisibility(View.VISIBLE);
            retryFeedbackButton.setVisibility(View.VISIBLE);
        }
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(getColor(color));
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private String formatRange(RecommendedSlot slot) {
        SimpleDateFormat format = new SimpleDateFormat("EEE, MMM d · h:mm a", Locale.US);
        format.setTimeZone(bogota);
        return format.format(new Date(slot.getStartTimeMillis())) + " – "
                + formatTime(slot.getEndTimeMillis());
    }

    private String formatTime(long millis) {
        SimpleDateFormat format = new SimpleDateFormat("h:mm a", Locale.US);
        format.setTimeZone(bogota);
        return format.format(new Date(millis));
    }

    private String toUtcIso(Calendar bogotaTime) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(bogotaTime.getTime());
    }

    private Long parseIso(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        for (String pattern : new String[]{"yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX"}) {
            SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
            parser.setLenient(false);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            ParsePosition position = new ParsePosition(0);
            Date parsed = parser.parse(value, position);
            if (parsed != null && position.getIndex() == value.length()) return parsed.getTime();
        }
        return null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String errorMessage(Exception error, String fallback) {
        String value = error == null ? null : error.getMessage();
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
