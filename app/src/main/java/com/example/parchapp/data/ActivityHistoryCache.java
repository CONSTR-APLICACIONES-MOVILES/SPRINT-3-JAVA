package com.example.parchapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.parchapp.domain.model.PlannedActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ActivityHistoryCache {
    private static final String TAG = "ActivityHistoryCache";
    private static final String PREFERENCES = "activity_history";
    private static final String KEY_PREFIX = "snapshot_";

    private ActivityHistoryCache() {
    }

    public static void save(Context context, String userId, List<PlannedActivity> activities,
                            long savedAt) {
        try {
            JSONArray rows = new JSONArray();
            for (PlannedActivity activity : activities) {
                JSONObject row = new JSONObject();
                row.put("id", activity.getId());
                row.put("groupId", activity.getGroupId());
                row.put("title", activity.getTitle());
                row.put("category", activity.getCategory());
                row.put("date", activity.getDate());
                row.put("time", activity.getTime());
                row.put("location", activity.getLocation());
                row.put("status", activity.getStatus());
                row.put("createdAt", activity.getCreatedAt());
                row.put("groupSizeAtCreation", activity.getGroupSizeAtCreation());
                row.put("comparisonUsed", activity.isComparisonUsed());
                row.put("startTime", activity.getStartTime());
                row.put("endTime", activity.getEndTime());
                row.put("locationAreaId", activity.getLocationAreaId());
                row.put("createdBy", activity.getCreatedBy());
                if (activity.getReminderMinutesBefore() != null) {
                    row.put("reminderMinutesBefore", activity.getReminderMinutesBefore());
                }
                row.put("cancelled", activity.isCancelled());
                row.put("participantCount", activity.getParticipantCount());
                if (activity.getLatitude() != null) row.put("latitude", activity.getLatitude());
                if (activity.getLongitude() != null) row.put("longitude", activity.getLongitude());
                rows.put(row);
            }
            JSONObject snapshot = new JSONObject();
            snapshot.put("savedAt", savedAt);
            snapshot.put("activities", rows);
            preferences(context).edit().putString(key(userId), snapshot.toString()).apply();
        } catch (JSONException error) {
            Log.e(TAG, "Could not serialize the activity history snapshot", error);
        }
    }

    @Nullable
    public static Snapshot read(Context context, String userId) {
        String json = preferences(context).getString(key(userId), null);
        if (json == null) return null;
        try {
            JSONObject snapshot = new JSONObject(json);
            JSONArray rows = snapshot.optJSONArray("activities");
            if (rows == null) return null;
            List<PlannedActivity> activities = new ArrayList<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                Double latitude = number(row, "latitude");
                Double longitude = number(row, "longitude");
                Integer reminder = row.isNull("reminderMinutesBefore")
                        ? null : row.optInt("reminderMinutesBefore");
                activities.add(new PlannedActivity(
                        text(row, "id"), text(row, "groupId"), text(row, "title"),
                        text(row, "category"), text(row, "date"), text(row, "time"),
                        text(row, "location"), text(row, "status"), row.optLong("createdAt"),
                        row.optInt("groupSizeAtCreation"), row.optBoolean("comparisonUsed"),
                        text(row, "startTime"), text(row, "endTime"),
                        text(row, "locationAreaId"), text(row, "createdBy"), reminder,
                        row.optBoolean("cancelled"), row.optInt("participantCount"),
                        latitude, longitude));
            }
            return new Snapshot(snapshot.optLong("savedAt"), activities);
        } catch (JSONException | RuntimeException error) {
            Log.w(TAG, "Ignoring an invalid activity history snapshot", error);
            return null;
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static String key(String userId) {
        return KEY_PREFIX + userId;
    }

    private static String text(JSONObject row, String name) {
        return row.isNull(name) ? "" : row.optString(name, "");
    }

    @Nullable
    private static Double number(JSONObject row, String name) {
        return row.has(name) && !row.isNull(name) ? row.optDouble(name) : null;
    }

    public static final class Snapshot {
        public final long savedAt;
        public final List<PlannedActivity> activities;

        private Snapshot(long savedAt, List<PlannedActivity> activities) {
            this.savedAt = savedAt;
            this.activities = Collections.unmodifiableList(new ArrayList<>(activities));
        }
    }
}
