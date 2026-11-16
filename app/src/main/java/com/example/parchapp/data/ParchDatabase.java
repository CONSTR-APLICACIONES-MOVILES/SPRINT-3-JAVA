package com.example.parchapp.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

// strategy 7, local storage: relational database of the app (room over sqlite)
// one instance for the whole app, opening a database is slow and two instances could fight for the file
@Database(entities = {GroupDraftEntity.class, DraftInviteEntity.class, ScheduleDayEntity.class},
        version = 2, exportSchema = false)
public abstract class ParchDatabase extends RoomDatabase {
    private static volatile ParchDatabase instance;

    // version 2 adds the saved Schedule days; a migration and not a destructive rebuild, so the group drafts stay
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `schedule_days` (`owner` TEXT NOT NULL, `dayKey` TEXT NOT NULL, "
                    + "`json` TEXT NOT NULL, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`owner`, `dayKey`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_days_owner_savedAt` "
                    + "ON `schedule_days` (`owner`, `savedAt`)");
        }
    };

    public abstract GroupDraftDao groupDrafts();

    public abstract ScheduleDayDao scheduleDays();

    public static ParchDatabase get(Context context) {
        if (instance == null) {
            synchronized (ParchDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    ParchDatabase.class, "parchapp.db")
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return instance;
    }
}
