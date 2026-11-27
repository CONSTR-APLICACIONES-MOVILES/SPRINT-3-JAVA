package com.example.parchapp;

import java.util.concurrent.CopyOnWriteArrayList;

/** In-process signal that the participant outbox changed, so an open screen can refresh. */
public final class ParticipantSyncEvents {
    private static final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    private ParticipantSyncEvents() {
    }

    public static void addListener(Runnable listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public static void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    public static void notifyChanged() {
        for (Runnable listener : listeners) listener.run();
    }
}
