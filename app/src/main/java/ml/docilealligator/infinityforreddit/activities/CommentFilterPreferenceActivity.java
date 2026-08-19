package ml.docilealligator.infinityforreddit.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Objects;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Named;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.adapters.CommentFilterWithUsageRecyclerViewAdapter;
import ml.docilealligator.infinityforreddit.bottomsheetfragments.CommentFilterOptionsBottomSheetFragment;
import ml.docilealligator.infinityforreddit.comment.Comment;
import ml.docilealligator.infinityforreddit.commentfilter.CommentFilter;
import ml.docilealligator.infinityforreddit.commentfilter.CommentFilterSeeds;
import ml.docilealligator.infinityforreddit.commentfilter.CommentFilterWithUsageViewModel;
import ml.docilealligator.infinityforreddit.commentfilter.DeleteCommentFilter;
import ml.docilealligator.infinityforreddit.commentfilter.SaveCommentFilter;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ActivityCommentFilterPreferenceBinding;
import ml.docilealligator.infinityforreddit.utils.Utils;

public class CommentFilterPreferenceActivity extends BaseActivity {

    public static final String EXTRA_COMMENT = "EC";

    private ActivityCommentFilterPreferenceBinding binding;

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
    public CommentFilterWithUsageViewModel commentFilterWithUsageViewModel;
    private CommentFilterWithUsageRecyclerViewAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        setImmersiveModeNotApplicableBelowAndroid16();

        super.onCreate(savedInstanceState);
        binding = ActivityCommentFilterPreferenceBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());

        applyCustomTheme();

        if (isImmersiveInterfaceRespectForcedEdgeToEdge()) {
            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(binding.appbarLayoutCommentFilterPreferenceActivity);
            }

            ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                    Insets allInsets = Utils.getInsets(insets, false, isForcedImmersiveInterface());

                    setMargins(binding.toolbarCommentFilterPreferenceActivity,
                            allInsets.left,
                            allInsets.top,
                            allInsets.right,
                            BaseActivity.IGNORE_MARGIN);

                    binding.recyclerViewCommentFilterPreferenceActivity.setPadding(
                            allInsets.left,
                            0,
                            allInsets.right,
                            allInsets.bottom
                    );

                    setMargins(binding.fabCommentFilterPreferenceActivity,
                            BaseActivity.IGNORE_MARGIN,
                            BaseActivity.IGNORE_MARGIN,
                            (int) Utils.convertDpToPixel(16, CommentFilterPreferenceActivity.this) + allInsets.right,
                            (int) Utils.convertDpToPixel(16, CommentFilterPreferenceActivity.this) + allInsets.bottom);

                    return WindowInsetsCompat.CONSUMED;
                }
            });
        }

        setSupportActionBar(binding.toolbarCommentFilterPreferenceActivity);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        Comment comment = getIntent().getParcelableExtra(EXTRA_COMMENT);

        binding.fabCommentFilterPreferenceActivity.setOnClickListener(view -> {
            if (comment != null) {
                showCommentFilterOptions(comment, null);
            } else {
                Intent intent = new Intent(this, CustomizeCommentFilterActivity.class);
                intent.putExtra(CustomizeCommentFilterActivity.EXTRA_FROM_SETTINGS, true);
                startActivity(intent);
            }
        });

        adapter = new CommentFilterWithUsageRecyclerViewAdapter(this, commentFilter -> {
            if (comment != null) {
                showCommentFilterOptions(comment, commentFilter);
            } else {
                CommentFilterOptionsBottomSheetFragment commentFilterOptionsBottomSheetFragment = new CommentFilterOptionsBottomSheetFragment();
                Bundle bundle = new Bundle();
                bundle.putParcelable(CommentFilterOptionsBottomSheetFragment.EXTRA_COMMENT_FILTER, commentFilter);
                commentFilterOptionsBottomSheetFragment.setArguments(bundle);
                commentFilterOptionsBottomSheetFragment.show(getSupportFragmentManager(), commentFilterOptionsBottomSheetFragment.getTag());
            }
        });

        binding.recyclerViewCommentFilterPreferenceActivity.setAdapter(adapter);

        commentFilterWithUsageViewModel = new ViewModelProvider(this,
                new CommentFilterWithUsageViewModel.Factory(redditDataRoomDatabase)).get(CommentFilterWithUsageViewModel.class);

        commentFilterWithUsageViewModel.getCommentFilterWithUsageListLiveData().observe(this, commentFilterWithUsages -> adapter.setCommentFilterWithUsageList(commentFilterWithUsages));
    }

    public void editCommentFilter(CommentFilter commentFilter) {
        Intent intent = new Intent(this, CustomizeCommentFilterActivity.class);
        intent.putExtra(CustomizeCommentFilterActivity.EXTRA_COMMENT_FILTER, commentFilter);
        intent.putExtra(CustomizeCommentFilterActivity.EXTRA_FROM_SETTINGS, true);
        startActivity(intent);
    }

    public void applyCommentFilterTo(CommentFilter commentFilter) {
        Intent intent = new Intent(this, CommentFilterUsageListingActivity.class);
        intent.putExtra(CommentFilterUsageListingActivity.EXTRA_COMMENT_FILTER, commentFilter);
        startActivity(intent);
    }

    public void deleteCommentFilter(CommentFilter commentFilter) {
        DeleteCommentFilter.deleteCommentFilter(redditDataRoomDatabase, executor, commentFilter);
    }

    public void showCommentFilterOptions(Comment comment, @Nullable CommentFilter commentFilter) {
        String[] options = getResources().getStringArray(R.array.add_to_comment_filter_options);
        boolean[] selectedOptions = new boolean[options.length];
        new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle(R.string.select)
                .setMultiChoiceItems(options, selectedOptions, (dialogInterface, i, b) -> selectedOptions[i] = b)
                .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                    if (selectedOptions.length > 0 && selectedOptions[0]) {
                        excludeUserInFilter(comment.getAuthor(), commentFilter);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Excludes {@code username} in {@code commentFilter} and leaves, or opens the Customize screen
     * when there is no filter to add to yet.
     *
     * A filter that already exists needs nothing else decided: it has a name, its feeds are stored,
     * and the excluded users only grow. Writing it here is what lets "add to comment filter" end
     * where it started — on the comment — instead of on a screen the user has to save and back out
     * of.
     */
    private void excludeUserInFilter(@Nullable String username, @Nullable CommentFilter commentFilter) {
        if (username == null || username.trim().isEmpty()) {
            // A comment whose author is gone has nothing to exclude.
            return;
        }

        if (commentFilter == null) {
            Intent intent = new Intent(this, CustomizeCommentFilterActivity.class);
            intent.putExtra(CustomizeCommentFilterActivity.EXTRA_EXCLUDE_USER, username);
            // A new filter still has to be named, so the Customize screen is unavoidable — but this
            // screen finishes, so saving there returns to the comment rather than to the filter list.
            intent.putExtra(CustomizeCommentFilterActivity.EXTRA_FROM_SETTINGS, true);
            startActivity(intent);
            finish();
            return;
        }

        if (!CommentFilterSeeds.addExcludedUser(commentFilter, username)) {
            // This filter already excludes them, so there is nothing to write — but the user asked
            // for a state that already holds, so say so and leave rather than stranding them here.
            confirmAndFinish(commentFilter.name);
            return;
        }

        // Saved under its own name, so the feeds it is applied to are left alone.
        SaveCommentFilter.saveCommentFilter(executor, new Handler(Looper.getMainLooper()), redditDataRoomDatabase,
                commentFilter, commentFilter.name, new SaveCommentFilter.SaveCommentFilterListener() {
                    @Override
                    public void success() {
                        confirmAndFinish(commentFilter.name);
                    }

                    @Override
                    public void duplicate() {
                        // Unreachable: the filter is written back under the name it already has, so
                        // the name check cannot trip. Reported rather than swallowed all the same.
                        if (!isFinishing() && !isDestroyed()) {
                            Toast.makeText(CommentFilterPreferenceActivity.this, R.string.something_went_wrong,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void confirmAndFinish(String filterName) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        Toast.makeText(this, getString(R.string.added_to_filter, filterName), Toast.LENGTH_SHORT).show();
        // Straight back to the comment: this screen only ever opened to pick a filter.
        finish();
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
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutCommentFilterPreferenceActivity,
                binding.collapsingToolbarLayoutCommentFilterPreferenceActivity, binding.toolbarCommentFilterPreferenceActivity);
        applyAppBarScrollFlagsIfApplicable(binding.collapsingToolbarLayoutCommentFilterPreferenceActivity);
        applyFABTheme(binding.fabCommentFilterPreferenceActivity);
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