package me.ocv.partyup.views;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import me.ocv.partyup.R;
import me.ocv.partyup.databinding.UploadStatusElementBinding;
import me.ocv.partyup.models.UploadStatus;

public final class UploadStatusView extends LinearLayout {
    private UploadStatusElementBinding statusElementBinding;

    public UploadStatusView(Context context) {
        super(context);
        init(context);
    }

    public UploadStatusView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public UploadStatusView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public static String formatTimestamp(long timestamp) {
        return new SimpleDateFormat("dd MMM, yyyy hh:mm a", Locale.getDefault()).format(new Date(timestamp));
    }

    public static String formatDuration(long start, long end) {
        long diff = Math.abs(end - start);

        long hours = TimeUnit.MILLISECONDS.toHours(diff);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(diff) % 60;
        long millis = diff % 1000;

        return String.format(Locale.getDefault(), "%02dH %02dM %02dS.%03dmS", hours, minutes, seconds, millis
        );
    }

    private void init(Context context) {
        statusElementBinding = UploadStatusElementBinding.inflate(LayoutInflater.from(context), this, true);
        reset();
    }

    /**
     * Public method to set or update the UploadStatus view.
     * Pass null to reset the view.
     *
     * @param uploadStatus instance of UploadStatus
     * @param onStop       callback when Stop button is clicked
     * @param onPause      callback when Pause button is clicked
     * @param onRemove     callback when Remove button is clicked
     */
    public void setUploadStatus(@Nullable UploadStatus uploadStatus, @Nullable Consumer<UploadStatus> onStop, @Nullable Consumer<UploadStatus> onPause, @Nullable Consumer<UploadStatus> onRemove) {
        if (statusElementBinding == null) {
            Log.e("StatusView", "Binding is null");
            return;
        }

        if (uploadStatus == null) {
            reset();
            return;
        }

        setTitle(uploadStatus.title);
        setProgress(uploadStatus.getPercentage());
        setStatus(uploadStatus.status, uploadStatus.statusText);
        setStats(uploadStatus);

        setOnStop(onStop, uploadStatus);
        setOnPause(onPause, uploadStatus);
        setOnRemove(onRemove, uploadStatus);
        setInfoDialog(uploadStatus.statusText);
    }

    private void setOnRemove(@Nullable Consumer<UploadStatus> onRemove, @NonNull UploadStatus status) {
        boolean enabled = (onRemove != null);
        statusElementBinding.uploadActionRemove.setEnabled(enabled);
        if (enabled) {
            statusElementBinding.uploadActionRemove.setOnClickListener(v -> onRemove.accept(status));
        } else {
            statusElementBinding.uploadActionRemove.setOnClickListener(null);
        }
    }

    private void setOnStop(@Nullable Consumer<UploadStatus> onStop, @NonNull UploadStatus status) {
        boolean enabled = (onStop != null);
        statusElementBinding.uploadActionStop.setEnabled(enabled);
        if (enabled) {
            statusElementBinding.uploadActionStop.setOnClickListener(v -> onStop.accept(status));
        } else {
            statusElementBinding.uploadActionStop.setOnClickListener(null);
        }
    }

    private void setOnPause(@Nullable Consumer<UploadStatus> onPause, @NonNull UploadStatus status) {
        boolean enabled = (onPause != null);
        statusElementBinding.uploadActionPause.setEnabled(enabled);
        if (enabled) {
            statusElementBinding.uploadActionPause.setOnClickListener(v -> onPause.accept(status));
        } else {
            statusElementBinding.uploadActionPause.setOnClickListener(null);
        }
    }

    private void setInfoDialog(String fullText) {
        boolean hasText = !TextUtils.isEmpty(fullText);
        statusElementBinding.uploadActionInfo.setEnabled(hasText);

        if (hasText) {
            statusElementBinding.uploadActionInfo.setOnClickListener(v -> new AlertDialog.Builder(getContext()).setTitle("Server Status Details").setMessage(fullText).setPositiveButton("Close", (dialog, which) -> dialog.dismiss()).show());
        } else {
            statusElementBinding.uploadActionInfo.setOnClickListener(null);
        }
    }

    private void setStats(@NonNull UploadStatus uploadStatus) {
        statusElementBinding.uploadBytesDone.setText(String.valueOf(uploadStatus.done));
        statusElementBinding.uploadBytesTotal.setText(String.valueOf(uploadStatus.total));
        statusElementBinding.uploadBytesLeft.setText(String.valueOf(Math.max(0, uploadStatus.total - uploadStatus.done)));

        statusElementBinding.uploadTimeStarted.setText(formatTimestamp(uploadStatus.startedWhen));
        statusElementBinding.uploadTimeElapsed.setText(formatDuration(System.currentTimeMillis(), uploadStatus.startedWhen));
    }

    private void setStatus(UploadStatus.Status status, String statusText) {
        if (status == null) {
            return;
        }

        int colorResId;
        switch (status) {
            case COMPLETED:
                colorResId = R.color.teal_completed;
                break;
            case RUNNING:
                colorResId = R.color.teal_running;
                break;
            case FAILED:
                colorResId = R.color.orange_failed;
                break;
            case ENQUEUED:
            default:
                colorResId = R.color.teal_enqueued;
                break;
        }

        ColorStateList color = ContextCompat.getColorStateList(getContext(), colorResId);
        if (color != null) {
            statusElementBinding.uploadProgress.setProgressTintList(color);
        }

        if (!TextUtils.isEmpty(statusText)) {
            statusElementBinding.uploadStatusText.setText(statusText);
        } else {
            statusElementBinding.uploadStatusText.setText(status.name());
        }
    }

    private void setTitle(@NonNull String title) {
        statusElementBinding.uploadTitle.setText(!TextUtils.isEmpty(title) ? title : "unnamed_file");
    }

    private void setProgress(float progress) {
        int p = Math.max(0, Math.min(100, Math.round(progress)));

        statusElementBinding.uploadProgress.setProgress(p);
        statusElementBinding.uploadProgress.setMax(100);
        statusElementBinding.uploadPercentage.setText(String.format(Locale.getDefault(), "%.2f%%", progress));
    }

    public void reset() {
        statusElementBinding.uploadProgress.setProgress(0);
        statusElementBinding.uploadProgress.setMax(100);
        statusElementBinding.uploadTitle.setText("");
        statusElementBinding.uploadPercentage.setText("");
        statusElementBinding.uploadStatusText.setText("Idle...");
        statusElementBinding.uploadBytesDone.setText("0");
        statusElementBinding.uploadBytesTotal.setText("0");
        statusElementBinding.uploadBytesLeft.setText("0");
        statusElementBinding.uploadTimeStarted.setText("-");
        statusElementBinding.uploadTimeElapsed.setText("-");
        statusElementBinding.uploadActionPause.setOnClickListener(null);
        statusElementBinding.uploadActionStop.setOnClickListener(null);
        statusElementBinding.uploadActionInfo.setOnClickListener(null);
    }
}
