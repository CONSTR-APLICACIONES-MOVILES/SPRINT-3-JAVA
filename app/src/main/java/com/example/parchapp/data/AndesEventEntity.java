package com.example.parchapp.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;

import com.example.parchapp.domain.model.AndesEvent;

/* Local storage for Andes Events: one snapshot of an event in one of three lists of the phone.
 *  - catalog: the events of the next 15 days, the copy shown offline (owner "", the catalog is public)
 *  - saved:   the events a person bookmarked, kept until they remove them (owner = their uid)
 *  - outbox:  the "Add to Schedule" a person asked for, waiting for the backend (owner = their uid), with its
 *             status (pending, syncing, confirmed, failed), the last error and how many times it was sent
 * The primary key (owner, list, eventId) keeps one row per event in each list, so the same bookmark or the same
 * add can never be repeated, and another account on the phone never sees these rows. */
@Entity(tableName = "andes_events",
        primaryKeys = {"owner", "list", "eventId"},
        indices = {@Index({"owner", "list", "startAt"})})
public class AndesEventEntity {
    static final String CATALOG = "catalog";
    static final String SAVED = "saved";
    static final String OUTBOX = "outbox";

    @NonNull
    public String owner = "";
    @NonNull
    public String list = "";
    @NonNull
    public String eventId = "";
    @NonNull
    public String title = "";
    @NonNull
    public String description = "";
    @NonNull
    public String category = "";
    @NonNull
    public String location = "";
    public long startAt;
    public long endAt;
    @Nullable
    public String sourceUrl;
    // System.currentTimeMillis() when it was cached, bookmarked or asked for
    public long storedAt;
    // outbox only
    @Nullable
    public String status;
    @Nullable
    public String error;
    public int retryCount;
    public long updatedAt;

    static AndesEventEntity of(String owner, String list, AndesEvent event, long now) {
        AndesEventEntity row = new AndesEventEntity();
        row.owner = owner;
        row.list = list;
        row.eventId = event.getId();
        row.title = event.getTitle();
        row.description = event.getDescription();
        row.category = event.getCategory();
        row.location = event.getLocation();
        row.startAt = event.getStart();
        row.endAt = event.getEnd();
        row.sourceUrl = event.getSourceUrl();
        row.storedAt = now;
        row.updatedAt = now;
        return row;
    }

    AndesEvent toEvent() {
        return new AndesEvent(eventId, title, description, category, location, startAt, endAt, sourceUrl);
    }
}
