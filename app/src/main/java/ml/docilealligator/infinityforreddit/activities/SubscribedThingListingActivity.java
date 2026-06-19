package ml.docilealligator.infinityforreddit.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.ViewGroupCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;

import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.adapters.SubredditAutocompleteRecyclerViewAdapter;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.subreddit.ParseSubredditData;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.JSONUtils;
import ml.docilealligator.infinityforreddit.asynctasks.DeleteMultiredditInDatabase;
import ml.docilealligator.infinityforreddit.asynctasks.InsertMultireddit;
import ml.docilealligator.infinityforreddit.asynctasks.InsertSubscribedThings;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ActivitySubscribedThingListingBinding;
import ml.docilealligator.infinityforreddit.events.GoBackToMainPageEvent;
import ml.docilealligator.infinityforreddit.events.RefreshMultiRedditsEvent;
import ml.docilealligator.infinityforreddit.events.SwitchAccountEvent;
import ml.docilealligator.infinityforreddit.fragments.FollowedMultiRedditListingFragment;
import ml.docilealligator.infinityforreddit.fragments.FollowedUsersListingFragment;
import ml.docilealligator.infinityforreddit.fragments.FragmentCommunicator;
import ml.docilealligator.infinityforreddit.fragments.MultiRedditListingFragment;
import ml.docilealligator.infinityforreddit.fragments.SubscribedSubredditsListingFragment;
import ml.docilealligator.infinityforreddit.multireddit.DeleteMultiReddit;
import ml.docilealligator.infinityforreddit.multireddit.FetchMyMultiReddits;
import ml.docilealligator.infinityforreddit.multireddit.MultiReddit;
import ml.docilealligator.infinityforreddit.network.AnyAccountAccessTokenAuthenticator;
import ml.docilealligator.infinityforreddit.subreddit.SubredditData;
import ml.docilealligator.infinityforreddit.subscribedsubreddit.SubscribedSubredditData;
import ml.docilealligator.infinityforreddit.subscribeduser.SubscribedUserData;
import ml.docilealligator.infinityforreddit.thing.FetchSubscribedThing;
import ml.docilealligator.infinityforreddit.user.UserFollowing;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

public class SubscribedThingListingActivity extends BaseActivity implements ActivityToolbarInterface {

    public static final String EXTRA_SHOW_MULTIREDDITS = "ESM";
    public static final String EXTRA_THING_SELECTION_MODE = "ETSM";
    public static final String EXTRA_THING_SELECTION_TYPE = "ETST";
    public static final String EXTRA_SPECIFIED_ACCOUNT = "ESA";
    public static final String EXTRA_EXTRA_CLEAR_SELECTION = "EECS";
    public static final int EXTRA_THING_SELECTION_TYPE_ALL = 0;
    public static final int EXTRA_THING_SELECTION_TYPE_SUBREDDIT = 1;
    public static final int EXTRA_THING_SELECTION_TYPE_USER = 2;
    public static final int EXTRA_THING_SELECTION_TYPE_MULTIREDDIT = 3;
    private static final String INSERT_SUBSCRIBED_SUBREDDIT_STATE = "ISSS";
    private static final String INSERT_MULTIREDDIT_STATE = "IMS";

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
    @Inject
    @Named("nsfw_and_spoiler")
    SharedPreferences mNsfwAndSpoilerSharedPreferences;
    private Runnable autoCompleteRunnable;
    private retrofit2.Call<String> subredditAutocompleteCall;
    private boolean mInsertSuccess;
    private boolean mInsertMultiredditSuccess;
    private boolean showMultiReddits;
    private boolean isThingSelectionMode;
    private boolean searchPrefixOnly;
    private int thingSelectionType;
    private String mAccountProfileImageUrl;
    private SectionsPagerAdapter sectionsPagerAdapter;
    private Menu mMenu;
    private ActivityResultLauncher<Intent> requestSearchThingLauncher;
    private ActivitySubscribedThingListingBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        super.onCreate(savedInstanceState);

        binding = ActivitySubscribedThingListingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        EventBus.getDefault().register(this);

        applyCustomTheme();

        attachSliderPanelIfApplicable();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();

            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(binding.appbarLayoutSubscribedThingListingActivity);
            }

            if (isImmersiveInterfaceRespectForcedEdgeToEdge()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.setDecorFitsSystemWindows(false);
                } else {
                    window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                }

                ViewGroupCompat.installCompatInsetsDispatch(binding.getRoot());
                ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                    @NonNull
                    @Override
                    public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                        Insets allInsets = Utils.getInsets(insets, true, isForcedImmersiveInterface());

                        setMargins(binding.toolbarSubscribedThingListingActivity,
                                allInsets.left,
                                allInsets.top,
                                allInsets.right,
                                BaseActivity.IGNORE_MARGIN);

                        binding.viewPagerSubscribedThingListingActivity.setPadding(allInsets.left, 0, allInsets.right, 0);

                        setMargins(binding.fabSubscribedThingListingActivity,
                                BaseActivity.IGNORE_MARGIN,
                                BaseActivity.IGNORE_MARGIN,
                                (int) Utils.convertDpToPixel(16, SubscribedThingListingActivity.this) + allInsets.right,
                                (int) Utils.convertDpToPixel(16, SubscribedThingListingActivity.this) + allInsets.bottom);

                        return insets;
                    }
                });

                /*adjustToolbar(binding.toolbarSubscribedThingListingActivity);

                int navBarHeight = getNavBarHeight();

                if (navBarHeight > 0) {
                    CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) binding.fabSubscribedThingListingActivity.getLayoutParams();
                    params.bottomMargin += navBarHeight;
                    binding.fabSubscribedThingListingActivity.setLayoutParams(params);
                }*/
            }
        }

        setSupportActionBar(binding.toolbarSubscribedThingListingActivity);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setToolbarGoToTop(binding.toolbarSubscribedThingListingActivity);

        if (getIntent().hasExtra(EXTRA_SPECIFIED_ACCOUNT)) {
            Account specifiedAccount = getIntent().getParcelableExtra(EXTRA_SPECIFIED_ACCOUNT);

            if (specifiedAccount != null) {
                accessToken = specifiedAccount.getAccessToken();
                accountName = specifiedAccount.getAccountName();
                mAccountProfileImageUrl = specifiedAccount.getProfileImageUrl();

                mOauthRetrofit = mOauthRetrofit.newBuilder().client(new OkHttpClient.Builder().authenticator(new AnyAccountAccessTokenAuthenticator(APIUtils.getClientId(getApplicationContext()), mRetrofit, mRedditDataRoomDatabase, specifiedAccount, mCurrentAccountSharedPreferences))
                                .connectTimeout(30, TimeUnit.SECONDS)
                                .readTimeout(30, TimeUnit.SECONDS)
                                .writeTimeout(30, TimeUnit.SECONDS)
                                .connectionPool(new ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                                .build())
                        .build();
            } else {
                mAccountProfileImageUrl = mCurrentAccountSharedPreferences.getString(SharedPreferencesUtils.ACCOUNT_IMAGE_URL, null);
            }
        } else {
            mAccountProfileImageUrl = mCurrentAccountSharedPreferences.getString(SharedPreferencesUtils.ACCOUNT_IMAGE_URL, null);
        }

        if (savedInstanceState != null) {
            mInsertSuccess = savedInstanceState.getBoolean(INSERT_SUBSCRIBED_SUBREDDIT_STATE);
            mInsertMultiredditSuccess = savedInstanceState.getBoolean(INSERT_MULTIREDDIT_STATE);
        } else {
            showMultiReddits = getIntent().getBooleanExtra(EXTRA_SHOW_MULTIREDDITS, false);
        }

        isThingSelectionMode = getIntent().getBooleanExtra(EXTRA_THING_SELECTION_MODE, false);
        thingSelectionType = getIntent().getIntExtra(EXTRA_THING_SELECTION_TYPE, EXTRA_THING_SELECTION_TYPE_ALL);

        if (isThingSelectionMode) {
            if (thingSelectionType == EXTRA_THING_SELECTION_TYPE_SUBREDDIT) {
                getSupportActionBar().setTitle(R.string.subreddit_selection_activity_label);
            } else if (thingSelectionType == EXTRA_THING_SELECTION_TYPE_MULTIREDDIT) {
                getSupportActionBar().setTitle(R.string.multireddit_selection_activity_label);
            }
        }

        if (isThingSelectionMode && thingSelectionType != EXTRA_THING_SELECTION_TYPE_ALL) {
            binding.tabLayoutSubscribedThingListingActivity.setVisibility(View.GONE);
        }

        if (accountName.equals(Account.ANONYMOUS_ACCOUNT) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.searchEditTextSubscribedThingListingActivity.setImeOptions(binding.searchEditTextSubscribedThingListingActivity.getImeOptions() | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING);
        }

        searchPrefixOnly = false;
        binding.prefixMatchSwitchSubscribedThingListingActivity.setChecked(false);
        binding.prefixMatchSwitchSubscribedThingListingActivity.setOnCheckedChangeListener((buttonView, isChecked) -> {
            searchPrefixOnly = isChecked;
            applySearchQuery(binding.searchEditTextSubscribedThingListingActivity.getText().toString());
        });

        binding.searchEditTextSubscribedThingListingActivity.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void afterTextChanged(Editable editable) {
                applySearchQuery(editable.toString());
            }
        });

        requestSearchThingLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            setResult(RESULT_OK, result.getData());
            finish();
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.searchContainerSubscribedThingListingActivity.getVisibility() == View.VISIBLE) {
                    Utils.hideKeyboard(SubscribedThingListingActivity.this);
                    binding.searchContainerSubscribedThingListingActivity.setVisibility(View.GONE);
                    binding.searchEditTextSubscribedThingListingActivity.setText("");
                    mMenu.findItem(R.id.action_search_subscribed_thing_listing_activity).setVisible(true);
                    applySearchQuery("");
                } else {
                    setEnabled(false);
                    triggerBackPress();
                }
            }
        });

        initializeViewPagerAndLoadSubscriptions();
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
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutSubscribedThingListingActivity, binding.collapsingToolbarLayoutSubscribedThingListingActivity, binding.toolbarSubscribedThingListingActivity);
        applyAppBarScrollFlagsIfApplicable(binding.collapsingToolbarLayoutSubscribedThingListingActivity);
        applyTabLayoutTheme(binding.tabLayoutSubscribedThingListingActivity);
        applyFABTheme(binding.fabSubscribedThingListingActivity);
        binding.searchEditTextSubscribedThingListingActivity.setTextColor(mCustomThemeWrapper.getToolbarPrimaryTextAndIconColor());
        binding.searchEditTextSubscribedThingListingActivity.setHintTextColor(mCustomThemeWrapper.getToolbarSecondaryTextColor());
        binding.prefixMatchSwitchSubscribedThingListingActivity.setTextColor(mCustomThemeWrapper.getToolbarPrimaryTextAndIconColor());
    }

    private void initializeViewPagerAndLoadSubscriptions() {
        binding.fabSubscribedThingListingActivity.setOnClickListener(view -> {
            int currentItem = binding.viewPagerSubscribedThingListingActivity.getCurrentItem();
            if (currentItem == 0) {
                showGoToSubredditDialog();
            } else if (currentItem == 1) {
                showAddUserDialog();
            } else if (currentItem == 3) {
                showAddUserMultiredditDialog();
            } else {
                Intent intent = new Intent(this, CreateMultiRedditActivity.class);
                startActivity(intent);
            }
        });
        sectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
        binding.viewPagerSubscribedThingListingActivity.setAdapter(sectionsPagerAdapter);
        binding.viewPagerSubscribedThingListingActivity.setOffscreenPageLimit(3);

        if (!shouldShowFab(binding.viewPagerSubscribedThingListingActivity.getCurrentItem())) {
            binding.fabSubscribedThingListingActivity.hide();
        }

        binding.viewPagerSubscribedThingListingActivity.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                if (position == 0) {
                    unlockSwipeRightToGoBack();
                } else {
                    lockSwipeRightToGoBack();
                }
                if (shouldShowFab(position)) {
                    binding.fabSubscribedThingListingActivity.show();
                } else {
                    binding.fabSubscribedThingListingActivity.hide();
                }
            }
        });
        binding.tabLayoutSubscribedThingListingActivity.setupWithViewPager(binding.viewPagerSubscribedThingListingActivity);

        if (showMultiReddits) {
            binding.viewPagerSubscribedThingListingActivity.setCurrentItem(2, false);
        }

        loadSubscriptions(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.subscribed_thing_listing_activity, menu);
        mMenu = menu;
        applyMenuItemTheme(menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_search_subscribed_thing_listing_activity) {
            if (isThingSelectionMode) {
                Intent intent = new Intent(this, SearchActivity.class);
                if (thingSelectionType == EXTRA_THING_SELECTION_TYPE_SUBREDDIT) {
                    intent.putExtra(SearchActivity.EXTRA_SEARCH_ONLY_SUBREDDITS, true);
                } else if (thingSelectionType == EXTRA_THING_SELECTION_TYPE_USER) {
                    intent.putExtra(SearchActivity.EXTRA_SEARCH_ONLY_USERS, true);
                } else if (thingSelectionType == EXTRA_THING_SELECTION_TYPE_MULTIREDDIT) {
                    item.setVisible(false);
                    binding.searchContainerSubscribedThingListingActivity.setVisibility(View.VISIBLE);
                    binding.searchEditTextSubscribedThingListingActivity.requestFocus();
                    if (binding.searchEditTextSubscribedThingListingActivity.requestFocus()) {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.showSoftInput(binding.searchEditTextSubscribedThingListingActivity, InputMethodManager.SHOW_IMPLICIT);
                    }

                    return true;
                } else {
                    intent.putExtra(SearchActivity.EXTRA_SEARCH_SUBREDDITS_AND_USERS, true);
                }

                requestSearchThingLauncher.launch(intent);

                return true;
            }

            item.setVisible(false);
            binding.searchContainerSubscribedThingListingActivity.setVisibility(View.VISIBLE);
            binding.searchEditTextSubscribedThingListingActivity.requestFocus();

            if (binding.searchEditTextSubscribedThingListingActivity.requestFocus()) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(binding.searchEditTextSubscribedThingListingActivity, InputMethodManager.SHOW_IMPLICIT);
            }

            return true;
        } else if (item.getItemId() == android.R.id.home) {
            triggerBackPress();
            return true;
        }

        return false;
    }

    private void applySearchQuery(String query) {
        sectionsPagerAdapter.changeSearchQuery(searchPrefixOnly ? query + "%" : "%" + query + "%");
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(INSERT_SUBSCRIBED_SUBREDDIT_STATE, mInsertSuccess);
        outState.putBoolean(INSERT_MULTIREDDIT_STATE, mInsertMultiredditSuccess);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    public void loadSubscriptions(boolean forceLoad) {
        if (!forceLoad && System.currentTimeMillis() - mCurrentAccountSharedPreferences.getLong(SharedPreferencesUtils.SUBSCRIBED_THINGS_SYNC_TIME, 0L) < 24 * 60 * 60 * 1000) {
            return;
        }

        if (!accountName.equals(Account.ANONYMOUS_ACCOUNT) && !(!forceLoad && mInsertSuccess)) {
            FetchSubscribedThing.fetchSubscribedThing(mExecutor, mHandler, mOauthRetrofit, accessToken, accountName, null,
                    new ArrayList<>(), new ArrayList<>(),
                    new ArrayList<>(),
                    new FetchSubscribedThing.FetchSubscribedThingListener() {
                        @Override
                        public void onFetchSubscribedThingSuccess(ArrayList<SubscribedSubredditData> subscribedSubredditData, ArrayList<SubscribedUserData> subscribedUserData, ArrayList<SubredditData> subredditData) {
                            mCurrentAccountSharedPreferences.edit().putLong(SharedPreferencesUtils.SUBSCRIBED_THINGS_SYNC_TIME, System.currentTimeMillis()).apply();
                            InsertSubscribedThings.insertSubscribedThings(
                                mExecutor,
                                new Handler(),
                                mRedditDataRoomDatabase,
                                accountName,
                                subscribedSubredditData,
                                subscribedUserData,
                                subredditData,
                                () -> {
                                    mInsertSuccess = true;
                                    sectionsPagerAdapter.stopRefreshProgressbar();
                                });
                        }

                        @Override
                        public void onFetchSubscribedThingFail() {
                            mInsertSuccess = false;
                            sectionsPagerAdapter.stopRefreshProgressbar();
                            Toast.makeText(SubscribedThingListingActivity.this, R.string.error_loading_subscriptions, Toast.LENGTH_SHORT).show();
                        }
                    });
        }

        if (!(!forceLoad && mInsertMultiredditSuccess)) {
            loadMultiReddits();
        }
    }

    private boolean shouldShowFab(int position) {
        // Only when browsing (not picking a thing): in selection mode the tab set/order differs and a
        // picker has no add action. Otherwise every tab has one: Subreddits (0, go to subreddit),
        // Users (1, follow a user), MultiReddits (2, create), Users MultiReddits (3, add a user's feeds).
        return !isThingSelectionMode && position >= 0 && position <= 3;
    }

    public void showFabInMultiredditTab() {
        if (shouldShowFab(binding.viewPagerSubscribedThingListingActivity.getCurrentItem())) {
            binding.fabSubscribedThingListingActivity.show();
        }
    }

    public void hideFabInMultiredditTab() {
        if (shouldShowFab(binding.viewPagerSubscribedThingListingActivity.getCurrentItem())) {
            binding.fabSubscribedThingListingActivity.hide();
        }
    }

    private void loadMultiReddits() {
        if (!accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
            FetchMyMultiReddits.fetchMyMultiReddits(mExecutor, mHandler, mOauthRetrofit, accessToken,
                    new FetchMyMultiReddits.FetchMyMultiRedditsListener() {
                        @Override
                        public void success(ArrayList<MultiReddit> multiReddits) {
                            InsertMultireddit.insertMultireddits(mExecutor, new Handler(), mRedditDataRoomDatabase, multiReddits, accountName, () -> {
                                mInsertMultiredditSuccess = true;
                                sectionsPagerAdapter.stopMultiRedditRefreshProgressbar();
                            });
                        }

                        @Override
                        public void failed() {
                            mInsertMultiredditSuccess = false;
                            sectionsPagerAdapter.stopMultiRedditRefreshProgressbar();
                            Toast.makeText(SubscribedThingListingActivity.this, R.string.error_loading_multi_reddit_list, Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    public void deleteMultiReddit(MultiReddit multiReddit) {
        if (multiReddit != null && multiReddit.isFollowed()) {
            new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                    .setTitle(R.string.remove_followed_multi_reddit)
                    .setMessage(R.string.remove_followed_multi_reddit_dialog_message)
                    .setPositiveButton(R.string.remove, (dialogInterface, i) -> mExecutor.execute(() -> {
                        mRedditDataRoomDatabase.multiRedditDao().deleteFollowedMultiReddit(multiReddit.getPath(), accountName);
                        mHandler.post(() -> Toast.makeText(SubscribedThingListingActivity.this, R.string.remove_followed_multi_reddit_success, Toast.LENGTH_SHORT).show());
                    }))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle(R.string.delete)
                .setMessage(R.string.delete_multi_reddit_dialog_message)
                .setPositiveButton(R.string.delete, (dialogInterface, i)
                        -> {
                    if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
                        DeleteMultiredditInDatabase.deleteMultiredditInDatabase(mExecutor, new Handler(), mRedditDataRoomDatabase, accountName, multiReddit.getPath(),
                                () -> Toast.makeText(SubscribedThingListingActivity.this, R.string.delete_multi_reddit_success, Toast.LENGTH_SHORT).show());
                    } else {
                        DeleteMultiReddit.deleteMultiReddit(mExecutor, new Handler(), mOauthRetrofit, mRedditDataRoomDatabase,
                                accessToken, accountName, multiReddit.getPath(), new DeleteMultiReddit.DeleteMultiRedditListener() {
                                    @Override
                                    public void success() {
                                        Toast.makeText(SubscribedThingListingActivity.this, R.string.delete_multi_reddit_success, Toast.LENGTH_SHORT).show();
                                        loadMultiReddits();
                                    }

                                    @Override
                                    public void failed() {
                                        Toast.makeText(SubscribedThingListingActivity.this, R.string.delete_multi_reddit_failed, Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showGoToSubredditDialog() {
        View rootView = getLayoutInflater().inflate(R.layout.dialog_go_to_thing_edit_text, (ViewGroup) binding.getRoot(), false);
        TextInputEditText thingEditText = rootView.findViewById(R.id.text_input_edit_text_go_to_thing_edit_text);
        thingEditText.setHint(R.string.go_to_subreddit);
        RecyclerView recyclerView = rootView.findViewById(R.id.recycler_view_go_to_thing_edit_text);
        SubredditAutocompleteRecyclerViewAdapter adapter = new SubredditAutocompleteRecyclerViewAdapter(
                this, mCustomThemeWrapper, subredditData -> {
            Utils.hideKeyboard(this);
            goToSubreddit(subredditData.getName());
        });
        recyclerView.setAdapter(adapter);

        thingEditText.requestFocus();
        Utils.showKeyboard(this, new Handler(), thingEditText);
        thingEditText.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE) {
                Utils.hideKeyboard(this);
                goToSubreddit(thingEditText.getText().toString());
                return true;
            }
            return false;
        });

        boolean nsfw = mNsfwAndSpoilerSharedPreferences.getBoolean((accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : accountName) + SharedPreferencesUtils.NSFW_BASE, false);
        Handler handler = new Handler();
        thingEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (subredditAutocompleteCall != null && subredditAutocompleteCall.isExecuted()) {
                    subredditAutocompleteCall.cancel();
                }
                if (autoCompleteRunnable != null) {
                    handler.removeCallbacks(autoCompleteRunnable);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (Account.ANONYMOUS_ACCOUNT.equals(accountName)) {
                    return;
                }
                String currentQuery = editable.toString().trim();
                if (!currentQuery.isEmpty()) {
                    autoCompleteRunnable = () -> {
                        subredditAutocompleteCall = mOauthRetrofit.create(RedditAPI.class).subredditAutocomplete(
                                APIUtils.getOAuthHeader(accessToken), currentQuery, nsfw);
                        subredditAutocompleteCall.enqueue(new retrofit2.Callback<>() {
                            @Override
                            public void onResponse(@NonNull retrofit2.Call<String> call, @NonNull retrofit2.Response<String> response) {
                                subredditAutocompleteCall = null;
                                if (response.isSuccessful() && !call.isCanceled()) {
                                    ParseSubredditData.parseSubredditListingData(mExecutor, handler, response.body(), nsfw,
                                            new ParseSubredditData.ParseSubredditListingDataListener() {
                                                @Override
                                                public void onParseSubredditListingDataSuccess(ArrayList<SubredditData> subredditData, String after) {
                                                    adapter.setSubreddits(subredditData);
                                                }

                                                @Override
                                                public void onParseSubredditListingDataFail() {}
                                            });
                                }
                            }

                            @Override
                            public void onFailure(@NonNull retrofit2.Call<String> call, @NonNull Throwable t) {
                                subredditAutocompleteCall = null;
                            }
                        });
                    };
                    handler.postDelayed(autoCompleteRunnable, 500);
                }
            }
        });

        new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle(R.string.go_to_subreddit)
                .setView(rootView)
                .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                    Utils.hideKeyboard(this);
                    goToSubreddit(thingEditText.getText().toString());
                })
                .setNegativeButton(R.string.cancel, (dialogInterface, i) -> Utils.hideKeyboard(this))
                .setOnDismissListener(dialogInterface -> {
                    Utils.hideKeyboard(this);
                    if (subredditAutocompleteCall != null) {
                        subredditAutocompleteCall.cancel();
                        subredditAutocompleteCall = null;
                    }
                    if (autoCompleteRunnable != null) {
                        handler.removeCallbacks(autoCompleteRunnable);
                        autoCompleteRunnable = null;
                    }
                })
                .show();
    }

    private void goToSubreddit(String subredditName) {
        String name = subredditName == null ? "" : subredditName.trim();
        if (name.startsWith("/r/")) {
            name = name.substring(3);
        } else if (name.startsWith("r/")) {
            name = name.substring(2);
        }
        name = name.trim();
        if (name.isEmpty()) {
            return;
        }
        Intent intent = new Intent(this, ViewSubredditDetailActivity.class);
        intent.putExtra(ViewSubredditDetailActivity.EXTRA_SUBREDDIT_NAME_KEY, name);
        startActivity(intent);
    }

    private interface OnUsernameEnteredListener {
        void onUsernameEntered(String username);
    }

    private void showAddUserMultiredditDialog() {
        showAddUsernameDialog(R.string.add_user_multireddit_title, this::openUserMultiReddits);
    }

    private void showAddUserDialog() {
        showAddUsernameDialog(R.string.add_user_title, this::followUser);
    }

    private void showAddUsernameDialog(int titleRes, OnUsernameEnteredListener listener) {
        View rootView = getLayoutInflater().inflate(R.layout.dialog_add_user_multireddit, (ViewGroup) binding.getRoot(), false);
        AutoCompleteTextView usernameEditText = rootView.findViewById(R.id.auto_complete_text_view_add_user_multireddit);
        usernameEditText.requestFocus();
        Utils.showKeyboard(this, new Handler(), usernameEditText);
        loadUsernameSuggestions(usernameEditText);
        usernameEditText.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE) {
                Utils.hideKeyboard(this);
                listener.onUsernameEntered(usernameEditText.getText().toString());
                return true;
            }
            return false;
        });
        new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle(titleRes)
                .setView(rootView)
                .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                    Utils.hideKeyboard(this);
                    listener.onUsernameEntered(usernameEditText.getText().toString());
                })
                .setNegativeButton(R.string.cancel, (dialogInterface, i) -> Utils.hideKeyboard(this))
                .setOnDismissListener(dialogInterface -> Utils.hideKeyboard(this))
                .show();
    }

    /**
     * Suggests usernames the user already knows: owners of feeds in the Users MultiReddits tab
     * (parsed from {@code /user/<owner>/m/<name>} paths) and users from the Users tab.
     */
    private void loadUsernameSuggestions(AutoCompleteTextView usernameEditText) {
        mExecutor.execute(() -> {
            Set<String> usernames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (String path : mRedditDataRoomDatabase.multiRedditDao().getFollowedMultiRedditPaths(accountName)) {
                if (path != null) {
                    String[] segments = path.split("/");
                    if (segments.length > 2 && "user".equals(segments[1])) {
                        usernames.add(segments[2]);
                    }
                }
            }
            for (SubscribedUserData user : mRedditDataRoomDatabase.subscribedUserDao().getAllSubscribedUsersList(accountName)) {
                if (user.getName() != null) {
                    usernames.add(user.getName());
                }
            }
            ArrayList<String> suggestions = new ArrayList<>(usernames);
            mHandler.post(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    usernameEditText.setAdapter(new ArrayAdapter<>(SubscribedThingListingActivity.this,
                            android.R.layout.simple_dropdown_item_1line, suggestions));
                }
            });
        });
    }

    private String cleanUsername(String input) {
        String username = input == null ? "" : input.trim();
        if (username.startsWith("/u/")) {
            username = username.substring(3);
        } else if (username.startsWith("u/")) {
            username = username.substring(2);
        } else if (username.startsWith("@")) {
            username = username.substring(1);
        }
        return username.trim();
    }

    private void openUserMultiReddits(String input) {
        String username = cleanUsername(input);
        if (username.isEmpty()) {
            return;
        }
        Intent intent = new Intent(this, UserMultiRedditsActivity.class);
        intent.putExtra(UserMultiRedditsActivity.EXTRA_USERNAME, username);
        startActivity(intent);
    }

    private void followUser(String input) {
        String username = cleanUsername(input);
        if (username.isEmpty()) {
            return;
        }
        UserFollowing.UserFollowingListener listener = new UserFollowing.UserFollowingListener() {
            @Override
            public void onUserFollowingSuccess() {
                Toast.makeText(SubscribedThingListingActivity.this, R.string.followed, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onUserFollowingFail() {
                showFollowFailureReason(username);
            }
        };
        if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
            UserFollowing.anonymousFollowUser(mExecutor, mHandler, mRetrofit, username, mRedditDataRoomDatabase, listener);
        } else {
            UserFollowing.followUser(mExecutor, mHandler, mOauthRetrofit, mRetrofit, accessToken, username, accountName, mRedditDataRoomDatabase, listener);
        }
    }

    /**
     * A follow can fail for several reasons. Look up the about endpoint and tell the user the actual
     * reason: suspended, deleted/non-existent (404), blocked, NSFW-gated, or otherwise not followable.
     * {@code 0} means "no specific reason found" and falls back to the generic message.
     */
    private void showFollowFailureReason(String username) {
        mExecutor.execute(() -> {
            int messageRes = 0;
            try {
                retrofit2.Response<String> response;
                if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
                    response = mRetrofit.create(RedditAPI.class).getUserData(username).execute();
                } else {
                    response = mOauthRetrofit.create(RedditAPI.class)
                            .getUserDataOauth(APIUtils.getOAuthHeader(accessToken), username).execute();
                }
                if (response.code() == 404) {
                    messageRes = R.string.follow_failed_user_not_found;
                } else if (response.isSuccessful() && response.body() != null) {
                    JSONObject data = new JSONObject(response.body()).optJSONObject(JSONUtils.DATA_KEY);
                    if (data != null) {
                        if (data.optBoolean(JSONUtils.IS_SUSPENDED_KEY, false)) {
                            messageRes = R.string.follow_failed_user_suspended;
                        } else if (data.optBoolean(JSONUtils.IS_BLOCKED_KEY, false)) {
                            messageRes = R.string.follow_failed_user_blocked;
                        } else {
                            JSONObject subreddit = data.optJSONObject(JSONUtils.SUBREDDIT_KEY);
                            if (subreddit == null) {
                                messageRes = R.string.follow_failed_user_cannot_be_followed;
                            } else if (subreddit.optBoolean(JSONUtils.OVER_18_KEY, false)) {
                                messageRes = R.string.follow_failed_user_nsfw;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            int finalMessageRes = messageRes;
            mHandler.post(() -> {
                if (finalMessageRes != 0) {
                    Toast.makeText(SubscribedThingListingActivity.this,
                            getString(finalMessageRes, username), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(SubscribedThingListingActivity.this, R.string.follow_failed, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Subscribe
    public void onAccountSwitchEvent(SwitchAccountEvent event) {
        finish();
    }

    @Subscribe
    public void goBackToMainPageEvent(GoBackToMainPageEvent event) {
        finish();
    }

    @Subscribe
    public void onRefreshMultiRedditsEvent(RefreshMultiRedditsEvent event) {
        loadMultiReddits();
    }

    @Override
    public void onLongPress() {
        if (sectionsPagerAdapter != null) {
            sectionsPagerAdapter.goBackToTop();
        }
    }

    @Override
    public void lockSwipeRightToGoBack() {
        if (mSliderPanel != null) {
            mSliderPanel.lock();
        }
    }

    @Override
    public void unlockSwipeRightToGoBack() {
        if (mSliderPanel != null) {
            mSliderPanel.unlock();
        }
    }

    private class SectionsPagerAdapter extends FragmentPagerAdapter {

        @Nullable
        private SubscribedSubredditsListingFragment subscribedSubredditsListingFragment;
        @Nullable
        private FollowedUsersListingFragment followedUsersListingFragment;
        @Nullable
        private MultiRedditListingFragment multiRedditListingFragment;
        @Nullable
        private FollowedMultiRedditListingFragment followedMultiRedditListingFragment;

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            if (isThingSelectionMode) {
                switch (thingSelectionType) {
                    case EXTRA_THING_SELECTION_TYPE_SUBREDDIT:
                        return getSubscribedSubredditListingFragment();
                    case EXTRA_THING_SELECTION_TYPE_USER:
                        return getFollowedUserFragment();
                    case EXTRA_THING_SELECTION_TYPE_MULTIREDDIT:
                        return getMultiRedditListingFragment();
                    default:
                        switch (position) {
                            case 0:
                                return getSubscribedSubredditListingFragment();
                            case 1:
                                return getFollowedUserFragment();
                            default:
                                return getMultiRedditListingFragment();
                        }
                }
            }

            switch (position) {
                case 0:
                    return getSubscribedSubredditListingFragment();
                case 1:
                    return getFollowedUserFragment();
                case 2:
                    return getMultiRedditListingFragment();
                default:
                    return getFollowedMultiRedditListingFragment();
            }
        }

        @NonNull
        private Fragment getSubscribedSubredditListingFragment() {
            SubscribedSubredditsListingFragment fragment = new SubscribedSubredditsListingFragment();
            Bundle bundle = new Bundle();
            bundle.putBoolean(SubscribedSubredditsListingFragment.EXTRA_IS_SUBREDDIT_SELECTION, isThingSelectionMode);
            bundle.putBoolean(SubscribedSubredditsListingFragment.EXTRA_EXTRA_CLEAR_SELECTION, isThingSelectionMode && getIntent().getBooleanExtra(EXTRA_EXTRA_CLEAR_SELECTION, false));
            bundle.putString(SubscribedSubredditsListingFragment.EXTRA_ACCOUNT_PROFILE_IMAGE_URL, mAccountProfileImageUrl);
            fragment.setArguments(bundle);

            return fragment;
        }

        @NonNull
        private Fragment getFollowedUserFragment() {
            FollowedUsersListingFragment fragment = new FollowedUsersListingFragment();
            Bundle bundle = new Bundle();
            bundle.putBoolean(FollowedUsersListingFragment.EXTRA_IS_USER_SELECTION, isThingSelectionMode);
            fragment.setArguments(bundle);

            return fragment;
        }

        @NonNull
        private Fragment getMultiRedditListingFragment() {
            MultiRedditListingFragment fragment = new MultiRedditListingFragment();
            Bundle bundle = new Bundle();
            bundle.putBoolean(MultiRedditListingFragment.EXTRA_IS_MULTIREDDIT_SELECTION, isThingSelectionMode);
            fragment.setArguments(bundle);

            return fragment;
        }

        @NonNull
        private Fragment getFollowedMultiRedditListingFragment() {
            FollowedMultiRedditListingFragment fragment = new FollowedMultiRedditListingFragment();
            Bundle bundle = new Bundle();
            bundle.putBoolean(MultiRedditListingFragment.EXTRA_IS_MULTIREDDIT_SELECTION, false);
            bundle.putBoolean(MultiRedditListingFragment.EXTRA_IS_FOLLOWED, true);
            fragment.setArguments(bundle);

            return fragment;
        }

        @Override
        public int getCount() {
            if (isThingSelectionMode) {
                switch (thingSelectionType) {
                    case EXTRA_THING_SELECTION_TYPE_ALL:
                        return Account.ANONYMOUS_ACCOUNT.equals(accountName) ? 2 : 3;
                    case EXTRA_THING_SELECTION_TYPE_SUBREDDIT:
                    case EXTRA_THING_SELECTION_TYPE_USER:
                    case EXTRA_THING_SELECTION_TYPE_MULTIREDDIT:
                        return 1;
                }
            }
            return 4;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (isThingSelectionMode) {
                switch (thingSelectionType) {
                    case EXTRA_THING_SELECTION_TYPE_ALL:
                        switch (position) {
                            case 0:
                                return Utils.getTabTextWithCustomFont(typeface, getString(R.string.subreddits));
                            case 1:
                                return Utils.getTabTextWithCustomFont(typeface, getString(R.string.users));
                            case 2:
                                return Utils.getTabTextWithCustomFont(typeface, getString(R.string.multi_reddits));
                        }
                    case EXTRA_THING_SELECTION_TYPE_SUBREDDIT:
                        return Utils.getTabTextWithCustomFont(typeface, getString(R.string.subreddits));
                    case EXTRA_THING_SELECTION_TYPE_USER:
                        return Utils.getTabTextWithCustomFont(typeface, getString(R.string.users));
                    case EXTRA_THING_SELECTION_TYPE_MULTIREDDIT:
                        return Utils.getTabTextWithCustomFont(typeface, getString(R.string.multi_reddits));
                }
            }

            switch (position) {
                case 0:
                    return Utils.getTabTextWithCustomFont(typeface, getString(R.string.subreddits));
                case 1:
                    return Utils.getTabTextWithCustomFont(typeface, getString(R.string.users));
                case 2:
                    return Utils.getTabTextWithCustomFont(typeface, getString(R.string.multi_reddits));
                case 3:
                    return Utils.getTabTextWithCustomFont(typeface, getString(R.string.users_multireddits));
            }

            return null;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            if (fragment instanceof SubscribedSubredditsListingFragment) {
                subscribedSubredditsListingFragment = (SubscribedSubredditsListingFragment) fragment;
            } else if (fragment instanceof FollowedUsersListingFragment) {
                followedUsersListingFragment = (FollowedUsersListingFragment) fragment;
            } else if (fragment instanceof FollowedMultiRedditListingFragment) {
                followedMultiRedditListingFragment = (FollowedMultiRedditListingFragment) fragment;
            } else if (fragment instanceof MultiRedditListingFragment) {
                multiRedditListingFragment = (MultiRedditListingFragment) fragment;
            }

            return fragment;
        }

        void stopRefreshProgressbar() {
            if (subscribedSubredditsListingFragment != null) {
                ((FragmentCommunicator) subscribedSubredditsListingFragment).stopRefreshProgressbar();
            }
            if (followedUsersListingFragment != null) {
                ((FragmentCommunicator) followedUsersListingFragment).stopRefreshProgressbar();
            }
        }

        void stopMultiRedditRefreshProgressbar() {
            if (multiRedditListingFragment != null) {
                ((FragmentCommunicator) multiRedditListingFragment).stopRefreshProgressbar();
            }
        }

        @Nullable
        Fragment getCurrentFragment() {
            List<Fragment> fragments = getSupportFragmentManager().getFragments();
            if (binding.viewPagerSubscribedThingListingActivity.getCurrentItem() < fragments.size()) {
                return fragments.get(binding.viewPagerSubscribedThingListingActivity.getCurrentItem());
            }

            return null;
        }

        void goBackToTop() {
            Fragment fragment = getCurrentFragment();
            if (fragment instanceof SubscribedSubredditsListingFragment) {
                ((SubscribedSubredditsListingFragment) fragment).goBackToTop();
            } else if (fragment instanceof FollowedUsersListingFragment) {
                ((FollowedUsersListingFragment) fragment).goBackToTop();
            } else if (fragment instanceof MultiRedditListingFragment) {
                ((MultiRedditListingFragment) fragment).goBackToTop();
            }
        }

        void changeSearchQuery(String searchQuery) {
            if (subscribedSubredditsListingFragment != null) {
                subscribedSubredditsListingFragment.changeSearchQuery(searchQuery);
            }

            if (followedUsersListingFragment != null) {
                followedUsersListingFragment.changeSearchQuery(searchQuery);
            }

            if (multiRedditListingFragment != null) {
                multiRedditListingFragment.changeSearchQuery(searchQuery);
            }

            if (followedMultiRedditListingFragment != null) {
                followedMultiRedditListingFragment.changeSearchQuery(searchQuery);
            }
        }
    }
}
