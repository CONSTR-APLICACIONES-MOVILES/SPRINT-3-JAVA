package com.example.parchapp.analytics;

import android.os.Bundle;
import android.util.Log;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// sends every event to the analyticsEvents collection, the backend reads it to make the team dashboard
// firestore keeps the writes on the phone when there is no signal and sends them later
class FirestoreSink implements AnalyticsSink {
    static final String COLLECTION = "analyticsEvents";
    private static final String TAG = "FirestoreSink";
// keys that can say where the user is, they never go to the database
    private static final Set<String> PRIVATE_KEYS = Collections.singleton(AnalyticsEvents.PARAM_LOCATION_AREA_ID);

// the place where the documents are written, the tests use a fake one
    interface Writer {
        void write(Map<String, Object> document);
    }

    private final Writer writer;
    private volatile boolean enabled;
    private volatile String userHash;

    FirestoreSink() {
        this(document -> {
// the server puts the time, so a wrong clock on the phone does not change the dashboard
            document.put("created_at", FieldValue.serverTimestamp());
            FirebaseFirestore.getInstance().collection(COLLECTION).add(document)
                    .addOnFailureListener(e -> Log.w(TAG, "Could not save the event", e));
        });
    }

    FirestoreSink(Writer writer) {
        this.writer = writer;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void send(String name, Bundle params) {
        Map<String, Object> values = new HashMap<>();
        for (String key : params.keySet()) {
            values.put(key, params.get(key));
        }
        send(name, values);
    }

    void send(String name, Map<String, Object> params) {
        if (!enabled) {
            return;
        }
        writer.write(document(name, params, userHash));
    }

// one document has the name, the params without private keys and the hash of the user, never the real id
    static Map<String, Object> document(String name, Map<String, Object> params, String userHash) {
        Map<String, Object> clean = new HashMap<>(params);
        clean.keySet().removeAll(PRIVATE_KEYS);
        Map<String, Object> document = new HashMap<>();
        document.put("name", name);
        document.put("params", clean);
        document.put("user_hash", userHash);
        return document;
    }

// the tracker gives the sha256 hash of the user, not the real id
    @Override
    public void setUserId(String anonymousId) {
        userHash = anonymousId;
    }

    @Override
    public void setCollectionEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
