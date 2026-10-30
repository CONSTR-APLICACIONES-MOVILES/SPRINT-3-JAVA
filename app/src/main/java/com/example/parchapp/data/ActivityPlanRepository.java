package com.example.parchapp.data;

import androidx.annotation.Nullable;

import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.util.Callback;

import java.util.List;

/** Reads group plans and owns their short-lived travel-time cache. */
public interface ActivityPlanRepository {
    void getActivities(String userId, Callback<List<PlannedActivity>> callback);

    /** F7: travel minutes measured in the last 15 minutes, or null when the cache is empty/expired. */
    @Nullable
    Integer getCachedTravelMinutes(String activityId);

    void cacheTravelMinutes(String activityId, int minutes);

    /** Reuses a fresh estimate only for the destination where it was measured. */
    @Nullable
    default Integer getCachedTravelMinutesForDestination(String activityId, String destinationId) {
        return null;
    }

    /** Reuses a fresh estimate only when the current origin is inside the allowed radius. */
    @Nullable
    default Integer getCachedTravelMinutesNear(String activityId, double latitude, double longitude,
                                               String destinationId, double radiusMeters) {
        return null;
    }

    /** Stores the estimate with the location context required to determine if it is still reusable. */
    default void cacheTravelMinutesForPlace(String activityId, int minutes, double latitude,
                                            double longitude, String destinationId) {
        cacheTravelMinutes(activityId, minutes);
    }
}
