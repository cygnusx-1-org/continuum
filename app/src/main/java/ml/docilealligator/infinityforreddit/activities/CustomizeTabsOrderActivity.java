package ml.docilealligator.infinityforreddit.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.adapters.MainPageTabsRecyclerViewAdapter;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.customviews.LinearLayoutManagerBugFixed;
import ml.docilealligator.infinityforreddit.databinding.ActivityCustomizeTabsOrderBinding;
import ml.docilealligator.infinityforreddit.multireddit.MultiReddit;
import ml.docilealligator.infinityforreddit.multireddit.MultiRedditViewModel;
import ml.docilealligator.infinityforreddit.settings.MainPageTabInput;
import ml.docilealligator.infinityforreddit.settings.MainPageTabsUtils;
import ml.docilealligator.infinityforreddit.subscribedsubreddit.SubscribedSubredditData;
import ml.docilealligator.infinityforreddit.subscribedsubreddit.SubscribedSubredditViewModel;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;

/**
 * The rearrangeable list of main page tabs. Adds/removes/reorders {@link MainPageTabInput}s and
 * persists them immediately via {@link MainPageTabsUtils}. Returns {@link #EXTRA_CHANGED} so the
 * settings screen can arm its deferred app restart.
 */
public class CustomizeTabsOrderActivity extends BaseActivity implements ActivityToolbarInterface {

    public static final String EXTRA_CHANGED = "EC";

    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;
    @Inject
    @Named("main_activity_tabs")
    SharedPreferences mMainActivityTabsSharedPreferences;
    @Inject
    CustomThemeWrapper mCustomThemeWrapper;
    @Inject
    RedditDataRoomDatabase mRedditDataRoomDatabase;

    private ActivityCustomizeTabsOrderBinding binding;
    private LinearLayoutManagerBugFixed linearLayoutManager;
    private MainPageTabsRecyclerViewAdapter adapter;
    private ItemTouchHelper itemTouchHelper;
    private List<MainPageTabInput> tabs;
    private boolean changed = false;
    // Which "Show ..." toggles are on, and the latest live items for each (only enabled sources).
    private Set<Integer> enabledSources;
    private final Map<Integer, List<MainPageTabInput>> liveBySource = new HashMap<>();
    private boolean dragging = false;
    // All of the current account's own multireddits (favorite + non-favorite), for the "MultiReddit"
    // add option's picker — independent of the "Show MultiReddits" toggle.
    private List<MultiReddit> ownMultiReddits;
    // All of the account's subscribed subreddits (favorite + non-favorite), for the "Subscribed
    // Subreddits" add option's picker — independent of the "Show ... Subreddits" toggles.
    private List<SubscribedSubredditData> subscribedSubreddits;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        setImmersiveModeNotApplicableBelowAndroid16();

        super.onCreate(savedInstanceState);

        binding = ActivityCustomizeTabsOrderBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyCustomTheme();

        attachSliderPanelIfApplicable();

        if (isImmersiveInterfaceRespectForcedEdgeToEdge()) {
            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(binding.appbarLayoutCustomizeTabsOrderActivity);
            }

            ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                    Insets allInsets = Utils.getInsets(insets, false, isForcedImmersiveInterface());

                    setMargins(binding.toolbarCustomizeTabsOrderActivity,
                            allInsets.left,
                            allInsets.top,
                            allInsets.right,
                            BaseActivity.IGNORE_MARGIN);

                    binding.recyclerViewCustomizeTabsOrderActivity.setPadding(
                            allInsets.left,
                            0,
                            allInsets.right,
                            allInsets.bottom);

                    setMargins(binding.fabCustomizeTabsOrderActivity,
                            BaseActivity.IGNORE_MARGIN,
                            BaseActivity.IGNORE_MARGIN,
                            (int) Utils.convertDpToPixel(16, CustomizeTabsOrderActivity.this) + allInsets.right,
                            (int) Utils.convertDpToPixel(16, CustomizeTabsOrderActivity.this) + allInsets.bottom);

                    return WindowInsetsCompat.CONSUMED;
                }
            });
        }

        setSupportActionBar(binding.toolbarCustomizeTabsOrderActivity);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        Objects.requireNonNull(getSupportActionBar()).setTitle(R.string.settings_edit_tabs_title);
        setToolbarGoToTop(binding.toolbarCustomizeTabsOrderActivity);

        tabs = MainPageTabsUtils.load(mMainActivityTabsSharedPreferences, accountName);

        adapter = new MainPageTabsRecyclerViewAdapter(this, mCustomThemeWrapper, tabs,
                viewHolder -> itemTouchHelper.startDrag(viewHolder),
                this::persist,
                this::promptRenameTab);
        linearLayoutManager = new LinearLayoutManagerBugFixed(this);
        binding.recyclerViewCustomizeTabsOrderActivity.setLayoutManager(linearLayoutManager);
        binding.recyclerViewCustomizeTabsOrderActivity.setAdapter(adapter);

        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                    return false;
                }
                adapter.onItemMove(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // No swipe actions.
            }

            @Override
            public boolean isLongPressDragEnabled() {
                // Drag is initiated from the drag handle only.
                return false;
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    dragging = true;
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                dragging = false;
                // Persist the new order once the drag settles.
                persist();
            }
        });
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewCustomizeTabsOrderActivity);

        binding.fabCustomizeTabsOrderActivity.setOnClickListener(view -> showAddTabDialog());

        // Materialize each enabled toggle's individual items into the list (append new / drop stale)
        // by observing the same live data MainActivity uses.
        enabledSources = MainPageTabsUtils.enabledSources(mMainActivityTabsSharedPreferences, accountName);
        observeToggleSources();
    }

    private void observeToggleSources() {
        MultiRedditViewModel multiRedditViewModel = new ViewModelProvider(this,
                new MultiRedditViewModel.Factory(mRedditDataRoomDatabase, accountName)).get(MultiRedditViewModel.class);
        multiRedditViewModel.getAllFavoriteMultiReddits().observe(this, list ->
                onLiveItems(SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_FAVORITE_MULTIREDDITS,
                        MainPageTabsUtils.fromMultiReddits(list, SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_FAVORITE_MULTIREDDITS)));
        multiRedditViewModel.getAllMultiReddits().observe(this, list -> {
            ownMultiReddits = list;
            onLiveItems(SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_MULTIREDDITS,
                    MainPageTabsUtils.fromMultiReddits(MainPageTabsUtils.excludeFavoriteMultiReddits(list), SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_MULTIREDDITS));
        });

        MultiRedditViewModel followedMultiRedditViewModel = new ViewModelProvider(this,
                new MultiRedditViewModel.Factory(mRedditDataRoomDatabase, accountName, true))
                .get("followed_multireddits", MultiRedditViewModel.class);
        followedMultiRedditViewModel.getAllFavoriteMultiReddits().observe(this, list ->
                onLiveItems(SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_FAVORITE_USERS_MULTIREDDITS,
                        MainPageTabsUtils.fromMultiReddits(list, SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_FAVORITE_USERS_MULTIREDDITS)));
        followedMultiRedditViewModel.getAllMultiReddits().observe(this, list ->
                onLiveItems(SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_USERS_MULTIREDDITS,
                        MainPageTabsUtils.fromMultiReddits(MainPageTabsUtils.excludeFavoriteMultiReddits(list), SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_USERS_MULTIREDDITS)));

        SubscribedSubredditViewModel subscribedSubredditViewModel = new ViewModelProvider(this,
                new SubscribedSubredditViewModel.Factory(mRedditDataRoomDatabase, accountName)).get(SubscribedSubredditViewModel.class);
        subscribedSubredditViewModel.getAllSubscribedSubreddits().observe(this, list -> {
            subscribedSubreddits = list;
            onLiveItems(SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_SUBSCRIBED_SUBREDDITS,
                    MainPageTabsUtils.fromSubreddits(MainPageTabsUtils.excludeFavoriteSubscribedSubreddits(list), SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_SUBSCRIBED_SUBREDDITS));
        });
        subscribedSubredditViewModel.getAllFavoriteSubscribedSubreddits().observe(this, list ->
                onLiveItems(SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_FAVORITE_SUBSCRIBED_SUBREDDITS,
                        MainPageTabsUtils.fromSubreddits(list, SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_FAVORITE_SUBSCRIBED_SUBREDDITS)));
    }

    private void onLiveItems(int source, List<MainPageTabInput> items) {
        if (!enabledSources.contains(source)) {
            // Toggle is off — its items don't belong in the list.
            return;
        }
        liveBySource.put(source, items);
        reconcileWithLive();
    }

    // Merge live toggle items into the in-memory order (append new, drop stale) without touching the
    // saved order — persistence happens only on an explicit user edit (add/delete/reorder).
    private void reconcileWithLive() {
        if (dragging) {
            return;
        }
        List<MainPageTabInput> merged = MainPageTabsUtils.merge(tabs, liveBySource, enabledSources);
        if (!sameOrder(merged, tabs)) {
            tabs.clear();
            tabs.addAll(merged);
            adapter.notifyDataSetChanged();
        }
    }

    private boolean sameOrder(List<MainPageTabInput> a, List<MainPageTabInput> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!MainPageTabsUtils.userKey(a.get(i).postType, a.get(i).name)
                    .equals(MainPageTabsUtils.userKey(b.get(i).postType, b.get(i).name))) {
                return false;
            }
        }
        return true;
    }

    private void showAddTabDialog() {
        List<String> labels = new java.util.ArrayList<>();
        List<Runnable> actions = new java.util.ArrayList<>();

        addOption(labels, actions, getString(R.string.home), () -> addNamelessTab(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_HOME));
        addOption(labels, actions, getString(R.string.popular), () -> addNamelessTab(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_POPULAR));
        addOption(labels, actions, getString(R.string.all), () -> addNamelessTab(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_ALL));
        addOption(labels, actions, getString(R.string.subreddit), () -> promptForName(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT, R.string.settings_tab_subreddit_name));
        // "Subscribed Subreddits" picks from the account's subscriptions; "Subreddit" above takes a free-text name.
        addOption(labels, actions, getString(R.string.subscribed_subreddits), this::pickSubscribedSubreddit);
        // "MultiReddit" picks from the account's own multireddits; "User MultiReddit" takes a free-text path.
        addOption(labels, actions, getString(R.string.multi_reddit), this::pickOwnMultiReddit);
        addOption(labels, actions, getString(R.string.user_multi_reddit), () -> promptForName(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT, R.string.settings_tab_multi_reddit_name));
        addOption(labels, actions, getString(R.string.user), () -> promptForName(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_USER, R.string.settings_tab_username));
        if (!accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
            addOption(labels, actions, getString(R.string.upvoted), () -> addNamelessTab(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_UPVOTED));
            addOption(labels, actions, getString(R.string.downvoted), () -> addNamelessTab(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_DOWNVOTED));
            addOption(labels, actions, getString(R.string.hidden), () -> addNamelessTab(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_HIDDEN));
            addOption(labels, actions, getString(R.string.saved_posts), () -> addNamelessTab(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SAVED));
            addOption(labels, actions, getString(R.string.saved_comments), () -> addNamelessTab(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SAVED_COMMENTS));
        }

        new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle(R.string.settings_add_tab)
                .setItems(labels.toArray(new String[0]), (dialogInterface, i) -> actions.get(i).run())
                .show();
    }

    private void addOption(List<String> labels, List<Runnable> actions, String label, Runnable action) {
        labels.add(label);
        actions.add(action);
    }

    private void addNamelessTab(int postType) {
        // Name-less types (Home, Popular, All, Upvoted, ...) can only appear once.
        if (MainPageTabsUtils.isDuplicate(tabs, postType, "")) {
            Toast.makeText(this, R.string.tab_already_added, Toast.LENGTH_SHORT).show();
            return;
        }
        addTab(postType, "");
    }

    private void pickSubscribedSubreddit() {
        if (subscribedSubreddits == null || subscribedSubreddits.isEmpty()) {
            Toast.makeText(this, R.string.no_subreddits, Toast.LENGTH_SHORT).show();
            return;
        }
        List<SubscribedSubredditData> subreddits = subscribedSubreddits;
        String[] names = new String[subreddits.size()];
        for (int i = 0; i < subreddits.size(); i++) {
            names[i] = subreddits.get(i).getName();
        }
        new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle(R.string.subscribed_subreddits)
                .setItems(names, (dialogInterface, i) -> {
                    String name = subreddits.get(i).getName();
                    if (MainPageTabsUtils.isDuplicate(tabs, SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT, name)) {
                        Toast.makeText(this, R.string.tab_already_added, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    addTab(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT, name);
                })
                .show();
    }

    private void pickOwnMultiReddit() {
        if (ownMultiReddits == null || ownMultiReddits.isEmpty()) {
            Toast.makeText(this, R.string.no_multi_reddits, Toast.LENGTH_SHORT).show();
            return;
        }
        List<MultiReddit> multis = ownMultiReddits;
        String[] names = new String[multis.size()];
        for (int i = 0; i < multis.size(); i++) {
            names[i] = multis.get(i).getDisplayName();
        }
        new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle(R.string.multi_reddit)
                .setItems(names, (dialogInterface, i) -> {
                    MultiReddit multiReddit = multis.get(i);
                    if (MainPageTabsUtils.isDuplicate(tabs, SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT, multiReddit.getPath())) {
                        Toast.makeText(this, R.string.tab_already_added, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    addTab(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT, multiReddit.getPath(), multiReddit.getDisplayName());
                })
                .show();
    }

    private void promptForName(int postType, int hintId) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_text, null);
        EditText editText = dialogView.findViewById(R.id.edit_text_edit_text_dialog);
        editText.setHint(hintId);
        editText.requestFocus();
        Utils.showKeyboard(this, new Handler(), editText);
        if (dialogView.getParent() != null) {
            ((ViewGroup) dialogView.getParent()).removeView(dialogView);
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle(hintId)
                .setView(dialogView)
                .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                    String name = editText.getText().toString().trim();
                    Utils.hideKeyboard(this);
                    if (!name.isEmpty() && !MainPageTabsUtils.isDuplicate(tabs, postType, name)) {
                        // A free-text "User MultiReddit" path becomes a "username name" label.
                        String displayName = postType == SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT
                                ? MainPageTabsUtils.userMultiRedditDisplayName(name) : null;
                        addTab(postType, name, displayName);
                    }
                })
                .setNegativeButton(R.string.cancel, (dialogInterface, i) -> Utils.hideKeyboard(this))
                .create();
        // Keep OK disabled until the name is non-empty and not a duplicate; warn inline on duplicates.
        dialog.setOnShowListener(dialogInterface -> {
            Button okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            okButton.setEnabled(false);
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    String name = editable.toString().trim();
                    boolean duplicate = !name.isEmpty() && MainPageTabsUtils.isDuplicate(tabs, postType, name);
                    okButton.setEnabled(!name.isEmpty() && !duplicate);
                    editText.setError(duplicate ? getString(R.string.tab_already_added) : null);
                }
            });
        });
        dialog.show();
    }

    // Long-press a tab -> Rename: override its displayed title. Applies to any row, including
    // group (toggle-sourced) tabs whose computed label is long (e.g. "u/m/Soft/SFW").
    private void promptRenameTab(MainPageTabInput tab) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_text, null);
        EditText editText = dialogView.findViewById(R.id.edit_text_edit_text_dialog);
        editText.setText(MainPageTabsUtils.getEffectiveTabLabel(this, tab));
        editText.setSelection(editText.getText().length());
        editText.requestFocus();
        Utils.showKeyboard(this, new Handler(), editText);
        if (dialogView.getParent() != null) {
            ((ViewGroup) dialogView.getParent()).removeView(dialogView);
        }
        new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle(R.string.rename)
                .setView(dialogView)
                .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                    String text = editText.getText().toString().trim();
                    Utils.hideKeyboard(this);
                    // Empty, or matching the computed default, clears the override (reset to default).
                    tab.customTitle = text.isEmpty() || text.equals(MainPageTabsUtils.getTabLabel(this, tab))
                            ? null : text;
                    // Resolve the row from the object; the list may have been rebuilt (reconcileWithLive)
                    // while the dialog was open, so the captured position can be stale.
                    int pos = tabs.indexOf(tab);
                    if (pos >= 0) {
                        adapter.notifyItemChanged(pos);
                    }
                    persist();
                })
                .setNegativeButton(R.string.cancel, (dialogInterface, i) -> Utils.hideKeyboard(this))
                .show();
    }

    private void addTab(int postType, String name) {
        addTab(postType, name, null);
    }

    private void addTab(int postType, String name, String displayName) {
        MainPageTabInput tab = new MainPageTabInput(postType, name, SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_USER);
        tab.displayName = displayName;
        tabs.add(tab);
        adapter.notifyItemInserted(tabs.size() - 1);
        binding.recyclerViewCustomizeTabsOrderActivity.smoothScrollToPosition(tabs.size() - 1);
        persist();
    }

    private void persist() {
        MainPageTabsUtils.save(mMainActivityTabsSharedPreferences, accountName, tabs);
        changed = true;
        Intent returnIntent = new Intent();
        returnIntent.putExtra(EXTRA_CHANGED, true);
        setResult(Activity.RESULT_OK, returnIntent);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public SharedPreferences getDefaultSharedPreferences() {
        return mSharedPreferences;
    }

    @Override
    public SharedPreferences getCurrentAccountSharedPreferences() {
        return mCurrentAccountSharedPreferences;
    }

    @Override
    public CustomThemeWrapper getCustomThemeWrapper() {
        return mCustomThemeWrapper;
    }

    @Override
    protected void applyCustomTheme() {
        binding.getRoot().setBackgroundColor(mCustomThemeWrapper.getBackgroundColor());
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutCustomizeTabsOrderActivity,
                binding.collapsingToolbarLayoutCustomizeTabsOrderActivity, binding.toolbarCustomizeTabsOrderActivity);
        // Keep the toolbar fixed (no scroll flags) so item drags don't fight the app bar / list scroll.
        applyFABTheme(binding.fabCustomizeTabsOrderActivity);
    }

    @Override
    public void onLongPress() {
        if (linearLayoutManager != null) {
            linearLayoutManager.scrollToPositionWithOffset(0, 0);
        }
    }
}
