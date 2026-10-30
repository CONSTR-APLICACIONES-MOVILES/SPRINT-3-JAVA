package com.example.parchapp.data;

import androidx.annotation.Nullable;

import com.example.parchapp.domain.model.ActivityDetails;
import com.example.parchapp.domain.model.ComparisonEvidence;
import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;

import java.util.ArrayList;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Activity creation/details/reminders are handled through authenticated backend callables. */
public final class FirebaseActivityRepository implements ActivityActionsRepository {
    private final FirebaseFunctions functions;

    public FirebaseActivityRepository(FirebaseFunctions functions) {
        this.functions = functions;
    }

    @Override
    public void createActivity(PlannedActivity activity, Callback<PlannedActivity> callback) {
        createActivity(activity, UUID.randomUUID().toString(), null, callback);
    }

    @Override
    public void createActivity(PlannedActivity activity, @Nullable ComparisonEvidence evidence,
                               Callback<PlannedActivity> callback) {
        createActivity(activity, UUID.randomUUID().toString(), evidence, callback);
    }

    @Override
    public void createActivity(PlannedActivity activity, String requestId,
                               @Nullable ComparisonEvidence evidence, Callback<PlannedActivity> callback) {
        String stableRequestId = requestId == null || requestId.trim().isEmpty()
                ? UUID.randomUUID().toString() : requestId;
        publish(activity, evidence, true, stableRequestId, callback);
    }

    private void publish(PlannedActivity activity, @Nullable ComparisonEvidence evidence,
                         boolean retryWithoutEvidence, String requestId,
                         Callback<PlannedActivity> callback) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("title", activity.getTitle());
        fields.put("category", activity.getCategory());
        fields.put("date", activity.getDate());
        fields.put("time", activity.getTime());
        fields.put("location", activity.getLocation());
        fields.put("status", activity.getStatus());

        Map<String, Object> input = new HashMap<>();
        input.put("requestId", requestId);
        input.put("groupId", activity.getGroupId());
        input.put("fields", fields);
        if (activity.getStartTime() != null && activity.getEndTime() != null) {
            input.put("startTime", activity.getStartTime());
            input.put("endTime", activity.getEndTime());
            input.put("timeZone", "America/Bogota");
        }
        boolean withEvidence = putAttribution(input, activity, evidence);
        functions.getHttpsCallable("publishActivity").call(input)
                .addOnSuccessListener(result -> {
                    Object id = map(result.getData()).get("activityId");
                    if (id == null) {
                        MainThread.post(() -> callback.onError(new IllegalStateException(
                                "Backend response did not contain activityId")));
                    } else {
                        MainThread.post(() -> callback.onSuccess(activity.withId(id.toString())));
                    }
                })
                .addOnFailureListener(error -> {
                    // bq9: the comparison expires after a few minutes; a late plan is still created, only without it
                    if (withEvidence && retryWithoutEvidence && error instanceof FirebaseFunctionsException
                            && ((FirebaseFunctionsException) error).getCode() == FirebaseFunctionsException.Code.FAILED_PRECONDITION) {
                        publish(activity, null, false, requestId, callback);
                        return;
                    }
                    MainThread.post(() -> callback.onError(error));
                });
    }

    /* BQ9: the same publishActivity fields as the Flutter app. With a comparison: its participants and evidence id,
     * direct(): measured as made without comparing, null: left unknown. Returns true when evidence was sent. */
    static boolean putAttribution(Map<String, Object> input, PlannedActivity activity, @Nullable ComparisonEvidence evidence) {
        if (evidence == null) {
            return false;
        }
        input.put("measurementVersion", 1);
        if (!evidence.appliesTo(activity.getDate())) {
            return false;
        }
        input.put("participantIds", new ArrayList<>(evidence.getParticipantIds()));
        input.put("comparisonEvidenceId", evidence.getId());
        return true;
    }

    @Override
    public void getActivityDetails(String activityId, Callback<ActivityDetails> callback) {
        Map<String, Object> input = new HashMap<>();
        input.put("activityId", activityId);
        functions.getHttpsCallable("getActivity").call(input)
                .addOnSuccessListener(result -> {
                    Map<?, ?> data = map(result.getData());
                    Object rawReminder = data.get("reminderMinutesBefore");
                    Integer reminder = rawReminder instanceof Number ? ((Number) rawReminder).intValue() : null;
                    Object rawDuration = data.get("durationMinutes");
                    Integer duration = rawDuration instanceof Number ? ((Number) rawDuration).intValue() : null;
                    int totalParticipants = 0;
                    int acceptedParticipants = 0;
                    Object rawParticipants = data.get("participants");
                    if (rawParticipants instanceof List) {
                        for (Object rawParticipant : (List<?>) rawParticipants) {
                            if (!(rawParticipant instanceof Map)) continue;
                            totalParticipants++;
                            Object response = ((Map<?, ?>) rawParticipant).get("activityRsvpStatus");
                            if ("accepted".equals(response)) acceptedParticipants++;
                        }
                    }
                    ActivityDetails details = new ActivityDetails(string(data.get("id")),
                            string(data.get("groupId")), string(data.get("title")),
                            string(data.get("category")), string(data.get("date")),
                            string(data.get("time")), string(data.get("location")),
                             string(data.get("status")), string(data.get("startTime")),
                             string(data.get("endTime")), reminder, string(data.get("createdBy")),
                            duration, Boolean.TRUE.equals(data.get("cancelled")),
                            totalParticipants, acceptedParticipants);
                    MainThread.post(() -> callback.onSuccess(details));
                })
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
    }

    @Override
    public void setActivityReminder(String activityId, int minutesBefore, Callback<Void> callback) {
        Map<String, Object> input = new HashMap<>();
        input.put("activityId", activityId);
        input.put("minutesBefore", minutesBefore);
        functions.getHttpsCallable("setActivityReminder").call(input)
                .addOnSuccessListener(result -> MainThread.post(() -> callback.onSuccess(null)))
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map ? (Map<?, ?>) value : Collections.emptyMap();
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }
}
