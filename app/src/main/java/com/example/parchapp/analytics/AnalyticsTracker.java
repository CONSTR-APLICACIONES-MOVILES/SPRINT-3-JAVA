package com.example.parchapp.analytics;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
    Singleton and Facade for analytics. Screens only call {@link #track(String, Map)}; the event 
    queue, the connectivity check and Firebase stay hidden here, so every event in the app uses the
    same format.
        <p>Events are sent in batches: when {@link #BATCH_SIZE} events are waiting, when the connection comes back, or when the app goes to the background. This saves battery (scenario 9.4).
 */
public final class AnalyticsTracker {
    static final int BATCH_SIZE = 10;

    public interface ConnectivityListener {
        void onConnectivityChanged(boolean online);
    }

    private static volatile AnalyticsTracker instance;

    private final EventQueue queue = new EventQueue();
    private final List<ConnectivityListener> connectivityListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private AnalyticsSink sink;
    private ConnectivityMonitor connectivity;
    private String pendingUserId;
    private volatile boolean analyticsConsent;

    private AnalyticsTracker() {
    }

    public static AnalyticsTracker getInstance() {
        if (instance == null) {
            synchronized (AnalyticsTracker.class) {
                if (instance == null) {
                    instance = new AnalyticsTracker();
                }
            }
        }
        return instance;
    }

    /* Called once from the Application. Events tracked before this are kept in the queue as we know. */
    public synchronized void init(Application application, boolean firebaseAvailable) {
        init(application, firebaseAvailable, false);
    }

    public synchronized void init(Application application, boolean firebaseAvailable, boolean analyticsConsent) {
        if (sink != null) {
            return;
        }
        this.analyticsConsent = analyticsConsent;
        queue.attach(application);
        sink = firebaseAvailable ? new FirebaseAnalyticsSink(application) : new LogcatSink();
        sink.setCollectionEnabled(analyticsConsent);
        if (!analyticsConsent) {
            queue.clear();
        }
        if (pendingUserId != null && analyticsConsent) {
            sink.setUserId(pendingUserId);
            pendingUserId = null;
        }
        connectivity = new ConnectivityMonitor(application);
        connectivity.addListener(online -> {
            if (online) {
                flush();
            }
            for (ConnectivityListener listener : connectivityListeners) {
                listener.onConnectivityChanged(online);
            }
        });
        application.registerActivityLifecycleCallbacks(new BackgroundFlusher());
    }

    public boolean isOnline() {
        ConnectivityMonitor current = connectivity;
        return current != null && current.isOnline();
    }

    public void addConnectivityListener(ConnectivityListener listener) {
        if (!connectivityListeners.contains(listener)) {
            connectivityListeners.add(listener);
            listener.onConnectivityChanged(isOnline());
        }
    }

    public void removeConnectivityListener(ConnectivityListener listener) {
        connectivityListeners.remove(listener);
    }

    public synchronized void setAnalyticsConsent(boolean accepted) {
        analyticsConsent = accepted;
        if (sink == null) {
            if (!accepted) queue.clear();
            return;
        }
        sink.setCollectionEnabled(accepted);
        if (accepted) {
            if (pendingUserId != null) {
                sink.setUserId(pendingUserId);
                pendingUserId = null;
            }
        } else {
            queue.clear();
            sink.setUserId(null);
        }
    }

    public void track(String name) {
        track(name, new HashMap<>());
    }

    public void track(String name, Map<String, Object> params) {
        boolean shouldFlush;
        synchronized (this) {
            if (!analyticsConsent) return;
            Map<String, Object> withTimestamp = new HashMap<>(params);
            // Batching delays the upload, so the real time of the event travels as a parameter.
            withTimestamp.put(AnalyticsEvents.PARAM_CLIENT_TS, System.currentTimeMillis());
            queue.add(name, withTimestamp);
            shouldFlush = queue.size() >= BATCH_SIZE;
        }
        if (shouldFlush) flush();
    }

    /* BQ8 shortcut: a core coordination feature was opened from {@code screen}. */
    public void trackFeature(String feature, String screen) {
        Map<String, Object> params = new HashMap<>();
        params.put(AnalyticsEvents.PARAM_FEATURE, feature);
        params.put(AnalyticsEvents.PARAM_SCREEN, screen);
        track(AnalyticsEvents.FEATURE_USED, params);
    }

    /* The raw ID never leaves the device, only its SHA-256 hash is sent. */
    public synchronized void setUserId(String rawUserId) {
        String anonymousId = rawUserId == null ? null : anonymise(rawUserId);
        if (sink == null) {
            pendingUserId = anonymousId;
        } else if (!analyticsConsent) {
            pendingUserId = anonymousId;
            sink.setUserId(null);
        } else {
            pendingUserId = null;
            sink.setUserId(anonymousId);
        }
    }

    public synchronized void flush() {
        if (sink == null || connectivity == null || !analyticsConsent || !connectivity.isOnline()) {
            return;
        }
        List<EventQueue.Event> batch = queue.drain();
        for (EventQueue.Event event : batch) {
            sink.send(event.name, event.toBundle());
        }
    }

    static String anonymise(String rawUserId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(rawUserId.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available on Android", e);
        }
    }

    private class BackgroundFlusher implements Application.ActivityLifecycleCallbacks {
        private int startedActivities;

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
            startedActivities++;
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
            startedActivities--;
            if (startedActivities == 0) {
                flush();
            }
        }

        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
        }
    }
}
