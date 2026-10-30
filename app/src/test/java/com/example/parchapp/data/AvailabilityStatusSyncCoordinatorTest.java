package com.example.parchapp.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AvailabilityStatusSyncCoordinatorTest {
    @Test
    public void mapsUiStatusLabelsToBackendEnums() {
        assertEquals("active", AvailabilityStatusSyncCoordinator.backendStatus("Available now"));
        assertEquals("inClass", AvailabilityStatusSyncCoordinator.backendStatus("In Class"));
        assertEquals("focused", AvailabilityStatusSyncCoordinator.backendStatus("Deep Focus"));
        assertEquals("busy", AvailabilityStatusSyncCoordinator.backendStatus("Busy / Personal"));
        assertEquals("inTransit", AvailabilityStatusSyncCoordinator.backendStatus("Commute / In Transit"));
    }

    @Test
    public void expiryUsesUtcIso8601WithMilliseconds() {
        assertEquals("2026-10-30T12:34:56.789Z",
                AvailabilityStatusSyncCoordinator.isoUtc(1_793_363_696_789L));
    }

    @Test
    public void preservesSupportedStatusPrivacyScope() {
        assertEquals("private", FirebasePresenceRepository.presenceVisibility("private", "groups"));
        assertEquals("closeFriends", FirebasePresenceRepository.presenceVisibility("closeFriends", "groups"));
        assertEquals("groups", FirebasePresenceRepository.presenceVisibility("unsupported", "groups"));
    }
}
