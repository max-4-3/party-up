package me.ocv.partyup.work;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.jetbrains.annotations.Contract;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import me.ocv.partyup.R;
import me.ocv.partyup.UploadInfoActivity;
import me.ocv.partyup.models.UploadStatus;
import me.ocv.partyup.models.pojo.UploadWithInfo;
import me.ocv.partyup.objects.CustomFile;
import me.ocv.partyup.room.repo.UploadRepository;
import me.ocv.partyup.utils.FileCache;
import me.ocv.partyup.utils.Uploader;

public final class UploadWork extends Worker {
    public static final String STATUS_ID = "status_id";
    public static final String INFO_ID = "info_id";
    private static final String CHANNEL_ID = "upload_status";
    private static final String RESULT_CHANNEL_ID = "upload_results";
    private static final int NOTIFICATION_DELAY_MS = 1500;

    public UploadWork(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);

        statusRepository = UploadRepository.getInstance();
        notificationManager = context.getSystemService(NotificationManager.class);

        createChannel();
        setProgressAsync(WorkUtility.progressData(null));
    }

    private final NotificationManager notificationManager;
    private final UploadRepository statusRepository;
    private Selected selected;

    private boolean foregroundStarted;
    private int lastPercent = -1;
    private long lastNotifyMs;

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

            AtomicReference<Throwable> error = new AtomicReference<>();
            Uploader uploader = Uploader.fromConfig(getApplicationContext(), progress -> {
                selected.done(progress.done);
                if (isStopped()) {
                    throw new IllegalStateException("Worker was cancelled");
                }
            }, err -> {
                selected.fail(err);
                error.set(err);
            }, response -> selected.complete(cleanResponse(response)));

            CustomFile customFile = selected.repository.peek().toCustomFile();
            uploader.uploadAndUpdateFile(customFile); // this should have set share_url, full_url
            selected.repository.links(customFile.share_url, customFile.full_url);

            Throwable e = error.get();
            if (e != null) {
                throw e;
            }

            if (selected.notCompleted() || selected.repository.peek().status.status != UploadStatus.Status.COMPLETED) {
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
                notifyResult(selected.repository.peek());

                Uri fileUri = selected.repository.peek().info.getUri();
                if (FileCache.isCacheFile(getApplicationContext(), fileUri) && fileUri.getPath() != null) {
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
        String cleaned = raw.replaceAll("\\p{C}", "").trim();

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
        if (selected != null && selected.notCompleted()) {
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

        public boolean notCompleted() {
            return !repository.completed();
        }

        @NonNull
        @Contract(value = "_, _, _ -> new")
        private static Selected select(@NonNull UploadRepository repository, UploadWithInfo upload, Consumer<UploadWithInfo> postTransactionHook) {
            return new Selected(repository.focusOn(upload), postTransactionHook);
        }
    }

    private void updateProgress(@NonNull UploadWithInfo upload) {
        setProgressAsync(WorkUtility.progressData(upload.status));
        notifyProgress(upload);
    }

    private void notifyResult(@NonNull UploadWithInfo upload) {
        Context ctx = getApplicationContext();
        UploadStatus status = upload.status;

        if (!status.isCompleted() && !status.isErrored()) {
            return;
        }
        String title = String.format("Upload %s", status.isCompleted() ? "completed" : "failed");
        String text = String.format("%s (%s)", upload.info.fileName, upload.info.handle);

        Intent open = UploadInfoActivity.createIntent(new long[]{upload.status.id}, ctx);
        PendingIntent pi = PendingIntent.getActivity(ctx, (int) status.id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new NotificationCompat.Builder(ctx, RESULT_CHANNEL_ID).setSmallIcon(R.drawable.ic_upload).setContentTitle(title).setContentText(text).setContentIntent(pi).setAutoCancel(true).setOnlyAlertOnce(true).build();

        try {
            notificationManager.cancel(notificationId(upload));
            notificationManager.notify(2000 + (int) status.id, n);
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS denied on Android 13+
        }
    }

    private void notifyProgress(@NonNull UploadWithInfo upload) {
        UploadStatus status = upload.status;

        int percent = status.total > 0 ? (int) (status.done * 100 / status.total) : 0;
        long now = System.currentTimeMillis();

        boolean changed = percent != lastPercent;
        if (foregroundStarted && (!changed || now - lastNotifyMs < NOTIFICATION_DELAY_MS)) {
            return;
        }

        lastNotifyMs = now;
        lastPercent = percent;

        Context ctx = getApplicationContext();
        int id = notificationId(upload);

        Notification n = new NotificationCompat.Builder(ctx, CHANNEL_ID).setSmallIcon(R.drawable.ic_upload).setContentTitle("Worker Status").setContentText(String.format("(%s%%) %s", percent, status.title)).setSubText(status.status.name()).setProgress(100, percent, status.total == 0).setOngoing(true).setOnlyAlertOnce(true).setPriority(NotificationCompat.PRIORITY_LOW).addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", WorkManager.getInstance(ctx).createCancelPendingIntent(getId())).build();

        try {
            if (!foregroundStarted) {
                ForegroundInfo info = Build.VERSION.SDK_INT >= 29 ? new ForegroundInfo(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) : new ForegroundInfo(id, n);
                setForegroundAsync(info).addListener(() -> foregroundStarted = true, Executors.newSingleThreadExecutor());
            } else {
                notificationManager.notify(id, n);
            }
        } catch (Exception ignored) {
            // ForegroundServiceStartNotAllowedException (Android 12+), SecurityException if notifications are denied
        }
    }

    private int notificationId(@NonNull UploadWithInfo info) {
        return (int) info.status.id + 1000;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            notificationManager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Upload Status", NotificationManager.IMPORTANCE_LOW));
            notificationManager.createNotificationChannel(new NotificationChannel(RESULT_CHANNEL_ID, "Upload Results", NotificationManager.IMPORTANCE_DEFAULT));
        }
    }
}
