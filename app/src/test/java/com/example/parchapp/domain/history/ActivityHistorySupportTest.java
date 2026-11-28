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

    @Test
    public void searchFindsByTitleOrPlaceIgnoringCaseAndExtraSpaces() {
        PlannedActivity pool = activity("Pool", "2026-11-20T13:00:00Z",
                PlannedActivity.STATUS_PROPOSED, false, "la Vega", null, null);
        PlannedActivity study = activity("Study group", "2026-11-21T13:00:00Z",
                PlannedActivity.STATUS_CONFIRMED, false, "Library", null, null);
        List<PlannedActivity> all = Arrays.asList(pool, study);

        assertEquals(1, ActivityHistorySupport.filter(all, "  POOL ").size());
        assertEquals("Pool", ActivityHistorySupport.filter(all, "vega").get(0).getId());
        assertEquals("Study group", ActivityHistorySupport.filter(all, "library").get(0).getId());
    }

    @Test
    public void searchNeedsEveryWordAndKeepsEverythingWhenEmpty() {
        PlannedActivity pool = activity("Pool", "2026-11-20T13:00:00Z",
                PlannedActivity.STATUS_PROPOSED, false, "la Vega", null, null);
        PlannedActivity study = activity("Study group", "2026-11-21T13:00:00Z",
                PlannedActivity.STATUS_CONFIRMED, false, "Library", null, null);
        List<PlannedActivity> all = Arrays.asList(pool, study);

        assertEquals(1, ActivityHistorySupport.filter(all, "pool vega").size());
        assertTrue(ActivityHistorySupport.filter(all, "pool library").isEmpty());
        assertEquals(2, ActivityHistorySupport.filter(all, "").size());
        assertEquals(2, ActivityHistorySupport.filter(all, null).size());
        assertEquals(2, ActivityHistorySupport.filter(all, "   ").size());
    }

    @Test
    public void searchWithNoMatchGivesAnEmptyList() {
        PlannedActivity pool = activity("Pool", "2026-11-20T13:00:00Z",
                PlannedActivity.STATUS_PROPOSED, false, "", null, null);

        assertTrue(ActivityHistorySupport.filter(Arrays.asList(pool), "chess").isEmpty());
    }

    @Test
    public void offlineBannerShowsTheDateOfTheSavedCopyNeverNow() {
        long now = 2_000_000L;
        assertEquals(now, ActivityHistorySupport.shownSavedAt(false, 500L, now));
        assertEquals(500L, ActivityHistorySupport.shownSavedAt(true, 500L, now));
        // no saved copy: no date, the banner says "an earlier date"
        assertEquals(0L, ActivityHistorySupport.shownSavedAt(true, 0L, now));
        assertEquals(0L, ActivityHistorySupport.shownSavedAt(true, -1L, now));
    }

    @Test
    public void aDateThatEndsWithADotLosesItBecauseTheSentenceAddsOne() {
        assertEquals("27/11/2026 10:13 p.m", ActivityHistorySupport.withoutTrailingDot("27/11/2026 10:13 p.m."));
        assertEquals("27/11/2026 22:13", ActivityHistorySupport.withoutTrailingDot("27/11/2026 22:13"));
        assertEquals("", ActivityHistorySupport.withoutTrailingDot(null));
    }

    private static PlannedActivity activity(String id, String startsAt, String status,
                                            boolean cancelled, String location,
                                            Double latitude, Double longitude) {
        return new PlannedActivity(id, "group", id, "study", "", "", location, status,
                0L, 0, false, startsAt, "", "unknown", "", null,
                cancelled, 0, latitude, longitude);
    }
}
