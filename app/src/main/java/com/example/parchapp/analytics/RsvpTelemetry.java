package com.example.parchapp.analytics;

import com.example.parchapp.domain.model.AlertItem;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Emits the BQ5 RSVP timing event after the backend confirms an invitation response. */
public final class RsvpTelemetry {
    private RsvpTelemetry() { }

    public static void recordSubmitted(AlertItem invitation, String response, long respondedAtMillis) {
        recordSubmitted(invitation, response, respondedAtMillis, null);
    }

    public static void recordSubmitted(AlertItem invitation, String response, long respondedAtMillis,
                                       AlertItem reminder) {
        Map<String, Object> params = parameters(invitation, response, respondedAtMillis);
        if (params == null) return;
        try {
            AnalyticsTracker tracker = AnalyticsTracker.getInstance();
            tracker.track(AnalyticsEvents.RSVP_SUBMITTED, params);
            Map<String, Object> afterReminder = responseAfterReminderParameters(
                    invitation, response, respondedAtMillis, reminder);
            if (afterReminder != null) tracker.track(AnalyticsEvents.RESPONSE_AFTER_REMINDER, afterReminder);
        } catch (RuntimeException ignored) {
            // The invitation response is already saved; analytics must not block the UI.
        }
    }

    static Map<String, Object> parameters(AlertItem invitation, String response, long respondedAtMillis) {
        if (invitation == null || invitation.getGroupSize() <= 0
                || blank(invitation.getActivityId()) || blank(invitation.getCreatedAt())
                || !invitation.isActivityInvitation() || invitation.getResponse() != null
                || !("accepted".equals(response) || "maybe".equals(response) || "declined".equals(response))) {
            return null;
        }
        Long invitedAt = parseIsoMillis(invitation.getCreatedAt());
        if (invitedAt == null) return null;
        Map<String, Object> params = new HashMap<>();
        params.put(AnalyticsEvents.PARAM_ACTIVITY_ID, invitation.getActivityId());
        params.put(AnalyticsEvents.PARAM_GROUP_SIZE, invitation.getGroupSize());
        params.put(AnalyticsEvents.PARAM_RESPONSE_MS, Math.max(0L, respondedAtMillis - invitedAt));
        params.put(AnalyticsEvents.PARAM_RESPONSE_AT_MS, respondedAtMillis);
        params.put(AnalyticsEvents.PARAM_RESPONSE, "accepted".equals(response)
                ? "going" : "maybe".equals(response) ? "maybe" : "declined");
        return params;
    }

    static Map<String, Object> responseAfterReminderParameters(AlertItem invitation, String response,
                                                                long respondedAtMillis, AlertItem reminder) {
        if (invitation == null || reminder == null || !reminder.isRsvpReminder()
                || blank(invitation.getActivityId()) || !invitation.getActivityId().equals(reminder.getActivityId())) {
            return null;
        }
        Long sentAt = ReminderTelemetry.reminderSentAtMillis(reminder);
        if (sentAt == null || sentAt > respondedAtMillis) return null;
        Map<String, Object> params = new HashMap<>();
        params.put(AnalyticsEvents.PARAM_ACTIVITY_ID, invitation.getActivityId());
        params.put(AnalyticsEvents.PARAM_REMINDER_ID, reminder.getReminderId());
        params.put(AnalyticsEvents.PARAM_REMINDER_STRATEGY, reminder.getReminderStrategy());
        params.put(AnalyticsEvents.PARAM_REMINDER_SEQUENCE, reminder.getReminderSequence());
        params.put(AnalyticsEvents.PARAM_DELAY_MINUTES, reminder.getReminderDelayMinutes());
        params.put(AnalyticsEvents.PARAM_RESPONSE_DELAY_MS, Math.max(0L, respondedAtMillis - sentAt));
        params.put(AnalyticsEvents.PARAM_RESPONSE_AT_MS, respondedAtMillis);
        params.put(AnalyticsEvents.PARAM_RESPONSE, response);
        return params;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static Long parseIsoMillis(String value) {
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
}
