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
@Database(entities = {GroupDraftEntity.class, DraftInviteEntity.class, ScheduleDayEntity.class,
        AndesEventEntity.class, ParticipantCacheEntity.class, ParticipantOutboxEntity.class,
        ActivityDraftEntity.class}, version = 5, exportSchema = false)
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

    // version 3 adds Andes Events (cached catalog, saved events and the add outbox); drafts and days stay
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `andes_events` (`owner` TEXT NOT NULL, `list` TEXT NOT NULL, "
                    + "`eventId` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, "
                    + "`category` TEXT NOT NULL, `location` TEXT NOT NULL, `startAt` INTEGER NOT NULL, "
                    + "`endAt` INTEGER NOT NULL, `sourceUrl` TEXT, `storedAt` INTEGER NOT NULL, `status` TEXT, "
                    + "`error` TEXT, `retryCount` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`owner`, `list`, `eventId`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_andes_events_owner_list_startAt` "
                    + "ON `andes_events` (`owner`, `list`, `startAt`)");
        }
    };

    // version 4 adds the participant snapshot cache and the offline participant outbox
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `participant_cache` (`activityId` TEXT NOT NULL, "
                    + "`json` TEXT NOT NULL, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`activityId`))");
            db.execSQL("CREATE TABLE IF NOT EXISTS `participant_outbox` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`activityId` TEXT NOT NULL, `action` TEXT NOT NULL, `userId` TEXT, "
                    + "`expectedVersion` TEXT NOT NULL, `status` TEXT NOT NULL, `error` TEXT, "
                    + "`createdAt` INTEGER NOT NULL)");
        }
    };

    // version 5 adds the saved activity drafts and their synchronization metadata
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `activity_drafts` (`owner` TEXT NOT NULL, "
                    + "`id` TEXT NOT NULL, `revision` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, "
                    + "`category` TEXT NOT NULL, `state` TEXT NOT NULL, `payload` TEXT, "
                    + "`remoteVersion` INTEGER NOT NULL, `pending` INTEGER NOT NULL, "
                    + "`operationId` TEXT NOT NULL, `syncError` TEXT, `requestKey` TEXT, "
                    + "PRIMARY KEY(`owner`, `id`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_drafts_owner_state_updatedAt` "
                    + "ON `activity_drafts` (`owner`, `state`, `updatedAt`)");
        }
    };

    public abstract GroupDraftDao groupDrafts();

    public abstract AndesEventsDao andesEvents();

    public abstract ScheduleDayDao scheduleDays();

    public abstract ParticipantCacheDao participantCache();

    public abstract ParticipantOutboxDao participantOutbox();

    public abstract ActivityDraftsDao activityDrafts();

    public static ParchDatabase get(Context context) {
        if (instance == null) {
            synchronized (ParchDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    ParchDatabase.class, "parchapp.db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                            .build();
                }
            }
        }
        return instance;
    }
}
