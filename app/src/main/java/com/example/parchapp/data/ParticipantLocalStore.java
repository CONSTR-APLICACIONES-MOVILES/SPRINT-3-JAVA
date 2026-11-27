package com.example.parchapp.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.example.parchapp.domain.model.ActivityParticipants;
import com.example.parchapp.util.Callback;

import org.json.JSONException;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Room-backed snapshot cache and offline outbox for the participant screen. */
public final class ParticipantLocalStore {
    public static final class Snapshot {
        public final ActivityParticipants participants;
        public final long savedAt;

        Snapshot(ActivityParticipants participants, long savedAt) {
            this.participants = participants;
            this.savedAt = savedAt;
        }
    }

    private final ParticipantCacheDao cache;
    private final ParticipantOutboxDao outbox;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());

    public ParticipantLocalStore(Context context) {
        ParchDatabase database = ParchDatabase.get(context.getApplicationContext());
        cache = database.participantCache();
        outbox = database.participantOutbox();
    }

    public void loadSnapshot(String activityId, Callback<Snapshot> callback) {
        pool.execute(() -> {
            ParticipantCacheEntity entry = cache.get(activityId);
            Snapshot snapshot = null;
            if (entry != null) {
                ActivityParticipants participants = ActivityParticipants.fromJson(entry.json);
                if (participants != null) snapshot = new Snapshot(participants, entry.savedAt);
            }
            Snapshot result = snapshot;
            main.post(() -> callback.onSuccess(result));
        });
    }

    public void saveSnapshot(ActivityParticipants participants) {
        if (participants == null) return;
        pool.execute(() -> {
            try {
                cache.put(new ParticipantCacheEntity(participants.getActivityId(),
                        participants.toJson(), System.currentTimeMillis()));
            } catch (JSONException ignored) {
                // The remote answer stays on screen; only the offline copy is skipped.
            }
        });
    }

    public void enqueue(String activityId, String action, @Nullable String userId,
                        String expectedVersion, Callback<Long> callback) {
        pool.execute(() -> {
            long id = outbox.insert(new ParticipantOutboxEntity(activityId, action, userId,
                    expectedVersion, ParticipantOutboxEntity.QUEUED, null, System.currentTimeMillis()));
            main.post(() -> callback.onSuccess(id));
        });
    }

    public void loadPending(String activityId, Callback<List<ParticipantOutboxEntity>> callback) {
        pool.execute(() -> {
            List<ParticipantOutboxEntity> pending = outbox.forActivity(activityId);
            main.post(() -> callback.onSuccess(pending));
        });
    }

    public void updateStatus(long id, String status, @Nullable String error) {
        pool.execute(() -> outbox.update(id, status, error));
    }

    public void delete(long id) {
        pool.execute(() -> outbox.delete(id));
    }

    public void shutdown() {
        pool.shutdownNow();
    }
}
