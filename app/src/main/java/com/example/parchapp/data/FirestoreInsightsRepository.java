package com.example.parchapp.data;

import com.example.parchapp.domain.model.SlotAcceptanceInsight;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;
import com.google.firebase.firestore.FirebaseFirestore;

/* F2: reads {@code insights/bq3_slot_acceptance}, the document the backend job refreshSlotRecommendation writes every day from the BQ3 result. */
class FirestoreInsightsRepository implements InsightsRepository {
    private final FirebaseFirestore db;
// strategy 9: cache, falling back to network, the result stays in memory for a few minutes
    private final SlotAcceptanceCache cache = new SlotAcceptanceCache(System::currentTimeMillis);

    FirestoreInsightsRepository(FirebaseFirestore db) {
        this.db = db;
    }

// the badge only needs the position, so it uses the same cached result
    @Override
    public void getRecommendedSlotPosition(Callback<Integer> callback) {
        getSlotAcceptance(new Callback<SlotAcceptanceInsight>() {
            @Override
            public void onSuccess(SlotAcceptanceInsight insight) {
                callback.onSuccess(insight.getRecommendedPosition());
            }

            @Override
            public void onError(Exception error) {
                callback.onSuccess(DEFAULT_RECOMMENDED_POSITION);
            }
        });
    }

// bq3: first the result in memory, if there is none or it expired we read the document and keep it
// firestore also has its own copy on the phone, so without connection we still get the last result
    @Override
    public void getSlotAcceptance(Callback<SlotAcceptanceInsight> callback) {
        SlotAcceptanceInsight cached = cache.get();
        if (cached != null) {
            MainThread.post(() -> callback.onSuccess(cached));
            return;
        }
        db.collection("insights").document("bq3_slot_acceptance").get()
                .addOnSuccessListener(snapshot -> {
                    Long position = snapshot.getLong("recommended_position");
                    Long sample = snapshot.getLong("sample_size");
                    SlotAcceptanceInsight insight = new SlotAcceptanceInsight(
                            position == null ? DEFAULT_RECOMMENDED_POSITION : position.intValue(),
                            snapshot.getDouble("pct_unchanged_overall"),
                            sample == null ? 0 : sample.intValue());
// only a real answer is kept, so a missing document is asked again next time
                    if (snapshot.exists()) {
                        cache.put(insight);
                    }
                    callback.onSuccess(insight);
                })
// without the document or offline with nothing saved the first slot is recommended and there is no data
                .addOnFailureListener(e -> callback.onSuccess(
                        SlotAcceptanceInsight.withoutData(DEFAULT_RECOMMENDED_POSITION)));
    }
}
