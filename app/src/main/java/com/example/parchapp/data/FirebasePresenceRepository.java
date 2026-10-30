package com.example.parchapp.data;

import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Firebase callable adapter for the shareable availability status. */
final class FirebasePresenceRepository implements PresenceRepository {
    private final FirebaseFunctions functions;

    FirebasePresenceRepository(FirebaseFunctions functions) {
        this.functions = functions;
    }

    @Override
    public void updatePresence(String userId, String status, String visibility, String expiresAt,
                               Callback<Void> callback) {
        // Preserve the user's existing privacy scope instead of silently widening status sharing.
        functions.getHttpsCallable("getProfile").call(Collections.emptyMap())
                .addOnSuccessListener(profileResult -> {
                    Map<?, ?> profile = map(profileResult.getData());
                    Map<?, ?> preferences = map(profile.get("preferences"));
                    String scope = presenceVisibility(preferences.get("statusVisibility"), visibility);
                    publishPresence(userId, status, scope, expiresAt, callback);
                })
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
    }

    private void publishPresence(String userId, String status, String visibility, String expiresAt,
                                 Callback<Void> callback) {
        Map<String, Object> input = new HashMap<>();
        input.put("clientUserId", userId);
        input.put("status", status);
        input.put("visibility", visibility);
        input.put("expiresAt", expiresAt);
        functions.getHttpsCallable("updatePresence").call(input)
                .addOnSuccessListener(result -> MainThread.post(() -> callback.onSuccess(null)))
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map ? (Map<?, ?>) value : Collections.emptyMap();
    }

    static String presenceVisibility(Object savedScope, String fallback) {
        return savedScope instanceof String
                && ("groups".equals(savedScope) || "closeFriends".equals(savedScope)
                || "private".equals(savedScope)) ? (String) savedScope : fallback;
    }
}
