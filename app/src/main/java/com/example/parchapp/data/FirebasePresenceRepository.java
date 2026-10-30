package com.example.parchapp.data;

import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Firebase callable adapter that preserves the user's existing status privacy scope. */
final class FirebasePresenceRepository implements PresenceRepository {
    private final FirebaseFunctions functions;

    FirebasePresenceRepository(FirebaseFunctions functions) { this.functions = functions; }

    @Override
    public void updatePresence(String userId, String status, String visibility, String expiresAt,
                               Callback<Void> callback) {
        functions.getHttpsCallable("getProfile").call(Collections.emptyMap())
                .addOnSuccessListener(profileResult -> {
                    Map<?, ?> profile = map(profileResult.getData());
                    Map<?, ?> preferences = map(profile.get("preferences"));
                    String scope = presenceVisibility(preferences.get("statusVisibility"), visibility);
                    Map<String, Object> input = new HashMap<>();
                    input.put("clientUserId", userId);
                    input.put("status", status);
                    input.put("visibility", scope);
                    input.put("expiresAt", expiresAt);
                    functions.getHttpsCallable("updatePresence").call(input)
                            .addOnSuccessListener(result -> MainThread.post(() -> callback.onSuccess(null)))
                            .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
                })
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
