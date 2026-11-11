package me.ocv.partyup.work;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.jetbrains.annotations.Contract;

import java.util.function.Consumer;

import me.ocv.partyup.models.UploadStatus;
import me.ocv.partyup.models.pojo.UploadWithInfo;
import me.ocv.partyup.room.repo.UploadRepository;

public final class UploadWork extends Worker {
    public static final String STATUS_ID = "status_id";
    public static final String INFO_ID = "info_id";

    public UploadWork(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        statusRepository = UploadRepository.getInstance();
        setProgressAsync(WorkUtility.progressData(null));
    }

    private final UploadRepository statusRepository;
    private Selected selected;

    private void init() {
        long statusId = getInputData().getLong(STATUS_ID, -1);
        long infoId = getInputData().getLong(INFO_ID, -1);

        if (statusId == -1 && infoId == -1) {
            throw new IllegalArgumentException("UploadWork input missing both `status_id` and `info_id`");
        } else if (statusId == -1) {
            statusId = statusRepository.infoOf(infoId).statusId;
        }

        UploadWithInfo upload = statusRepository.uploadOf(statusId);
        selected = Selected.select(statusRepository, upload, this::updateProgress);

        if (upload.status == null || upload.status.total == 0 || upload.status.total < upload.status.done) {
            throw new IllegalStateException("UploadWork need proper status");
        }

        if (upload.status.status != UploadStatus.Status.ENQUEUED) {
            throw new IllegalStateException("UploadWork needs to be in proper state");
        }

        if (upload.info.handle.isEmpty()) {
            throw new IllegalStateException("UploadWork needs to be in proper state, file handle must not be empty");
        }

        updateProgress(upload);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            init();
            selected.start();
            Log.d("WORKER", String.format("Upload: %s", selected.repository.peek()));

            while (!selected.completed()) {
                if (isStopped()) {
                    selected.fail(new InterruptedException("Worker was cancelled"));
                    return Result.failure();
                }

                selected.step(10);
                Thread.sleep(6000);
            }

            selected.complete();
            return Result.success();
        } catch (Throwable tr) {
            if (selected != null) {
                selected.fail(tr);
            }
            return Result.failure();
        }
    }

    @Override
    public void onStopped() {
        super.onStopped();
        if (selected != null && !selected.completed()) {
            selected.fail(new IllegalStateException("Worker stopped or terminated!"));
        }
    }

    private static final class Selected {
        private final UploadRepository.FocusedRepository repository;
        private final Consumer<UploadWithInfo> hook;

        private Selected(UploadRepository.FocusedRepository repository, Consumer<UploadWithInfo> hook) {
            this.repository = repository;
            this.hook = hook;
        }

        public void start() {
            repository.start();
            repository.status(UploadStatus.Status.RUNNING, "Work is starting to run...");

            hook.accept(repository.peek());
        }

        public void complete() {
            repository.status(UploadStatus.Status.COMPLETED, "Upload Succeeded");
            hook.accept(repository.peek());
        }

        public void fail(Throwable tr) {
            repository.status(UploadStatus.Status.FAILED, String.format("Error occurred: %s", tr != null ? tr.getMessage() : "Unexpected scenario!"));
            hook.accept(repository.peek());
        }

        public void step(long count) {
            repository.increment(count);
            hook.accept(repository.peek());
        }

        public boolean completed() {
            return repository.completed();
        }

        @NonNull
        @Contract(value = "_, _, _ -> new")
        private static Selected select(@NonNull UploadRepository repository, UploadWithInfo upload, Consumer<UploadWithInfo> postTransactionHook) {
            return new Selected(repository.focusOn(upload), postTransactionHook);
        }
    }

    private void updateProgress(UploadWithInfo upload) {
        setProgressAsync(WorkUtility.progressData(upload.status));
    }
}
