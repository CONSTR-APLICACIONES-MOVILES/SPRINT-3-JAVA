package com.example.parchapp.data;

import com.example.parchapp.domain.SlotDecision;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;
import com.google.firebase.functions.FirebaseFunctions;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** The backend records the authoritative BQ3 outcome inside a Firestore transaction. */
public final class FirebaseRecommendationDecisionRepository implements RecommendationDecisionRepository {
    private final FirebaseFunctions functions;

    public FirebaseRecommendationDecisionRepository(FirebaseFunctions functions) {
        this.functions = functions;
    }

    // the backend allows up to 10 suggestions; enough to find the saved time among them
    private static final int SUGGESTION_LIMIT = 10;

    @Override
    public void prepareDecision(String activityId, String date, int durationMinutes, int slotPosition,
                                long chosenStartMs, long chosenEndMs, Callback<PreparedDecision> callback) {
        Map<String, Object> input = new HashMap<>();
        input.put("activityId", activityId);
        input.put("date", date);
        input.put("durationMinutes", durationMinutes);
        input.put("limit", SUGGESTION_LIMIT);
        functions.getHttpsCallable("getActivityRecommendations").call(input)
                .addOnSuccessListener(result -> {
                    List<String> ids = new ArrayList<>();
                    List<long[]> slots = new ArrayList<>();
                    Object data = result.getData();
                    Object raw = data instanceof Map ? ((Map<?, ?>) data).get("recommendations") : null;
                    if (raw instanceof List) {
                        for (Object item : (List<?>) raw) {
                            if (!(item instanceof Map)) continue;
                            Map<?, ?> candidate = (Map<?, ?>) item;
                            Long start = parseIso(candidate.get("startTime"));
                            Long end = parseIso(candidate.get("endTime"));
                            if (candidate.get("id") == null || start == null || end == null) continue;
                            ids.add(candidate.get("id").toString());
                            slots.add(new long[]{start, end});
                        }
                    }
                    SlotDecision decision = SlotDecision.choose(slots, chosenStartMs, chosenEndMs, slotPosition);
                    if (decision == null) {
                        MainThread.post(() -> callback.onSuccess(null));
                        return;
                    }
                    // only the suggestions the organizer could see count as presented, so positions are not inflated
                    present(activityId, ids.subList(0, SlotDecision.presentedCount(ids.size(), decision)),
                            new PreparedDecision(ids.get(decision.position), decision.modified, decision.position),
                            callback);
                })
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
    }

    private void present(String activityId, List<String> shownIds, PreparedDecision decision,
                         Callback<PreparedDecision> callback) {
        Map<String, Object> input = new HashMap<>();
        input.put("activityId", activityId);
        input.put("recommendationIds", new ArrayList<>(shownIds));
        functions.getHttpsCallable("presentActivityRecommendations").call(input)
                .addOnSuccessListener(result -> MainThread.post(() -> callback.onSuccess(decision)))
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
    }

    /* the backend sends Date.toISOString(), for example 2026-10-28T22:30:00.000Z */
    private static Long parseIso(Object value) {
        if (value == null) return null;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            return format.parse(value.toString()).getTime();
        } catch (ParseException error) {
            return null;
        }
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
