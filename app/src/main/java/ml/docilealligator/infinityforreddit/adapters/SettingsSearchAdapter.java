package ml.docilealligator.infinityforreddit.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.settings.SettingsSearchItem;
import ml.docilealligator.infinityforreddit.settings.SettingsSearchRegistry;

public class SettingsSearchAdapter extends RecyclerView.Adapter<SettingsSearchAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(SettingsSearchItem item);
    }

    private final OnItemClickListener mListener;
    private List<SettingsSearchItem> mFilteredItems = new ArrayList<>();

    public SettingsSearchAdapter(OnItemClickListener listener) {
        mListener = listener;
    }

    public void filter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        List<SettingsSearchItem> all = SettingsSearchRegistry.getInstance().getItems();
        if (q.isEmpty()) {
            mFilteredItems = new ArrayList<>(all);
        } else {
            List<SettingsSearchItem> result = new ArrayList<>();
            for (SettingsSearchItem item : all) {
                if (item.title.toLowerCase().contains(q)
                        || (item.summary != null && item.summary.toLowerCase().contains(q))
                        || item.breadcrumb.toLowerCase().contains(q)) {
                    result.add(item);
                }
            }
            mFilteredItems = result;
        }
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return mFilteredItems.isEmpty();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_settings_search, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SettingsSearchItem item = mFilteredItems.get(position);
        holder.titleView.setText(item.title);
        holder.breadcrumbView.setText(item.breadcrumb);
        holder.itemView.setOnClickListener(v -> mListener.onItemClick(item));
    }

    @Override
    public int getItemCount() {
        return mFilteredItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView titleView;
        final TextView breadcrumbView;

        ViewHolder(View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.title_item_settings_search);
            breadcrumbView = itemView.findViewById(R.id.breadcrumb_item_settings_search);
        }
    }
}
