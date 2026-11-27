package com.example.parchapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// the answer of getAndesEvents: valid events, invalid ones counted, and what is already in my Schedule
public class AndesCatalogParseTest {

    private static Map<String, Object> raw(String id, String start, String end) {
        Map<String, Object> event = new HashMap<>();
        event.put("id", id);
        event.put("title", "Feria");
        event.put("description", "Stands");
        event.put("category", "academic");
        event.put("location", "Santo Domingo");
        event.put("start", start);
        event.put("end", end);
        return event;
    }

    @Test
    public void readsTheCatalog() {
        Map<String, Object> data = new HashMap<>();
        data.put("events", Arrays.asList(raw("feria", "2026-11-25T19:00:00.000Z", "2026-11-25T21:00:00.000Z"),
                raw("broken", "yesterday", "2026-11-25T21:00:00.000Z")));
        data.put("invalidCount", 2);
        data.put("addedIds", Collections.singletonList("feria"));
        AndesEventsRepository.Catalog catalog = FunctionsAndesEventsRepository.parseCatalog(data);
        assertEquals(1, catalog.events.size());
        assertEquals(3, catalog.invalidCount);
        assertTrue(catalog.addedIds.contains("feria"));
        assertEquals(java.util.Date.UTC(126, 10, 25, 19, 0, 0), catalog.events.get(0).getStart());
    }

// the time the card showed goes back exactly as the backend wrote it, so it can compare it
    @Test
    public void isoRoundTrip() {
        String iso = "2026-11-25T19:00:00.000Z";
        assertEquals(iso, FunctionsAndesEventsRepository.iso(FunctionsAndesEventsRepository.instant(iso)));
        assertNull(FunctionsAndesEventsRepository.instant("2026-11-25"));
        List<Object> none = Collections.emptyList();
        assertEquals(0, FunctionsAndesEventsRepository.parseCatalog(Collections.singletonMap("events", none)).events.size());
    }
}
