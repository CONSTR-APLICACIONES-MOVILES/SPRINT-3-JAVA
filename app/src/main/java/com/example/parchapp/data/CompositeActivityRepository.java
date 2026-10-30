package com.example.parchapp.data;

import androidx.annotation.Nullable;

import com.example.parchapp.domain.model.ActivityDetails;
import com.example.parchapp.domain.model.ComparisonEvidence;
import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.util.Callback;

import java.util.List;

/** Routes group-plan reads to Firestore and activity mutations/details to the backend callables. */
final class CompositeActivityRepository implements ActivityRepository {
    private final ActivityPlanRepository firestore;
    private final ActivityActionsRepository backend;

    CompositeActivityRepository(ActivityPlanRepository firestore, ActivityActionsRepository backend) {
        this.firestore = firestore;
        this.backend = backend;
    }

    @Override
    public void createActivity(PlannedActivity activity, Callback<PlannedActivity> callback) {
        backend.createActivity(activity, callback);
    }

    @Override
    public void createActivity(PlannedActivity activity, @Nullable ComparisonEvidence evidence,
                               Callback<PlannedActivity> callback) {
        backend.createActivity(activity, evidence, callback);
    }

    @Override
    public void getActivities(String userId, Callback<List<PlannedActivity>> callback) {
        firestore.getActivities(userId, callback);
    }

    @Override
    public void getActivityDetails(String activityId, Callback<ActivityDetails> callback) {
        backend.getActivityDetails(activityId, callback);
    }

    @Override
    public void setActivityReminder(String activityId, int minutesBefore, Callback<Void> callback) {
        backend.setActivityReminder(activityId, minutesBefore, callback);
    }

    @Nullable
    @Override
    public Integer getCachedTravelMinutes(String activityId) {
        return firestore.getCachedTravelMinutes(activityId);
    }

    @Override
    public void cacheTravelMinutes(String activityId, int minutes) {
        firestore.cacheTravelMinutes(activityId, minutes);
    }
}
