package com.example.parchapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.parchapp.UserProfile;
import com.example.parchapp.UserSession;
import com.example.parchapp.domain.model.DaySchedule;
import com.example.parchapp.domain.model.ScheduleFriend;
import com.example.parchapp.domain.model.TimeBlock;
import com.example.parchapp.util.Callback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/* Eventual connectivity for Schedule, the same idea as the Flutter OfflineMobileApi: every answer of getScheduleFriends and getScheduleDay is saved per user, and when the network fails the saved answer is shown instead of an error. Writes (validate, import) always need the backend. Only free/busy minutes are saved, never event details.
 * A day is read in three layers: memory (ScheduleDayCache, caching #46), then the network, then the copy on the
 * phone (SharedPreferences, local storage). The copy keeps at most MAX_SAVED_DAYS per user, the oldest go first. */
public class OfflineScheduleRepository implements ScheduleRepository {
    private static final String TAG = "OfflineSchedule";
    private static final String PREFS = "parchapp_schedule_offline";
    private static final String PERF = "ParchPerf";
    private static final String SAVED_AT = ".savedAt";
    // local storage: about a month of days per user; SharedPreferences is read whole, so it must not grow forever
    static final int MAX_SAVED_DAYS = 30;

    private final ScheduleRepository remote;
    private final SharedPreferences prefs;
    private volatile boolean lastFromSaved;
    private volatile long lastSavedAt;
    private final ScheduleDayCache<List<DaySchedule>> memory = new ScheduleDayCache<>(SystemClock::elapsedRealtime);

    OfflineScheduleRepository(Context context, ScheduleRepository remote) {
        this.remote = remote;
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /* true when the last friends or day answer came from the phone because the backend was not reachable */
    public boolean isShowingSaved() {
        return lastFromSaved;
    }

    /* when the copy on screen was saved (System.currentTimeMillis), 0 when unknown */
    public long getShownSavedAt() {
        return lastSavedAt;
    }

    @Override
    public void getFriends(Callback<List<ScheduleFriend>> callback) {
        String key = key("friends");
        remote.getFriends(new Callback<List<ScheduleFriend>>() {
            @Override
            public void onSuccess(List<ScheduleFriend> friends) {
                lastFromSaved = false;
                save(key, friendsToJson(friends));
                callback.onSuccess(friends);
            }

            @Override
            public void onError(Exception error) {
                String saved = prefs.getString(key, null);
                if (saved == null) {
                    callback.onError(error);
                    return;
                }
                lastFromSaved = true;
                callback.onSuccess(friendsFromJson(saved));
            }
        });
    }

    @Override
    public void getDay(String date, List<String> friendIds, Callback<List<DaySchedule>> callback) {
        List<String> sorted = new ArrayList<>(friendIds);
        java.util.Collections.sort(sorted);
        String key = key("day." + date + "." + String.join(",", sorted));
        long started = SystemClock.elapsedRealtime();
        // 1. memory: a day seen in the last minutes answers at once, without calling the backend
        List<DaySchedule> cached = memory.get(key);
        if (cached != null) {
            lastFromSaved = false;
            Log.d(PERF, String.format(Locale.US, "Schedule %s from memory in %d ms", date, SystemClock.elapsedRealtime() - started));
            callback.onSuccess(cached);
            return;
        }
        // 2. network
        remote.getDay(date, friendIds, new Callback<List<DaySchedule>>() {
            @Override
            public void onSuccess(List<DaySchedule> schedules) {
                lastFromSaved = false;
                memory.put(key, schedules);
                saveDay(key, daysToJson(schedules));
                Log.d(PERF, String.format(Locale.US, "Schedule %s from network in %d ms", date, SystemClock.elapsedRealtime() - started));
                callback.onSuccess(schedules);
            }

            // 3. phone: the saved copy instead of an error; it is not put in memory, so the network is tried again next time
            @Override
            public void onError(Exception error) {
                String saved = prefs.getString(key, null);
                if (saved == null) {
                    callback.onError(error);
                    return;
                }
                lastFromSaved = true;
                lastSavedAt = prefs.getLong(key + SAVED_AT, 0L);
                callback.onSuccess(daysFromJson(saved));
            }
        });
    }

    @Override
    public void validatePlanningSlot(List<String> participantIds, String startTime, String endTime,
                                     Callback<String> callback) {
        remote.validatePlanningSlot(participantIds, startTime, endTime, callback);
    }

    @Override
    public void prepareProposalComparison(String date, List<String> friendIds, Callback<String> callback) {
        remote.prepareProposalComparison(date, friendIds, callback);
    }

    @Override
    public void importCalendarAvailability(String startDate, int days, List<String[]> intervals,
                                           Callback<Integer> callback) {
        remote.importCalendarAvailability(startDate, days, intervals, new Callback<Integer>() {
            @Override
            public void onSuccess(Integer imported) {
                // my busy blocks just changed, the days in memory are old
                memory.clear();
                callback.onSuccess(imported);
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error);
            }
        });
    }

    // one saved copy per signed-in user, so another account on the phone never sees it
    private String key(String request) {
        UserProfile user = UserSession.getCurrentUser();
        return (user == null ? "anonymous" : user.getId()) + "." + request;
    }

    private void save(String key, String json) {
        if (json != null) {
            prefs.edit().putString(key, json).apply();
        }
    }

    /* local storage: saves the day with the time it was saved, and forgets the oldest days of this user
     * when there are more than MAX_SAVED_DAYS (an index in save order lives next to them) */
    private void saveDay(String key, @Nullable String json) {
        if (json == null) {
            return;
        }
        String indexKey = key("days.index");
        List<String> index = readIndex(prefs.getString(indexKey, null));
        List<String> removed = trimIndex(index, key, MAX_SAVED_DAYS);
        SharedPreferences.Editor editor = prefs.edit()
                .putString(key, json)
                .putLong(key + SAVED_AT, System.currentTimeMillis())
                .putString(indexKey, new JSONArray(index).toString());
        for (String old : removed) {
            editor.remove(old).remove(old + SAVED_AT);
        }
        editor.apply();
    }

    /* moves key to the end (newest) and returns the oldest keys that no longer fit; index is changed in place */
    static List<String> trimIndex(List<String> index, String key, int max) {
        index.remove(key);
        index.add(key);
        List<String> removed = new ArrayList<>();
        while (index.size() > max) {
            removed.add(index.remove(0));
        }
        return removed;
    }

    private static List<String> readIndex(@Nullable String json) {
        List<String> index = new ArrayList<>();
        if (json == null) {
            return index;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                index.add(array.getString(i));
            }
        } catch (JSONException error) {
            Log.w(TAG, "Could not read the saved days index", error);
        }
        return index;
    }

    private static String friendsToJson(List<ScheduleFriend> friends) {
        try {
            JSONArray array = new JSONArray();
            for (ScheduleFriend friend : friends) {
                array.put(new JSONObject().put("id", friend.getId()).put("name", friend.getName())
                        .put("initials", friend.getInitials()));
            }
            return array.toString();
        } catch (JSONException error) {
            Log.w(TAG, "Could not save friends", error);
            return null;
        }
    }

    private static List<ScheduleFriend> friendsFromJson(String json) {
        List<ScheduleFriend> friends = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject raw = array.getJSONObject(i);
                friends.add(new ScheduleFriend(raw.getString("id"), raw.getString("name"), raw.getString("initials")));
            }
        } catch (JSONException error) {
            Log.w(TAG, "Saved friends are unreadable", error);
        }
        return friends;
    }

    private static String daysToJson(List<DaySchedule> schedules) {
        try {
            JSONArray array = new JSONArray();
            for (DaySchedule schedule : schedules) {
                JSONArray busy = new JSONArray();
                for (TimeBlock block : schedule.getBusy()) {
                    busy.put(new JSONArray().put(block.getStartMinute()).put(block.getEndMinute()));
                }
                array.put(new JSONObject().put("ownerId", schedule.getOwnerId())
                        .put("known", schedule.isKnown()).put("busy", busy));
            }
            return array.toString();
        } catch (JSONException error) {
            Log.w(TAG, "Could not save the day", error);
            return null;
        }
    }

    private static List<DaySchedule> daysFromJson(String json) {
        List<DaySchedule> schedules = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject raw = array.getJSONObject(i);
                List<TimeBlock> busy = new ArrayList<>();
                JSONArray blocks = raw.getJSONArray("busy");
                for (int j = 0; j < blocks.length(); j++) {
                    JSONArray block = blocks.getJSONArray(j);
                    busy.add(new TimeBlock(block.getInt(0), block.getInt(1)));
                }
                schedules.add(new DaySchedule(raw.getString("ownerId"), raw.getBoolean("known"), busy));
            }
        } catch (JSONException error) {
            Log.w(TAG, "Saved day is unreadable", error);
        }
        return schedules;
    }
}
