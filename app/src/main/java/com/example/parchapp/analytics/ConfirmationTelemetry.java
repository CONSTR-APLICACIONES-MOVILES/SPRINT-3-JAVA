package com.example.parchapp.analytics;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.parchapp.domain.model.PlannedActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/* BQ9: activities are confirmed on the backend (status CONFIRMED), not in a screen of this app, so the app logs activity_confirmed the first time it sees a confirmed plan. Each plan is logged once per phone; bq9_comparison_confirmation.sql counts distinct activity ids, so the same plan seen on two phones still counts once. */
public final class ConfirmationTelemetry {
    private static final String PREFS = "parchapp_bq9";
    private static final String KEY_LOGGED = "confirmed_logged_ids";

    private ConfirmationTelemetry() {
    }

    public static void logNewlyConfirmed(Context context, List<PlannedActivity> activities) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> logged = new HashSet<>(prefs.getStringSet(KEY_LOGGED, new HashSet<>()));
        List<PlannedActivity> fresh = newlyConfirmed(activities, logged);
        if (fresh.isEmpty()) {
            return;
        }
        for (PlannedActivity activity : fresh) {
            Map<String, Object> params = new HashMap<>();
            params.put(AnalyticsEvents.PARAM_ACTIVITY_ID, activity.getId());
            params.put(AnalyticsEvents.PARAM_CATEGORY, activity.getCategory());
            params.put(AnalyticsEvents.PARAM_GROUP_SIZE, activity.getGroupSizeAtCreation());
            params.put(AnalyticsEvents.PARAM_LOCATION_AREA_ID, activity.getLocationAreaId());
            AnalyticsTracker.getInstance().track(AnalyticsEvents.ACTIVITY_CONFIRMED, params);
            logged.add(activity.getId());
        }
        prefs.edit().putStringSet(KEY_LOGGED, logged).apply();
    }

    /* confirmed plans with an id that were not logged before */
    static List<PlannedActivity> newlyConfirmed(List<PlannedActivity> activities, Set<String> logged) {
        List<PlannedActivity> fresh = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PlannedActivity activity : activities) {
            String id = activity.getId();
            if (id == null || id.isEmpty() || logged.contains(id) || !seen.add(id)) {
                continue;
            }
            if (PlannedActivity.STATUS_CONFIRMED.equals(activity.getStatus())) {
                fresh.add(activity);
            }
        }
        return fresh;
    }
}
