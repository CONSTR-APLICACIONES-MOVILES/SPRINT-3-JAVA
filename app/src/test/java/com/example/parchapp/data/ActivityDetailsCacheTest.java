package com.example.parchapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import com.example.parchapp.domain.model.ActivityDetails;

import org.junit.Test;

public class ActivityDetailsCacheTest {
    @Test
    public void cachedSummarySurvivesRecreatingTheCacheAndIsScopedToTheUser() {
        MemoryStore store = new MemoryStore();
        ActivityDetailsCache writer = new ActivityDetailsCache(store, () -> 10_000L);
        writer.put("user-a", details("activity-1", "Study group"));

        ActivityDetailsCache reader = new ActivityDetailsCache(store, () -> 12_000L);
        ActivityDetailsCache.Snapshot snapshot = reader.get("user-a", "activity-1");

        assertNotNull(snapshot);
        assertEquals("Study group", snapshot.getDetails().getTitle());
        assertEquals(10_000L, snapshot.getSavedAtMillis());
        assertNull(reader.get("user-b", "activity-1"));
    }

    @Test
    public void expiresSnapshotsAndEvictsLeastRecentlyUsedEntries() {
        MemoryStore store = new MemoryStore();
        long[] now = {1_000L};
        ActivityDetailsCache cache = new ActivityDetailsCache(store, () -> now[0]);
        cache.put("user", details("activity-0", "Plan 0"));
        for (int i = 1; i < ActivityDetailsCache.MAX_ENTRIES; i++) {
            cache.put("user", details("activity-" + i, "Plan " + i));
        }
        assertNotNull(cache.get("user", "activity-0"));
        cache.put("user", details("activity-new", "New plan"));
        assertNotNull(cache.get("user", "activity-0"));
        assertNull(cache.get("user", "activity-1"));

        now[0] += ActivityDetailsCache.TTL_MS;
        assertNull(cache.get("user", "activity-new"));
    }

    private static ActivityDetails details(String id, String title) {
        return new ActivityDetails(id, "group-1", title, "study", "2026-10-29", "10:00",
                "Library", "PROPOSED", "2026-10-29T15:00:00.000Z",
                "2026-10-29T16:00:00.000Z", null, "organizer", 60, false, 4, 1);
    }

    private static final class MemoryStore implements ActivityDetailsCache.Store {
        private String value = "[]";
        @Override public String read() { return value; }
        @Override public void write(String serialized) { value = serialized; }
    }
}
