package me.ocv.partyup.views;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TableLayout;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

        return String.format(
                Locale.getDefault(),
                "%02dH %02dM %02dS.%03dmS",
                hours, minutes, seconds, millis
        );
    }

    private void init(Context context) {
        statusElementBinding = UploadStatusElementBinding.inflate(LayoutInflater.from(context), this, true);
    }

    /**
     * Set the upload status
     *
     * @param uploadStatus instance of UploadStatus to set, pass null to reset
     */
    public void setUploadStatus(UploadStatus uploadStatus) {
        if (statusElementBinding == null) {
            Log.e("StatusView", "Binding is null");
            return;
        }

        if (uploadStatus == null) {
            reset();
            return;
        }

        Log.e("StatusView", "Applying!!!");

        setTitle(uploadStatus.title);
        setProgress(uploadStatus.getPercentage());

//        Map<String, Object> info = new LinkedHashMap<>();
//
//        info.put("elapsed time:", formatDuration(uploadStatus.startedWhen, System.currentTimeMillis()));
//        info.put("bytes send:", uploadStatus.done);
//        info.put("bytes left:", uploadStatus.total - uploadStatus.done);
//        info.put("bytes total:", uploadStatus.total);
//        info.put("started at:", formatTimestamp(uploadStatus.startedWhen));
//        info.put("total files:", uploadStatus.files);
//        info.put("extra text:", uploadStatus.statusText);
//
//        displayMapTable(info, statusElementBinding.uploadTable);
    }

    private void displayMapTable(Map<String, Object> infoMap, TableLayout tableLayout) {
        if (infoMap == null || tableLayout == null) return;

        tableLayout.removeAllViews();
        for (Map.Entry<String, Object> entry : infoMap.entrySet()) {
            addTableRow(entry.getKey(), String.valueOf(entry.getValue()), tableLayout);
        }
    }

    private void addTableRow(String key, String value, TableLayout layout) {
        if (layout == null || TextUtils.isEmpty(key) || TextUtils.isEmpty(value)) return;

        Context context = getContext();
//        UploadStatusTableRowElementBinding rowElementBinding = UploadStatusTableRowElementBinding.inflate(LayoutInflater.from(context), layout, false);
//
//        rowElementBinding.uploadTableKey.setText(key);
//        rowElementBinding.uploadTableValue.setText(value);
//
//        layout.addView(rowElementBinding.getRoot());
    }

    /**
     * Set title of current status
     *
     * @param title the title to be set, if empty (or blank) sets to "unnamed_file"
     */
    private void setTitle(@NonNull String title) {
        statusElementBinding.uploadTitle.setText(!TextUtils.isEmpty(title) ? title : "unnamed_file");
    }

    /**
     * Set progress of progress bar
     *
     * @param progress a value b/w 0 and 100 (float) example: 72.2, 41, 1.2, ...
     */
    private void setProgress(float progress) {
        int p = Math.max(0, Math.min(100, Math.round(progress)));

        statusElementBinding.uploadProgress.setProgress(p);
        statusElementBinding.uploadProgress.setMax(100);
        statusElementBinding.uploadPercentage.setText(String.format("%.2f%%", progress));
    }

    private void reset() {
        statusElementBinding.uploadProgress.setProgress(0);
        statusElementBinding.uploadProgress.setMax(100);
        statusElementBinding.uploadTitle.setText("");
        statusElementBinding.uploadPercentage.setText("");
    }
}
