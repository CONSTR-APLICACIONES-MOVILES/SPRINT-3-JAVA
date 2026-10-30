package com.example.parchapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.parchapp.domain.model.ProfileDetails;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;
import com.google.firebase.functions.FirebaseFunctions;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Profile adapter backed by getProfile/updateProfile/updatePresence. */
final class FirebaseProfileRepository implements ProfileRepository {
    private static final String PREFS = "parchapp_profile_snapshots";
    private final SharedPreferences cache;
    private final FirebaseFunctions functions;

    FirebaseProfileRepository(Context context, FirebaseFunctions functions) {
        cache = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.functions = functions;
    }

    @Override
    public void load(String userId, Callback<ProfileDetails> callback) {
        functions.getHttpsCallable("getProfile").call(Collections.emptyMap())
                .addOnSuccessListener(result -> {
                    Map<?, ?> data = map(result.getData());
                    ProfileDetails details = ProfileDetails.fromMap(data, System.currentTimeMillis());
                    saveSnapshot(userId, details);
                    MainThread.post(() -> callback.onSuccess(details));
                })
                .addOnFailureListener(error -> {
                    ProfileDetails saved = readSnapshot(userId);
                    MainThread.post(() -> {
                        if (saved != null) callback.onSuccess(saved);
                        else callback.onError(error);
                    });
                });
    }

    @Override
    public void updateProfile(ProfileDetails profile, Callback<ProfileDetails> callback) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", profile.getName().trim());
        fields.put("program", profile.getProgram().trim());
        fields.put("semester", profile.getSemester());
        fields.put("year", profile.getYear());
        if (!profile.getAcademicLevel().isEmpty()) fields.put("academicLevel", profile.getAcademicLevel());
        fields.put("campus", profile.getCampus().trim());
        fields.put("university", profile.getUniversity().trim());
        fields.put("studentId", profile.getStudentId().trim());
        Map<String, Object> input = new HashMap<>();
        input.put("clientUserId", profile.getUserId());
        input.put("fields", fields);
        functions.getHttpsCallable("updateProfile").call(input)
                .addOnSuccessListener(result -> deliverProfile(profile.getUserId(), result.getData(), callback))
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
    }

    @Override
    public void updatePresence(String userId, String status, String expiresAt, String visibility,
                               Callback<ProfileDetails> callback) {
        Map<String, Object> input = new HashMap<>();
        input.put("clientUserId", userId);
        input.put("status", status);
        input.put("visibility", visibility);
        input.put("expiresAt", expiresAt);
        functions.getHttpsCallable("updatePresence").call(input)
                .addOnSuccessListener(result -> deliverProfile(userId, result.getData(), callback))
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
    }

    private void deliverProfile(String userId, Object raw, Callback<ProfileDetails> callback) {
        ProfileDetails details = ProfileDetails.fromMap(map(raw), System.currentTimeMillis());
        saveSnapshot(userId, details);
        MainThread.post(() -> callback.onSuccess(details));
    }

    private void saveSnapshot(String userId, ProfileDetails details) {
        if (userId == null || userId.trim().isEmpty()) return;
        JSONObject json = new JSONObject();
        try {
            json.put("userId", details.getUserId());
            json.put("name", details.getName());
            json.put("username", details.getUsername());
            json.put("program", details.getProgram());
            json.put("verified", details.isVerified());
            json.put("semester", details.getSemester());
            json.put("year", details.getYear());
            json.put("academicLevel", details.getAcademicLevel());
            json.put("campus", details.getCampus());
            json.put("university", details.getUniversity());
            json.put("studentId", details.getStudentId());
            json.put("status", details.getStatus());
            json.put("statusExpiresAt", details.getStatusExpiresAt());
            json.put("statusVisibility", details.getStatusVisibility());
            json.put("plansCompleted", details.getPlansCompleted());
            json.put("activeGroups", details.getActiveGroups());
            json.put("onTimeRate", details.getOnTimeRate());
            json.put("savedAt", System.currentTimeMillis());
            cache.edit().putString(key(userId), json.toString()).apply();
        } catch (JSONException ignored) {
            // A profile cache failure never turns a successful server update into a failed one.
        }
    }

    private ProfileDetails readSnapshot(String userId) {
        if (userId == null || userId.trim().isEmpty()) return null;
        String serialized = cache.getString(key(userId), null);
        if (serialized == null) return null;
        try {
            JSONObject json = new JSONObject(serialized);
            Map<String, Object> data = new HashMap<>();
            data.put("id", userId);
            data.put("name", json.optString("name", ""));
            data.put("username", json.optString("username", ""));
            data.put("program", json.optString("program", ""));
            data.put("verified", json.optBoolean("verified"));
            data.put("semester", json.optInt("semester"));
            data.put("year", json.optInt("year"));
            data.put("academicLevel", json.optString("academicLevel", ""));
            data.put("campus", json.optString("campus", ""));
            data.put("university", json.optString("university", ""));
            data.put("studentId", json.optString("studentId", ""));
            data.put("status", json.optString("status", "offline"));
            data.put("statusExpiresAt", json.optString("statusExpiresAt", ""));
            data.put("plansCompleted", json.optInt("plansCompleted"));
            data.put("activeGroups", json.optInt("activeGroups"));
            data.put("onTimeRate", json.isNull("onTimeRate") ? null : json.optInt("onTimeRate"));
            Map<String, Object> preferences = new HashMap<>();
            preferences.put("statusVisibility", json.optString("statusVisibility", "groups"));
            data.put("preferences", preferences);
            return ProfileDetails.fromMap(data, json.optLong("savedAt")).asCached(json.optLong("savedAt"));
        } catch (JSONException error) {
            return null;
        }
    }

    private String key(String userId) { return "profile." + userId; }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map ? (Map<?, ?>) value : Collections.emptyMap();
    }
}
