package com.example.parchapp.data;

import com.example.parchapp.domain.model.ActivityDetails;
import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;
import com.google.firebase.functions.FirebaseFunctions;

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
        Map<String, Object> fields = new HashMap<>();
        fields.put("title", activity.getTitle());
        fields.put("category", activity.getCategory());
        fields.put("date", activity.getDate());
        fields.put("time", activity.getTime());
        fields.put("location", activity.getLocation());
        fields.put("status", activity.getStatus());

        Map<String, Object> input = new HashMap<>();
        input.put("requestId", UUID.randomUUID().toString());
        input.put("groupId", activity.getGroupId());
        input.put("fields", fields);
        if (activity.getStartTime() != null && activity.getEndTime() != null) {
            input.put("startTime", activity.getStartTime());
            input.put("endTime", activity.getEndTime());
            input.put("timeZone", "America/Bogota");
        }
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
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
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
