package me.ocv.partyup.work;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.jetbrains.annotations.Contract;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import me.ocv.partyup.models.UploadStatus;
import me.ocv.partyup.models.pojo.UploadWithInfo;
import me.ocv.partyup.objects.PrefsKey;
import me.ocv.partyup.room.repo.UploadRepository;
import me.ocv.partyup.utils.FileCacheUtility;
import me.ocv.partyup.utils.Uploader;

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

        boolean isTextFile = upload.info.contentType.startsWith("text") && !upload.info.textContent.isEmpty();
        if (!isTextFile && upload.info.handle.isEmpty()) {
            throw new IllegalStateException("UploadWork needs to be in proper state, file handle must not be empty for a non text file");
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

            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            AtomicReference<Throwable> error = new AtomicReference<>();

            Uploader uploader = new Uploader.Builder(getApplicationContext()).setServerUrl(preferences.getString(PrefsKey.SERVER_URL, "")).setServerPassword(preferences.getString(PrefsKey.SERVER_PASSWORD, "")).setOnComplete(response -> selected.complete(cleanResponse(response))).setOnProgress(progress -> {
                selected.done(progress.done);
                if (isStopped()) {
                    throw new IllegalStateException("Worker was cancelled");
                }
            }).setOnError(err -> {
                selected.fail(err);
                error.set(err);
            }).build();

            uploader.upload(selected.repository.peek().toCustomFile());
            Throwable e = error.get();
            if (e != null) {
                throw e;
            }

            if (!selected.completed() || selected.repository.peek().status.status != UploadStatus.Status.COMPLETED) {
                selected.done(selected.repository.peek().status.total);
                selected.complete("Upload Completed, I guess!");
            }

            return Result.success();
        } catch (Throwable tr) {
            if (selected != null) {
                selected.fail(tr);
            }
            return Result.failure();
        } finally {
            if (selected != null) {
                Uri fileUri = selected.repository.peek().info.getUri();
                if (FileCacheUtility.isCacheFile(getApplicationContext(), fileUri) && fileUri.getPath() != null) {
                    File cacheFile = new File(fileUri.getPath());
                    if (cacheFile.exists()) {
                        if (cacheFile.delete()) {
                            Log.d("UploadWork", "Cache file deleted: " + cacheFile.getName());
                        }
                    }
                }
            }
        }
    }

    @NonNull
    public static String cleanResponse(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        // control characters, zero-width spaces, and invalid non-printable bytes
        String cleaned = raw.replaceAll("[\\p{C}]", "").trim();

        // truncate or extract just the URL/first line to keep the status bar readable.
        if (cleaned.length() > 256) {
            if (cleaned.contains("http://") || cleaned.contains("https://")) {
                int httpIndex = cleaned.indexOf("http");
                int spaceIndex = cleaned.indexOf(" ", httpIndex);
                return spaceIndex != -1 ? cleaned.substring(httpIndex, spaceIndex) : cleaned.substring(httpIndex);
            }

            return cleaned.substring(0, 256) + "...";
        }
        return cleaned;
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

        public void complete(String reason) {
            repository.status(UploadStatus.Status.COMPLETED, reason);
            hook.accept(repository.peek());
        }

        public void fail(Throwable tr) {
            repository.status(UploadStatus.Status.FAILED, String.format("Error occurred: %s", tr != null ? tr.getMessage() : "Unexpected scenario!"));
            hook.accept(repository.peek());
        }

        public void done(long done) {
            repository.done(done);
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
