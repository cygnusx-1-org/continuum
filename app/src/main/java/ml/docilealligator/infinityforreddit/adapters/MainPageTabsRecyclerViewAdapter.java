package ml.docilealligator.infinityforreddit.adapters;

import android.annotation.SuppressLint;
import android.graphics.PorterDuff;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
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

    public interface OnRenameListener {
        void onRename(MainPageTabInput tab);
    }

    private static final int MENU_RENAME = 1;
    private static final int MENU_RESET = 2;
    private static final int MENU_MOVE_TOP = 3;
    private static final int MENU_MOVE_BOTTOM = 4;

    private final BaseActivity activity;
    private final CustomThemeWrapper customThemeWrapper;
    private final List<MainPageTabInput> tabs;
    private final OnStartDragListener startDragListener;
    private final Runnable onChanged;
    private final OnRenameListener renameListener;
    // Reused across binds for the dimmed default label; spans are immutable position-less markers,
    // so one instance can back every row's SpannableStringBuilder.
    private final ForegroundColorSpan secondaryColorSpan;

    public MainPageTabsRecyclerViewAdapter(BaseActivity activity, CustomThemeWrapper customThemeWrapper,
                                           List<MainPageTabInput> tabs,
                                           OnStartDragListener startDragListener, Runnable onChanged,
                                           OnRenameListener renameListener) {
        this.activity = activity;
        this.customThemeWrapper = customThemeWrapper;
        this.tabs = tabs;
        this.startDragListener = startDragListener;
        this.onChanged = onChanged;
        this.renameListener = renameListener;
        this.secondaryColorSpan = new ForegroundColorSpan(customThemeWrapper.getSecondaryTextColor());
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

        // When a rename override is set, append the computed default dimmed to the title's right, as
        // one flowing text so a long title line-wraps to fit both instead of ellipsizing. The default
        // is kept unbreakable (word joiners between its chars) so it drops to the next line whole
        // — never splitting mid-label — when it can't fit beside the title; the space before it stays
        // the only break point.
        String title = MainPageTabsUtils.getEffectiveTabLabel(activity, tab);
        boolean renamed = tab.customTitle != null && !tab.customTitle.isEmpty();
        String defaultLabel = renamed ? MainPageTabsUtils.getTabLabel(activity, tab) : null;
        // Only append the dimmed default when the override actually differs from it (a title that
        // happens to equal the default would otherwise show it twice).
        if (renamed && !title.equals(defaultLabel)) {
            SpannableStringBuilder text = new SpannableStringBuilder(title).append("  ").append(nonBreaking(java.util.Objects.requireNonNull(defaultLabel)));
            text.setSpan(secondaryColorSpan, title.length() + 2, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            tabHolder.binding.nameTextViewItemMainPageTab.setText(text);
        } else {
            tabHolder.binding.nameTextViewItemMainPageTab.setText(title);
        }

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

    /**
     * Returns {@code text} with a word joiner (U+2060) between every pair of characters so the line
     * breaker treats it as a single unbreakable unit — used for the dimmed default label so it wraps
     * to a new line whole instead of splitting at its slashes/spaces.
     */
    private static CharSequence nonBreaking(String text) {
        if (text == null || text.length() < 2) {
            return text == null ? "" : text;
        }
        StringBuilder sb = new StringBuilder(text.length() * 2 - 1);
        for (int i = 0; i < text.length(); i++) {
            if (i > 0) {
                sb.append('\u2060'); // word joiner: prohibits a line break between the two chars
            }
            sb.append(text.charAt(i));
        }
        return sb;
    }

    private void showMoveMenu(View anchor, RecyclerView.ViewHolder holder) {
        int menuPos = holder.getBindingAdapterPosition();
        if (menuPos == RecyclerView.NO_POSITION) {
            return;
        }
        PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);
        popup.getMenu().add(0, MENU_RENAME, 0, R.string.rename);
        MainPageTabInput menuTab = tabs.get(menuPos);
        // Only offer "Reset name" when there is an override to clear.
        if (menuTab.customTitle != null && !menuTab.customTitle.isEmpty()) {
            popup.getMenu().add(0, MENU_RESET, 1, R.string.reset_name);
        }
        popup.getMenu().add(0, MENU_MOVE_TOP, 2, R.string.move_to_top);
        popup.getMenu().add(0, MENU_MOVE_BOTTOM, 3, R.string.move_to_bottom);
        popup.setOnMenuItemClickListener(item -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) {
                return false;
            }
            if (item.getItemId() == MENU_RENAME) {
                renameListener.onRename(tabs.get(pos));
                return true;
            } else if (item.getItemId() == MENU_RESET) {
                tabs.get(pos).customTitle = null;
                notifyItemChanged(pos);
                onChanged.run();
                return true;
            } else if (item.getItemId() == MENU_MOVE_TOP) {
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
