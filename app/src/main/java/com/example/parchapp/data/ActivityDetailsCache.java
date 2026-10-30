package com.example.parchapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.parchapp.domain.model.ActivityDetails;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Persistent, user-scoped cache of the small summary shown by Activity Created. */
public final class ActivityDetailsCache {
    static final long TTL_MS = 24 * 60 * 60 * 1000L;
    static final int MAX_ENTRIES = 50;
    private static final String PREFS = "parchapp_activity_details_cache";
    private static final String KEY_ENTRIES = "entries";

    interface Store {
        String read();
        void write(String serialized);
    }

    interface Clock {
        long now();
    }

    public static final class Snapshot {
        private final ActivityDetails details;
        private final long savedAtMillis;

        Snapshot(ActivityDetails details, long savedAtMillis) {
            this.details = details;
            this.savedAtMillis = savedAtMillis;
        }

        public ActivityDetails getDetails() { return details; }
        public long getSavedAtMillis() { return savedAtMillis; }
    }

    private static final class Entry {
        final String userId;
        final ActivityDetails details;
        final long savedAtMillis;
        long lastAccessMillis;

        Entry(String userId, ActivityDetails details, long savedAtMillis, long lastAccessMillis) {
            this.userId = userId;
            this.details = details;
            this.savedAtMillis = savedAtMillis;
            this.lastAccessMillis = lastAccessMillis;
        }
    }

    private final Store store;
    private final Clock clock;

    public ActivityDetailsCache(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences preferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.store = new Store() {
            @Override public String read() { return preferences.getString(KEY_ENTRIES, "[]"); }
            @Override public void write(String serialized) {
                preferences.edit().putString(KEY_ENTRIES, serialized).apply();
            }
        };
        this.clock = System::currentTimeMillis;
    }

    ActivityDetailsCache(Store store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public synchronized void put(String userId, ActivityDetails details) {
        if (blank(userId) || details == null || blank(details.getId()) || blank(details.getTitle())) return;
        List<Entry> entries = liveEntries(readEntries());
        entries.removeIf(entry -> entry.userId.equals(userId) && entry.details.getId().equals(details.getId()));
        long now = clock.now();
        entries.add(new Entry(userId, summary(details), now, nextAccess(entries)));
        entries.sort(Comparator.comparingLong(entry -> entry.lastAccessMillis));
        while (entries.size() > MAX_ENTRIES) entries.remove(0);
        writeEntries(entries);
    }

    public synchronized Snapshot get(String userId, String activityId) {
        if (blank(userId) || blank(activityId)) return null;
        List<Entry> entries = liveEntries(readEntries());
        Entry found = null;
        for (Entry entry : entries) {
            if (entry.userId.equals(userId) && entry.details.getId().equals(activityId)) {
                found = entry;
                break;
            }
        }
        if (found == null) {
            writeEntries(entries);
            return null;
        }
        found.lastAccessMillis = nextAccess(entries);
        entries.sort(Comparator.comparingLong(entry -> entry.lastAccessMillis));
        writeEntries(entries);
        return new Snapshot(found.details, found.savedAtMillis);
    }

    public synchronized void clearUser(String userId) {
        if (blank(userId)) return;
        List<Entry> entries = readEntries();
        entries.removeIf(entry -> entry.userId.equals(userId));
        writeEntries(entries);
    }

    private List<Entry> liveEntries(List<Entry> entries) {
        long now = clock.now();
        entries.removeIf(entry -> {
            long age = now - entry.savedAtMillis;
            return age < 0 || age >= TTL_MS;
        });
        return entries;
    }

    private long nextAccess(List<Entry> entries) {
        long latest = clock.now();
        for (Entry entry : entries) latest = Math.max(latest, entry.lastAccessMillis + 1);
        return latest;
    }

    private List<Entry> readEntries() {
        List<Entry> entries = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(store.read());
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String userId = item.optString("userId", "");
                String id = item.optString("id", "");
                String title = item.optString("title", "");
                if (blank(userId) || blank(id) || blank(title)) continue;
                Integer duration = item.isNull("durationMinutes") ? null : item.optInt("durationMinutes");
                ActivityDetails details = new ActivityDetails(id,
                        item.optString("groupId", ""), title, item.optString("category", ""),
                        item.optString("date", ""), item.optString("time", ""),
                        item.optString("location", ""), item.optString("status", ""),
                        item.optString("startTime", ""), item.optString("endTime", ""),
                        null, "", duration, item.optBoolean("cancelled", false), 0, 0);
                entries.add(new Entry(userId, details, item.optLong("savedAt"), item.optLong("lastAccess")));
            }
        } catch (JSONException ignored) {
            return new ArrayList<>();
        }
        return entries;
    }

    private void writeEntries(List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            JSONObject item = new JSONObject();
            try {
                ActivityDetails details = entry.details;
                item.put("userId", entry.userId);
                item.put("id", details.getId());
                item.put("groupId", details.getGroupId());
                item.put("title", details.getTitle());
                item.put("category", details.getCategory());
                item.put("date", details.getDate());
                item.put("time", details.getTime());
                item.put("location", details.getLocation());
                item.put("status", details.getStatus());
                item.put("startTime", details.getStartTime());
                item.put("endTime", details.getEndTime());
                item.put("durationMinutes", details.getDurationMinutes());
                item.put("cancelled", details.isCancelled());
                item.put("savedAt", entry.savedAtMillis);
                item.put("lastAccess", entry.lastAccessMillis);
                array.put(item);
            } catch (JSONException ignored) {
                // Skip only the malformed row; the rest of the cache remains useful.
            }
        }
        store.write(array.toString());
    }

    private static ActivityDetails summary(ActivityDetails details) {
        return new ActivityDetails(details.getId(), details.getGroupId(), details.getTitle(),
                details.getCategory(), details.getDate(), details.getTime(), details.getLocation(),
                details.getStatus(), details.getStartTime(), details.getEndTime(), null, "",
                details.getDurationMinutes(), details.isCancelled(), 0, 0);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
