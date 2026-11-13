package me.ocv.partyup.views;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import me.ocv.partyup.R;
import me.ocv.partyup.databinding.UploadStatusElementBinding;
import me.ocv.partyup.models.UploadStatus;
import me.ocv.partyup.utils.NumberUtils;
import me.ocv.partyup.utils.ToastUtils;

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

    @NonNull
    public static String formatTimestamp(long timestamp) {
        return new SimpleDateFormat("dd MMM, yyyy hh:mm a", Locale.getDefault()).format(new Date(timestamp));
    }

    @NonNull
    public static String formatDuration(long start, long end) {
        long diff = Math.abs(end - start);

        long hours = TimeUnit.MILLISECONDS.toHours(diff);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(diff) % 60;
        long millis = diff % 1000;

        return String.format(Locale.getDefault(), "%02dH %02dM %02dS.%03dmS", hours, minutes, seconds, millis);
    }

    @NonNull
    private static String formatBytes(long bytes) {
        return String.format("%s (%s)", NumberUtils.formatBytes(bytes), bytes);
    }

    private void init(Context context) {
        statusElementBinding = UploadStatusElementBinding.inflate(LayoutInflater.from(context), this, true);
        reset();
    }

    public void setSelected(boolean selected) {
        if (selected) {
            statusElementBinding.getRoot().setBackgroundColor(ContextCompat.getColor(getContext(), R.color.container_selected_background));
        } else {
            statusElementBinding.getRoot().setBackgroundColor(Color.TRANSPARENT);
        }
    }

    /**
     * Public method to set or updateStatus the UploadStatus view.
     * Pass null to reset the view.
     *
     * @param uploadStatus instance of UploadStatus
     * @param onStop       callback when Stop button is clicked
     * @param onPause      callback when Pause button is clicked
     * @param onRemove     callback when Remove button is clicked
     */
    public void setUploadStatus(@Nullable UploadStatus uploadStatus,
                                @Nullable Consumer<UploadStatus> onStop,
                                @Nullable Consumer<UploadStatus> onPause,
                                @Nullable Consumer<UploadStatus> onRemove) {
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
        setStatus(uploadStatus.status);
        setStats(uploadStatus);

        Map<String, Consumer<UploadStatus>> moreActions = new LinkedHashMap<>();
        moreActions.put("Info", u -> {
            if (u.statusText == null || TextUtils.isEmpty(u.statusText)) return;

            new AlertDialog.Builder(getContext())
                    .setTitle("Server response")
                    .setMessage(u.statusText)
                    .setPositiveButton("Copy", (d, i) -> {
                        copyToClipboard(u.statusText);
                        d.dismiss();
                    })
                    .setNegativeButton("Dismiss", (d, i) -> d.dismiss())
                    .create()
                    .show();
        });
        moreActions.put("Remove", onRemove);

        setOnStop(onStop, uploadStatus);
        setOnPause(onPause, uploadStatus);
        setMoreActions(moreActions, uploadStatus);

        syncButtons(uploadStatus);
    }

    private void toast() {
        statusElementBinding.getRoot().post(() -> ToastUtils.show(getContext(), "Copied to clipboard!"));
    }

    private void setMoreActions(Map<String, Consumer<UploadStatus>> moreActions, UploadStatus status) {
        statusElementBinding.uploadActionMore.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(getContext(), v);
            for (String name: moreActions.keySet()) {
                menu.getMenu().add(name);
            }

            menu.setOnMenuItemClickListener(item -> {
                CharSequence title = item.getTitle();
                if (title != null && title.length() > 0) {
                    Consumer<UploadStatus> action = moreActions.get(title.toString());
                    if (action != null) {
                        action.accept(status);
                        return true;
                    }
                }

                return false;
            });

            menu.show();
        });
    }

    private void syncButtons(@NonNull UploadStatus uploadStatus) {
        boolean pause = true;
        boolean stop = true;

        if (uploadStatus.isCompleted()) {
            pause = stop = false;
        } else if (uploadStatus.isErrored() && uploadStatus.isFinished()) {
            stop = pause = false;
        }

        statusElementBinding.uploadActionPause.setEnabled(pause);
        statusElementBinding.uploadActionStop.setEnabled(stop);
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

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Server Response", text));

        toast();
    }

    private void setStats(@NonNull UploadStatus uploadStatus) {
        statusElementBinding.uploadBytesDone.setText(formatBytes(uploadStatus.done));
        statusElementBinding.uploadBytesTotal.setText(formatBytes(uploadStatus.total));
        statusElementBinding.uploadBytesLeft.setText(formatBytes(Math.max(0, uploadStatus.total - uploadStatus.done)));

        statusElementBinding.uploadTimeStarted.setText(formatTimestamp(uploadStatus.startedWhen));
        statusElementBinding.uploadTimeElapsed.setText(formatDuration(System.currentTimeMillis(), uploadStatus.startedWhen));
    }

    private void setStatus(UploadStatus.Status status) {
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

        statusElementBinding.uploadStatusText.setText(status.name());
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
        statusElementBinding.uploadStatusText.setText("");
        statusElementBinding.uploadBytesDone.setText("0");
        statusElementBinding.uploadBytesTotal.setText("0");
        statusElementBinding.uploadBytesLeft.setText("0");
        statusElementBinding.uploadTimeStarted.setText("-");
        statusElementBinding.uploadTimeElapsed.setText("-");
        statusElementBinding.uploadActionPause.setOnClickListener(null);
        statusElementBinding.uploadActionStop.setOnClickListener(null);
        statusElementBinding.uploadActionMore.setOnClickListener(null);
        statusElementBinding.getRoot().setBackgroundColor(Color.TRANSPARENT);
    }
}
