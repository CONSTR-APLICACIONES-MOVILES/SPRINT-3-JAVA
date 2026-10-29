package com.example.parchapp.analytics;

import android.os.Bundle;
import android.util.Log;

/* Used when Firebase is not configured, so events can still be checked with {@code adb logcat -s ParchAnalytics}. */
class LogcatSink implements AnalyticsSink {
    private static final String TAG = "ParchAnalytics";
    private volatile boolean enabled;

    @Override
    @SuppressWarnings("deprecation") // Bundle.get(key) is fine for a debug output ( as he teacher said make dummy environment mocks).
    public void send(String name, Bundle params) {
        if (!enabled) return;
        StringBuilder text = new StringBuilder(name);
        for (String key : params.keySet()) {
            text.append(' ').append(key).append('=').append(params.get(key));
        }
        Log.i(TAG, text.toString());
    }

    @Override
    public void setUserId(String anonymousId) {
        if (enabled) Log.i(TAG, "user_id=" + anonymousId);
    }

    @Override
    public void setCollectionEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
