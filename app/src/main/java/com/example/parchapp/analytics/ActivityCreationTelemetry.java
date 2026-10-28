package com.example.parchapp.analytics;

import java.util.HashMap;
import java.util.Map;

/** Keeps the BQ2 event contract identical for every activity creation entry point. */
public final class ActivityCreationTelemetry {
    private ActivityCreationTelemetry() {
    }

    public static void attempt(String sessionId, String source, int groupSize) {
        Map<String, Object> params = new HashMap<>();
        params.put(AnalyticsEvents.PARAM_CREATION_SESSION_ID, sessionId);
        params.put(AnalyticsEvents.PARAM_SOURCE, source);
        params.put(AnalyticsEvents.PARAM_GROUP_SIZE, groupSize);
        AnalyticsTracker.getInstance().track(AnalyticsEvents.ACTIVITY_CREATE_ATTEMPT, params);
    }

    public static void result(String sessionId, String source, long durationMs, boolean success,
                              String activityId, String errorCode) {
        Map<String, Object> params = new HashMap<>();
        params.put(AnalyticsEvents.PARAM_CREATION_SESSION_ID, sessionId);
        params.put(AnalyticsEvents.PARAM_SOURCE, source);
        params.put(AnalyticsEvents.PARAM_DURATION_MS, durationMs);
        params.put(AnalyticsEvents.PARAM_RESULT, success ? "success" : "failure");
        if (activityId != null) {
            params.put(AnalyticsEvents.PARAM_ACTIVITY_ID, activityId);
        }
        if (errorCode != null) {
            params.put(AnalyticsEvents.PARAM_ERROR_CODE, errorCode);
        }
        AnalyticsTracker.getInstance().track(AnalyticsEvents.ACTIVITY_CREATE_RESULT, params);
    }
}
