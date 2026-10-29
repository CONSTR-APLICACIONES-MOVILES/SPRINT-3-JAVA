package com.example.parchapp.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// f7: the saved travel time is only reused near the place where it was measured
public class TravelTimeServiceTest {

    private static final double[] ML_BUILDING = {4.6026, -74.0660};

// f7: a few meters away the saved time is still right
    @Test
    public void reusesTheSavedTimeNearby() {
        assertTrue(TravelTimeService.isNearOrigin(ML_BUILDING, 4.6030, -74.0662));
    }

// f7: after moving across the city we measure again
    @Test
    public void measuresAgainAfterMoving() {
        assertFalse(TravelTimeService.isNearOrigin(ML_BUILDING, 4.6280, -74.0560));
    }

// f7: a time saved without its origin is not trusted
    @Test
    public void unknownOriginIsNotReused() {
        assertFalse(TravelTimeService.isNearOrigin(null, 4.6026, -74.0660));
    }
}
