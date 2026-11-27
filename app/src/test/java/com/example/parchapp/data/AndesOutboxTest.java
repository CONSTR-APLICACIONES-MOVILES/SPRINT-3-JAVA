package com.example.parchapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.Nullable;

import com.example.parchapp.domain.model.AndesEvent;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// eventual connectivity of Andes Events: the add requests saved on the phone and how they are sent
public class AndesOutboxTest {
    private static final long NOW = 1_000_000L;

    private final FakeStore store = new FakeStore();
    private final List<String> sent = new ArrayList<>();
    private final Map<String, Exception> failures = new HashMap<>();
    private String owner = "ana";
    private int confirmations;
    private final AndesOutbox outbox = new AndesOutbox(store, (who, event) -> {
        sent.add(who + ":" + event.getId());
        Exception failure = failures.get(event.getId());
        if (failure != null) {
            throw failure;
        }
    }, () -> owner, Runnable::run, Runnable::run, () -> NOW);

    {
        outbox.setOnConfirmed(() -> confirmations++);
    }

    private static AndesEvent event(String id) {
        return new AndesEvent(id, "Title " + id, "Description", "academic", "Place", NOW + 1000, NOW + 5000, null);
    }

    private String enqueue(AndesEvent event) {
        String[] result = {"not called"};
        outbox.enqueue(event, error -> result[0] = error);
        return result[0];
    }

// added offline it waits as pending, and is confirmed only when the backend answers
    @Test
    public void sendsPendingRequestsAndConfirmsThem() {
        assertNull(enqueue(event("a")));
        assertNull(enqueue(event("b")));
        assertEquals(AndesOutbox.PENDING, outbox.operation("a").status);
        outbox.sync();
        assertEquals(List.of("ana:a", "ana:b"), sent);
        assertEquals(AndesOutbox.CONFIRMED, outbox.operation("a").status);
        assertEquals(AndesOutbox.CONFIRMED, outbox.operation("b").status);
        assertEquals(2, confirmations);
        // a confirmed request is not sent again
        outbox.sync();
        assertEquals(2, sent.size());
    }

// without a connection the request stays pending and the next ones are not tried
    @Test
    public void aLostConnectionKeepsItPending() {
        enqueue(event("a"));
        enqueue(event("b"));
        failures.put("a", new IOException("offline"));
        outbox.sync();
        assertEquals(List.of("ana:a"), sent);
        assertEquals(AndesOutbox.PENDING, outbox.operation("a").status);
        assertEquals(AndesOutbox.PENDING, outbox.operation("b").status);
        failures.clear();
        outbox.sync();
        assertEquals(AndesOutbox.CONFIRMED, outbox.operation("b").status);
    }

// a refusal of the backend fails once and is only sent again after Retry, with the event shown now
    @Test
    public void aRefusalFailsUntilRetry() {
        enqueue(event("a"));
        failures.put("a", new IllegalStateException("refused"));
        outbox.sync();
        outbox.sync();
        assertEquals(1, sent.size());
        assertEquals(AndesOutbox.FAILED, outbox.operation("a").status);
        assertEquals("Could not add this event. Please try again.", outbox.operation("a").error);
        assertEquals(1, outbox.failed().size());
        failures.clear();
        String[] result = {"not called"};
        outbox.retry(event("a"), error -> result[0] = error);
        assertNull(result[0]);
        assertEquals(AndesOutbox.CONFIRMED, outbox.operation("a").status);
    }

// asking twice keeps one request, and an app closed while sending sends it again
    @Test
    public void oneRequestPerEventAndRecoveryAfterACrash() {
        enqueue(event("a"));
        enqueue(event("a"));
        assertEquals(1, store.rows.size());
        store.rows.get("ana:a").status = AndesOutbox.SYNCING;
        outbox.sync();
        assertEquals(List.of("ana:a"), sent);
    }

// requests belong to the account that made them; another account neither sees nor sends them
    @Test
    public void requestsBelongToTheirAccount() {
        enqueue(event("a"));
        owner = "luis";
        outbox.sync();
        assertTrue(sent.isEmpty());
        assertNull(outbox.operation("a"));
        assertEquals("This event is invalid or has already ended.",
                enqueueEnded());
    }

    private String enqueueEnded() {
        String[] result = {"not called"};
        outbox.enqueue(new AndesEvent("old", "t", "d", "academic", "p", NOW - 5000, NOW - 1000, null), error -> result[0] = error);
        return result[0];
    }

    private static final class Row {
        AndesEvent event;
        String status;
        String error;
    }

    private static final class FakeStore implements AndesOutbox.Store {
        final Map<String, Row> rows = new LinkedHashMap<>();

        @Override
        public void recover(String owner) {
            for (Map.Entry<String, Row> entry : rows.entrySet()) {
                if (entry.getKey().startsWith(owner + ":") && AndesOutbox.SYNCING.equals(entry.getValue().status)) {
                    entry.getValue().status = AndesOutbox.PENDING;
                }
            }
        }

        @Override
        public List<AndesOutbox.Operation> list(String owner) {
            List<AndesOutbox.Operation> list = new ArrayList<>();
            for (Map.Entry<String, Row> entry : rows.entrySet()) {
                if (entry.getKey().startsWith(owner + ":")) {
                    list.add(new AndesOutbox.Operation(entry.getValue().event, entry.getValue().status, entry.getValue().error));
                }
            }
            return list;
        }

        @Override
        public void enqueue(String owner, AndesEvent event) {
            if (!rows.containsKey(owner + ":" + event.getId())) {
                Row row = new Row();
                row.event = event;
                row.status = AndesOutbox.PENDING;
                rows.put(owner + ":" + event.getId(), row);
            }
        }

        @Override
        public void setStatus(String owner, String eventId, String status, @Nullable String error, boolean sent) {
            Row row = rows.get(owner + ":" + eventId);
            row.status = status;
            row.error = error;
        }

        @Override
        public void delete(String owner, String eventId) {
            rows.remove(owner + ":" + eventId);
        }

        @Override
        public boolean retryWithEvent(String owner, AndesEvent event) {
            Row row = rows.get(owner + ":" + event.getId());
            if (row == null || !AndesOutbox.FAILED.equals(row.status)) {
                return false;
            }
            row.event = event;
            row.status = AndesOutbox.PENDING;
            row.error = null;
            return true;
        }
    }
}
