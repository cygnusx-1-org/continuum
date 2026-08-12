package ml.docilealligator.infinityforreddit.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.inputmethod.EditorInfoCompat;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Named;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ActivityEditMultiRedditBinding;
import ml.docilealligator.infinityforreddit.multireddit.EditMultiReddit;
import ml.docilealligator.infinityforreddit.multireddit.ExpandedSubredditInMultiReddit;
import ml.docilealligator.infinityforreddit.multireddit.FetchMultiRedditInfo;
import ml.docilealligator.infinityforreddit.multireddit.MultiReddit;
import ml.docilealligator.infinityforreddit.multireddit.MultiRedditJSONModel;
import ml.docilealligator.infinityforreddit.utils.Utils;
import retrofit2.Retrofit;

public class EditMultiRedditActivity extends BaseActivity {
    public static final String EXTRA_MULTI_PATH = "EMP";
    private static final int SUBREDDIT_SELECTION_REQUEST_CODE = 1;
    private static final String MULTI_REDDIT_STATE = "MRS";
    private static final String MULTI_PATH_STATE = "MPS";
    @Inject
    @Named("oauth")
    Retrofit mRetrofit;
    @Inject
    RedditDataRoomDatabase mRedditDataRoomDatabase;
    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;
    @Inject
    CustomThemeWrapper mCustomThemeWrapper;
    @Inject
    Executor mExecutor;
    /** Null until the multireddit is fetched, and stays null if that fetch fails. */
    @Nullable
    private MultiReddit multiReddit;
    private String multipath;
    private ActivityEditMultiRedditBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        setImmersiveModeNotApplicableBelowAndroid16();

        super.onCreate(savedInstanceState);
        binding = ActivityEditMultiRedditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyCustomTheme();

        if (isImmersiveInterfaceRespectForcedEdgeToEdge()) {
            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(binding.appbarLayoutEditMultiRedditActivity);
            }

            ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                    Insets allInsets = Utils.getInsets(insets, true, isForcedImmersiveInterface());

                    setMargins(binding.toolbarEditMultiRedditActivity,
                            allInsets.left,
                            allInsets.top,
                            allInsets.right,
                            BaseActivity.IGNORE_MARGIN);

                    binding.nestedScrollViewEditMultiRedditActivity.setPadding(
                            allInsets.left,
                            0,
                            allInsets.right,
                            allInsets.bottom
                    );

                    return WindowInsetsCompat.CONSUMED;
                }
            });
        }

        setSupportActionBar(binding.toolbarEditMultiRedditActivity);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
            binding.visibilityWrapperLinearLayoutEditMultiRedditActivity.setVisibility(View.GONE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                binding.multiRedditNameEditTextEditMultiRedditActivity.setImeOptions(binding.multiRedditNameEditTextEditMultiRedditActivity.getImeOptions() | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING);
                binding.descriptionEditTextEditMultiRedditActivity.setImeOptions(binding.descriptionEditTextEditMultiRedditActivity.getImeOptions() | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING);
            }
        }

        if (savedInstanceState != null) {
            multiReddit = savedInstanceState.getParcelable(MULTI_REDDIT_STATE);
            multipath = Objects.requireNonNull(savedInstanceState.getString(MULTI_PATH_STATE));
        } else {
            multipath = Objects.requireNonNull(getIntent().getStringExtra(EXTRA_MULTI_PATH));
        }

        bindView();
    }

    private void bindView() {
        MultiReddit loadedMultiReddit = multiReddit;
        if (loadedMultiReddit == null) {
            if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
                FetchMultiRedditInfo.anonymousFetchMultiRedditInfo(mExecutor, new Handler(),
                        mRedditDataRoomDatabase, multipath, new FetchMultiRedditInfo.FetchMultiRedditInfoListener() {
                            @Override
                            public void success(MultiReddit multiReddit) {
                                if (isFinishing() || isDestroyed()) {
                                    return;
                                }
                                EditMultiRedditActivity.this.multiReddit = multiReddit;
                                displayMultiReddit(multiReddit, false);
                            }

                            @Override
                            public void failed() {
                                onFetchFailed();
                            }
                        });
            } else {
                String token = accessToken;
                if (token == null) {
                    // Nothing to authenticate with, so the fetch would only 401. Fail the same
                    // way the request itself would rather than asserting on the way in.
                    onFetchFailed();
                    return;
                }
                FetchMultiRedditInfo.fetchMultiRedditInfo(mExecutor, mHandler, mRetrofit, token,
                        multipath, new FetchMultiRedditInfo.FetchMultiRedditInfoListener() {
                            @Override
                            public void success(MultiReddit multiReddit) {
                                if (isFinishing() || isDestroyed()) {
                                    return;
                                }
                                EditMultiRedditActivity.this.multiReddit = multiReddit;
                                displayMultiReddit(multiReddit, true);
                            }

                            @Override
                            public void failed() {
                                onFetchFailed();
                            }
                        });
            }
        } else {
            displayMultiReddit(loadedMultiReddit, true);
        }
        binding.selectSubredditTextViewEditMultiRedditActivity.setOnClickListener(view -> {
            MultiReddit currentMultiReddit = multiReddit;
            if (currentMultiReddit == null) {
                // The fetch is still in flight, or it failed and left nothing to edit.
                Snackbar.make(binding.coordinatorLayoutEditMultiRedditActivity, R.string.cannot_fetch_multireddit, Snackbar.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(EditMultiRedditActivity.this, SelectedSubredditsAndUsersActivity.class);
            ArrayList<ExpandedSubredditInMultiReddit> subreddits = currentMultiReddit.getSubreddits();
            if (subreddits != null) {
                intent.putParcelableArrayListExtra(SelectedSubredditsAndUsersActivity.EXTRA_SELECTED_SUBREDDITS, subreddits);
            }
            startActivityForResult(intent, SUBREDDIT_SELECTION_REQUEST_CODE);
        });
    }

    /**
     * There is nothing to edit without the multireddit, and the content stays {@code gone} until
     * {@link #displayMultiReddit} runs — so staying would leave a permanent spinner over a screen
     * whose every control reports the same failure. Leave, as the anonymous path already did.
     *
     * <p>A Toast, not a Snackbar: a Snackbar is anchored to this activity's view hierarchy, which
     * {@link #finish()} tears down before it can animate in. The no-token caller runs during
     * {@code onCreate}, so a Snackbar there would never be seen at all.
     */
    private void onFetchFailed() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        Toast.makeText(this, R.string.cannot_fetch_multireddit, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void displayMultiReddit(MultiReddit multiReddit, boolean showVisibility) {
        binding.progressBarEditMultiRedditActivity.setVisibility(View.GONE);
        binding.linearLayoutEditMultiRedditActivity.setVisibility(View.VISIBLE);
        binding.multiRedditNameEditTextEditMultiRedditActivity.setText(multiReddit.getDisplayName());
        binding.descriptionEditTextEditMultiRedditActivity.setText(multiReddit.getDescription());
        if (showVisibility) {
            binding.visibilitySwitchEditMultiRedditActivity.setChecked(!"public".equals(multiReddit.getVisibility()));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.edit_multi_reddit_activity, menu);
        applyMenuItemTheme(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        } else if (itemId == R.id.action_save_edit_multi_reddit_activity) {
            Editable nameText = binding.multiRedditNameEditTextEditMultiRedditActivity.getText();
            String name = nameText == null ? "" : nameText.toString();
            if (name.isEmpty()) {
                Snackbar.make(binding.coordinatorLayoutEditMultiRedditActivity, R.string.no_multi_reddit_name, Snackbar.LENGTH_SHORT).show();
                return true;
            }
            Editable descriptionText = binding.descriptionEditTextEditMultiRedditActivity.getText();
            String description = descriptionText == null ? "" : descriptionText.toString();

            MultiReddit currentMultiReddit = multiReddit;
            if (currentMultiReddit == null) {
                // Nothing was loaded to edit, so there is nothing to save onto.
                Snackbar.make(binding.coordinatorLayoutEditMultiRedditActivity, R.string.cannot_fetch_multireddit, Snackbar.LENGTH_SHORT).show();
                return true;
            }

            if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
                currentMultiReddit.setDisplayName(name);
                currentMultiReddit.setName(name);
                currentMultiReddit.setDescription(description);
                EditMultiReddit.anonymousEditMultiReddit(mExecutor, new Handler(), mRedditDataRoomDatabase,
                        currentMultiReddit, new EditMultiReddit.EditMultiRedditListener() {
                            @Override
                            public void success() {
                                finish();
                            }

                            @Override
                            public void failed() {
                                //Will not be called
                            }
                        });
            } else {
                String token = accessToken;
                if (token == null) {
                    // The request would only 401; report it the way a rejected edit reports.
                    Snackbar.make(binding.coordinatorLayoutEditMultiRedditActivity, R.string.edit_multi_reddit_failed, Snackbar.LENGTH_SHORT).show();
                    return true;
                }
                String jsonModel = new MultiRedditJSONModel(name, description,
                        binding.visibilitySwitchEditMultiRedditActivity.isChecked(), currentMultiReddit.getSubreddits()).createJSONModel();
                EditMultiReddit.editMultiReddit(mRetrofit, token, currentMultiReddit.getPath(),
                        jsonModel, new EditMultiReddit.EditMultiRedditListener() {
                            @Override
                            public void success() {
                                finish();
                            }

                            @Override
                            public void failed() {
                                Snackbar.make(binding.coordinatorLayoutEditMultiRedditActivity, R.string.edit_multi_reddit_failed, Snackbar.LENGTH_SHORT).show();
                            }
                        });
            }
            return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SUBREDDIT_SELECTION_REQUEST_CODE && resultCode == RESULT_OK) {
            MultiReddit currentMultiReddit = multiReddit;
            if (data != null && currentMultiReddit != null) {
                ArrayList<ExpandedSubredditInMultiReddit> selectedSubreddits = data.getParcelableArrayListExtra(
                        SelectedSubredditsAndUsersActivity.EXTRA_RETURN_SELECTED_SUBREDDITS);
                if (selectedSubreddits != null) {
                    currentMultiReddit.setSubreddits(selectedSubreddits);
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(MULTI_REDDIT_STATE, multiReddit);
        outState.putString(MULTI_PATH_STATE, multipath);
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
        binding.coordinatorLayoutEditMultiRedditActivity.setBackgroundColor(mCustomThemeWrapper.getBackgroundColor());
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutEditMultiRedditActivity,
                binding.collapsingToolbarLayoutEditMultiRedditActivity, binding.toolbarEditMultiRedditActivity);
        applyAppBarScrollFlagsIfApplicable(binding.collapsingToolbarLayoutEditMultiRedditActivity);
        binding.progressBarEditMultiRedditActivity.setIndicatorColor(mCustomThemeWrapper.getColorAccent());
        int primaryTextColor = mCustomThemeWrapper.getPrimaryTextColor();
        int secondaryTextColor = mCustomThemeWrapper.getSecondaryTextColor();
        binding.multiRedditNameEditTextEditMultiRedditActivity.setTextColor(primaryTextColor);
        binding.multiRedditNameEditTextEditMultiRedditActivity.setHintTextColor(secondaryTextColor);
        int dividerColor = mCustomThemeWrapper.getDividerColor();
        binding.divider1EditMultiRedditActivity.setBackgroundColor(dividerColor);
        binding.divider2EditMultiRedditActivity.setBackgroundColor(dividerColor);
        binding.descriptionEditTextEditMultiRedditActivity.setTextColor(primaryTextColor);
        binding.descriptionEditTextEditMultiRedditActivity.setHintTextColor(secondaryTextColor);
        binding.visibilityTextViewEditMultiRedditActivity.setTextColor(primaryTextColor);
        binding.selectSubredditTextViewEditMultiRedditActivity.setTextColor(primaryTextColor);

        if (typeface != null) {
            Utils.setFontToAllTextViews(binding.coordinatorLayoutEditMultiRedditActivity, typeface);
        }
    }
}
