package com.example.parchapp.data;

import com.example.parchapp.domain.model.SlotAcceptanceInsight;
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

// bq3: reads the position, the percent accepted without changes and the number of slots of the same document
    @Override
    public void getSlotAcceptance(Callback<SlotAcceptanceInsight> callback) {
        db.collection("insights").document("bq3_slot_acceptance").get()
                .addOnSuccessListener(snapshot -> {
                    Long position = snapshot.getLong("recommended_position");
                    Long sample = snapshot.getLong("sample_size");
                    callback.onSuccess(new SlotAcceptanceInsight(
                            position == null ? DEFAULT_RECOMMENDED_POSITION : position.intValue(),
                            snapshot.getDouble("pct_unchanged_overall"),
                            sample == null ? 0 : sample.intValue()));
                })
// without the document or offline with nothing saved the first slot is recommended and there is no data
                .addOnFailureListener(e -> callback.onSuccess(
                        SlotAcceptanceInsight.withoutData(DEFAULT_RECOMMENDED_POSITION)));
    }
}
