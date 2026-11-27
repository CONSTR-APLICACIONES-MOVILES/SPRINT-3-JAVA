package com.example.parchapp.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

// local storage: the queries of the Andes Events lists; room does not run them on the main thread,
// AndesEventsStore calls them from its own serial queue
@Dao
public abstract class AndesEventsDao {
    @Query("SELECT * FROM andes_events WHERE owner = :owner AND list = :list ORDER BY startAt, eventId")
    public abstract List<AndesEventEntity> list(String owner, String list);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void put(AndesEventEntity row);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void putAll(List<AndesEventEntity> rows);

    // the outbox keeps the first request: asking twice to add the same event does not reset its status
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public abstract long putIfAbsent(AndesEventEntity row);

    @Query("DELETE FROM andes_events WHERE owner = :owner AND list = :list AND eventId = :eventId")
    public abstract void delete(String owner, String list, String eventId);

    @Query("DELETE FROM andes_events WHERE owner = :owner AND list = :list")
    public abstract void deleteList(String owner, String list);

    // eviction by date: what ended or is outside the 15 days window leaves the cached catalog
    @Query("DELETE FROM andes_events WHERE owner = :owner AND list = 'catalog' "
            + "AND (endAt <= :now OR endAt <= :windowStart OR startAt >= :windowEnd)")
    public abstract int evict(String owner, long now, long windowStart, long windowEnd);

    @Query("UPDATE andes_events SET status = :status, error = :error, updatedAt = :now, "
            + "retryCount = retryCount + :sent WHERE owner = :owner AND list = 'outbox' AND eventId = :eventId")
    public abstract int setStatus(String owner, String eventId, String status, String error, long now, int sent);

    // an app closed while sending: what was "syncing" is sent again
    @Query("UPDATE andes_events SET status = 'pending' WHERE owner = :owner AND list = 'outbox' AND status = 'syncing'")
    public abstract int recover(String owner);

    // read the window and evict in one transaction, so the screen never shows a row that was being deleted
    @Transaction
    public List<AndesEventEntity> readCatalog(long now, long windowStart, long windowEnd) {
        evict("", now, windowStart, windowEnd);
        return list("", AndesEventEntity.CATALOG);
    }

    // a confirmed answer of the server replaces the whole cached catalog at once, or nothing changes
    @Transaction
    public void replaceCatalog(List<AndesEventEntity> rows) {
        deleteList("", AndesEventEntity.CATALOG);
        putAll(rows);
    }
}
