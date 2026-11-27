package com.example.parchapp.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** One locally saved activity draft with its remote synchronization state. */
@Entity(tableName = "activity_drafts", primaryKeys = {"owner", "id"},
        indices = {@Index(value = {"owner", "state", "updatedAt"})})
public class ActivityDraftEntity {
    @NonNull
    public String owner;
    @NonNull
    public String id;
    public long revision;
    public long updatedAt;
    @NonNull
    public String category;
    @NonNull
    public String state;
    @Nullable
    public String payload;
    public long remoteVersion;
    public boolean pending;
    @NonNull
    public String operationId;
    @Nullable
    public String syncError;
    @Nullable
    public String requestKey;

    public ActivityDraftEntity(@NonNull String owner, @NonNull String id, long revision,
                               long updatedAt, @NonNull String category, @NonNull String state,
                               @Nullable String payload, long remoteVersion, boolean pending,
                               @NonNull String operationId, @Nullable String syncError,
                               @Nullable String requestKey) {
        this.owner = owner;
        this.id = id;
        this.revision = revision;
        this.updatedAt = updatedAt;
        this.category = category;
        this.state = state;
        this.payload = payload;
        this.remoteVersion = remoteVersion;
        this.pending = pending;
        this.operationId = operationId;
        this.syncError = syncError;
        this.requestKey = requestKey;
    }
}
