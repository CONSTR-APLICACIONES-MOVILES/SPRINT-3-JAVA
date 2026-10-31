package com.example.parchapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

// eventual connectivity: honest messages when an action needs the server
public class OfflineGuardTest {

    @Test
    public void messageSaysWhatNeedsTheNetwork() {
        assertEquals("You're offline. Connect to the internet to create the activity.",
                OfflineGuard.offlineMessage("create the activity"));
    }

    @Test
    public void dataErrorsAreNotTreatedAsOffline() {
        // Firebase exceptions can not be built on the JVM, so here we check that other errors keep their own message
        assertFalse(OfflineGuard.isNetworkError(new IllegalStateException("bad data")));
        assertFalse(OfflineGuard.isNetworkError(new RuntimeException(new IllegalArgumentException("wrapped"))));
        assertFalse(OfflineGuard.isNetworkError(null));
    }
}
