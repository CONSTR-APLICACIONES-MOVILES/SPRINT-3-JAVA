package com.example.parchapp.data;

import androidx.annotation.Nullable;

import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.util.Callback;

import java.util.ArrayList;
import java.util.List;

/** Reads group plans and owns their short-lived travel-time cache. */
public interface ActivityPlanRepository {
    void getActivities(String userId, Callback<List<PlannedActivity>> callback);

    /* Multithreading: the same read, but it waits for the answer, so call it only from a worker thread.
     * fromCache reads only what is saved on the phone. */
    default List<PlannedActivity> loadActivities(String userId, boolean fromCache) throws Exception {
        return new ArrayList<>();
    }

    /** F7: travel minutes measured in the last 15 minutes, or null when the cache is empty/expired. */
    @Nullable
    Integer getCachedTravelMinutes(String activityId);

    void cacheTravelMinutes(String activityId, int minutes);
}
