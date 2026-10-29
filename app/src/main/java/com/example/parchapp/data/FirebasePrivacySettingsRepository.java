package com.example.parchapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stores analytics consent locally and updates schedule visibility with the authenticated callable. */
final class FirebasePrivacySettingsRepository implements PrivacySettingsRepository {
    private static final String PREFS = "parchapp_privacy";
    private static final String KEY_ANALYTICS_CONSENT = "analytics_consent";

    private final SharedPreferences localPreferences;
    private final FirebaseFunctions functions;

    FirebasePrivacySettingsRepository(Context context, FirebaseFunctions functions) {
        localPreferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.functions = functions;
    }

    @Override
    public boolean isAnalyticsConsentAccepted() {
        return localPreferences.getBoolean(KEY_ANALYTICS_CONSENT, false);
    }

    @Override
    public void saveAnalyticsConsent(boolean accepted, Callback<Void> callback) {
        localPreferences.edit().putBoolean(KEY_ANALYTICS_CONSENT, accepted).apply();
        MainThread.post(() -> callback.onSuccess(null));
    }

    @Override
    public void getAvailabilityHiddenFromGroups(Callback<Boolean> callback) {
        loadPreferences(new Callback<Map<String, Object>>() {
            @Override public void onSuccess(Map<String, Object> preferences) {
                callback.onSuccess(!"groups".equals(preferences.get("scheduleVisibility")));
            }

            @Override public void onError(Exception error) { callback.onError(error); }
        });
    }

    @Override
    public void setAvailabilityHiddenFromGroups(boolean hidden, Callback<Void> callback) {
        loadPreferences(new Callback<Map<String, Object>>() {
            @Override public void onSuccess(Map<String, Object> preferences) {
                preferences.put("scheduleVisibility", hidden ? "private" : "groups");
                Map<String, Object> input = new HashMap<>();
                input.put("preferences", preferences);
                functions.getHttpsCallable("updatePreferences").call(input)
                        .addOnSuccessListener(result -> MainThread.post(() -> callback.onSuccess(null)))
                        .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
            }

            @Override public void onError(Exception error) { callback.onError(error); }
        });
    }

    private void loadPreferences(Callback<Map<String, Object>> callback) {
        functions.getHttpsCallable("getProfile").call(Collections.emptyMap())
                .addOnSuccessListener(result -> {
                    Map<?, ?> profile = map(result.getData());
                    Map<?, ?> saved = map(profile.get("preferences"));
                    Map<String, Object> preferences = new HashMap<>();
                    preferences.put("statusVisibility", valueOr(saved.get("statusVisibility"), "groups"));
                    preferences.put("scheduleVisibility", valueOr(saved.get("scheduleVisibility"), "groups"));
                    preferences.put("invitationBadges", saved.get("invitationBadges") instanceof Boolean
                            ? saved.get("invitationBadges") : true);
                    preferences.put("closeFriendIds", stringList(saved.get("closeFriendIds")));
                    MainThread.post(() -> callback.onSuccess(preferences));
                })
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map ? (Map<?, ?>) value : Collections.emptyMap();
    }

    private static Object valueOr(Object value, String fallback) {
        return value == null ? fallback : value;
    }

    private static List<String> stringList(@Nullable Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item != null) result.add(item.toString());
            }
        }
        return result;
    }
}
