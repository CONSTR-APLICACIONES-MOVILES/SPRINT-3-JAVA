package com.example.parchapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;

/** Allows local analytics consent without Firebase; group visibility needs the backend. */
final class DisabledPrivacySettingsRepository implements PrivacySettingsRepository {
    private static final String PREFS = "parchapp_privacy";
    private static final String KEY_ANALYTICS_CONSENT = "analytics_consent";
    private final SharedPreferences preferences;

    DisabledPrivacySettingsRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override public boolean isAnalyticsConsentAccepted() {
        return preferences.getBoolean(KEY_ANALYTICS_CONSENT, false);
    }

    @Override public void saveAnalyticsConsent(boolean accepted, Callback<Void> callback) {
        preferences.edit().putBoolean(KEY_ANALYTICS_CONSENT, accepted).apply();
        MainThread.post(() -> callback.onSuccess(null));
    }

    @Override public void getAvailabilityHiddenFromGroups(Callback<Boolean> callback) {
        MainThread.post(() -> callback.onError(new UnsupportedOperationException(
                "Availability privacy needs the backend.")));
    }

    @Override public void setAvailabilityHiddenFromGroups(boolean hidden, Callback<Void> callback) {
        MainThread.post(() -> callback.onError(new UnsupportedOperationException(
                "Availability privacy needs the backend.")));
    }
}
