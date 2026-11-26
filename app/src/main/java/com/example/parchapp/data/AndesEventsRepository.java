package com.example.parchapp.data;

import com.example.parchapp.domain.model.AndesEvent;
import com.example.parchapp.util.Callback;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/* Andes Events on the backend (Backend functions/src/andesEvents.js): the published catalog, removing an event
 * from Schedule, and the add that the outbox sends. The backend re-reads the event before adding it, so a
 * changed, ended or unpublished event is never added. */
public interface AndesEventsRepository {

    final class Catalog {
        public final List<AndesEvent> events;
        public final int invalidCount;
        public final Set<String> addedIds;

        public Catalog(List<AndesEvent> events, int invalidCount, Set<String> addedIds) {
            this.events = Collections.unmodifiableList(events);
            this.invalidCount = invalidCount;
            this.addedIds = Collections.unmodifiableSet(addedIds);
        }
    }

    /* every published event that has not ended, and the ids already in my Schedule */
    void load(Callback<Catalog> callback);

    void remove(String eventId, Callback<Void> callback);

    /* blocking, only from a background thread (the outbox queue): adds the event as it was shown to ownerId */
    void addBlocking(String ownerId, AndesEvent shown) throws Exception;
}
