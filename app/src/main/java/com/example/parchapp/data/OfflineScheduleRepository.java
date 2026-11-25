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
import com.example.parchapp.util.BackgroundThreads;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/* Eventual connectivity for Schedule, the same idea as the Flutter OfflineMobileApi: every answer of getScheduleFriends and getScheduleDay is saved per user, and when the network fails the saved answer is shown instead of an error. Writes (validate, import) always need the backend. Only free/busy minutes are saved, never event details.
 * A day is read in three layers: memory (ScheduleDayCache, caching #46), then the network, then the copy on the
 * phone. Local storage uses two stores, each for what it fits: the days go to the Room table schedule_days
 * (many rows, a limit by date, a delete by query) and the friends list is one small value in SharedPreferences. */
public class OfflineScheduleRepository implements ScheduleRepository {
    private static final String TAG = "OfflineSchedule";
    private static final String PREFS = "parchapp_schedule_offline";
    private static final String PERF = "ParchPerf";
    // local storage: about a month of days per user, older ones are deleted by ScheduleDayDao.keepNewest
    static final int MAX_SAVED_DAYS = 30;
    // multithreading: room is disk i/o and does not run on the main thread; one serial queue, so a read made
    // after a save always sees it, and the main thread only gets the answer (thread parch-schedule-db-1)
    private static final ExecutorService DB_QUEUE = BackgroundThreads.serial("parch-schedule-db");

    private final ScheduleRepository remote;
    private final SharedPreferences prefs;
    private final ScheduleDayDao days;
    private volatile boolean lastFromSaved;
    private volatile long lastSavedAt;
    private final ScheduleDayCache<List<DaySchedule>> memory = new ScheduleDayCache<>(SystemClock::elapsedRealtime);
    // when each day in memory came from the backend (System.currentTimeMillis), for the "Last updated" banner
    private final java.util.Map<String, Long> fetchedAt = new java.util.concurrent.ConcurrentHashMap<>();

    OfflineScheduleRepository(Context context, ScheduleRepository remote) {
        this.remote = remote;
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.days = ParchDatabase.get(context).scheduleDays();
        DB_QUEUE.execute(this::forgetDaysSavedInPreferences);
    }

    /* true when the last friends or day answer came from the phone because the backend was not reachable */
    public boolean isShowingSaved() {
        return lastFromSaved;
    }

    /* when the day on screen came from the backend (System.currentTimeMillis): now for a fresh answer, the time it
     * was fetched for a day from memory, the time it was saved for a copy from the phone; 0 when unknown */
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
        String owner = owner();
        String dayKey = date + "." + String.join(",", sorted);
        String key = owner + ".day." + dayKey;
        long started = SystemClock.elapsedRealtime();
        // 1. memory: a day seen in the last minutes answers at once, without calling the backend
        List<DaySchedule> cached = memory.get(key);
        if (cached != null) {
            lastFromSaved = false;
            Long fetched = fetchedAt.get(key);
            lastSavedAt = fetched == null ? 0 : fetched;
            Log.d(PERF, String.format(Locale.US, "Schedule %s from memory in %d ms", date, SystemClock.elapsedRealtime() - started));
            callback.onSuccess(cached);
            return;
        }
        // 2. network
        remote.getDay(date, friendIds, new Callback<List<DaySchedule>>() {
            @Override
            public void onSuccess(List<DaySchedule> schedules) {
                lastFromSaved = false;
                lastSavedAt = System.currentTimeMillis();
                fetchedAt.put(key, lastSavedAt);
                memory.put(key, schedules);
                saveDay(owner, dayKey, daysToJson(schedules));
                Log.d(PERF, String.format(Locale.US, "Schedule %s from network in %d ms", date, SystemClock.elapsedRealtime() - started));
                callback.onSuccess(schedules);
            }

            // 3. phone: the saved copy instead of an error; it is not put in memory, so the network is tried again next time
            // the row is read on the db queue and the answer goes back to the main thread
            @Override
            public void onError(Exception error) {
                DB_QUEUE.execute(() -> {
                    ScheduleDayEntity saved = readDay(owner, dayKey);
                    // offline Schedule only asks for my own day; when that exact copy is missing, my blocks are
                    // taken from a copy of the same date that was saved together with friends
                    boolean mineOnly = saved == null && friendIds.isEmpty();
                    if (mineOnly) {
                        saved = readNewestOfDate(owner, date);
                    }
                    ScheduleDayEntity found = saved;
                    MainThread.post(() -> {
                        if (found == null) {
                            callback.onError(error);
                            return;
                        }
                        lastFromSaved = true;
                        lastSavedAt = found.savedAt;
                        List<DaySchedule> schedules = daysFromJson(found.json);
                        callback.onSuccess(mineOnly ? onlyOwner(schedules, owner) : schedules);
                    });
                });
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
    private static String owner() {
        UserProfile user = UserSession.getCurrentUser();
        return user == null ? "anonymous" : user.getId();
    }

    private String key(String request) {
        return owner() + "." + request;
    }

    // key-value: the friends list is one small value read whole by its key, SharedPreferences is enough
    private void save(String key, String json) {
        if (json != null) {
            prefs.edit().putString(key, json).apply();
        }
    }

    /* relational: the day goes to its row with the time it was saved, and in the same transaction the days of
     * this user beyond the MAX_SAVED_DAYS newest are deleted. An error of the phone storage loses one save,
     * it never closes the app; the screen already has the data from the network. */
    private void saveDay(String owner, String dayKey, @Nullable String json) {
        if (json == null) {
            return;
        }
        ScheduleDayEntity day = ScheduleDayEntity.of(owner, dayKey, json, System.currentTimeMillis());
        DB_QUEUE.execute(() -> {
            try {
                days.save(day, MAX_SAVED_DAYS);
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not save the Schedule day", error);
            }
        });
    }

    @Nullable
    private ScheduleDayEntity readDay(String owner, String dayKey) {
        try {
            return days.day(owner, dayKey);
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not read the saved Schedule day", error);
            return null;
        }
    }

    @Nullable
    private ScheduleDayEntity readNewestOfDate(String owner, String date) {
        try {
            return days.newestOfDate(owner, date);
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not read the saved Schedule day", error);
            return null;
        }
    }

    /* the schedules of one person from a saved day; the friends in that copy are dropped */
    static List<DaySchedule> onlyOwner(List<DaySchedule> schedules, String owner) {
        List<DaySchedule> mine = new ArrayList<>();
        for (DaySchedule schedule : schedules) {
            if (owner.equals(schedule.getOwnerId())) {
                mine.add(schedule);
            }
        }
        return mine;
    }

    /* before version 2 the days were saved in SharedPreferences; they now live in room, so the old copies
     * are removed once, otherwise they would stay loaded in memory with the preferences for nothing */
    private void forgetDaysSavedInPreferences() {
        SharedPreferences.Editor editor = null;
        for (String key : prefs.getAll().keySet()) {
            if (key.contains(".day.") || key.endsWith(".days.index")) {
                editor = editor == null ? prefs.edit() : editor;
                editor.remove(key);
            }
        }
        if (editor != null) {
            editor.apply();
        }
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
