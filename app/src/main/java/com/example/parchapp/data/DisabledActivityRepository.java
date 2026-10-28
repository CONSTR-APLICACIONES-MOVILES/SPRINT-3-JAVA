package com.example.parchapp.data;

import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.domain.model.ActivityDetails;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;

import java.util.Collections;
import java.util.List;

/** Keeps activity views buildable while the activity/backend feature is implemented separately. */
class DisabledActivityRepository implements ActivityRepository {
    private final TravelTimeCache travelCache = new TravelTimeCache();
    @Override public void createActivity(PlannedActivity activity, Callback<PlannedActivity> callback) {
        MainThread.post(() -> callback.onError(new UnsupportedOperationException(
                "Activity publishing is not enabled in this base branch")));
    }
    @Override public void getActivities(String userId, Callback<List<PlannedActivity>> callback) {
        MainThread.post(() -> callback.onSuccess(Collections.emptyList()));
    }
    @Override public void getActivityDetails(String activityId, Callback<ActivityDetails> callback) {
        MainThread.post(() -> callback.onError(new UnsupportedOperationException(
                "Activity backend is not enabled in this base branch")));
    }
    @Override public void setActivityReminder(String activityId, int minutesBefore, Callback<Void> callback) {
        MainThread.post(() -> callback.onError(new UnsupportedOperationException(
                "Activity reminders are not enabled in this base branch")));

    @Override
    public Integer getCachedTravelMinutes(String activityId) {
        return travelCache.get(activityId);
    }

    @Override
    public void cacheTravelMinutes(String activityId, int minutes) {
        travelCache.put(activityId, minutes);
    }
}
