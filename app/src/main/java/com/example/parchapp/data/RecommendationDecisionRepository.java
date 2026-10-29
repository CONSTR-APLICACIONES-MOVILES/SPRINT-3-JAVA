package com.example.parchapp.data;

import com.example.parchapp.util.Callback;

/** Persists an organizer's unchanged/modified choice for a previously presented BQ3 slot. */
public interface RecommendationDecisionRepository {
    /* What the organizer did with the backend suggestion at position: kept it unchanged or modified it. */
    final class PreparedDecision {
        public final String recommendationId;
        public final boolean wasModified;
        public final int position;

        public PreparedDecision(String recommendationId, boolean wasModified, int position) {
            this.recommendationId = recommendationId;
            this.wasModified = wasModified;
            this.position = position;
        }
    }

    /* BQ3: for an activity just created from a Compare Availability slot, asks the backend for its suggestions (today only, everyone free), acknowledges the visible ones as presented and works out the decision. The callback gets null when there is nothing to record. */
    void prepareDecision(String activityId, String date, int durationMinutes, int slotPosition,
                         long chosenStartMs, long chosenEndMs, Callback<PreparedDecision> callback);

    void recordDecision(String activityId, String recommendationId, boolean wasModified,
                        String selectedStartTime, String selectedEndTime,
                        Callback<Void> callback);
}
