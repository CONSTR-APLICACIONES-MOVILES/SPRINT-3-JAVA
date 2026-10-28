package com.example.parchapp.domain;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/* The scenario owns the availability the user broadcasts to their groups: the quick status shown on the dashboard and the status chosen in the availability sheet. Kept on the device so it survives a process restart.
 */
public class StatusService {

    public interface Listener {
        void onStatusChanged(StatusService status);

    }

    private static final String PREFS = "parchapp_status";
    private static final String KEY_FREE_UNTIL = "free_until";
    private static final String KEY_NEXT_ACTIVITY = "next_activity";
    private static final String KEY_AVAILABILITY = "availability";
    private static final String KEY_DURATION = "availability_duration";

    private final SharedPreferences prefs;
    private final List<Listener> listeners = new ArrayList<>();

    public StatusService(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getFreeUntil() {
        return prefs.getString(KEY_FREE_UNTIL, "");
    }

    public String getNextActivity() {
        return prefs.getString(KEY_NEXT_ACTIVITY, "");
    }

    public String getAvailability() {
        return prefs.getString(KEY_AVAILABILITY, "Available now");
    }

    public String getAvailabilityDuration() {
        return prefs.getString(KEY_DURATION, "For 1 hour");
    }

    /** In the following code the empty values keep the current one, matching the quick status sheet. */
    public void updateQuickStatus(String freeUntil, String nextActivity) {
        SharedPreferences.Editor editor = prefs.edit();
        if (!freeUntil.isEmpty()) {
            editor.putString(KEY_FREE_UNTIL, freeUntil);
        }
        if (!nextActivity.isEmpty()) {
            editor.putString(KEY_NEXT_ACTIVITY, nextActivity);
        }
        editor.apply();
        notifyListeners();
    }

    public void setAvailability(String availability, String duration) {
        prefs.edit()
                .putString(KEY_AVAILABILITY, availability)
                .putString(KEY_DURATION, duration)
                .apply();
        notifyListeners();
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onStatusChanged(this);
        }
    }
}
