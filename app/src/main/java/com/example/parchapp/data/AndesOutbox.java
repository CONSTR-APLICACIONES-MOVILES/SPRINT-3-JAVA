package com.example.parchapp.data;

import androidx.annotation.Nullable;

import com.example.parchapp.domain.model.AndesEvent;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.functions.FirebaseFunctionsException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/* Eventual connectivity for "Add to Schedule" of Andes Events, the outbox of the Flutter app.
 * The wish to add an event is written to the phone first (Room, list "outbox"), so it survives closing the app
 * or losing the network, and only then sent. Each request goes pending -> syncing -> confirmed, or back to
 * pending when the network failed (it waits for the connection), or failed when the backend refused it (ended,
 * unpublished, changed, another account); a failed one is only sent again when the user taps Retry.
 * Multithreading: everything runs on one serial queue (thread parch-andes-outbox), so two syncs never send the
 * same request twice and a request is never read while it is being changed; the screen hears about changes on
 * the main thread. An event is never shown as added before the backend confirms it. */
public final class AndesOutbox {
    public static final String PENDING = "pending";
    public static final String SYNCING = "syncing";
    public static final String CONFIRMED = "confirmed";
    public static final String FAILED = "failed";

    public static final class Operation {
        public final AndesEvent event;
        public final String status;
        @Nullable
        public final String error;

        public Operation(AndesEvent event, String status, @Nullable String error) {
            this.event = event;
            this.status = status;
            this.error = error;
        }
    }

    /* the requests saved on the phone; called only from the outbox queue */
    public interface Store {
        void recover(String owner);

        List<Operation> list(String owner);

        void enqueue(String owner, AndesEvent event);

        void setStatus(String owner, String eventId, String status, @Nullable String error, boolean sent);

        void delete(String owner, String eventId);

        /* a failed request takes the event shown now and is pending again; false when it is no longer failed */
        boolean retryWithEvent(String owner, AndesEvent event);
    }

    public interface Sender {
        void send(String owner, AndesEvent event) throws Exception;
    }

    public interface Listener {
        void onOperationsChanged();
    }

    private final Store store;
    private final Sender sender;
    private final Supplier<String> currentOwner;
    private final Executor queue;
    private final Executor mainThread;
    private final Supplier<Long> clock;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile Map<String, Operation> operations = Collections.emptyMap();
    private volatile String visibleOwner;
    private boolean syncQueued;
    @Nullable
    private volatile Runnable onConfirmed;

    public AndesOutbox(Store store, Sender sender, Supplier<String> currentOwner, Executor queue,
                       Executor mainThread, Supplier<Long> clock) {
        this.store = store;
        this.sender = sender;
        this.currentOwner = currentOwner;
        this.queue = queue;
        this.mainThread = mainThread;
        this.clock = clock;
    }

    /* runs on the outbox queue each time the backend confirms an add */
    public void setOnConfirmed(@Nullable Runnable onConfirmed) {
        this.onConfirmed = onConfirmed;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /* the request of this event for the signed-in account, or null */
    @Nullable
    public Operation operation(String eventId) {
        String owner = currentOwner.get();
        return owner != null && owner.equals(visibleOwner) ? operations.get(eventId) : null;
    }

    public List<Operation> failed() {
        List<Operation> failed = new ArrayList<>();
        String owner = currentOwner.get();
        if (owner == null || !owner.equals(visibleOwner)) {
            return failed;
        }
        for (Operation operation : operations.values()) {
            if (FAILED.equals(operation.status)) {
                failed.add(operation);
            }
        }
        return failed;
    }

    /* reads the requests of the signed-in account again (on open, or after another account signed in) */
    public void refresh() {
        queue.execute(this::publish);
    }

    /* saves the wish to add the event; the caller decides whether to sync now (online) or later */
    public void enqueue(AndesEvent event, Callback done) {
        String owner = currentOwner.get();
        if (owner == null) {
            mainThread.execute(() -> done.onResult("Please sign in to add an event."));
            return;
        }
        if (!event.isValid() || event.hasEnded(clock.get())) {
            mainThread.execute(() -> done.onResult("This event is invalid or has already ended."));
            return;
        }
        queue.execute(() -> {
            String error = null;
            try {
                store.enqueue(owner, event);
            } catch (RuntimeException failure) {
                error = "Could not save the operation. Please try again.";
            }
            publish();
            String result = error;
            mainThread.execute(() -> done.onResult(result));
        });
    }

    public void retry(AndesEvent shownNow, Callback done) {
        String owner = currentOwner.get();
        if (owner == null) {
            mainThread.execute(() -> done.onResult("Please sign in again."));
            return;
        }
        queue.execute(() -> {
            boolean retried;
            try {
                retried = store.retryWithEvent(owner, shownNow);
            } catch (RuntimeException failure) {
                retried = false;
            }
            publish();
            boolean ok = retried;
            mainThread.execute(() -> done.onResult(ok ? null : "Could not save the operation. Please try again."));
            if (ok) {
                sync();
            }
        });
    }

    /* the event left my Schedule: its old request is not needed anymore */
    public void forget(String eventId) {
        String owner = currentOwner.get();
        if (owner == null) {
            return;
        }
        queue.execute(() -> {
            store.delete(owner, eventId);
            publish();
        });
    }

    /* sends what is pending; several calls while one sync is waiting in the queue become one */
    public void sync() {
        synchronized (this) {
            if (syncQueued) {
                return;
            }
            syncQueued = true;
        }
        queue.execute(() -> {
            synchronized (this) {
                syncQueued = false;
            }
            String owner = currentOwner.get();
            if (owner != null) {
                syncNow(owner);
            } else {
                publish();
            }
        });
    }

    // runs on the queue
    void syncNow(String owner) {
        store.recover(owner);
        publish();
        for (Operation operation : store.list(owner)) {
            if (!owner.equals(currentOwner.get())) {
                break;
            }
            if (!PENDING.equals(operation.status)) {
                continue;
            }
            String eventId = operation.event.getId();
            store.setStatus(owner, eventId, SYNCING, null, true);
            publish();
            try {
                sender.send(owner, operation.event);
                store.setStatus(owner, eventId, CONFIRMED, null, false);
                Runnable confirmed = onConfirmed;
                if (confirmed != null) {
                    confirmed.run();
                }
            } catch (Exception failure) {
                if (isTemporary(failure)) {
                    // no connection: it stays pending and the rest wait too, they would fail the same way
                    store.setStatus(owner, eventId, PENDING, "Waiting for a connection.", false);
                    publish();
                    return;
                }
                store.setStatus(owner, eventId, FAILED, failureMessage(failure), false);
            }
            publish();
        }
        publish();
    }

    private void publish() {
        String owner = currentOwner.get();
        Map<String, Operation> next = new LinkedHashMap<>();
        if (owner != null) {
            for (Operation operation : store.list(owner)) {
                next.put(operation.event.getId(), operation);
            }
        }
        operations = Collections.unmodifiableMap(next);
        visibleOwner = owner;
        mainThread.execute(() -> {
            for (Listener listener : listeners) {
                listener.onOperationsChanged();
            }
        });
    }

    /* the network or the server did not answer: the request is sent again later */
    static boolean isTemporary(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof TimeoutException || cause instanceof IOException
                    || cause instanceof FirebaseNetworkException) {
                return true;
            }
            if (cause instanceof FirebaseFunctionsException) {
                FirebaseFunctionsException.Code code = ((FirebaseFunctionsException) cause).getCode();
                return code == FirebaseFunctionsException.Code.UNAVAILABLE
                        || code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED
                        || code == FirebaseFunctionsException.Code.ABORTED
                        || code == FirebaseFunctionsException.Code.CANCELLED;
            }
            if (!(cause instanceof ExecutionException)) {
                break;
            }
        }
        return false;
    }

    /* why the backend refused it, in words the card can show */
    static String failureMessage(Throwable failure) {
        Throwable cause = failure instanceof ExecutionException && failure.getCause() != null ? failure.getCause() : failure;
        if (cause instanceof FirebaseFunctionsException) {
            FirebaseFunctionsException.Code code = ((FirebaseFunctionsException) cause).getCode();
            String message = cause.getMessage();
            switch (code) {
                case PERMISSION_DENIED:
                    return message != null && message.contains("another account")
                            ? "This request belongs to another account."
                            : "Access denied or this event is no longer published.";
                case UNAUTHENTICATED:
                    return "Please sign in again.";
                case NOT_FOUND:
                    return "This event is no longer available.";
                case FAILED_PRECONDITION:
                    return message != null && !message.isEmpty() ? message : "This event is invalid or has already ended.";
                default:
                    break;
            }
        }
        return "Could not add this event. Please try again.";
    }

    /* null when it worked, otherwise the message for the user */
    public interface Callback {
        void onResult(@Nullable String error);
    }
}
