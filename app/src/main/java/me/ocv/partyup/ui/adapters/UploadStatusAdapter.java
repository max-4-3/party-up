package me.ocv.partyup.ui.adapters;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.ocv.partyup.models.UploadStatus;
import me.ocv.partyup.ui.views.UploadStatusView;

public final class UploadStatusAdapter extends ListAdapter<UploadStatus, UploadStatusAdapter.ViewHolder> {

    private static final DiffUtil.ItemCallback<UploadStatus> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull UploadStatus a, @NonNull UploadStatus b) {
            return a.id == b.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull UploadStatus a, @NonNull UploadStatus b) {
            return a.equals(b);
        }
    };

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
    private final Set<Long> selectedIds = new HashSet<>();

    public UploadStatusAdapter(Binder binder, ItemLongClickListener longClickListener) {
        super(DIFF);
        this.binder = binder;
        this.longClickListener = longClickListener;
        setHasStableIds(true);
    }

    public void replaceAll(List<UploadStatus> newStatuses) {
        submitList(new ArrayList<>(newStatuses)); // must be a new list instance
    }

    public void toggleSelection(long statusId) {
        if (!selectedIds.remove(statusId)) {
            selectedIds.add(statusId);
        }
        notifyItemChangedById(statusId);
    }

    public boolean isSelected(long statusId) {
        return selectedIds.contains(statusId);
    }

    public void clearSelection() {
        Set<Long> previous = new HashSet<>(selectedIds);
        selectedIds.clear();
        List<UploadStatus> list = getCurrentList();
        for (int i = 0; i < list.size(); i++) {
            if (previous.contains(list.get(i).id)) {
                notifyItemChanged(i);
            }
        }
    }

    private void notifyItemChangedById(long id) {
        List<UploadStatus> list = getCurrentList();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == id) {
                notifyItemChanged(i);
                return;
            }
        }
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
        UploadStatus status = getItem(position);

        binder.bind(holder.view, status);
        holder.itemView.setSelected(isSelected(status.id));

        holder.view.setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) {
                return false;
            }
            longClickListener.onLongClick(getItem(pos), pos);
            return true;
        });
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).id;
    }
}
