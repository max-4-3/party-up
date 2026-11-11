package me.ocv.partyup.room.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import me.ocv.partyup.models.UploadStatus;
import me.ocv.partyup.room.dao.UploadStatusDao;

@Database(entities = {UploadStatus.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract UploadStatusDao uploadStatusDao();

    private static AppDatabase appDatabase;

    public static void init(Context context) {
        appDatabase = Room.databaseBuilder(context, AppDatabase.class, "app_database").build();
    }

    @NonNull
    public static AppDatabase getAppDatabase() {
        return appDatabase;
    }
}
