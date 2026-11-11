package me.ocv.partyup.room.dao;

import androidx.annotation.NonNull;
import androidx.room3.Dao;
import androidx.room3.Insert;
import androidx.room3.OnConflictStrategy;
import androidx.room3.Query;
import androidx.room3.Update;

import java.util.List;

import me.ocv.partyup.models.UploadStatus;

@Dao
public interface UploadStatusDao {
    @Query("select * from upload_status;")
    List<UploadStatus> getAll();

    @Query("select * from upload_status where id = :id;")
    UploadStatus getById(long id);

    @Insert(entity = UploadStatus.class, onConflict = OnConflictStrategy.REPLACE)
    void insert_(@NonNull UploadStatus status);

    @Update(entity = UploadStatus.class)
    void update_(@NonNull UploadStatus status);

    default long insert(@NonNull UploadStatus status) {
        insert_(status);
        return status.id;
    }

    default long update(@NonNull UploadStatus status) {
        update_(status);
        return status.id;
    }
}
