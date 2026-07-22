package ml.docilealligator.infinityforreddit.activities;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PersistableBundle;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Named;
import jp.wasabeef.glide.transformations.RoundedCornersTransformation;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.adapters.MarkdownBottomBarRecyclerViewAdapter;
import ml.docilealligator.infinityforreddit.adapters.RedditGallerySubmissionRecyclerViewAdapter;
import ml.docilealligator.infinityforreddit.asynctasks.FlairRequirementController;
import ml.docilealligator.infinityforreddit.asynctasks.LoadSubredditIcon;
import ml.docilealligator.infinityforreddit.bottomsheetfragments.AccountChooserBottomSheetFragment;
import ml.docilealligator.infinityforreddit.bottomsheetfragments.FlairBottomSheetFragment;
import ml.docilealligator.infinityforreddit.bottomsheetfragments.SelectOrCaptureImageBottomSheetFragment;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.customviews.LinearLayoutManagerBugFixed;
import ml.docilealligator.infinityforreddit.databinding.ActivityPostGalleryBinding;
import ml.docilealligator.infinityforreddit.events.SubmitGalleryPostEvent;
import ml.docilealligator.infinityforreddit.events.SwitchAccountEvent;
import ml.docilealligator.infinityforreddit.post.RedditGalleryPayload;
import ml.docilealligator.infinityforreddit.services.SubmitPostService;
import ml.docilealligator.infinityforreddit.subreddit.Flair;
import ml.docilealligator.infinityforreddit.thing.SelectThingReturnKey;
import ml.docilealligator.infinityforreddit.utils.CameraCapturePermissionHelper;
import ml.docilealligator.infinityforreddit.utils.JSONUtils;
import ml.docilealligator.infinityforreddit.utils.UploadImageUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;
import retrofit2.Retrofit;

public class PostGalleryActivity extends BaseActivity implements FlairBottomSheetFragment.FlairSelectionCallback,
        AccountChooserBottomSheetFragment.AccountChooserListener {

    static final String EXTRA_SUBREDDIT_NAME = "ESN";

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
    private static final String REDDIT_GALLERY_IMAGE_INFO_STATE = "RGIIS";
    private static final String IMAGE_URI_STATE = "IUS";

    private static final int SUBREDDIT_SELECTION_REQUEST_CODE = 0;
    private static final int PICK_IMAGE_REQUEST_CODE = 1;
    private static final int CAPTURE_IMAGE_REQUEST_CODE = 2;

    @Inject
    @Named("no_oauth")
    Retrofit mRetrofit;
    @Inject
    @Named("oauth")
    Retrofit mOauthRetrofit;
    @Inject
    @Named("upload_media")
    Retrofit mUploadMediaRetrofit;
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
    @Nullable
    private ArrayList<RedditGallerySubmissionRecyclerViewAdapter.RedditGalleryImageInfo> redditGalleryImageInfoList;
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
    private RedditGallerySubmissionRecyclerViewAdapter adapter;
    @Nullable
    private Uri imageUri;
    private boolean isUploading;
    private ActivityPostGalleryBinding binding;
    private FlairRequirementController flairController;

    private CameraCapturePermissionHelper cameraCapturePermissionHelper;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        setImmersiveModeNotApplicableBelowAndroid16();

        super.onCreate(savedInstanceState);
        binding = ActivityPostGalleryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        cameraCapturePermissionHelper = new CameraCapturePermissionHelper(this,
                this::launchCaptureImageIntent,
                () -> Snackbar.make(binding.coordinatorLayoutPostGalleryActivity, R.string.camera_permission_required_capture, Snackbar.LENGTH_SHORT).show());

        EventBus.getDefault().register(this);

        applyCustomTheme();

        flairController = new FlairRequirementController(mOauthRetrofit,
                R.id.action_send_post_gallery_activity,
                this::applyFlairLabelStyle);

        if (isImmersiveInterfaceRespectForcedEdgeToEdge()) {
            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(binding.appbarLayoutPostGalleryActivity);
            }

            ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                    Insets allInsets = Utils.getInsets(insets, true, isForcedImmersiveInterface());

                    setMargins(binding.toolbarPostGalleryActivity,
                            allInsets.left,
                            allInsets.top,
                            allInsets.right,
                            BaseActivity.IGNORE_MARGIN);

                    binding.linearLayoutPostGalleryActivity.setPadding(
                            allInsets.left,
                            0,
                            allInsets.right,
                            allInsets.bottom
                    );

                    return WindowInsetsCompat.CONSUMED;
                }
            });
        }

        setSupportActionBar(binding.toolbarPostGalleryActivity);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        mGlide = Glide.with(this);

        mPostingSnackbar = Snackbar.make(binding.coordinatorLayoutPostGalleryActivity, R.string.posting, Snackbar.LENGTH_INDEFINITE);

        resources = getResources();

        adapter = new RedditGallerySubmissionRecyclerViewAdapter(this, mCustomThemeWrapper, () -> {
            if (!isUploading) {
                SelectOrCaptureImageBottomSheetFragment fragment = new SelectOrCaptureImageBottomSheetFragment();
                fragment.show(getSupportFragmentManager(), fragment.getTag());
            } else {
                Snackbar.make(binding.coordinatorLayoutPostGalleryActivity, R.string.please_wait_image_is_uploading, Snackbar.LENGTH_SHORT).show();
            }
        });
        binding.imagesRecyclerViewPostGalleryActivity.setAdapter(adapter);
        Resources resources = getResources();
        int nColumns = resources.getBoolean(R.bool.isTablet) || resources.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 3 : 2;
        ((GridLayoutManager) Objects.requireNonNull(binding.imagesRecyclerViewPostGalleryActivity.getLayoutManager())).setSpanCount(nColumns);
        binding.imagesRecyclerViewPostGalleryActivity.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int offset = (int) (Utils.convertDpToPixel(16, PostGalleryActivity.this));
                int halfOffset = offset / 2;
                outRect.set(halfOffset, 0, halfOffset, offset);
            }
        });

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
            redditGalleryImageInfoList = savedInstanceState.getParcelableArrayList(REDDIT_GALLERY_IMAGE_INFO_STATE);
            String savedImageUri = savedInstanceState.getString(IMAGE_URI_STATE);
            imageUri = savedImageUri == null ? null : Uri.parse(savedImageUri);

            if (selectedAccount != null) {
                mGlide.load(selectedAccount.getProfileImageUrl())
                        .transform(new RoundedCornersTransformation(72, 0))
                        .error(mGlide.load(R.drawable.subreddit_default_icon)
                                .transform(new RoundedCornersTransformation(72, 0)))
                        .into(binding.accountIconGifImageViewPostGalleryActivity);

                binding.accountNameTextViewPostGalleryActivity.setText(selectedAccount.getAccountName());
            } else {
                loadCurrentAccount();
            }

            if (redditGalleryImageInfoList != null) {
                adapter.setRedditGalleryImageInfoList(redditGalleryImageInfoList);
                // A missing payload means the upload had not finished when the state was saved.
                // uploadImage() reports back through the adapter it captured, which belonged to
                // the destroyed instance, so that result can never reach this one — restarting
                // the upload is the only way a recreated activity gets a payload. Do not "fix"
                // this into a skip-if-already-uploading check; it would leave the image stuck.
                for (RedditGallerySubmissionRecyclerViewAdapter.RedditGalleryImageInfo imageInfo : redditGalleryImageInfoList) {
                    if (imageInfo.payload == null) {
                        uploadImage(Uri.parse(imageInfo.imageUrlString), imageInfo.id);
                    }
                }
            }

            if (subredditName != null) {
                binding.subredditNameTextViewPostGalleryActivity.setTextColor(primaryTextColor);
                binding.subredditNameTextViewPostGalleryActivity.setText(subredditName);
                binding.flairCustomTextViewPostGalleryActivity.setVisibility(View.VISIBLE);
                if (!loadSubredditIconSuccessful) {
                    loadSubredditIcon();
                }
                notifyControllerOfSubreddit();
            }
            displaySubredditIcon();

            if (isPosting) {
                mPostingSnackbar.show();
            }

            if (flair != null) {
                binding.flairCustomTextViewPostGalleryActivity.setText(flair.getText());
                binding.flairCustomTextViewPostGalleryActivity.setBackgroundColor(flairBackgroundColor);
                binding.flairCustomTextViewPostGalleryActivity.setBorderColor(flairBackgroundColor);
                binding.flairCustomTextViewPostGalleryActivity.setTextColor(flairTextColor);
            }
            if (isSpoiler) {
                binding.spoilerCustomTextViewPostGalleryActivity.setBackgroundColor(spoilerBackgroundColor);
                binding.spoilerCustomTextViewPostGalleryActivity.setBorderColor(spoilerBackgroundColor);
                binding.spoilerCustomTextViewPostGalleryActivity.setTextColor(spoilerTextColor);
            }
            if (isNSFW) {
                binding.nsfwCustomTextViewPostGalleryActivity.setBackgroundColor(nsfwBackgroundColor);
                binding.nsfwCustomTextViewPostGalleryActivity.setBorderColor(nsfwBackgroundColor);
                binding.nsfwCustomTextViewPostGalleryActivity.setTextColor(nsfwTextColor);
            }
        } else {
            isPosting = false;

            loadCurrentAccount();

            if (getIntent().hasExtra(EXTRA_SUBREDDIT_NAME)) {
                loadSubredditIconSuccessful = false;
                subredditName = getIntent().getStringExtra(EXTRA_SUBREDDIT_NAME);
                subredditSelected = true;
                binding.subredditNameTextViewPostGalleryActivity.setTextColor(primaryTextColor);
                binding.subredditNameTextViewPostGalleryActivity.setText(subredditName);
                binding.flairCustomTextViewPostGalleryActivity.setVisibility(View.VISIBLE);
                loadSubredditIcon();
                notifyControllerOfSubreddit();
            } else {
                mGlide.load(R.drawable.subreddit_default_icon)
                        .transform(new RoundedCornersTransformation(72, 0))
                        .into(binding.subredditIconGifImageViewPostGalleryActivity);
            }
        }

        binding.accountLinearLayoutPostGalleryActivity.setOnClickListener(view -> {
            AccountChooserBottomSheetFragment fragment = new AccountChooserBottomSheetFragment();
            fragment.show(getSupportFragmentManager(), fragment.getTag());
        });

        binding.subredditRelativeLayoutPostGalleryActivity.setOnClickListener(view -> {
            Intent intent = new Intent(this, SubscribedThingListingActivity.class);
            intent.putExtra(SubscribedThingListingActivity.EXTRA_SPECIFIED_ACCOUNT, selectedAccount);
            intent.putExtra(SubscribedThingListingActivity.EXTRA_THING_SELECTION_MODE, true);
            intent.putExtra(SubscribedThingListingActivity.EXTRA_THING_SELECTION_TYPE,
                    SubscribedThingListingActivity.EXTRA_THING_SELECTION_TYPE_SUBREDDIT);
            startActivityForResult(intent, SUBREDDIT_SELECTION_REQUEST_CODE);
        });

        binding.rulesButtonPostGalleryActivity.setOnClickListener(view -> {
            if (subredditName == null) {
                Snackbar.make(binding.coordinatorLayoutPostGalleryActivity, R.string.select_a_subreddit, Snackbar.LENGTH_SHORT).show();
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

        binding.flairCustomTextViewPostGalleryActivity.setOnClickListener(view -> {
            if (flair == null) {
                flairSelectionBottomSheetFragment = new FlairBottomSheetFragment();
                Bundle bundle = new Bundle();
                bundle.putString(FlairBottomSheetFragment.EXTRA_SUBREDDIT_NAME, subredditName);
                flairSelectionBottomSheetFragment.setArguments(bundle);
                flairSelectionBottomSheetFragment.show(getSupportFragmentManager(), flairSelectionBottomSheetFragment.getTag());
            } else {
                flair = null;
                flairController.setHasFlair(false);
                binding.flairCustomTextViewPostGalleryActivity.setBackgroundColor(resources.getColor(android.R.color.transparent));
                applyFlairLabelStyle();
            }
        });

        binding.spoilerCustomTextViewPostGalleryActivity.setOnClickListener(view -> {
            if (!isSpoiler) {
                binding.spoilerCustomTextViewPostGalleryActivity.setBackgroundColor(spoilerBackgroundColor);
                binding.spoilerCustomTextViewPostGalleryActivity.setBorderColor(spoilerBackgroundColor);
                binding.spoilerCustomTextViewPostGalleryActivity.setTextColor(spoilerTextColor);
                isSpoiler = true;
            } else {
                binding.spoilerCustomTextViewPostGalleryActivity.setBackgroundColor(resources.getColor(android.R.color.transparent));
                binding.spoilerCustomTextViewPostGalleryActivity.setTextColor(primaryTextColor);
                isSpoiler = false;
            }
        });

        binding.nsfwCustomTextViewPostGalleryActivity.setOnClickListener(view -> {
            if (!isNSFW) {
                binding.nsfwCustomTextViewPostGalleryActivity.setBackgroundColor(nsfwBackgroundColor);
                binding.nsfwCustomTextViewPostGalleryActivity.setBorderColor(nsfwBackgroundColor);
                binding.nsfwCustomTextViewPostGalleryActivity.setTextColor(nsfwTextColor);
                isNSFW = true;
            } else {
                binding.nsfwCustomTextViewPostGalleryActivity.setBackgroundColor(resources.getColor(android.R.color.transparent));
                binding.nsfwCustomTextViewPostGalleryActivity.setTextColor(primaryTextColor);
                isNSFW = false;
            }
        });

        binding.receivePostReplyNotificationsLinearLayoutPostGalleryActivity.setOnClickListener(view -> {
            binding.receivePostReplyNotificationsSwitchMaterialPostGalleryActivity.performClick();
        });

        MarkdownBottomBarRecyclerViewAdapter adapter = new MarkdownBottomBarRecyclerViewAdapter(
                mCustomThemeWrapper,
                new MarkdownBottomBarRecyclerViewAdapter.ItemClickListener() {
                    @Override
                    public void onClick(int item) {
                        MarkdownBottomBarRecyclerViewAdapter.bindEditTextWithItemClickListener(
                                PostGalleryActivity.this, binding.postContentEditTextPostGalleryActivity, item);
                    }

                    @Override
                    public void onUploadImage() {

                    }
                });

        binding.markdownBottomBarRecyclerViewPostGalleryActivity.setLayoutManager(new LinearLayoutManagerBugFixed(this,
                LinearLayoutManager.HORIZONTAL, true).setStackFromEndAndReturnCurrentObject());
        binding.markdownBottomBarRecyclerViewPostGalleryActivity.setAdapter(adapter);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isPosting) {
                    promptAlertDialog(R.string.exit_when_submit, R.string.exit_when_submit_post_detail);
                } else {
                    redditGalleryImageInfoList = PostGalleryActivity.this.adapter.getRedditGalleryImageInfoList();
                    if (!binding.postTitleEditTextPostGalleryActivity.getText().toString().isEmpty()
                            || !binding.postContentEditTextPostGalleryActivity.getText().toString().isEmpty()
                            || !redditGalleryImageInfoList.isEmpty()) {
                        promptAlertDialog(R.string.discard, R.string.discard_detail);
                    } else {
                        finish();
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
                            .into(binding.accountIconGifImageViewPostGalleryActivity);

                    binding.accountNameTextViewPostGalleryActivity.setText(account.getAccountName());
                }
            });
        });
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
        binding.coordinatorLayoutPostGalleryActivity.setBackgroundColor(mCustomThemeWrapper.getBackgroundColor());
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutPostGalleryActivity, null, binding.toolbarPostGalleryActivity);
        primaryTextColor = mCustomThemeWrapper.getPrimaryTextColor();
        binding.accountNameTextViewPostGalleryActivity.setTextColor(primaryTextColor);
        int secondaryTextColor = mCustomThemeWrapper.getSecondaryTextColor();
        binding.subredditNameTextViewPostGalleryActivity.setTextColor(secondaryTextColor);
        binding.rulesButtonPostGalleryActivity.setTextColor(mCustomThemeWrapper.getButtonTextColor());
        binding.rulesButtonPostGalleryActivity.setBackgroundColor(mCustomThemeWrapper.getColorPrimaryLightTheme());
        binding.receivePostReplyNotificationsTextViewPostGalleryActivity.setTextColor(primaryTextColor);
        int dividerColor = mCustomThemeWrapper.getDividerColor();
        binding.divider1PostGalleryActivity.setDividerColor(dividerColor);
        binding.divider2PostGalleryActivity.setDividerColor(dividerColor);
        flairBackgroundColor = mCustomThemeWrapper.getFlairBackgroundColor();
        flairTextColor = mCustomThemeWrapper.getFlairTextColor();
        spoilerBackgroundColor = mCustomThemeWrapper.getSpoilerBackgroundColor();
        spoilerTextColor = mCustomThemeWrapper.getSpoilerTextColor();
        nsfwBackgroundColor = mCustomThemeWrapper.getNsfwBackgroundColor();
        nsfwTextColor = mCustomThemeWrapper.getNsfwTextColor();
        binding.flairCustomTextViewPostGalleryActivity.setTextColor(primaryTextColor);
        binding.spoilerCustomTextViewPostGalleryActivity.setTextColor(primaryTextColor);
        binding.nsfwCustomTextViewPostGalleryActivity.setTextColor(primaryTextColor);
        binding.postTitleEditTextPostGalleryActivity.setTextColor(primaryTextColor);
        binding.postTitleEditTextPostGalleryActivity.setHintTextColor(secondaryTextColor);
        binding.postContentEditTextPostGalleryActivity.setTextColor(primaryTextColor);
        binding.postContentEditTextPostGalleryActivity.setHintTextColor(secondaryTextColor);
        if (typeface != null) {
            binding.subredditNameTextViewPostGalleryActivity.setTypeface(typeface);
            binding.rulesButtonPostGalleryActivity.setTypeface(typeface);
            binding.receivePostReplyNotificationsTextViewPostGalleryActivity.setTypeface(typeface);
            binding.flairCustomTextViewPostGalleryActivity.setTypeface(typeface);
            binding.spoilerCustomTextViewPostGalleryActivity.setTypeface(typeface);
            binding.nsfwCustomTextViewPostGalleryActivity.setTypeface(typeface);
            binding.postTitleEditTextPostGalleryActivity.setTypeface(typeface);
        }
        if (contentTypeface != null) {
            binding.postContentEditTextPostGalleryActivity.setTypeface(contentTypeface);
        }
    }

    public void selectImage() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, resources.getString(R.string.select_from_gallery)), PICK_IMAGE_REQUEST_CODE);
    }

    public void captureImage() {
        cameraCapturePermissionHelper.launch();
    }

    private void launchCaptureImageIntent() {
        Intent pictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            imageUri = FileProvider.getUriForFile(this, getPackageName() + ".provider",
                    File.createTempFile("temp_img", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES)));
            pictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
            pictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (IOException ex) {
            imageUri = null;
            Snackbar.make(binding.coordinatorLayoutPostGalleryActivity, R.string.error_creating_temp_file, Snackbar.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivityForResult(pictureIntent, CAPTURE_IMAGE_REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            Utils.deleteContentUriFileQuietly(this, imageUri);
            imageUri = null;
            Snackbar.make(binding.coordinatorLayoutPostGalleryActivity, R.string.no_camera_available, Snackbar.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Utils.deleteContentUriFileQuietly(this, imageUri);
            imageUri = null;
            Snackbar.make(binding.coordinatorLayoutPostGalleryActivity, R.string.camera_permission_required_capture, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void uploadImage(Uri uploadUri, String imageId) {
        Handler handler = new Handler();
        isUploading = true;
        mExecutor.execute(() -> {
            try {
                String response = UploadImageUtils.uploadImage(mOauthRetrofit, mUploadMediaRetrofit, getContentResolver(),
                        accessToken, uploadUri, true, false);
                String mediaId = new JSONObject(response).getJSONObject(JSONUtils.ASSET_KEY).getString(JSONUtils.ASSET_ID_KEY);
                handler.post(() -> {
                    adapter.setImageAsUploaded(imageId, mediaId);
                    isUploading = false;
                });
            } catch (XmlPullParserException | JSONException | IOException e) {
                e.printStackTrace();
                handler.post(() -> {
                    adapter.removeFailedToUploadImage(imageId);
                    Snackbar.make(binding.coordinatorLayoutPostGalleryActivity, R.string.upload_image_failed, Snackbar.LENGTH_LONG).show();
                    isUploading = false;
                });
            }
        });
    }

    private void displaySubredditIcon() {
        if (iconUrl != null && !iconUrl.isEmpty()) {
            mGlide.load(iconUrl)
                    .transform(new RoundedCornersTransformation(72, 0))
                    .error(mGlide.load(R.drawable.subreddit_default_icon)
                            .transform(new RoundedCornersTransformation(72, 0)))
                    .into(binding.subredditIconGifImageViewPostGalleryActivity);
        } else {
            mGlide.load(R.drawable.subreddit_default_icon)
                    .transform(new RoundedCornersTransformation(72, 0))
                    .into(binding.subredditIconGifImageViewPostGalleryActivity);
        }
    }

    private void applyFlairLabelStyle() {
        if (flair != null) return;
        boolean required = flairController != null && flairController.isFlairRequired();
        binding.flairCustomTextViewPostGalleryActivity.setText(getString(required ? R.string.flair_required : R.string.flair));
        binding.flairCustomTextViewPostGalleryActivity.setTextColor(required ? nsfwBackgroundColor : primaryTextColor);
    }

    private void notifyControllerOfSubreddit() {
        String token = selectedAccount != null && selectedAccount.getAccessToken() != null
                ? selectedAccount.getAccessToken() : accessToken;
        flairController.onSubredditChanged(subredditName, subredditIsUser, token);
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

    private void promptAlertDialog(int titleResId, int messageResId) {
        new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle(titleResId)
                .setMessage(messageResId)
                .setPositiveButton(R.string.discard_dialog_button, (dialogInterface, i)
                        -> finish())
                .setNegativeButton(R.string.no, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.post_gallery_activity, menu);
        applyMenuItemTheme(menu);
        flairController.setPosting(isPosting);
        flairController.setMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            triggerBackPress();
            return true;
        } else if (itemId == R.id.action_send_post_gallery_activity) {
            if (!subredditSelected) {
                Snackbar.make(binding.coordinatorLayoutPostGalleryActivity, R.string.select_a_subreddit, Snackbar.LENGTH_SHORT).show();
                return true;
            }

            if (binding.postTitleEditTextPostGalleryActivity.getText() == null || binding.postTitleEditTextPostGalleryActivity.getText().toString().isEmpty()) {
                Snackbar.make(binding.coordinatorLayoutPostGalleryActivity, R.string.title_required, Snackbar.LENGTH_SHORT).show();
                return true;
            }

            redditGalleryImageInfoList = adapter.getRedditGalleryImageInfoList();
            if (redditGalleryImageInfoList.isEmpty()) {
                Snackbar.make(binding.coordinatorLayoutPostGalleryActivity, R.string.select_an_image, Snackbar.LENGTH_SHORT).show();
                return true;
            }

            // Every entry, not just the last: an image whose upload is still pending can sit
            // anywhere in the list once the user has removed others, and a null payload here
            // would serialize as a null gallery item.
            for (RedditGallerySubmissionRecyclerViewAdapter.RedditGalleryImageInfo imageInfo : redditGalleryImageInfoList) {
                if (imageInfo.payload == null) {
                    Snackbar.make(binding.coordinatorLayoutPostGalleryActivity, R.string.please_wait_image_is_uploading, Snackbar.LENGTH_LONG).show();
                    return true;
                }
            }

            Account account = selectedAccount;
            if (account == null) {
                // A finished read with no account means there is nothing left to wait for.
                Snackbar.make(binding.coordinatorLayoutPostGalleryActivity,
                        accountLoadFinished ? R.string.login_first : R.string.account_not_loaded_yet,
                        Snackbar.LENGTH_SHORT).show();
                return true;
            }

            isPosting = true;
            flairController.setPosting(true);

            mPostingSnackbar.show();

            String subredditName;
            if (subredditIsUser) {
                subredditName = "u_" + binding.subredditNameTextViewPostGalleryActivity.getText().toString();
            } else {
                subredditName = binding.subredditNameTextViewPostGalleryActivity.getText().toString();
            }

            /*Intent intent = new Intent(this, SubmitPostService.class);
            intent.putExtra(SubmitPostService.EXTRA_ACCOUNT, selectedAccount);
            intent.putExtra(SubmitPostService.EXTRA_SUBREDDIT_NAME, subredditName);
            intent.putExtra(SubmitPostService.EXTRA_POST_TYPE, SubmitPostService.EXTRA_POST_TYPE_GALLERY);
            ArrayList<RedditGalleryPayload.Item> items = new ArrayList<>();
            for (RedditGallerySubmissionRecyclerViewAdapter.RedditGalleryImageInfo i : redditGalleryImageInfoList) {
                items.add(i.payload);
            }
            RedditGalleryPayload payload = new RedditGalleryPayload(subredditName, subredditIsUser ? "profile" : "subreddit",
                    binding.postTitleEditTextPostGalleryActivity.getText().toString(),
                    binding.postContentEditTextPostGalleryActivity.getText().toString(), isSpoiler, isNSFW,
                    binding.receivePostReplyNotificationsSwitchMaterialPostGalleryActivity.isChecked(), flair, items);
            intent.putExtra(SubmitPostService.EXTRA_REDDIT_GALLERY_PAYLOAD, new Gson().toJson(payload));

            ContextCompat.startForegroundService(this, intent);*/

            PersistableBundle extras = new PersistableBundle();
            extras.putString(SubmitPostService.EXTRA_ACCOUNT, account.getJSONModel());
            extras.putString(SubmitPostService.EXTRA_SUBREDDIT_NAME, subredditName);
            extras.putInt(SubmitPostService.EXTRA_POST_TYPE, SubmitPostService.EXTRA_POST_TYPE_GALLERY);
            ArrayList<RedditGalleryPayload.Item> items = new ArrayList<>();
            for (RedditGallerySubmissionRecyclerViewAdapter.RedditGalleryImageInfo i : redditGalleryImageInfoList) {
                items.add(i.payload);
            }
            RedditGalleryPayload payload = new RedditGalleryPayload(subredditName, subredditIsUser ? "profile" : "subreddit",
                    binding.postTitleEditTextPostGalleryActivity.getText().toString(),
                    binding.postContentEditTextPostGalleryActivity.getText().toString(), isSpoiler, isNSFW,
                    binding.receivePostReplyNotificationsSwitchMaterialPostGalleryActivity.isChecked(), flair, items);

            String payloadJSON = new Gson().toJson(payload);
            extras.putString(SubmitPostService.EXTRA_REDDIT_GALLERY_PAYLOAD, payloadJSON);

            JobInfo jobInfo = SubmitPostService.constructJobInfo(this, payloadJSON.length() * 2L, extras);
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
        redditGalleryImageInfoList = adapter.getRedditGalleryImageInfoList();
        outState.putParcelableArrayList(REDDIT_GALLERY_IMAGE_INFO_STATE, redditGalleryImageInfoList);
        // The camera path leaves this as the only handle on a photo that hasn't reached the
        // adapter yet, so it has to survive process death while the camera app is foreground.
        outState.putString(IMAGE_URI_STATE, imageUri == null ? null : imageUri.toString());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SUBREDDIT_SELECTION_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                subredditName = data.getStringExtra(SelectThingReturnKey.RETURN_EXTRA_SUBREDDIT_OR_USER_NAME);
                iconUrl = data.getStringExtra(SelectThingReturnKey.RETURN_EXTRA_SUBREDDIT_OR_USER_ICON);
                subredditSelected = true;
                subredditIsUser = data.getIntExtra(SelectThingReturnKey.RETURN_EXTRA_THING_TYPE, SelectThingReturnKey.THING_TYPE.SUBREDDIT) == SelectThingReturnKey.THING_TYPE.USER;

                binding.subredditNameTextViewPostGalleryActivity.setTextColor(primaryTextColor);
                binding.subredditNameTextViewPostGalleryActivity.setText(subredditName);
                displaySubredditIcon();

                flair = null;
                flairController.setHasFlair(false);
                binding.flairCustomTextViewPostGalleryActivity.setVisibility(View.VISIBLE);
                binding.flairCustomTextViewPostGalleryActivity.setBackgroundColor(resources.getColor(android.R.color.transparent));
                applyFlairLabelStyle();
                notifyControllerOfSubreddit();
            }
        } else if (requestCode == PICK_IMAGE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                if (data == null) {
                    Snackbar.make(binding.coordinatorLayoutPostGalleryActivity, R.string.error_getting_image, Snackbar.LENGTH_SHORT).show();
                    return;
                }

                Uri pickedImageUri = data.getData();
                if (pickedImageUri == null) {
                    Snackbar.make(binding.coordinatorLayoutPostGalleryActivity, R.string.error_getting_image, Snackbar.LENGTH_SHORT).show();
                    return;
                }

                imageUri = pickedImageUri;
                String pickedImageId = adapter.addImage(pickedImageUri.toString());
                uploadImage(pickedImageUri, pickedImageId);
            }
        } else if (requestCode == CAPTURE_IMAGE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Uri capturedImageUri = imageUri;
                if (capturedImageUri == null) {
                    Snackbar.make(binding.coordinatorLayoutPostGalleryActivity, R.string.error_getting_image, Snackbar.LENGTH_SHORT).show();
                    return;
                }

                String capturedImageId = adapter.addImage(capturedImageUri.toString());
                uploadImage(capturedImageUri, capturedImageId);
            } else {
                // Camera cancelled/dismissed — drop the unused temp output file (it was never added
                // to the gallery adapter, so nothing else references it).
                Utils.deleteContentUriFileQuietly(this, imageUri);
                imageUri = null;
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
        binding.flairCustomTextViewPostGalleryActivity.setText(flair.getText());
        binding.flairCustomTextViewPostGalleryActivity.setBackgroundColor(flairBackgroundColor);
        binding.flairCustomTextViewPostGalleryActivity.setBorderColor(flairBackgroundColor);
        binding.flairCustomTextViewPostGalleryActivity.setTextColor(flairTextColor);
        flairController.setHasFlair(true);
    }

    @Override
    public void onAccountSelected(Account account) {
        selectedAccount = account;

        mGlide.load(account.getProfileImageUrl())
                .transform(new RoundedCornersTransformation(72, 0))
                .error(mGlide.load(R.drawable.subreddit_default_icon)
                        .transform(new RoundedCornersTransformation(72, 0)))
                .into(binding.accountIconGifImageViewPostGalleryActivity);

        binding.accountNameTextViewPostGalleryActivity.setText(account.getAccountName());

        // Flair requirements are per-account: re-fetch with the newly selected account's token.
        notifyControllerOfSubreddit();
    }

    public void setCaptionAndUrl(int position, String caption, String url) {
        if (adapter != null) {
            adapter.setCaptionAndUrl(position, caption, url);
        }
    }

    @Subscribe
    public void onAccountSwitchEvent(SwitchAccountEvent event) {
        finish();
    }

    @Subscribe
    public void onSubmitGalleryPostEvent(SubmitGalleryPostEvent submitGalleryPostEvent) {
        isPosting = false;
        flairController.setPosting(false);
        mPostingSnackbar.dismiss();
        if (submitGalleryPostEvent.postSuccess) {
            Intent intent = new Intent(this, LinkResolverActivity.class);
            intent.setData(Uri.parse(submitGalleryPostEvent.postUrl));
            startActivity(intent);
            finish();
        } else {
            if (submitGalleryPostEvent.errorMessage == null || submitGalleryPostEvent.errorMessage.isEmpty()) {
                Snackbar.make(binding.coordinatorLayoutPostGalleryActivity, R.string.post_failed, Snackbar.LENGTH_SHORT).show();
            } else {
                Snackbar.make(binding.coordinatorLayoutPostGalleryActivity, submitGalleryPostEvent.errorMessage.substring(0, 1).toUpperCase()
                        + submitGalleryPostEvent.errorMessage.substring(1), Snackbar.LENGTH_SHORT).show();
            }
        }
    }
}