package com.example.parchapp.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface HelpDao {
    @Query("SELECT * FROM help_faq ORDER BY screen, position")
    List<HelpFaqEntity> all();

    // MATCH uses the full-text index, it does not walk every row like LIKE '%text%' does
    @Query("SELECT * FROM help_faq WHERE help_faq MATCH :query ORDER BY position")
    List<HelpFaqEntity> search(String query);

    @Query("SELECT COUNT(*) FROM help_faq")
    int count();

    @Query("DELETE FROM help_faq")
    void clear();

    @Insert
    void insertAll(List<HelpFaqEntity> rows);

    // a new download replaces the whole list in one transaction, so the screen never sees half of it
    @Transaction
    default void replaceAll(List<HelpFaqEntity> rows) {
        clear();
        insertAll(rows);
    }

    // generic fallback: the check and the insert in one transaction, so a Wi-Fi download that finishes
    // in between is not overwritten by the copy inside the app
    @Transaction
    default void seedIfEmpty(List<HelpFaqEntity> rows) {
        if (count() == 0) insertAll(rows);
    }
}
