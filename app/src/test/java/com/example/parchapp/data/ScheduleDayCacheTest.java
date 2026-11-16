package com.example.parchapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

// caching #46: the Schedule days kept in memory (the limit of days saved on the phone is in ScheduleDayDaoTest)
public class ScheduleDayCacheTest {

    private final long[] now = {0};
    private final ScheduleDayCache<String> cache = new ScheduleDayCache<>(() -> now[0]);

// a day asked again within five minutes comes from memory
    @Test
    public void answersFromMemoryUntilItExpires() {
        cache.put("mon", "busy blocks");
        now[0] += ScheduleDayCache.TTL_MS;
        assertEquals("busy blocks", cache.get("mon"));
        now[0] += 1;
        assertNull(cache.get("mon"));
    }

// with more days than fit, the least recently used one goes, not the oldest saved
    @Test
    public void removesTheLeastRecentlyUsedDay() {
        for (int day = 0; day < ScheduleDayCache.MAX_ENTRIES; day++) {
            cache.put("day" + day, "value" + day);
        }
        cache.get("day0");
        cache.put("new", "value");
        assertEquals("value0", cache.get("day0"));
        assertNull(cache.get("day1"));
        assertEquals(ScheduleDayCache.MAX_ENTRIES, cache.size());
    }

// importing my calendar empties the memory
    @Test
    public void clearForgetsEverything() {
        cache.put("mon", "busy");
        cache.clear();
        assertNull(cache.get("mon"));
    }
}
