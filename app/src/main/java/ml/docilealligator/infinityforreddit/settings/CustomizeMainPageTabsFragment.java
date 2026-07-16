package ml.docilealligator.infinityforreddit.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.activities.CustomizeTabsOrderActivity;
import ml.docilealligator.infinityforreddit.activities.SettingsActivity;
import ml.docilealligator.infinityforreddit.databinding.FragmentCustomizeMainPageTabsBinding;
import ml.docilealligator.infinityforreddit.utils.AppRestartHelper;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;

public class CustomizeMainPageTabsFragment extends Fragment {

    private FragmentCustomizeMainPageTabsBinding binding;
    @Inject
    @Named("main_activity_tabs")
    SharedPreferences mainActivityTabsSharedPreferences;
    private SettingsActivity mActivity;
    private TextView restartWarning;
    private OnBackPressedCallback mRestartOnBackCallback;
    private boolean mSettingsChanged = false;
    private ActivityResultLauncher<Intent> editTabsLauncher;

    public CustomizeMainPageTabsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Restart is deferred until the user leaves the screen, so editing several tab
        // settings only restarts once. Fires on back button / back gesture / Up.
        mRestartOnBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (mActivity != null) {
                    AppRestartHelper.triggerAppRestart(mActivity);
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(this, mRestartOnBackCallback);

        editTabsLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null
                    && result.getData().getBooleanExtra(CustomizeTabsOrderActivity.EXTRA_CHANGED, false)) {
                mSettingsChanged = true;
                updateRestartButtonVisibility();
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentCustomizeMainPageTabsBinding.inflate(inflater, container, false);

        ((Infinity) mActivity.getApplication()).getAppComponent().inject(this);

        // Initialize the restart warning banner (mirrors API Keys: the restart happens on back,
        // the banner just warns that leaving will restart). Styled via spans so it survives the
        // setFontToAllTextViews() pass below, which replaces the base typeface but not spans/color.
        restartWarning = binding.getRoot().findViewById(R.id.restart_warning_customize_main_page_tabs);
        if (restartWarning != null) {
            String symbol = "⚠";
            SpannableString warningText = new SpannableString(symbol + "  " + getString(R.string.app_will_restart));
            warningText.setSpan(new RelativeSizeSpan(2f), 0, symbol.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            warningText.setSpan(new StyleSpan(Typeface.BOLD), 0, warningText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            restartWarning.setText(warningText);
            // Accent is the theme's alert/attention color, so the text stands out.
            restartWarning.setTextColor(mActivity.customThemeWrapper.getColorAccent());
            restartWarning.setBackgroundColor(mActivity.customThemeWrapper.getCardViewBackgroundColor());
        }
        updateRestartButtonVisibility(); // Set initial visibility (hidden)

        binding.getRoot().setBackgroundColor(mActivity.customThemeWrapper.getBackgroundColor());
        applyCustomTheme();

        if (mActivity.isImmersiveInterfaceRespectForcedEdgeToEdge()) {
            ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                    Insets allInsets = Utils.getInsets(insets, false, mActivity.isForcedImmersiveInterface());
                    binding.getRoot().setPadding(allInsets.left, 0, allInsets.right, allInsets.bottom);
                    return WindowInsetsCompat.CONSUMED;
                }
            });
        }

        if (mActivity.typeface != null) {
            Utils.setFontToAllTextViews(binding.getRoot(), mActivity.typeface);
        }

        binding.tabsLinearLayoutCustomizeMainPageTabsFragment.setOnClickListener(view ->
                editTabsLauncher.launch(new Intent(mActivity, CustomizeTabsOrderActivity.class)));

        setupShowToggle(binding.showFavoriteMultiredditsSwitchMaterialCustomizeMainPageTabsFragment,
                binding.showFavoriteMultiredditsLinearLayoutCustomizeMainPageTabsFragment,
                SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_FAVORITE_MULTIREDDITS);
        setupShowToggle(binding.showMultiredditsSwitchMaterialCustomizeMainPageTabsFragment,
                binding.showMultiredditsLinearLayoutCustomizeMainPageTabsFragment,
                SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_MULTIREDDITS);
        setupShowToggle(binding.showFavoriteUsersMultiredditsSwitchMaterialCustomizeMainPageTabsFragment,
                binding.showFavoriteUsersMultiredditsLinearLayoutCustomizeMainPageTabsFragment,
                SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_FAVORITE_USERS_MULTIREDDITS);
        setupShowToggle(binding.showUsersMultiredditsSwitchMaterialCustomizeMainPageTabsFragment,
                binding.showUsersMultiredditsLinearLayoutCustomizeMainPageTabsFragment,
                SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_USERS_MULTIREDDITS);
        setupShowToggle(binding.showFavoriteSubscribedSubredditsSwitchMaterialCustomizeMainPageTabsFragment,
                binding.showFavoriteSubscribedSubredditsLinearLayoutCustomizeMainPageTabsFragment,
                SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_FAVORITE_SUBSCRIBED_SUBREDDITS);
        setupShowToggle(binding.showSubscribedSubredditsSwitchMaterialCustomizeMainPageTabsFragment,
                binding.showSubscribedSubredditsLinearLayoutCustomizeMainPageTabsFragment,
                SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_SUBSCRIBED_SUBREDDITS);

        // "Enable Tabs" maps directly to MAIN_PAGE_SHOW_TAB_NAMES (checked = show the tab bar), default on.
        boolean showTabNames = mainActivityTabsSharedPreferences.getBoolean(accountPrefix() + SharedPreferencesUtils.MAIN_PAGE_SHOW_TAB_NAMES, true);
        binding.showTabNamesSwitchMaterialCustomizeMainPageTabsFragment.setChecked(showTabNames);
        binding.showTabNamesSwitchMaterialCustomizeMainPageTabsFragment.setOnCheckedChangeListener((compoundButton, checked) -> {
            mainActivityTabsSharedPreferences.edit().putBoolean(accountPrefix() + SharedPreferencesUtils.MAIN_PAGE_SHOW_TAB_NAMES, checked).apply();
            mSettingsChanged = true;
            updateRestartButtonVisibility();
        });
        binding.showTabNamesLinearLayoutCustomizeMainPageTabsFragment.setOnClickListener(view ->
                binding.showTabNamesSwitchMaterialCustomizeMainPageTabsFragment.performClick());

        return binding.getRoot();
    }

    private String accountPrefix() {
        return mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName;
    }

    private void setupShowToggle(android.widget.CompoundButton switchMaterial, View row, int source) {
        String key = MainPageTabsUtils.toggleKeyForGroupSource(source);
        switchMaterial.setChecked(mainActivityTabsSharedPreferences.getBoolean(accountPrefix() + key, false));
        switchMaterial.setOnCheckedChangeListener((compoundButton, checked) -> {
            mainActivityTabsSharedPreferences.edit().putBoolean(accountPrefix() + key, checked).apply();
            if (!checked) {
                // Turning a toggle off removes its items from the saved list; enabling re-materializes
                // them (on the Tabs screen / at runtime), so nothing to do here for the on case.
                List<MainPageTabInput> order = MainPageTabsUtils.load(mainActivityTabsSharedPreferences, mActivity.accountName);
                if (MainPageTabsUtils.removeSource(order, source)) {
                    MainPageTabsUtils.save(mainActivityTabsSharedPreferences, mActivity.accountName, order);
                }
            }
            mSettingsChanged = true;
            updateRestartButtonVisibility();
        });
        row.setOnClickListener(view -> switchMaterial.performClick());
    }

    private void updateRestartButtonVisibility() {
        if (mRestartOnBackCallback != null) {
            mRestartOnBackCallback.setEnabled(mSettingsChanged);
        }
        if (restartWarning != null) {
            restartWarning.setVisibility(mSettingsChanged ? View.VISIBLE : View.GONE);
        }
    }

    private void applyCustomTheme() {
        int primaryTextColor = mActivity.customThemeWrapper.getPrimaryTextColor();
        int secondaryTextColor = mActivity.customThemeWrapper.getSecondaryTextColor();
        int colorAccent = mActivity.customThemeWrapper.getColorAccent();
        binding.tabsTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.tabsSummaryTextViewCustomizeMainPageTabsFragment.setTextColor(secondaryTextColor);
        binding.moreTabsGroupSummaryCustomizeMainPageTabsFragment.setTextColor(colorAccent);
        binding.moreTabsInfoTextViewCustomizeMainPageTabsFragment.setTextColor(secondaryTextColor);
        Drawable infoDrawable = Utils.getTintedDrawable(mActivity, R.drawable.ic_info_preference_day_night_24dp, secondaryTextColor);
        binding.moreTabsInfoTextViewCustomizeMainPageTabsFragment.setCompoundDrawablesWithIntrinsicBounds(infoDrawable, null, null, null);
        binding.showFavoriteMultiredditsTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.showMultiredditsTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.showFavoriteUsersMultiredditsTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.showUsersMultiredditsTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.showFavoriteSubscribedSubredditsTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.showSubscribedSubredditsTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.showTabNamesTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mActivity = (SettingsActivity) context;
    }
}
