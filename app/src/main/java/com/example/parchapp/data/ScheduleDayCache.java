package com.example.parchapp.data;

import androidx.annotation.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/* Caching (issue #46): the Schedule days the backend already answered, kept in memory for a few minutes.
 * Going from Monday to Friday and back to Monday does not call getScheduleDay again, it answers at once.
 * LRU: a LinkedHashMap in access order keeps the most recently used day last; when there are more than
 * MAX_ENTRIES the least recently used one is removed, so memory stays bounded. Each entry expires after TTL_MS,
 * because a friend can change their calendar; after that the network is asked again. */
final class ScheduleDayCache<V> {
    static final int MAX_ENTRIES = 14;          // two weeks of days, what the week selector can show
    static final long TTL_MS = 5 * 60 * 1000L;  // busy blocks change rarely within a few minutes

    interface Clock {
        long now();
    }

    private static final class Entry<V> {
        final V value;
        final long savedAt;

        Entry(V value, long savedAt) {
            this.value = value;
            this.savedAt = savedAt;
        }
    }

    private final LinkedHashMap<String, Entry<V>> entries = new LinkedHashMap<>(16, .75f, true);
    private final Clock clock;

    ScheduleDayCache(Clock clock) {
        this.clock = clock;
    }

    // synchronized: the answers arrive on Firebase threads while the screen asks from the main thread
    @Nullable
    synchronized V get(String key) {
        Entry<V> entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (clock.now() - entry.savedAt > TTL_MS) {
            entries.remove(key);
            return null;
        }
        return entry.value;
    }

    synchronized void put(String key, V value) {
        entries.put(key, new Entry<>(value, clock.now()));
        Iterator<Map.Entry<String, Entry<V>>> oldest = entries.entrySet().iterator();
        while (entries.size() > MAX_ENTRIES && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    /* after importing my calendar my busy blocks changed, so nothing saved before is right anymore */
    synchronized void clear() {
        entries.clear();
    }

    synchronized int size() {
        return entries.size();
    }
}
