package ml.docilealligator.infinityforreddit.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
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
import ml.docilealligator.infinityforreddit.databinding.ActivityCreateMultiRedditBinding;
import ml.docilealligator.infinityforreddit.multireddit.CreateMultiReddit;
import ml.docilealligator.infinityforreddit.multireddit.ExpandedSubredditInMultiReddit;
import ml.docilealligator.infinityforreddit.multireddit.MultiRedditJSONModel;
import ml.docilealligator.infinityforreddit.utils.Utils;
import retrofit2.Retrofit;

public class CreateMultiRedditActivity extends BaseActivity {

    private static final int SUBREDDIT_SELECTION_REQUEST_CODE = 1;
    private static final String SELECTED_SUBREDDITS_STATE = "SSS";
    @Inject
    @Named("oauth")
    Retrofit mOauthRetrofit;
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
    private ActivityCreateMultiRedditBinding binding;
    private ArrayList<ExpandedSubredditInMultiReddit> mSubreddits = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        setImmersiveModeNotApplicableBelowAndroid16();

        super.onCreate(savedInstanceState);
        binding = ActivityCreateMultiRedditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyCustomTheme();

        if (isImmersiveInterfaceRespectForcedEdgeToEdge()) {
            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(binding.appbarLayoutCreateMultiRedditActivity);
            }

            ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                    Insets allInsets = Utils.getInsets(insets, true, isForcedImmersiveInterface());

                    setMargins(binding.toolbarCreateMultiRedditActivity,
                            allInsets.left,
                            allInsets.top,
                            allInsets.right,
                            BaseActivity.IGNORE_MARGIN);

                    binding.nestedScrollViewCreateMultiRedditActivity.setPadding(
                            allInsets.left,
                            0,
                            allInsets.right,
                            allInsets.bottom
                    );

                    return WindowInsetsCompat.CONSUMED;
                }
            });
        }

        setSupportActionBar(binding.toolbarCreateMultiRedditActivity);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
            binding.visibilityChipCreateMultiRedditActivity.setVisibility(View.GONE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                binding.multiRedditNameEditTextCreateMultiRedditActivity.setImeOptions(binding.multiRedditNameEditTextCreateMultiRedditActivity.getImeOptions() | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING);
                binding.descriptionEditTextCreateMultiRedditActivity.setImeOptions(binding.descriptionEditTextCreateMultiRedditActivity.getImeOptions() | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING);
            }
        }

        if (savedInstanceState != null) {
            ArrayList<ExpandedSubredditInMultiReddit> restoredSubreddits =
                    savedInstanceState.getParcelableArrayList(SELECTED_SUBREDDITS_STATE);
            if (restoredSubreddits != null) {
                mSubreddits = restoredSubreddits;
            }
        }
        bindView();
    }

    private void bindView() {
        binding.selectSubredditChipCreateMultiRedditActivity.setOnClickListener(view -> {
            Intent intent = new Intent(CreateMultiRedditActivity.this, SelectedSubredditsAndUsersActivity.class);
            intent.putParcelableArrayListExtra(SelectedSubredditsAndUsersActivity.EXTRA_SELECTED_SUBREDDITS, mSubreddits);
            startActivityForResult(intent, SUBREDDIT_SELECTION_REQUEST_CODE);
        });

        binding.visibilityChipCreateMultiRedditActivity.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                binding.visibilityChipCreateMultiRedditActivity.setChipBackgroundColor(ColorStateList.valueOf(mCustomThemeWrapper.getFilledCardViewBackgroundColor()));
            } else {
                //Match the background color
                binding.visibilityChipCreateMultiRedditActivity.setChipBackgroundColor(ColorStateList.valueOf(mCustomThemeWrapper.getBackgroundColor()));
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.create_multi_reddit_activity, menu);
        applyMenuItemTheme(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        } else if (itemId == R.id.action_save_create_multi_reddit_activity) {
            Editable nameText = binding.multiRedditNameEditTextCreateMultiRedditActivity.getText();
            String name = nameText == null ? "" : nameText.toString();
            if (name.isEmpty()) {
                Snackbar.make(binding.coordinatorLayoutCreateMultiRedditActivity, R.string.no_multi_reddit_name, Snackbar.LENGTH_SHORT).show();
                return true;
            }
            Editable descriptionText = binding.descriptionEditTextCreateMultiRedditActivity.getText();
            String description = descriptionText == null ? "" : descriptionText.toString();

            if (!accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
                String token = accessToken;
                if (token == null) {
                    // The request would only 401; report it the way a rejected create reports.
                    Snackbar.make(binding.coordinatorLayoutCreateMultiRedditActivity, R.string.create_multi_reddit_failed, Snackbar.LENGTH_SHORT).show();
                    return true;
                }
                String jsonModel = new MultiRedditJSONModel(name, description,
                        binding.visibilityChipCreateMultiRedditActivity.isChecked(), mSubreddits).createJSONModel();
                CreateMultiReddit.createMultiReddit(mExecutor, mHandler, mOauthRetrofit, mRedditDataRoomDatabase,
                        token, "/user/" + accountName + "/m/" + name,
                        jsonModel, new CreateMultiReddit.CreateMultiRedditListener() {
                            @Override
                            public void success() {
                                finish();
                            }

                            @Override
                            public void failed(int errorCode) {
                                if (errorCode == 409) {
                                    Snackbar.make(binding.coordinatorLayoutCreateMultiRedditActivity, R.string.duplicate_multi_reddit, Snackbar.LENGTH_SHORT).show();
                                } else {
                                    Snackbar.make(binding.coordinatorLayoutCreateMultiRedditActivity, R.string.create_multi_reddit_failed, Snackbar.LENGTH_SHORT).show();
                                }
                            }
                        });
            } else {
                CreateMultiReddit.anonymousCreateMultiReddit(mExecutor, new Handler(), mRedditDataRoomDatabase,
                        "/user/-/m/" + name, name, description,
                        mSubreddits, new CreateMultiReddit.CreateMultiRedditListener() {
                            @Override
                            public void success() {
                                finish();
                            }

                            @Override
                            public void failed(int errorType) {
                                Snackbar.make(binding.coordinatorLayoutCreateMultiRedditActivity, R.string.duplicate_multi_reddit, Snackbar.LENGTH_SHORT).show();
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
            if (data != null) {
                ArrayList<ExpandedSubredditInMultiReddit> selectedSubreddits =
                        data.getParcelableArrayListExtra(SelectedSubredditsAndUsersActivity.EXTRA_RETURN_SELECTED_SUBREDDITS);
                if (selectedSubreddits != null) {
                    mSubreddits = selectedSubreddits;
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(SELECTED_SUBREDDITS_STATE, mSubreddits);
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
        binding.coordinatorLayoutCreateMultiRedditActivity.setBackgroundColor(mCustomThemeWrapper.getBackgroundColor());
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutCreateMultiRedditActivity,
                binding.collapsingToolbarLayoutCreateMultiRedditActivity, binding.toolbarCreateMultiRedditActivity);
        applyAppBarScrollFlagsIfApplicable(binding.collapsingToolbarLayoutCreateMultiRedditActivity);
        int primaryTextColor = mCustomThemeWrapper.getPrimaryTextColor();
        binding.inputCardViewCreateMultiRedditActivity.setCardBackgroundColor(mCustomThemeWrapper.getFilledCardViewBackgroundColor());

        binding.multiRedditNameExplanationTextInputLayoutCreateMultiRedditActivity.setTextColor(primaryTextColor);
        binding.multiRedditNameTextInputLayoutCreateMultiRedditActivity.setBoxStrokeColor(primaryTextColor);
        binding.multiRedditNameTextInputLayoutCreateMultiRedditActivity.setDefaultHintTextColor(ColorStateList.valueOf(primaryTextColor));
        binding.multiRedditNameEditTextCreateMultiRedditActivity.setTextColor(primaryTextColor);

        binding.descriptionTextInputLayoutCreateMultiRedditActivity.setBoxStrokeColor(primaryTextColor);
        binding.descriptionTextInputLayoutCreateMultiRedditActivity.setDefaultHintTextColor(ColorStateList.valueOf(primaryTextColor));
        binding.descriptionEditTextCreateMultiRedditActivity.setTextColor(primaryTextColor);


        binding.selectSubredditChipCreateMultiRedditActivity.setTextColor(primaryTextColor);
        binding.selectSubredditChipCreateMultiRedditActivity.setChipBackgroundColor(ColorStateList.valueOf(mCustomThemeWrapper.getFilledCardViewBackgroundColor()));
        binding.selectSubredditChipCreateMultiRedditActivity.setChipStrokeColor(ColorStateList.valueOf(mCustomThemeWrapper.getFilledCardViewBackgroundColor()));

        binding.visibilityChipCreateMultiRedditActivity.setTextColor(primaryTextColor);
        binding.visibilityChipCreateMultiRedditActivity.setChipBackgroundColor(ColorStateList.valueOf(mCustomThemeWrapper.getFilledCardViewBackgroundColor()));
        binding.visibilityChipCreateMultiRedditActivity.setChipStrokeColor(ColorStateList.valueOf(mCustomThemeWrapper.getFilledCardViewBackgroundColor()));

        if (typeface != null) {
            Utils.setFontToAllTextViews(binding.coordinatorLayoutCreateMultiRedditActivity, typeface);
            binding.selectSubredditChipCreateMultiRedditActivity.setTypeface(typeface);
            binding.visibilityChipCreateMultiRedditActivity.setTypeface(typeface);
        }
    }
}
