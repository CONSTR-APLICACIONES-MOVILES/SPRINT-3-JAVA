package com.example.parchapp.analytics;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.parchapp.domain.model.AlertItem;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/** Client telemetry for reminder delivery; the measured delivery time comes from the server alert. */
public final class ReminderTelemetry {
    private static final String PREFS = "parchapp_bq6";
    private static final String KEY_RECORDED_REMINDERS = "recorded_reminder_ids";
    private static final int MAX_RECORDED_REMINDERS = 1000;

    private ReminderTelemetry() { }

    public static synchronized void recordReminderAlerts(Context context, Iterable<AlertItem> alerts) {
        AnalyticsTracker tracker = AnalyticsTracker.getInstance();
        if (!tracker.isAnalyticsConsentEnabled()) return;
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> recorded = new HashSet<>(preferences.getStringSet(
                KEY_RECORDED_REMINDERS, Collections.emptySet()));
        boolean changed = false;
        for (AlertItem alert : alerts) {
            Map<String, Object> params = reminderSentParameters(alert);
            if (params == null || !recorded.add(alert.getId())) continue;
            try {
                tracker.track(AnalyticsEvents.REMINDER_SENT, params);
                changed = true;
            } catch (RuntimeException ignored) {
                // The alert remains usable if analytics cannot accept the event.
            }
        }
        if (changed) {
            while (recorded.size() > MAX_RECORDED_REMINDERS) {
                recorded.remove(recorded.iterator().next());
            }
            preferences.edit().putStringSet(KEY_RECORDED_REMINDERS, recorded).apply();
        }
    }

    static Map<String, Object> reminderSentParameters(AlertItem alert) {
        if (alert == null || !alert.isDeliveredReminder() || blank(alert.getActivityId())
                || alert.getReminderDelayMinutes() == null || alert.getReminderDelayMinutes() <= 0) {
            return null;
        }
        Long sentAt = parseIsoMillis(alert.getReminderSentAt());
        if (sentAt == null) return null;
        Map<String, Object> params = new HashMap<>();
        params.put(AnalyticsEvents.PARAM_ACTIVITY_ID, alert.getActivityId());
        params.put(AnalyticsEvents.PARAM_REMINDER_ID, alert.getReminderId());
        params.put(AnalyticsEvents.PARAM_REMINDER_PURPOSE, alert.getReminderPurpose());
        params.put(AnalyticsEvents.PARAM_REMINDER_STRATEGY, alert.getReminderStrategy());
        params.put(AnalyticsEvents.PARAM_REMINDER_SEQUENCE, alert.getReminderSequence());
        params.put(AnalyticsEvents.PARAM_DELAY_MINUTES, alert.getReminderDelayMinutes());
        params.put(AnalyticsEvents.PARAM_REMINDER_SENT_AT_MS, sentAt);
        return params;
    }

    public static Long reminderSentAtMillis(AlertItem alert) {
        return alert == null || !alert.isDeliveredReminder()
                ? null : parseIsoMillis(alert.getReminderSentAt());
    }

    static Long parseIsoMillis(String value) {
        if (blank(value)) return null;
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

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
