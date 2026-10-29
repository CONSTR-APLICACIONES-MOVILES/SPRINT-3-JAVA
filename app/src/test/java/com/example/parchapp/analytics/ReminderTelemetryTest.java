package com.example.parchapp.analytics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.parchapp.domain.model.AlertItem;

import org.junit.Test;

import java.util.Map;

public class ReminderTelemetryTest {

    @Test
    public void createsBq6ParametersFromDeliveredReminderAlert() {
        AlertItem reminder = new AlertItem("evt_reminder-1", "reminder", "Upcoming activity",
                "Math session", "2026-10-28T16:00:00.000Z", false, null,
                "activity-1", "group-1", "single_after_60_min", 1);

        Map<String, Object> params = ReminderTelemetry.reminderSentParameters(reminder);

        assertEquals("activity-1", params.get(AnalyticsEvents.PARAM_ACTIVITY_ID));
        assertEquals("evt_reminder-1", params.get(AnalyticsEvents.PARAM_REMINDER_ID));
        assertEquals("single_after_60_min", params.get(AnalyticsEvents.PARAM_REMINDER_STRATEGY));
        assertEquals(Integer.valueOf(1), params.get(AnalyticsEvents.PARAM_REMINDER_SEQUENCE));
        assertEquals(Long.valueOf(1_793_203_200_000L), params.get(AnalyticsEvents.PARAM_REMINDER_SENT_AT_MS));
    }

    @Test
    public void ignoresSyntheticReminderWithoutDeliveryStrategy() {
        AlertItem upcoming = new AlertItem("rem_upcoming", "reminder", "Starts within an hour",
                "Math session", "2026-10-28T16:00:00.000Z", false, null,
                "activity-1", "group-1");

        assertNull(ReminderTelemetry.reminderSentParameters(upcoming));
    }
}
