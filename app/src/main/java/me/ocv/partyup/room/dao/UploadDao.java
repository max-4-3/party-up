package me.ocv.partyup.room.dao;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

import me.ocv.partyup.models.UploadInfo;
import me.ocv.partyup.models.UploadStatus;
import me.ocv.partyup.models.pojo.UploadWithInfo;

@Dao
public interface UploadDao {
    @Query("select * from upload_status;")
    List<UploadStatus> getStatusAll();

    @Query("select * from upload_status where id is :id;")
    UploadStatus getStatusById(long id);

    @Query("select * from upload_info where id is :id;")
    UploadInfo getInfoById(long id);

    @Query("select * from upload_status where id is :id;")
    LiveData<UploadStatus> getStatusByIdLive(long id);

    @Delete(entity = UploadStatus.class)
    void removeStatus(UploadStatus status);

    @Insert(entity = UploadStatus.class, onConflict = OnConflictStrategy.REPLACE)
    long insertStatus_(UploadStatus status);

    @Update(entity = UploadStatus.class)
    void updateStatus_(UploadStatus status);

    default void insertStatus(@NonNull UploadStatus status) {
        status.id = insertStatus_(status);
    }

    default void updateStatus(@NonNull UploadStatus status) {
        updateStatus_(status);
    }

    @Insert(entity = UploadInfo.class, onConflict = OnConflictStrategy.REPLACE)
    long insertInfo_(UploadInfo info);

    @Update(entity = UploadInfo.class)
    void updateInfo_(UploadInfo info);

    default void insertInfo(@NonNull UploadInfo info) {
        info.id = insertInfo_(info);
    }

    default void updateInfo(@NonNull UploadInfo info) {
        updateInfo_(info);
    }

    @Transaction
    default void update(@NonNull UploadStatus status, @NonNull UploadInfo info) {
        updateStatus(status);
        updateInfo(info);
    }

    @Transaction
    default void insert(@NonNull UploadStatus status, @NonNull UploadInfo info) {
        insertStatus(status);
        info.statusId = status.id;
        insertInfo(info);
    }

    @Transaction
    @Query("select * from upload_status where id is :statusId")
    UploadWithInfo getUploadWithInfoById(long statusId);
}
