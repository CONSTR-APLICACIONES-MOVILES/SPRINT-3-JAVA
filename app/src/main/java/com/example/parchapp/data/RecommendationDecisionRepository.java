package com.example.parchapp.data;

import com.example.parchapp.util.Callback;

/** Persists an organizer's unchanged/modified choice for a previously presented BQ3 slot. */
public interface RecommendationDecisionRepository {
    void recordDecision(String activityId, String recommendationId, boolean wasModified,
                        String selectedStartTime, String selectedEndTime,
                        Callback<Void> callback);
}
