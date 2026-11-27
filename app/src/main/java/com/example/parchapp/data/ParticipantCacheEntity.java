package com.example.parchapp.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Last participants snapshot kept on the phone for offline reads. */
@Entity(tableName = "participant_cache")
public class ParticipantCacheEntity {
    @PrimaryKey
    @NonNull
    public String activityId;
    @NonNull
    public String json;
    public long savedAt;

    public ParticipantCacheEntity(@NonNull String activityId, @NonNull String json, long savedAt) {
        this.activityId = activityId;
        this.json = json;
        this.savedAt = savedAt;
    }
}
