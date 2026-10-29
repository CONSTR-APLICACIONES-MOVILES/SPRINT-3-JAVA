package com.example.parchapp.analytics;

import android.os.Bundle;

import java.util.Arrays;
import java.util.List;

// gives the same event to more than one sink, for example firebase analytics and firestore
class CompositeSink implements AnalyticsSink {
    private final List<AnalyticsSink> sinks;

    CompositeSink(AnalyticsSink... sinks) {
        this.sinks = Arrays.asList(sinks);
    }

    @Override
    public void send(String name, Bundle params) {
        for (AnalyticsSink sink : sinks) {
            sink.send(name, params);
        }
    }

    @Override
    public void setUserId(String anonymousId) {
        for (AnalyticsSink sink : sinks) {
            sink.setUserId(anonymousId);
        }
    }

    @Override
    public void setCollectionEnabled(boolean enabled) {
        for (AnalyticsSink sink : sinks) {
            sink.setCollectionEnabled(enabled);
        }
    }
}
