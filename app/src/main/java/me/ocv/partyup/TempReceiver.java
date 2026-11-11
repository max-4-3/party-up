package me.ocv.partyup;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.work.WorkManager;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.ocv.partyup.objects.CustomFile;
import me.ocv.partyup.utils.Analyzer;
import me.ocv.partyup.utils.ToastUtils;
import me.ocv.partyup.work.WorkUtility;

public class TempReceiver extends AppCompatActivity {
    public static final String TAG = "TempReceiver";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_temp_receiver);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        CustomFile[] files = new Analyzer(this).analyze(getIntent(), msg -> Log.e(TAG, String.format("Error while parsing intent: %s", msg)));
        WorkManager workManager = WorkManager.getInstance(this);
        for (CustomFile file : files) {
            Log.w(TAG, String.format("parsed file: %s", file.toString()));
            if (file.name == null || file.size == null) {
                continue;
            }

            executor.execute(() -> {
                String msg = String.format(Locale.getDefault(), "Enqueued '%s'(%d): %s", file.name, file.size, workManager.enqueue(WorkUtility.enqueueWork(file).request));

                Log.d(TAG, msg);
                runOnUiThread(() -> ToastUtils.show(this, msg));
            });
        }

        startActivity(new Intent(this, UploadInfoActivity.class));
    }
}