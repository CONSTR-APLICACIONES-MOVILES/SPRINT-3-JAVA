package com.example.parchapp.data;

import static org.junit.Assert.assertEquals;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.parchapp.domain.AndesCacheWindow;
import com.example.parchapp.domain.model.AndesEvent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// local storage of Andes Events in room, on a real sqlite of the device but in memory
@RunWith(AndroidJUnit4.class)
public class AndesEventsDaoTest {
    private static final long HOUR = 3_600_000L;
    private static final long NOW = java.util.Date.UTC(126, 10, 25, 15, 0, 0);
    private ParchDatabase db;
    private AndesEventsDao dao;

    @Before
    public void open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                ParchDatabase.class).build();
        dao = db.andesEvents();
    }

    @After
    public void close() {
        db.close();
    }

    private static AndesEvent event(String id, long start, long end) {
        return new AndesEvent(id, "Title " + id, "Description", "academic", "Place", start, end, null);
    }

    private static List<String> ids(List<AndesEventEntity> rows) {
        List<String> ids = new ArrayList<>();
        for (AndesEventEntity row : rows) {
            ids.add(row.eventId);
        }
        return ids;
    }

// reading the cached catalog first deletes what ended or left the 15 days window
    @Test
    public void evictsByDateWhenReading() {
        AndesCacheWindow window = new AndesCacheWindow(NOW);
        dao.replaceCatalog(Arrays.asList(
                AndesEventEntity.of("", AndesEventEntity.CATALOG, event("soon", NOW + HOUR, NOW + 2 * HOUR), NOW),
                AndesEventEntity.of("", AndesEventEntity.CATALOG, event("ended", NOW - 2 * HOUR, NOW - HOUR), NOW),
                AndesEventEntity.of("", AndesEventEntity.CATALOG, event("far", NOW + 20 * 24 * HOUR, NOW + 20 * 24 * HOUR + HOUR), NOW)));
        assertEquals(Arrays.asList("soon"), ids(dao.readCatalog(window.getNow(), window.getStart(), window.getEnd())));
    }

// a confirmed catalog replaces the old copy completely, saved events and requests are not touched
    @Test
    public void replacingTheCatalogKeepsSavedAndOutbox() {
        dao.put(AndesEventEntity.of("ana", AndesEventEntity.SAVED, event("bookmark", NOW + HOUR, NOW + 2 * HOUR), NOW));
        dao.putIfAbsent(AndesEventEntity.of("ana", AndesEventEntity.OUTBOX, event("add", NOW + HOUR, NOW + 2 * HOUR), NOW));
        dao.replaceCatalog(Arrays.asList(AndesEventEntity.of("", AndesEventEntity.CATALOG, event("old", NOW + HOUR, NOW + 2 * HOUR), NOW)));
        dao.replaceCatalog(Arrays.asList(AndesEventEntity.of("", AndesEventEntity.CATALOG, event("new", NOW + HOUR, NOW + 2 * HOUR), NOW)));
        assertEquals(Arrays.asList("new"), ids(dao.list("", AndesEventEntity.CATALOG)));
        assertEquals(Arrays.asList("bookmark"), ids(dao.list("ana", AndesEventEntity.SAVED)));
        assertEquals(Arrays.asList("add"), ids(dao.list("ana", AndesEventEntity.OUTBOX)));
    }

// the outbox keeps the first request of an event, and "syncing" goes back to "pending" after a crash
    @Test
    public void outboxRequestsAreUniqueAndRecoverable() {
        AndesEventEntity first = AndesEventEntity.of("ana", AndesEventEntity.OUTBOX, event("a", NOW + HOUR, NOW + 2 * HOUR), NOW);
        first.status = AndesOutbox.PENDING;
        dao.putIfAbsent(first);
        dao.setStatus("ana", "a", AndesOutbox.SYNCING, null, NOW, 1);
        AndesEventEntity again = AndesEventEntity.of("ana", AndesEventEntity.OUTBOX, event("a", NOW + HOUR, NOW + 2 * HOUR), NOW);
        again.status = AndesOutbox.PENDING;
        assertEquals(-1, dao.putIfAbsent(again));
        assertEquals(AndesOutbox.SYNCING, dao.list("ana", AndesEventEntity.OUTBOX).get(0).status);
        assertEquals(1, dao.recover("ana"));
        AndesEventEntity row = dao.list("ana", AndesEventEntity.OUTBOX).get(0);
        assertEquals(AndesOutbox.PENDING, row.status);
        assertEquals(1, row.retryCount);
        assertEquals(0, dao.list("luis", AndesEventEntity.OUTBOX).size());
    }
}
