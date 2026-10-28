package com.example.parchapp.analytics;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/* Local queue of events waiting to be sent. It is saved on the device, so events logged without signal (for example inside TransMilenio) are not lost if the app is closed before sending them (we have borradores in spanish slang).
 */
class EventQueue {
    static final int MAX_EVENTS = 500;

    private static final String TAG = "EventQueue";
    private static final String PREFS = "parchapp_analytics";
    private static final String KEY_EVENTS = "pending_events";

    static class Event {
        final String name;
        final JSONObject params;

        Event(String name, JSONObject params) {
            this.name = name;
            this.params = params;
        }

        Bundle toBundle() {
            Bundle bundle = new Bundle();
            Iterator<String> keys = params.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = params.opt(key);
                if (value instanceof Integer || value instanceof Long) {
                    bundle.putLong(key, ((Number) value).longValue());
                } else if (value instanceof Number) {
                    bundle.putDouble(key, ((Number) value).doubleValue());
                } else if (value != null) {
                    bundle.putString(key, value.toString());
                }
            }
            return bundle;
        }
    }

    private final List<Event> events = new ArrayList<>();
    private SharedPreferences prefs;

    /* Loads events left from a previous session. Events tracked before this call are kept. */
    synchronized void attach(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<Event> stored = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_EVENTS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                stored.add(new Event(item.getString("n"), item.getJSONObject("p")));
            }
        } catch (JSONException e) {
            Log.w(TAG, "Discarding unreadable analytics queue", e);
        }
        events.addAll(0, stored);
        trimAndPersist();
    }

    synchronized void add(String name, Map<String, Object> params) {
        JSONObject json = new JSONObject();
        try {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Boolean) {
                    value = ((Boolean) value) ? 1L : 0L;
                }
                json.put(entry.getKey(), value);
            }
        } catch (JSONException e) {
            Log.w(TAG, "Dropping invalid parameter for " + name, e);
        }
        events.add(new Event(name, json));
        trimAndPersist();
    }

    synchronized List<Event> drain() {
        List<Event> drained = new ArrayList<>(events);
        events.clear();
        trimAndPersist();
        return drained;
    }

    synchronized int size() {
        return events.size();
    }

    private void trimAndPersist() {
        while (events.size() > MAX_EVENTS) {
            events.remove(0);
        }
        if (prefs == null) {
            return;
        }
        JSONArray array = new JSONArray();
        for (Event event : events) {
            JSONObject item = new JSONObject();
            try {
                item.put("n", event.name);
                item.put("p", event.params);
            } catch (JSONException e) {
                continue;
            }
            array.put(item);
        }
        prefs.edit().putString(KEY_EVENTS, array.toString()).apply();
    }
}
