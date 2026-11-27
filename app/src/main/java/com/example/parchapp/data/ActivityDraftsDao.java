package com.example.parchapp.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ActivityDraftsDao {
    @Query("SELECT * FROM activity_drafts WHERE owner = :owner AND (state = 'active' OR syncError = 'conflict') ORDER BY updatedAt DESC")
    List<ActivityDraftEntity> visible(String owner);

    @Query("SELECT * FROM activity_drafts WHERE owner = :owner")
    List<ActivityDraftEntity> all(String owner);

    @Query("SELECT * FROM activity_drafts WHERE owner = :owner AND pending = 1 ORDER BY updatedAt ASC")
    List<ActivityDraftEntity> pending(String owner);

    @Query("SELECT * FROM activity_drafts WHERE owner = :owner AND id = :id LIMIT 1")
    ActivityDraftEntity get(String owner, String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void put(ActivityDraftEntity entity);

    @Query("DELETE FROM activity_drafts WHERE owner = :owner AND id = :id")
    void delete(String owner, String id);

    @Query("UPDATE activity_drafts SET remoteVersion = :version, pending = 0, syncError = NULL, updatedAt = :updatedAt WHERE owner = :owner AND id = :id")
    void markSynced(String owner, String id, long version, long updatedAt);

    @Query("UPDATE activity_drafts SET pending = 0, syncError = :error WHERE owner = :owner AND id = :id")
    void markConflict(String owner, String id, String error);

    @Query("UPDATE activity_drafts SET revision = :revision, payload = :payload, category = :category, updatedAt = :updatedAt, pending = :pending, operationId = :operationId, syncError = NULL, requestKey = :requestKey WHERE owner = :owner AND id = :id")
    void updateContent(String owner, String id, long revision, String payload, String category,
                       long updatedAt, boolean pending, String operationId, String requestKey);
}
