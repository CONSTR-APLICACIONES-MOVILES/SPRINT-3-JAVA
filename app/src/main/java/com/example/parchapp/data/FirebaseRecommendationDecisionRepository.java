package com.example.parchapp.data;

import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.HashMap;
import java.util.Map;

/** The backend records the authoritative BQ3 outcome inside a Firestore transaction. */
public final class FirebaseRecommendationDecisionRepository implements RecommendationDecisionRepository {
    private final FirebaseFunctions functions;

    public FirebaseRecommendationDecisionRepository(FirebaseFunctions functions) {
        this.functions = functions;
    }

    @Override
    public void recordDecision(String activityId, String recommendationId, boolean wasModified,
                               String selectedStartTime, String selectedEndTime,
                               Callback<Void> callback) {
        Map<String, Object> input = new HashMap<>();
        input.put("activityId", activityId);
        input.put("recommendationId", recommendationId);
        String callable = "acceptActivityRecommendation";
        if (wasModified) {
            callable = "modifyActivityRecommendation";
            input.put("startTime", selectedStartTime);
            input.put("endTime", selectedEndTime);
        }
        functions.getHttpsCallable(callable).call(input)
                .addOnSuccessListener(result -> MainThread.post(() -> callback.onSuccess(null)))
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
    }
}
