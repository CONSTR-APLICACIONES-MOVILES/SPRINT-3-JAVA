package com.example.parchapp.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.parchapp.domain.model.AndesEvent;

import org.junit.Test;

// caching of Andes Events: the 15 days window in Bogota decides what stays on the phone
public class AndesCacheWindowTest {
    private static final long HOUR = 3_600_000L;

    private static long bogota(int day, int hour) {
        // Nov 1 2026 00:00 in Bogota is 05:00 UTC
        long novFirst = java.util.Date.UTC(126, 10, 1, 5, 0, 0);
        return novFirst + (day - 1) * 24 * HOUR + hour * HOUR;
    }

    private static AndesEvent event(long start, long end) {
        return new AndesEvent("e", "Title", "Description", "academic", "Place", start, end, null);
    }

// the window starts at today's midnight in Bogota and ends 15 midnights later
    @Test
    public void startsAtBogotaMidnight() {
        AndesCacheWindow window = new AndesCacheWindow(bogota(25, 10));
        assertEquals(bogota(25, 0), window.getStart());
        assertEquals(bogota(25 + AndesCacheWindow.DAYS, 0), window.getEnd());
    }

// late at night in Bogota it is already the next day in UTC, the window still starts today in Bogota
    @Test
    public void usesTheBogotaDayNotTheUtcDay() {
        AndesCacheWindow window = new AndesCacheWindow(bogota(25, 22));
        assertEquals(bogota(25, 0), window.getStart());
    }

// an ongoing event stays, an ended one or one after the 15 days does not
    @Test
    public void keepsOnlyEventsThatOverlapAndHaveNotEnded() {
        AndesCacheWindow window = new AndesCacheWindow(bogota(25, 10));
        assertTrue(window.includes(event(bogota(25, 9), bogota(25, 11))));
        assertTrue(window.includes(event(bogota(30, 18), bogota(30, 20))));
        assertFalse(window.includes(event(bogota(25, 7), bogota(25, 9))));
        assertFalse(window.includes(event(bogota(25 + 15, 8), bogota(25 + 15, 9))));
        assertTrue(window.includes(event(bogota(25 + 14, 23), bogota(25 + 15, 1))));
    }

// unknown categories become "other", like the backend and Flutter
    @Test
    public void normalizesTheCategory() {
        assertEquals("cultural", new AndesEvent("e", "t", "d", " Cultural ", "p", 1, 2, null).getCategory());
        assertEquals("other", new AndesEvent("e", "t", "d", "Club", "p", 1, 2, null).getCategory());
        assertEquals("Sports", new AndesEvent("e", "t", "d", "sports", "p", 1, 2, null).categoryLabel());
        assertTrue(new AndesEvent("e", "Feria", "Stands", "academic", "Santo Domingo", 1, 2, null).matches("santo"));
    }
}
