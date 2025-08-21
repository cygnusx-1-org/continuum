package ml.docilealligator.infinityforreddit.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;

import java.util.ArrayList;

import javax.inject.Inject;
import javax.inject.Named;

import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.adapters.UserMultiselectionRecyclerViewAdapter;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.customviews.LinearLayoutManagerBugFixed;
import ml.docilealligator.infinityforreddit.databinding.ActivitySubscribedUsersMultiselectionBinding;
import ml.docilealligator.infinityforreddit.subscribeduser.SubscribedUserViewModel;
import retrofit2.Retrofit;

public class UserMultiselectionActivity extends BaseActivity implements ActivityToolbarInterface {

    static final String EXTRA_RETURN_SELECTED_USERNAMES = "ERSU";

    public static final String EXTRA_GET_SELECTED_USERS = "EGSU";

    private static final int USERNAME_SEARCH_REQUEST_CODE = 3;

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
    public SubscribedUserViewModel mSubscribedUSerViewModel;
    private LinearLayoutManagerBugFixed mLinearLayoutManager;
    private UserMultiselectionRecyclerViewAdapter mAdapter;
    private RequestManager mGlide;
    private ActivitySubscribedUsersMultiselectionBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);
        super.onCreate(savedInstanceState);

        binding = ActivitySubscribedUsersMultiselectionBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());

        applyCustomTheme();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();

            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(binding.appbarLayoutUsersMultiselectionActivity);
            }

            if (isImmersiveInterface()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.setDecorFitsSystemWindows(false);
                } else {
                    window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                }
                ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                    @NonNull
                    @Override
                    public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                        Insets allInsets = insets.getInsets(
                                WindowInsetsCompat.Type.systemBars()
                                        | WindowInsetsCompat.Type.displayCutout()
                        );

                        setMargins(binding.toolbarSubscribedUsersMultiselectionActivity,
                                allInsets.left,
                                allInsets.top,
                                allInsets.right,
                                BaseActivity.IGNORE_MARGIN);

                        binding.recyclerViewSubscribedSubscribedUsersMultiselectionActivity.setPadding(allInsets.left, 0, allInsets.right, allInsets.bottom);

                        return WindowInsetsCompat.CONSUMED;
                    }
                });
                //adjustToolbar(binding.toolbarSubscribedUsersMultiselectionActivity);

                //int navBarHeight = getNavBarHeight();

                //if (navBarHeight > 0) {
                //    binding.recyclerViewSubscribedSubscribedUsersMultiselectionActivity.setPadding(0, 0, 0, navBarHeight);
                //}
            }
        }

        setSupportActionBar(binding.toolbarSubscribedUsersMultiselectionActivity);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mGlide = Glide.with(this);

        binding.swipeRefreshLayoutSubscribedSubscribedUsersMultiselectionActivity.setEnabled(false);

        bindView();
    }

    private void bindView() {
        mLinearLayoutManager = new LinearLayoutManagerBugFixed(this);
        binding.recyclerViewSubscribedSubscribedUsersMultiselectionActivity.setLayoutManager(mLinearLayoutManager);
        mAdapter = new UserMultiselectionRecyclerViewAdapter(this, mCustomThemeWrapper);
        binding.recyclerViewSubscribedSubscribedUsersMultiselectionActivity.setAdapter(mAdapter);

        mSubscribedUSerViewModel = new ViewModelProvider(this,
                new SubscribedUserViewModel.Factory(mRedditDataRoomDatabase, accountName))
                .get(SubscribedUserViewModel.class);
        mSubscribedUSerViewModel.getAllSubscribedUsers().observe(this, subscribedUserData -> {
            binding.swipeRefreshLayoutSubscribedSubscribedUsersMultiselectionActivity.setRefreshing(false);
            if (subscribedUserData == null || subscribedUserData.size() == 0) {
                binding.recyclerViewSubscribedSubscribedUsersMultiselectionActivity.setVisibility(View.GONE);
                binding.noSubscriptionsLinearLayoutSubscribedUsersMultiselectionActivity.setVisibility(View.VISIBLE);
            } else {
                binding.noSubscriptionsLinearLayoutSubscribedUsersMultiselectionActivity.setVisibility(View.GONE);
                binding.recyclerViewSubscribedSubscribedUsersMultiselectionActivity.setVisibility(View.VISIBLE);
                mGlide.clear(binding.noSubscriptionsImageViewSubscribedUsersMultiselectionActivity);
            }

            mAdapter.setSubscribedUsers(subscribedUserData, getIntent().getStringExtra(EXTRA_GET_SELECTED_USERS));
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.user_multiselection_activity, menu);
        applyMenuItemTheme(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        } else if (itemId == R.id.action_save_user_multiselection_activity) {
            if (mAdapter != null) {
                Intent returnIntent = new Intent();
                returnIntent.putStringArrayListExtra(EXTRA_RETURN_SELECTED_USERNAMES,
                        mAdapter.getAllSelectedUsers());
                setResult(RESULT_OK, returnIntent);
            }
            finish();
            return true;
        } else if (itemId == R.id.action_search_user_multiselection_activity) {
            Intent intent = new Intent(this, SearchActivity.class);
            intent.putExtra(SearchActivity.EXTRA_SEARCH_ONLY_USERS, true);
            intent.putExtra(SearchActivity.EXTRA_IS_MULTI_SELECTION, true);
            startActivityForResult(intent, USERNAME_SEARCH_REQUEST_CODE);
        }

        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == USERNAME_SEARCH_REQUEST_CODE && resultCode == RESULT_OK && data != null && mAdapter != null) {
            Intent returnIntent = new Intent();
            ArrayList<String> selectedUsers = mAdapter.getAllSelectedUsers();
            ArrayList<String> searchedUsers = data.getStringArrayListExtra(SearchActivity.RETURN_EXTRA_SELECTED_USERNAMES);
            if (searchedUsers != null) {
                selectedUsers.addAll(searchedUsers);
            }
            returnIntent.putStringArrayListExtra(EXTRA_RETURN_SELECTED_USERNAMES, selectedUsers);
            setResult(RESULT_OK, returnIntent);
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
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
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutUsersMultiselectionActivity,
                binding.collapsingToolbarLayoutSubscribedUsersMultiselectionActivity, binding.toolbarSubscribedUsersMultiselectionActivity);
        binding.errorTextViewSubscribedUsersMultiselectionActivity.setTextColor(mCustomThemeWrapper.getSecondaryTextColor());
        if (typeface != null) {
            binding.errorTextViewSubscribedUsersMultiselectionActivity.setTypeface(typeface);
        }
    }

    @Override
    public void onLongPress() {
        if (mLinearLayoutManager != null) {
            mLinearLayoutManager.scrollToPositionWithOffset(0, 0);
        }
    }
}
