package me.ocv.partyup.room.dao;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import me.ocv.partyup.models.UploadStatus;

@Dao
public interface UploadStatusDao {
    @Query("select * from upload_status;")
    List<UploadStatus> getAll();

    @Query("select * from upload_status where id is :id;")
    UploadStatus getById(long id);

    @Query("select * from upload_status where id is :id;")
    LiveData<UploadStatus> getByIdLive(long id);

    @Delete(entity = UploadStatus.class)
    void remove(UploadStatus status);

    @Insert(entity = UploadStatus.class, onConflict = OnConflictStrategy.REPLACE)
    long insert_(UploadStatus status);

    @Update(entity = UploadStatus.class)
    void update_(UploadStatus status);

    default void insert(@NonNull UploadStatus status) {
        status.id = insert_(status);
    }

    default void update(@NonNull UploadStatus status) {
        update_(status);
    }
}
