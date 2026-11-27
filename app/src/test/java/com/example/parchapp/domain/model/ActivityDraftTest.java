package com.example.parchapp.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

public class ActivityDraftTest {
    private ActivityDraft draft() {
        return new ActivityDraft("g1", "Study", "notes", "study", "Library",
                PlannedActivity.STATUS_PROPOSED, "2026-11-27", "14:00",
                "2026-11-27T19:00:00.000Z", "2026-11-27T20:00:00.000Z",
                "request-1", "comparison-1", Arrays.asList("u1", "u2"),
                "2026-11-27", 2);
    }

    @Test
    public void roundTripsLocalDraftJson() throws Exception {
        ActivityDraft copy = ActivityDraft.fromJson(draft().toJson());
        assertNotNull(copy);
        assertEquals("g1", copy.getGroupId());
        assertEquals("Study", copy.getTitle());
        assertEquals("study", copy.getCategory());
        assertEquals("comparison-1", copy.getComparisonId());
        assertEquals(2, copy.getSlotPosition());
        assertEquals(2, copy.getComparedIds().size());
    }

    @Test
    public void remotePayloadKeepsOnlyBackendFields() {
        Map<String, Object> payload = draft().toRemotePayload();
        assertEquals("g1", payload.get("groupId"));
        assertEquals("study", payload.get("category"));
        assertTrue(payload.containsKey("startsAt"));
        assertTrue(payload.containsKey("comparison"));
        assertTrue(!payload.containsKey("slotPosition"));
        assertTrue(!payload.containsKey("comparedIds"));
    }
}
