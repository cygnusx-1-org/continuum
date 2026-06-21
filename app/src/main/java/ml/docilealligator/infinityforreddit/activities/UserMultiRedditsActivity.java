package ml.docilealligator.infinityforreddit.activities;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Named;

import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.adapters.UserMultiRedditsRecyclerViewAdapter;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.customviews.LinearLayoutManagerBugFixed;
import ml.docilealligator.infinityforreddit.databinding.ActivityUserMultiRedditsBinding;
import ml.docilealligator.infinityforreddit.multireddit.FetchUserMultiReddits;
import ml.docilealligator.infinityforreddit.multireddit.MultiReddit;
import ml.docilealligator.infinityforreddit.utils.Utils;
import retrofit2.Retrofit;

public class UserMultiRedditsActivity extends BaseActivity {

    public static final String EXTRA_USERNAME = "EU";

    @Inject
    @Named("no_oauth")
    Retrofit mRetrofit;
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

    private ActivityUserMultiRedditsBinding binding;
    private String username;
    private UserMultiRedditsRecyclerViewAdapter mAdapter;
    private ArrayList<MultiReddit> mMultiReddits;
    private Set<String> mOriginalSavedPaths;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        super.onCreate(savedInstanceState);

        binding = ActivityUserMultiRedditsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyCustomTheme();

        attachSliderPanelIfApplicable();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();

            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(binding.appbarLayoutUserMultiRedditsActivity);
            }

            if (isImmersiveInterfaceRespectForcedEdgeToEdge()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.setDecorFitsSystemWindows(false);
                } else {
                    window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                }

                ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                    @NonNull
                    @Override
                    public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                        Insets allInsets = Utils.getInsets(insets, false, isForcedImmersiveInterface());

                        setMargins(binding.toolbarUserMultiRedditsActivity,
                                allInsets.left,
                                allInsets.top,
                                allInsets.right,
                                BaseActivity.IGNORE_MARGIN);

                        binding.recyclerViewUserMultiRedditsActivity.setPadding(allInsets.left, 0, allInsets.right, allInsets.bottom);

                        return insets;
                    }
                });
            }
        }

        setSupportActionBar(binding.toolbarUserMultiRedditsActivity);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setToolbarGoToTop(binding.toolbarUserMultiRedditsActivity);

        username = getIntent().getStringExtra(EXTRA_USERNAME);
        if (username == null || username.isEmpty()) {
            Toast.makeText(this, R.string.error_getting_multi_reddit_data, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        getSupportActionBar().setTitle(getString(R.string.user_multireddits_activity_label, username));

        binding.recyclerViewUserMultiRedditsActivity.setLayoutManager(new LinearLayoutManagerBugFixed(this));

        fetchUserMultiReddits();
    }

    private void fetchUserMultiReddits() {
        binding.progressBarUserMultiRedditsActivity.setVisibility(View.VISIBLE);
        binding.recyclerViewUserMultiRedditsActivity.setVisibility(View.GONE);
        binding.errorTextViewUserMultiRedditsActivity.setVisibility(View.GONE);

        Retrofit retrofit = accountName.equals(Account.ANONYMOUS_ACCOUNT) ? mRetrofit : mOauthRetrofit;
        FetchUserMultiReddits.fetchUserMultiReddits(mExecutor, mHandler, retrofit, accessToken, accountName, username,
                new FetchUserMultiReddits.FetchUserMultiRedditsListener() {
                    @Override
                    public void success(ArrayList<MultiReddit> multiReddits) {
                        if (multiReddits.isEmpty()) {
                            binding.progressBarUserMultiRedditsActivity.setVisibility(View.GONE);
                            binding.errorTextViewUserMultiRedditsActivity.setText(R.string.user_has_no_public_multireddit);
                            binding.errorTextViewUserMultiRedditsActivity.setVisibility(View.VISIBLE);
                            return;
                        }
                        loadSavedPathsThenDisplay(multiReddits);
                    }

                    @Override
                    public void failed() {
                        binding.progressBarUserMultiRedditsActivity.setVisibility(View.GONE);
                        binding.errorTextViewUserMultiRedditsActivity.setText(R.string.fetch_user_multireddit_failed);
                        binding.errorTextViewUserMultiRedditsActivity.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void loadSavedPathsThenDisplay(ArrayList<MultiReddit> multiReddits) {
        mExecutor.execute(() -> {
            Set<String> savedPaths = new HashSet<>(mRedditDataRoomDatabase.multiRedditDao().getFollowedMultiRedditPaths(accountName));
            mHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                mMultiReddits = multiReddits;
                mOriginalSavedPaths = savedPaths;
                binding.progressBarUserMultiRedditsActivity.setVisibility(View.GONE);
                binding.recyclerViewUserMultiRedditsActivity.setVisibility(View.VISIBLE);
                mAdapter = new UserMultiRedditsRecyclerViewAdapter(this, mCustomThemeWrapper, multiReddits, savedPaths);
                binding.recyclerViewUserMultiRedditsActivity.setAdapter(mAdapter);
            });
        });
    }

    private void saveSelection() {
        if (mAdapter == null || mMultiReddits == null) {
            finish();
            return;
        }
        Set<String> selectedPaths = new HashSet<>(mAdapter.getSelectedPaths());
        Set<String> originalPaths = mOriginalSavedPaths == null ? new HashSet<>() : mOriginalSavedPaths;

        ArrayList<MultiReddit> toAdd = new ArrayList<>();
        for (MultiReddit multiReddit : mMultiReddits) {
            // Don't "follow" your own feeds: they share the (path, username) primary key with the
            // owned rows, so inserting would overwrite them. They already live in the MultiReddits tab.
            if (ownerOf(multiReddit).equalsIgnoreCase(accountName)) {
                continue;
            }
            if (selectedPaths.contains(multiReddit.getPath()) && !originalPaths.contains(multiReddit.getPath())) {
                toAdd.add(multiReddit);
            }
        }
        ArrayList<String> toRemove = new ArrayList<>();
        for (String path : originalPaths) {
            if (!selectedPaths.contains(path)) {
                toRemove.add(path);
            }
        }

        mExecutor.execute(() -> {
            for (MultiReddit source : toAdd) {
                MultiReddit followed = new MultiReddit(source.getPath(), source.getDisplayName(),
                        source.getName(), source.getDescription(), source.getCopiedFrom(), source.getIconUrl(),
                        source.getVisibility(), accountName, source.getNSubscribers(), source.getCreatedUTC(),
                        source.isOver18(), false, false);
                followed.setFollowed(true);
                mRedditDataRoomDatabase.multiRedditDao().insert(followed);
            }
            for (String path : toRemove) {
                mRedditDataRoomDatabase.multiRedditDao().deleteFollowedMultiReddit(path, accountName);
            }
            mHandler.post(() -> {
                Toast.makeText(UserMultiRedditsActivity.this,
                        getString(R.string.save_user_multireddit_success, toAdd.size()), Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private String ownerOf(MultiReddit multiReddit) {
        String path = multiReddit.getPath();
        if (path != null) {
            String[] segments = path.split("/");
            if (segments.length > 2 && "user".equals(segments[1])) {
                return segments[2];
            }
        }
        return username;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.user_multi_reddits_activity, menu);
        applyMenuItemTheme(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_save_user_multi_reddits_activity) {
            saveSelection();
            return true;
        }
        return false;
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
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutUserMultiRedditsActivity,
                null, binding.toolbarUserMultiRedditsActivity);
        binding.errorTextViewUserMultiRedditsActivity.setTextColor(mCustomThemeWrapper.getSecondaryTextColor());
    }
}
