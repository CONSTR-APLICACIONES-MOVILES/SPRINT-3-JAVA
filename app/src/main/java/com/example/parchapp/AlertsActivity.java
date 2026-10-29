package com.example.parchapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.analytics.AnalyticsEvents;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.example.parchapp.data.AlertsRepository;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.domain.PlanContext;
import com.example.parchapp.domain.model.AlertItem;
import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.analytics.ReminderTelemetry;
import com.example.parchapp.util.Callback;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class AlertsActivity extends AppCompatActivity {
    private final List<AlertItem> alerts = new ArrayList<>();
    private LinearLayout alertsContainer;
    private LinearLayout remindersContainer;
    private TextView alertsState;
    private TextView remindersState;
    private TextView unreadBadge;
    private ProgressBar alertsLoading;
    private View retryButton;
    private EditText searchInput;
    private String activeCategory = "all";
    private String activePriority = "all";
    private int reminderLoadGeneration;
    private boolean alertsLoadFailed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alerts);

        alertsContainer = findViewById(R.id.alerts_container);
        remindersContainer = findViewById(R.id.reminders_container);
        alertsState = findViewById(R.id.alerts_state_text);
        remindersState = findViewById(R.id.reminders_state_text);
        unreadBadge = findViewById(R.id.alerts_unread_count);
        alertsLoading = findViewById(R.id.alerts_loading);
        retryButton = findViewById(R.id.alerts_retry_button);
        searchInput = findViewById(R.id.alerts_search_input);

        // bq8: alerts counts as used when this screen opens (not again on rotation)
        if (savedInstanceState == null) {
            AnalyticsTracker.getInstance().trackFeature(AnalyticsEvents.FEATURE_ALERTS, "alerts");
        }
        findViewById(R.id.alerts_back_button).setOnClickListener(v -> finish());
        findViewById(R.id.alerts_mark_all_read).setOnClickListener(v -> markAllRead());
        retryButton.setOnClickListener(v -> loadAlerts());
        findViewById(R.id.reminders_retry_button).setOnClickListener(v -> loadReminderActivities());
        bindFilters();
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderAlerts();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        findViewById(R.id.nav_home).setOnClickListener(v -> openScreen(DashboardActivity.class));
        findViewById(R.id.nav_groups).setOnClickListener(v -> openScreen(GroupsActivity.class));
        findViewById(R.id.nav_schedule).setOnClickListener(v -> openScreen(ScheduleActivity.class));
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadAlerts();
        loadReminderActivities();
    }

    private void bindFilters() {
        findViewById(R.id.priority_all).setOnClickListener(v -> setPriority("all"));
        findViewById(R.id.priority_high).setOnClickListener(v -> setPriority("high"));
        findViewById(R.id.priority_medium).setOnClickListener(v -> setPriority("medium"));
        findViewById(R.id.priority_low).setOnClickListener(v -> setPriority("low"));

        findViewById(R.id.alerts_filter_all).setOnClickListener(v -> setCategory("all"));
        findViewById(R.id.alerts_filter_priority).setOnClickListener(v -> setCategory("priority"));
        findViewById(R.id.alerts_filter_invitations).setOnClickListener(v -> setCategory("invitations"));
        findViewById(R.id.alerts_filter_schedule).setOnClickListener(v -> setCategory("schedule"));
        findViewById(R.id.alerts_filter_friends).setOnClickListener(v -> setCategory("friends"));
        findViewById(R.id.alerts_filter_groups).setOnClickListener(v -> setCategory("groups"));
        findViewById(R.id.alerts_filter_unread).setOnClickListener(v -> setCategory("unread"));
        updateFilterStyles();
    }

    private void setPriority(String priority) {
        activePriority = priority;
        updateFilterStyles();
        renderAlerts();
    }

    private void setCategory(String category) {
        activeCategory = category;
        updateFilterStyles();
        renderAlerts();
    }

    private void updateFilterStyles() {
        int[] priorityIds = {R.id.priority_all, R.id.priority_high, R.id.priority_medium, R.id.priority_low};
        String[] priorities = {"all", "high", "medium", "low"};
        for (int i = 0; i < priorityIds.length; i++) {
            styleFilter(priorityIds[i], activePriority.equals(priorities[i]));
        }

        int[] categoryIds = {R.id.alerts_filter_all, R.id.alerts_filter_priority,
                R.id.alerts_filter_invitations, R.id.alerts_filter_schedule,
                R.id.alerts_filter_friends, R.id.alerts_filter_groups, R.id.alerts_filter_unread};
        String[] categories = {"all", "priority", "invitations", "schedule", "friends", "groups", "unread"};
        for (int i = 0; i < categoryIds.length; i++) {
            styleFilter(categoryIds[i], activeCategory.equals(categories[i]));
        }
    }

    private void styleFilter(int id, boolean selected) {
        View view = findViewById(id);
        view.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        ((TextView) view).setTextColor(getColor(selected ? R.color.brand_primary : R.color.brand_dark));
    }

    private void loadAlerts() {
        alertsLoadFailed = false;
        alertsLoading.setVisibility(View.VISIBLE);
        alertsState.setVisibility(View.VISIBLE);
        alertsState.setText(R.string.alerts_loading);
        alertsContainer.removeAllViews();
        retryButton.setVisibility(View.GONE);
        findViewById(R.id.alerts_mark_all_read).setEnabled(false);
        if (UserSession.getCurrentUser() == null) {
            alertsLoading.setVisibility(View.GONE);
            alerts.clear();
            unreadBadge.setVisibility(View.GONE);
            alertsState.setText(R.string.alerts_sign_in);
            return;
        }
        Repositories.alerts().getAlerts(new Callback<List<AlertItem>>() {
            @Override
            public void onSuccess(List<AlertItem> result) {
                if (isFinishing() || isDestroyed()) return;
                alertsLoading.setVisibility(View.GONE);
                alerts.clear();
                alerts.addAll(result);
                ReminderTelemetry.recordReminderAlerts(AlertsActivity.this, result);
                renderAlerts();
            }

            @Override
            public void onError(Exception error) {
                if (isFinishing() || isDestroyed()) return;
                alertsLoadFailed = true;
                alertsLoading.setVisibility(View.GONE);
                alerts.clear();
                unreadBadge.setVisibility(View.GONE);
                alertsState.setText(R.string.alerts_load_error);
                alertsState.setVisibility(View.VISIBLE);
                retryButton.setVisibility(View.VISIBLE);
            }
        });
    }

    private void renderAlerts() {
        if (alertsContainer == null || alertsLoadFailed || alertsLoading.getVisibility() == View.VISIBLE) return;
        alertsContainer.removeAllViews();
        updateUnreadCount();

        List<AlertItem> filtered = new ArrayList<>();
        String query = searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        for (AlertItem alert : alerts) {
            if (!matchesFilters(alert) || !matchesSearch(alert, query)) continue;
            filtered.add(alert);
        }

        if (filtered.isEmpty()) {
            alertsState.setText(alerts.isEmpty() ? R.string.alerts_empty : R.string.alerts_no_filter_match);
            alertsState.setVisibility(View.VISIBLE);
            retryButton.setVisibility(View.GONE);
            return;
        }
        alertsState.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        for (AlertItem alert : filtered) {
            View card = LayoutInflater.from(this).inflate(R.layout.item_alert_card, alertsContainer, false);
            bindAlertCard(card, alert);
            alertsContainer.addView(card);
        }
    }

    private boolean matchesFilters(AlertItem alert) {
        if (!"all".equals(activePriority) && !activePriority.equals(alert.getPriorityKey())) return false;
        switch (activeCategory) {
            case "priority": return "high".equals(alert.getPriorityKey());
            case "unread": return !alert.isRead();
            case "all": return true;
            default: return activeCategory.equals(alert.getCategoryKey());
        }
    }

    private boolean matchesSearch(AlertItem alert, String query) {
        if (query.isEmpty()) return true;
        return alert.getTitle().toLowerCase(Locale.ROOT).contains(query)
                || alert.getDescription().toLowerCase(Locale.ROOT).contains(query);
    }

    private void bindAlertCard(View card, AlertItem alert) {
        int priorityColor = priorityColor(alert.getPriorityKey());
        card.findViewById(R.id.alert_priority_accent).setBackgroundTintList(
                ColorStateList.valueOf(getColor(priorityColor)));
        ImageView icon = card.findViewById(R.id.alert_icon);
        icon.setImageResource(alertIcon(alert));
        icon.setImageTintList(ColorStateList.valueOf(getColor(priorityColor)));
        icon.setBackgroundTintList(ColorStateList.valueOf(getColor(iconColor(alert))));
        TextView category = card.findViewById(R.id.alert_category);
        category.setText(categoryLabel(alert));
        TextView priority = card.findViewById(R.id.alert_priority);
        priority.setText(getString(R.string.alerts_priority_label,
                getString(priorityString(alert.getPriorityKey()))));
        priority.setTextColor(getColor(priorityColor));
        ((TextView) card.findViewById(R.id.alert_timestamp)).setText(formatTimestamp(alert.getCreatedAt()));
        ((TextView) card.findViewById(R.id.alert_title)).setText(alert.getTitle());
        ((TextView) card.findViewById(R.id.alert_description)).setText(alert.getDescription());
        card.findViewById(R.id.alert_unread_dot).setVisibility(alert.isRead() ? View.INVISIBLE : View.VISIBLE);

        TextView responseLabel = card.findViewById(R.id.alert_response_label);
        if (alert.getResponse() != null && !alert.getResponse().isEmpty()) {
            responseLabel.setText(getString(R.string.alerts_response_saved, responseLabel(alert.getResponse())));
            responseLabel.setVisibility(View.VISIBLE);
        } else {
            responseLabel.setVisibility(View.GONE);
        }

        bindAlertActions(card, alert);
        card.setOnClickListener(v -> {
            if (!alert.isRead()) markRead(Collections.singletonList(alert.getId()));
        });
    }

    private void bindAlertActions(View card, AlertItem alert) {
        LinearLayout actions = card.findViewById(R.id.alert_actions);
        AppCompatButton accept = card.findViewById(R.id.alert_accept_button);
        AppCompatButton maybe = card.findViewById(R.id.alert_maybe_button);
        AppCompatButton decline = card.findViewById(R.id.alert_decline_button);
        AppCompatButton markRead = card.findViewById(R.id.alert_mark_read_button);
        accept.setVisibility(View.GONE);
        maybe.setVisibility(View.GONE);
        decline.setVisibility(View.GONE);
        markRead.setVisibility(View.GONE);

        boolean pendingActivity = alert.isActivityInvitation()
                && (alert.getResponse() == null || alert.getResponse().isEmpty());
        boolean pendingGroup = alert.isGroupInvitation();
        if (pendingActivity || pendingGroup) {
            actions.setVisibility(View.VISIBLE);
            accept.setVisibility(View.VISIBLE);
            decline.setVisibility(View.VISIBLE);
            if (pendingActivity) {
                maybe.setVisibility(View.VISIBLE);
                maybe.setOnClickListener(v -> respondToActivityInvitation(alert, "maybe", actions));
            }
            accept.setOnClickListener(v -> {
                if (pendingActivity) respondToActivityInvitation(alert, "accepted", actions);
                else respondToGroupInvitation(alert, "accepted", actions);
            });
            decline.setOnClickListener(v -> {
                if (pendingActivity) respondToActivityInvitation(alert, "declined", actions);
                else respondToGroupInvitation(alert, "declined", actions);
            });
        } else if (!alert.isRead()) {
            actions.setVisibility(View.VISIBLE);
            markRead.setVisibility(View.VISIBLE);
            markRead.setOnClickListener(v -> markRead(Collections.singletonList(alert.getId())));
        } else {
            actions.setVisibility(View.GONE);
        }
    }

    private void respondToActivityInvitation(AlertItem alert, String response, View actions) {
        setActionEnabled(actions, false);
        Repositories.alerts().respondToActivityInvitation(alert.getActivityId(), response, new Callback<Void>() {
            @Override public void onSuccess(Void ignored) {
                if (!isFinishing() && !isDestroyed()) {
                    ReminderTelemetry.recordInvitationResponse(alert.getActivityId(), response);
                    loadAlerts();
                }
            }
            @Override public void onError(Exception error) {
                if (!isFinishing() && !isDestroyed()) actionFailed(actions);
            }
        });
    }

    private void respondToGroupInvitation(AlertItem alert, String response, View actions) {
        setActionEnabled(actions, false);
        Repositories.alerts().respondToGroupInvitation(alert.getGroupId(), response, new Callback<Void>() {
            @Override public void onSuccess(Void ignored) {
                if (isFinishing() || isDestroyed()) return;
                loadAlerts();
                loadReminderActivities();
            }
            @Override public void onError(Exception error) {
                if (!isFinishing() && !isDestroyed()) actionFailed(actions);
            }
        });
    }

    private void markRead(List<String> ids) {
        Repositories.alerts().markAlertsRead(ids, new Callback<Void>() {
            @Override public void onSuccess(Void ignored) {
                if (!isFinishing() && !isDestroyed()) loadAlerts();
            }
            @Override public void onError(Exception error) {
                if (!isFinishing() && !isDestroyed()) {
                    Toast.makeText(AlertsActivity.this, R.string.alerts_action_error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void markAllRead() {
        List<String> unreadIds = new ArrayList<>();
        for (AlertItem alert : alerts) if (!alert.isRead()) unreadIds.add(alert.getId());
        if (!unreadIds.isEmpty()) markRead(unreadIds);
    }

    private void actionFailed(View actions) {
        setActionEnabled(actions, true);
        Toast.makeText(this, R.string.alerts_action_error, Toast.LENGTH_SHORT).show();
    }

    private void setActionEnabled(View actions, boolean enabled) {
        for (int id : new int[]{R.id.alert_accept_button, R.id.alert_maybe_button,
                R.id.alert_decline_button, R.id.alert_mark_read_button}) {
            actions.findViewById(id).setEnabled(enabled);
        }
    }

    private void updateUnreadCount() {
        int unread = 0;
        for (AlertItem alert : alerts) if (!alert.isRead()) unread++;
        if (unread == 0) {
            unreadBadge.setVisibility(View.GONE);
        } else {
            unreadBadge.setVisibility(View.VISIBLE);
            unreadBadge.setText(getString(R.string.alerts_unread_count, unread));
        }
        findViewById(R.id.alerts_mark_all_read).setEnabled(unread > 0);
    }

    private void loadReminderActivities() {
        remindersContainer.removeAllViews();
        remindersState.setVisibility(View.VISIBLE);
        remindersState.setText(R.string.reminders_loading);
        findViewById(R.id.reminders_retry_button).setVisibility(View.GONE);
        UserProfile user = UserSession.getCurrentUser();
        if (user == null) {
            remindersState.setText(R.string.reminders_sign_in);
            return;
        }
        int generation = ++reminderLoadGeneration;
        Repositories.activities().getActivities(user.getId(), new Callback<List<PlannedActivity>>() {
            @Override public void onSuccess(List<PlannedActivity> activities) {
                if (isFinishing() || isDestroyed() || generation != reminderLoadGeneration) return;
                List<PlannedActivity> upcoming = upcomingActivities(activities);
                if (upcoming.isEmpty()) {
                    remindersState.setText(R.string.reminders_empty);
                    return;
                }
                remindersState.setVisibility(View.GONE);
                for (PlannedActivity activity : upcoming) bindReminderCard(activity, user.getId());
            }

            @Override public void onError(Exception error) {
                if (!isFinishing() && !isDestroyed() && generation == reminderLoadGeneration) {
                    remindersState.setText(R.string.reminders_load_error);
                    findViewById(R.id.reminders_retry_button).setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private List<PlannedActivity> upcomingActivities(List<PlannedActivity> activities) {
        Calendar now = Calendar.getInstance(PlanContext.bogota());
        int nowMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        List<PlannedActivity> upcoming = new ArrayList<>();
        for (PlannedActivity activity : activities) {
            Integer dayOffset = PlanContext.dayOffset(activity.getDate(), now);
            int startMinute = PlanContext.startMinute(activity.getTime());
            if (dayOffset == null || dayOffset < 0 || startMinute < 0) continue;
            if (dayOffset == 0 && startMinute <= nowMinute) continue;
            upcoming.add(activity);
        }
        upcoming.sort(Comparator
                .comparingInt((PlannedActivity activity) -> PlanContext.dayOffset(activity.getDate(), now))
                .thenComparingInt(activity -> PlanContext.startMinute(activity.getTime())));
        return upcoming;
    }

    private void bindReminderCard(PlannedActivity activity, String userId) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_activity_reminder, remindersContainer, false);
        ((TextView) card.findViewById(R.id.reminder_activity_title)).setText(activity.getTitle());
        ((TextView) card.findViewById(R.id.reminder_activity_schedule)).setText(
                joinNonEmpty(activity.getDate(), activity.getTime(), activity.getLocation()));
        TextView setting = card.findViewById(R.id.reminder_setting_text);
        TextView state = card.findViewById(R.id.reminder_state_text);
        AppCompatButton button = card.findViewById(R.id.reminder_setting_button);
        bindReminderValue(setting, activity.getReminderMinutesBefore());
        remindersContainer.addView(card);
        if (isBlank(activity.getStartTime()) || isBlank(activity.getEndTime())) {
            state.setText(R.string.reminder_settings_no_schedule);
            state.setVisibility(View.VISIBLE);
            button.setVisibility(View.GONE);
        } else if (userId.equals(activity.getCreatedBy())) {
            button.setVisibility(View.VISIBLE);
            button.setOnClickListener(v -> showReminderOptions(activity, setting, state, button));
            state.setVisibility(View.GONE);
        } else {
            button.setVisibility(View.GONE);
            state.setText(R.string.reminder_settings_organizer_only);
            state.setVisibility(View.VISIBLE);
        }
    }

    private void showReminderOptions(PlannedActivity activity, TextView setting, TextView state,
                                    AppCompatButton button) {
        String[] labels = {getString(R.string.reminder_option_none), getString(R.string.reminder_option_15),
                getString(R.string.reminder_option_30), getString(R.string.reminder_option_60)};
        int[] values = {0, 15, 30, 60};
        new AlertDialog.Builder(this)
                .setTitle(R.string.reminder_options_title)
                .setItems(labels, (dialog, selected) -> saveReminder(activity, values[selected], setting, state, button))
                .show();
    }

    private void saveReminder(PlannedActivity activity, int minutes, TextView setting,
                              TextView state, AppCompatButton button) {
        button.setEnabled(false);
        state.setText(R.string.reminder_settings_saving);
        state.setVisibility(View.VISIBLE);
        Repositories.activities().setActivityReminder(activity.getId(), minutes, new Callback<Void>() {
            @Override public void onSuccess(Void ignored) {
                if (isFinishing() || isDestroyed()) return;
                button.setEnabled(true);
                bindReminderValue(setting, minutes);
                state.setText(minutes == 0 ? R.string.reminder_settings_disabled : R.string.reminder_settings_saved);
            }

            @Override public void onError(Exception error) {
                if (isFinishing() || isDestroyed()) return;
                button.setEnabled(true);
                state.setText(R.string.reminder_settings_save_error);
            }
        });
    }

    private void bindReminderValue(TextView setting, Integer minutes) {
        if (minutes == null || minutes == 0) {
            setting.setText(R.string.reminder_settings_none);
        } else {
            String value = minutes == 60 ? getString(R.string.reminder_option_60)
                    : getString(minutes == 15 ? R.string.reminder_option_15 : R.string.reminder_option_30);
            setting.setText(getString(R.string.reminder_settings_value, value));
        }
    }

    private void openScreen(Class<? extends AppCompatActivity> screen) {
        if (getClass() == screen) return;
        startActivity(new Intent(this, screen)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    private String categoryLabel(AlertItem alert) {
        switch (alert.getKind()) {
            case "invitation": return getString(R.string.alerts_category_invitation);
            case "groupInvite": return getString(R.string.alerts_category_group_invite);
            case "reminder": return getString(R.string.alerts_category_reminder);
            case "friendRequest": return getString(R.string.alerts_category_friend);
            default: return getString(R.string.alerts_category_schedule);
        }
    }

    private int alertIcon(AlertItem alert) {
        switch (alert.getKind()) {
            case "invitation": return R.drawable.ic_mail;
            case "groupInvite": return R.drawable.ic_group;
            case "reminder": return R.drawable.ic_timer;
            case "timeChanged": return R.drawable.ic_schedule_week;
            case "friendRequest": return R.drawable.ic_person_add;
            default: return R.drawable.ic_notifications;
        }
    }

    private int priorityColor(String priority) {
        switch (priority) {
            case "high": return R.color.brand_rose;
            case "medium": return R.color.brand_amber;
            default: return R.color.brand_success;
        }
    }

    private int iconColor(AlertItem alert) {
        return alert.getKind().equals("groupInvite") ? R.color.brand_purple_background
                : alert.getPriorityKey().equals("high") ? R.color.brand_rose_background : R.color.brand_chip_background;
    }

    private int priorityString(String priority) {
        switch (priority) {
            case "high": return R.string.alerts_priority_high;
            case "medium": return R.string.alerts_priority_medium;
            default: return R.string.alerts_priority_low;
        }
    }

    private String responseLabel(String response) {
        switch (response) {
            case "accepted": return getString(R.string.alerts_accept);
            case "maybe": return getString(R.string.alerts_maybe);
            default: return getString(R.string.alerts_decline);
        }
    }

    private String formatTimestamp(String value) {
        Date date = parseIso(value);
        if (date == null) return "";
        return new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(date);
    }

    private Date parseIso(String value) {
        if (value == null || value.isEmpty()) return null;
        for (String pattern : new String[]{"yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX"}) {
            SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
            parser.setLenient(false);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            ParsePosition position = new ParsePosition(0);
            Date parsed = parser.parse(value, position);
            if (parsed != null && position.getIndex() == value.length()) return parsed;
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String joinNonEmpty(String... values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (result.length() > 0) result.append(" · ");
            result.append(value.trim());
        }
        return result.length() == 0 ? getString(R.string.alerts_schedule_unspecified) : result.toString();
    }
}
