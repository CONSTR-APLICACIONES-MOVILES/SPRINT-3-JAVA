package com.example.parchapp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
}
