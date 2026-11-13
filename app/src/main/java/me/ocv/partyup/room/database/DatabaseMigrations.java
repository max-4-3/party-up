package me.ocv.partyup.room.database;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.room.Index;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import org.jetbrains.annotations.Contract;

public final class DatabaseMigrations {
    private DatabaseMigrations() {}

    @NonNull
    @Contract("_ -> param1")
    public static <T extends RoomDatabase> RoomDatabase.Builder<T> apply(@NonNull RoomDatabase.Builder<T> builder) {
        builder.addMigrations(MIGRATION_1_2);
        builder.addMigrations(MIGRATION_2_3);

        return builder;
    }

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // Create the new upload_info table with its foreign key constraint
            db.execSQL("CREATE TABLE IF NOT EXISTS `upload_info` (" + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " + "`status_id` INTEGER NOT NULL, " + "`uri` TEXT, " + "`size` INTEGER NOT NULL, " + "`content_type` TEXT, " + "`file_name` TEXT, " + "`content` TEXT, " + "FOREIGN KEY(`status_id`) REFERENCES `upload_status`(`id`) ON DELETE CASCADE)");

            // Create the index on status_id for performance
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_upload_info_status_id` ON `upload_info` (`status_id`)");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("alter table `upload_info` add column share_url TEXT");
            db.execSQL("alter table `upload_info` add column full_url TEXT");
        }
    };
}
