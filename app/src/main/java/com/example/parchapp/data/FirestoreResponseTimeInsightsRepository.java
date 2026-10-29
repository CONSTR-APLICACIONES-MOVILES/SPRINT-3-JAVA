package com.example.parchapp.data;

import com.example.parchapp.domain.model.ResponseTimeEstimate;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class FirestoreResponseTimeInsightsRepository implements ResponseTimeInsightsRepository {
    private final FirebaseFirestore db;

    FirestoreResponseTimeInsightsRepository(FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public void getEstimates(Callback<List<ResponseTimeEstimate>> callback) {
        db.collection("insights").document("bq5_response_time").get()
                .addOnSuccessListener(snapshot -> MainThread.post(() ->
                        callback.onSuccess(parseEstimates(snapshot.get("by_group_size")))))
                .addOnFailureListener(error -> MainThread.post(() ->
                        callback.onSuccess(Collections.emptyList())));
    }

    static List<ResponseTimeEstimate> parseEstimates(Object rawRows) {
        if (!(rawRows instanceof List)) return Collections.emptyList();
        List<ResponseTimeEstimate> estimates = new ArrayList<>();
        for (Object raw : (List<?>) rawRows) {
            if (!(raw instanceof Map)) continue;
            Map<?, ?> row = (Map<?, ?>) raw;
            Object size = row.get("group_size");
            Object minutes = row.get("estimate_minutes");
            Object rawKind = row.get("estimate_kind");
            if (!(size instanceof Number) || !(minutes instanceof Number) || rawKind == null) continue;
            ResponseTimeEstimate.Kind kind;
            if ("all_responded".equals(rawKind.toString())) {
                kind = ResponseTimeEstimate.Kind.ALL_RESPONDED;
            } else if ("per_response".equals(rawKind.toString())) {
                kind = ResponseTimeEstimate.Kind.PER_RESPONSE;
            } else {
                continue;
            }
            double value = ((Number) minutes).doubleValue();
            if (value <= 0 || ((Number) size).intValue() <= 0) continue;
            estimates.add(new ResponseTimeEstimate(((Number) size).intValue(), value, kind));
        }
        return Collections.unmodifiableList(estimates);
    }
}
