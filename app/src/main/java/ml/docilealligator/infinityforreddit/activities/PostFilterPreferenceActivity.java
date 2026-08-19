package ml.docilealligator.infinityforreddit.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Named;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.adapters.PostFilterWithUsageRecyclerViewAdapter;
import ml.docilealligator.infinityforreddit.bottomsheetfragments.PostFilterOptionsBottomSheetFragment;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ActivityPostFilterPreferenceBinding;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.postfilter.DeletePostFilter;
import ml.docilealligator.infinityforreddit.postfilter.FilterRule;
import ml.docilealligator.infinityforreddit.postfilter.PostFilter;
import ml.docilealligator.infinityforreddit.postfilter.PostFilterRules;
import ml.docilealligator.infinityforreddit.postfilter.PostFilterSeeds;
import ml.docilealligator.infinityforreddit.postfilter.PostFilterWithUsage;
import ml.docilealligator.infinityforreddit.postfilter.PostFilterWithUsageViewModel;
import ml.docilealligator.infinityforreddit.postfilter.RuleField;
import ml.docilealligator.infinityforreddit.utils.Utils;
import ml.docilealligator.infinityforreddit.viewmodels.CustomizePostFilterViewModel;
import ml.docilealligator.infinityforreddit.viewmodels.SavePostFilterResult;

public class PostFilterPreferenceActivity extends BaseActivity {

    public static final String EXTRA_POST = "EP";
    public static final String EXTRA_SUBREDDIT_NAME = "ESN";
    public static final String EXTRA_USER_NAME = "EUN";

    private static final String SAVING_INTO_FILTER_NAME_STATE = "SIFNS";

    @Inject
    @Named("default")
    SharedPreferences sharedPreferences;
    @Inject
    @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;
    @Inject
    RedditDataRoomDatabase redditDataRoomDatabase;
    @Inject
    CustomThemeWrapper mCustomThemeWrapper;
    @Inject
    Executor executor;
    public PostFilterWithUsageViewModel postFilterWithUsageViewModel;
    private CustomizePostFilterViewModel savePostFilterViewModel;
    private PostFilterWithUsageRecyclerViewAdapter adapter;
    private ActivityPostFilterPreferenceBinding binding;
    /**
     * The filter the in-flight save is adding to, kept for the confirmation toast. Survives a
     * rotation mid-save because the save itself does, in {@link CustomizePostFilterViewModel}.
     */
    private String savingIntoFilterName = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        setImmersiveModeNotApplicableBelowAndroid16();

        super.onCreate(savedInstanceState);

        binding = ActivityPostFilterPreferenceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyCustomTheme();

        if (isImmersiveInterfaceRespectForcedEdgeToEdge()) {
            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(binding.appbarLayoutPostFilterPreferenceActivity);
            }

            ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                    Insets allInsets = Utils.getInsets(insets, false, isForcedImmersiveInterface());

                    setMargins(binding.toolbarPostFilterPreferenceActivity,
                            allInsets.left,
                            allInsets.top,
                            allInsets.right,
                            BaseActivity.IGNORE_MARGIN);

                    binding.contentLinearLayoutPostFilterPreferenceActivity.setPadding(
                            allInsets.left,
                            0,
                            allInsets.right,
                            0
                    );

                    binding.recyclerViewPostFilterPreferenceActivity.setPadding(
                            0,
                            0,
                            0,
                            allInsets.bottom
                    );

                    setMargins(binding.fabPostFilterPreferenceActivity,
                            BaseActivity.IGNORE_MARGIN,
                            BaseActivity.IGNORE_MARGIN,
                            (int) Utils.convertDpToPixel(16, PostFilterPreferenceActivity.this) + allInsets.right,
                            (int) Utils.convertDpToPixel(16, PostFilterPreferenceActivity.this) + allInsets.bottom);

                    return WindowInsetsCompat.CONSUMED;
                }
            });
        }

        setSupportActionBar(binding.toolbarPostFilterPreferenceActivity);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        Post post = getIntent().getParcelableExtra(EXTRA_POST);
        String subredditName = getIntent().getStringExtra(EXTRA_SUBREDDIT_NAME);
        String username = getIntent().getStringExtra(EXTRA_USER_NAME);

        binding.fabPostFilterPreferenceActivity.setOnClickListener(view -> {
            if (post != null) {
                showPostFilterOptions(post, null);
            } else if (subredditName != null) {
                excludeSubredditInFilter(subredditName, null);
            } else if (username != null) {
                excludeUserInFilter(username, null);
            } else {
                Intent intent = new Intent(PostFilterPreferenceActivity.this, CustomizePostFilterActivity.class);
                intent.putExtra(CustomizePostFilterActivity.EXTRA_FROM_SETTINGS, true);
                startActivity(intent);
            }
        });

        adapter = new PostFilterWithUsageRecyclerViewAdapter(this, mCustomThemeWrapper, postFilter -> {
            if (post != null) {
                showPostFilterOptions(post, postFilter);
            } else if (subredditName != null) {
                excludeSubredditInFilter(subredditName, postFilter);
            } else if (username != null) {
                excludeUserInFilter(username, postFilter);
            } else {
                PostFilterOptionsBottomSheetFragment postFilterOptionsBottomSheetFragment = new PostFilterOptionsBottomSheetFragment();
                Bundle bundle = new Bundle();
                bundle.putParcelable(PostFilterOptionsBottomSheetFragment.EXTRA_POST_FILTER, postFilter);
                postFilterOptionsBottomSheetFragment.setArguments(bundle);
                postFilterOptionsBottomSheetFragment.show(getSupportFragmentManager(), postFilterOptionsBottomSheetFragment.getTag());
            }
        });

        binding.recyclerViewPostFilterPreferenceActivity.setAdapter(adapter);

        postFilterWithUsageViewModel = new ViewModelProvider(this,
                new PostFilterWithUsageViewModel.Factory(redditDataRoomDatabase)).get(PostFilterWithUsageViewModel.class);

        postFilterWithUsageViewModel.getPostFilterWithUsageListLiveData().observe(this, new Observer<List<PostFilterWithUsage>>() {
            @Override
            public void onChanged(List<PostFilterWithUsage> postFilterWithUsages) {
                adapter.setPostFilterWithUsageList(postFilterWithUsages);
            }
        });

        savePostFilterViewModel = new ViewModelProvider(this,
                CustomizePostFilterViewModel.provideFactory(executor, redditDataRoomDatabase))
                .get(CustomizePostFilterViewModel.class);

        savePostFilterViewModel.getSaveResult().observe(this, result -> {
            if (result instanceof SavePostFilterResult.Success) {
                Toast.makeText(this, getString(R.string.added_to_filter, savingIntoFilterName),
                        Toast.LENGTH_SHORT).show();
                // Straight back to whatever the user was reading: this screen only ever opened to
                // pick a filter, and the rule is now in it.
                finish();
            } else {
                // Duplicate is unreachable here — the filter is written back under the name it
                // already has — so anything else is the write itself failing.
                Snackbar.make(binding.getRoot(), R.string.save_post_filter_failed, Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SAVING_INTO_FILTER_NAME_STATE, savingIntoFilterName);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        savingIntoFilterName = savedInstanceState.getString(SAVING_INTO_FILTER_NAME_STATE, "");
    }

    public void showPostFilterOptions(Post post, @Nullable PostFilter postFilter) {
        String[] options = getResources().getStringArray(R.array.add_to_post_filter_options);
        boolean[] selectedOptions = new boolean[options.length];
        new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle(R.string.select)
                .setMultiChoiceItems(options, selectedOptions, (dialogInterface, i, b) -> selectedOptions[i] = b)
                .setPositiveButton(R.string.ok, (dialogInterface, i) ->
                        addRulesToFilter(PostFilterSeeds.rulesForPost(post, selectedOptions), postFilter))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public void excludeSubredditInFilter(String subredditName, @Nullable PostFilter postFilter) {
        addRulesToFilter(PostFilterSeeds.excludeRule(RuleField.SUBREDDIT, subredditName), postFilter);
    }

    public void excludeUserInFilter(String username, @Nullable PostFilter postFilter) {
        addRulesToFilter(PostFilterSeeds.excludeRule(RuleField.USER, username), postFilter);
    }

    /**
     * Adds what the user picked to {@code postFilter} and leaves, or opens the Customize screen when
     * there is no filter to add to yet.
     *
     * A filter that already exists needs nothing else decided: it has a name, its feeds are stored,
     * and the terms only grow. Writing it here is what lets "add to post filter" end where it
     * started — on the post — instead of on a screen the user has to save and then back out of.
     */
    private void addRulesToFilter(List<FilterRule> newRules, @Nullable PostFilter postFilter) {
        if (newRules.isEmpty()) {
            // Nothing ticked, or nothing this post could supply for what was ticked.
            return;
        }

        if (postFilter == null) {
            Intent intent = new Intent(this, CustomizePostFilterActivity.class);
            intent.putExtra(CustomizePostFilterActivity.EXTRA_SEED_RULES, new ArrayList<>(newRules));
            // A new filter still has to be named, so the Customize screen is unavoidable — but this
            // screen finishes, so saving there returns to the post rather than to the filter list.
            intent.putExtra(CustomizePostFilterActivity.EXTRA_FROM_SETTINGS, true);
            startActivity(intent);
            finish();
            return;
        }

        List<FilterRule> rules = new ArrayList<>(PostFilterRules.toRules(postFilter));
        for (FilterRule rule : newRules) {
            PostFilterRules.addRule(rules, rule);
        }
        PostFilterRules.applyRules(postFilter, rules);
        savingIntoFilterName = postFilter.name;
        // Saved under its own name, so the stored feeds and blocked-subreddit rows are left alone.
        savePostFilterViewModel.savePostFilter(postFilter, postFilter.name);
    }

    public void editPostFilter(PostFilter postFilter) {
        Intent intent = new Intent(PostFilterPreferenceActivity.this, CustomizePostFilterActivity.class);
        intent.putExtra(CustomizePostFilterActivity.EXTRA_POST_FILTER, postFilter);
        intent.putExtra(CustomizePostFilterActivity.EXTRA_FROM_SETTINGS, true);
        startActivity(intent);
    }


    public void deletePostFilter(PostFilter postFilter) {
        DeletePostFilter.deletePostFilter(redditDataRoomDatabase, executor, postFilter);
    }

    @Override
    public SharedPreferences getDefaultSharedPreferences() {
        return sharedPreferences;
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
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutPostFilterPreferenceActivity,
                binding.collapsingToolbarLayoutPostFilterPreferenceActivity, binding.toolbarPostFilterPreferenceActivity);
        applyAppBarScrollFlagsIfApplicable(binding.collapsingToolbarLayoutPostFilterPreferenceActivity);
        applyFABTheme(binding.fabPostFilterPreferenceActivity);
        binding.getRoot().setBackgroundColor(mCustomThemeWrapper.getBackgroundColor());
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return false;
    }
}