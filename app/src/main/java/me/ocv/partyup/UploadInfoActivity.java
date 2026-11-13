package me.ocv.partyup;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Predicate;

import me.ocv.partyup.databinding.ActivityUploadInfoBinding;
import me.ocv.partyup.models.UploadStatus;
import me.ocv.partyup.room.repo.UploadRepository;
import me.ocv.partyup.utils.PostUploadActions;
import me.ocv.partyup.utils.ToastUtils;
import me.ocv.partyup.views.UploadStatusView;

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

        public interface ItemLongClickListener {
            void onLongClick(UploadStatus item, int position);
        }

        private final Binder binder;
        private final ItemLongClickListener longClickListener;
        private final List<UploadStatus> statuses = new ArrayList<>();
        private final Set<Long> selectedIds = new HashSet<>();

        public UploadStatusAdapter(Binder binder, ItemLongClickListener longClickListener) {
            this.binder = binder;
            this.longClickListener = longClickListener;
            setHasStableIds(true);
        }

        public void toggleSelection(long statusId) {
            if (selectedIds.contains(statusId)) {
                selectedIds.remove(statusId);
            } else {
                selectedIds.add(statusId);
            }
        }

        public boolean isSelected(long statusId) {
            return selectedIds.contains(statusId);
        }

        @SuppressLint("NotifyDataSetChanged")
        public void clearSelection() {
            selectedIds.clear();
            notifyDataSetChanged();
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
            UploadStatus status = statuses.get(position);

            binder.bind(holder.view, status);
            holder.itemView.setSelected(isSelected(status.id));

            holder.view.setOnLongClickListener(v -> {
                longClickListener.onLongClick(status, position);
                return true;
            });
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

    private final Set<UploadStatus> selectedItems = new HashSet<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityUploadInfoBinding.inflate(getLayoutInflater());
        manager = WorkManager.getInstance(UploadInfoActivity.this);
        setContentView(binding.getRoot());

        adapter = new UploadStatusAdapter(this::bindStatusView, this::handleLongClick);
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

        binding.fabBatchActions.setOnClickListener(v -> {
            if (selectedItems.isEmpty()) return;

            executor.execute(() -> {
                String shareUrl = getShareUrl(selectedItems);
                if (shareUrl == null || TextUtils.isEmpty(shareUrl)) return;

                Map<String, Consumer<String>> actions = new LinkedHashMap<>();

                actions.put("Show QR", link -> PostUploadActions.create(UploadInfoActivity.this, adapter::clearSelection).showQRLink(link));
                actions.put("Copy Link", link -> PostUploadActions.create(UploadInfoActivity.this, adapter::clearSelection).copyLink(link));
                actions.put("Share Link", link -> PostUploadActions.create(UploadInfoActivity.this, adapter::clearSelection).shareLink(link));

                runOnUiThread(() -> showMenu(actions, shareUrl, binding.fabBatchActions));
            });
        });
    }

    private <T> void showMenu(Map<String, Consumer<T>> actions, T data, View anchor) {
        if (data == null) return;

        PopupMenu menu = new PopupMenu(UploadInfoActivity.this, anchor);
        for (String name: actions.keySet()) {
            menu.getMenu().add(name);
        }

        menu.setOnMenuItemClickListener(item -> {
            CharSequence title = item.getTitle();
            if (title != null && title.length() > 0) {
                Consumer<T> action = actions.get(title.toString());
                if (action != null) {
                    action.accept(data);
                    return true;
                }
            }

            return false;
        });

        menu.show();
    }

    @Nullable
    private String getShareUrl(Iterable<UploadStatus> statuses) {
        // TODO: Implement the function
        return null;
    }

    private void handleLongClick(UploadStatus uploadStatus, int position) {
        if (uploadStatus == null || position < 0) return;

        if (selectedItems.contains(uploadStatus)) {
            selectedItems.remove(uploadStatus);
        } else {
            selectedItems.add(uploadStatus);
        }

        adapter.toggleSelection(uploadStatus.id);
        adapter.notifyItemChanged(position);
        updateFabVisibility();
    }

    private void updateFabVisibility() {
        if (selectedItems.isEmpty()) {
            binding.fabBatchActions.hide();
            binding.fabBatchActions.setVisibility(View.GONE);
        } else {
            binding.fabBatchActions.show();
            binding.fabBatchActions.setVisibility(View.VISIBLE);
        }
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
}
