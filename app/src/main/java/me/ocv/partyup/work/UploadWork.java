package me.ocv.partyup.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.jetbrains.annotations.Contract;

import java.util.Random;
import java.util.function.Consumer;

import me.ocv.partyup.models.UploadStatus;
import me.ocv.partyup.room.repo.UploadStatusRepository;

public final class UploadWork extends Worker {
    public static final String STATUS_ID = "status_id";

    public UploadWork(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        statusRepository = UploadStatusRepository.getInstance();
        setProgressAsync(WorkUtility.progressData(null));
    }

    private final UploadStatusRepository statusRepository;
    private Selected selected;

    private void init() {
        long id = getInputData().getLong(STATUS_ID, -1);
        if (id == -1) {
            throw new IllegalArgumentException("UploadWork input missing the `status_id`");
        }

        UploadStatus uploadStatus = statusRepository.statusOf(id);
        if (uploadStatus == null || uploadStatus.total == 0 || uploadStatus.total < uploadStatus.done) {
            throw new IllegalStateException("UploadWork need proper status");
        }

        selected = Selected.select(statusRepository, uploadStatus, this::updateProgress);
        updateProgress(uploadStatus);
    }

    @NonNull
    @Override
    public Result doWork() {
        init();
        selected.start();

        try {
            Random random = new Random();
            long limit = selected.repository.peek().total;

            while (!selected.completed()) {
                long value = random.nextLong() & Long.MAX_VALUE;
                value = 1 + (value % limit);

                selected.step(value / (limit / 4));
                Thread.sleep(10);
            }

            selected.complete();
            return Result.success();
        } catch (Throwable tr) {
            selected.fail(tr);
            return Result.failure();
        }
    }

    private static final class Selected {
        private final UploadStatusRepository.FocusedRepository repository;
        private final Consumer<UploadStatus> hook;

        private Selected(UploadStatusRepository.FocusedRepository repository, Consumer<UploadStatus> hook) {
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
        private static Selected select(@NonNull UploadStatusRepository repository, UploadStatus uploadStatus, Consumer<UploadStatus> postTransactionHook) {
            return new Selected(repository.focusOn(uploadStatus), postTransactionHook);
        }
    }


    private void updateProgress(UploadStatus status) {
        setProgressAsync(WorkUtility.progressData(status));
    }
}
