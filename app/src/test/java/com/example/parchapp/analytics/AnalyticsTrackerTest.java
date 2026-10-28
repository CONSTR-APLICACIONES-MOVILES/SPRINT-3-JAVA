package com.example.parchapp.analytics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AnalyticsTrackerTest {

    @Test
    public void everyCallerSharesOneInstance() {
        assertSame(AnalyticsTracker.getInstance(), AnalyticsTracker.getInstance());
    }

    @Test
    public void userIdIsAnonymisedDeterministically() {
        String anonymous = AnalyticsTracker.anonymise("firebase-uid-123");

        assertEquals(anonymous, AnalyticsTracker.anonymise("firebase-uid-123"));
        assertNotEquals(anonymous, AnalyticsTracker.anonymise("firebase-uid-124"));
        assertEquals(32, anonymous.length());
        assertTrue(anonymous.matches("[0-9a-f]+"));
    }
}
