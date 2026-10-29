package com.example.parchapp.analytics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// tests for the sink that sends the events to firestore
public class FirestoreSinkTest {

    private final List<Map<String, Object>> written = new ArrayList<>();
    private final FirestoreSink sink = new FirestoreSink(written::add);

    private static Map<String, Object> params() {
        Map<String, Object> params = new HashMap<>();
        params.put(AnalyticsEvents.PARAM_FEATURE, AnalyticsEvents.FEATURE_GROUPS);
        params.put(AnalyticsEvents.PARAM_LOCATION_AREA_ID, "library");
        return params;
    }

// the document has the name, the params and the hash of the user
    @Test
    @SuppressWarnings("unchecked")
    public void writesNameParamsAndUserHash() {
        sink.setCollectionEnabled(true);
        sink.setUserId("abc123");
        sink.send(AnalyticsEvents.FEATURE_USED, params());

        assertEquals(1, written.size());
        Map<String, Object> document = written.get(0);
        assertEquals(AnalyticsEvents.FEATURE_USED, document.get("name"));
        assertEquals("abc123", document.get("user_hash"));
        Map<String, Object> saved = (Map<String, Object>) document.get("params");
        assertEquals(AnalyticsEvents.FEATURE_GROUPS, saved.get(AnalyticsEvents.PARAM_FEATURE));
    }

// the place of the user never goes to the database
    @Test
    @SuppressWarnings("unchecked")
    public void dropsTheLocation() {
        sink.setCollectionEnabled(true);
        sink.send(AnalyticsEvents.FEATURE_USED, params());

        Map<String, Object> saved = (Map<String, Object>) written.get(0).get("params");
        assertFalse(saved.containsKey(AnalyticsEvents.PARAM_LOCATION_AREA_ID));
    }

// without the consent of the user nothing is written
    @Test
    public void writesNothingWithoutConsent() {
        sink.send(AnalyticsEvents.FEATURE_USED, params());
        sink.setCollectionEnabled(true);
        sink.setCollectionEnabled(false);
        sink.send(AnalyticsEvents.FEATURE_USED, params());

        assertTrue(written.isEmpty());
    }

// the composite sink gives the consent and the user to every sink
    @Test
    public void compositeSharesConsentAndUser() {
        List<Map<String, Object>> other = new ArrayList<>();
        FirestoreSink second = new FirestoreSink(other::add);
        CompositeSink composite = new CompositeSink(sink, second);
        composite.setCollectionEnabled(true);
        composite.setUserId("abc123");
        sink.send(AnalyticsEvents.FEATURE_USED, params());
        second.send(AnalyticsEvents.FEATURE_USED, params());

        assertEquals("abc123", written.get(0).get("user_hash"));
        assertEquals("abc123", other.get(0).get("user_hash"));
    }
}
