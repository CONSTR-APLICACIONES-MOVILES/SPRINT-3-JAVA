package com.example.parchapp.domain.history;

import com.example.parchapp.domain.model.PlannedActivity;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ActivityHistorySupportTest {
    @Test
    public void pastActivitiesExcludeFutureAndCancelledAndSortByStartTime() {
        long now = 1795780800000L;
        PlannedActivity older = activity("older", "2026-11-20T13:00:00Z",
                PlannedActivity.STATUS_CONFIRMED, false, "Library", null, null);
        PlannedActivity newer = activity("newer", "2026-11-21T13:00:00Z",
                PlannedActivity.STATUS_PROPOSED, false, "Library", null, null);
        PlannedActivity future = activity("future", "2026-12-01T13:00:00Z",
                PlannedActivity.STATUS_PROPOSED, false, "Library", null, null);
        PlannedActivity cancelled = activity("cancelled", "2026-11-22T13:00:00Z",
                PlannedActivity.STATUS_CONFIRMED, true, "Library", null, null);

        List<PlannedActivity> result = ActivityHistorySupport.pastActivities(
                Arrays.asList(older, future, cancelled, newer), now);

        assertEquals(2, result.size());
        assertEquals("newer", result.get(0).getId());
        assertEquals("older", result.get(1).getId());
        assertFalse(result.contains(cancelled));
        assertFalse(result.contains(future));
    }

    @Test
    public void summaryCountsConfirmedMonthAndMostFrequentPlace() {
        long now = 1795780800000L;
        List<PlannedActivity> past = Arrays.asList(
                activity("one", "2026-11-20T13:00:00Z",
                        PlannedActivity.STATUS_CONFIRMED, false, "Library", null, null),
                activity("two", "2026-11-21T13:00:00Z",
                        PlannedActivity.STATUS_CONFIRMED, false, "Library", null, null),
                activity("three", "2026-10-19T13:00:00Z",
                        PlannedActivity.STATUS_PROPOSED, false, "Park", null, null));

        ActivityHistorySupport.Summary summary = ActivityHistorySupport.summarize(past, now);

        assertEquals(3, summary.total);
        assertEquals(2, summary.confirmed);
        assertEquals(2, summary.thisMonth);
        assertEquals("Library", summary.favoritePlace);
        assertFalse(summary.favoriteTime.isEmpty());
    }

    @Test
    public void mapTileUrlRequiresValidCoordinates() {
        PlannedActivity withLocation = activity("mapped", "2026-11-20T13:00:00Z",
                PlannedActivity.STATUS_PROPOSED, false, "Campus", 4.65, -74.05);
        PlannedActivity withoutLocation = activity("plain", "2026-11-20T13:00:00Z",
                PlannedActivity.STATUS_PROPOSED, false, "Campus", null, null);
        PlannedActivity invalidLocation = activity("invalid", "2026-11-20T13:00:00Z",
                PlannedActivity.STATUS_PROPOSED, false, "Campus", 91.0, -74.05);

        assertTrue(ActivityHistorySupport.mapTileUrl(withLocation)
                .startsWith("https://tile.openstreetmap.org/16/"));
        assertNull(ActivityHistorySupport.mapTileUrl(withoutLocation));
        assertNull(ActivityHistorySupport.mapTileUrl(invalidLocation));
    }

    private static PlannedActivity activity(String id, String startsAt, String status,
                                            boolean cancelled, String location,
                                            Double latitude, Double longitude) {
        return new PlannedActivity(id, "group", id, "study", "", "", location, status,
                0L, 0, false, startsAt, "", "unknown", "", null,
                cancelled, 0, latitude, longitude);
    }
}
