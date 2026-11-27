package com.example.parchapp.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface ParticipantCacheDao {
    @Query("SELECT * FROM participant_cache WHERE activityId = :activityId LIMIT 1")
    ParticipantCacheEntity get(String activityId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void put(ParticipantCacheEntity entry);
}
