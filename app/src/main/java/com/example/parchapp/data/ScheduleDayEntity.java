package com.example.parchapp.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

// local storage: one Schedule day saved on the phone, a row for each account and each day + compared friends
// the primary key has the owner, so another account on the same phone never reads these days
// the index on (owner, savedAt) is what the "keep only the newest days" delete walks, so it does not scan the table
@Entity(tableName = "schedule_days",
        primaryKeys = {"owner", "dayKey"},
        indices = {@Index({"owner", "savedAt"})})
public class ScheduleDayEntity {
    @NonNull
    public String owner = "";
    // "2026-11-13.friendA,friendB", the friend ids are sorted so the same comparison is always the same row
    @NonNull
    public String dayKey = "";
    // only free/busy minutes of each person, never the titles of the events
    @NonNull
    public String json = "";
    // System.currentTimeMillis() when it was saved, offline the screen says how old the copy is
    public long savedAt;

    static ScheduleDayEntity of(String owner, String dayKey, String json, long savedAt) {
        ScheduleDayEntity entity = new ScheduleDayEntity();
        entity.owner = owner;
        entity.dayKey = dayKey;
        entity.json = json;
        entity.savedAt = savedAt;
        return entity;
    }
}
