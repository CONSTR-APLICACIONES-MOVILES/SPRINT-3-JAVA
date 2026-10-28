package com.example.parchapp.data;

import com.example.parchapp.util.Callback;
import com.google.firebase.firestore.FirebaseFirestore;

/* F2: reads {@code insights/bq3_slot_acceptance}, the document the backend job refreshSlotRecommendation writes every day from the BQ3 result. */
class FirestoreInsightsRepository implements InsightsRepository {
    private final FirebaseFirestore db;

    FirestoreInsightsRepository(FirebaseFirestore db) {
        this.db = db;
    }

    @Override
    public void getRecommendedSlotPosition(Callback<Integer> callback) {
        db.collection("insights").document("bq3_slot_acceptance").get()
                .addOnSuccessListener(snapshot -> {
                    Long position = snapshot.getLong("recommended_position");
                    callback.onSuccess(position == null ? DEFAULT_RECOMMENDED_POSITION : position.intValue());
                })
                // without the document (or offline with nothing cached) the first slot stays the recommended one
                .addOnFailureListener(e -> callback.onSuccess(DEFAULT_RECOMMENDED_POSITION));
    }
}
