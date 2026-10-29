package com.example.parchapp.data;

import com.example.parchapp.domain.model.AlertItem;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads alerts and sends invitation/read actions through authenticated callables. */
final class FirebaseAlertsRepository implements AlertsRepository {
    private final FirebaseFunctions functions;

    FirebaseAlertsRepository(FirebaseFunctions functions) {
        this.functions = functions;
    }

    @Override
    public void getAlerts(Callback<List<AlertItem>> callback) {
        functions.getHttpsCallable("getAlerts").call(Collections.emptyMap())
                .addOnSuccessListener(result -> {
                    Object rawAlerts = map(result.getData()).get("alerts");
                    List<AlertItem> alerts = new ArrayList<>();
                    if (rawAlerts instanceof List) {
                        for (Object raw : (List<?>) rawAlerts) {
                            Map<?, ?> data = map(raw);
                            alerts.add(new AlertItem(string(data.get("id")), string(data.get("kind")),
                                    string(data.get("title")), string(data.get("description")),
                                    string(data.get("createdAt")), Boolean.TRUE.equals(data.get("isRead")),
                                    nullableString(data.get("response")), string(data.get("studySessionId")),
                                    string(data.get("groupId")), integer(data.get("groupSize"))));
                        }
                    }
                    MainThread.post(() -> callback.onSuccess(alerts));
                })
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
    }

    @Override
    public void markAlertsRead(List<String> ids, Callback<Void> callback) {
        if (ids.isEmpty()) {
            MainThread.post(() -> callback.onSuccess(null));
            return;
        }
        Map<String, Object> input = new HashMap<>();
        input.put("ids", new ArrayList<>(ids));
        functions.getHttpsCallable("markAlertsRead").call(input)
                .addOnSuccessListener(result -> MainThread.post(() -> callback.onSuccess(null)))
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
    }

    @Override
    public void respondToActivityInvitation(String activityId, String response, Callback<Void> callback) {
        Map<String, Object> input = new HashMap<>();
        input.put("id", activityId);
        input.put("response", response);
        functions.getHttpsCallable("respondToActivityInvitation").call(input)
                .addOnSuccessListener(result -> MainThread.post(() -> callback.onSuccess(null)))
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
    }

    @Override
    public void respondToGroupInvitation(String groupId, String response, Callback<Void> callback) {
        Map<String, Object> input = new HashMap<>();
        input.put("groupId", groupId);
        input.put("response", response);
        functions.getHttpsCallable("respondGroupInvitation").call(input)
                .addOnSuccessListener(result -> MainThread.post(() -> callback.onSuccess(null)))
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map ? (Map<?, ?>) value : Collections.emptyMap();
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String nullableString(Object value) {
        return value == null ? null : value.toString();
    }

    private static int integer(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }
}
