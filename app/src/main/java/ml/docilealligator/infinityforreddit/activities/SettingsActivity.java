package ml.docilealligator.infinityforreddit.activities;

import static com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS;
import static com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL;
import static com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Named;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.account.AccountScopedKeys;
import ml.docilealligator.infinityforreddit.bottomsheetfragments.AccountChooserBottomSheetFragment;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ActivitySettingsBinding;
import ml.docilealligator.infinityforreddit.events.RecreateActivityEvent;
import ml.docilealligator.infinityforreddit.settings.APIKeysPreferenceFragment;
import ml.docilealligator.infinityforreddit.settings.AboutPreferenceFragment;
import ml.docilealligator.infinityforreddit.settings.DebugPreferenceFragment;
import ml.docilealligator.infinityforreddit.settings.FontPreferenceFragment;
import ml.docilealligator.infinityforreddit.settings.GesturesAndButtonsPreferenceFragment;
import ml.docilealligator.infinityforreddit.settings.GlobalSettingsManagementPreferenceFragment;
import ml.docilealligator.infinityforreddit.settings.InterfacePreferenceFragment;
import ml.docilealligator.infinityforreddit.settings.MainPreferenceFragment;
import ml.docilealligator.infinityforreddit.settings.PostPreferenceFragment;
import ml.docilealligator.infinityforreddit.settings.SettingsScreenArgs;
import ml.docilealligator.infinityforreddit.settings.SettingsSearchFragment;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesLiveDataKt;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class SettingsActivity extends BaseActivity implements
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
        AccountChooserBottomSheetFragment.AccountChooserListener {

    private static final String TITLE_STATE = "TS";
    /** The screens open above the settings root, by class name. See {@link #saveResumeState}. */
    private static final String RESUME_FRAGMENTS_STATE = "RFS";
    /** Their toolbar titles, in the same order. */
    private static final String RESUME_FRAGMENT_TITLES_STATE = "RFTS";

    private ActivitySettingsBinding binding;

    /**
     * The screens stacked above the settings root, bottom first, and the titles they were opened
     * with -- one entry per back stack entry, kept in step with it by the listener in
     * {@code onCreate}.
     *
     * The back stack itself cannot answer this: an entry knows its name, which is null here, and
     * not which fragment it added. Recording it is what lets "Resume where I left off" come back to
     * API Keys rather than to the settings root, since the snapshot stores activities and this
     * screen is one activity however deep the user is in it.
     */
    private final ArrayList<String> resumeFragments = new ArrayList<>();
    private final ArrayList<String> resumeFragmentTitles = new ArrayList<>();

    /**
     * Whether a recorded chain of screens may still be reopened. False once the framework has
     * restored the back stack itself -- a rotation -- where replaying it would stack the same
     * screens on top of the ones already there.
     */
    private boolean resumeFragmentsRestorable;

    /**
     * Cut the record back to the screens actually on the back stack.
     *
     * <p>Posted rather than run from the back stack listener, because the count read inside that
     * listener is the one from before the change: measured on device, pushing a screen reports the
     * depth without it and popping one reports the depth with it still there. Trusting it left the
     * record one entry long, so backing out of API Keys to the settings root still resumed into
     * API Keys. By the time a posted message runs, the stack is settled and the count is the truth.
     */
    private final Runnable syncResumeFragments = () -> {
        int depth = getSupportFragmentManager().getBackStackEntryCount();
        while (resumeFragments.size() > depth) {
            resumeFragments.remove(resumeFragments.size() - 1);
            resumeFragmentTitles.remove(resumeFragmentTitles.size() - 1);
        }
    };

    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;
    @Inject
    @Named("navigation_drawer")
    SharedPreferences mNavigationDrawerSharedPreferences;
    @Inject
    @Named("post_details")
    SharedPreferences mPostDetailsSharedPreferences;
    @Inject
    CustomThemeWrapper mCustomThemeWrapper;

    /**
     * The account-scoped preferences behind a settings screen, or null for a screen whose file is
     * not per-account. Screens name their file through
     * {@code PreferenceManager.setSharedPreferencesName}; a null name, or the default name androidx
     * starts out with, means the default file.
     *
     * Preference screens otherwise reach their file by name, which bypasses the scoping entirely and
     * would write an edit somewhere nothing reads it.
     */
    @Nullable
    public SharedPreferences accountScopedPreferencesFor(@Nullable String sharedPreferencesName) {
        // The default file answers to two names: androidx starts every PreferenceManager on it by
        // name rather than leaving the name null, so matching only null missed every screen that
        // lives there — they read and wrote the unscoped key while the app read the scoped one.
        //
        // Built rather than taken from SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE, which names
        // the legacy file only the API keys screen uses. androidx keeps this private, so the
        // convention is spelled out here; it is the same one the platform has always used.
        if (sharedPreferencesName == null
                || sharedPreferencesName.equals(getPackageName() + "_preferences")) {
            return mSharedPreferences;
        }
        if (sharedPreferencesName.equals(SharedPreferencesUtils.NAVIGATION_DRAWER_SHARED_PREFERENCES_FILE)) {
            return mNavigationDrawerSharedPreferences;
        }
        if (sharedPreferencesName.equals(SharedPreferencesUtils.POST_DETAILS_SHARED_PREFERENCES_FILE)) {
            return mPostDetailsSharedPreferences;
        }
        // Fail closed. Returning null here would say "not per-account" and hand the screen back to
        // androidx's by-name lookup, which is the very thing this method exists to prevent; a
        // screen added on sort_type, post_layout or bottom_app_bar would then write unscoped keys
        // with nothing to show for it. Whoever adds one has to inject its instance above.
        if (AccountScopedKeys.isWholeFileScoped(sharedPreferencesName)) {
            throw new IllegalStateException(
                    "No account-scoped SharedPreferences injected for " + sharedPreferencesName);
        }
        return null;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        setImmersiveModeNotApplicableBelowAndroid16();

        super.onCreate(savedInstanceState);

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        EventBus.getDefault().register(this);

        applyCustomTheme();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(binding.appbarLayoutSettingsActivity);
            }

            if (isImmersiveInterfaceRespectForcedEdgeToEdge()) {
                ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                    @NonNull
                    @Override
                    public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                        Insets allInsets = Utils.getInsets(insets, false, isForcedImmersiveInterface());

                        setMargins(binding.toolbarSettingsActivity,
                                allInsets.left,
                                allInsets.top,
                                allInsets.right,
                                BaseActivity.IGNORE_MARGIN);

                        return insets;
                    }
                });
            }
        }

        setSupportActionBar(binding.toolbarSettingsActivity);

        resumeFragmentsRestorable = savedInstanceState == null;
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.frame_layout_settings_activity, new MainPreferenceFragment())
                .commit();
        } else {
            setTitle(savedInstanceState.getCharSequence(TITLE_STATE));
            // The back stack comes back on its own; the record of what is in it does not, and
            // without this a screen rotated in Settings would resume to the settings root.
            ArrayList<String> fragments = savedInstanceState.getStringArrayList(RESUME_FRAGMENTS_STATE);
            ArrayList<String> titles = savedInstanceState.getStringArrayList(RESUME_FRAGMENT_TITLES_STATE);
            if (fragments != null && titles != null && fragments.size() == titles.size()) {
                resumeFragments.addAll(fragments);
                resumeFragmentTitles.addAll(titles);
            }
        }

        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            invalidateOptionsMenu();
            // Popped screens leave the record behind, and this is the only notice of a pop there
            // is. Truncating rather than removing one entry: popBackStack can take several at once.
            mHandler.removeCallbacks(syncResumeFragments);
            mHandler.post(syncResumeFragments);
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                setTitle(R.string.settings_activity_label);
                setToolbarScrollLocked(mSharedPreferences.getBoolean(SharedPreferencesUtils.LOCK_TOOLBAR, false));
                return;
            }

            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.frame_layout_settings_activity);

            if (fragment instanceof SettingsSearchFragment) {
                setTitle(R.string.settings_search_settings);
                setToolbarScrollLocked(true);
            } else {
                setToolbarScrollLocked(mSharedPreferences.getBoolean(SharedPreferencesUtils.LOCK_TOOLBAR, false));
                CharSequence screenTitle = SettingsScreenArgs.screenTitle(fragment);
                if (screenTitle != null) {
                    // The screen recorded its own title when it was opened; trust that over
                    // guessing from the fragment type, which cannot represent a literal title.
                    setTitle(screenTitle);
                } else if (fragment instanceof AboutPreferenceFragment) {
                    setTitle(R.string.settings_about_master_title);
                } else if (fragment instanceof InterfacePreferenceFragment) {
                    setTitle(R.string.settings_interface_title);
                } else if (fragment instanceof FontPreferenceFragment) {
                    setTitle(R.string.settings_font_title);
                } else if (fragment instanceof GesturesAndButtonsPreferenceFragment) {
                    setTitle(R.string.settings_gestures_and_buttons_title);
                } else if (fragment instanceof PostPreferenceFragment) {
                    setTitle(R.string.settings_category_post_title);
                } else if (fragment instanceof GlobalSettingsManagementPreferenceFragment) {
                    setTitle(R.string.settings_global_settings_management_title);
                } else if (fragment instanceof APIKeysPreferenceFragment) {
                    setTitle(R.string.settings_api_keys_title);
                } else if (fragment instanceof DebugPreferenceFragment) {
                    setTitle(R.string.settings_debug_title);
                } else if (fragment instanceof MainPreferenceFragment) {
                    setTitle(R.string.settings_activity_label);
                }
            }
        });

        SharedPreferencesLiveDataKt.booleanLiveData(mSharedPreferences, SharedPreferencesUtils.LOCK_TOOLBAR, false).observe(this, lock -> {
            Fragment current = getSupportFragmentManager().findFragmentById(R.id.frame_layout_settings_activity);
            setToolbarScrollLocked(lock || current instanceof SettingsSearchFragment);
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
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutSettingsActivity,
                binding.collapsingToolbarLayoutSettingsActivity, binding.toolbarSettingsActivity);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_settings, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.frame_layout_settings_activity);
        menu.findItem(R.id.action_search_settings).setVisible(!(current instanceof SettingsSearchFragment));
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }

        if (item.getItemId() == R.id.action_search_settings) {
            Fragment current = getSupportFragmentManager()
                    .findFragmentById(R.id.frame_layout_settings_activity);
            if (!(current instanceof SettingsSearchFragment)) {
                SettingsSearchFragment searchFragment = new SettingsSearchFragment();
                recordResumeNavigation(searchFragment, getString(R.string.settings_search_settings));
                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left,
                                R.anim.enter_from_left, R.anim.exit_to_right)
                        .replace(R.id.frame_layout_settings_activity, searchFragment)
                        .addToBackStack(null)
                        .commit();
                binding.appbarLayoutSettingsActivity.setExpanded(true);
                setToolbarScrollLocked(true);
                setTitle(R.string.settings_search_settings);
            }
            return true;
        }

        return false;
    }

    public void navigateToSettingsFragment(Fragment fragment, CharSequence title) {
        navigateToSettingsFragment(fragment, title, true);
    }

    /**
     * @param animate false when the screen is being put back rather than opened: a resume that
     *                replays two or three screens would otherwise slide each of them in over the
     *                last, which is a launch animating the user's history at them.
     */
    private void navigateToSettingsFragment(Fragment fragment, CharSequence title, boolean animate) {
        Bundle args = fragment.getArguments();
        if (args == null) {
            args = new Bundle();
            fragment.setArguments(args);
        }
        SettingsScreenArgs.putScreenTitle(args, title);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (animate) {
            transaction.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left,
                    R.anim.enter_from_left, R.anim.exit_to_right);
        }
        recordResumeNavigation(fragment, title);
        transaction
                .replace(R.id.frame_layout_settings_activity, fragment)
                .addToBackStack(null)
                .commit();
        binding.appbarLayoutSettingsActivity.setExpanded(true);
        setToolbarScrollLocked(mSharedPreferences.getBoolean(SharedPreferencesUtils.LOCK_TOOLBAR, false));
        setTitle(title);
    }

    /**
     * Note that {@code fragment} is being pushed onto the back stack, so a resume can put it back.
     *
     * <p>Called before the transaction is committed, so the record and the back stack grow in the
     * same order the listener in {@code onCreate} shrinks them.
     */
    private void recordResumeNavigation(Fragment fragment, @Nullable CharSequence title) {
        resumeFragments.add(fragment.getClass().getName());
        resumeFragmentTitles.add(title == null ? "" : title.toString());
    }

    private void setToolbarScrollLocked(boolean locked) {
        AppBarLayout.LayoutParams p = (AppBarLayout.LayoutParams)
                binding.collapsingToolbarLayoutSettingsActivity.getLayoutParams();
        p.setScrollFlags(locked ? SCROLL_FLAG_NO_SCROLL : SCROLL_FLAG_SCROLL | SCROLL_FLAG_ENTER_ALWAYS);
        binding.collapsingToolbarLayoutSettingsActivity.setLayoutParams(p);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putCharSequence(TITLE_STATE, getTitle());
        outState.putStringArrayList(RESUME_FRAGMENTS_STATE, new ArrayList<>(resumeFragments));
        outState.putStringArrayList(RESUME_FRAGMENT_TITLES_STATE, new ArrayList<>(resumeFragmentTitles));
    }

    /**
     * Record which settings screen is open, not just that Settings is.
     *
     * <p>The snapshot stores activities, and this is one activity however deep the user is inside
     * it, so without this a resume from API Keys reopens the settings root and asks them to find
     * their way back. The search screen is dropped from the record rather than kept or truncated
     * at: it is a way of finding a setting rather than a place to be returned to, and the screen it
     * was used to open -- which sits above it on the stack -- is exactly where the user was.
     */
    @Override
    public void saveResumeState(@NonNull Bundle out) {
        super.saveResumeState(out);
        ArrayList<String> fragments = new ArrayList<>(resumeFragments.size());
        ArrayList<String> titles = new ArrayList<>(resumeFragmentTitles.size());
        String search = SettingsSearchFragment.class.getName();
        for (int i = 0; i < resumeFragments.size(); i++) {
            if (search.equals(resumeFragments.get(i))) {
                continue;
            }
            fragments.add(resumeFragments.get(i));
            titles.add(resumeFragmentTitles.get(i));
        }
        // Written even when it is empty, unlike a feed's position: "the settings root" is a real
        // answer this screen always knows, and an empty bundle would leave the last record standing
        // -- so backing out of API Keys to the root would still resume into API Keys.
        out.putStringArrayList(RESUME_FRAGMENTS_STATE, fragments);
        out.putStringArrayList(RESUME_FRAGMENT_TITLES_STATE, titles);
    }

    @Override
    public void restoreResumeState(@NonNull Bundle state) {
        super.restoreResumeState(state);
        if (!resumeFragmentsRestorable) {
            return;
        }
        ArrayList<String> fragments = state.getStringArrayList(RESUME_FRAGMENTS_STATE);
        ArrayList<String> titles = state.getStringArrayList(RESUME_FRAGMENT_TITLES_STATE);
        if (fragments == null || titles == null || fragments.size() != titles.size()) {
            return;
        }
        resumeFragmentsRestorable = false;
        FragmentManager fragmentManager = getSupportFragmentManager();
        for (int i = 0; i < fragments.size(); i++) {
            Fragment fragment;
            try {
                fragment = fragmentManager.getFragmentFactory()
                        .instantiate(getClassLoader(), fragments.get(i));
            } catch (RuntimeException e) {
                // A screen this build no longer has, or one that cannot be built without the
                // arguments it was opened with. What is below it is still where the user was.
                return;
            }
            navigateToSettingsFragment(fragment, titles.get(i), false);
            // Each transaction is executed before the next is queued, so the back stack and the
            // record above grow in step -- the listener that keeps them that way runs on every
            // change and would otherwise see a record several screens ahead of the stack and cut
            // it back down.
            fragmentManager.executePendingTransactions();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (getSupportFragmentManager().popBackStackImmediate()) {
            return true;
        }

        return super.onSupportNavigateUp();
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, Preference pref) {
        // Instantiate the new Fragment
        // Copy, not the live bundle: getExtras() is owned by the Preference, and handing it over
        // as fragment arguments would let the destination's edits write back into the preference.
        final Bundle args = new Bundle(pref.getExtras());
        final Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(getClassLoader(), Objects.requireNonNull(pref.getFragment()));
        SettingsScreenArgs.putScreenTitle(args, pref.getTitle());
        fragment.setArguments(args);
        fragment.setTargetFragment(caller, 0);
        recordResumeNavigation(fragment, pref.getTitle());

        getSupportFragmentManager().beginTransaction()
            .setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left, R.anim.enter_from_left, R.anim.exit_to_right)
            .replace(R.id.frame_layout_settings_activity, fragment)
            .addToBackStack(null)
            .commit();
        binding.appbarLayoutSettingsActivity.setExpanded(true);
        setTitle(pref.getTitle());
        return true;
    }

    /**
     * The account chooser reports to the activity hosting it, so pass the choice on to the settings
     * screen that asked for it. One screen is showing at a time, which is what makes that
     * unambiguous.
     */
    @Override
    public void onAccountSelected(Account account) {
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.frame_layout_settings_activity);
        if (current instanceof AccountChooserBottomSheetFragment.AccountChooserListener) {
            ((AccountChooserBottomSheetFragment.AccountChooserListener) current).onAccountSelected(account);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    public void showSnackbar(int stringId, int actionStringId, View.OnClickListener onClickListener) {
        Snackbar.make(binding.getRoot(), stringId, BaseTransientBottomBar.LENGTH_SHORT).setAction(actionStringId, onClickListener).show();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRecreateActivityEvent(RecreateActivityEvent recreateActivityEvent) {
        ActivityCompat.recreate(this);
    }
}
