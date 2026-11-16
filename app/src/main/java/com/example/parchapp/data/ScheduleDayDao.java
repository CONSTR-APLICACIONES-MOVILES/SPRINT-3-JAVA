package com.example.parchapp.data;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

// local storage: the queries of the Schedule days saved on the phone
// room does not let these run on the main thread, OfflineScheduleRepository calls them from its queue
@Dao
public abstract class ScheduleDayDao {
    @Nullable
    @Query("SELECT * FROM schedule_days WHERE owner = :owner AND dayKey = :dayKey")
    public abstract ScheduleDayEntity day(String owner, String dayKey);

    // saving the same day again replaces the row and makes it the newest
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void put(ScheduleDayEntity day);

    // the storage limit: everything of this account except its `max` newest days is deleted
    @Query("DELETE FROM schedule_days WHERE owner = :owner AND dayKey NOT IN "
            + "(SELECT dayKey FROM schedule_days WHERE owner = :owner ORDER BY savedAt DESC LIMIT :max)")
    public abstract void keepNewest(String owner, int max);

    @Query("SELECT COUNT(*) FROM schedule_days WHERE owner = :owner")
    public abstract int count(String owner);

    // a transaction, so the new day and the cleanup happen together or do not happen
    @Transaction
    public void save(ScheduleDayEntity day, int max) {
        put(day);
        keepNewest(day.owner, max);
    }
}
