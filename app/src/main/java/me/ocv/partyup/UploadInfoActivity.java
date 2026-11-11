package me.ocv.partyup;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.ocv.partyup.databinding.ActivityUploadInfoBinding;
import me.ocv.partyup.models.UploadStatus;
import me.ocv.partyup.objects.CustomFile;
import me.ocv.partyup.room.repo.UploadRepository;
import me.ocv.partyup.utils.ToastUtils;
import me.ocv.partyup.views.UploadStatusView;
import me.ocv.partyup.work.WorkUtility;

public class UploadInfoActivity extends AppCompatActivity {
    public static final String EXTRA_UPLOAD_STATUS = "upload_status";

    public static final class UploadStatusAdapter extends BaseAdapter {

        public interface Binder {
            void bind(UploadStatusView view, UploadStatus status);
        }

        private final Context context;
        private final Binder binder;
        private final List<UploadStatus> statuses = new ArrayList<>();

        public UploadStatusAdapter(Context context, Binder binder) {
            this.context = context;
            this.binder = binder;
        }

        public void addOrUpdate(UploadStatus status) {
            for (int i = 0; i < statuses.size(); i++) {
                if (statuses.get(i).id == status.id) {
                    statuses.set(i, status);
                    notifyDataSetChanged();
                    return;
                }
            }

            statuses.add(status);
            notifyDataSetChanged();
        }

        public void remove(long id) {
            statuses.removeIf(status -> status.id == id);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return statuses.size();
        }

        @Override
        public UploadStatus getItem(int position) {
            return statuses.get(position);
        }

        @Override
        public long getItemId(int position) {
            return statuses.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            UploadStatusView view;

            if (convertView instanceof UploadStatusView) {
                view = (UploadStatusView) convertView;
            } else {
                view = new UploadStatusView(context);
            }

            binder.bind(view, getItem(position));
            return view;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }


    private ActivityUploadInfoBinding binding;
    private UploadStatusAdapter adapter;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Set<Long> observedStatusIds = new HashSet<>();

    private final UploadRepository repository = UploadRepository.getInstance();

    public static void start(Context context, UploadStatus uploadStatus) {
        Intent intent = new Intent(context, UploadInfoActivity.class);
        intent.putExtra(EXTRA_UPLOAD_STATUS, uploadStatus);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityUploadInfoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        adapter = new UploadStatusAdapter(this, this::bindStatusView);
        binding.uploadLists.setAdapter(adapter);

        binding.inputDone.setOnClickListener(v -> {
            String title = binding.inputTitle.getText().toString().trim();
            String totalText = binding.inputTotal.getText().toString();

            if (TextUtils.isEmpty(title) || TextUtils.isEmpty(totalText) || !TextUtils.isDigitsOnly(totalText)) {
                return;
            }

            try {
                long total = Long.parseLong(totalText);

                enqueueWork(title, total);

                binding.inputTitle.setText("");
                binding.inputTotal.setText("");

            } catch (NumberFormatException exception) {
                ToastUtils.show(this, "Invalid total (not a valid long)");
            }
        });

        loadExistingUploads();
    }

    private void loadExistingUploads() {
        executor.execute(() -> {
            List<UploadStatus> statuses = repository.allStatus();

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                for (UploadStatus status : statuses) {
                    if (status.status == UploadStatus.Status.COMPLETED || status.status == UploadStatus.Status.FAILED || status.done >= status.total) {
                        continue;
                    }

                    adapter.addOrUpdate(status);
                    observeStatus(status.id);
                }
            });
        });
    }

    private void enqueueWork(String title, long total) {
        executor.execute(() -> {
            CustomFile file = new CustomFile();
            file.name = title;
            file.size = total;
            file.handle = Uri.EMPTY;

            WorkUtility.UploadRequest request = WorkUtility.enqueueWork(file);

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                WorkManager manager = WorkManager.getInstance(this);

                manager.enqueue(request.request);

                manager.getWorkInfoByIdLiveData(request.request.getId()).observe(this, workInfo -> {
                    if (workInfo == null) {
                        return;
                    }

                    long id = workInfo.getProgress().getLong("id", -1);

                    if (id != -1) {
                        observeStatus(id);
                    }
                });
            });
        });
    }

    private void observeStatus(long id) {
        // Prevent registering duplicate observers for the same upload.
        if (!observedStatusIds.add(id)) {
            return;
        }

        repository.liveStatusOf(id).observe(this, status -> {
            if (status != null) {
                adapter.addOrUpdate(status);
            }
        });
    }

    private void bindStatusView(@NonNull UploadStatusView view, UploadStatus status) {
        WorkManager manager = WorkManager.getInstance(UploadInfoActivity.this);

        view.setUploadStatus(status,

            // First callback
            _i -> executor.execute(() -> {
                if (!TextUtils.isEmpty(status.workerId)) {
                    manager.cancelWorkById(UUID.fromString(status.workerId));
                }
            }),

            // Second callback
            __i -> ToastUtils.show(UploadInfoActivity.this, "PAUSE"),

            // Third callback
            __i -> executor.execute(() -> {
                if (__i.id >= 0) {
                    UploadRepository.getInstance().remove(__i);
                    if (!TextUtils.isEmpty(__i.workerId)) {
                        manager.cancelWorkById(UUID.fromString(__i.workerId));
                    }
                }
            }));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
