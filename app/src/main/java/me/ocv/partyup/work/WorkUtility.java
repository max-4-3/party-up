package me.ocv.partyup.work;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkRequest;

import me.ocv.partyup.models.UploadStatus;
import me.ocv.partyup.room.repo.UploadStatusRepository;

public final class WorkUtility {
    public static final class UploadRequest {
        public final WorkRequest request;
        public final UploadStatus status;

        public UploadRequest(WorkRequest request, UploadStatus status) {
            this.request = request;
            this.status = status;
        }
    }

    @NonNull
    public static UploadRequest enqueueWork(String title, long total) {
        UploadStatus uploadStatus = UploadStatusRepository.getInstance().createStatus(title, total);

        return new UploadRequest(new OneTimeWorkRequest.Builder(UploadWork.class).setInputData(new Data.Builder().putLong(UploadWork.STATUS_ID, uploadStatus.id).build()).setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build(), uploadStatus);
    }

    @NonNull
    public static Data progressData(UploadStatus uploadStatus) {
        if (uploadStatus != null) {
            return new Data.Builder().putLong("id", uploadStatus.id).putLong("done", uploadStatus.done).putLong("total", uploadStatus.total).build();
        } else {
            return new Data.Builder().putLong("id", -1).putLong("done", 0).putLong("total", 0).build();
        }
    }

    private WorkUtility() {}
}
