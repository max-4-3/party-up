package me.ocv.partyup;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import me.ocv.partyup.databinding.ActivityTempReceiverBinding;
import me.ocv.partyup.objects.CustomFile;
import me.ocv.partyup.utils.Analyzer;
import me.ocv.partyup.utils.FileCache;
import me.ocv.partyup.utils.NumberUtils;
import me.ocv.partyup.utils.ToastUtils;
import me.ocv.partyup.views.InfoKeyValuePairView;
import me.ocv.partyup.work.WorkUtility;

public final class TempReceiver extends AppCompatActivity {
    private static final String TAG = "TempReceiver";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ActivityTempReceiverBinding binding;
    private WorkManager workManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTempReceiverBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.main);

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.uploadConfirmServerUrl.setText(Config.string(Config.Keys.SERVER_URL));
        workManager = WorkManager.getInstance(this);
        List<CustomFile> files = getFilesAndShow();

        if (Config.bool(Config.Keys.AUTOSEND)) {
            toast("Auto sending...");

            binding.uploadConfirmUploadBtn.setEnabled(false);
            binding.uploadConfirmCancelBtn.setEnabled(false);

            doUpload(files);
        } else {
            binding.uploadConfirmUploadBtn.setEnabled(true);
            binding.uploadConfirmUploadBtn.setOnClickListener(v -> {
                binding.uploadConfirmCancelBtn.setEnabled(false);
                doUpload(files);
            });

            binding.uploadConfirmCancelBtn.setEnabled(true);
            binding.uploadConfirmCancelBtn.setOnClickListener(v -> {
                binding.uploadConfirmUploadBtn.setEnabled(false);
                finish();
            });
        }
    }

    private void doUpload(List<CustomFile> files) {
        executor.execute(() -> {
            List<Long> statusIds = uploadFiles(files);
            Log.d("TempReceiver", String.format("received status ids: %s", statusIds.stream().map(String::valueOf).collect(Collectors.joining(","))));

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                showUploadInfo(statusIds);
                toast(String.format("Enqueued %s files!", files.size()));
                binding.uploadConfirmUploadBtn.setEnabled(false);
            });
        });
    }

    @NonNull
    private View getSeparator() {
        View separator = new View(this);
        separator.setBackgroundColor(Color.WHITE);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4));

        params.setMargins(dp(2), dp(4), dp(2), dp(4));

        separator.setLayoutParams(params);
        return separator;
    }

    @NonNull
    private List<CustomFile> getFilesAndShow() {
        List<CustomFile> files = getFilesFromIntent();
        if (!files.isEmpty()) {
            binding.uploadConfirmStats.addView(getSeparator());
        }

        files.forEach(file -> {
            showStat(file);
            binding.uploadConfirmStats.addView(getSeparator());
        });

        return files;
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private void toast(String msg) {
        ToastUtils.show(this, msg);
    }

    private void showStat(CustomFile file) {
        if (file == null || !file.isValid()) {
            return;
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("Name", file.name);
        info.put("Size", String.format("%s (%s)", NumberUtils.formatBytes(file.size), file.size));
        info.put("Mime", file.mime);

        if (file.isText()) {
            info.put("Content", file.content);
        }

        info.put("Handle", file.handle);
        info.put("Cached", FileCache.isCacheFile(TempReceiver.this, file.handle) ? "yes" : "no");

        runOnUiThread(() -> info.forEach((key, value) -> {
            InfoKeyValuePairView view = new InfoKeyValuePairView(this);
            view.setInfo(key, value.toString());

            binding.uploadConfirmStats.addView(view);
        }));
    }

    @NonNull
    @WorkerThread
    private List<Long> uploadFiles(@NonNull List<CustomFile> files) {
        List<String> enqueued = new LinkedList<>();
        List<Long> statusIds = new ArrayList<>();

        files.forEach(file -> {
            WorkUtility.UploadRequest request = WorkUtility.enqueueWork(file);
            statusIds.add(request.status.id);
            enqueued.add(request.request.getId().toString());

            workManager.enqueue(request.request);
        });

        enqueued.forEach(id -> toast(String.format("Enqueued '%s'", id)));

        return statusIds;
    }

    @UiThread
    private void showUploadInfo(List<Long> statusIds) {
        UploadInfoActivity.startFor(statusIds, this);
    }

    @NonNull
    private List<CustomFile> getFilesFromIntent() {
        List<CustomFile> files = new ArrayList<>();

        for (CustomFile file : Analyzer.analyze(this, getIntent())) {
            Log.w(TAG, String.format("parsed file: %s", file.toString()));

            if (file.isValid()) {
                files.add(file);
            }
        }

        return files;
    }
}