package com.example.parchapp;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.parchapp.data.ParticipantOutboxDao;
import com.example.parchapp.data.ParticipantOutboxEntity;
import com.example.parchapp.data.ParchDatabase;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.util.Callback;
import com.google.firebase.functions.FirebaseFunctionsException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Retries one offline participant action when the network is available. */
public class ParticipantActionWorker extends androidx.work.Worker {
    static final String KEY_ID = "outboxId";
    static final String KEY_ACTIVITY = "activityId";
    static final String KEY_ACTION = "action";
    static final String KEY_USER = "userId";
    static final String KEY_VERSION = "expectedVersion";
    static final String KEY_CLIENT = "clientUserId";

    public ParticipantActionWorker(@NonNull Context context, @NonNull androidx.work.WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        long id = getInputData().getLong(KEY_ID, -1L);
        String activityId = getInputData().getString(KEY_ACTIVITY);
        String action = getInputData().getString(KEY_ACTION);
        String userId = getInputData().getString(KEY_USER);
        String version = getInputData().getString(KEY_VERSION);
        String clientUserId = getInputData().getString(KEY_CLIENT);
        if (id < 0 || activityId == null || action == null || version == null || clientUserId == null) {
            return Result.failure();
        }

        Context context = getApplicationContext();
        Repositories.init(context);
        ParticipantOutboxDao outbox = ParchDatabase.get(context).participantOutbox();
        outbox.update(id, ParticipantOutboxEntity.SYNCING, null);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<>();
        Repositories.activities().changeActivityParticipants(activityId, action, userId, version,
                clientUserId, new Callback<Void>() {
                    @Override public void onSuccess(Void ignored) {
                        latch.countDown();
                    }

                    @Override public void onError(Exception error) {
                        failure.set(error);
                        latch.countDown();
                    }
                });

        try {
            if (!latch.await(60, TimeUnit.SECONDS)) {
                outbox.update(id, ParticipantOutboxEntity.QUEUED, null);
                return Result.retry();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            outbox.update(id, ParticipantOutboxEntity.QUEUED, null);
            return Result.retry();
        }

        Exception error = failure.get();
        if (error == null) {
            outbox.delete(id);
            ParticipantSyncEvents.notifyChanged();
            return Result.success();
        }
        String message = error.getLocalizedMessage();
        if (error instanceof FirebaseFunctionsException) {
            FirebaseFunctionsException.Code code = ((FirebaseFunctionsException) error).getCode();
            if (code == FirebaseFunctionsException.Code.UNAVAILABLE
                    || code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED) {
                outbox.update(id, ParticipantOutboxEntity.QUEUED, null);
                return Result.retry();
            }
            if (code == FirebaseFunctionsException.Code.ABORTED) {
                outbox.update(id, ParticipantOutboxEntity.CONFLICT,
                        message == null ? "Participants changed. Reload." : message);
                ParticipantSyncEvents.notifyChanged();
                return Result.failure();
            }
        }
        outbox.update(id, ParticipantOutboxEntity.FAILED,
                message == null ? "Could not update participants." : message);
        ParticipantSyncEvents.notifyChanged();
        return Result.failure();
    }

    public static void enqueue(Context context, long id, String activityId, String action,
                               @Nullable String userId, String expectedVersion, String clientUserId) {
        Data input = new Data.Builder()
                .putLong(KEY_ID, id)
                .putString(KEY_ACTIVITY, activityId)
                .putString(KEY_ACTION, action)
                .putString(KEY_USER, userId)
                .putString(KEY_VERSION, expectedVersion)
                .putString(KEY_CLIENT, clientUserId)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ParticipantActionWorker.class)
                .setInputData(input)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork("participant-action-" + id,
                ExistingWorkPolicy.KEEP, request);
    }
}
