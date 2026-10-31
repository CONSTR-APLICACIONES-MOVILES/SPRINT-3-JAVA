package com.example.parchapp;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.data.Repositories;

public class ParchApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // the screens are designed only in light colors, on a phone in dark mode the hints were almost invisible
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        // every screen stays inside the status bar and the navigation bar (real phones with 3-button navigation)
        registerActivityLifecycleCallbacks(new SystemBarsInsets());
        Repositories.init(this);
        AnalyticsTracker.getInstance().init(this, Repositories.isFirebaseAnalyticsAvailable(),
                Repositories.privacySettings().isAnalyticsConsentAccepted());

        UserProfile restored = Repositories.auth().getSignedInUser();
        if (restored != null) {
            UserSession.setCurrentUser(restored);
            AnalyticsTracker.getInstance().setUserId(restored.getId());
        }
        Repositories.availabilityStatusSync().start();
    }
}
