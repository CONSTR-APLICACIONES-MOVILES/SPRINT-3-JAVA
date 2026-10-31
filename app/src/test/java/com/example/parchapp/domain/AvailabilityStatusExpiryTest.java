package com.example.parchapp.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AvailabilityStatusExpiryTest {
    @Test
    public void durationChoicesStayWithinTheBackendTwentyFourHourLimit() {
        long now = 1_793_363_696_789L;
        assertEquals(now + 60 * 60 * 1000L,
                AvailabilityStatusExpiry.expiryFor("For 1 hour", now));
        assertEquals(now + AvailabilityStatusExpiry.MAX_DURATION_MS,
                AvailabilityStatusExpiry.expiryFor("Until tomorrow", now));
        assertTrue(AvailabilityStatusExpiry.isValid(
                AvailabilityStatusExpiry.expiryFor("Until 6:00 PM", now), now));
        assertTrue(AvailabilityStatusExpiry.isValid(now + AvailabilityStatusExpiry.MAX_DURATION_MS, now));
        assertFalse(AvailabilityStatusExpiry.isValid(now + AvailabilityStatusExpiry.MAX_DURATION_MS + 1, now));
        assertFalse(AvailabilityStatusExpiry.isValid(now, now));
    }

    @Test
    public void manualExpirySelectsNextBogotaOccurrenceAndIsBounded() {
        long now = 1_793_363_696_789L;
        long expiry = AvailabilityStatusExpiry.manualTime(8, 30, now);
        assertTrue(expiry > now);
        assertTrue(AvailabilityStatusExpiry.isValid(expiry, now));
    }
}
