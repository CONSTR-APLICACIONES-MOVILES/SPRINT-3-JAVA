package com.example.parchapp.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

// bq9: the comparison evidence sent with a plan, the same rules the backend checks
public class ComparisonEvidenceTest {

    @Test
    public void participantsAreMeAndMyFriendsSorted() {
        ComparisonEvidence evidence = ComparisonEvidence.of("c1", "me", Arrays.asList("zoe", "alex", "me"), "2026-10-30");
        assertEquals(Arrays.asList("alex", "me", "zoe"), evidence.getParticipantIds());
        assertTrue(evidence.isComparison());
    }

    @Test
    public void onlyAppliesToTheComparedDay() {
        ComparisonEvidence evidence = ComparisonEvidence.of("c1", "me", Collections.singletonList("alex"), "2026-10-30");
        assertTrue(evidence.appliesTo("2026-10-30"));
        assertFalse(evidence.appliesTo("2026-10-31"));
    }

    @Test
    public void fitsOnlyGroupsWithEveryComparedFriend() {
        ComparisonEvidence evidence = ComparisonEvidence.of("c1", "me", Collections.singletonList("alex"), null);
        assertTrue(evidence.fitsGroup(Arrays.asList("me", "alex", "sam")));
        assertFalse(evidence.fitsGroup(Arrays.asList("me", "sam")));
    }

    @Test
    public void directPlanIsNotAComparison() {
        assertFalse(ComparisonEvidence.direct().isComparison());
        assertFalse(ComparisonEvidence.direct().appliesTo("2026-10-30"));
    }
}
