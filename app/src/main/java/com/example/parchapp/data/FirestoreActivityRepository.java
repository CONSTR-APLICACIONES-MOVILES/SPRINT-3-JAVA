package com.example.parchapp.data;

import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/* F6: reads the activities of my groups for the Today's Plans section. Saving an activity is the Activity Created feature, so createActivity is not part of this branch. */
class FirestoreActivityRepository implements ActivityRepository {
    private final FirebaseFirestore db;

    FirestoreActivityRepository(FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public void createActivity(PlannedActivity activity, Callback<PlannedActivity> callback) {
        MainThread.post(() -> callback.onError(new UnsupportedOperationException(
                "Activity publishing is not part of this branch")));
    }

    // first my groups, then one query per group, because the rules only let me read activities of groups i belong to
    @Override
    public void getActivities(String userId, Callback<List<PlannedActivity>> callback) {
        db.collection("groups").whereArrayContains("memberIds", userId).get()
                .addOnSuccessListener(groups -> {
                    List<Task<QuerySnapshot>> queries = new ArrayList<>();
                    for (DocumentSnapshot group : groups.getDocuments()) {
                        queries.add(db.collection("activities")
                                .whereEqualTo("groupId", group.getId())
                                .orderBy("createdAt")
                                .get());
                    }
                    if (queries.isEmpty()) {
                        MainThread.post(() -> callback.onSuccess(Collections.emptyList()));
                        return;
                    }
                    Tasks.whenAllSuccess(queries).addOnSuccessListener(results -> {
                        List<PlannedActivity> activities = new ArrayList<>();
                        for (Object result : results) {
                            for (DocumentSnapshot activity : ((QuerySnapshot) result).getDocuments()) {
                                activities.add(fromSnapshot(activity));
                            }
                        }
                        // newest first
                        activities.sort((left, right) -> Long.compare(right.getCreatedAt(), left.getCreatedAt()));
                        MainThread.post(() -> callback.onSuccess(activities));
                    }).addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
                })
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
    }

    static PlannedActivity fromSnapshot(DocumentSnapshot snapshot) {
        // read it as a plain object: asking firestore for Map.class crashes because Map has generic types
        Object rawContext = snapshot.get("planningContext");
        Map<?, ?> context = rawContext instanceof Map ? (Map<?, ?>) rawContext : null;
        int groupSize = context != null && context.get("groupSizeAtCreation") instanceof Number
                ? ((Number) context.get("groupSizeAtCreation")).intValue() : 0;
        boolean comparisonUsed = context != null && Boolean.TRUE.equals(context.get("comparisonUsed"));
        String areaId = context != null && context.get("locationAreaId") != null
                ? context.get("locationAreaId").toString() : PlannedActivity.UNKNOWN_AREA;
        return new PlannedActivity(snapshot.getId(),
                stringValue(snapshot.get("groupId")), stringValue(snapshot.get("title")),
                stringValue(snapshot.get("category")), stringValue(snapshot.get("date")),
                stringValue(snapshot.get("time")), stringValue(snapshot.get("location")),
                stringValue(snapshot.get("status")), timestampMillis(snapshot.get("createdAt")),
                groupSize, comparisonUsed, areaId);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static long timestampMillis(Object value) {
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate().getTime();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }
}
