package me.ocv.partyup.room.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import me.ocv.partyup.models.UploadInfo;
import me.ocv.partyup.models.UploadStatus;
import me.ocv.partyup.room.dao.UploadDao;

@Database(entities = {UploadStatus.class, UploadInfo.class}, version = 2)
public abstract class AppDatabase extends RoomDatabase {
    public abstract UploadDao uploadStatusDao();

    private static AppDatabase appDatabase;

    public static void init(Context context) {
        appDatabase = Room.databaseBuilder(context, AppDatabase.class, "app_database").addMigrations(new Migration(1, 2) {
            @Override
            public void migrate(@NonNull SupportSQLiteDatabase db) {
                // Create the new upload_info table with its foreign key constraint
                db.execSQL("CREATE TABLE IF NOT EXISTS `upload_info` (" + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " + "`status_id` INTEGER NOT NULL, " + "`uri` TEXT, " + "`size` INTEGER NOT NULL, " + "`content_type` TEXT, " + "`file_name` TEXT, " + "`content` TEXT, " + "FOREIGN KEY(`status_id`) REFERENCES `upload_status`(`id`) ON DELETE CASCADE)");

                // Create the index on status_id for performance
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_upload_info_status_id` ON `upload_info` (`status_id`)");
            }
        }).build();
    }

    @NonNull
    public static AppDatabase getAppDatabase() {
        return appDatabase;
    }
}
