package com.example.parchapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.parchapp.domain.model.ComparisonEvidence;
import com.example.parchapp.domain.model.PlannedActivity;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// bq9: the publishActivity fields that tell the backend about the comparison, like the Flutter app
public class FirebaseActivityAttributionTest {

    private static PlannedActivity plan(String date) {
        return new PlannedActivity("a1", "g", "Study", "study", date, "17:30", "", "PROPOSED", 0);
    }

    @Test
    public void comparisonSendsParticipantsAndEvidence() {
        Map<String, Object> input = new HashMap<>();
        ComparisonEvidence evidence = ComparisonEvidence.of("c1", "me", Collections.singletonList("alex"), "2026-10-30");
        assertTrue(FirebaseActivityRepository.putAttribution(input, plan("2026-10-30"), evidence));
        assertEquals(1, input.get("measurementVersion"));
        assertEquals("c1", input.get("comparisonEvidenceId"));
        assertEquals(Arrays.asList("alex", "me"), input.get("participantIds"));
    }

    @Test
    public void planMovedToAnotherDayGoesWithoutEvidence() {
        Map<String, Object> input = new HashMap<>();
        ComparisonEvidence evidence = ComparisonEvidence.of("c1", "me", Collections.singletonList("alex"), "2026-10-30");
        assertFalse(FirebaseActivityRepository.putAttribution(input, plan("2026-10-31"), evidence));
        assertFalse(input.containsKey("comparisonEvidenceId"));
        assertFalse(input.containsKey("participantIds"));
    }

    @Test
    public void directPlanIsMeasuredAndUnknownIsNot() {
        Map<String, Object> direct = new HashMap<>();
        FirebaseActivityRepository.putAttribution(direct, plan("2026-10-30"), ComparisonEvidence.direct());
        assertEquals(1, direct.get("measurementVersion"));
        Map<String, Object> unknown = new HashMap<>();
        FirebaseActivityRepository.putAttribution(unknown, plan("2026-10-30"), null);
        assertTrue(unknown.isEmpty());
    }
}
