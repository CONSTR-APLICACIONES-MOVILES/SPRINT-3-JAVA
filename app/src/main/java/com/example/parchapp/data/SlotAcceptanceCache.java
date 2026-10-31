package com.example.parchapp.data;

import com.example.parchapp.domain.model.SlotAcceptanceInsight;

import java.util.function.LongSupplier;

// strategy 9, caching: the bq3 result is kept in memory for a few minutes
// the backend computes bq3 only once a day, so asking firestore every time compare availability opens is not needed
// it is only one value, so it is a value and the time it arrived, not an lru with many entries
final class SlotAcceptanceCache {
// ten minutes, short enough to see a new result the same day and long enough to skip the repeated reads
    static final long TTL_MS = 10 * 60 * 1000L;

    private final LongSupplier clock;
    private SlotAcceptanceInsight value;
    private long savedAt;

    SlotAcceptanceCache(LongSupplier clock) {
        this.clock = clock;
    }

// the saved result while it is fresh, null when there is nothing or it expired
    synchronized SlotAcceptanceInsight get() {
        if (value == null || clock.getAsLong() - savedAt >= TTL_MS) {
            return null;
        }
        return value;
    }

    synchronized void put(SlotAcceptanceInsight insight) {
        value = insight;
        savedAt = clock.getAsLong();
    }
}
