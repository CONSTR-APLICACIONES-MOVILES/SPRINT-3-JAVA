package com.example.parchapp.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** One participant action waiting for the backend. */
@Entity(tableName = "participant_outbox")
public class ParticipantOutboxEntity {
    public static final String QUEUED = "QUEUED";
    public static final String SYNCING = "SYNCING";
    public static final String FAILED = "FAILED";
    public static final String CONFLICT = "CONFLICT";

    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public String activityId;
    @NonNull
    public String action;
    @Nullable
    public String userId;
    @NonNull
    public String expectedVersion;
    @NonNull
    public String status;
    @Nullable
    public String error;
    public long createdAt;

    public ParticipantOutboxEntity(@NonNull String activityId, @NonNull String action,
                                    @Nullable String userId, @NonNull String expectedVersion,
                                    @NonNull String status, @Nullable String error, long createdAt) {
        this.activityId = activityId;
        this.action = action;
        this.userId = userId;
        this.expectedVersion = expectedVersion;
        this.status = status;
        this.error = error;
        this.createdAt = createdAt;
    }
}
