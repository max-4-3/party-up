package me.ocv.partyup.room.repo;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.ocv.partyup.models.UploadStatus;
import me.ocv.partyup.room.dao.UploadStatusDao;
import me.ocv.partyup.room.database.AppDatabase;

public final class UploadStatusRepository {
    public static final String TAG = "UploadStatusRepo";
    public static final long THROTTLE_INTERVAL_MS = 1000L;

    private final Map<Long, Long> lastUpdateTime = new ConcurrentHashMap<>();
    private final UploadStatusDao statusDao;

    public UploadStatus statusOf(long id) {
        UploadStatus status = statusDao.getById(id);
        Log.d(TAG, String.format("status of '%d': %s", id, status));

        return status;
    }

    @NonNull
    public LiveData<UploadStatus> liveStatusOf(long id) {
        LiveData<UploadStatus> status = statusDao.getByIdLive(id);
        Log.d(TAG, String.format("status of '%d': %s", id, status.getValue()));

        return status;
    }

    @NonNull
    public UploadStatus createStatus(String title, long total) {
        UploadStatus uploadStatus = new UploadStatus(0, title, 0, total, 0, "Work Created", UploadStatus.Status.ENQUEUED, "");
        Log.d(TAG, String.format("status before add: %s", uploadStatus));
        long _id = add(uploadStatus);
        Log.d(TAG, String.format("id received from add: %s", _id));

        uploadStatus.id = _id;
        Log.d(TAG, String.format("status after add: %s", uploadStatus));

        return uploadStatus;
    }

    @NonNull
    public List<UploadStatus> all() {
        List<UploadStatus> status = statusDao.getAll();
        if (status == null) {
            status = new ArrayList<>();
        }

        Log.d(TAG, String.format("total status: %d", status.size()));

        return status;
    }

    public long remove(UploadStatus status) {
        statusDao.remove(status);
        Log.d(TAG, String.format("[%s] removing status: %s", status.id, status));

        return status.id;
    }

    public long add(UploadStatus status) {
        statusDao.insert(status);
        Log.d(TAG, String.format("[%s] inserting status: %s", status.id, status));

        return status.id;
    }

    public long update(UploadStatus status) {
        statusDao.update(status);
        Log.d(TAG, String.format("[%s] updating status: %s", status.id, status));

        return status.id;
    }

    public long updateDone(@NonNull UploadStatus status, long done) {
        if (done < 0 || done > status.total) {
            Log.w(TAG, String.format("Suspicious progress: id=%d, done=%d, total=%d", status.id, done, status.total));
        }

        if (status.total < 0) {
            throw new IllegalStateException("Negative total: " + status.total);
        }

        status.done = Math.max(0L, Math.min(status.total, done));

        long curr = System.currentTimeMillis();

        Long lastB = lastUpdateTime.get(status.id);
        long last = lastB != null ? lastB : 0L;

        boolean isCompleted = status.done >= status.total;

        if (isCompleted || curr - last >= THROTTLE_INTERVAL_MS) {
            lastUpdateTime.put(status.id, curr);
            return update(status);
        }

        return status.id;
    }

    public long updateStatus(@NonNull UploadStatus uploadStatus, UploadStatus.Status status, String reason) {
        uploadStatus.status = status;
        uploadStatus.statusText = reason;

        lastUpdateTime.remove(uploadStatus.id);
        return update(uploadStatus);
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
            if (count < 0) {
                throw new IllegalArgumentException("Negative increment");
            }
            done((uploadStatus.done + count) % Long.MAX_VALUE);
        }

        @NonNull
        @Contract(value = " -> new", pure = true)
        public UploadStatus peek() {
            return uploadStatus.snapshot();
        }

        public boolean completed() {
            return uploadStatus.done >= uploadStatus.total;
        }
    }
    @NonNull
    @Contract(value = "_ -> new", pure = true)
    public FocusedRepository focusOn(UploadStatus status) {
        return new FocusedRepository(status);
    }

    private UploadStatusRepository(@NonNull AppDatabase appDatabase) {
        statusDao = appDatabase.uploadStatusDao();
    }

    @NonNull
    public static UploadStatusRepository getInstance() {
        return new UploadStatusRepository(AppDatabase.getAppDatabase());
    }
}
