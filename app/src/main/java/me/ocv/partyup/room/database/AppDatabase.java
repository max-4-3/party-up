package me.ocv.partyup.room.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import me.ocv.partyup.models.UploadInfo;
import me.ocv.partyup.models.UploadStatus;
import me.ocv.partyup.room.dao.UploadDao;

@Database(entities = {UploadStatus.class, UploadInfo.class}, version = 3)
public abstract class AppDatabase extends RoomDatabase {
    public abstract UploadDao uploadStatusDao();

    private static AppDatabase appDatabase;

    public static void init(Context context) {
        appDatabase = DatabaseMigrations.apply(Room.databaseBuilder(context, AppDatabase.class, "app_database")).build();
    }

    @NonNull
    public static AppDatabase getAppDatabase() {
        return appDatabase;
    }
}
