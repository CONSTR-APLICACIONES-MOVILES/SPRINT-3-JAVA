package com.example.parchapp.analytics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.parchapp.domain.model.AlertItem;

import org.junit.Test;

import java.util.Map;

public class RsvpTelemetryTest {
    @Test
    public void recordsBq5ResponseTimingForPendingActivityInvitation() {
        AlertItem invitation = new AlertItem("activity-1", "invitation", "Study group",
                "Today · Library", "2026-10-29T14:00:00.000Z", false, null,
                "activity-1", "group-1", 5);

        Map<String, Object> params = RsvpTelemetry.parameters(invitation, "accepted",
                1_793_283_000_000L);

        assertEquals("activity-1", params.get(AnalyticsEvents.PARAM_ACTIVITY_ID));
        assertEquals(5, params.get(AnalyticsEvents.PARAM_GROUP_SIZE));
        assertEquals(600_000L, params.get(AnalyticsEvents.PARAM_RESPONSE_MS));
        assertEquals("going", params.get(AnalyticsEvents.PARAM_RESPONSE));
    }

    @Test
    public void skipsInvitationsWithoutGroupSizeOrAlreadyRecordedResponses() {
        AlertItem unknownSize = new AlertItem("activity-1", "invitation", "Study group",
                "Today", "2026-10-29T14:00:00.000Z", false, null,
                "activity-1", "group-1");
        AlertItem alreadyAnswered = new AlertItem("activity-2", "invitation", "Study group",
                "Today", "2026-10-29T14:00:00.000Z", false, "accepted",
                "activity-2", "group-1", 5);

        assertNull(RsvpTelemetry.parameters(unknownSize, "maybe", 1_793_283_000_000L));
        assertNull(RsvpTelemetry.parameters(alreadyAnswered, "maybe", 1_793_283_000_000L));
    }

    @Test
    public void pairsOnlyAResponseAfterTheMatchingRsvpReminder() {
        AlertItem invitation = new AlertItem("activity-1", "invitation", "Study group",
                "Today", "2026-10-29T14:00:00.000Z", false, null,
                "activity-1", "group-1", 5);
        AlertItem reminder = new AlertItem("evt_reminder-activity-1-1", "reminder", "Pending response",
                "Today", "2026-10-29T14:05:00.000Z", false, null,
                "activity-1", "group-1", 0, "reminder-activity-1-1", "rsvp",
                "rsvp_after_30_min", 1, 30, "2026-10-29T14:05:00.000Z");

        Map<String, Object> params = RsvpTelemetry.responseAfterReminderParameters(
                invitation, "accepted", 1_793_283_000_000L, reminder);

        assertEquals("activity-1", params.get(AnalyticsEvents.PARAM_ACTIVITY_ID));
        assertEquals("reminder-activity-1-1", params.get(AnalyticsEvents.PARAM_REMINDER_ID));
        assertEquals("rsvp_after_30_min", params.get(AnalyticsEvents.PARAM_REMINDER_STRATEGY));
        assertEquals(300_000L, params.get(AnalyticsEvents.PARAM_RESPONSE_DELAY_MS));
        assertNull(RsvpTelemetry.responseAfterReminderParameters(
                invitation, "accepted", 1_793_282_000_000L, reminder));
    }
}
