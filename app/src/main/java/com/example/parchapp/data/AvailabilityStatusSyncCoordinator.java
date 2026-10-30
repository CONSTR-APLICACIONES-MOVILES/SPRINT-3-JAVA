package com.example.parchapp.data;

import com.example.parchapp.UserProfile;
import com.example.parchapp.UserSession;
import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.domain.AvailabilityStatusExpiry;
import com.example.parchapp.domain.StatusService;
import com.example.parchapp.util.BackgroundThreads;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;

/** Coalesces offline availability edits and publishes only the latest revision. */
public final class AvailabilityStatusSyncCoordinator implements StatusService.Listener,
        AnalyticsTracker.ConnectivityListener {
    private final StatusService status;
    private final PresenceRepository presence;
    private final AnalyticsTracker tracker;
    private final ExecutorService syncQueue = BackgroundThreads.serial("parch-presence-sync");
    private final Object lock = new Object();
    private boolean started;
    private boolean inFlight;
    private long inFlightRevision;
    private String inFlightUserId;
    private String failedUserId;
    private long failedRevision = -1L;

    public AvailabilityStatusSyncCoordinator(StatusService status, PresenceRepository presence,
                                             AnalyticsTracker tracker) {
        this.status = status;
        this.presence = presence;
        this.tracker = tracker;
    }

    public void start() {
        synchronized (lock) {
            if (started) return;
            started = true;
        }
        status.addListener(this);
        tracker.addConnectivityListener(this);
        onUserAvailable();
    }

    public void onUserAvailable() {
        UserProfile user = UserSession.getCurrentUser();
        status.setUserId(user == null ? null : user.getId());
        clearFailure();
        syncLatest();
    }

    public void onUserSignedOut() { status.setUserId(null); }

    public void retry() {
        clearFailure();
        syncLatest();
    }

    @Override public void onStatusChanged(StatusService changed) {
        if (!changed.isStatusSyncPending() || !changed.isAutoSyncEnabled()) return;
        UserProfile user = UserSession.getCurrentUser();
        if (user != null && user.getId().equals(failedUserId)
                && changed.getStatusRevision() == failedRevision) return;
        syncLatest();
    }

    @Override public void onConnectivityChanged(boolean online) {
        if (online) {
            clearFailure();
            syncLatest();
        }
    }

    private void syncLatest() {
        syncQueue.execute(this::syncLatestOnWorker);
    }

    private void syncLatestOnWorker() {
        UserProfile user = UserSession.getCurrentUser();
        if (user == null || presence == null || !status.isRemoteSyncAvailable()
                || !status.isAutoSyncEnabled() || !status.isStatusSyncPending()
                || !user.getId().equals(status.getUserId()) || !tracker.isOnline()) return;

        long revision = status.getStatusRevision();
        String userId = user.getId();
        long expiresAt = status.getAvailabilityExpiresAtMillis();
        if (!AvailabilityStatusExpiry.isValid(expiresAt, System.currentTimeMillis())) {
            MainThread.post(() -> status.markStatusExpiredBeforeSync(revision));
            return;
        }
        synchronized (lock) {
            if (inFlight) return;
            inFlight = true;
            inFlightRevision = revision;
            inFlightUserId = userId;
        }

        String expiry = isoUtc(expiresAt);
        try {
            presence.updatePresence(userId, backendStatus(status.getAvailability()), "groups", expiry,
                    new Callback<Void>() {
                        @Override public void onSuccess(Void ignored) { finish(userId, revision, null); }
                        @Override public void onError(Exception error) { finish(userId, revision, error); }
                    });
        } catch (RuntimeException error) {
            finish(userId, revision, error);
        }
    }

    private void finish(String userId, long revision, Exception error) {
        if (userId.equals(status.getUserId())) {
            if (error == null) {
                clearFailure();
                status.markStatusSynced(revision);
            } else if (!AvailabilityStatusExpiry.isValid(
                    status.getAvailabilityExpiresAtMillis(), System.currentTimeMillis())) {
                failedUserId = userId;
                failedRevision = revision;
                status.markStatusExpiredBeforeSync(revision);
            } else {
                failedUserId = userId;
                failedRevision = revision;
                String message = error.getLocalizedMessage();
                status.markStatusSyncError(revision, message == null || message.trim().isEmpty()
                        ? "Could not sync status. Tap to retry." : message);
            }
        }
        boolean retryNewerRevision;
        synchronized (lock) {
            UserProfile currentUser = UserSession.getCurrentUser();
            retryNewerRevision = inFlight && revision == inFlightRevision
                    && userId.equals(inFlightUserId) && currentUser != null
                    && currentUser.getId().equals(status.getUserId())
                    && status.isStatusSyncPending() && status.isAutoSyncEnabled()
                    && (!userId.equals(currentUser.getId()) || status.getStatusRevision() != revision);
            inFlight = false;
            inFlightUserId = null;
        }
        if (retryNewerRevision) syncLatest();
    }

    private void clearFailure() {
        failedUserId = null;
        failedRevision = -1L;
    }

    static String backendStatus(String label) {
        Map<String, String> values = new HashMap<>();
        values.put("Available now", "active");
        values.put("In Class", "inClass");
        values.put("Deep Focus", "focused");
        values.put("Busy / Personal", "busy");
        values.put("Commute / In Transit", "inTransit");
        return values.getOrDefault(label, "active");
    }

    static String isoUtc(long timestamp) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(timestamp));
    }
}
