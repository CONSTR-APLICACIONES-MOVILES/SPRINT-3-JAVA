package com.example.parchapp.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AlertItemTest {

    @Test
    public void classifiesActivityInvitationsForFiltersAndResponses() {
        AlertItem alert = new AlertItem("activity-1", "invitation", "Study session", "Tomorrow",
                "2026-10-28T12:00:00.000Z", false, null, "activity-1", "group-1");

        assertEquals("invitations", alert.getCategoryKey());
        assertEquals("high", alert.getPriorityKey());
        assertTrue(alert.isActivityInvitation());
    }

    @Test
    public void classifiesGroupInvitationsAndScheduleEvents() {
        AlertItem groupInvite = new AlertItem("grp_1", "groupInvite", "Group invite", "Join",
                "2026-10-28T12:00:00.000Z", false, null, "", "group-1");
        AlertItem reminder = new AlertItem("rem_1", "reminder", "Upcoming activity", "Today",
                "2026-10-28T12:00:00.000Z", false, null, "activity-1", "group-1");
        AlertItem timeChange = new AlertItem("evt_1", "timeChanged", "Activity time changed", "Study",
                "2026-10-28T12:00:00.000Z", false, null, "activity-1", "group-1");

        assertEquals("groups", groupInvite.getCategoryKey());
        assertEquals("medium", groupInvite.getPriorityKey());
        assertTrue(groupInvite.isGroupInvitation());
        assertEquals("schedule", reminder.getCategoryKey());
        assertEquals("high", reminder.getPriorityKey());
        assertEquals("schedule", timeChange.getCategoryKey());
    }
}
