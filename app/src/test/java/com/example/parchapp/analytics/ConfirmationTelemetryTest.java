package com.example.parchapp.analytics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.parchapp.domain.model.PlannedActivity;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class ConfirmationTelemetryTest {

    private static PlannedActivity plan(String id, String status) {
        return new PlannedActivity(id, "g", "Plan " + id, "study", "2026-10-28", "17:30", "", status, 0);
    }

    @Test
    public void onlyConfirmedPlansThatWereNotLoggedBefore() {
        List<PlannedActivity> plans = Arrays.asList(
                plan("a", PlannedActivity.STATUS_CONFIRMED),
                plan("b", PlannedActivity.STATUS_PROPOSED),
                plan("c", PlannedActivity.STATUS_CONFIRMED),
                plan("a", PlannedActivity.STATUS_CONFIRMED));

        List<PlannedActivity> fresh = ConfirmationTelemetry.newlyConfirmed(plans, new HashSet<>(Collections.singleton("c")));

        assertEquals(1, fresh.size());
        assertEquals("a", fresh.get(0).getId());
    }

    @Test
    public void plansWithoutIdAreSkipped() {
        assertTrue(ConfirmationTelemetry.newlyConfirmed(
                Collections.singletonList(plan(null, PlannedActivity.STATUS_CONFIRMED)), new HashSet<>()).isEmpty());
    }
}
