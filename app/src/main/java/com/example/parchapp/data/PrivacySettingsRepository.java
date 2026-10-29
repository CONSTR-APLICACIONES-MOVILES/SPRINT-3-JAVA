package com.example.parchapp.data;

import com.example.parchapp.util.Callback;

/** Analytics consent is stored locally; schedule visibility is saved through the backend repository. */
public interface PrivacySettingsRepository {
    boolean isAnalyticsConsentAccepted();

    void saveAnalyticsConsent(boolean accepted, Callback<Void> callback);

    void getAvailabilityHiddenFromGroups(Callback<Boolean> callback);

    void setAvailabilityHiddenFromGroups(boolean hidden, Callback<Void> callback);
}
