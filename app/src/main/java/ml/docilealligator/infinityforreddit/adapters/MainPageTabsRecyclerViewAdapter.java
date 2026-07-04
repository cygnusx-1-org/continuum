package ml.docilealligator.infinityforreddit.adapters;

import android.annotation.SuppressLint;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Collections;
import java.util.List;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ItemMainPageTabBinding;
import ml.docilealligator.infinityforreddit.settings.MainPageTabInput;
import ml.docilealligator.infinityforreddit.settings.MainPageTabsUtils;

/**
 * Drag-to-reorder list of {@link MainPageTabInput}. User-added rows can be deleted; group rows
 * (injected by the "Show ..." toggles) are reorderable but not deletable here.
 */
public class MainPageTabsRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnStartDragListener {
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }

    private static final int MENU_MOVE_TOP = 1;
    private static final int MENU_MOVE_BOTTOM = 2;

    private final BaseActivity activity;
    private final CustomThemeWrapper customThemeWrapper;
    private final List<MainPageTabInput> tabs;
    private final OnStartDragListener startDragListener;
    private final Runnable onChanged;

    public MainPageTabsRecyclerViewAdapter(BaseActivity activity, CustomThemeWrapper customThemeWrapper,
                                           List<MainPageTabInput> tabs,
                                           OnStartDragListener startDragListener, Runnable onChanged) {
        this.activity = activity;
        this.customThemeWrapper = customThemeWrapper;
        this.tabs = tabs;
        this.startDragListener = startDragListener;
        this.onChanged = onChanged;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new TabViewHolder(ItemMainPageTabBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (!(holder instanceof TabViewHolder)) {
            return;
        }
        TabViewHolder tabHolder = (TabViewHolder) holder;
        MainPageTabInput tab = tabs.get(position);

        tabHolder.binding.nameTextViewItemMainPageTab.setText(MainPageTabsUtils.getTabLabel(activity, tab));

        tabHolder.binding.deleteImageViewItemMainPageTab.setVisibility(tab.isGroup() ? View.GONE : View.VISIBLE);
        tabHolder.binding.deleteImageViewItemMainPageTab.setOnClickListener(view -> {
            int pos = tabHolder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) {
                return;
            }
            if (tabs.size() <= 1) {
                // Keep at least one tab so the main page never resolves to a blank ViewPager.
                Toast.makeText(view.getContext(), R.string.cannot_delete_last_tab, Toast.LENGTH_SHORT).show();
                return;
            }
            tabs.remove(pos);
            notifyItemRemoved(pos);
            onChanged.run();
        });

        tabHolder.binding.dragHandleImageViewItemMainPageTab.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                startDragListener.onStartDrag(tabHolder);
            }
            return false;
        });

        tabHolder.itemView.setOnLongClickListener(v -> {
            showMoveMenu(v, tabHolder);
            return true;
        });
    }

    private void showMoveMenu(View anchor, RecyclerView.ViewHolder holder) {
        if (holder.getBindingAdapterPosition() == RecyclerView.NO_POSITION) {
            return;
        }
        PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);
        popup.getMenu().add(0, MENU_MOVE_TOP, 0, R.string.move_to_top);
        popup.getMenu().add(0, MENU_MOVE_BOTTOM, 1, R.string.move_to_bottom);
        popup.setOnMenuItemClickListener(item -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) {
                return false;
            }
            if (item.getItemId() == MENU_MOVE_TOP) {
                moveToTop(pos);
                return true;
            } else if (item.getItemId() == MENU_MOVE_BOTTOM) {
                moveToBottom(pos);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void moveToTop(int pos) {
        if (pos <= 0 || pos >= tabs.size()) {
            return;
        }
        tabs.add(0, tabs.remove(pos));
        notifyItemMoved(pos, 0);
        onChanged.run();
    }

    private void moveToBottom(int pos) {
        int last = tabs.size() - 1;
        if (pos < 0 || pos >= last) {
            return;
        }
        tabs.add(tabs.remove(pos));
        notifyItemMoved(pos, tabs.size() - 1);
        onChanged.run();
    }

    @Override
    public int getItemCount() {
        return tabs.size();
    }

    /** Reorder in response to a drag. Persist via {@code onChanged} when the drag settles. */
    public void onItemMove(int fromPosition, int toPosition) {
        Collections.swap(tabs, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }

    class TabViewHolder extends RecyclerView.ViewHolder {
        final ItemMainPageTabBinding binding;

        TabViewHolder(@NonNull ItemMainPageTabBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            binding.nameTextViewItemMainPageTab.setTextColor(customThemeWrapper.getPrimaryTextColor());
            binding.dragHandleImageViewItemMainPageTab.setColorFilter(customThemeWrapper.getPrimaryIconColor(), PorterDuff.Mode.SRC_IN);
            binding.deleteImageViewItemMainPageTab.setColorFilter(customThemeWrapper.getPrimaryIconColor(), PorterDuff.Mode.SRC_IN);

            if (activity.typeface != null) {
                binding.nameTextViewItemMainPageTab.setTypeface(activity.typeface);
            }
        }
    }
}
