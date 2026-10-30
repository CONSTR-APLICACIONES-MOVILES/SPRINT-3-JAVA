package com.example.parchapp.data;

import androidx.annotation.Nullable;

import com.example.parchapp.domain.PlanContext;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/* Short-lived, bounded LRU estimates. A route is reusable only for the same destination and nearby origin. */
class TravelTimeCache {
    static final long TTL_MS = 15 * 60 * 1000L;
    static final int MAX_ENTRIES = 100;

    private static final class Entry {
        final int minutes;
        final long savedAt;
        final Double originLatitude;
        final Double originLongitude;
        final String destinationId;

        Entry(int minutes, long savedAt, Double originLatitude, Double originLongitude, String destinationId) {
            this.minutes = minutes;
            this.savedAt = savedAt;
            this.originLatitude = originLatitude;
            this.originLongitude = originLongitude;
            this.destinationId = destinationId;
        }
    }

    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(16, .75f, true);
    private final Clock clock;

    interface Clock {
        long now();
    }

    TravelTimeCache() {
        this(android.os.SystemClock::elapsedRealtime);
    }

    TravelTimeCache(Clock clock) {
        this.clock = clock;
    }

    @Nullable
    synchronized Integer get(String activityId) {
        Entry entry = validEntry(activityId);
        return entry == null ? null : entry.minutes;
    }

    @Nullable
    synchronized Integer getForDestination(String activityId, String destinationId) {
        Entry entry = validEntry(activityId);
        return entry == null || destinationId == null || !destinationId.equals(entry.destinationId)
                ? null : entry.minutes;
    }

    @Nullable
    synchronized Integer getNearOrigin(String activityId, double latitude, double longitude,
                                       String destinationId, double radiusMeters) {
        Entry entry = validEntry(activityId);
        if (entry == null || entry.originLatitude == null || entry.originLongitude == null
                || destinationId == null || !destinationId.equals(entry.destinationId)) return null;
        return PlanContext.distanceMeters(entry.originLatitude, entry.originLongitude,
                latitude, longitude) <= radiusMeters ? entry.minutes : null;
    }

    synchronized void put(String activityId, int minutes) {
        put(activityId, minutes, null, null, null);
    }

    synchronized void put(String activityId, int minutes, double latitude, double longitude,
                          String destinationId) {
        put(activityId, minutes, Double.valueOf(latitude), Double.valueOf(longitude), destinationId);
    }

    private void put(String activityId, int minutes, Double latitude, Double longitude,
                     String destinationId) {
        if (activityId == null || activityId.trim().isEmpty() || minutes <= 0) return;
        pruneExpired();
        entries.put(activityId, new Entry(minutes, clock.now(), latitude, longitude, destinationId));
        while (entries.size() > MAX_ENTRIES) {
            Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private Entry validEntry(String activityId) {
        if (activityId == null) return null;
        Entry entry = entries.get(activityId);
        if (entry == null) return null;
        if (clock.now() - entry.savedAt >= TTL_MS) {
            entries.remove(activityId);
            return null;
        }
        return entry;
    }

    private void pruneExpired() {
        long now = clock.now();
        Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().savedAt >= TTL_MS) iterator.remove();
        }
    }
}
