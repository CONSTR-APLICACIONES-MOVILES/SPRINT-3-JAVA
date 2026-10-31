package com.example.parchapp.data;

import android.content.Context;
import android.content.SharedPreferences;

// remembers when each group came from the server for the last time
// it lives in shared preferences, so group details can say it even when the app opens without connection
public final class GroupSyncStore {
    private static final String PREFS = "parchapp_group_sync";
    private final SharedPreferences prefs;

    public GroupSyncStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void markSynced(String groupId, long timeMs) {
        prefs.edit().putLong(groupId, timeMs).apply();
    }

// the time of the last sync, or -1 when this group never came from the server on this phone
    public long lastSynced(String groupId) {
        return prefs.getLong(groupId, -1L);
    }
}
