package me.ocv.partyup;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import me.ocv.partyup.databinding.ActivityUploadInfoBinding;
import me.ocv.partyup.models.UploadStatus;
import me.ocv.partyup.objects.CustomFile;
import me.ocv.partyup.room.repo.UploadRepository;
import me.ocv.partyup.utils.ToastUtils;
import me.ocv.partyup.views.UploadStatusView;
import me.ocv.partyup.work.WorkUtility;

public final class UploadInfoActivity extends AppCompatActivity {
    public static final class UploadStatusAdapter extends RecyclerView.Adapter<UploadStatusAdapter.ViewHolder> {
        public static final class ViewHolder extends RecyclerView.ViewHolder {
            final UploadStatusView view;

            public ViewHolder(@NonNull UploadStatusView itemView) {
                super(itemView);
                this.view = itemView;
            }
        }

        public interface Binder {
            void bind(UploadStatusView view, UploadStatus status);
        }

        private final Binder binder;
        private final List<UploadStatus> statuses = new ArrayList<>();

        public UploadStatusAdapter(Binder binder) {
            this.binder = binder;
            setHasStableIds(true);
        }

        public void addOrUpdate(UploadStatus status) {
            for (int i = 0; i < statuses.size(); i++) {
                if (statuses.get(i).id == status.id) {
                    statuses.set(i, status);
                    notifyItemChanged(i);
                    return;
                }
            }

            int position = statuses.size();
            statuses.add(status);
            notifyItemInserted(position);
        }

        public void remove(long statusId) {
            for (int i = 0; i < statuses.size(); ++i) {
                if (statuses.get(i).id == statusId) {
                    statuses.remove(i);
                    notifyItemRemoved(i);
                    return;
                }
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        public void replaceAll(List<UploadStatus> newStatues) {
            statuses.clear();
            statuses.addAll(newStatues);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            UploadStatusView view = new UploadStatusView(parent.getContext());

            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            binder.bind(holder.view, statuses.get(position));
        }

        @Override
        public int getItemCount() {
            return statuses.size();
        }

        @Override
        public long getItemId(int position) {
            return statuses.get(position).id;
        }
    }

    private ActivityUploadInfoBinding binding;
    private UploadStatusAdapter adapter;
    private WorkManager manager;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final UploadRepository repository = UploadRepository.getInstance();

    private final Set<Long> observedStatusIds = new HashSet<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityUploadInfoBinding.inflate(getLayoutInflater());
        manager = WorkManager.getInstance(UploadInfoActivity.this);
        setContentView(binding.getRoot());

        adapter = new UploadStatusAdapter(this::bindStatusView);
        binding.uploadLists.setLayoutManager(new LinearLayoutManager(this));
        binding.uploadLists.setAdapter(adapter);

        repository.liveStatus().observe(this, new Observer<>() {
            private List<UploadStatus> mStatuses;

            @Override
            public void onChanged(List<UploadStatus> statuses) {
                if (statuses == null) {
                    return;
                } else {
                    setStatuses(statuses);
                }

                adapter.replaceAll(mStatuses);

                int r = count(s -> s.status == UploadStatus.Status.RUNNING);
                int f = count(s -> s.status == UploadStatus.Status.FAILED);
                int c = count(s -> s.status == UploadStatus.Status.COMPLETED);
                int t = mStatuses.size();

                if (t != (r + f + c)) {
                    return;
                }

                binding.uploadStats.post(() -> {
                    binding.uploadsCompleted.setText(String.valueOf(c));
                    binding.uploadsFailed.setText(String.valueOf(f));
                    binding.uploadsRunning.setText(String.valueOf(r));
                    binding.uploadsTotal.setText(String.valueOf(r + f + c));
                });
            }

            private void setStatuses(List<UploadStatus> statuses) {
                mStatuses = new ArrayList<>(statuses);
                mStatuses.sort(Comparator.comparingLong((UploadStatus s) -> s.startedWhen).reversed());
            }

            private int count(Predicate<UploadStatus> predicate) {
                int result = 0;

                if (mStatuses != null) {
                    for (UploadStatus status : mStatuses) {
                        if (predicate.test(status)) {
                            result++;
                        }
                    }
                }

                return result;
            }
        });

    }

    private void observeStatus(long statusId) {
        if (!observedStatusIds.add(statusId)) {
            return;
        }

        repository.liveStatusOf(statusId).observe(this, status -> {
            if (status != null) {
                adapter.addOrUpdate(status);
            }
        });
    }

    private void bindStatusView(@NonNull UploadStatusView view, UploadStatus status) {
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
}
