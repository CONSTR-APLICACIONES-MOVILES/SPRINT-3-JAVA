package com.example.parchapp.data;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/* Short-lived travel estimates per activity; entries expire because travel time changes as the user moves. */
class TravelTimeCache {
    static final long TTL_MS = 15 * 60 * 1000L;

    private final Map<String, long[]> entries = new HashMap<>();
    private final Clock clock;

    interface Clock {
        long now();
    }

    TravelTimeCache() {
        this(System::currentTimeMillis);
    }

    TravelTimeCache(Clock clock) {
        this.clock = clock;
    }

    @Nullable
    synchronized Integer get(String activityId) {
        long[] entry = entries.get(activityId);
        if (entry == null) {
            return null;
        }
        if (clock.now() - entry[1] > TTL_MS) {
            entries.remove(activityId);
            return null;
        }
        return (int) entry[0];
    }

    synchronized void put(String activityId, int minutes) {
        entries.put(activityId, new long[]{minutes, clock.now()});
    }
}
