package com.example.parchapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.parchapp.domain.AndesCacheWindow;
import com.example.parchapp.domain.model.AndesEvent;
import com.example.parchapp.util.BackgroundThreads;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/* Local storage of Andes Events, the three lists of the phone in Room (table andes_events):
 * the catalog of the next 15 days for offline, the saved (bookmarked) events and the add outbox.
 * The time of the last confirmed catalog is one small value, so it lives in SharedPreferences.
 * Room never runs on the main thread: the screen calls these methods and gets the answer back on it. */
public final class AndesEventsStore implements AndesOutbox.Store {
    private static final String TAG = "AndesEventsStore";
    private static final String PREFS = "parchapp_andes_events";
    private static final String LAST_SYNC = "catalog.lastSync";
    // multithreading: one serial queue for the catalog and the saved events (thread parch-andes-db-1)
    private static final ExecutorService DB_QUEUE = BackgroundThreads.serial("parch-andes-db");

    public static final class CachedCatalog {
        public final List<AndesEvent> events;
        public final long lastSync;

        CachedCatalog(List<AndesEvent> events, long lastSync) {
            this.events = events;
            this.lastSync = lastSync;
        }
    }

    private final AndesEventsDao dao;
    private final SharedPreferences prefs;

    AndesEventsStore(Context context) {
        this.dao = ParchDatabase.get(context).andesEvents();
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------- Catalog (cache of the next 15 days) ----------

    /* evicts what left the window and reads the rest, with the time of the last confirmed catalog */
    public void readCatalog(AndesCacheWindow window, Callback<CachedCatalog> callback) {
        DB_QUEUE.execute(() -> {
            try {
                List<AndesEvent> events = toEvents(dao.readCatalog(window.getNow(), window.getStart(), window.getEnd()));
                CachedCatalog cached = new CachedCatalog(events, prefs.getLong(LAST_SYNC, 0));
                MainThread.post(() -> callback.onSuccess(cached));
            } catch (RuntimeException failure) {
                Log.w(TAG, "Could not read the cached Andes Events", failure);
                MainThread.post(() -> callback.onError(failure));
            }
        });
    }

    /* after the backend confirmed the catalog: only the events in the window are kept, all at once */
    public void replaceCatalog(List<AndesEvent> events, AndesCacheWindow window, Callback<Long> callback) {
        DB_QUEUE.execute(() -> {
            try {
                List<AndesEventEntity> rows = new ArrayList<>();
                for (AndesEvent event : events) {
                    if (window.includes(event)) {
                        rows.add(AndesEventEntity.of("", AndesEventEntity.CATALOG, event, window.getNow()));
                    }
                }
                dao.replaceCatalog(rows);
                prefs.edit().putLong(LAST_SYNC, window.getNow()).apply();
                MainThread.post(() -> callback.onSuccess(window.getNow()));
            } catch (RuntimeException failure) {
                Log.w(TAG, "Could not cache the Andes Events", failure);
                MainThread.post(() -> callback.onError(failure));
            }
        });
    }

    // ---------- Saved events (bookmarks of one account, never evicted) ----------

    public void saved(String owner, Callback<List<AndesEvent>> callback) {
        DB_QUEUE.execute(() -> {
            try {
                List<AndesEvent> events = toEvents(dao.list(owner, AndesEventEntity.SAVED));
                MainThread.post(() -> callback.onSuccess(events));
            } catch (RuntimeException failure) {
                MainThread.post(() -> callback.onError(failure));
            }
        });
    }

    public void setSaved(String owner, AndesEvent event, boolean saved, Callback<Void> callback) {
        DB_QUEUE.execute(() -> {
            try {
                if (saved) {
                    dao.put(AndesEventEntity.of(owner, AndesEventEntity.SAVED, event, System.currentTimeMillis()));
                } else {
                    dao.delete(owner, AndesEventEntity.SAVED, event.getId());
                }
                MainThread.post(() -> callback.onSuccess(null));
            } catch (RuntimeException failure) {
                MainThread.post(() -> callback.onError(failure));
            }
        });
    }

    // ---------- Outbox (AndesOutbox calls these from its own queue) ----------

    @Override
    public void recover(String owner) {
        dao.recover(owner);
    }

    @Override
    public List<AndesOutbox.Operation> list(String owner) {
        List<AndesOutbox.Operation> operations = new ArrayList<>();
        for (AndesEventEntity row : dao.list(owner, AndesEventEntity.OUTBOX)) {
            operations.add(new AndesOutbox.Operation(row.toEvent(),
                    row.status == null ? AndesOutbox.PENDING : row.status, row.error));
        }
        return operations;
    }

    @Override
    public void enqueue(String owner, AndesEvent event) {
        AndesEventEntity row = AndesEventEntity.of(owner, AndesEventEntity.OUTBOX, event, System.currentTimeMillis());
        row.status = AndesOutbox.PENDING;
        dao.putIfAbsent(row);
    }

    @Override
    public void setStatus(String owner, String eventId, String status, @Nullable String error, boolean sent) {
        dao.setStatus(owner, eventId, status, error, System.currentTimeMillis(), sent ? 1 : 0);
    }

    @Override
    public void delete(String owner, String eventId) {
        dao.delete(owner, AndesEventEntity.OUTBOX, eventId);
    }

    @Override
    public boolean retryWithEvent(String owner, AndesEvent event) {
        for (AndesEventEntity row : dao.list(owner, AndesEventEntity.OUTBOX)) {
            if (row.eventId.equals(event.getId()) && AndesOutbox.FAILED.equals(row.status)) {
                AndesEventEntity next = AndesEventEntity.of(owner, AndesEventEntity.OUTBOX, event, row.storedAt);
                next.status = AndesOutbox.PENDING;
                next.retryCount = row.retryCount;
                next.updatedAt = System.currentTimeMillis();
                dao.put(next);
                return true;
            }
        }
        return false;
    }

    private static List<AndesEvent> toEvents(List<AndesEventEntity> rows) {
        List<AndesEvent> events = new ArrayList<>();
        for (AndesEventEntity row : rows) {
            events.add(row.toEvent());
        }
        return events;
    }
}
