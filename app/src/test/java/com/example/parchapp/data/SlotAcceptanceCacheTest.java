package com.example.parchapp.data;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.example.parchapp.domain.model.SlotAcceptanceInsight;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

// tests for the bq3 result kept in memory
public class SlotAcceptanceCacheTest {

    private final AtomicLong now = new AtomicLong(1_000L);
    private final SlotAcceptanceCache cache = new SlotAcceptanceCache(now::get);
    private final SlotAcceptanceInsight insight = new SlotAcceptanceInsight(1, 25.9, 27);

// at the start there is nothing saved
    @Test
    public void emptyAtTheStart() {
        assertNull(cache.get());
    }

// while it is fresh the same result comes back without asking firestore
    @Test
    public void freshValueComesBack() {
        cache.put(insight);
        now.addAndGet(SlotAcceptanceCache.TTL_MS - 1);
        assertSame(insight, cache.get());
    }

// after the ttl the result expired and the app must ask again
    @Test
    public void expiredValueIsNotUsed() {
        cache.put(insight);
        now.addAndGet(SlotAcceptanceCache.TTL_MS);
        assertNull(cache.get());
    }

// a new result replaces the old one and starts a new ttl
    @Test
    public void newValueStartsAgain() {
        cache.put(insight);
        now.addAndGet(SlotAcceptanceCache.TTL_MS - 1);
        SlotAcceptanceInsight newer = new SlotAcceptanceInsight(0, 40.0, 30);
        cache.put(newer);
        now.addAndGet(SlotAcceptanceCache.TTL_MS - 1);
        assertSame(newer, cache.get());
    }
}
