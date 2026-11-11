package me.ocv.partyup;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import me.ocv.partyup.databinding.ActivityUploadInfoBinding;
import me.ocv.partyup.models.UploadStatus;
import me.ocv.partyup.views.UploadStatusView;

public class UploadInfoActivity extends AppCompatActivity {
    public static final String EXTRA_UPLOAD_STATUS = "upload_status";
    ActivityUploadInfoBinding binding;

    public static void start(Context context, UploadStatus uploadStatus) {
        Intent intent = new Intent(context, UploadInfoActivity.class);
        intent.putExtra(EXTRA_UPLOAD_STATUS, uploadStatus);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final UploadStatus status = UploadStatus.sample();

        binding = ActivityUploadInfoBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());

        Log.d("UploadInfoActivity", String.format("Showing: %s", status));
        UploadStatusView statusView = new UploadStatusView(this);
        statusView.setUploadStatus(status);

        statusView.setOnClickListener(v -> {
            status.done++;
            if (status.total < status.done) {
                status.total += 20;
            }

            statusView.setUploadStatus(status);
        });

        binding.getRoot().addView(statusView);
    }

    @Nullable
    private UploadStatus getUploadStatusFromIntent() {
        UploadStatus status = null;
        Intent intent = getIntent();

        if (intent == null) return status;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            status = intent.getParcelableExtra(EXTRA_UPLOAD_STATUS, UploadStatus.class);
        } else {
            status = intent.getParcelableExtra(EXTRA_UPLOAD_STATUS);
        }

        return status;
    }
}
