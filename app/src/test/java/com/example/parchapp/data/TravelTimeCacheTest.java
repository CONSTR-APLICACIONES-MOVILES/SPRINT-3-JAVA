package com.example.parchapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class TravelTimeCacheTest {

    @Test
    public void keepsMinutesPerActivityUntilTheyExpire() {
        long[] now = {1_000};
        TravelTimeCache cache = new TravelTimeCache(() -> now[0]);

        cache.put("study", 12);
        assertEquals(Integer.valueOf(12), cache.get("study"));
        assertNull(cache.get("soccer"));

        now[0] += TravelTimeCache.TTL_MS;
        assertNull(cache.get("study"));
    }

    @Test
    public void reusesOnlyFreshRoutesForTheSamePlaceAndNearbyOrigin() {
        TravelTimeCache cache = new TravelTimeCache(() -> 5_000L);
        cache.put("study", 12, 4.6026, -74.0660, "ml-building");

        assertEquals(Integer.valueOf(12), cache.getForDestination("study", "ml-building"));
        assertEquals(Integer.valueOf(12), cache.getNearOrigin(
                "study", 4.6030, -74.0662, "ml-building", 300));
        assertNull(cache.getForDestination("study", "different-building"));
        assertNull(cache.getNearOrigin("study", 4.6280, -74.0560, "ml-building", 300));
    }

    @Test
    public void evictsLeastRecentlyUsedEntriesWhenCapacityIsReached() {
        long[] now = {10_000L};
        TravelTimeCache cache = new TravelTimeCache(() -> now[0]);
        for (int i = 0; i < TravelTimeCache.MAX_ENTRIES; i++) {
            cache.put("activity-" + i, i + 1);
        }
        assertNotNull(cache.get("activity-0")); // promote it to most recently used
        cache.put("activity-new", 99);

        assertNotNull(cache.get("activity-0"));
        assertNull(cache.get("activity-1"));
        assertEquals(Integer.valueOf(99), cache.get("activity-new"));
    }

    @Test
    public void removesExpiredEntriesWhenAddingNewValues() {
        long[] now = {20_000L};
        TravelTimeCache cache = new TravelTimeCache(() -> now[0]);
        cache.put("old", 10);
        now[0] += TravelTimeCache.TTL_MS;
        cache.put("new", 11);

        assertNull(cache.get("old"));
        assertEquals(Integer.valueOf(11), cache.get("new"));
    }
}
