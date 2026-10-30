package com.example.parchapp;

import android.app.Application;

import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.data.Repositories;

public class ParchApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Repositories.init(this);
        AnalyticsTracker.getInstance().init(this, Repositories.isFirebaseAnalyticsAvailable(),
                Repositories.privacySettings().isAnalyticsConsentAccepted());

        UserProfile restored = Repositories.auth().getSignedInUser();
        if (restored != null) {
            UserSession.setCurrentUser(restored);
            AnalyticsTracker.getInstance().setUserId(restored.getId());
        }
        Repositories.activityCreationSync().start(AnalyticsTracker.getInstance());
        Repositories.availabilityStatusSync().start();
    }
}
