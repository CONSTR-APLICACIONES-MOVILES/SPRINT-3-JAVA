package com.example.parchapp.data;

import static org.junit.Assert.assertEquals;

import com.example.parchapp.domain.model.ResponseTimeEstimate;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class FirestoreResponseTimeInsightsRepositoryTest {
    @Test
    public void parsesValidBq5RowsAndIgnoresIncompleteRows() {
        Map<String, Object> complete = new HashMap<>();
        complete.put("group_size", 5L);
        complete.put("estimate_minutes", 95.5);
        complete.put("estimate_kind", "all_responded");
        Map<String, Object> incomplete = new HashMap<>();
        incomplete.put("group_size", 8L);
        incomplete.put("estimate_minutes", null);
        incomplete.put("estimate_kind", "all_responded");
        Map<String, Object> unknownKind = new HashMap<>();
        unknownKind.put("group_size", 4L);
        unknownKind.put("estimate_minutes", 30);
        unknownKind.put("estimate_kind", "unknown");

        java.util.List<ResponseTimeEstimate> estimates =
                FirestoreResponseTimeInsightsRepository.parseEstimates(
                        Arrays.asList(complete, incomplete, unknownKind));

        assertEquals(1, estimates.size());
        assertEquals(5, estimates.get(0).getGroupSize());
        assertEquals(95.5, estimates.get(0).getMinutes(), 0.01);
        assertEquals(ResponseTimeEstimate.Kind.ALL_RESPONDED, estimates.get(0).getKind());
        assertEquals("1.5 h", estimates.get(0).getDurationLabel());
    }
}
