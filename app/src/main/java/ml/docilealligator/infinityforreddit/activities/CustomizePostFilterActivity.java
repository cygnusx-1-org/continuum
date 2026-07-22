package ml.docilealligator.infinityforreddit.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ActivityCustomizePostFilterBinding;
import ml.docilealligator.infinityforreddit.postfilter.PostFilter;
import ml.docilealligator.infinityforreddit.subreddit.SubredditWithSelection;
import ml.docilealligator.infinityforreddit.utils.Utils;
import ml.docilealligator.infinityforreddit.viewmodels.CustomizePostFilterViewModel;
import ml.docilealligator.infinityforreddit.viewmodels.SavePostFilterResult;

public class CustomizePostFilterActivity extends BaseActivity {

    public static final String EXTRA_POST_FILTER = "EPF";
    public static final String EXTRA_FROM_SETTINGS = "EFS";
    public static final String EXTRA_EXCLUDE_SUBREDDIT = "EES";
    public static final String EXTRA_CONTAIN_SUBREDDIT = "ECS";
    public static final String EXTRA_EXCLUDE_USER = "EEU";
    public static final String EXTRA_CONTAIN_USER = "ECU";
    public static final String EXTRA_EXCLUDE_FLAIR = "EEF";
    public static final String EXTRA_CONTAIN_FLAIR = "ECF";
    public static final String EXTRA_EXCLUDE_DOMAIN = "EED";
    public static final String EXTRA_CONTAIN_DOMAIN = "ECD";
    public static final String EXTRA_START_FILTERED_POSTS_WHEN_FINISH = "ESFPWF";
    public static final String RETURN_EXTRA_POST_FILTER = "REPF";
    private static final String POST_FILTER_STATE = "PFS";
    private static final String ORIGINAL_NAME_STATE = "ONS";
    private static final int ADD_EXCLUDE_SUBREDDITS_REQUEST_CODE = 1;
    private static final int ADD_CONTAIN_SUBREDDITS_REQUEST_CODE = 11;
    private static final int ADD_EXCLUDE_USERS_REQUEST_CODE      = 3;
    private static final int ADD_CONTAIN_USERS_REQUEST_CODE      = 33;

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
    private ActivityCustomizePostFilterBinding binding;
    private PostFilter postFilter;
    private boolean fromSettings;
    private String originalName;
    @Nullable
    private AlertDialog activeDialog;
    private CustomizePostFilterViewModel customizePostFilterViewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        setImmersiveModeNotApplicableBelowAndroid16();

        super.onCreate(savedInstanceState);
        binding = ActivityCustomizePostFilterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyCustomTheme();

        customizePostFilterViewModel = new ViewModelProvider(this,
                CustomizePostFilterViewModel.Companion.provideFactory(mExecutor, mRedditDataRoomDatabase))
                .get(CustomizePostFilterViewModel.class);
        // The save runs in the ViewModel so its result — and the duplicate-name dialog — reaches the
        // live instance after a rotation, instead of firing on a dead one (CHUNKS deferred item 4). On
        // a back-press there is no observer, so a completed save no longer launches FilteredPosts at a
        // user who left; on a rotation the relaunched instance's observer picks it up.
        customizePostFilterViewModel.getSaveResult().observe(this, result -> {
            if (result instanceof SavePostFilterResult.Success) {
                if (getIntent().getBooleanExtra(EXTRA_START_FILTERED_POSTS_WHEN_FINISH, false)) {
                    Intent intent = new Intent(this, FilteredPostsActivity.class);
                    intent.putExtras(getIntent());
                    intent.putExtra(FilteredPostsActivity.EXTRA_CONSTRUCTED_POST_FILTER, postFilter);
                    startActivity(intent);
                } else {
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra(RETURN_EXTRA_POST_FILTER, postFilter);
                    setResult(Activity.RESULT_OK, returnIntent);
                }
                finish();
            } else if (result instanceof SavePostFilterResult.Duplicate) {
                activeDialog = new MaterialAlertDialogBuilder(CustomizePostFilterActivity.this, R.style.MaterialAlertDialogTheme)
                        .setTitle(getString(R.string.duplicate_post_filter_dialog_title, postFilter.name))
                        .setMessage(R.string.duplicate_post_filter_dialog_message)
                        .setPositiveButton(R.string.override, (dialogInterface, i) -> customizePostFilterViewModel.savePostFilter(postFilter, postFilter.name))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            } else if (result instanceof SavePostFilterResult.Failure) {
                // The DB write threw (and rolled back); the guard has been released, so the user can retry.
                Snackbar.make(binding.coordinatorLayoutCustomizePostFilterActivity,
                        R.string.save_post_filter_failed, Snackbar.LENGTH_SHORT).show();
            }
        });

        if (isImmersiveInterfaceRespectForcedEdgeToEdge()) {
            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(binding.appbarLayoutCustomizePostFilterActivity);
            }

            ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                    Insets windowInsets = Utils.getInsets(insets, false, isForcedImmersiveInterface());
                    Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());

                    setMargins(binding.toolbarCustomizePostFilterActivity,
                            windowInsets.left,
                            windowInsets.top,
                            windowInsets.right,
                            BaseActivity.IGNORE_MARGIN);

                    binding.contentWrapperViewCustomizePostFilterActivity.setPadding(
                            windowInsets.left,
                            0,
                            windowInsets.right,
                            windowInsets.bottom
                    );

                    setMargins(binding.contentWrapperViewCustomizePostFilterActivity,
                            BaseActivity.IGNORE_MARGIN,
                            BaseActivity.IGNORE_MARGIN,
                            BaseActivity.IGNORE_MARGIN,
                            imeInsets.bottom);

                    return WindowInsetsCompat.CONSUMED;
                }
            });
        }

        setSupportActionBar(binding.toolbarCustomizePostFilterActivity);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        setToolbarGoToTop(binding.toolbarCustomizePostFilterActivity);

        fromSettings = getIntent().getBooleanExtra(EXTRA_FROM_SETTINGS, false);

        binding.postTypeTextLinearLayoutCustomizePostFilterActivity.setOnClickListener(view -> {
            binding.postTypeTextSwitchCustomizePostFilterActivity.performClick();
        });

        binding.postTypeLinkLinearLayoutCustomizePostFilterActivity.setOnClickListener(view -> {
            binding.postTypeLinkSwitchCustomizePostFilterActivity.performClick();
        });

        binding.postTypeImageLinearLayoutCustomizePostFilterActivity.setOnClickListener(view -> {
            binding.postTypeImageSwitchCustomizePostFilterActivity.performClick();
        });

        binding.postTypeGifLinearLayoutCustomizePostFilterActivity.setOnClickListener(view -> {
            binding.postTypeGifSwitchCustomizePostFilterActivity.performClick();
        });

        binding.postTypeVideoLinearLayoutCustomizePostFilterActivity.setOnClickListener(view -> {
            binding.postTypeVideoSwitchCustomizePostFilterActivity.performClick();
        });

        binding.postTypeGalleryLinearLayoutCustomizePostFilterActivity.setOnClickListener(view -> {
            binding.postTypeGallerySwitchCustomizePostFilterActivity.performClick();
        });

        binding.onlyNsfwLinearLayoutCustomizePostFilterActivity.setOnClickListener(view -> {
            binding.onlyNsfwSwitchCustomizePostFilterActivity.performClick();
        });

        binding.onlySpoilerLinearLayoutCustomizePostFilterActivity.setOnClickListener(view -> {
            binding.onlySpoilerSwitchCustomizePostFilterActivity.performClick();
        });

        binding.excludeAddSubredditsImageViewCustomizePostFilterActivity.setOnClickListener(view -> {
            Intent intent = new Intent(this, SubredditMultiselectionActivity.class);
            intent.putExtra(SubredditMultiselectionActivity.EXTRA_GET_SELECTED_SUBREDDITS,
                    Objects.requireNonNull(binding.excludesSubredditsTextInputEditTextCustomizePostFilterActivity.getText()).toString().trim());
            startActivityForResult(intent, ADD_EXCLUDE_SUBREDDITS_REQUEST_CODE);
        });
        binding.containAddSubredditsImageViewCustomizePostFilterActivity.setOnClickListener(view -> {
            Intent intent = new Intent(this, SubredditMultiselectionActivity.class);
            intent.putExtra(SubredditMultiselectionActivity.EXTRA_GET_SELECTED_SUBREDDITS,
                    Objects.requireNonNull(binding.containsSubredditsTextInputEditTextCustomizePostFilterActivity.getText()).toString().trim());
            startActivityForResult(intent, ADD_CONTAIN_SUBREDDITS_REQUEST_CODE);
        });
        binding.excludeAddUsersImageViewCustomizePostFilterActivity.setOnClickListener(view -> {
            Intent intent = new Intent(this, SearchActivity.class);
            intent.putExtra(SearchActivity.EXTRA_SEARCH_ONLY_USERS, true);
            intent.putExtra(SearchActivity.EXTRA_IS_MULTI_SELECTION, true);
            startActivityForResult(intent, ADD_EXCLUDE_USERS_REQUEST_CODE);
        });
        binding.containAddUsersImageViewCustomizePostFilterActivity.setOnClickListener(view -> {
            Intent intent = new Intent(this, SearchActivity.class);
            intent.putExtra(SearchActivity.EXTRA_SEARCH_ONLY_USERS, true);
            intent.putExtra(SearchActivity.EXTRA_IS_MULTI_SELECTION, true);
            startActivityForResult(intent, ADD_CONTAIN_USERS_REQUEST_CODE);
        });

        if (savedInstanceState != null) {
            // onSaveInstanceState always writes both keys.
            postFilter = Objects.requireNonNull(savedInstanceState.getParcelable(POST_FILTER_STATE));
            originalName = Objects.requireNonNull(savedInstanceState.getString(ORIGINAL_NAME_STATE));
        } else {
            PostFilter postFilterExtra = getIntent().getParcelableExtra(EXTRA_POST_FILTER);
            if (postFilterExtra == null) {
                postFilter = new PostFilter();
                originalName = "";
            } else {
                postFilter = postFilterExtra;
                if (!fromSettings) {
                    originalName = "";
                } else {
                    originalName = postFilterExtra.name;
                }
            }
            bindView();
        }
    }

    private void bindView() {
        binding.nameTextInputEditTextCustomizePostFilterActivity.setText(postFilter.name);
        binding.postTypeTextSwitchCustomizePostFilterActivity.setChecked(postFilter.containTextType);
        binding.postTypeLinkSwitchCustomizePostFilterActivity.setChecked(postFilter.containLinkType);
        binding.postTypeImageSwitchCustomizePostFilterActivity.setChecked(postFilter.containImageType);
        binding.postTypeGifSwitchCustomizePostFilterActivity.setChecked(postFilter.containGifType);
        binding.postTypeVideoSwitchCustomizePostFilterActivity.setChecked(postFilter.containVideoType);
        binding.postTypeGallerySwitchCustomizePostFilterActivity.setChecked(postFilter.containGalleryType);
        binding.onlyNsfwSwitchCustomizePostFilterActivity.setChecked(postFilter.onlyNSFW);
        binding.onlySpoilerSwitchCustomizePostFilterActivity.setChecked(postFilter.onlySpoiler);
        binding.titleExcludesStringsTextInputEditTextCustomizePostFilterActivity.setText(postFilter.postTitleExcludesStrings);
        binding.titleContainsStringsTextInputEditTextCustomizePostFilterActivity.setText(postFilter.postTitleContainsStrings);
        binding.titleExcludesRegexTextInputEditTextCustomizePostFilterActivity.setText(postFilter.postTitleExcludesRegex);
        binding.titleContainsRegexTextInputEditTextCustomizePostFilterActivity.setText(postFilter.postTitleContainsRegex);
        binding.excludesSubredditsTextInputEditTextCustomizePostFilterActivity.setText(postFilter.excludeSubreddits);
        binding.containsSubredditsTextInputEditTextCustomizePostFilterActivity.setText(postFilter.containSubreddits);
        binding.excludesUsersTextInputEditTextCustomizePostFilterActivity.setText(postFilter.excludeUsers);
        binding.containsUsersTextInputEditTextCustomizePostFilterActivity.setText(postFilter.containUsers);
        binding.excludesFlairsTextInputEditTextCustomizePostFilterActivity.setText(postFilter.excludeFlairs);
        binding.containsFlairsTextInputEditTextCustomizePostFilterActivity.setText(postFilter.containFlairs);
        binding.excludeDomainsTextInputEditTextCustomizePostFilterActivity.setText(postFilter.excludeDomains);
        binding.containDomainsTextInputEditTextCustomizePostFilterActivity.setText(postFilter.containDomains);
        binding.minVoteTextInputEditTextCustomizePostFilterActivity.setText(Integer.toString(postFilter.minVote));
        binding.maxVoteTextInputEditTextCustomizePostFilterActivity.setText(Integer.toString(postFilter.maxVote));
        binding.minCommentsTextInputEditTextCustomizePostFilterActivity.setText(Integer.toString(postFilter.minComments));
        binding.maxCommentsTextInputEditTextCustomizePostFilterActivity.setText(Integer.toString(postFilter.maxComments));

        Intent intent = getIntent();
        String excludeSubreddit = intent.getStringExtra(EXTRA_EXCLUDE_SUBREDDIT);
        String excludeUser = intent.getStringExtra(EXTRA_EXCLUDE_USER);
        String excludeFlair = intent.getStringExtra(EXTRA_EXCLUDE_FLAIR);
        String containFlair = intent.getStringExtra(EXTRA_CONTAIN_FLAIR);
        // Reduced to a bare domain up front, so a value that yields no domain at all is skipped
        // entirely rather than appended after its separator.
        String excludeDomain = domainOf(intent.getStringExtra(EXTRA_EXCLUDE_DOMAIN));
        String containDomain = domainOf(intent.getStringExtra(EXTRA_CONTAIN_DOMAIN));
        String containSubreddit = intent.getStringExtra(EXTRA_CONTAIN_SUBREDDIT);
        String containUser = intent.getStringExtra(EXTRA_CONTAIN_USER);

        if (excludeSubreddit != null && !excludeSubreddit.equals("")) {
            if (!Objects.requireNonNull(binding.excludesSubredditsTextInputEditTextCustomizePostFilterActivity.getText()).toString().equals("")) {
                binding.excludesSubredditsTextInputEditTextCustomizePostFilterActivity.append(",");
            }
            binding.excludesSubredditsTextInputEditTextCustomizePostFilterActivity.append(excludeSubreddit);
        }
        if (containSubreddit != null && !containSubreddit.equals("")) {
            if (!Objects.requireNonNull(binding.containsSubredditsTextInputEditTextCustomizePostFilterActivity.getText()).toString().equals("")) {
                binding.containsSubredditsTextInputEditTextCustomizePostFilterActivity.append(",");
            }
            binding.containsSubredditsTextInputEditTextCustomizePostFilterActivity.append(containSubreddit);
        }
        if (containUser != null && !containUser.equals("")) {
            if (!Objects.requireNonNull(binding.containsUsersTextInputEditTextCustomizePostFilterActivity.getText()).toString().equals("")) {
                binding.containsUsersTextInputEditTextCustomizePostFilterActivity.append(",");
            }
            binding.containsUsersTextInputEditTextCustomizePostFilterActivity.append(containUser);
        }
        if (excludeUser != null && !excludeUser.equals("")) {
            if (!Objects.requireNonNull(binding.excludesUsersTextInputEditTextCustomizePostFilterActivity.getText()).toString().equals("")) {
                binding.excludesUsersTextInputEditTextCustomizePostFilterActivity.append(",");
            }
            binding.excludesUsersTextInputEditTextCustomizePostFilterActivity.append(excludeUser);
        }
        if (excludeFlair != null && !excludeFlair.equals("")) {
            if (!Objects.requireNonNull(binding.excludesFlairsTextInputEditTextCustomizePostFilterActivity.getText()).toString().equals("")) {
                binding.excludesFlairsTextInputEditTextCustomizePostFilterActivity.append(",");
            }
            binding.excludesFlairsTextInputEditTextCustomizePostFilterActivity.append(excludeFlair);
        }
        if (containFlair != null && !containFlair.equals("")) {
            if (!Objects.requireNonNull(binding.containsFlairsTextInputEditTextCustomizePostFilterActivity.getText()).toString().equals("")) {
                binding.containsFlairsTextInputEditTextCustomizePostFilterActivity.append(",");
            }
            binding.containsFlairsTextInputEditTextCustomizePostFilterActivity.append(containFlair);
        }
        if (excludeDomain != null) {
            if (!Objects.requireNonNull(binding.excludeDomainsTextInputEditTextCustomizePostFilterActivity.getText()).toString().equals("")) {
                binding.excludeDomainsTextInputEditTextCustomizePostFilterActivity.append(",");
            }
            binding.excludeDomainsTextInputEditTextCustomizePostFilterActivity.append(excludeDomain);
        }
        if (containDomain != null) {
            if (!Objects.requireNonNull(binding.containDomainsTextInputEditTextCustomizePostFilterActivity.getText()).toString().equals("")) {
                binding.containDomainsTextInputEditTextCustomizePostFilterActivity.append(",");
            }
            binding.containDomainsTextInputEditTextCustomizePostFilterActivity.append(containDomain);
        }
    }

    /**
     * Reduces a post URL to the domain this filter stores, or null when there is no domain to store.
     *
     * <p>{@link Uri#getHost()} is null for anything without a scheme — a bare "example.com" parses as
     * a relative path — and in that case the value already is the domain. It is also null for a
     * scheme with no authority ("https://"), which is neither a URL nor a domain; returning null for
     * that lets the caller skip it, instead of appending a stray separator or throwing on
     * {@code Editable.append(null)}.
     */
    @Nullable
    private static String domainOf(@Nullable String urlOrDomain) {
        if (urlOrDomain == null || urlOrDomain.isEmpty()) {
            return null;
        }
        String host = Uri.parse(urlOrDomain).getHost();
        if (host != null && !host.isEmpty()) {
            return host;
        }
        return urlOrDomain.contains("://") ? null : urlOrDomain;
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
        binding.coordinatorLayoutCustomizePostFilterActivity.setBackgroundColor(mCustomThemeWrapper.getBackgroundColor());
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutCustomizePostFilterActivity,
                binding.collapsingToolbarLayoutCustomizePostFilterActivity, binding.toolbarCustomizePostFilterActivity);
        applyAppBarScrollFlagsIfApplicable(binding.collapsingToolbarLayoutCustomizePostFilterActivity);
        int primaryTextColor = mCustomThemeWrapper.getPrimaryTextColor();
        int primaryIconColor = mCustomThemeWrapper.getPrimaryIconColor();
        int filledCardViewBackgroundColor = mCustomThemeWrapper.getFilledCardViewBackgroundColor();

        binding.nameCardViewCustomizePostFilterActivity.setCardBackgroundColor(filledCardViewBackgroundColor);
        binding.nameExplanationTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.nameTextInputLayoutCustomizePostFilterActivity.setBoxStrokeColor(primaryTextColor);
        binding.nameTextInputLayoutCustomizePostFilterActivity.setDefaultHintTextColor(ColorStateList.valueOf(primaryTextColor));
        binding.nameTextInputEditTextCustomizePostFilterActivity.setTextColor(primaryTextColor);

        binding.postTypeCardViewCustomizePostFilterActivity.setCardBackgroundColor(filledCardViewBackgroundColor);
        binding.postTypeExplanationTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.postTypeTextTextViewCustomizePostFilterActivity.setCompoundDrawablesWithIntrinsicBounds(Utils.getTintedDrawable(this, R.drawable.ic_text_day_night_24dp, primaryIconColor), null, null, null);
        binding.postTypeTextTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.postTypeLinkTextViewCustomizePostFilterActivity.setCompoundDrawablesWithIntrinsicBounds(Utils.getTintedDrawable(this, R.drawable.ic_link_day_night_24dp, primaryIconColor), null, null, null);
        binding.postTypeLinkTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.postTypeImageTextViewCustomizePostFilterActivity.setCompoundDrawablesWithIntrinsicBounds(Utils.getTintedDrawable(this, R.drawable.ic_image_day_night_24dp, primaryIconColor), null, null, null);
        binding.postTypeImageTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.postTypeGifTextViewCustomizePostFilterActivity.setCompoundDrawablesWithIntrinsicBounds(Utils.getTintedDrawable(this, R.drawable.ic_image_day_night_24dp, primaryIconColor), null, null, null);
        binding.postTypeGifTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.postTypeVideoTextViewCustomizePostFilterActivity.setCompoundDrawablesWithIntrinsicBounds(Utils.getTintedDrawable(this, R.drawable.ic_video_day_night_24dp, primaryIconColor), null, null, null);
        binding.postTypeVideoTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.postTypeGalleryTextViewCustomizePostFilterActivity.setCompoundDrawablesWithIntrinsicBounds(Utils.getTintedDrawable(this, R.drawable.ic_gallery_day_night_24dp, primaryIconColor), null, null, null);
        binding.postTypeGalleryTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);

        binding.onlyNsfwSpoilerCardViewCustomizePostFilterActivity.setCardBackgroundColor(filledCardViewBackgroundColor);
        binding.onlyNsfwSpoilerExplanationTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.onlyNsfwTextViewCustomizePostFilterActivity.setCompoundDrawablesWithIntrinsicBounds(Utils.getTintedDrawable(this, R.drawable.ic_nsfw_on_day_night_24dp, primaryIconColor), null, null, null);
        binding.onlyNsfwTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.onlySpoilerTextViewCustomizePostFilterActivity.setCompoundDrawablesWithIntrinsicBounds(Utils.getTintedDrawable(this, R.drawable.ic_spoiler_black_24dp, primaryIconColor), null, null, null);
        binding.onlySpoilerTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);

        binding.titleStringsCardViewCustomizePostFilterActivity.setCardBackgroundColor(filledCardViewBackgroundColor);
        binding.titleExcludeStringsExplanationTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.titleExcludesStringsTextInputLayoutCustomizePostFilterActivity.setBoxStrokeColor(primaryTextColor);
        binding.titleExcludesStringsTextInputLayoutCustomizePostFilterActivity.setDefaultHintTextColor(ColorStateList.valueOf(primaryTextColor));
        binding.titleExcludesStringsTextInputEditTextCustomizePostFilterActivity.setTextColor(primaryTextColor);

        binding.titleContainsStringsExplanationTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.titleContainsStringsTextInputLayoutCustomizePostFilterActivity.setBoxStrokeColor(primaryTextColor);
        binding.titleContainsStringsTextInputLayoutCustomizePostFilterActivity.setDefaultHintTextColor(ColorStateList.valueOf(primaryTextColor));
        binding.titleContainsStringsTextInputEditTextCustomizePostFilterActivity.setTextColor(primaryTextColor);

        binding.titleRegexCardViewCustomizePostFilterActivity.setCardBackgroundColor(filledCardViewBackgroundColor);
        binding.titleExcludesRegexExplanationTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.titleExcludesRegexTextInputLayoutCustomizePostFilterActivity.setBoxStrokeColor(primaryTextColor);
        binding.titleExcludesRegexTextInputLayoutCustomizePostFilterActivity.setDefaultHintTextColor(ColorStateList.valueOf(primaryTextColor));
        binding.titleExcludesRegexTextInputEditTextCustomizePostFilterActivity.setTextColor(primaryTextColor);

        binding.titleContainsRegexExplanationTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.titleContainsRegexTextInputLayoutCustomizePostFilterActivity.setBoxStrokeColor(primaryTextColor);
        binding.titleContainsRegexTextInputLayoutCustomizePostFilterActivity.setDefaultHintTextColor(ColorStateList.valueOf(primaryTextColor));
        binding.titleContainsRegexTextInputEditTextCustomizePostFilterActivity.setTextColor(primaryTextColor);

        binding.subredditsCardViewCustomizePostFilterActivity.setCardBackgroundColor(filledCardViewBackgroundColor);
        binding.excludeSubredditsExplanationTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.excludesSubredditsTextInputLayoutCustomizePostFilterActivity.setBoxStrokeColor(primaryTextColor);
        binding.excludesSubredditsTextInputLayoutCustomizePostFilterActivity.setDefaultHintTextColor(ColorStateList.valueOf(primaryTextColor));
        binding.excludesSubredditsTextInputEditTextCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.excludeAddSubredditsImageViewCustomizePostFilterActivity.setImageDrawable(Utils.getTintedDrawable(this, R.drawable.ic_add_24dp, primaryIconColor));

        binding.containSubredditsExplanationTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.containsSubredditsTextInputLayoutCustomizePostFilterActivity.setBoxStrokeColor(primaryTextColor);
        binding.containsSubredditsTextInputLayoutCustomizePostFilterActivity.setDefaultHintTextColor(ColorStateList.valueOf(primaryTextColor));
        binding.containsSubredditsTextInputEditTextCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.containAddSubredditsImageViewCustomizePostFilterActivity.setImageDrawable(Utils.getTintedDrawable(this, R.drawable.ic_add_24dp, primaryIconColor));

        binding.usersCardViewCustomizePostFilterActivity.setCardBackgroundColor(filledCardViewBackgroundColor);
        binding.excludeUsersExplanationTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.excludesUsersTextInputLayoutCustomizePostFilterActivity.setBoxStrokeColor(primaryTextColor);
        binding.excludesUsersTextInputLayoutCustomizePostFilterActivity.setDefaultHintTextColor(ColorStateList.valueOf(primaryTextColor));
        binding.excludesUsersTextInputEditTextCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.excludeAddUsersImageViewCustomizePostFilterActivity.setImageDrawable(Utils.getTintedDrawable(this, R.drawable.ic_add_24dp, primaryIconColor));

        binding.containUsersExplanationTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.containsUsersTextInputLayoutCustomizePostFilterActivity.setBoxStrokeColor(primaryTextColor);
        binding.containsUsersTextInputLayoutCustomizePostFilterActivity.setDefaultHintTextColor(ColorStateList.valueOf(primaryTextColor));
        binding.containsUsersTextInputEditTextCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.containAddUsersImageViewCustomizePostFilterActivity.setImageDrawable(Utils.getTintedDrawable(this, R.drawable.ic_add_24dp, primaryIconColor));

        binding.flairsCardViewCustomizePostFilterActivity.setCardBackgroundColor(filledCardViewBackgroundColor);
        binding.excludeFlairsExplanationTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.excludesFlairsTextInputLayoutCustomizePostFilterActivity.setBoxStrokeColor(primaryTextColor);
        binding.excludesFlairsTextInputLayoutCustomizePostFilterActivity.setDefaultHintTextColor(ColorStateList.valueOf(primaryTextColor));
        binding.excludesFlairsTextInputEditTextCustomizePostFilterActivity.setTextColor(primaryTextColor);

        binding.containFlairsExplanationTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.containsFlairsTextInputLayoutCustomizePostFilterActivity.setBoxStrokeColor(primaryTextColor);
        binding.containsFlairsTextInputLayoutCustomizePostFilterActivity.setDefaultHintTextColor(ColorStateList.valueOf(primaryTextColor));
        binding.containsFlairsTextInputEditTextCustomizePostFilterActivity.setTextColor(primaryTextColor);

        binding.domainsCardViewCustomizePostFilterActivity.setCardBackgroundColor(filledCardViewBackgroundColor);
        binding.excludeDomainsExplanationTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.excludeDomainsTextInputLayoutCustomizePostFilterActivity.setBoxStrokeColor(primaryTextColor);
        binding.excludeDomainsTextInputLayoutCustomizePostFilterActivity.setDefaultHintTextColor(ColorStateList.valueOf(primaryTextColor));
        binding.excludeDomainsTextInputEditTextCustomizePostFilterActivity.setTextColor(primaryTextColor);

        binding.containDomainsExplanationTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.containDomainsTextInputLayoutCustomizePostFilterActivity.setBoxStrokeColor(primaryTextColor);
        binding.containDomainsTextInputLayoutCustomizePostFilterActivity.setDefaultHintTextColor(ColorStateList.valueOf(primaryTextColor));
        binding.containDomainsTextInputEditTextCustomizePostFilterActivity.setTextColor(primaryTextColor);

        binding.voteCardViewCustomizePostFilterActivity.setCardBackgroundColor(filledCardViewBackgroundColor);
        binding.minVoteExplanationTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.minVoteTextInputLayoutCustomizePostFilterActivity.setBoxStrokeColor(primaryTextColor);
        binding.minVoteTextInputLayoutCustomizePostFilterActivity.setDefaultHintTextColor(ColorStateList.valueOf(primaryTextColor));
        binding.minVoteTextInputEditTextCustomizePostFilterActivity.setTextColor(primaryTextColor);

        binding.maxVoteExplanationTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.maxVoteTextInputLayoutCustomizePostFilterActivity.setBoxStrokeColor(primaryTextColor);
        binding.maxVoteTextInputLayoutCustomizePostFilterActivity.setDefaultHintTextColor(ColorStateList.valueOf(primaryTextColor));
        binding.maxVoteTextInputEditTextCustomizePostFilterActivity.setTextColor(primaryTextColor);

        binding.commentsCardViewCustomizePostFilterActivity.setCardBackgroundColor(filledCardViewBackgroundColor);
        binding.minCommentsExplanationTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.minCommentsTextInputLayoutCustomizePostFilterActivity.setBoxStrokeColor(primaryTextColor);
        binding.minCommentsTextInputLayoutCustomizePostFilterActivity.setDefaultHintTextColor(ColorStateList.valueOf(primaryTextColor));
        binding.minCommentsTextInputEditTextCustomizePostFilterActivity.setTextColor(primaryTextColor);

        binding.maxCommentsExplanationTextViewCustomizePostFilterActivity.setTextColor(primaryTextColor);
        binding.maxCommentsTextInputLayoutCustomizePostFilterActivity.setBoxStrokeColor(primaryTextColor);
        binding.maxCommentsTextInputLayoutCustomizePostFilterActivity.setDefaultHintTextColor(ColorStateList.valueOf(primaryTextColor));
        binding.maxCommentsTextInputEditTextCustomizePostFilterActivity.setTextColor(primaryTextColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            binding.nameTextInputLayoutCustomizePostFilterActivity.setCursorColor(ColorStateList.valueOf(primaryTextColor));
            binding.titleExcludesStringsTextInputLayoutCustomizePostFilterActivity.setCursorColor(ColorStateList.valueOf(primaryTextColor));
            binding.titleContainsStringsTextInputLayoutCustomizePostFilterActivity.setCursorColor(ColorStateList.valueOf(primaryTextColor));
            binding.titleExcludesRegexTextInputLayoutCustomizePostFilterActivity.setCursorColor(ColorStateList.valueOf(primaryTextColor));
            binding.titleContainsRegexTextInputLayoutCustomizePostFilterActivity.setCursorColor(ColorStateList.valueOf(primaryTextColor));
            binding.excludesSubredditsTextInputLayoutCustomizePostFilterActivity.setCursorColor(ColorStateList.valueOf(primaryTextColor));
            binding.containsSubredditsTextInputLayoutCustomizePostFilterActivity.setCursorColor(ColorStateList.valueOf(primaryTextColor));
            binding.excludesUsersTextInputLayoutCustomizePostFilterActivity.setCursorColor(ColorStateList.valueOf(primaryTextColor));
            binding.containsUsersTextInputLayoutCustomizePostFilterActivity.setCursorColor(ColorStateList.valueOf(primaryTextColor));
            binding.excludesFlairsTextInputLayoutCustomizePostFilterActivity.setCursorColor(ColorStateList.valueOf(primaryTextColor));
            binding.containsFlairsTextInputLayoutCustomizePostFilterActivity.setCursorColor(ColorStateList.valueOf(primaryTextColor));
            binding.excludeDomainsTextInputLayoutCustomizePostFilterActivity.setCursorColor(ColorStateList.valueOf(primaryTextColor));
            binding.containDomainsTextInputLayoutCustomizePostFilterActivity.setCursorColor(ColorStateList.valueOf(primaryTextColor));
            binding.minVoteTextInputLayoutCustomizePostFilterActivity.setCursorColor(ColorStateList.valueOf(primaryTextColor));
            binding.maxVoteTextInputLayoutCustomizePostFilterActivity.setCursorColor(ColorStateList.valueOf(primaryTextColor));
            binding.minCommentsTextInputLayoutCustomizePostFilterActivity.setCursorColor(ColorStateList.valueOf(primaryTextColor));
            binding.maxCommentsTextInputLayoutCustomizePostFilterActivity.setCursorColor(ColorStateList.valueOf(primaryTextColor));
        } else {
            setCursorDrawableColor(binding.nameTextInputEditTextCustomizePostFilterActivity, primaryTextColor);
            setCursorDrawableColor(binding.titleExcludesStringsTextInputEditTextCustomizePostFilterActivity, primaryTextColor);
            setCursorDrawableColor(binding.titleContainsStringsTextInputEditTextCustomizePostFilterActivity, primaryTextColor);
            setCursorDrawableColor(binding.titleExcludesRegexTextInputEditTextCustomizePostFilterActivity, primaryTextColor);
            setCursorDrawableColor(binding.titleContainsRegexTextInputEditTextCustomizePostFilterActivity, primaryTextColor);
            setCursorDrawableColor(binding.excludesSubredditsTextInputEditTextCustomizePostFilterActivity, primaryTextColor);
            setCursorDrawableColor(binding.containsSubredditsTextInputEditTextCustomizePostFilterActivity, primaryTextColor);
            setCursorDrawableColor(binding.excludesUsersTextInputEditTextCustomizePostFilterActivity, primaryTextColor);
            setCursorDrawableColor(binding.containsUsersTextInputEditTextCustomizePostFilterActivity, primaryTextColor);
            setCursorDrawableColor(binding.excludesFlairsTextInputEditTextCustomizePostFilterActivity, primaryTextColor);
            setCursorDrawableColor(binding.containsUsersTextInputEditTextCustomizePostFilterActivity, primaryTextColor);
            setCursorDrawableColor(binding.containsFlairsTextInputEditTextCustomizePostFilterActivity, primaryTextColor);
            setCursorDrawableColor(binding.excludeDomainsTextInputEditTextCustomizePostFilterActivity, primaryTextColor);
            setCursorDrawableColor(binding.containDomainsTextInputEditTextCustomizePostFilterActivity, primaryTextColor);
            setCursorDrawableColor(binding.minVoteTextInputEditTextCustomizePostFilterActivity, primaryTextColor);
            setCursorDrawableColor(binding.maxVoteTextInputEditTextCustomizePostFilterActivity, primaryTextColor);
            setCursorDrawableColor(binding.minCommentsTextInputEditTextCustomizePostFilterActivity, primaryTextColor);
            setCursorDrawableColor(binding.maxCommentsTextInputEditTextCustomizePostFilterActivity, primaryTextColor);
        }

        if (typeface != null) {
            Utils.setFontToAllTextViews(binding.coordinatorLayoutCustomizePostFilterActivity, typeface);
        }
    }

    private void setCursorDrawableColor(EditText editText, int color) {
        try {
            Field fCursorDrawableRes = TextView.class.getDeclaredField("mCursorDrawableRes");
            fCursorDrawableRes.setAccessible(true);
            int mCursorDrawableRes = fCursorDrawableRes.getInt(editText);
            Field fEditor = TextView.class.getDeclaredField("mEditor");
            fEditor.setAccessible(true);
            Object editor = fEditor.get(editText);
            Class<?> clazz = editor.getClass();
            Field fCursorDrawable = clazz.getDeclaredField("mCursorDrawable");
            fCursorDrawable.setAccessible(true);
            Drawable[] drawables = new Drawable[2];
            drawables[0] = editText.getContext().getResources().getDrawable(mCursorDrawableRes);
            drawables[1] = editText.getContext().getResources().getDrawable(mCursorDrawableRes);
            drawables[0].setColorFilter(color, PorterDuff.Mode.SRC_IN);
            drawables[1].setColorFilter(color, PorterDuff.Mode.SRC_IN);
            fCursorDrawable.set(editor, drawables);
        } catch (Throwable ignored) { }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.customize_post_filter_activity, menu);
        if (fromSettings) {
            menu.findItem(R.id.action_save_customize_post_filter_activity).setVisible(false);
        }
        applyMenuItemTheme(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_save_customize_post_filter_activity) {
            try {
                constructPostFilter();
                if (getIntent().getBooleanExtra(EXTRA_START_FILTERED_POSTS_WHEN_FINISH, false)) {
                    Intent intent = new Intent(this, FilteredPostsActivity.class);
                    intent.putExtras(getIntent());
                    intent.putExtra(FilteredPostsActivity.EXTRA_CONSTRUCTED_POST_FILTER, postFilter);
                    startActivity(intent);
                } else {
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra(RETURN_EXTRA_POST_FILTER, postFilter);
                    setResult(Activity.RESULT_OK, returnIntent);
                }
                finish();
            } catch (PatternSyntaxException e) {
                Toast.makeText(this, R.string.invalid_regex, Toast.LENGTH_SHORT).show();
            }

            return true;
        } else if (item.getItemId() == R.id.action_save_to_database_customize_post_filter_activity) {
            try {
                constructPostFilter();

                if (!postFilter.name.equals("")) {
                    customizePostFilterViewModel.savePostFilter(postFilter, originalName);
                } else {
                    Toast.makeText(CustomizePostFilterActivity.this, R.string.post_filter_requires_a_name, Toast.LENGTH_LONG).show();
                }
            } catch (PatternSyntaxException e) {
                Toast.makeText(this, R.string.invalid_regex, Toast.LENGTH_SHORT).show();
            }
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == ADD_EXCLUDE_SUBREDDITS_REQUEST_CODE) {
                ArrayList<SubredditWithSelection> subredditWithSelections = data.getParcelableArrayListExtra(SubredditMultiselectionActivity.EXTRA_RETURN_SELECTED_SUBREDDITS);
                if (subredditWithSelections != null) {
                    updateSubredditsUsersNames(new ArrayList<>(subredditWithSelections.stream().map(SubredditWithSelection::getName).collect(Collectors.toList())),
                            binding.excludesSubredditsTextInputEditTextCustomizePostFilterActivity);
                }
            } else if (requestCode == ADD_CONTAIN_SUBREDDITS_REQUEST_CODE) {
                ArrayList<SubredditWithSelection> subredditWithSelections = data.getParcelableArrayListExtra(SubredditMultiselectionActivity.EXTRA_RETURN_SELECTED_SUBREDDITS);
                if (subredditWithSelections != null) {
                    updateSubredditsUsersNames(new ArrayList<>(subredditWithSelections.stream().map(SubredditWithSelection::getName).collect(Collectors.toList())),
                            binding.containsSubredditsTextInputEditTextCustomizePostFilterActivity);
                }
            } else if (requestCode == ADD_EXCLUDE_USERS_REQUEST_CODE) {
                ArrayList<String> usernames = data.getStringArrayListExtra(SearchActivity.RETURN_EXTRA_SELECTED_USERNAMES);
                updateSubredditsUsersNames(usernames, binding.excludesUsersTextInputEditTextCustomizePostFilterActivity);
            } else if (requestCode == ADD_CONTAIN_USERS_REQUEST_CODE) {
                ArrayList<String> usernames = data.getStringArrayListExtra(SearchActivity.RETURN_EXTRA_SELECTED_USERNAMES);
                updateSubredditsUsersNames(usernames, binding.containsUsersTextInputEditTextCustomizePostFilterActivity);
            }
        }
    }

    private void updateSubredditsUsersNames(@Nullable ArrayList<String> subredditNames,
                                      com.google.android.material.textfield.TextInputEditText targetEditText) {
        if (subredditNames == null || subredditNames.isEmpty()) return;

        String current = Objects.requireNonNull(targetEditText.getText()).toString().trim();
        if (!current.isEmpty() && current.charAt(current.length() - 1) != ',') {
            targetEditText.setText(current + ",");
        }

        StringBuilder sb = new StringBuilder();
        for (String s : subredditNames) {
            sb.append(s).append(",");
        }
        if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
        targetEditText.append(sb.toString());
    }


    private void constructPostFilter() throws PatternSyntaxException {
        postFilter.name = Objects.requireNonNull(binding.nameTextInputEditTextCustomizePostFilterActivity.getText()).toString();
        postFilter.maxVote = binding.maxVoteTextInputEditTextCustomizePostFilterActivity.getText() == null || binding.maxVoteTextInputEditTextCustomizePostFilterActivity.getText().toString().equals("") ? -1 : Integer.parseInt(binding.maxVoteTextInputEditTextCustomizePostFilterActivity.getText().toString());
        postFilter.minVote = binding.minVoteTextInputEditTextCustomizePostFilterActivity.getText() == null || binding.minVoteTextInputEditTextCustomizePostFilterActivity.getText().toString().equals("") ? -1 : Integer.parseInt(binding.minVoteTextInputEditTextCustomizePostFilterActivity.getText().toString());
        postFilter.maxComments = binding.maxCommentsTextInputEditTextCustomizePostFilterActivity.getText() == null || binding.maxCommentsTextInputEditTextCustomizePostFilterActivity.getText().toString().equals("") ? -1 : Integer.parseInt(binding.maxCommentsTextInputEditTextCustomizePostFilterActivity.getText().toString());
        postFilter.minComments = binding.minCommentsTextInputEditTextCustomizePostFilterActivity.getText() == null || binding.minCommentsTextInputEditTextCustomizePostFilterActivity.getText().toString().equals("") ? -1 : Integer.parseInt(binding.minCommentsTextInputEditTextCustomizePostFilterActivity.getText().toString());
        postFilter.maxAwards = -1;
        postFilter.minAwards = -1;
        postFilter.postTitleExcludesRegex = Objects.requireNonNull(binding.titleExcludesRegexTextInputEditTextCustomizePostFilterActivity.getText()).toString();
        Pattern.compile(postFilter.postTitleExcludesRegex);
        postFilter.postTitleContainsRegex = Objects.requireNonNull(binding.titleContainsRegexTextInputEditTextCustomizePostFilterActivity.getText()).toString();
        Pattern.compile(postFilter.postTitleContainsRegex);
        postFilter.postTitleExcludesStrings = Objects.requireNonNull(binding.titleExcludesStringsTextInputEditTextCustomizePostFilterActivity.getText()).toString();
        postFilter.postTitleContainsStrings = Objects.requireNonNull(binding.titleContainsStringsTextInputEditTextCustomizePostFilterActivity.getText()).toString();
        postFilter.excludeSubreddits = Objects.requireNonNull(binding.excludesSubredditsTextInputEditTextCustomizePostFilterActivity.getText()).toString();
        postFilter.containSubreddits = Objects.requireNonNull(binding.containsSubredditsTextInputEditTextCustomizePostFilterActivity.getText()).toString();
        postFilter.excludeUsers = Objects.requireNonNull(binding.excludesUsersTextInputEditTextCustomizePostFilterActivity.getText()).toString();
        postFilter.containUsers = Objects.requireNonNull(binding.containsUsersTextInputEditTextCustomizePostFilterActivity.getText()).toString();
        postFilter.excludeFlairs = Objects.requireNonNull(binding.excludesFlairsTextInputEditTextCustomizePostFilterActivity.getText()).toString();
        postFilter.containFlairs = Objects.requireNonNull(binding.containsFlairsTextInputEditTextCustomizePostFilterActivity.getText()).toString();
        postFilter.excludeDomains = Objects.requireNonNull(binding.excludeDomainsTextInputEditTextCustomizePostFilterActivity.getText()).toString();
        postFilter.containDomains = Objects.requireNonNull(binding.containDomainsTextInputEditTextCustomizePostFilterActivity.getText()).toString();
        postFilter.containTextType = binding.postTypeTextSwitchCustomizePostFilterActivity.isChecked();
        postFilter.containLinkType = binding.postTypeLinkSwitchCustomizePostFilterActivity.isChecked();
        postFilter.containImageType = binding.postTypeImageSwitchCustomizePostFilterActivity.isChecked();
        postFilter.containGifType = binding.postTypeGifSwitchCustomizePostFilterActivity.isChecked();
        postFilter.containVideoType = binding.postTypeVideoSwitchCustomizePostFilterActivity.isChecked();
        postFilter.containGalleryType = binding.postTypeGallerySwitchCustomizePostFilterActivity.isChecked();
        postFilter.onlyNSFW = binding.onlyNsfwSwitchCustomizePostFilterActivity.isChecked();
        postFilter.onlySpoiler = binding.onlySpoilerSwitchCustomizePostFilterActivity.isChecked();
    }

    @Override
    protected void onDestroy() {
        // The duplicate-name prompt is a raw AlertDialog rather than a DialogFragment, so one left
        // showing across a rotation leaks its window and this activity with it.
        AlertDialog dialog = activeDialog;
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        activeDialog = null;
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(POST_FILTER_STATE, postFilter);
        outState.putString(ORIGINAL_NAME_STATE, originalName);
    }
}
