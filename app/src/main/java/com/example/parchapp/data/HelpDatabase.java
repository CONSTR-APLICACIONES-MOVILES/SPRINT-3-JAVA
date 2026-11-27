package com.example.parchapp.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

// local storage: Help has its own Room database, so the team's ParchDatabase is not touched
// it starts at version 1 and needs no migration; if the table changes, raise the version and add the migration here
@Database(entities = {HelpFaqEntity.class}, version = 1, exportSchema = false)
public abstract class HelpDatabase extends RoomDatabase {
    private static volatile HelpDatabase instance;

    public abstract HelpDao help();

    public static HelpDatabase get(Context context) {
        if (instance == null) {
            synchronized (HelpDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                            HelpDatabase.class, "parchapp_help.db").build();
                }
            }
        }
        return instance;
    }
}
