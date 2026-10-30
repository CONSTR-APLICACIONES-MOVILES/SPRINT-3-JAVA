package com.example.parchapp.data;

import android.content.Context;

import com.example.parchapp.UserProfile;
import com.example.parchapp.UserSession;
import com.example.parchapp.analytics.ActivityCreationTelemetry;
import com.example.parchapp.analytics.AnalyticsEvents;
import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.analytics.CreationFlowTracker;
import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;
import com.google.firebase.functions.FirebaseFunctionsException;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Persists activity publishes before sending and retries them when connectivity returns. */
public final class ActivityCreationSyncCoordinator implements AnalyticsTracker.ConnectivityListener {
    public interface Listener {
        void onPendingActivityChanged(PendingActivityCreation activity);
    }

    public interface EnqueueCallback {
        void onSaved(PendingActivityCreation activity);
        void onError(Exception error);
    }

    private final ActivityCreationOutbox outbox;
    private final ActivityRepository activities;
    private final RecommendationDecisionRepository recommendations;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile AnalyticsTracker tracker;

    public ActivityCreationSyncCoordinator(Context context, ActivityRepository activities,
                                           RecommendationDecisionRepository recommendations) {
        this.outbox = new ActivityCreationOutbox(context);
        this.activities = activities;
        this.recommendations = recommendations;
    }

    public void start(AnalyticsTracker tracker) {
        this.tracker = tracker;
        tracker.addConnectivityListener(this);
        syncPendingForCurrentUser();
    }

    public void onUserAvailable() {
        syncPendingForCurrentUser();
    }

    public void enqueue(PendingActivityCreation activity, EnqueueCallback callback) {
        executor.execute(() -> {
            if (!outbox.save(activity)) {
                MainThread.post(() -> callback.onError(
                        new IllegalStateException("Could not save this activity for sync. Keep the form open and retry.")));
                return;
            }
            MainThread.post(() -> {
                callback.onSaved(activity);
                notifyListeners(activity);
            });
            sync(activity.getUserId(), activity.getRequestId());
        });
    }

    public PendingActivityCreation get(String userId, String requestId) {
        return outbox.get(userId, requestId);
    }

    public void addListener(Listener listener) {
        if (listener != null) listeners.addIfAbsent(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void retry(String requestId) {
        UserProfile user = UserSession.getCurrentUser();
        if (user == null) return;
        executor.execute(() -> {
            PendingActivityCreation current = outbox.get(user.getId(), requestId);
            if (current == null || current.getState() != PendingActivityCreation.State.PENDING) return;
            PendingActivityCreation pending = current.withState(PendingActivityCreation.State.PENDING,
                    "Waiting to sync…", null, current.getRecommendationOutcome());
            outbox.update(pending);
            MainThread.post(() -> notifyListeners(pending));
            sync(user.getId(), requestId);
        });
    }

    public void cancel(String requestId) {
        UserProfile user = UserSession.getCurrentUser();
        if (user == null) return;
        executor.execute(() -> {
            PendingActivityCreation current = outbox.get(user.getId(), requestId);
            if (current != null && current.getState() == PendingActivityCreation.State.NEEDS_REVIEW) {
                outbox.remove(user.getId(), requestId);
            }
        });
    }

    public void removeCreated(String userId, String requestId) {
        if (userId == null || requestId == null) return;
        executor.execute(() -> outbox.remove(userId, requestId));
    }

    @Override
    public void onConnectivityChanged(boolean online) {
        if (online) syncPendingForCurrentUser();
    }

    private void syncPendingForCurrentUser() {
        UserProfile user = UserSession.getCurrentUser();
        if (user == null) return;
        executor.execute(() -> {
            outbox.recoverInterrupted(user.getId());
            for (PendingActivityCreation activity : outbox.forUser(user.getId())) {
                if (activity.getState() == PendingActivityCreation.State.PENDING) {
                    sync(user.getId(), activity.getRequestId());
                }
            }
        });
    }

    private void sync(String userId, String requestId) {
        AnalyticsTracker currentTracker = tracker;
        if (currentTracker == null || !currentTracker.isOnline()) return;
        String key = userId + ":" + requestId;
        if (!inFlight.add(key)) return;
        PendingActivityCreation current = outbox.get(userId, requestId);
        if (current == null || current.getState() == PendingActivityCreation.State.NEEDS_REVIEW
                || current.getState() == PendingActivityCreation.State.CREATED) {
            inFlight.remove(key);
            return;
        }
        PendingActivityCreation syncing = current.withState(PendingActivityCreation.State.SYNCING,
                "Publishing activity…", null, current.getRecommendationOutcome());
        outbox.update(syncing);
        MainThread.post(() -> notifyListeners(syncing));
        try {
            activities.createActivity(current.getActivity(), current.getRequestId(),
                    current.getComparisonEvidence(), new Callback<PlannedActivity>() {
                    @Override
                    public void onSuccess(PlannedActivity saved) {
                        if (saved == null || saved.getId() == null) {
                            finishWithError(current, key, new IllegalStateException(
                                    "The server did not return an activity ID."));
                            return;
                        }
                        logCreationSuccess(current, saved);
                        recordRecommendationOutcome(current, saved.getId(), key);
                    }

                    @Override
                    public void onError(Exception error) {
                        finishWithError(current, key, error);
                    }
                    });
        } catch (RuntimeException error) {
            finishWithError(current, key, error);
        }
    }

    private void logCreationSuccess(PendingActivityCreation draft, PlannedActivity saved) {
        ActivityCreationTelemetry.result(draft.getCreationSessionId(), "group_activity",
                Math.max(0L, System.currentTimeMillis() - draft.getAttemptStartedAtMillis()),
                true, saved.getId(), null);
        Map<String, Object> event = new HashMap<>();
        event.put(AnalyticsEvents.PARAM_ACTIVITY_ID, saved.getId());
        event.put(AnalyticsEvents.PARAM_CREATION_SESSION_ID, draft.getCreationSessionId());
        event.put(AnalyticsEvents.PARAM_CATEGORY, saved.getCategory());
        event.put(AnalyticsEvents.PARAM_GROUP_SIZE, saved.getGroupSizeAtCreation());
        boolean compared = draft.getSlotPosition() >= 0 || draft.getComparisonEvidence() != null
                && draft.getComparisonEvidence().isComparison();
        event.put(AnalyticsEvents.PARAM_SOURCE, compared
                ? AnalyticsEvents.SOURCE_SUGGESTED_SLOT : AnalyticsEvents.SOURCE_GROUP_ACTIVITY);
        if (draft.getComparisonEvidence() != null && draft.getComparisonEvidence().isComparison()) {
            event.put(AnalyticsEvents.PARAM_COMPARISON_ID, draft.getComparisonEvidence().getId());
        }
        AnalyticsTracker.getInstance().track(AnalyticsEvents.ACTIVITY_CREATED, event);
        if (!draft.getFlowId().isEmpty()) {
            CreationFlowTracker.restore(draft.getFlowId(), draft.getFlowStartedAtMillis(),
                    draft.getFlowSteps(), false).finished();
        }
    }

    private void recordRecommendationOutcome(PendingActivityCreation draft, String activityId, String key) {
        if (draft.getSlotPosition() < 0 || recommendations == null
                || draft.getDurationMinutes() < 15 || draft.getSelectedStartMillis() <= 0
                || draft.getSelectedEndMillis() <= draft.getSelectedStartMillis()) {
            complete(draft, activityId, "");
            inFlight.remove(key);
            return;
        }
        recommendations.prepareDecision(activityId, draft.getActivity().getDate(), draft.getDurationMinutes(),
                draft.getSlotPosition(), draft.getSelectedStartMillis(), draft.getSelectedEndMillis(),
                draft.getShownStartMinutes(),
                new Callback<RecommendationDecisionRepository.PreparedDecision>() {
                    @Override
                    public void onSuccess(RecommendationDecisionRepository.PreparedDecision decision) {
                        if (decision == null) {
                            complete(draft, activityId, "");
                            inFlight.remove(key);
                            return;
                        }
                        String start = draft.getActivity().getStartTime();
                        String end = draft.getActivity().getEndTime();
                        recommendations.recordDecision(activityId, decision.recommendationId,
                                decision.wasModified, start, end, new Callback<Void>() {
                                    @Override public void onSuccess(Void ignored) {
                                        Map<String, Object> event = new HashMap<>();
                                        event.put(AnalyticsEvents.PARAM_RECOMMENDATION_ID, decision.recommendationId);
                                        event.put(AnalyticsEvents.PARAM_MODIFIED, decision.wasModified);
                                        event.put(AnalyticsEvents.PARAM_SLOT_POSITION, decision.position);
                                        event.put(AnalyticsEvents.PARAM_GROUP_SIZE, draft.getActivity().getGroupSizeAtCreation());
                                        AnalyticsTracker.getInstance().track(AnalyticsEvents.SLOT_ACCEPTED, event);
                                        complete(draft, activityId, decision.wasModified
                                                ? "The suggested time was modified" : "The suggested time was kept");
                                        inFlight.remove(key);
                                    }

                                    @Override public void onError(Exception error) {
                                        complete(draft, activityId,
                                                "Activity created; recommendation feedback could not be saved.");
                                        inFlight.remove(key);
                                    }
                                });
                    }

                    @Override
                    public void onError(Exception error) {
                        complete(draft, activityId,
                                "Activity created; recommendation feedback could not be saved.");
                        inFlight.remove(key);
                    }
                });
    }

    private void complete(PendingActivityCreation draft, String activityId, String recommendationOutcome) {
        PendingActivityCreation completed = draft.withState(PendingActivityCreation.State.CREATED,
                "Activity created", activityId, recommendationOutcome);
        executor.execute(() -> {
            outbox.update(completed);
            MainThread.post(() -> notifyListeners(completed));
        });
    }

    private void finishWithError(PendingActivityCreation draft, String key, Exception error) {
        boolean review = isPermanent(error);
        PendingActivityCreation updated = draft.withState(review
                        ? PendingActivityCreation.State.NEEDS_REVIEW : PendingActivityCreation.State.PENDING,
                error == null || error.getMessage() == null ? "Waiting to sync…" : error.getMessage(), null,
                draft.getRecommendationOutcome());
        if (review) {
            ActivityCreationTelemetry.result(draft.getCreationSessionId(), "group_activity",
                    Math.max(0L, System.currentTimeMillis() - draft.getAttemptStartedAtMillis()),
                    false, null, error == null ? "FAILED" : error.getClass().getSimpleName());
        }
        executor.execute(() -> {
            outbox.update(updated);
            inFlight.remove(key);
            MainThread.post(() -> notifyListeners(updated));
        });
    }

    private boolean isPermanent(Exception error) {
        if (!(error instanceof FirebaseFunctionsException)) return false;
        FirebaseFunctionsException.Code code = ((FirebaseFunctionsException) error).getCode();
        return code == FirebaseFunctionsException.Code.INVALID_ARGUMENT
                || code == FirebaseFunctionsException.Code.PERMISSION_DENIED
                || code == FirebaseFunctionsException.Code.UNAUTHENTICATED
                || code == FirebaseFunctionsException.Code.NOT_FOUND
                || code == FirebaseFunctionsException.Code.ALREADY_EXISTS
                || code == FirebaseFunctionsException.Code.FAILED_PRECONDITION;
    }

    private void notifyListeners(PendingActivityCreation activity) {
        for (Listener listener : listeners) listener.onPendingActivityChanged(activity);
    }
}
