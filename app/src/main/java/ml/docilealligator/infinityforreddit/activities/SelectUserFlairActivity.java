package ml.docilealligator.infinityforreddit.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Named;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.adapters.UserFlairRecyclerViewAdapter;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.customviews.LinearLayoutManagerBugFixed;
import ml.docilealligator.infinityforreddit.databinding.ActivitySelectUserFlairBinding;
import ml.docilealligator.infinityforreddit.user.FetchUserFlairs;
import ml.docilealligator.infinityforreddit.user.UserFlair;
import ml.docilealligator.infinityforreddit.utils.Utils;
import ml.docilealligator.infinityforreddit.viewmodels.SelectFlairResult;
import ml.docilealligator.infinityforreddit.viewmodels.SelectUserFlairViewModel;
import retrofit2.Retrofit;

public class SelectUserFlairActivity extends BaseActivity implements ActivityToolbarInterface {

    public static final String EXTRA_SUBREDDIT_NAME = "ESN";
    private static final String USER_FLAIRS_STATE = "UFS";

    @Inject
    @Named("oauth")
    Retrofit mOauthRetrofit;
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
    @Nullable
    private LinearLayoutManagerBugFixed mLinearLayoutManager;
    @Nullable
    private ArrayList<UserFlair> mUserFlairs;
    private String mSubredditName;
    private ActivitySelectUserFlairBinding binding;
    private SelectUserFlairViewModel selectUserFlairViewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        setImmersiveModeNotApplicableBelowAndroid16();

        super.onCreate(savedInstanceState);

        binding = ActivitySelectUserFlairBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyCustomTheme();

        if (isImmersiveInterfaceRespectForcedEdgeToEdge()) {
            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(binding.appbarLayoutSelectUserFlairActivity);
            }

            ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                    Insets allInsets = Utils.getInsets(insets, false, isForcedImmersiveInterface());

                    setMargins(binding.toolbarSelectUserFlairActivity,
                            allInsets.left,
                            allInsets.top,
                            allInsets.right,
                            BaseActivity.IGNORE_MARGIN);

                    binding.recyclerViewSelectUserFlairActivity.setPadding(
                            allInsets.left,
                            0,
                            allInsets.right,
                            allInsets.bottom
                    );

                    return WindowInsetsCompat.CONSUMED;
                }
            });
        }

        attachSliderPanelIfApplicable();

        setSupportActionBar(binding.toolbarSelectUserFlairActivity);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        setToolbarGoToTop(binding.toolbarSelectUserFlairActivity);

        mSubredditName = Objects.requireNonNull(getIntent().getStringExtra(EXTRA_SUBREDDIT_NAME));
        setTitle(mSubredditName);

        selectUserFlairViewModel = new ViewModelProvider(this,
                SelectUserFlairViewModel.Companion.provideFactory(mExecutor, mOauthRetrofit))
                .get(SelectUserFlairViewModel.class);
        // The request runs in the ViewModel so its result reaches whichever instance is live after a
        // rotation, instead of firing on a dead one (CHUNKS deferred item 4).
        selectUserFlairViewModel.getSelectResult().observe(this, result -> {
            if (result instanceof SelectFlairResult.Success) {
                boolean cleared = ((SelectFlairResult.Success) result).getCleared();
                Toast.makeText(this, cleared ? R.string.clear_user_flair_success : R.string.select_user_flair_success,
                        Toast.LENGTH_SHORT).show();
                finish();
            } else if (result instanceof SelectFlairResult.Failure) {
                String errorMessage = ((SelectFlairResult.Failure) result).getMessage();
                Snackbar.make(binding.getRoot(),
                        (errorMessage == null || errorMessage.isEmpty()) ? getString(R.string.update_flair_failed) : errorMessage,
                        Snackbar.LENGTH_SHORT).show();
            }
        });

        if (savedInstanceState != null) {
            mUserFlairs = savedInstanceState.getParcelableArrayList(USER_FLAIRS_STATE);
        }
        bindView();
    }

    private void bindView() {
        ArrayList<UserFlair> userFlairs = mUserFlairs;
        if (userFlairs == null) {
            FetchUserFlairs.fetchUserFlairsInSubreddit(mExecutor, mHandler, mOauthRetrofit, accessToken, mSubredditName,
                    new FetchUserFlairs.FetchUserFlairsInSubredditListener() {
                        @Override
                        public void fetchSuccessful(@Nullable ArrayList<UserFlair> fetchedUserFlairs) {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            // A 403 means the subreddit has no user flairs; normalize that to an
                            // empty list so the adapter always has one to render.
                            ArrayList<UserFlair> flairs = fetchedUserFlairs == null ? new ArrayList<>() : fetchedUserFlairs;
                            mUserFlairs = flairs;
                            instantiateRecyclerView(flairs);
                        }

                        @Override
                        public void fetchFailed() {
                            onFetchFailed();
                        }
                    });
        } else {
            instantiateRecyclerView(userFlairs);
        }
    }

    /**
     * The fetch runs from {@link #onCreate}, and the content view stays empty until
     * {@link #instantiateRecyclerView} fills it, so a failure would otherwise strand the user on a
     * blank screen with no message and nothing to tap. Report and leave instead. This is a Toast
     * rather than a Snackbar because {@code finish()} tears down the view hierarchy a Snackbar
     * anchors to, so it would never be seen.
     */
    private void onFetchFailed() {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        Toast.makeText(this, R.string.cannot_fetch_user_flairs, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void instantiateRecyclerView(ArrayList<UserFlair> userFlairs) {
        UserFlairRecyclerViewAdapter adapter = new UserFlairRecyclerViewAdapter(this, mCustomThemeWrapper, userFlairs, (userFlair, editUserFlair) -> {
            if (editUserFlair) {
                // The adapter only passes editUserFlair == true from a flair row's edit button,
                // never from the leading "clear flair" row, so the flair is always present here.
                UserFlair flairToEdit = Objects.requireNonNull(userFlair);
                View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_flair, null);
                EditText flairEditText = dialogView.findViewById(R.id.flair_edit_text_edit_flair_dialog);
                flairEditText.setText(flairToEdit.getText());
                flairEditText.requestFocus();
                Utils.showKeyboard(this, new Handler(), flairEditText);
                new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                        .setTitle(R.string.edit_flair)
                        .setView(dialogView)
                        .setPositiveButton(R.string.ok, (dialogInterface, i)
                                -> {
                            Utils.hideKeyboard(this);
                            flairToEdit.setText(flairEditText.getText().toString());
                            selectUserFlair(flairToEdit);
                        })
                        .setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                            Utils.hideKeyboard(this);
                        })
                        .setOnDismissListener(dialogInterface -> {
                            Utils.hideKeyboard(this);
                        })
                        .show();
            } else {
                if (userFlair == null) {
                    new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                            .setTitle(R.string.clear_user_flair)
                            .setPositiveButton(R.string.yes, (dialogInterface, i) -> selectUserFlair(userFlair))
                            .setNegativeButton(R.string.no, null)
                            .show();
                } else {
                    new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                            .setTitle(R.string.select_this_user_flair)
                            .setMessage(userFlair.getText())
                            .setPositiveButton(R.string.yes, (dialogInterface, i) -> selectUserFlair(userFlair))
                            .setNegativeButton(R.string.no, null)
                            .show();
                }
            }
        });
        mLinearLayoutManager = new LinearLayoutManagerBugFixed(SelectUserFlairActivity.this);
        binding.recyclerViewSelectUserFlairActivity.setLayoutManager(mLinearLayoutManager);
        binding.recyclerViewSelectUserFlairActivity.setAdapter(adapter);
    }

    private void selectUserFlair(@Nullable UserFlair userFlair) {
        // Reddit reports plenty of failures with nothing to quote (empty Response.message() on HTTP/2,
        // null Throwable.getMessage() for many IOExceptions); the observer falls back to
        // update_flair_failed rather than the success string this used to show.
        selectUserFlairViewModel.selectUserFlair(accessToken, userFlair, mSubredditName, accountName);
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
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(USER_FLAIRS_STATE, mUserFlairs);
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
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutSelectUserFlairActivity, null, binding.toolbarSelectUserFlairActivity);
    }

    @Override
    public void onLongPress() {
        if (mLinearLayoutManager != null) {
            mLinearLayoutManager.scrollToPositionWithOffset(0, 0);
        }
    }
}