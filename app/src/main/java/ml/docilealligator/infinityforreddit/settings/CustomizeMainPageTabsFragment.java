package ml.docilealligator.infinityforreddit.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import javax.inject.Inject;
import javax.inject.Named;

import ml.docilealligator.infinityforreddit.Constants;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.thing.SelectThingReturnKey;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.activities.SearchActivity;
import ml.docilealligator.infinityforreddit.activities.SettingsActivity;
import ml.docilealligator.infinityforreddit.activities.SubscribedThingListingActivity;
import ml.docilealligator.infinityforreddit.databinding.FragmentCustomizeMainPageTabsBinding;
import ml.docilealligator.infinityforreddit.multireddit.MultiReddit;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;
import ml.docilealligator.infinityforreddit.utils.AppRestartHelper;

public class CustomizeMainPageTabsFragment extends Fragment {

    private FragmentCustomizeMainPageTabsBinding binding;
    @Inject
    @Named("main_activity_tabs")
    SharedPreferences mainActivityTabsSharedPreferences;
    private SettingsActivity mActivity;
    private int tabCount;
    private String tab1CurrentTitle;
    private int tab1CurrentPostType;
    private String tab1CurrentName;
    private String tab2CurrentTitle;
    private int tab2CurrentPostType;
    private String tab2CurrentName;
    private String tab3CurrentTitle;
    private int tab3CurrentPostType;
    private String tab3CurrentName;
    private String tab4CurrentTitle;
    private int tab4CurrentPostType;
    private String tab4CurrentName;
    private String tab5CurrentTitle;
    private int tab5CurrentPostType;
    private String tab5CurrentName;
    private String tab6CurrentTitle;
    private int tab6CurrentPostType;
    private String tab6CurrentName;
    private Button restartButton;
    private boolean mSettingsChanged = false;

    public CustomizeMainPageTabsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentCustomizeMainPageTabsBinding.inflate(inflater, container, false);

        ((Infinity) mActivity.getApplication()).getAppComponent().inject(this);

        // Initialize the restart button
        restartButton = binding.getRoot().findViewById(R.id.restart_button_customize_main_page_tabs);
        if (restartButton != null) {
            restartButton.setOnClickListener(v -> {
                if (mActivity != null) {
                    AppRestartHelper.triggerAppRestart(mActivity);
                    mSettingsChanged = false; // Reset flag
                    updateRestartButtonVisibility(); // Hide button
                }
            });
        }
        updateRestartButtonVisibility(); // Set initial visibility (should be hidden)

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

        String[] typeValues;
        if (mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
            typeValues = mActivity.getResources().getStringArray(R.array.settings_tab_post_type_anonymous);
        } else {
            typeValues = mActivity.getResources().getStringArray(R.array.settings_tab_post_type);
        }

        tabCount = mainActivityTabsSharedPreferences.getInt((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_COUNT, Constants.DEFAULT_TAB_COUNT);
        binding.tabCountTextViewCustomizeMainPageTabsFragment.setText(Integer.toString(tabCount));
        binding.tabCountLinearLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                    .setTitle(R.string.settings_tab_count)
                    .setSingleChoiceItems(R.array.settings_main_page_tab_count, tabCount - 1, (dialogInterface, i) -> {
                        tabCount = i + 1;
                        mainActivityTabsSharedPreferences.edit().putInt((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_COUNT, tabCount).apply();
                        binding.tabCountTextViewCustomizeMainPageTabsFragment.setText(Integer.toString(tabCount));
                        updateTabViewsVisibility(tabCount);
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                        dialogInterface.dismiss();
                    })
                    .show();
        });

        boolean showTabNames = mainActivityTabsSharedPreferences.getBoolean((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_SHOW_TAB_NAMES, true);
        binding.showTabNamesSwitchMaterialCustomizeMainPageTabsFragment.setChecked(showTabNames);
        binding.showTabNamesSwitchMaterialCustomizeMainPageTabsFragment.setOnCheckedChangeListener((compoundButton, b) -> {
            mainActivityTabsSharedPreferences.edit().putBoolean((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_SHOW_TAB_NAMES, b).apply();
            mSettingsChanged = true;
            updateRestartButtonVisibility();
        });
        binding.showTabNamesLinearLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> binding.showTabNamesSwitchMaterialCustomizeMainPageTabsFragment.performClick());

        tab1CurrentTitle = mainActivityTabsSharedPreferences.getString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_1_TITLE, getString(R.string.home));
        tab1CurrentPostType = mainActivityTabsSharedPreferences.getInt((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_1_POST_TYPE, SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_HOME);
        if (!mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
            tab1CurrentPostType = Utils.fixIndexOutOfBoundsUsingPredetermined(typeValues, tab1CurrentPostType, 1);
        }
        tab1CurrentName = mainActivityTabsSharedPreferences.getString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_1_NAME, "");
        binding.tab1TypeSummaryTextViewCustomizeMainPageTabsFragment.setText(typeValues[tab1CurrentPostType]);
        binding.tab1TitleSummaryTextViewCustomizeMainPageTabsFragment.setText(tab1CurrentTitle);
        binding.tab1NameSummaryTextViewCustomizeMainPageTabsFragment.setText(tab1CurrentName);
        applyTab1NameView(binding.tab1NameConstraintLayoutCustomizeMainPageTabsFragment, binding.tab1NameTitleTextViewCustomizeMainPageTabsFragment, tab1CurrentPostType);

        View dialogView = mActivity.getLayoutInflater().inflate(R.layout.dialog_edit_text, null);
        EditText editText = dialogView.findViewById(R.id.edit_text_edit_text_dialog);

        binding.tab1TitleLinearLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            editText.setHint(R.string.settings_tab_title);
            editText.setText(tab1CurrentTitle);
            editText.requestFocus();
            Utils.showKeyboard(mActivity, new Handler(), editText);
            if (dialogView.getParent() != null) {
                ((ViewGroup) dialogView.getParent()).removeView(dialogView);
            }
            new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                    .setTitle(R.string.settings_tab_title)
                    .setView(dialogView)
                    .setPositiveButton(R.string.ok, (dialogInterface, i)
                            -> {
                        tab1CurrentTitle = editText.getText().toString();
                        mainActivityTabsSharedPreferences.edit().putString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_1_TITLE, tab1CurrentTitle).apply();
                        binding.tab1TitleSummaryTextViewCustomizeMainPageTabsFragment.setText(tab1CurrentTitle);
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                        Utils.hideKeyboard(mActivity);
                    })
                    .setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                        Utils.hideKeyboard(mActivity);
                    })
                    .show();
        });

        binding.tab1TypeLinearLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                    .setTitle(R.string.settings_tab_title)
                    .setSingleChoiceItems(typeValues, tab1CurrentPostType, (dialogInterface, i) -> {
                        tab1CurrentPostType = i;
                        mainActivityTabsSharedPreferences.edit().putInt((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_1_POST_TYPE, i).apply();
                        binding.tab1TypeSummaryTextViewCustomizeMainPageTabsFragment.setText(typeValues[i]);
                        applyTab1NameView(binding.tab1NameConstraintLayoutCustomizeMainPageTabsFragment, binding.tab1NameTitleTextViewCustomizeMainPageTabsFragment, i);
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                        dialogInterface.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        binding.tab1NameConstraintLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            int titleId;
            switch (tab1CurrentPostType) {
                case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT:
                    titleId = R.string.settings_tab_subreddit_name;
                    break;
                case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT:
                    titleId = R.string.settings_tab_multi_reddit_name;
                    break;
                case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_USER:
                    titleId = R.string.settings_tab_username;
                    break;
                default:
                    return;
            }
            editText.setText(tab1CurrentName);
            editText.setHint(titleId);
            editText.requestFocus();
            Utils.showKeyboard(mActivity, new Handler(), editText);
            if (dialogView.getParent() != null) {
                ((ViewGroup) dialogView.getParent()).removeView(dialogView);
            }
            new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                    .setTitle(titleId)
                    .setView(dialogView)
                    .setPositiveButton(R.string.ok, (dialogInterface, i)
                            -> {
                        tab1CurrentName = editText.getText().toString();
                        mainActivityTabsSharedPreferences.edit().putString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_1_NAME, tab1CurrentName).apply();
                        binding.tab1NameSummaryTextViewCustomizeMainPageTabsFragment.setText(tab1CurrentName);
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                        Utils.hideKeyboard(mActivity);
                    })
                    .setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                        Utils.hideKeyboard(mActivity);
                    })
                    .show();
        });

        binding.tab1NameAddImageViewCustomizeMainPageTabsFragment.setOnClickListener(view -> selectName(0));

        tab2CurrentTitle = mainActivityTabsSharedPreferences.getString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_2_TITLE, getString(R.string.popular));
        tab2CurrentPostType = mainActivityTabsSharedPreferences.getInt((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_2_POST_TYPE, SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_POPULAR);
        if (!mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
            tab2CurrentPostType = Utils.fixIndexOutOfBoundsUsingPredetermined(typeValues, tab2CurrentPostType, 1);
        }
        tab2CurrentName = mainActivityTabsSharedPreferences.getString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_2_NAME, "");
        binding.tab2TypeSummaryTextViewCustomizeMainPageTabsFragment.setText(typeValues[tab2CurrentPostType]);
        binding.tab2TitleSummaryTextViewCustomizeMainPageTabsFragment.setText(tab2CurrentTitle);
        binding.tab2NameSummaryTextViewCustomizeMainPageTabsFragment.setText(tab2CurrentName);
        applyTab2NameView(binding.tab2NameConstraintLayoutCustomizeMainPageTabsFragment, binding.tab2NameTitleTextViewCustomizeMainPageTabsFragment, tab2CurrentPostType);

        binding.tab2TitleLinearLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            editText.setHint(R.string.settings_tab_title);
            editText.setText(tab2CurrentTitle);
            editText.requestFocus();
            Utils.showKeyboard(mActivity, new Handler(), editText);
            if (dialogView.getParent() != null) {
                ((ViewGroup) dialogView.getParent()).removeView(dialogView);
            }
            new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                    .setTitle(R.string.settings_tab_title)
                    .setView(dialogView)
                    .setPositiveButton(R.string.ok, (dialogInterface, i)
                            -> {
                        tab2CurrentTitle = editText.getText().toString();
                        mainActivityTabsSharedPreferences.edit().putString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_2_TITLE, tab2CurrentTitle).apply();
                        binding.tab2TitleSummaryTextViewCustomizeMainPageTabsFragment.setText(tab2CurrentTitle);
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                        Utils.hideKeyboard(mActivity);
                    })
                    .setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                        Utils.hideKeyboard(mActivity);
                    })
                    .show();
        });

        binding.tab2TypeLinearLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                    .setTitle(R.string.settings_tab_title)
                    .setSingleChoiceItems(typeValues, tab2CurrentPostType, (dialogInterface, i) -> {
                        tab2CurrentPostType = i;
                        mainActivityTabsSharedPreferences.edit().putInt((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_2_POST_TYPE, i).apply();
                        binding.tab2TypeSummaryTextViewCustomizeMainPageTabsFragment.setText(typeValues[i]);
                        applyTab2NameView(binding.tab2NameConstraintLayoutCustomizeMainPageTabsFragment, binding.tab2NameTitleTextViewCustomizeMainPageTabsFragment, i);
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                        dialogInterface.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        binding.tab2NameConstraintLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            int titleId;
            switch (tab2CurrentPostType) {
                case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT:
                    titleId = R.string.settings_tab_subreddit_name;
                    break;
                case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT:
                    titleId = R.string.settings_tab_multi_reddit_name;
                    break;
                case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_USER:
                    titleId = R.string.settings_tab_username;
                    break;
                default:
                    return;
            }
            editText.setText(tab2CurrentName);
            editText.setHint(titleId);
            editText.requestFocus();
            Utils.showKeyboard(mActivity, new Handler(), editText);
            if (dialogView.getParent() != null) {
                ((ViewGroup) dialogView.getParent()).removeView(dialogView);
            }
            new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                    .setTitle(titleId)
                    .setView(dialogView)
                    .setPositiveButton(R.string.ok, (dialogInterface, i)
                            -> {
                        tab2CurrentName = editText.getText().toString();
                        mainActivityTabsSharedPreferences.edit().putString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_2_NAME, tab2CurrentName).apply();
                        binding.tab2NameSummaryTextViewCustomizeMainPageTabsFragment.setText(tab2CurrentName);
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                        Utils.hideKeyboard(mActivity);
                    })
                    .setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                        Utils.hideKeyboard(mActivity);
                    })
                    .show();
        });

        binding.tab2NameAddImageViewCustomizeMainPageTabsFragment.setOnClickListener(view -> selectName(1));

        tab3CurrentTitle = mainActivityTabsSharedPreferences.getString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_3_TITLE, getString(R.string.all));
        tab3CurrentPostType = mainActivityTabsSharedPreferences.getInt((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_3_POST_TYPE, SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_ALL);
        if (!mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
            tab3CurrentPostType = Utils.fixIndexOutOfBoundsUsingPredetermined(typeValues, tab3CurrentPostType, 1);
        }
        tab3CurrentName = mainActivityTabsSharedPreferences.getString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_3_NAME, "");
        binding.tab3TypeSummaryTextViewCustomizeMainPageTabsFragment.setText(typeValues[tab3CurrentPostType]);
        binding.tab3TitleSummaryTextViewCustomizeMainPageTabsFragment.setText(tab3CurrentTitle);
        binding.tab3NameSummaryTextViewCustomizeMainPageTabsFragment.setText(tab3CurrentName);
        applyTab3NameView(binding.tab3NameConstraintLayoutCustomizeMainPageTabsFragment, binding.tab3NameTitleTextViewCustomizeMainPageTabsFragment, tab3CurrentPostType);

        binding.tab3TitleLinearLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            editText.setHint(R.string.settings_tab_title);
            editText.setText(tab3CurrentTitle);
            editText.requestFocus();
            Utils.showKeyboard(mActivity, new Handler(), editText);
            if (dialogView.getParent() != null) {
                ((ViewGroup) dialogView.getParent()).removeView(dialogView);
            }
            new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                    .setTitle(R.string.settings_tab_title)
                    .setView(dialogView)
                    .setPositiveButton(R.string.ok, (dialogInterface, i)
                            -> {
                        tab3CurrentTitle = editText.getText().toString();
                        mainActivityTabsSharedPreferences.edit().putString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_3_TITLE, tab3CurrentTitle).apply();
                        binding.tab3TitleSummaryTextViewCustomizeMainPageTabsFragment.setText(tab3CurrentTitle);
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                        Utils.hideKeyboard(mActivity);
                    })
                    .setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                        Utils.hideKeyboard(mActivity);
                    })
                    .show();
        });

        binding.tab3TypeLinearLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                    .setTitle(R.string.settings_tab_title)
                    .setSingleChoiceItems(typeValues, tab3CurrentPostType, (dialogInterface, i) -> {
                        tab3CurrentPostType = i;
                        mainActivityTabsSharedPreferences.edit().putInt((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_3_POST_TYPE, i).apply();
                        binding.tab3TypeSummaryTextViewCustomizeMainPageTabsFragment.setText(typeValues[i]);
                        applyTab3NameView(binding.tab3NameConstraintLayoutCustomizeMainPageTabsFragment, binding.tab3NameTitleTextViewCustomizeMainPageTabsFragment, i);
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                        dialogInterface.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        binding.tab3NameConstraintLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            int titleId;
            switch (tab3CurrentPostType) {
                case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT:
                    titleId = R.string.settings_tab_subreddit_name;
                    break;
                case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT:
                    titleId = R.string.settings_tab_multi_reddit_name;
                    break;
                case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_USER:
                    titleId = R.string.settings_tab_username;
                    break;
                default:
                    return;
            }
            editText.setText(tab3CurrentName);
            editText.setHint(titleId);
            editText.requestFocus();
            Utils.showKeyboard(mActivity, new Handler(), editText);
            if (dialogView.getParent() != null) {
                ((ViewGroup) dialogView.getParent()).removeView(dialogView);
            }
            new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                    .setTitle(titleId)
                    .setView(dialogView)
                    .setPositiveButton(R.string.ok, (dialogInterface, i)
                            -> {
                        tab3CurrentName = editText.getText().toString();
                        mainActivityTabsSharedPreferences.edit().putString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_3_NAME, tab3CurrentName).apply();
                        binding.tab3NameSummaryTextViewCustomizeMainPageTabsFragment.setText(tab3CurrentName);
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                        Utils.hideKeyboard(mActivity);
                    })
                    .setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                        Utils.hideKeyboard(mActivity);
                    })
                    .show();
        });

        binding.tab3NameAddImageViewCustomizeMainPageTabsFragment.setOnClickListener(view -> selectName(2));

        tab4CurrentTitle = mainActivityTabsSharedPreferences.getString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_4_TITLE, getString(R.string.upvoted));
        tab4CurrentPostType = mainActivityTabsSharedPreferences.getInt((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_4_POST_TYPE, SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_ALL);
        if (!mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
            tab4CurrentPostType = Utils.fixIndexOutOfBoundsUsingPredetermined(typeValues, tab4CurrentPostType, 1);
        }
        tab4CurrentName = mainActivityTabsSharedPreferences.getString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_4_NAME, "");
        binding.tab4TypeSummaryTextViewCustomizeMainPageTabsFragment.setText(typeValues[tab4CurrentPostType]);
        binding.tab4TitleSummaryTextViewCustomizeMainPageTabsFragment.setText(tab4CurrentTitle);
        binding.tab4NameSummaryTextViewCustomizeMainPageTabsFragment.setText(tab4CurrentName);
        applyTab4NameView(binding.tab4NameConstraintLayoutCustomizeMainPageTabsFragment, binding.tab4NameTitleTextViewCustomizeMainPageTabsFragment, tab4CurrentPostType);

        binding.tab4TitleLinearLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            editText.setHint(R.string.settings_tab_title);
            editText.setText(tab4CurrentTitle);
            editText.requestFocus();
            Utils.showKeyboard(mActivity, new Handler(), editText);
            if (dialogView.getParent() != null) {
                ((ViewGroup) dialogView.getParent()).removeView(dialogView);
            }
            new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                    .setTitle(R.string.settings_tab_title)
                    .setView(dialogView)
                    .setPositiveButton(R.string.ok, (dialogInterface, i)
                            -> {
                        tab4CurrentTitle = editText.getText().toString();
                        mainActivityTabsSharedPreferences.edit().putString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_4_TITLE, tab4CurrentTitle).apply();
                        binding.tab4TitleSummaryTextViewCustomizeMainPageTabsFragment.setText(tab4CurrentTitle);
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                        Utils.hideKeyboard(mActivity);
                    })
                    .setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                        Utils.hideKeyboard(mActivity);
                    })
                    .show();
        });

        binding.tab4TypeLinearLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                    .setTitle(R.string.settings_tab_title)
                    .setSingleChoiceItems(typeValues, tab4CurrentPostType, (dialogInterface, i) -> {
                        tab4CurrentPostType = i;
                        mainActivityTabsSharedPreferences.edit().putInt((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_4_POST_TYPE, i).apply();
                        binding.tab4TypeSummaryTextViewCustomizeMainPageTabsFragment.setText(typeValues[i]);
                        applyTab4NameView(binding.tab4NameConstraintLayoutCustomizeMainPageTabsFragment, binding.tab4NameTitleTextViewCustomizeMainPageTabsFragment, i);
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                        dialogInterface.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        binding.tab4NameConstraintLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            int titleId;
            switch (tab4CurrentPostType) {
                case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT:
                    titleId = R.string.settings_tab_subreddit_name;
                    break;
                case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT:
                    titleId = R.string.settings_tab_multi_reddit_name;
                    break;
                case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_USER:
                    titleId = R.string.settings_tab_username;
                    break;
                default:
                    return;
            }
            editText.setText(tab4CurrentName);
            editText.setHint(titleId);
            editText.requestFocus();
            Utils.showKeyboard(mActivity, new Handler(), editText);
            if (dialogView.getParent() != null) {
                ((ViewGroup) dialogView.getParent()).removeView(dialogView);
            }
            new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                    .setTitle(titleId)
                    .setView(dialogView)
                    .setPositiveButton(R.string.ok, (dialogInterface, i)
                            -> {
                        tab4CurrentName = editText.getText().toString();
                        mainActivityTabsSharedPreferences.edit().putString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_4_NAME, tab4CurrentName).apply();
                        binding.tab4NameSummaryTextViewCustomizeMainPageTabsFragment.setText(tab4CurrentName);
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                        Utils.hideKeyboard(mActivity);
                    })
                    .setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                        Utils.hideKeyboard(mActivity);
                    })
                    .show();
        });

        binding.tab4NameAddImageViewCustomizeMainPageTabsFragment.setOnClickListener(view -> selectName(3));

        tab5CurrentTitle = mainActivityTabsSharedPreferences.getString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_5_TITLE, getString(R.string.downvoted));
        tab5CurrentPostType = mainActivityTabsSharedPreferences.getInt((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_5_POST_TYPE, SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_ALL);
        if (!mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
            tab5CurrentPostType = Utils.fixIndexOutOfBoundsUsingPredetermined(typeValues, tab5CurrentPostType, 1);
        }
        tab5CurrentName = mainActivityTabsSharedPreferences.getString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_5_NAME, "");
        binding.tab5TypeSummaryTextViewCustomizeMainPageTabsFragment.setText(typeValues[tab5CurrentPostType]);
        binding.tab5TitleSummaryTextViewCustomizeMainPageTabsFragment.setText(tab5CurrentTitle);
        binding.tab5NameSummaryTextViewCustomizeMainPageTabsFragment.setText(tab5CurrentName);
        applyTab5NameView(binding.tab5NameConstraintLayoutCustomizeMainPageTabsFragment, binding.tab5NameTitleTextViewCustomizeMainPageTabsFragment, tab5CurrentPostType);

        binding.tab5TitleLinearLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            editText.setHint(R.string.settings_tab_title);
            editText.setText(tab5CurrentTitle);
            editText.requestFocus();
            Utils.showKeyboard(mActivity, new Handler(), editText);
            if (dialogView.getParent() != null) {
                ((ViewGroup) dialogView.getParent()).removeView(dialogView);
            }
            new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                    .setTitle(R.string.settings_tab_title)
                    .setView(dialogView)
                    .setPositiveButton(R.string.ok, (dialogInterface, i)
                            -> {
                        tab5CurrentTitle = editText.getText().toString();
                        mainActivityTabsSharedPreferences.edit().putString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_5_TITLE, tab5CurrentTitle).apply();
                        binding.tab5TitleSummaryTextViewCustomizeMainPageTabsFragment.setText(tab5CurrentTitle);
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                        Utils.hideKeyboard(mActivity);
                    })
                    .setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                        Utils.hideKeyboard(mActivity);
                    })
                    .show();
        });

        binding.tab5TypeLinearLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                    .setTitle(R.string.settings_tab_title)
                    .setSingleChoiceItems(typeValues, tab5CurrentPostType, (dialogInterface, i) -> {
                        tab5CurrentPostType = i;
                        mainActivityTabsSharedPreferences.edit().putInt((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_5_POST_TYPE, i).apply();
                        binding.tab5TypeSummaryTextViewCustomizeMainPageTabsFragment.setText(typeValues[i]);
                        applyTab5NameView(binding.tab5NameConstraintLayoutCustomizeMainPageTabsFragment, binding.tab5NameTitleTextViewCustomizeMainPageTabsFragment, i);
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                        dialogInterface.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        binding.tab5NameConstraintLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            int titleId;
            switch (tab5CurrentPostType) {
                case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT:
                    titleId = R.string.settings_tab_subreddit_name;
                    break;
                case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT:
                    titleId = R.string.settings_tab_multi_reddit_name;
                    break;
                case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_USER:
                    titleId = R.string.settings_tab_username;
                    break;
                default:
                    return;
            }
            editText.setText(tab5CurrentName);
            editText.setHint(titleId);
            editText.requestFocus();
            Utils.showKeyboard(mActivity, new Handler(), editText);
            if (dialogView.getParent() != null) {
                ((ViewGroup) dialogView.getParent()).removeView(dialogView);
            }
            new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                    .setTitle(titleId)
                    .setView(dialogView)
                    .setPositiveButton(R.string.ok, (dialogInterface, i)
                            -> {
                        tab5CurrentName = editText.getText().toString();
                        mainActivityTabsSharedPreferences.edit().putString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_5_NAME, tab5CurrentName).apply();
                        binding.tab5NameSummaryTextViewCustomizeMainPageTabsFragment.setText(tab5CurrentName);
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                        Utils.hideKeyboard(mActivity);
                    })
                    .setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                        Utils.hideKeyboard(mActivity);
                    })
                    .show();
        });

        binding.tab5NameAddImageViewCustomizeMainPageTabsFragment.setOnClickListener(view -> selectName(4));

        tab6CurrentTitle = mainActivityTabsSharedPreferences.getString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_6_TITLE, getString(R.string.saved));
        tab6CurrentPostType = mainActivityTabsSharedPreferences.getInt((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_6_POST_TYPE, SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_ALL);
        if (!mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
            tab6CurrentPostType = Utils.fixIndexOutOfBoundsUsingPredetermined(typeValues, tab6CurrentPostType, 1);
        }
        tab6CurrentName = mainActivityTabsSharedPreferences.getString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_6_NAME, "");
        binding.tab6TypeSummaryTextViewCustomizeMainPageTabsFragment.setText(typeValues[tab6CurrentPostType]);
        binding.tab6TitleSummaryTextViewCustomizeMainPageTabsFragment.setText(tab6CurrentTitle);
        binding.tab6NameSummaryTextViewCustomizeMainPageTabsFragment.setText(tab6CurrentName);
        applyTab6NameView(binding.tab6NameConstraintLayoutCustomizeMainPageTabsFragment, binding.tab6NameTitleTextViewCustomizeMainPageTabsFragment, tab6CurrentPostType);

        binding.tab6TitleLinearLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            editText.setHint(R.string.settings_tab_title);
            editText.setText(tab6CurrentTitle);
            editText.requestFocus();
            Utils.showKeyboard(mActivity, new Handler(), editText);
            if (dialogView.getParent() != null) {
                ((ViewGroup) dialogView.getParent()).removeView(dialogView);
            }
            new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                    .setTitle(R.string.settings_tab_title)
                    .setView(dialogView)
                    .setPositiveButton(R.string.ok, (dialogInterface, i)
                            -> {
                        tab6CurrentTitle = editText.getText().toString();
                        mainActivityTabsSharedPreferences.edit().putString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_6_TITLE, tab6CurrentTitle).apply();
                        binding.tab6TitleSummaryTextViewCustomizeMainPageTabsFragment.setText(tab6CurrentTitle);
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                        Utils.hideKeyboard(mActivity);
                    })
                    .setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                        Utils.hideKeyboard(mActivity);
                    })
                    .show();
        });

        binding.tab6TypeLinearLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                    .setTitle(R.string.settings_tab_title)
                    .setSingleChoiceItems(typeValues, tab6CurrentPostType, (dialogInterface, i) -> {
                        tab6CurrentPostType = i;
                        mainActivityTabsSharedPreferences.edit().putInt((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_6_POST_TYPE, i).apply();
                        binding.tab6TypeSummaryTextViewCustomizeMainPageTabsFragment.setText(typeValues[i]);
                        applyTab6NameView(binding.tab6NameConstraintLayoutCustomizeMainPageTabsFragment, binding.tab6NameTitleTextViewCustomizeMainPageTabsFragment, i);
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                        dialogInterface.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        binding.tab6NameConstraintLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            int titleId;
            switch (tab6CurrentPostType) {
                case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT:
                    titleId = R.string.settings_tab_subreddit_name;
                    break;
                case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT:
                    titleId = R.string.settings_tab_multi_reddit_name;
                    break;
                case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_USER:
                    titleId = R.string.settings_tab_username;
                    break;
                default:
                    return;
            }
            editText.setText(tab6CurrentName);
            editText.setHint(titleId);
            editText.requestFocus();
            Utils.showKeyboard(mActivity, new Handler(), editText);
            if (dialogView.getParent() != null) {
                ((ViewGroup) dialogView.getParent()).removeView(dialogView);
            }
            new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialogTheme)
                    .setTitle(titleId)
                    .setView(dialogView)
                    .setPositiveButton(R.string.ok, (dialogInterface, i)
                            -> {
                        tab6CurrentName = editText.getText().toString();
                        mainActivityTabsSharedPreferences.edit().putString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_6_NAME, tab6CurrentName).apply();
                        binding.tab6NameSummaryTextViewCustomizeMainPageTabsFragment.setText(tab6CurrentName);
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                        Utils.hideKeyboard(mActivity);
                    })
                    .setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                        Utils.hideKeyboard(mActivity);
                    })
                    .show();
        });

        binding.tab6NameAddImageViewCustomizeMainPageTabsFragment.setOnClickListener(view -> selectName(5));

        binding.showMultiredditsSwitchMaterialCustomizeMainPageTabsFragment.setChecked(mainActivityTabsSharedPreferences.getBoolean((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_SHOW_MULTIREDDITS, false));
        binding.showMultiredditsSwitchMaterialCustomizeMainPageTabsFragment.setOnCheckedChangeListener((compoundButton, b) -> {
            mainActivityTabsSharedPreferences.edit().putBoolean((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_SHOW_MULTIREDDITS, b).apply();
            mSettingsChanged = true;
            updateRestartButtonVisibility();
        });
        binding.showMultiredditsLinearLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            binding.showMultiredditsSwitchMaterialCustomizeMainPageTabsFragment.performClick();
        });

        binding.showFavoriteMultiredditsSwitchMaterialCustomizeMainPageTabsFragment.setChecked(mainActivityTabsSharedPreferences.getBoolean((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_SHOW_FAVORITE_MULTIREDDITS, false));
        binding.showFavoriteMultiredditsSwitchMaterialCustomizeMainPageTabsFragment.setOnCheckedChangeListener((compoundButton, b) -> {
            mainActivityTabsSharedPreferences.edit().putBoolean((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_SHOW_FAVORITE_MULTIREDDITS, b).apply();
            mSettingsChanged = true;
            updateRestartButtonVisibility();
        });
        binding.showFavoriteMultiredditsLinearLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            binding.showFavoriteMultiredditsSwitchMaterialCustomizeMainPageTabsFragment.performClick();
        });

        binding.showSubscribedSubredditsSwitchMaterialCustomizeMainPageTabsFragment.setChecked(mainActivityTabsSharedPreferences.getBoolean((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_SHOW_SUBSCRIBED_SUBREDDITS, false));
        binding.showSubscribedSubredditsSwitchMaterialCustomizeMainPageTabsFragment.setOnCheckedChangeListener((compoundButton, b) -> {
            mainActivityTabsSharedPreferences.edit().putBoolean((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_SHOW_SUBSCRIBED_SUBREDDITS, b).apply();
            mSettingsChanged = true;
            updateRestartButtonVisibility();
        });
        binding.showSubscribedSubredditsLinearLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            binding.showSubscribedSubredditsSwitchMaterialCustomizeMainPageTabsFragment.performClick();
        });

        binding.showFavoriteSubscribedSubredditsSwitchMaterialCustomizeMainPageTabsFragment.setChecked(mainActivityTabsSharedPreferences.getBoolean((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_SHOW_FAVORITE_SUBSCRIBED_SUBREDDITS, false));
        binding.showFavoriteSubscribedSubredditsSwitchMaterialCustomizeMainPageTabsFragment.setOnCheckedChangeListener((compoundButton, b) -> {
            mainActivityTabsSharedPreferences.edit().putBoolean((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_SHOW_FAVORITE_SUBSCRIBED_SUBREDDITS, b).apply();
            mSettingsChanged = true;
            updateRestartButtonVisibility();
        });
        binding.showFavoriteSubscribedSubredditsLinearLayoutCustomizeMainPageTabsFragment.setOnClickListener(view -> {
            binding.showFavoriteSubscribedSubredditsSwitchMaterialCustomizeMainPageTabsFragment.performClick();
        });

        updateTabViewsVisibility(tabCount);

        return binding.getRoot();
    }

    private void updateTabViewsVisibility(int currentTabCount) {
        // Tab 1
        binding.tab1GroupSummaryCustomizeMainPageTabsFragment.setVisibility(View.VISIBLE); // Always visible
        binding.tab1TitleLinearLayoutCustomizeMainPageTabsFragment.setVisibility(View.VISIBLE);
        binding.tab1TypeLinearLayoutCustomizeMainPageTabsFragment.setVisibility(View.VISIBLE);
        binding.tab1NameConstraintLayoutCustomizeMainPageTabsFragment.setVisibility((tab1CurrentPostType == SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT || tab1CurrentPostType == SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT || tab1CurrentPostType == SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_USER) ? View.VISIBLE : View.GONE);
        // divider_1_customize_main_page_tabs_fragment seems to be static, after "show tab names" - not controlled by tabCount

        // Tab 2
        binding.divider2CustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 2 ? View.VISIBLE : View.GONE);
        binding.tab2GroupSummaryCustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 2 ? View.VISIBLE : View.GONE);
        binding.tab2TitleLinearLayoutCustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 2 ? View.VISIBLE : View.GONE);
        binding.tab2TypeLinearLayoutCustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 2 ? View.VISIBLE : View.GONE);
        binding.tab2NameConstraintLayoutCustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 2 && (tab2CurrentPostType == SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT || tab2CurrentPostType == SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT || tab2CurrentPostType == SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_USER) ? View.VISIBLE : View.GONE);

        // Tab 3
        binding.divider3CustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 3 ? View.VISIBLE : View.GONE);
        binding.tab3GroupSummaryCustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 3 ? View.VISIBLE : View.GONE);
        binding.tab3TitleLinearLayoutCustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 3 ? View.VISIBLE : View.GONE);
        binding.tab3TypeLinearLayoutCustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 3 ? View.VISIBLE : View.GONE);
        binding.tab3NameConstraintLayoutCustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 3 && (tab3CurrentPostType == SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT || tab3CurrentPostType == SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT || tab3CurrentPostType == SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_USER) ? View.VISIBLE : View.GONE);

        // Tab 4
        binding.divider4CustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 4 ? View.VISIBLE : View.GONE);
        binding.tab4GroupSummaryCustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 4 ? View.VISIBLE : View.GONE);
        binding.tab4TitleLinearLayoutCustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 4 ? View.VISIBLE : View.GONE);
        binding.tab4TypeLinearLayoutCustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 4 ? View.VISIBLE : View.GONE);
        binding.tab4NameConstraintLayoutCustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 4 && (tab4CurrentPostType == SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT || tab4CurrentPostType == SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT || tab4CurrentPostType == SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_USER) ? View.VISIBLE : View.GONE);

        // Tab 5
        binding.divider5CustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 5 ? View.VISIBLE : View.GONE);
        binding.tab5GroupSummaryCustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 5 ? View.VISIBLE : View.GONE);
        binding.tab5TitleLinearLayoutCustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 5 ? View.VISIBLE : View.GONE);
        binding.tab5TypeLinearLayoutCustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 5 ? View.VISIBLE : View.GONE);
        binding.tab5NameConstraintLayoutCustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 5 && (tab5CurrentPostType == SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT || tab5CurrentPostType == SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT || tab5CurrentPostType == SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_USER) ? View.VISIBLE : View.GONE);

        // Tab 6
        // Assuming there's a divider6 after tab 5 if tab 6 is shown.
        // If it's missing in XML, this line will cause a new error.
        // binding.divider6CustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 6 ? View.VISIBLE : View.GONE);
        binding.tab6GroupSummaryCustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 6 ? View.VISIBLE : View.GONE);
        binding.tab6TitleLinearLayoutCustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 6 ? View.VISIBLE : View.GONE);
        binding.tab6TypeLinearLayoutCustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 6 ? View.VISIBLE : View.GONE);
        binding.tab6NameConstraintLayoutCustomizeMainPageTabsFragment.setVisibility(currentTabCount >= 6 && (tab6CurrentPostType == SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT || tab6CurrentPostType == SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT || tab6CurrentPostType == SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_USER) ? View.VISIBLE : View.GONE);

        // "More Tabs" sections - these titles/info texts refer to tabs 4, 5, 6
        binding.moreTabsGroupSummaryCustomizeMainPageTabsFragment.setVisibility(currentTabCount > 3 ? View.VISIBLE : View.GONE);
        binding.moreTabsInfoTextViewCustomizeMainPageTabsFragment.setVisibility(currentTabCount > 3 ? View.VISIBLE : View.GONE);
    }

    private void updateRestartButtonVisibility() {
        if (restartButton != null) {
            restartButton.setVisibility(mSettingsChanged ? View.VISIBLE : View.GONE);
        }
    }

    private void applyCustomTheme() {
        int primaryTextColor = mActivity.customThemeWrapper.getPrimaryTextColor();
        int secondaryTextColor = mActivity.customThemeWrapper.getSecondaryTextColor();
        int colorAccent = mActivity.customThemeWrapper.getColorAccent();
        int primaryIconColor = mActivity.customThemeWrapper.getPrimaryIconColor();
        binding.moreTabsInfoTextViewCustomizeMainPageTabsFragment.setTextColor(secondaryTextColor);
        Drawable infoDrawable = Utils.getTintedDrawable(mActivity, R.drawable.ic_info_preference_day_night_24dp, secondaryTextColor);
        binding.moreTabsInfoTextViewCustomizeMainPageTabsFragment.setCompoundDrawablesWithIntrinsicBounds(infoDrawable, null, null, null);
        binding.tabCountTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.tabCountTextViewCustomizeMainPageTabsFragment.setTextColor(secondaryTextColor);
        binding.showTabNamesTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.tab1GroupSummaryCustomizeMainPageTabsFragment.setTextColor(colorAccent);
        binding.tab1TitleTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.tab1TitleSummaryTextViewCustomizeMainPageTabsFragment.setTextColor(secondaryTextColor);
        binding.tab1TypeTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.tab1TypeSummaryTextViewCustomizeMainPageTabsFragment.setTextColor(secondaryTextColor);
        binding.tab1NameTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.tab1NameSummaryTextViewCustomizeMainPageTabsFragment.setTextColor(secondaryTextColor);
        binding.tab1NameAddImageViewCustomizeMainPageTabsFragment.setColorFilter(primaryIconColor, android.graphics.PorterDuff.Mode.SRC_IN);
        binding.tab2GroupSummaryCustomizeMainPageTabsFragment.setTextColor(colorAccent);
        binding.tab2TitleTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.tab2TitleSummaryTextViewCustomizeMainPageTabsFragment.setTextColor(secondaryTextColor);
        binding.tab2TypeTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.tab2TypeSummaryTextViewCustomizeMainPageTabsFragment.setTextColor(secondaryTextColor);
        binding.tab2NameTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.tab2NameSummaryTextViewCustomizeMainPageTabsFragment.setTextColor(secondaryTextColor);
        binding.tab2NameAddImageViewCustomizeMainPageTabsFragment.setColorFilter(primaryIconColor, android.graphics.PorterDuff.Mode.SRC_IN);
        binding.tab3GroupSummaryCustomizeMainPageTabsFragment.setTextColor(colorAccent);
        binding.tab3TitleTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.tab3TitleSummaryTextViewCustomizeMainPageTabsFragment.setTextColor(secondaryTextColor);
        binding.tab3TypeTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.tab3TypeSummaryTextViewCustomizeMainPageTabsFragment.setTextColor(secondaryTextColor);
        binding.tab3NameTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.tab3NameSummaryTextViewCustomizeMainPageTabsFragment.setTextColor(secondaryTextColor);
        binding.tab3NameAddImageViewCustomizeMainPageTabsFragment.setColorFilter(primaryIconColor, android.graphics.PorterDuff.Mode.SRC_IN);
        binding.tab4GroupSummaryCustomizeMainPageTabsFragment.setTextColor(colorAccent);
        binding.moreTabsGroupSummaryCustomizeMainPageTabsFragment.setTextColor(colorAccent);
        binding.tab4TitleTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.tab4TitleSummaryTextViewCustomizeMainPageTabsFragment.setTextColor(secondaryTextColor);
        binding.tab4TypeTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.tab4TypeSummaryTextViewCustomizeMainPageTabsFragment.setTextColor(secondaryTextColor);
        binding.tab4NameTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.tab4NameSummaryTextViewCustomizeMainPageTabsFragment.setTextColor(secondaryTextColor);
        binding.tab4NameAddImageViewCustomizeMainPageTabsFragment.setColorFilter(primaryIconColor, android.graphics.PorterDuff.Mode.SRC_IN);
        binding.tab5GroupSummaryCustomizeMainPageTabsFragment.setTextColor(colorAccent);
        binding.tab5TitleTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.tab5TitleSummaryTextViewCustomizeMainPageTabsFragment.setTextColor(secondaryTextColor);
        binding.tab5TypeTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.tab5TypeSummaryTextViewCustomizeMainPageTabsFragment.setTextColor(secondaryTextColor);
        binding.tab5NameTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.tab5NameSummaryTextViewCustomizeMainPageTabsFragment.setTextColor(secondaryTextColor);
        binding.tab5NameAddImageViewCustomizeMainPageTabsFragment.setColorFilter(primaryIconColor, android.graphics.PorterDuff.Mode.SRC_IN);
        binding.tab6GroupSummaryCustomizeMainPageTabsFragment.setTextColor(colorAccent);
        binding.tab6TitleTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.tab6TitleSummaryTextViewCustomizeMainPageTabsFragment.setTextColor(secondaryTextColor);
        binding.tab6TypeTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.tab6TypeSummaryTextViewCustomizeMainPageTabsFragment.setTextColor(secondaryTextColor);
        binding.moreTabsInfoTextViewCustomizeMainPageTabsFragment.setTextColor(secondaryTextColor);
        binding.moreTabsInfoTextViewCustomizeMainPageTabsFragment.setCompoundDrawablesWithIntrinsicBounds(infoDrawable, null, null, null);
        binding.showFavoriteMultiredditsTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.showMultiredditsTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.showSubscribedSubredditsTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
        binding.showFavoriteSubscribedSubredditsTitleTextViewCustomizeMainPageTabsFragment.setTextColor(primaryTextColor);
    }

    private void applyTab1NameView(ConstraintLayout constraintLayout, TextView titleTextView, int postType) {
        switch (postType) {
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT:
                constraintLayout.setVisibility(View.VISIBLE);
                titleTextView.setText(R.string.settings_tab_subreddit_name);
                break;
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT:
                constraintLayout.setVisibility(View.VISIBLE);
                titleTextView.setText(R.string.settings_tab_multi_reddit_name);
                break;
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_USER:
                constraintLayout.setVisibility(View.VISIBLE);
                titleTextView.setText(R.string.settings_tab_username);
                break;
            default:
                constraintLayout.setVisibility(View.GONE);
        }
    }

    private void applyTab2NameView(ConstraintLayout linearLayout, TextView titleTextView, int postType) {
        switch (postType) {
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT:
                linearLayout.setVisibility(View.VISIBLE);
                titleTextView.setText(R.string.settings_tab_subreddit_name);
                break;
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT:
                linearLayout.setVisibility(View.VISIBLE);
                titleTextView.setText(R.string.settings_tab_multi_reddit_name);
                break;
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_USER:
                linearLayout.setVisibility(View.VISIBLE);
                titleTextView.setText(R.string.settings_tab_username);
                break;
            default:
                linearLayout.setVisibility(View.GONE);
        }
    }

    private void applyTab3NameView(ConstraintLayout constraintLayout, TextView titleTextView, int postType) {
        switch (postType) {
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT:
                constraintLayout.setVisibility(View.VISIBLE);
                titleTextView.setText(R.string.settings_tab_subreddit_name);
                break;
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT:
                constraintLayout.setVisibility(View.VISIBLE);
                titleTextView.setText(R.string.settings_tab_multi_reddit_name);
                break;
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_USER:
                constraintLayout.setVisibility(View.VISIBLE);
                titleTextView.setText(R.string.settings_tab_username);
                break;
            default:
                constraintLayout.setVisibility(View.GONE);
        }
    }

    private void applyTab4NameView(ConstraintLayout constraintLayout, TextView titleTextView, int postType) {
        switch (postType) {
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT:
                constraintLayout.setVisibility(View.VISIBLE);
                titleTextView.setText(R.string.settings_tab_subreddit_name);
                break;
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT:
                constraintLayout.setVisibility(View.VISIBLE);
                titleTextView.setText(R.string.settings_tab_multi_reddit_name);
                break;
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_USER:
                constraintLayout.setVisibility(View.VISIBLE);
                titleTextView.setText(R.string.settings_tab_username);
                break;
            default:
                constraintLayout.setVisibility(View.GONE);
        }
    }

    private void applyTab5NameView(ConstraintLayout constraintLayout, TextView titleTextView, int postType) {
        switch (postType) {
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT:
                constraintLayout.setVisibility(View.VISIBLE);
                titleTextView.setText(R.string.settings_tab_subreddit_name);
                break;
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT:
                constraintLayout.setVisibility(View.VISIBLE);
                titleTextView.setText(R.string.settings_tab_multi_reddit_name);
                break;
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_USER:
                constraintLayout.setVisibility(View.VISIBLE);
                titleTextView.setText(R.string.settings_tab_username);
                break;
            default:
                constraintLayout.setVisibility(View.GONE);
        }
    }

    private void applyTab6NameView(ConstraintLayout constraintLayout, TextView titleTextView, int postType) {
        switch (postType) {
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT:
                constraintLayout.setVisibility(View.VISIBLE);
                titleTextView.setText(R.string.settings_tab_subreddit_name);
                break;
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT:
                constraintLayout.setVisibility(View.VISIBLE);
                titleTextView.setText(R.string.settings_tab_multi_reddit_name);
                break;
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_USER:
                constraintLayout.setVisibility(View.VISIBLE);
                titleTextView.setText(R.string.settings_tab_username);
                break;
            default:
                constraintLayout.setVisibility(View.GONE);
        }
    }

    private void selectName(int tab) {
        switch (tab) {
            case 0:
                switch (tab1CurrentPostType) {
                    case 3: {
                        Intent intent = new Intent(mActivity, SubscribedThingListingActivity.class);
                        intent.putExtra(SubscribedThingListingActivity.EXTRA_THING_SELECTION_MODE, true);
                        intent.putExtra(SubscribedThingListingActivity.EXTRA_THING_SELECTION_TYPE,
                                SubscribedThingListingActivity.EXTRA_THING_SELECTION_TYPE_SUBREDDIT);
                        startActivityForResult(intent, tab);
                        break;
                    }
                    case 4: {
                        Intent intent = new Intent(mActivity, SubscribedThingListingActivity.class);
                        intent.putExtra(SubscribedThingListingActivity.EXTRA_THING_SELECTION_MODE, true);
                        intent.putExtra(SubscribedThingListingActivity.EXTRA_THING_SELECTION_TYPE,
                                SubscribedThingListingActivity.EXTRA_THING_SELECTION_TYPE_MULTIREDDIT);
                        startActivityForResult(intent, tab);
                        break;
                    }
                    case 5: {
                        Intent intent = new Intent(mActivity, SearchActivity.class);
                        intent.putExtra(SearchActivity.EXTRA_SEARCH_ONLY_USERS, true);
                        startActivityForResult(intent, tab);
                        break;
                    }
                }
                break;
            case 1:
                switch (tab2CurrentPostType) {
                    case 3: {
                        Intent intent = new Intent(mActivity, SubscribedThingListingActivity.class);
                        intent.putExtra(SubscribedThingListingActivity.EXTRA_THING_SELECTION_MODE, true);
                        intent.putExtra(SubscribedThingListingActivity.EXTRA_THING_SELECTION_TYPE,
                                SubscribedThingListingActivity.EXTRA_THING_SELECTION_TYPE_SUBREDDIT);
                        startActivityForResult(intent, tab);
                        break;
                    }
                    case 4: {
                        Intent intent = new Intent(mActivity, SubscribedThingListingActivity.class);
                        intent.putExtra(SubscribedThingListingActivity.EXTRA_THING_SELECTION_MODE, true);
                        intent.putExtra(SubscribedThingListingActivity.EXTRA_THING_SELECTION_TYPE,
                                SubscribedThingListingActivity.EXTRA_THING_SELECTION_TYPE_MULTIREDDIT);
                        startActivityForResult(intent, tab);
                        break;
                    }
                    case 5: {
                        Intent intent = new Intent(mActivity, SearchActivity.class);
                        intent.putExtra(SearchActivity.EXTRA_SEARCH_ONLY_USERS, true);
                        startActivityForResult(intent, tab);
                        break;
                    }
                }
                break;
            case 2:
                switch (tab3CurrentPostType) {
                    case 3: {
                        Intent intent = new Intent(mActivity, SubscribedThingListingActivity.class);
                        intent.putExtra(SubscribedThingListingActivity.EXTRA_THING_SELECTION_MODE, true);
                        intent.putExtra(SubscribedThingListingActivity.EXTRA_THING_SELECTION_TYPE,
                                SubscribedThingListingActivity.EXTRA_THING_SELECTION_TYPE_SUBREDDIT);
                        startActivityForResult(intent, tab);
                        break;
                    }
                    case 4: {
                        Intent intent = new Intent(mActivity, SubscribedThingListingActivity.class);
                        intent.putExtra(SubscribedThingListingActivity.EXTRA_THING_SELECTION_MODE, true);
                        intent.putExtra(SubscribedThingListingActivity.EXTRA_THING_SELECTION_TYPE,
                                SubscribedThingListingActivity.EXTRA_THING_SELECTION_TYPE_MULTIREDDIT);
                        startActivityForResult(intent, tab);
                        break;
                    }
                    case 5: {
                        Intent intent = new Intent(mActivity, SearchActivity.class);
                        intent.putExtra(SearchActivity.EXTRA_SEARCH_ONLY_USERS, true);
                        startActivityForResult(intent, tab);
                        break;
                    }
                }
                break;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && data != null) {
            int thingType = data.getIntExtra(SelectThingReturnKey.RETURN_EXTRA_THING_TYPE, SelectThingReturnKey.THING_TYPE.SUBREDDIT);
            switch (requestCode) {
                case 0:
                    if (thingType == SelectThingReturnKey.THING_TYPE.SUBREDDIT) {
                        tab1CurrentName = data.getStringExtra(SelectThingReturnKey.RETURN_EXTRA_SUBREDDIT_OR_USER_NAME);
                        binding.tab1NameSummaryTextViewCustomizeMainPageTabsFragment.setText(tab1CurrentName);
                        mainActivityTabsSharedPreferences.edit().putString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_1_NAME, tab1CurrentName).apply();
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                    } else if (thingType == SelectThingReturnKey.THING_TYPE.MULTIREDDIT) {
                        MultiReddit multireddit = data.getParcelableExtra(SelectThingReturnKey.RETRUN_EXTRA_MULTIREDDIT);
                        if (multireddit != null) {
                            tab1CurrentName = multireddit.getPath();
                            binding.tab1NameSummaryTextViewCustomizeMainPageTabsFragment.setText(tab1CurrentName);
                            mainActivityTabsSharedPreferences.edit().putString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_1_NAME, tab1CurrentName).apply();
                            mSettingsChanged = true;
                            updateRestartButtonVisibility();
                        }
                    } else if (thingType == SelectThingReturnKey.THING_TYPE.USER) {
                        tab1CurrentName = data.getStringExtra(SelectThingReturnKey.RETURN_EXTRA_SUBREDDIT_OR_USER_NAME);
                        binding.tab1NameSummaryTextViewCustomizeMainPageTabsFragment.setText(tab1CurrentName);
                        mainActivityTabsSharedPreferences.edit().putString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_1_NAME, tab1CurrentName).apply();
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                    }
                    break;
                case 1:
                    if (thingType == SelectThingReturnKey.THING_TYPE.SUBREDDIT) {
                        tab2CurrentName = data.getStringExtra(SelectThingReturnKey.RETURN_EXTRA_SUBREDDIT_OR_USER_NAME);
                        binding.tab2NameSummaryTextViewCustomizeMainPageTabsFragment.setText(tab2CurrentName);
                        mainActivityTabsSharedPreferences.edit().putString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_2_NAME, tab2CurrentName).apply();
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                    } else if (thingType == SelectThingReturnKey.THING_TYPE.MULTIREDDIT) {
                        MultiReddit multireddit = data.getParcelableExtra(SelectThingReturnKey.RETRUN_EXTRA_MULTIREDDIT);
                        if (multireddit != null) {
                            tab2CurrentName = multireddit.getPath();
                            binding.tab2NameSummaryTextViewCustomizeMainPageTabsFragment.setText(tab2CurrentName);
                            mainActivityTabsSharedPreferences.edit().putString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_2_NAME, tab2CurrentName).apply();
                            mSettingsChanged = true;
                            updateRestartButtonVisibility();
                        }
                    } else if (thingType == SelectThingReturnKey.THING_TYPE.USER) {
                        tab2CurrentName = data.getStringExtra(SelectThingReturnKey.RETURN_EXTRA_SUBREDDIT_OR_USER_NAME);
                        binding.tab2NameSummaryTextViewCustomizeMainPageTabsFragment.setText(tab2CurrentName);
                        mainActivityTabsSharedPreferences.edit().putString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_2_NAME, tab2CurrentName).apply();
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                    }
                    break;
                case 2:
                    if (thingType == SelectThingReturnKey.THING_TYPE.SUBREDDIT) {
                        tab3CurrentName = data.getStringExtra(SelectThingReturnKey.RETURN_EXTRA_SUBREDDIT_OR_USER_NAME);
                        binding.tab3NameSummaryTextViewCustomizeMainPageTabsFragment.setText(tab3CurrentName);
                        mainActivityTabsSharedPreferences.edit().putString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_3_NAME, tab3CurrentName).apply();
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                    } else if (thingType == SelectThingReturnKey.THING_TYPE.MULTIREDDIT) {
                        MultiReddit multireddit = data.getParcelableExtra(SelectThingReturnKey.RETRUN_EXTRA_MULTIREDDIT);
                        if (multireddit != null) {
                            tab3CurrentName = multireddit.getPath();
                            binding.tab3NameSummaryTextViewCustomizeMainPageTabsFragment.setText(tab3CurrentName);
                            mainActivityTabsSharedPreferences.edit().putString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_3_NAME, tab3CurrentName).apply();
                            mSettingsChanged = true;
                            updateRestartButtonVisibility();
                        }
                    } else if (thingType == SelectThingReturnKey.THING_TYPE.USER) {
                        tab3CurrentName = data.getStringExtra(SelectThingReturnKey.RETURN_EXTRA_SUBREDDIT_OR_USER_NAME);
                        binding.tab3NameSummaryTextViewCustomizeMainPageTabsFragment.setText(tab3CurrentName);
                        mainActivityTabsSharedPreferences.edit().putString((mActivity.accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : mActivity.accountName) + SharedPreferencesUtils.MAIN_PAGE_TAB_3_NAME, tab3CurrentName).apply();
                        mSettingsChanged = true;
                        updateRestartButtonVisibility();
                    }
                    break;
            }
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mActivity = (SettingsActivity) context;
    }
}
