package com.example.parchapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

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

/* Eventual connectivity for Schedule, the same idea as the Flutter OfflineMobileApi: every answer of getScheduleFriends and getScheduleDay is saved per user, and when the network fails the saved answer is shown instead of an error. Writes (validate, import) always need the backend. Only free/busy minutes are saved, never event details. */
public class OfflineScheduleRepository implements ScheduleRepository {
    private static final String TAG = "OfflineSchedule";
    private static final String PREFS = "parchapp_schedule_offline";

    private final ScheduleRepository remote;
    private final SharedPreferences prefs;
    private volatile boolean lastFromSaved;

    OfflineScheduleRepository(Context context, ScheduleRepository remote) {
        this.remote = remote;
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /* true when the last friends or day answer came from the phone because the backend was not reachable */
    public boolean isShowingSaved() {
        return lastFromSaved;
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
        remote.getDay(date, friendIds, new Callback<List<DaySchedule>>() {
            @Override
            public void onSuccess(List<DaySchedule> schedules) {
                lastFromSaved = false;
                save(key, daysToJson(schedules));
                callback.onSuccess(schedules);
            }

            @Override
            public void onError(Exception error) {
                String saved = prefs.getString(key, null);
                if (saved == null) {
                    callback.onError(error);
                    return;
                }
                lastFromSaved = true;
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
    public void importCalendarAvailability(String startDate, int days, List<String[]> intervals,
                                           Callback<Integer> callback) {
        remote.importCalendarAvailability(startDate, days, intervals, callback);
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
