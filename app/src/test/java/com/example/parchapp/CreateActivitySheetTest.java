package com.example.parchapp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.parchapp.domain.model.ComparisonEvidence;

import org.junit.Test;

import java.util.Collections;

// bq9: which new activities count as planned after comparing availability
public class CreateActivitySheetTest {

    @Test
    public void compareAvailabilitySlotCountsAsComparing() {
        assertTrue(CreateActivitySheet.madeAfterComparing(0, null));
    }

    @Test
    public void scheduleComparisonWithFriendsCountsAsComparing() {
        assertTrue(CreateActivitySheet.madeAfterComparing(-1, "comparison-1"));
    }

    @Test
    public void planWithoutSlotOrComparisonDoesNotCount() {
        assertFalse(CreateActivitySheet.madeAfterComparing(-1, null));
        assertFalse(CreateActivitySheet.madeAfterComparing(-1, ""));
    }

// bq9: a Schedule comparison goes with its friends and day
    @Test
    public void scheduleComparisonSendsEvidence() {
        ComparisonEvidence evidence = CreateActivitySheet.evidenceFor(-1, "c1",
                Collections.singletonList("alex"), "2026-10-30", "me");
        assertTrue(evidence.isComparison());
        assertTrue(evidence.appliesTo("2026-10-30"));
    }

// bq9: a plan made without comparing is measured as direct
    @Test
    public void planWithoutComparingIsDirect() {
        assertFalse(CreateActivitySheet.evidenceFor(-1, null, null, null, "me").isComparison());
    }

// bq9: a Compare Availability slot has no backend comparison, so it stays unknown instead of direct
    @Test
    public void compareAvailabilitySlotIsUnknown() {
        assertNull(CreateActivitySheet.evidenceFor(0, null, null, null, "me"));
        assertNull(CreateActivitySheet.evidenceFor(-1, "c1", null, "2026-10-30", "me"));
    }
}
