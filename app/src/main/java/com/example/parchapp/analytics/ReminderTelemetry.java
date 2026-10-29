package com.example.parchapp.analytics;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.parchapp.domain.model.AlertItem;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.TimeZone;

/** Records reminder deliveries once per alert and invitation responses for BQ6. */
public final class ReminderTelemetry {
    private static final String PREFS = "parchapp_bq6";
    private static final String KEY_RECORDED_REMINDERS = "recorded_reminder_ids";
    private static final int MAX_RECORDED_REMINDERS = 1000;

    private ReminderTelemetry() { }

    public static synchronized void recordReminderAlerts(Context context, Iterable<AlertItem> alerts) {
        AnalyticsTracker tracker = AnalyticsTracker.getInstance();
        if (!tracker.isAnalyticsConsentEnabled()) return;

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> recorded = new HashSet<>(prefs.getStringSet(KEY_RECORDED_REMINDERS, Collections.emptySet()));
        boolean changed = false;
        for (AlertItem alert : alerts) {
            Map<String, Object> params = reminderSentParameters(alert);
            if (params == null || !recorded.add(alert.getId())) continue;
            tracker.track(AnalyticsEvents.REMINDER_SENT, params);
            changed = true;
        }
        if (changed) {
            while (recorded.size() > MAX_RECORDED_REMINDERS) {
                recorded.remove(recorded.iterator().next());
            }
            prefs.edit().putStringSet(KEY_RECORDED_REMINDERS, recorded).apply();
        }
    }

    static Map<String, Object> reminderSentParameters(AlertItem alert) {
        if (!"reminder".equals(alert.getKind()) || isBlank(alert.getId())
                || isBlank(alert.getActivityId()) || isBlank(alert.getStrategy())
                || alert.getSequence() <= 0) {
            return null;
        }
        Map<String, Object> params = new HashMap<>();
        params.put(AnalyticsEvents.PARAM_ACTIVITY_ID, alert.getActivityId());
        params.put(AnalyticsEvents.PARAM_REMINDER_ID, alert.getId());
        params.put(AnalyticsEvents.PARAM_REMINDER_STRATEGY, alert.getStrategy());
        params.put(AnalyticsEvents.PARAM_REMINDER_SEQUENCE, alert.getSequence());
        params.put(AnalyticsEvents.PARAM_SOURCE, "alerts");
        Long sentAt = parseIsoMillis(alert.getCreatedAt());
        if (sentAt != null) params.put(AnalyticsEvents.PARAM_REMINDER_SENT_AT_MS, sentAt);
        return params;
    }

    public static void recordInvitationResponse(String activityId, String response) {
        if (isBlank(activityId) || isBlank(response)) return;
        Map<String, Object> params = new HashMap<>();
        params.put(AnalyticsEvents.PARAM_ACTIVITY_ID, activityId);
        params.put(AnalyticsEvents.PARAM_RESPONSE, response);
        params.put(AnalyticsEvents.PARAM_SOURCE, "alerts");
        AnalyticsTracker.getInstance().track(AnalyticsEvents.RSVP_SUBMITTED, params);
    }

    static Long parseIsoMillis(String value) {
        if (isBlank(value)) return null;
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

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
