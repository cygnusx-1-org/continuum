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
import ml.docilealligator.infinityforreddit.user.SelectUserFlair;
import ml.docilealligator.infinityforreddit.user.UserFlair;
import ml.docilealligator.infinityforreddit.utils.Utils;
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
        SelectUserFlair.selectUserFlair(mExecutor, mHandler, mOauthRetrofit, accessToken, userFlair, mSubredditName, accountName,
                new SelectUserFlair.SelectUserFlairListener() {
                    @Override
                    public void success() {
                        if (isFinishing() || isDestroyed()) {
                            // The request outlived the instance that started it (a rotation, say),
                            // so this result can no longer reach the live screen — it stays open on
                            // the flair list with the change already applied server-side. Fixing
                            // that needs the request hoisted off the activity instance; see the
                            // deferred list in CHUNKS.md.
                            return;
                        }

                        if (userFlair == null) {
                            Toast.makeText(SelectUserFlairActivity.this, R.string.clear_user_flair_success, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(SelectUserFlairActivity.this, R.string.select_user_flair_success, Toast.LENGTH_SHORT).show();
                        }
                        finish();
                    }

                    @Override
                    public void failed(@Nullable String errorMessage) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        // Reddit reports plenty of failures with nothing to quote: OkHttp leaves
                        // Response.message() empty on HTTP/2, and Throwable.getMessage() is null for
                        // many IOExceptions. Fall back to a failure string rather than the success
                        // one this used to show.
                        if (errorMessage == null || errorMessage.isEmpty()) {
                            Snackbar.make(binding.getRoot(), R.string.update_flair_failed, Snackbar.LENGTH_SHORT).show();
                        } else {
                            Snackbar.make(binding.getRoot(), errorMessage, Snackbar.LENGTH_SHORT).show();
                        }
                    }
                });
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