package com.example.parchapp.data;

import static org.junit.Assert.assertEquals;

import com.example.parchapp.domain.model.SlotRecommendationBatch;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class FirebaseRecommendationDecisionRepositoryTest {
    @Test
    public void parsesServerRankedSlotsInOrderWithAvailabilityAndReasons() {
        Map<String, Object> slot = new HashMap<>();
        slot.put("id", "slot-1");
        slot.put("startTime", "2026-10-29T15:00:00.000Z");
        slot.put("endTime", "2026-10-29T16:00:00.000Z");
        slot.put("availableParticipants", 4);
        slot.put("totalParticipants", 5);
        slot.put("reasons", Arrays.asList("Preferred hour", "Everyone is free"));

        Map<String, Object> response = new HashMap<>();
        response.put("batchId", "batch-1");
        response.put("activityId", "activity-1");
        response.put("date", "2026-10-29");
        response.put("timeZone", "America/Bogota");
        response.put("recommendations", Arrays.asList(slot));

        SlotRecommendationBatch batch = FirebaseRecommendationDecisionRepository.parseBatch(response);

        assertEquals("batch-1", batch.getId());
        assertEquals("activity-1", batch.getActivityId());
        assertEquals(1, batch.getSlots().size());
        assertEquals("slot-1", batch.getSlots().get(0).getId());
        assertEquals(4, batch.getSlots().get(0).getAvailableParticipants());
        assertEquals(5, batch.getSlots().get(0).getTotalParticipants());
        assertEquals("Preferred hour", batch.getSlots().get(0).getReasons().get(0));
    }
}
