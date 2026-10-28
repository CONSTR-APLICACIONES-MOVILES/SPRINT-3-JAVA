package com.example.parchapp.analytics;

import android.content.Context;
import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;

class FirebaseAnalyticsSink implements AnalyticsSink {
    private final FirebaseAnalytics firebaseAnalytics;

    FirebaseAnalyticsSink(Context context) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context);
    }

    @Override
    public void send(String name, Bundle params) {
        firebaseAnalytics.logEvent(name, params);
    }

    @Override
    public void setUserId(String anonymousId) {
        firebaseAnalytics.setUserId(anonymousId);
    }
}
