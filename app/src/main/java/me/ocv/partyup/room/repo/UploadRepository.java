package me.ocv.partyup.room.repo;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import org.jetbrains.annotations.Contract;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.ocv.partyup.models.UploadInfo;
import me.ocv.partyup.models.UploadStatus;
import me.ocv.partyup.models.pojo.UploadWithInfo;
import me.ocv.partyup.room.dao.UploadDao;
import me.ocv.partyup.room.database.AppDatabase;

public final class UploadRepository {
    public static final String TAG = "UploadStatusRepo";
    public static final long THROTTLE_INTERVAL_MS = 1000L;

    private final Map<Long, Long> lastUpdateTime = new ConcurrentHashMap<>();
    private final UploadDao uploadDao;

    public LiveData<List<UploadStatus>> liveStatus() {
        return uploadDao.getLiveStatusAll();
    }

    public UploadInfo infoOf(long id) {
        UploadInfo info = uploadDao.getInfoById(id);
        Log.d(TAG, String.format("info of '%d': %s", id, info));

        return info;
    }

    public UploadWithInfo uploadOf(long statusId) {
        UploadWithInfo upload = uploadDao.getUploadWithInfoById(statusId);
        Log.d(TAG, String.format("status+info of '%d': %s", statusId, upload));

        return upload;
    }

    @NonNull
    public LiveData<UploadStatus> liveStatusOf(long id) {
        LiveData<UploadStatus> status = uploadDao.getStatusByIdLive(id);
        Log.d(TAG, String.format("status of '%d': %s", id, status.getValue()));

        return status;
    }

    public void remove(UploadStatus status) {
        uploadDao.removeStatus(status);
        Log.d(TAG, String.format("[%s] removing status: %s", status.id, status));
    }

    public void addAndJoin(UploadStatus status, UploadInfo info) {
        uploadDao.insert(status, info);
    }

    public void update(UploadWithInfo upload) {
        uploadDao.update(upload.status, upload.info);
        Log.d(TAG, String.format("[%s,%s] updating upload: %s", upload.status.id, upload.info.id, upload));
    }

    public long update(UploadStatus status) {
        uploadDao.updateStatus(status);
        Log.d(TAG, String.format("[%s] updating status: %s", status.id, status));

        return status.id;
    }

    public void updateDone(@NonNull UploadStatus status, long done) {
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
            update(status);
        }

    }

    public void updateStatus(@NonNull UploadStatus uploadStatus, UploadStatus.Status status, String reason) {
        uploadStatus.status = status;
        uploadStatus.statusText = reason;

        lastUpdateTime.remove(uploadStatus.id);
        update(uploadStatus);
    }

    public final class FocusedRepository {
        private final UploadWithInfo upload;

        private FocusedRepository(UploadWithInfo status) {
            this.upload = status;
        }

        public void start() {
            upload.status.startedWhen = System.currentTimeMillis();

            update(upload);
        }

        public void status(UploadStatus.Status status, String reason) {
            updateStatus(upload.status, status, reason);
        }

        public void done(long done) {
            updateDone(upload.status, done);
        }

        public void increment(long count) {
            if (count < 0) {
                throw new IllegalArgumentException("Negative increment");
            }
            done((upload.status.done + count) % Long.MAX_VALUE);
        }

        public void links(String shareUrl, String fullUrl) {
            upload.info.shareUrl = shareUrl;
            upload.info.fullUrl = fullUrl;

            update(upload);
        }

        @NonNull
        @Contract(value = " -> new", pure = true)
        public UploadWithInfo peek() {
            return upload.snapshot();
        }

        public boolean completed() {
            return upload.status.done >= upload.status.total;
        }
    }
    @NonNull
    @Contract(value = "_ -> new", pure = true)
    public FocusedRepository focusOn(UploadWithInfo upload) {
        return new FocusedRepository(upload);
    }

    private UploadRepository(@NonNull AppDatabase appDatabase) {
        uploadDao = appDatabase.uploadStatusDao();
    }

    @NonNull
    public static UploadRepository getInstance() {
        return new UploadRepository(AppDatabase.getAppDatabase());
    }
}
