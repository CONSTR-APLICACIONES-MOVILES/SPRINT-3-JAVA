package com.example.parchapp.data;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ParticipantOutboxDao {
    @Insert
    long insert(ParticipantOutboxEntity entry);

    @Query("SELECT * FROM participant_outbox WHERE activityId = :activityId ORDER BY createdAt ASC")
    List<ParticipantOutboxEntity> forActivity(String activityId);

    @Query("SELECT * FROM participant_outbox WHERE id = :id LIMIT 1")
    ParticipantOutboxEntity byId(long id);

    @Query("UPDATE participant_outbox SET status = :status, error = :error WHERE id = :id")
    void update(long id, String status, @Nullable String error);

    @Query("DELETE FROM participant_outbox WHERE id = :id")
    void delete(long id);
}
