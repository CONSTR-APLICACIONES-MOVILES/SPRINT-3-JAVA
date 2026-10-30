package com.example.parchapp.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

// strategy 7, local storage: relational database of the app (room over sqlite)
// one instance for the whole app, opening a database is slow and two instances could fight for the file
@Database(entities = {GroupDraftEntity.class, DraftInviteEntity.class}, version = 1, exportSchema = false)
public abstract class ParchDatabase extends RoomDatabase {
    private static volatile ParchDatabase instance;

    public abstract GroupDraftDao groupDrafts();

    public static ParchDatabase get(Context context) {
        if (instance == null) {
            synchronized (ParchDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                            ParchDatabase.class, "parchapp.db").build();
                }
            }
        }
        return instance;
    }
}
