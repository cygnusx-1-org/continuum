package ml.docilealligator.infinityforreddit.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.adapters.PostFilterUsageRecyclerViewAdapter;
import ml.docilealligator.infinityforreddit.bottomsheetfragments.NewPostFilterUsageBottomSheetFragment;
import ml.docilealligator.infinityforreddit.bottomsheetfragments.PostFilterUsageOptionsBottomSheetFragment;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ActivityPostFilterApplicationBinding;
import ml.docilealligator.infinityforreddit.postfilter.DeletePostFilterUsage;
import ml.docilealligator.infinityforreddit.postfilter.PostFilter;
import ml.docilealligator.infinityforreddit.postfilter.PostFilterUsage;
import ml.docilealligator.infinityforreddit.postfilter.PostFilterUsageViewModel;
import ml.docilealligator.infinityforreddit.postfilter.SavePostFilterUsage;
import ml.docilealligator.infinityforreddit.utils.Utils;

public class PostFilterUsageListingActivity extends BaseActivity {

    public static final String EXTRA_POST_FILTER = "EPF";
    public static final int ADD_SUBREDDITS_REQUEST_CODE = 666;
    public static final int ADD_USERS_REQUEST_CODE = 777;

    @Inject
    @Named("default")
    SharedPreferences sharedPreferences;
    @Inject
    @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;
    @Inject
    RedditDataRoomDatabase redditDataRoomDatabase;
    @Inject
    CustomThemeWrapper customThemeWrapper;
    @Inject
    Executor executor;
    public PostFilterUsageViewModel postFilterUsageViewModel;
    private PostFilterUsageRecyclerViewAdapter adapter;
    private PostFilter postFilter;
    private ActivityPostFilterApplicationBinding binding;
        private TextInputEditText textInputEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        setImmersiveModeNotApplicableBelowAndroid16();

        super.onCreate(savedInstanceState);

        binding = ActivityPostFilterApplicationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyCustomTheme();

        if (isImmersiveInterface()) {
            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(binding.appbarLayoutPostFilterApplicationActivity);
            }

            ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                    Insets allInsets = Utils.getInsets(insets, false);

                    setMargins(binding.toolbarPostFilterApplicationActivity,
                            allInsets.left,
                            allInsets.top,
                            allInsets.right,
                            BaseActivity.IGNORE_MARGIN);

                    binding.recyclerViewPostFilterApplicationActivity.setPadding(
                            allInsets.left,
                            0,
                            allInsets.right,
                            allInsets.bottom
                    );

                    setMargins(binding.fabPostFilterApplicationActivity,
                            BaseActivity.IGNORE_MARGIN,
                            BaseActivity.IGNORE_MARGIN,
                            (int) Utils.convertDpToPixel(16, PostFilterUsageListingActivity.this) + allInsets.right,
                            (int) Utils.convertDpToPixel(16, PostFilterUsageListingActivity.this) + allInsets.bottom);

                    return WindowInsetsCompat.CONSUMED;
                }
            });
        }

        setSupportActionBar(binding.toolbarPostFilterApplicationActivity);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        postFilter = getIntent().getParcelableExtra(EXTRA_POST_FILTER);

        setTitle(postFilter.name);

        binding.fabPostFilterApplicationActivity.setOnClickListener(view -> {
            NewPostFilterUsageBottomSheetFragment newPostFilterUsageBottomSheetFragment = new NewPostFilterUsageBottomSheetFragment();
            newPostFilterUsageBottomSheetFragment.show(getSupportFragmentManager(), newPostFilterUsageBottomSheetFragment.getTag());
        });

        adapter = new PostFilterUsageRecyclerViewAdapter(this, customThemeWrapper, postFilterUsage -> {
            PostFilterUsageOptionsBottomSheetFragment postFilterUsageOptionsBottomSheetFragment = new PostFilterUsageOptionsBottomSheetFragment();
            Bundle bundle = new Bundle();
            bundle.putParcelable(PostFilterUsageOptionsBottomSheetFragment.EXTRA_POST_FILTER_USAGE, postFilterUsage);
            postFilterUsageOptionsBottomSheetFragment.setArguments(bundle);
            postFilterUsageOptionsBottomSheetFragment.show(getSupportFragmentManager(), postFilterUsageOptionsBottomSheetFragment.getTag());
        });
        binding.recyclerViewPostFilterApplicationActivity.setAdapter(adapter);

        postFilterUsageViewModel = new ViewModelProvider(this,
                new PostFilterUsageViewModel.Factory(redditDataRoomDatabase, postFilter.name)).get(PostFilterUsageViewModel.class);

        postFilterUsageViewModel.getPostFilterUsageListLiveData().observe(this, postFilterUsages -> adapter.setPostFilterUsages(postFilterUsages));
    }

    public void newPostFilterUsage(int type) {
        switch (type) {
            case PostFilterUsage.HOME_TYPE:
            case PostFilterUsage.SEARCH_TYPE:
            case PostFilterUsage.HISTORY_TYPE:
            case PostFilterUsage.UPVOTED_TYPE:
            case PostFilterUsage.DOWNVOTED_TYPE:
            case PostFilterUsage.HIDDEN_TYPE:
            case PostFilterUsage.SAVED_TYPE:
                PostFilterUsage postFilterUsage = new PostFilterUsage(postFilter.name, type, PostFilterUsage.NO_USAGE);
                SavePostFilterUsage.savePostFilterUsage(redditDataRoomDatabase, executor, postFilterUsage);
                break;
            case PostFilterUsage.SUBREDDIT_TYPE:
            case PostFilterUsage.USER_TYPE:
            case PostFilterUsage.MULTIREDDIT_TYPE:
                editAndPostFilterUsageNameOfUsage(type, null);
                break;
        }
    }

    private void editAndPostFilterUsageNameOfUsage(int type, String nameOfUsage) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_post_or_comment_filter_name_of_usage, null);
        TextInputLayout textInputLayout = dialogView.findViewById(R.id.text_input_layout_edit_post_or_comment_filter_name_of_usage_dialog);
        textInputEditText = dialogView.findViewById(R.id.text_input_edit_text_edit_post_or_comment_filter_name_of_usage_dialog);
        ImageView excludeIv = dialogView.findViewById(R.id.add_subreddits_users_image_view_customize_post_filter_activity);

        int primaryIconColor = customThemeWrapper.getPrimaryIconColor();
        excludeIv.setImageDrawable(
                Utils.getTintedDrawable(this, R.drawable.ic_add_24dp, primaryIconColor));
        int primaryTextColor = customThemeWrapper.getPrimaryTextColor();
        textInputLayout.setBoxStrokeColor(primaryTextColor);
        textInputLayout.setDefaultHintTextColor(ColorStateList.valueOf(primaryTextColor));
        textInputEditText.setTextColor(primaryTextColor);
        if (nameOfUsage != null && !nameOfUsage.equals(PostFilterUsage.NO_USAGE)) {
            textInputEditText.setText(nameOfUsage);
        }
        textInputEditText.requestFocus();
        int titleStringId = R.string.subreddit;
        switch (type) {
            case PostFilterUsage.SUBREDDIT_TYPE:
                textInputEditText.setHint(R.string.settings_tab_subreddit_name);
                excludeIv.setOnClickListener(v -> {
                    Intent intent = new Intent(this, SubredditMultiselectionActivity.class);
                    intent.putExtra(SubredditMultiselectionActivity.EXTRA_GET_SELECTED_SUBREDDITS, textInputEditText.getText().toString().trim());
                    startActivityForResult(intent, ADD_SUBREDDITS_REQUEST_CODE);
                });
                break;
            case PostFilterUsage.USER_TYPE:
                textInputEditText.setHint(R.string.settings_tab_username);
                titleStringId = R.string.user;
                excludeIv.setOnClickListener(view -> {
                    Intent intent = new Intent(this, UserMultiselectionActivity.class);
                    intent.putExtra(UserMultiselectionActivity.EXTRA_GET_SELECTED_USERS, textInputEditText.getText().toString().trim());
                    startActivityForResult(intent, ADD_USERS_REQUEST_CODE);
                });
                break;
            case PostFilterUsage.MULTIREDDIT_TYPE:
                textInputEditText.setHint(R.string.settings_tab_multi_reddit_name);
                titleStringId = R.string.multi_reddit;
                excludeIv.setVisibility(View.GONE);
                break;
        }

        Utils.showKeyboard(this, new Handler(), textInputEditText);
        new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle(titleStringId)
                .setView(dialogView)
                .setPositiveButton(R.string.ok, (editTextDialogInterface, i1)
                        -> {
                    Utils.hideKeyboard(this);

                    PostFilterUsage postFilterUsage;
                    if (textInputEditText.getText().toString().equals("")) {
                        postFilterUsage = new PostFilterUsage(postFilter.name, type, PostFilterUsage.NO_USAGE);
                    } else {
                        postFilterUsage = new PostFilterUsage(postFilter.name, type, textInputEditText.getText().toString());
                    }

                    SavePostFilterUsage.savePostFilterUsage(redditDataRoomDatabase, executor, postFilterUsage);
                })
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener(editTextDialogInterface -> {
                    Utils.hideKeyboard(this);
                })
                .show();
    }

    public void editPostFilterUsage(PostFilterUsage postFilterUsage) {
        editAndPostFilterUsageNameOfUsage(postFilterUsage.usage, postFilterUsage.nameOfUsage);
    }

    public void deletePostFilterUsage(PostFilterUsage postFilterUsage) {
        DeletePostFilterUsage.deletePostFilterUsage(redditDataRoomDatabase, executor, postFilterUsage);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return false;
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
        return customThemeWrapper;
    }

    @Override
    protected void applyCustomTheme() {
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutPostFilterApplicationActivity,
                binding.collapsingToolbarLayoutPostFilterApplicationActivity, binding.toolbarPostFilterApplicationActivity);
        applyFABTheme(binding.fabPostFilterApplicationActivity);
        binding.getRoot().setBackgroundColor(customThemeWrapper.getBackgroundColor());
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null && textInputEditText != null) {
            if (requestCode == ADD_SUBREDDITS_REQUEST_CODE) {
                ArrayList<String> subredditNames = data.getStringArrayListExtra(SubredditMultiselectionActivity.EXTRA_RETURN_SELECTED_SUBREDDITS);
                applyNewItemsToField(textInputEditText, subredditNames);
            } else if (requestCode == ADD_USERS_REQUEST_CODE) {
                ArrayList<String> usernames = data.getStringArrayListExtra(UserMultiselectionActivity.EXTRA_RETURN_SELECTED_USERNAMES);
                applyNewItemsToField(textInputEditText, usernames);
            }
        }
    }
    private void applyNewItemsToField(com.google.android.material.textfield.TextInputEditText field,
                                        @Nullable ArrayList<String> newItems) {
        if (newItems == null || newItems.isEmpty()) return;

        String currentCsv = field.getText().toString().trim();
        List<String> toAdd = getToAdd(currentCsv, newItems);
        if (toAdd.isEmpty()) return;

        StringBuilder updated = getStringBuilder(currentCsv, toAdd);
        field.setText(updated.toString());
    }

    @NonNull
    private static List<String> getToAdd(String currentCsv, List<String> candidates) {
        Set<String> existing = new HashSet<>();
        if (!currentCsv.isEmpty()) {
            for (String u : currentCsv.split(",")) {
                String t = u.trim();
                if (!t.isEmpty()) existing.add(t);
            }
        }
        List<String> toAdd = new ArrayList<>();
        for (String s : candidates) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty() && !existing.contains(trimmed)) {
                toAdd.add(trimmed);
            }
        }
        return toAdd;
    }

    @NonNull
    private static StringBuilder getStringBuilder(String currentCsv, List<String> toAdd) {
        StringBuilder sb = new StringBuilder();
        if (!currentCsv.isEmpty() && !currentCsv.endsWith(",")) {
            sb.append(currentCsv).append(",");
        } else {
            sb.append(currentCsv);
        }
        for (String u : toAdd) {
            sb.append(u).append(",");
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ',') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb;
    }

}