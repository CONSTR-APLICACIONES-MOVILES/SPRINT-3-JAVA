package com.example.parchapp.domain;

import com.example.parchapp.domain.model.AndesEvent;

/* Caching for Andes Events, the rolling window of the Flutter app: from today's midnight in Bogota to the
 * midnight 15 days later. Only events that have not ended and overlap the window stay on the phone, so the copy
 * is small and always about the next two weeks. Events expire by time, not by use, so this is evicted by date
 * and not LRU. Bogota is UTC-5 all year (no daylight saving time). */
public final class AndesCacheWindow {
    public static final int DAYS = 15;
    private static final long HOUR = 60L * 60 * 1000;
    private static final long DAY = 24 * HOUR;
    private static final long BOGOTA_OFFSET = -5 * HOUR;

    private final long now;
    private final long start;
    private final long end;

    public AndesCacheWindow(long now) {
        this.now = now;
        long local = now + BOGOTA_OFFSET;
        this.start = Math.floorDiv(local, DAY) * DAY - BOGOTA_OFFSET;
        this.end = start + DAYS * DAY;
    }

    public boolean includes(AndesEvent event) {
        return event.getEnd() > now && event.getEnd() > start && event.getStart() < end;
    }

    public long getNow() {
        return now;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }
}
