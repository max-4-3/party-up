package me.ocv.partyup.room.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.Contract;

import java.util.List;

import me.ocv.partyup.models.UploadStatus;
import me.ocv.partyup.room.dao.UploadStatusDao;
import me.ocv.partyup.room.database.AppDatabase;

public final class UploadStatusRepository {
    private final UploadStatusDao statusDao;

    public UploadStatus statusOf(long id) {
        return statusDao.getById(id);
    }

    @NonNull
    public UploadStatus createStatus(String title, long total) {
        UploadStatus uploadStatus = new UploadStatus(0, title, 0, total, 0, "Work Created", UploadStatus.Status.ENQUEUED);

        uploadStatus.id = add(uploadStatus);
        return uploadStatus;
    }

    public List<UploadStatus> all() {
        return statusDao.getAll();
    }

    public long add(UploadStatus status) {
        return statusDao.insert(status);
    }

    public long update(UploadStatus status) {
        return statusDao.update(status);
    }

    public final class FocusedRepository {
        private final UploadStatus uploadStatus;

        private FocusedRepository(UploadStatus status) {
            this.uploadStatus = status;
        }

        public void start() {
            uploadStatus.startedWhen = System.currentTimeMillis();

            update(uploadStatus);
        }

        public long status(UploadStatus.Status status, String reason) {
            return updateStatus(uploadStatus, status, reason);
        }

        public long done(long done) {
            return updateDone(uploadStatus, done);
        }

        public void increment(long count) {
            done(uploadStatus.done + count);
        }

        public boolean completed() {
            return uploadStatus.done >= uploadStatus.total;
        }
    }

    public long updateDone(@NonNull UploadStatus uploadStatus, long done) {
        uploadStatus.done = Math.min(uploadStatus.total, done);

        return statusDao.update(uploadStatus);
    }

    public long updateStatus(@NonNull UploadStatus uploadStatus, UploadStatus.Status status, String reason) {
        uploadStatus.status = status;
        uploadStatus.statusText = reason;

        return statusDao.update(uploadStatus);
    }

    @NonNull
    @Contract(value = "_ -> new", pure = true)
    public FocusedRepository focusOn(UploadStatus status) {
        return new FocusedRepository(status);
    }

    @Nullable
    public FocusedRepository focusOn(long id) {
        UploadStatus status = statusOf(id);
        return status != null ? new FocusedRepository(status) : null;
    }

    private UploadStatusRepository(@NonNull AppDatabase appDatabase) {
        statusDao = appDatabase.uploadStatusDao();
    }

    @NonNull
    public static UploadStatusRepository getInstance() {
        return new UploadStatusRepository(AppDatabase.getAppDatabase());
    }
}
