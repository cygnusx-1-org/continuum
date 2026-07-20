package ml.docilealligator.infinityforreddit.activities;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.PersistableBundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Named;
import jp.wasabeef.glide.transformations.RoundedCornersTransformation;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.asynctasks.FetchCrosspostableSubreddits;
import ml.docilealligator.infinityforreddit.asynctasks.FlairRequirementController;
import ml.docilealligator.infinityforreddit.asynctasks.LoadSubredditIcon;
import ml.docilealligator.infinityforreddit.bottomsheetfragments.AccountChooserBottomSheetFragment;
import ml.docilealligator.infinityforreddit.bottomsheetfragments.FlairBottomSheetFragment;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ActivitySubmitCrosspostBinding;
import ml.docilealligator.infinityforreddit.events.SubmitCrosspostEvent;
import ml.docilealligator.infinityforreddit.events.SwitchAccountEvent;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.services.SubmitPostService;
import ml.docilealligator.infinityforreddit.subreddit.Flair;
import ml.docilealligator.infinityforreddit.thing.SelectThingReturnKey;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.JSONUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class SubmitCrosspostActivity extends BaseActivity implements FlairBottomSheetFragment.FlairSelectionCallback,
        AccountChooserBottomSheetFragment.AccountChooserListener {

    public static final String EXTRA_POST = "EP";

    private static final String SELECTED_ACCOUNT_STATE = "SAS";
    private static final String SUBREDDIT_NAME_STATE = "SNS";
    private static final String SUBREDDIT_ICON_STATE = "SIS";
    private static final String SUBREDDIT_SELECTED_STATE = "SSS";
    private static final String SUBREDDIT_IS_USER_STATE = "SIUS";
    private static final String LOAD_SUBREDDIT_ICON_STATE = "LSIS";
    private static final String IS_POSTING_STATE = "IPS";
    private static final String FLAIR_STATE = "FS";
    private static final String IS_SPOILER_STATE = "ISS";
    private static final String IS_NSFW_STATE = "INS";

    private static final int SUBREDDIT_SELECTION_REQUEST_CODE = 0;

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
    @Nullable
    private Account selectedAccount;
    /** Set once the current-account read lands, so a null {@link #selectedAccount} can tell
     * "no account exists" apart from "still loading". */
    private boolean accountLoadFinished;
    private Post post;
    @Nullable
    private String iconUrl;
    @Nullable
    private String subredditName;
    private boolean subredditSelected = false;
    private boolean subredditIsUser;
    private boolean loadSubredditIconSuccessful = true;
    private boolean isPosting;
    private int primaryTextColor;
    private int flairBackgroundColor;
    private int flairTextColor;
    private int spoilerBackgroundColor;
    private int spoilerTextColor;
    private int nsfwBackgroundColor;
    private int nsfwTextColor;
    @Nullable
    private Flair flair;
    private boolean isSpoiler = false;
    private boolean isNSFW = false;
    private Resources resources;
    private RequestManager mGlide;
    @Nullable
    private FlairBottomSheetFragment flairSelectionBottomSheetFragment;
    private Snackbar mPostingSnackbar;
    private ActivitySubmitCrosspostBinding binding;
    @Nullable
    private Set<String> crosspostableSubreddits;
    private boolean crosspostableLoadFailed = false;
    private FlairRequirementController flairController;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        setImmersiveModeNotApplicableBelowAndroid16();

        super.onCreate(savedInstanceState);

        binding = ActivitySubmitCrosspostBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        EventBus.getDefault().register(this);

        applyCustomTheme();

        if (isImmersiveInterfaceRespectForcedEdgeToEdge()) {
            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(binding.appbarLayoutSubmitCrosspostActivity);
            }

            ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                    Insets allInsets = Utils.getInsets(insets, true, isForcedImmersiveInterface());

                    setMargins(binding.toolbarSubmitCrosspostActivity,
                            allInsets.left,
                            allInsets.top,
                            allInsets.right,
                            BaseActivity.IGNORE_MARGIN);

                    binding.nestedScrollViewSubmitCrosspostActivity.setPadding(
                            allInsets.left,
                            0,
                            allInsets.right,
                            allInsets.bottom);

                    return WindowInsetsCompat.CONSUMED;
                }
            });
        }

        setSupportActionBar(binding.toolbarSubmitCrosspostActivity);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        mGlide = Glide.with(this);

        mPostingSnackbar = Snackbar.make(binding.getRoot(), R.string.posting, Snackbar.LENGTH_INDEFINITE);

        resources = getResources();

        // Both launch sites always supply the post being crossposted; without it there is
        // nothing for this screen to do.
        post = Objects.requireNonNull(getIntent().getParcelableExtra(EXTRA_POST));

        flairController = new FlairRequirementController(mOauthRetrofit,
                R.id.action_send_submit_crosspost_activity,
                this::applyFlairLabelStyle);

        fetchCrosspostableSubreddits();

        if (savedInstanceState != null) {
            selectedAccount = savedInstanceState.getParcelable(SELECTED_ACCOUNT_STATE);
            subredditName = savedInstanceState.getString(SUBREDDIT_NAME_STATE);
            iconUrl = savedInstanceState.getString(SUBREDDIT_ICON_STATE);
            subredditSelected = savedInstanceState.getBoolean(SUBREDDIT_SELECTED_STATE);
            subredditIsUser = savedInstanceState.getBoolean(SUBREDDIT_IS_USER_STATE);
            loadSubredditIconSuccessful = savedInstanceState.getBoolean(LOAD_SUBREDDIT_ICON_STATE);
            isPosting = savedInstanceState.getBoolean(IS_POSTING_STATE);
            flair = savedInstanceState.getParcelable(FLAIR_STATE);
            isSpoiler = savedInstanceState.getBoolean(IS_SPOILER_STATE);
            isNSFW = savedInstanceState.getBoolean(IS_NSFW_STATE);

            if (selectedAccount != null) {
                mGlide.load(selectedAccount.getProfileImageUrl())
                        .transform(new RoundedCornersTransformation(72, 0))
                        .error(mGlide.load(R.drawable.subreddit_default_icon)
                                .transform(new RoundedCornersTransformation(72, 0)))
                        .into(binding.accountIconGifImageViewSubmitCrosspostActivity);

                binding.accountNameTextViewSubmitCrosspostActivity.setText(selectedAccount.getAccountName());
            } else {
                loadCurrentAccount();
            }

            if (subredditName != null) {
                binding.subredditNameTextViewSubmitCrosspostActivity.setTextColor(primaryTextColor);
                binding.subredditNameTextViewSubmitCrosspostActivity.setText(subredditName);
                binding.flairCustomTextViewSubmitCrosspostActivity.setVisibility(View.VISIBLE);
                onSubredditChangedNotifyController(subredditName, subredditIsUser);
                if (!loadSubredditIconSuccessful) {
                    loadSubredditIcon();
                }
            }
            displaySubredditIcon();

            if (isPosting) {
                mPostingSnackbar.show();
            }

            if (flair != null) {
                binding.flairCustomTextViewSubmitCrosspostActivity.setText(flair.getText());
                binding.flairCustomTextViewSubmitCrosspostActivity.setBackgroundColor(flairBackgroundColor);
                binding.flairCustomTextViewSubmitCrosspostActivity.setBorderColor(flairBackgroundColor);
                binding.flairCustomTextViewSubmitCrosspostActivity.setTextColor(flairTextColor);
            }
            if (isSpoiler) {
                binding.spoilerCustomTextViewSubmitCrosspostActivity.setBackgroundColor(spoilerBackgroundColor);
                binding.spoilerCustomTextViewSubmitCrosspostActivity.setBorderColor(spoilerBackgroundColor);
                binding.spoilerCustomTextViewSubmitCrosspostActivity.setTextColor(spoilerTextColor);
            }
            if (isNSFW) {
                binding.nsfwCustomTextViewSubmitCrosspostActivity.setBackgroundColor(nsfwBackgroundColor);
                binding.nsfwCustomTextViewSubmitCrosspostActivity.setBorderColor(nsfwBackgroundColor);
                binding.nsfwCustomTextViewSubmitCrosspostActivity.setTextColor(nsfwTextColor);
            }
        } else {
            isPosting = false;

            loadCurrentAccount();

            mGlide.load(R.drawable.subreddit_default_icon)
                    .transform(new RoundedCornersTransformation(72, 0))
                    .into(binding.subredditIconGifImageViewSubmitCrosspostActivity);

            if (post.isSpoiler()) {
                binding.spoilerCustomTextViewSubmitCrosspostActivity.setBackgroundColor(spoilerBackgroundColor);
                binding.spoilerCustomTextViewSubmitCrosspostActivity.setBorderColor(spoilerBackgroundColor);
                binding.spoilerCustomTextViewSubmitCrosspostActivity.setTextColor(spoilerTextColor);
            }
            if (post.isNSFW()) {
                binding.nsfwCustomTextViewSubmitCrosspostActivity.setBackgroundColor(nsfwBackgroundColor);
                binding.nsfwCustomTextViewSubmitCrosspostActivity.setBorderColor(nsfwBackgroundColor);
                binding.nsfwCustomTextViewSubmitCrosspostActivity.setTextColor(nsfwTextColor);
            }

            binding.postTitleEditTextSubmitCrosspostActivity.setText(post.getTitle());
        }

        if (post.getPostType() == Post.TEXT_TYPE) {
            binding.postContentTextViewSubmitCrosspostActivity.setVisibility(View.VISIBLE);
            binding.postContentTextViewSubmitCrosspostActivity.setText(post.getSelfTextPlain());
        } else if (post.getPostType() == Post.LINK_TYPE || post.getPostType() == Post.NO_PREVIEW_LINK_TYPE) {
            binding.postContentTextViewSubmitCrosspostActivity.setVisibility(View.VISIBLE);
            binding.postContentTextViewSubmitCrosspostActivity.setText(post.getUrl());
        } else {
            Post.Preview preview = getPreview(post);
            if (preview != null) {
                binding.frameLayoutSubmitCrosspostActivity.setVisibility(View.VISIBLE);
                mGlide.asBitmap().load(preview.getPreviewUrl()).into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        binding.imageViewSubmitCrosspostActivity.setImage(ImageSource.bitmap(resource));
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {

                    }
                });

                if (post.getPostType() == Post.VIDEO_TYPE || post.getPostType() == Post.GIF_TYPE) {
                    binding.playButtonImageViewSubmitCrosspostActivity.setVisibility(View.VISIBLE);
                    binding.playButtonImageViewSubmitCrosspostActivity.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_play_circle_36dp));
                } else if (post.getPostType() == Post.GALLERY_TYPE) {
                    binding.playButtonImageViewSubmitCrosspostActivity.setVisibility(View.VISIBLE);
                    binding.playButtonImageViewSubmitCrosspostActivity.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_gallery_day_night_24dp));
                }
            }
        }

        binding.accountLinearLayoutSubmitCrosspostActivity.setOnClickListener(view -> {
            AccountChooserBottomSheetFragment fragment = new AccountChooserBottomSheetFragment();
            fragment.show(getSupportFragmentManager(), fragment.getTag());
        });

        binding.subredditIconGifImageViewSubmitCrosspostActivity.setOnClickListener(view -> {
            binding.subredditNameTextViewSubmitCrosspostActivity.performClick();
        });

        binding.subredditNameTextViewSubmitCrosspostActivity.setOnClickListener(view -> {
            Intent intent = new Intent(this, SubscribedThingListingActivity.class);
            intent.putExtra(SubscribedThingListingActivity.EXTRA_SPECIFIED_ACCOUNT, selectedAccount);
            intent.putExtra(SubscribedThingListingActivity.EXTRA_THING_SELECTION_MODE, true);
            intent.putExtra(SubscribedThingListingActivity.EXTRA_THING_SELECTION_TYPE,
                    SubscribedThingListingActivity.EXTRA_THING_SELECTION_TYPE_SUBREDDIT);
            startActivityForResult(intent, SUBREDDIT_SELECTION_REQUEST_CODE);
        });

        binding.rulesButtonSubmitCrosspostActivity.setOnClickListener(view -> {
            if (subredditName == null) {
                Snackbar.make(binding.getRoot(), R.string.select_a_subreddit, Snackbar.LENGTH_SHORT).show();
            } else {
                Intent intent = new Intent(this, RulesActivity.class);
                if (subredditIsUser) {
                    intent.putExtra(RulesActivity.EXTRA_SUBREDDIT_NAME, "u_" + subredditName);
                } else {
                    intent.putExtra(RulesActivity.EXTRA_SUBREDDIT_NAME, subredditName);
                }
                startActivity(intent);
            }
        });

        binding.flairCustomTextViewSubmitCrosspostActivity.setOnClickListener(view -> {
            if (flair == null) {
                flairSelectionBottomSheetFragment = new FlairBottomSheetFragment();
                Bundle bundle = new Bundle();
                if (subredditIsUser) {
                    bundle.putString(FlairBottomSheetFragment.EXTRA_SUBREDDIT_NAME, "u_" + subredditName);
                } else {
                    bundle.putString(FlairBottomSheetFragment.EXTRA_SUBREDDIT_NAME, subredditName);
                }
                flairSelectionBottomSheetFragment.setArguments(bundle);
                flairSelectionBottomSheetFragment.show(getSupportFragmentManager(), flairSelectionBottomSheetFragment.getTag());
            } else {
                flair = null;
                flairController.setHasFlair(false);
                binding.flairCustomTextViewSubmitCrosspostActivity.setBackgroundColor(resources.getColor(android.R.color.transparent));
                applyFlairLabelStyle();
            }
        });

        binding.spoilerCustomTextViewSubmitCrosspostActivity.setOnClickListener(view -> {
            if (!isSpoiler) {
                binding.spoilerCustomTextViewSubmitCrosspostActivity.setBackgroundColor(spoilerBackgroundColor);
                binding.spoilerCustomTextViewSubmitCrosspostActivity.setBorderColor(spoilerBackgroundColor);
                binding.spoilerCustomTextViewSubmitCrosspostActivity.setTextColor(spoilerTextColor);
                isSpoiler = true;
            } else {
                binding.spoilerCustomTextViewSubmitCrosspostActivity.setBackgroundColor(resources.getColor(android.R.color.transparent));
                binding.spoilerCustomTextViewSubmitCrosspostActivity.setTextColor(primaryTextColor);
                isSpoiler = false;
            }
        });

        binding.nsfwCustomTextViewSubmitCrosspostActivity.setOnClickListener(view -> {
            if (!isNSFW) {
                binding.nsfwCustomTextViewSubmitCrosspostActivity.setBackgroundColor(nsfwBackgroundColor);
                binding.nsfwCustomTextViewSubmitCrosspostActivity.setBorderColor(nsfwBackgroundColor);
                binding.nsfwCustomTextViewSubmitCrosspostActivity.setTextColor(nsfwTextColor);
                isNSFW = true;
            } else {
                binding.nsfwCustomTextViewSubmitCrosspostActivity.setBackgroundColor(resources.getColor(android.R.color.transparent));
                binding.nsfwCustomTextViewSubmitCrosspostActivity.setTextColor(primaryTextColor);
                isNSFW = false;
            }
        });

        binding.receivePostReplyNotificationsLinearLayoutSubmitCrosspostActivity.setOnClickListener(view -> {
            binding.receivePostReplyNotificationsSwitchMaterialSubmitCrosspostActivity.performClick();
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isPosting) {
                    promptAlertDialog(R.string.exit_when_submit, R.string.exit_when_submit_post_detail);
                } else {
                    if (!binding.postTitleEditTextSubmitCrosspostActivity.getText().toString().equals("")) {
                        promptAlertDialog(R.string.discard, R.string.discard_detail);
                    } else {
                        setEnabled(false);
                        triggerBackPress();
                    }
                }
            }
        });
    }

    private void loadCurrentAccount() {
        Handler handler = new Handler();
        mExecutor.execute(() -> {
            Account account = mRedditDataRoomDatabase.accountDao().getCurrentAccount();
            handler.post(() -> {
                accountLoadFinished = true;
                if (selectedAccount != null) {
                    // The user picked an account while this load was in flight; don't stomp it.
                    return;
                }
                selectedAccount = account;
                if (!isFinishing() && !isDestroyed() && account != null) {
                    mGlide.load(account.getProfileImageUrl())
                            .transform(new RoundedCornersTransformation(72, 0))
                            .error(mGlide.load(R.drawable.subreddit_default_icon)
                                    .transform(new RoundedCornersTransformation(72, 0)))
                            .into(binding.accountIconGifImageViewSubmitCrosspostActivity);

                    binding.accountNameTextViewSubmitCrosspostActivity.setText(account.getAccountName());
                }
            });
        });
    }

    @Nullable
    private Post.Preview getPreview(Post post) {
        ArrayList<Post.Preview> previews = post.getPreviews();
        if (previews != null && !previews.isEmpty()) {
            return previews.get(0);
        }

        return null;
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
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutSubmitCrosspostActivity,
                null, binding.toolbarSubmitCrosspostActivity);
        primaryTextColor = mCustomThemeWrapper.getPrimaryTextColor();
        binding.accountNameTextViewSubmitCrosspostActivity.setTextColor(primaryTextColor);
        int secondaryTextColor = mCustomThemeWrapper.getSecondaryTextColor();
        binding.subredditNameTextViewSubmitCrosspostActivity.setTextColor(secondaryTextColor);
        binding.rulesButtonSubmitCrosspostActivity.setTextColor(mCustomThemeWrapper.getButtonTextColor());
        binding.rulesButtonSubmitCrosspostActivity.setBackgroundColor(mCustomThemeWrapper.getColorPrimaryLightTheme());
        binding.receivePostReplyNotificationsTextViewSubmitCrosspostActivity.setTextColor(primaryTextColor);
        int dividerColor = mCustomThemeWrapper.getDividerColor();
        binding.divider1SubmitCrosspostActivity.setBackgroundColor(dividerColor);
        binding.divider2SubmitCrosspostActivity.setBackgroundColor(dividerColor);
        binding.divider3SubmitCrosspostActivity.setBackgroundColor(dividerColor);
        binding.divider4SubmitCrosspostActivity.setBackgroundColor(dividerColor);
        flairBackgroundColor = mCustomThemeWrapper.getFlairBackgroundColor();
        flairTextColor = mCustomThemeWrapper.getFlairTextColor();
        spoilerBackgroundColor = mCustomThemeWrapper.getSpoilerBackgroundColor();
        spoilerTextColor = mCustomThemeWrapper.getSpoilerTextColor();
        nsfwBackgroundColor = mCustomThemeWrapper.getNsfwBackgroundColor();
        nsfwTextColor = mCustomThemeWrapper.getNsfwTextColor();
        binding.flairCustomTextViewSubmitCrosspostActivity.setTextColor(primaryTextColor);
        binding.spoilerCustomTextViewSubmitCrosspostActivity.setTextColor(primaryTextColor);
        binding.nsfwCustomTextViewSubmitCrosspostActivity.setTextColor(primaryTextColor);
        binding.postTitleEditTextSubmitCrosspostActivity.setTextColor(primaryTextColor);
        binding.postTitleEditTextSubmitCrosspostActivity.setHintTextColor(secondaryTextColor);
        binding.postContentTextViewSubmitCrosspostActivity.setTextColor(primaryTextColor);
        binding.postContentTextViewSubmitCrosspostActivity.setHintTextColor(secondaryTextColor);
        binding.playButtonImageViewSubmitCrosspostActivity.setColorFilter(mCustomThemeWrapper.getMediaIndicatorIconColor(), PorterDuff.Mode.SRC_IN);
        binding.playButtonImageViewSubmitCrosspostActivity.setBackgroundTintList(ColorStateList.valueOf(mCustomThemeWrapper.getMediaIndicatorBackgroundColor()));
        if (typeface != null) {
            binding.subredditNameTextViewSubmitCrosspostActivity.setTypeface(typeface);
            binding.rulesButtonSubmitCrosspostActivity.setTypeface(typeface);
            binding.receivePostReplyNotificationsTextViewSubmitCrosspostActivity.setTypeface(typeface);
            binding.flairCustomTextViewSubmitCrosspostActivity.setTypeface(typeface);
            binding.spoilerCustomTextViewSubmitCrosspostActivity.setTypeface(typeface);
            binding.nsfwCustomTextViewSubmitCrosspostActivity.setTypeface(typeface);
            binding.postTitleEditTextSubmitCrosspostActivity.setTypeface(typeface);
        }
        if (contentTypeface != null) {
            binding.postContentTextViewSubmitCrosspostActivity.setTypeface(contentTypeface);
        }
    }

    private void displaySubredditIcon() {
        if (iconUrl != null && !iconUrl.equals("")) {
            mGlide.load(iconUrl)
                    .transform(new RoundedCornersTransformation(72, 0))
                    .error(mGlide.load(R.drawable.subreddit_default_icon)
                            .transform(new RoundedCornersTransformation(72, 0)))
                    .into(binding.subredditIconGifImageViewSubmitCrosspostActivity);
        } else {
            mGlide.load(R.drawable.subreddit_default_icon)
                    .transform(new RoundedCornersTransformation(72, 0))
                    .into(binding.subredditIconGifImageViewSubmitCrosspostActivity);
        }
    }

    private void loadSubredditIcon() {
        String currentSubredditName = subredditName;
        if (currentSubredditName == null) {
            // Nothing to look up — keep the default icon and stop asking for it.
            displaySubredditIcon();
            loadSubredditIconSuccessful = true;
            return;
        }
        LoadSubredditIcon.loadSubredditIcon(mExecutor, new Handler(), mRedditDataRoomDatabase, currentSubredditName,
                accessToken, accountName, mOauthRetrofit, mRetrofit, iconImageUrl -> {
            iconUrl = iconImageUrl;
            displaySubredditIcon();
            loadSubredditIconSuccessful = true;
        });
    }

    private void applyFlairLabelStyle() {
        if (flair != null) return;
        boolean required = flairController != null && flairController.isFlairRequired();
        binding.flairCustomTextViewSubmitCrosspostActivity.setText(getString(required ? R.string.flair_required : R.string.flair));
        binding.flairCustomTextViewSubmitCrosspostActivity.setTextColor(required ? nsfwBackgroundColor : primaryTextColor);
    }

    private void onSubredditChangedNotifyController(@Nullable String targetSub, boolean targetIsUser) {
        String token = selectedAccount != null && selectedAccount.getAccessToken() != null
                ? selectedAccount.getAccessToken() : accessToken;
        flairController.onSubredditChanged(targetSub, targetIsUser, token);
    }

    private void fetchCrosspostableSubreddits() {
        crosspostableSubreddits = null;
        crosspostableLoadFailed = false;
        String token = selectedAccount != null && selectedAccount.getAccessToken() != null
                ? selectedAccount.getAccessToken() : accessToken;
        if (token == null) {
            crosspostableLoadFailed = true;
            return;
        }
        FetchCrosspostableSubreddits.fetch(mOauthRetrofit, token, post.getSubredditName(),
                new FetchCrosspostableSubreddits.FetchCrosspostableSubredditsListener() {
                    @Override
                    public void onSuccess(Set<String> allowed) {
                        crosspostableSubreddits = allowed;
                        crosspostableLoadFailed = false;
                    }

                    @Override
                    public void onFail() {
                        crosspostableSubreddits = null;
                        crosspostableLoadFailed = true;
                    }
                });
    }

    /**
     * @return true if the target sub is known-eligible; false means caller should bail.
     * When loaded-and-disallowed, shows a blocking dialog explaining why.
     * When still loading or load failed, shows the "couldn't verify" dialog (fail-closed).
     */
    private boolean checkCrosspostAllowed(String targetSubName, boolean targetIsUser) {
        if (crosspostableLoadFailed || crosspostableSubreddits == null) {
            showCouldNotVerifyDialog();
            return false;
        }
        String key = (targetIsUser ? "u_" + targetSubName : targetSubName).toLowerCase();
        if (crosspostableSubreddits.contains(key)) {
            return true;
        }
        explainCrosspostBlock(targetSubName, targetIsUser);
        return false;
    }

    private void explainCrosspostBlock(String targetSubName, boolean targetIsUser) {
        if (targetIsUser) {
            new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                    .setTitle(R.string.crosspost_disallowed_title)
                    .setMessage(R.string.crosspost_user_profile_disallowed_message)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }
        // Reddit's `user_is_subscriber` on /r/{sub}/about.json is the authoritative answer.
        // The local SubscribedSubreddit DB cache can lag behind, so we ask Reddit directly.
        String token = selectedAccount != null && selectedAccount.getAccessToken() != null
                ? selectedAccount.getAccessToken() : accessToken;
        mOauthRetrofit.create(RedditAPI.class)
                .getSubredditDataOauth(targetSubName, APIUtils.getOAuthHeader(token))
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                        Boolean subscribed = null;
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                JSONObject data = new JSONObject(response.body()).getJSONObject(JSONUtils.DATA_KEY);
                                if (!data.isNull("user_is_subscriber")) {
                                    subscribed = data.getBoolean("user_is_subscriber");
                                }
                            } catch (Exception ignored) {
                            }
                        }
                        showBlockDialog(targetSubName, subscribed);
                    }

                    @Override
                    public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                        showBlockDialog(targetSubName, null);
                    }
                });
    }

    private void showBlockDialog(String targetSubName, @Nullable Boolean subscribed) {
        if (isFinishing() || isDestroyed()) return;
        int titleRes;
        String message;
        if (subscribed == null) {
            // Couldn't confirm subscription state — present the disallowed message,
            // since exclusion from crosspostable_subreddits when subscribed implies disallowed.
            titleRes = R.string.crosspost_disallowed_title;
            message = getString(R.string.crosspost_disallowed_message, targetSubName);
        } else if (subscribed) {
            titleRes = R.string.crosspost_disallowed_title;
            message = getString(R.string.crosspost_disallowed_message, targetSubName);
        } else {
            titleRes = R.string.crosspost_not_subscribed_title;
            message = getString(R.string.crosspost_not_subscribed_message, targetSubName);
        }
        new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle(titleRes)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void showCouldNotVerifyDialog() {
        new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle(R.string.crosspost_could_not_verify_title)
                .setMessage(R.string.crosspost_could_not_verify_message)
                .setPositiveButton(R.string.crosspost_retry, (d, w) -> fetchCrosspostableSubreddits())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void promptAlertDialog(int titleResId, int messageResId) {
        new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle(titleResId)
                .setMessage(messageResId)
                .setPositiveButton(R.string.yes, (dialogInterface, i)
                        -> finish())
                .setNegativeButton(R.string.no, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.submit_crosspost_activity, menu);
        applyMenuItemTheme(menu);
        flairController.setMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            triggerBackPress();
            return true;
        } else if (itemId == R.id.action_send_submit_crosspost_activity) {
            if (!subredditSelected) {
                Snackbar.make(binding.getRoot(), R.string.select_a_subreddit, Snackbar.LENGTH_SHORT).show();
                return true;
            }

            if (binding.postTitleEditTextSubmitCrosspostActivity.getText() == null || binding.postTitleEditTextSubmitCrosspostActivity.getText().toString().equals("")) {
                Snackbar.make(binding.getRoot(), R.string.title_required, Snackbar.LENGTH_SHORT).show();
                return true;
            }

            Account account = selectedAccount;
            if (account == null) {
                // A finished read with no account means there is nothing left to wait for.
                // Checked before the eligibility test, which can fire a network request.
                Snackbar.make(binding.getRoot(),
                        accountLoadFinished ? R.string.login_first : R.string.account_not_loaded_yet,
                        Snackbar.LENGTH_SHORT).show();
                return true;
            }

            String targetSubName = binding.subredditNameTextViewSubmitCrosspostActivity.getText().toString();
            if (!checkCrosspostAllowed(targetSubName, subredditIsUser)) {
                return true;
            }

            isPosting = true;
            flairController.setPosting(true);

            mPostingSnackbar.show();

            String subredditName;
            if (subredditIsUser) {
                subredditName = "u_" + binding.subredditNameTextViewSubmitCrosspostActivity.getText().toString();
            } else {
                subredditName = binding.subredditNameTextViewSubmitCrosspostActivity.getText().toString();
            }

            /*Intent intent = new Intent(this, SubmitPostService.class);
            intent.putExtra(SubmitPostService.EXTRA_ACCOUNT, selectedAccount);
            intent.putExtra(SubmitPostService.EXTRA_SUBREDDIT_NAME, subredditName);
            intent.putExtra(SubmitPostService.EXTRA_TITLE, binding.postTitleEditTextSubmitCrosspostActivity.getText().toString());
            if (post.isCrosspost()) {
                intent.putExtra(SubmitPostService.EXTRA_CONTENT, "t3_" + post.getCrosspostParentId());
            } else {
                intent.putExtra(SubmitPostService.EXTRA_CONTENT, post.getFullName());
            }
            intent.putExtra(SubmitPostService.EXTRA_KIND, APIUtils.KIND_CROSSPOST);
            intent.putExtra(SubmitPostService.EXTRA_FLAIR, flair);
            intent.putExtra(SubmitPostService.EXTRA_IS_SPOILER, isSpoiler);
            intent.putExtra(SubmitPostService.EXTRA_IS_NSFW, isNSFW);
            intent.putExtra(SubmitPostService.EXTRA_RECEIVE_POST_REPLY_NOTIFICATIONS,
                    binding.receivePostReplyNotificationsSwitchMaterialSubmitCrosspostActivity.isChecked());
            intent.putExtra(SubmitPostService.EXTRA_POST_TYPE, SubmitPostService.EXTRA_POST_TYPE_CROSSPOST);
            ContextCompat.startForegroundService(this, intent);*/


            PersistableBundle extras = new PersistableBundle();
            extras.putString(SubmitPostService.EXTRA_ACCOUNT, account.getJSONModel());
            extras.putString(SubmitPostService.EXTRA_SUBREDDIT_NAME, subredditName);
            String title = binding.postTitleEditTextSubmitCrosspostActivity.getText().toString();
            extras.putString(SubmitPostService.EXTRA_TITLE, title);
            if (post.isCrosspost()) {
                extras.putString(SubmitPostService.EXTRA_CONTENT, "t3_" + post.getCrosspostParentId());
            } else {
                extras.putString(SubmitPostService.EXTRA_CONTENT, post.getFullName());
            }
            extras.putString(SubmitPostService.EXTRA_KIND, APIUtils.KIND_CROSSPOST);
            if (flair != null) {
                extras.putString(SubmitPostService.EXTRA_FLAIR, flair.getJSONModel());
            }
            extras.putInt(SubmitPostService.EXTRA_IS_SPOILER, isSpoiler ? 1 : 0);
            extras.putInt(SubmitPostService.EXTRA_IS_NSFW, isNSFW ? 1 : 0);
            extras.putInt(SubmitPostService.EXTRA_RECEIVE_POST_REPLY_NOTIFICATIONS,
                    binding.receivePostReplyNotificationsSwitchMaterialSubmitCrosspostActivity.isChecked() ? 1 : 0);
            extras.putInt(SubmitPostService.EXTRA_POST_TYPE, SubmitPostService.EXTRA_POST_TYPE_CROSSPOST);

            // TODO: contentEstimatedBytes
            JobInfo jobInfo = SubmitPostService.constructJobInfo(this, title.length() * 2L + 20000, extras);
            ((JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE)).schedule(jobInfo);

            return true;
        }

        return false;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(SELECTED_ACCOUNT_STATE, selectedAccount);
        outState.putString(SUBREDDIT_NAME_STATE, subredditName);
        outState.putString(SUBREDDIT_ICON_STATE, iconUrl);
        outState.putBoolean(SUBREDDIT_SELECTED_STATE, subredditSelected);
        outState.putBoolean(SUBREDDIT_IS_USER_STATE, subredditIsUser);
        outState.putBoolean(LOAD_SUBREDDIT_ICON_STATE, loadSubredditIconSuccessful);
        outState.putBoolean(IS_POSTING_STATE, isPosting);
        outState.putParcelable(FLAIR_STATE, flair);
        outState.putBoolean(IS_SPOILER_STATE, isSpoiler);
        outState.putBoolean(IS_NSFW_STATE, isNSFW);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SUBREDDIT_SELECTION_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                String pickedName = data.getStringExtra(SelectThingReturnKey.RETURN_EXTRA_SUBREDDIT_OR_USER_NAME);
                boolean pickedIsUser = data.getIntExtra(SelectThingReturnKey.RETURN_EXTRA_THING_TYPE, SelectThingReturnKey.THING_TYPE.SUBREDDIT) == SelectThingReturnKey.THING_TYPE.USER;

                if (crosspostableSubreddits != null && pickedName != null) {
                    String key = (pickedIsUser ? "u_" + pickedName : pickedName).toLowerCase();
                    if (!crosspostableSubreddits.contains(key)) {
                        explainCrosspostBlock(pickedName, pickedIsUser);
                        return;
                    }
                }

                subredditName = pickedName;
                iconUrl = data.getStringExtra(SelectThingReturnKey.RETURN_EXTRA_SUBREDDIT_OR_USER_ICON);
                subredditSelected = true;
                subredditIsUser = pickedIsUser;

                binding.subredditNameTextViewSubmitCrosspostActivity.setTextColor(primaryTextColor);
                binding.subredditNameTextViewSubmitCrosspostActivity.setText(subredditName);
                displaySubredditIcon();

                flair = null;
                binding.flairCustomTextViewSubmitCrosspostActivity.setVisibility(View.VISIBLE);
                binding.flairCustomTextViewSubmitCrosspostActivity.setBackgroundColor(resources.getColor(android.R.color.transparent));
                binding.flairCustomTextViewSubmitCrosspostActivity.setTextColor(primaryTextColor);
                binding.flairCustomTextViewSubmitCrosspostActivity.setText(getString(R.string.flair));

                onSubredditChangedNotifyController(subredditName, subredditIsUser);
            }
        }
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    @Override
    public void flairSelected(Flair flair) {
        this.flair = flair;
        binding.flairCustomTextViewSubmitCrosspostActivity.setText(flair.getText());
        binding.flairCustomTextViewSubmitCrosspostActivity.setBackgroundColor(flairBackgroundColor);
        binding.flairCustomTextViewSubmitCrosspostActivity.setBorderColor(flairBackgroundColor);
        binding.flairCustomTextViewSubmitCrosspostActivity.setTextColor(flairTextColor);
        flairController.setHasFlair(true);
    }

    @Override
    public void onAccountSelected(Account account) {
        selectedAccount = account;

        mGlide.load(account.getProfileImageUrl())
                .transform(new RoundedCornersTransformation(72, 0))
                .error(mGlide.load(R.drawable.subreddit_default_icon)
                        .transform(new RoundedCornersTransformation(72, 0)))
                .into(binding.accountIconGifImageViewSubmitCrosspostActivity);

        binding.accountNameTextViewSubmitCrosspostActivity.setText(account.getAccountName());

        fetchCrosspostableSubreddits();
    }

    @Subscribe
    public void onAccountSwitchEvent(SwitchAccountEvent event) {
        finish();
    }

    @Subscribe
    public void onSubmitCrosspostEvent(SubmitCrosspostEvent submitCrosspostEvent) {
        isPosting = false;
        flairController.setPosting(false);
        mPostingSnackbar.dismiss();
        if (submitCrosspostEvent.postSuccess) {
            Intent intent = new Intent(this, ViewPostDetailActivity.class);
            intent.putExtra(ViewPostDetailActivity.EXTRA_POST_DATA, submitCrosspostEvent.post);
            startActivity(intent);
            finish();
        } else {
            if (submitCrosspostEvent.errorMessage == null || submitCrosspostEvent.errorMessage.equals("")) {
                Snackbar.make(binding.getRoot(), R.string.post_failed, Snackbar.LENGTH_SHORT).show();
            } else {
                Snackbar.make(binding.getRoot(), submitCrosspostEvent.errorMessage.substring(0, 1).toUpperCase()
                        + submitCrosspostEvent.errorMessage.substring(1), Snackbar.LENGTH_SHORT).show();
            }
        }
    }
}