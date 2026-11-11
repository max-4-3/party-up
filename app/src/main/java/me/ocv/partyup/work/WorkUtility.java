package me.ocv.partyup.work;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkRequest;

import me.ocv.partyup.models.UploadInfo;
import me.ocv.partyup.models.UploadStatus;
import me.ocv.partyup.objects.CustomFile;
import me.ocv.partyup.room.repo.UploadRepository;

public final class WorkUtility {
    public static final class UploadRequest {
        public final WorkRequest request;
        public final UploadStatus status;
        public final UploadInfo info;

        public UploadRequest(WorkRequest request, UploadStatus status, UploadInfo info) {
            this.request = request;
            this.status = status;
            this.info = info;
        }
    }

    @NonNull
    public static UploadRequest enqueueWork(CustomFile customFile) {
        String title = customFile.name;
        long total = customFile.size;
        String handle = customFile.handle.toString();
        String mime = customFile.mime;

        UploadInfo info = new UploadInfo(0, 0, handle, total, mime, title, customFile.content);
        UploadStatus status = new UploadStatus(0, title, 0, total, 0, "Idle...", UploadStatus.Status.ENQUEUED, "");

        UploadRepository.getInstance().addAndJoin(status, info);

        WorkRequest request = new OneTimeWorkRequest.Builder(UploadWork.class).setInputData(new Data.Builder().putLong(UploadWork.STATUS_ID, status.id).putLong(UploadWork.INFO_ID, info.id).build()).setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();

        status.workerId = request.getId().toString();
        UploadRepository.getInstance().update(status);

        return new UploadRequest(request, status, info);
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
