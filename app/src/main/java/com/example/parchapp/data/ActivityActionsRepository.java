package com.example.parchapp.data;

import androidx.annotation.Nullable;

import com.example.parchapp.domain.model.ActivityDetails;
import com.example.parchapp.domain.model.ActivityParticipants;
import com.example.parchapp.domain.model.ComparisonEvidence;
import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.util.Callback;

/** Creates activities and configures their backend-owned details/reminders. */
public interface ActivityActionsRepository {
    void createActivity(PlannedActivity activity, Callback<PlannedActivity> callback);

    /* BQ9: publishes with what the backend needs to know about the comparison behind the plan; adapters without a backend ignore it */
    default void createActivity(PlannedActivity activity, @Nullable ComparisonEvidence evidence,
                                Callback<PlannedActivity> callback) {
        createActivity(activity, callback);
    }

    default void createActivity(PlannedActivity activity, @Nullable ComparisonEvidence evidence,
                                String requestId, Callback<PlannedActivity> callback) {
        createActivity(activity, evidence, callback);
    }

    void getActivityDetails(String activityId, Callback<ActivityDetails> callback);

    void setActivityReminder(String activityId, int minutesBefore, Callback<Void> callback);

    default void getActivityParticipants(String activityId, Callback<ActivityParticipants> callback) {
        callback.onError(new UnsupportedOperationException("Participants need the backend"));
    }

    default void changeActivityParticipants(String activityId, String action, @Nullable String userId,
                                            String expectedVersion, String clientUserId,
                                            Callback<Void> callback) {
        callback.onError(new UnsupportedOperationException("Participants need the backend"));
    }

    default void inviteGroupFriend(String groupId, String userId, String clientUserId,
                                   Callback<Void> callback) {
        callback.onError(new UnsupportedOperationException("Group invitations need the backend"));
    }
}
