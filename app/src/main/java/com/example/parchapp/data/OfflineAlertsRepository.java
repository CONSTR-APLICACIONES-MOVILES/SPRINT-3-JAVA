package com.example.parchapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.parchapp.UserProfile;
import com.example.parchapp.UserSession;
import com.example.parchapp.domain.model.AlertItem;
import com.example.parchapp.util.Callback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/* Eventual connectivity for Alerts: network, falling back to a saved copy. Every alert list the backend answers
 * is saved on the phone (SharedPreferences, JSON, one copy per signed-in user, with the time it was saved).
 * Without network the saved list is shown instead of an empty screen. Answering an invitation or marking alerts
 * as read always needs the backend, so those calls go straight through (the screen checks the network first). */
public class OfflineAlertsRepository implements AlertsRepository {
    private static final String TAG = "OfflineAlerts";
    private static final String PREFS = "parchapp_alerts_offline";

    private final AlertsRepository remote;
    private final SharedPreferences prefs;
    private volatile boolean lastFromSaved;
    private volatile long lastSavedAt;

    OfflineAlertsRepository(Context context, AlertsRepository remote) {
        this.remote = remote;
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /* true when the last list came from the phone because the backend was not reachable */
    public boolean isShowingSaved() {
        return lastFromSaved;
    }

    /* when the saved list on screen was saved (System.currentTimeMillis), 0 when unknown */
    public long getShownSavedAt() {
        return lastSavedAt;
    }

    @Override
    public void getAlerts(Callback<List<AlertItem>> callback) {
        String key = key();
        remote.getAlerts(new Callback<List<AlertItem>>() {
            @Override
            public void onSuccess(List<AlertItem> alerts) {
                lastFromSaved = false;
                String json = toJson(alerts);
                if (json != null) {
                    prefs.edit().putString(key, json).putLong(key + ".savedAt", System.currentTimeMillis()).apply();
                }
                callback.onSuccess(alerts);
            }

            @Override
            public void onError(Exception error) {
                String saved = prefs.getString(key, null);
                List<AlertItem> alerts = saved == null ? null : fromJson(saved);
                if (alerts == null) {
                    callback.onError(error);
                    return;
                }
                lastFromSaved = true;
                lastSavedAt = prefs.getLong(key + ".savedAt", 0L);
                callback.onSuccess(alerts);
            }
        });
    }

    @Override
    public void markAlertsRead(List<String> ids, Callback<Void> callback) {
        remote.markAlertsRead(ids, callback);
    }

    @Override
    public void respondToActivityInvitation(String activityId, String response, Callback<Void> callback) {
        remote.respondToActivityInvitation(activityId, response, callback);
    }

    @Override
    public void respondToGroupInvitation(String groupId, String response, Callback<Void> callback) {
        remote.respondToGroupInvitation(groupId, response, callback);
    }

    // one copy per signed-in user, so another account on the phone never sees it
    private static String key() {
        UserProfile user = UserSession.getCurrentUser();
        return (user == null ? "anonymous" : user.getId()) + ".alerts";
    }

    @Nullable
    static String toJson(List<AlertItem> alerts) {
        try {
            JSONArray array = new JSONArray();
            for (AlertItem alert : alerts) {
                array.put(new JSONObject()
                        .put("id", alert.getId()).put("kind", alert.getKind())
                        .put("title", alert.getTitle()).put("description", alert.getDescription())
                        .put("createdAt", alert.getCreatedAt()).put("read", alert.isRead())
                        .put("response", alert.getResponse()).put("activityId", alert.getActivityId())
                        .put("groupId", alert.getGroupId()).put("groupSize", alert.getGroupSize())
                        .put("reminderId", alert.getReminderId()).put("reminderPurpose", alert.getReminderPurpose())
                        .put("reminderStrategy", alert.getReminderStrategy())
                        .put("reminderSequence", alert.getReminderSequence())
                        .put("reminderDelayMinutes", alert.getReminderDelayMinutes())
                        .put("reminderSentAt", alert.getReminderSentAt()));
            }
            return array.toString();
        } catch (JSONException error) {
            Log.w(TAG, "Could not save the alerts", error);
            return null;
        }
    }

    @Nullable
    static List<AlertItem> fromJson(String json) {
        try {
            JSONArray array = new JSONArray(json);
            List<AlertItem> alerts = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject raw = array.getJSONObject(i);
                alerts.add(new AlertItem(text(raw, "id"), text(raw, "kind"), text(raw, "title"),
                        text(raw, "description"), text(raw, "createdAt"), raw.optBoolean("read"),
                        text(raw, "response"), text(raw, "activityId"), text(raw, "groupId"),
                        raw.optInt("groupSize"), text(raw, "reminderId"), text(raw, "reminderPurpose"),
                        text(raw, "reminderStrategy"), raw.optInt("reminderSequence"),
                        raw.isNull("reminderDelayMinutes") ? null : raw.optInt("reminderDelayMinutes"),
                        text(raw, "reminderSentAt")));
            }
            return alerts;
        } catch (JSONException error) {
            Log.w(TAG, "Could not read the saved alerts", error);
            return null;
        }
    }

    // JSONObject turns a missing value into "null" text; the screen expects a real null
    @Nullable
    private static String text(JSONObject raw, String name) {
        return raw.isNull(name) ? null : raw.optString(name, null);
    }
}
