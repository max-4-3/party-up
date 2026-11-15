package me.ocv.partyup;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
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
import java.util.stream.Collectors;

import me.ocv.partyup.databinding.ActivityUploadInfoBinding;
import me.ocv.partyup.models.UploadStatus;
import me.ocv.partyup.models.pojo.UploadWithInfo;
import me.ocv.partyup.objects.CustomFile;
import me.ocv.partyup.room.repo.UploadRepository;
import me.ocv.partyup.utils.PostUploadActions;
import me.ocv.partyup.utils.ToastUtils;
import me.ocv.partyup.utils.Uploader;
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
            void bind(UploadStatusView ignoredView, UploadStatus ignoredStatus);
        }

        public interface ItemLongClickListener {
            void onLongClick(UploadStatus ignoredItem, int ignoredPosition);
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

    private static final String STATUS_IDS = "status_ids";

    private ActivityUploadInfoBinding binding;
    private UploadStatusAdapter adapter;
    private WorkManager manager;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final UploadRepository repository = UploadRepository.getInstance();

    private final Set<UploadStatus> selectedItems = new HashSet<>();
    private final Set<Long> targetStatusIds = new HashSet<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityUploadInfoBinding.inflate(getLayoutInflater());
        manager = WorkManager.getInstance(UploadInfoActivity.this);
        setContentView(binding.getRoot());

        adapter = new UploadStatusAdapter(this::bindStatusView, this::handleLongClick);
        binding.uploadLists.setLayoutManager(new LinearLayoutManager(this));
        binding.uploadLists.setAdapter(adapter);

        List<Long> statusIds = getStatusIdsFromIntent();
        if (statusIds != null && !statusIds.isEmpty()) {
            targetStatusIds.addAll(statusIds);
            Log.d("UploadInfo", "Filtering for specific target IDs: " + targetStatusIds);
        } else {
            targetStatusIds.clear();
            Log.d("UploadInfo", "No target IDs found in intent. Showing all statuses.");
        }

        repository.liveStatus().observe(this, new Observer<>() {
            private final List<UploadStatus> mStatuses = new ArrayList<>();

            @Override
            public void onChanged(List<UploadStatus> statuses) {
                if (statuses == null) {
                    return;
                } else {
                    setStatuses(statuses);
                }

                adapter.replaceAll(mStatuses);

                int r = countStatus(UploadStatus.Status.RUNNING);
                int f = countStatus(UploadStatus.Status.FAILED);
                int c = countStatus(UploadStatus.Status.COMPLETED);
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
                mStatuses.clear();

                Log.d("UploadInfo", String.format("Filtering based on: %s", targetStatusIds.stream().map(String::valueOf).collect(Collectors.joining(","))));

                if (targetStatusIds.isEmpty()) {
                    mStatuses.addAll(statuses);
                } else {
                    for (UploadStatus status : statuses) {
                        if (targetStatusIds.contains(status.id)) {
                            Log.d("UploadInfo", String.format("%s is present!", status.id));
                            mStatuses.add(status);
                        }
                    }
                }

                mStatuses.sort(Comparator.comparingLong((UploadStatus s) -> s.startedWhen).reversed());
            }

            private int countStatus(UploadStatus.Status expected) {
                return count(u -> u.status == expected);
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
            if (selectedItems.isEmpty()) {
                new AlertDialog.Builder(UploadInfoActivity.this).setTitle("How to share?").setMessage("Long press on any item under Uploads to select it.\nThen you'll be able so make a share link out of the selected items!").setPositiveButton("Okay", (d, w) -> d.dismiss()).show();
                return;
            }

            executor.execute(() -> {
                try {
                    String shareUrl = getShareUrl(selectedItems);
                    if (shareUrl == null || TextUtils.isEmpty(shareUrl)) {
                        throw new IllegalArgumentException("generated share url must not be null");
                    }

                    Map<String, Consumer<String>> actions = createActions();
                    runOnUiThread(() -> showMenu(actions, shareUrl, binding.fabBatchActions));
                } catch (Exception e) {
                    runOnUiThread(() -> new AlertDialog.Builder(UploadInfoActivity.this).setTitle("Share creation failed").setMessage(String.format("Creation of share failed for following:\n%s\nReason: \n%s", selectedItems.stream().map(i -> i.title).collect(Collectors.joining("\n")), e.getMessage())).show());
                } finally {
                    runOnUiThread(() -> {
                        adapter.clearSelection();
                        selectedItems.clear();
                    });
                }
            });
        });
    }

    @NonNull
    private Map<String, Consumer<String>> createActions() {
        Map<String, Consumer<String>> actions = new LinkedHashMap<>();

        actions.put("Show QR", link -> PostUploadActions.create(UploadInfoActivity.this, adapter::clearSelection).showQRLink(link));
        actions.put("Copy Link", link -> PostUploadActions.create(UploadInfoActivity.this, adapter::clearSelection).copyLink(link));
        actions.put("Share Link", link -> PostUploadActions.create(UploadInfoActivity.this, adapter::clearSelection).shareLink(link));
        return actions;
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
    private String getShareUrl(@NonNull Iterable<UploadStatus> statuses) throws Exception {
        List<CustomFile> files = new ArrayList<>();
        for (UploadStatus status: statuses) {
            UploadWithInfo info = repository.uploadOf(status.id);
            if (info != null) {
                files.add(info.toCustomFile());
            }
        }

        return Uploader.getShareUrl(files);
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

    @Nullable
    private List<Long> getStatusIdsFromIntent() {
        Intent intent = getIntent();
        long[] ids = intent.getLongArrayExtra(STATUS_IDS);
        if (ids == null || ids.length == 0) {
            return null;
        }

        List<Long> _ids = new ArrayList<>();
        for (long id : ids) {
            if (id >= 0) {
                _ids.add(id);
            }
        }

        return !_ids.isEmpty() ? _ids : null;
    }

    public static void startFor(Iterable<Long> statusIds, Context context) {
        if (context == null) {
            return;
        }
        Intent intent = new Intent(context, UploadInfoActivity.class);

        if (statusIds != null) {
            final String TAG = "UploadInfo";

            List<Long> _statusIds = new ArrayList<>();
            int totalCount = 0;

            for (Long id : statusIds) {
                totalCount++;
                if (id != null && id >= 0) {
                    _statusIds.add(id);
                } else {
                    Log.w(TAG, "Skipped invalid or negative status ID: " + id);
                }
            }

            Log.d(TAG, "Filtered status IDs: " + _statusIds.size() + " valid out of " + totalCount + " total");

            if (!_statusIds.isEmpty()) {
                long[] ids = new long[_statusIds.size()];
                for (int i = 0; i < _statusIds.size(); ++i) {
                    ids[i] = _statusIds.get(i);
                }

                intent.putExtra(STATUS_IDS, ids);
                Log.d(TAG, "Successfully attached " + ids.length + " status IDs to intent");
            } else {
                Log.w(TAG, "No valid status IDs to add to intent");
            }
        }

        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        context.startActivity(intent);
    }
}
