package com.example.parchapp.analytics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;

import java.util.List;

public class FeatureUsageTelemetryTest {

    @Test
    public void trackFeatureQueuesBq8EventsForEveryCoreFeature() throws JSONException {
        EventQueue queue = new EventQueue();
        AnalyticsTracker tracker = new AnalyticsTracker(queue);
        tracker.setAnalyticsConsent(true);

        String[][] featuresAndScreens = {
                {AnalyticsEvents.FEATURE_GROUPS, "groups"},
                {AnalyticsEvents.FEATURE_COMPARE_AVAILABILITY, "compare_availability"},
                {AnalyticsEvents.FEATURE_SCHEDULE, "schedule"},
                {AnalyticsEvents.FEATURE_INVITATIONS, "create_group"},
                {AnalyticsEvents.FEATURE_ALERTS, "alerts"},
        };
        for (String[] featureAndScreen : featuresAndScreens) {
            tracker.trackFeature(featureAndScreen[0], featureAndScreen[1]);
        }

        List<EventQueue.Event> events = queue.drain();
        assertEquals(featuresAndScreens.length, events.size());
        for (int i = 0; i < featuresAndScreens.length; i++) {
            EventQueue.Event event = events.get(i);
            assertEquals(AnalyticsEvents.FEATURE_USED, event.name);
            assertEquals(featuresAndScreens[i][0], event.params.getString(AnalyticsEvents.PARAM_FEATURE));
            assertEquals(featuresAndScreens[i][1], event.params.getString(AnalyticsEvents.PARAM_SCREEN));
            assertTrue(event.params.getLong(AnalyticsEvents.PARAM_CLIENT_TS) > 0);
        }
    }

    @Test
    public void trackFeatureDoesNotQueueEventsWithoutAnalyticsConsent() {
        EventQueue queue = new EventQueue();
        AnalyticsTracker tracker = new AnalyticsTracker(queue);

        tracker.trackFeature(AnalyticsEvents.FEATURE_GROUPS, "groups");

        assertEquals(0, queue.size());
    }
}
