package com.example.parchapp.analytics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.parchapp.domain.model.AlertItem;

import org.junit.Test;

import java.util.Map;

public class ReminderTelemetryTest {
    @Test
    public void recordsServerSentRsvpReminderMetadataForBq6() {
        AlertItem reminder = new AlertItem("evt_reminder-activity-1-1", "reminder", "Pending response",
                "Study group", "2026-10-29T14:05:00.000Z", false, null,
                "activity-1", "group-1", 0, "reminder-activity-1-1", "rsvp",
                "rsvp_after_30_min", 1, 30, "2026-10-29T14:05:00.000Z");

        Map<String, Object> params = ReminderTelemetry.reminderSentParameters(reminder);

        assertEquals("activity-1", params.get(AnalyticsEvents.PARAM_ACTIVITY_ID));
        assertEquals("reminder-activity-1-1", params.get(AnalyticsEvents.PARAM_REMINDER_ID));
        assertEquals("rsvp", params.get(AnalyticsEvents.PARAM_REMINDER_PURPOSE));
        assertEquals("rsvp_after_30_min", params.get(AnalyticsEvents.PARAM_REMINDER_STRATEGY));
        assertEquals(30, params.get(AnalyticsEvents.PARAM_DELAY_MINUTES));
        assertEquals(1_793_282_700_000L, params.get(AnalyticsEvents.PARAM_REMINDER_SENT_AT_MS));
    }

    @Test
    public void ignoresSyntheticReminderWithoutServerDeliveryMetadata() {
        AlertItem synthetic = new AlertItem("rem_upcoming", "reminder", "Starts within an hour",
                "Study group", "2026-10-29T14:05:00.000Z", false, null,
                "activity-1", "group-1");
        assertNull(ReminderTelemetry.reminderSentParameters(synthetic));
    }
}
