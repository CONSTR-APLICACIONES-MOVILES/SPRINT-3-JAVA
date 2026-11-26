package com.example.parchapp.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ActivityParticipantsTest {
    @Test
    public void parsesMembersAndPermissionFlags() {
        Map<String, Object> organizer = new HashMap<>();
        organizer.put("id", "u1");
        organizer.put("name", "Alex");
        organizer.put("organizer", true);
        organizer.put("invited", true);
        organizer.put("response", "accepted");

        Map<String, Object> pending = new HashMap<>();
        pending.put("id", "u2");
        pending.put("name", "Mateo");
        pending.put("organizer", false);
        pending.put("invited", true);
        pending.put("response", null);

        Map<String, Object> data = new HashMap<>();
        data.put("activityId", "a1");
        data.put("groupId", "g1");
        data.put("version", "v1");
        data.put("canManage", true);
        data.put("canJoin", false);
        data.put("joined", true);
        data.put("canInviteFriends", true);
        data.put("cancelled", false);
        data.put("members", Arrays.asList(organizer, pending));

        ActivityParticipants participants = ActivityParticipants.from(data);

        assertEquals("a1", participants.getActivityId());
        assertEquals("g1", participants.getGroupId());
        assertEquals("v1", participants.getVersion());
        assertTrue(participants.canManage());
        assertFalse(participants.canJoin());
        assertTrue(participants.isJoined());
        assertTrue(participants.canInviteFriends());
        assertFalse(participants.isCancelled());
        assertEquals(2, participants.getMembers().size());
        assertTrue(participants.getMembers().get(0).isOrganizer());
        assertTrue(participants.getMembers().get(1).isInvited());
        assertNull(participants.getMembers().get(1).getResponse());
        assertTrue(participants.containsMember("u2"));
    }

    @Test
    public void showsFriendlyResponseLabels() {
        assertEquals("Accepted", new ActivityParticipant("u", "A", false, true, "accepted").getResponseLabel());
        assertEquals("Maybe", new ActivityParticipant("u", "A", false, true, "maybe").getResponseLabel());
        assertEquals("Declined", new ActivityParticipant("u", "A", false, true, "declined").getResponseLabel());
        assertEquals("Awaiting response",
                new ActivityParticipant("u", "A", false, true, null).getResponseLabel());
        assertEquals("future-state",
                new ActivityParticipant("u", "A", false, true, "future-state").getResponseLabel());
    }
}
