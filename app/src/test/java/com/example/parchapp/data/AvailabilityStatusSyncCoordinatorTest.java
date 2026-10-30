package com.example.parchapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.parchapp.domain.AvailabilityStatusExpiry;

import org.junit.Test;

public class AvailabilityStatusSyncCoordinatorTest {

    @Test
    public void mapsUiStatusesToBackendEnum() {
        assertEquals("active", AvailabilityStatusSyncCoordinator.backendStatus("Available now"));
        assertEquals("inClass", AvailabilityStatusSyncCoordinator.backendStatus("In Class"));
        assertEquals("focused", AvailabilityStatusSyncCoordinator.backendStatus("Deep Focus"));
        assertEquals("busy", AvailabilityStatusSyncCoordinator.backendStatus("Busy / Personal"));
        assertEquals("inTransit", AvailabilityStatusSyncCoordinator.backendStatus("Commute / In Transit"));
    }

    @Test
    public void formatsExpiryAsUtcIso8601WithMilliseconds() {
        assertEquals("2026-10-30T12:34:56.789Z",
                AvailabilityStatusSyncCoordinator.isoUtc(1_793_363_696_789L));
    }

    @Test
    public void presenceUpdatePreservesOnlySupportedExistingPrivacyScopes() {
        assertEquals("private", FirebasePresenceRepository.presenceVisibility("private", "groups"));
        assertEquals("closeFriends", FirebasePresenceRepository.presenceVisibility("closeFriends", "groups"));
        assertEquals("groups", FirebasePresenceRepository.presenceVisibility("unknown", "groups"));
    }

    @Test
    public void statusExpiryChoicesStayWithinTheBackendTwentyFourHourLimit() {
        long now = 1_793_363_696_789L;
        assertEquals(now + 60 * 60 * 1000L, AvailabilityStatusExpiry.expiryFor("For 1 hour", now));
        assertEquals(now + AvailabilityStatusExpiry.MAX_DURATION_MS,
                AvailabilityStatusExpiry.expiryFor("Until tomorrow", now));
        assertTrue(AvailabilityStatusExpiry.isValid(
                AvailabilityStatusExpiry.expiryFor("Until 6:00 PM", now), now));
        assertTrue(AvailabilityStatusExpiry.isValid(now + AvailabilityStatusExpiry.MAX_DURATION_MS, now));
        assertFalse(AvailabilityStatusExpiry.isValid(now + AvailabilityStatusExpiry.MAX_DURATION_MS + 1, now));
        assertFalse(AvailabilityStatusExpiry.isValid(now, now));
    }
}
