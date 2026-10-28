package com.example.parchapp.analytics;

import android.os.Bundle;

/* Destination of the batched events. Hidden behind {@link AnalyticsTracker}. */
public interface AnalyticsSink {
    void send(String name, Bundle params);

    void setUserId(String anonymousId);
}
