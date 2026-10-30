package com.example.parchapp.data;

import android.util.Log;

import com.example.parchapp.domain.model.SlotAcceptanceInsight;
import com.example.parchapp.util.BackgroundThreads;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

/* F2: reads {@code insights/bq3_slot_acceptance}, the document the backend job refreshSlotRecommendation writes every day from the BQ3 result. */
class FirestoreInsightsRepository implements InsightsRepository {
    private static final String TAG = "FirestoreInsights";
// strategy 6: the file is i/o, so it goes in its own queue and never on the main thread
    private static final ExecutorService DISK = BackgroundThreads.serial("parch-insight-file");

    private final FirebaseFirestore db;
// strategy 9: cache, falling back to network, the result stays in memory for a few minutes
    private final SlotAcceptanceCache cache = new SlotAcceptanceCache(System::currentTimeMillis);
// strategy 7: the last result on the phone, for when firestore has no copy and there is no connection
    private final SlotAcceptanceFile file;

    FirestoreInsightsRepository(FirebaseFirestore db, SlotAcceptanceFile file) {
        this.db = db;
        this.file = file;
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
                        saveToFile(insight);
                    }
                    callback.onSuccess(insight);
                })
                .addOnFailureListener(e -> loadFromFile(callback));
    }

    private void saveToFile(SlotAcceptanceInsight insight) {
        DISK.execute(() -> {
            try {
                file.save(insight);
            } catch (IOException error) {
                Log.w(TAG, "Could not save the bq3 result", error);
            }
        });
    }

// offline and firestore has no copy: the file, and without file the first slot is recommended and there is no data
    private void loadFromFile(Callback<SlotAcceptanceInsight> callback) {
        DISK.execute(() -> {
            SlotAcceptanceInsight saved = file.read();
            MainThread.post(() -> callback.onSuccess(saved != null ? saved
                    : SlotAcceptanceInsight.withoutData(DEFAULT_RECOMMENDED_POSITION)));
        });
    }
}
