package com.example.parchapp.data;

import androidx.annotation.Nullable;

import com.example.parchapp.domain.model.ActivityDetails;
import com.example.parchapp.domain.model.ComparisonEvidence;
import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.util.Callback;

/** Creates activities and configures their backend-owned details/reminders. */
public interface ActivityActionsRepository {
    void createActivity(PlannedActivity activity, Callback<PlannedActivity> callback);

    /** Retries a durable local draft with its original server idempotency key. */
    default void createActivity(PlannedActivity activity, String requestId,
                                @Nullable ComparisonEvidence evidence,
                                Callback<PlannedActivity> callback) {
        createActivity(activity, evidence, callback);
    }

    /* BQ9: publishes with what the backend needs to know about the comparison behind the plan; adapters without a backend ignore it */
    default void createActivity(PlannedActivity activity, @Nullable ComparisonEvidence evidence,
                                Callback<PlannedActivity> callback) {
        createActivity(activity, callback);
    }

    void getActivityDetails(String activityId, Callback<ActivityDetails> callback);

    void setActivityReminder(String activityId, int minutesBefore, Callback<Void> callback);
}
