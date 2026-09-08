package ml.docilealligator.infinityforreddit.activities;

import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;
import static com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED;
import static com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.view.menu.MenuItemImpl;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Objects;
import ml.docilealligator.infinityforreddit.CustomFontReceiver;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.customviews.slidr.Slidr;
import ml.docilealligator.infinityforreddit.customviews.slidr.widget.SliderPanel;
import ml.docilealligator.infinityforreddit.events.FinishViewMediaActivityEvent;
import ml.docilealligator.infinityforreddit.font.ContentFontFamily;
import ml.docilealligator.infinityforreddit.font.ContentFontStyle;
import ml.docilealligator.infinityforreddit.font.FontFamily;
import ml.docilealligator.infinityforreddit.font.FontStyle;
import ml.docilealligator.infinityforreddit.font.TitleFontFamily;
import ml.docilealligator.infinityforreddit.font.TitleFontStyle;
import ml.docilealligator.infinityforreddit.resume.Restorable;
import ml.docilealligator.infinityforreddit.resume.ResumeState;
import ml.docilealligator.infinityforreddit.utils.CustomThemeSharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.RedditLinkUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;
import org.greenrobot.eventbus.EventBus;

public abstract class BaseActivity extends AppCompatActivity implements CustomFontReceiver, Restorable {

    /** How far down its scrolling view a plain screen was. See {@link #saveResumeState}. */
    private static final String STATE_RESUME_SCROLL_Y = "RSY";
    /** How far the app bar was scrolled off the top. See {@link #saveResumeAppBarOffset}. */
    private static final String STATE_RESUME_APP_BAR_OFFSET = "RABO";
    /** Where the app bar's bottom edge sat. See {@link #saveResumeAppBarOffset}. */
    private static final String STATE_RESUME_APP_BAR_BOTTOM = "RABB";
    /** Not a possible app bar offset -- they are never positive -- so it can mean "not yet". */
    private static final int NO_APP_BAR_OFFSET = 1;
    public static final int IGNORE_MARGIN = -1;
    // Tag for refresh-rate diagnostics. Filter logcat with `RefreshRate:* *:S` to confirm whether
    // the "Force Maximum Refresh Rate" setting is actually taking effect on a given device.
    private static final String REFRESH_RATE_TAG = "RefreshRate";

    /** Whether {@link #claimResumeState()} has already run. See its javadoc for the ordering. */
    private boolean resumeStateClaimed;
    /** Live offset of the app bar handed to {@link #trackAppBarOffsetForResume}. Never positive. */
    private int appBarVerticalOffset;
    /** The app bar handed to {@link #trackAppBarOffsetForResume}, read again when saving. */
    @Nullable
    private AppBarLayout resumeAppBar;
    private boolean immersiveInterface;
    private boolean changeStatusBarIconColor;
    private boolean hasDrawerLayout = false;
    private boolean isImmersiveInterfaceApplicable = true;
    @SuppressWarnings("NullAway.Init")
    private View navBarScrim;
    private int systemVisibilityToolbarExpanded = 0;
    private int systemVisibilityToolbarCollapsed = 0;
    private boolean shouldTrackFullscreenMediaPeekTouchEvent;
    public CustomThemeWrapper customThemeWrapper;
    @Nullable
    public Typeface typeface;
    @Nullable
    public Typeface titleTypeface;
    @Nullable
    public Typeface contentTypeface;
    @Nullable
    public SliderPanel mSliderPanel;
    @Nullable
    public ViewPager2 mViewPager2;
    @Nullable
    public String accessToken;
    @NonNull
    public String accountName = Account.ANONYMOUS_ACCOUNT;
    public Handler mHandler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        customThemeWrapper = getCustomThemeWrapper();

        SharedPreferences mSharedPreferences = getDefaultSharedPreferences();

        String language = Objects.requireNonNull(mSharedPreferences.getString(SharedPreferencesUtils.LANGUAGE, SharedPreferencesUtils.LANGUAGE_DEFAULT_VALUE));
        Locale systemLocale = Resources.getSystem().getConfiguration().locale;
        Locale locale;
        if (language.equals(SharedPreferencesUtils.LANGUAGE_DEFAULT_VALUE)) {
            language = systemLocale.getLanguage();
            locale = new Locale(language, systemLocale.getCountry());
        } else {
            if (language.contains("-")) {
                locale = new Locale(language.substring(0, 2), language.substring(4));
            } else {
                locale = new Locale(language);
            }
        }
        Locale.setDefault(locale);
        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());

        boolean systemDefault = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
        int systemThemeType = SharedPreferencesUtils.getInt(mSharedPreferences, SharedPreferencesUtils.THEME_KEY, SharedPreferencesUtils.THEME_FOLLOW_SYSTEM);
        immersiveInterface = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                mSharedPreferences.getBoolean(SharedPreferencesUtils.IMMERSIVE_INTERFACE_KEY, true);
        if (immersiveInterface && config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            immersiveInterface = !mSharedPreferences.getBoolean(SharedPreferencesUtils.DISABLE_IMMERSIVE_INTERFACE_IN_LANDSCAPE_MODE, false);
        }
        switch (systemThemeType) {
            case 0:
                AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_NO);
                getTheme().applyStyle(R.style.Theme_Normal, true);
                customThemeWrapper.setThemeType(CustomThemeSharedPreferencesUtils.LIGHT);
                break;
            case 1:
                AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES);
                if(mSharedPreferences.getBoolean(SharedPreferencesUtils.AMOLED_DARK_KEY, false)) {
                    getTheme().applyStyle(R.style.Theme_Normal_AmoledDark, true);
                    customThemeWrapper.setThemeType(CustomThemeSharedPreferencesUtils.AMOLED);
                } else {
                    getTheme().applyStyle(R.style.Theme_Normal_NormalDark, true);
                    customThemeWrapper.setThemeType(CustomThemeSharedPreferencesUtils.DARK);
                }
                break;
            case 2:
                if (systemDefault) {
                    AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM);
                } else {
                    AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_AUTO_BATTERY);
                }
                if((getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO) {
                    getTheme().applyStyle(R.style.Theme_Normal, true);
                    customThemeWrapper.setThemeType(CustomThemeSharedPreferencesUtils.LIGHT);
                } else {
                    if(mSharedPreferences.getBoolean(SharedPreferencesUtils.AMOLED_DARK_KEY, false)) {
                        getTheme().applyStyle(R.style.Theme_Normal_AmoledDark, true);
                        customThemeWrapper.setThemeType(CustomThemeSharedPreferencesUtils.AMOLED);
                    } else {
                        getTheme().applyStyle(R.style.Theme_Normal_NormalDark, true);
                        customThemeWrapper.setThemeType(CustomThemeSharedPreferencesUtils.DARK);
                    }
                }
        }

        boolean userDefinedChangeStatusBarIconColorInImmersiveInterface =
                customThemeWrapper.isChangeStatusBarIconColorAfterToolbarCollapsedInImmersiveInterface();
        if (isImmersiveInterface()) {
            changeStatusBarIconColor = userDefinedChangeStatusBarIconColorInImmersiveInterface;
        } else {
            changeStatusBarIconColor = false;
        }

        getTheme().applyStyle(FontStyle.valueOf(Objects.requireNonNull(mSharedPreferences
                .getString(SharedPreferencesUtils.FONT_SIZE_KEY, FontStyle.Normal.name()))).getResId(), true);

        getTheme().applyStyle(TitleFontStyle.valueOf(Objects.requireNonNull(mSharedPreferences
                .getString(SharedPreferencesUtils.TITLE_FONT_SIZE_KEY, TitleFontStyle.Normal.name()))).getResId(), true);

        getTheme().applyStyle(ContentFontStyle.valueOf(Objects.requireNonNull(mSharedPreferences
                .getString(SharedPreferencesUtils.CONTENT_FONT_SIZE_KEY, ContentFontStyle.Normal.name()))).getResId(), true);

        getTheme().applyStyle(FontFamily.valueOf(Objects.requireNonNull(mSharedPreferences
                .getString(SharedPreferencesUtils.FONT_FAMILY_KEY, FontFamily.Default.name()))).getResId(), true);

        getTheme().applyStyle(TitleFontFamily.valueOf(Objects.requireNonNull(mSharedPreferences
                .getString(SharedPreferencesUtils.TITLE_FONT_FAMILY_KEY, TitleFontFamily.Default.name()))).getResId(), true);

        getTheme().applyStyle(ContentFontFamily.valueOf(Objects.requireNonNull(mSharedPreferences
                .getString(SharedPreferencesUtils.CONTENT_FONT_FAMILY_KEY, ContentFontFamily.Default.name()))).getResId(), true);

        Window window = getWindow();
        View decorView = window.getDecorView();
        boolean isLightStatusbar = customThemeWrapper.isLightStatusBar();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            boolean isLightNavBar = customThemeWrapper.isLightNavBar();
            if (isLightStatusbar) {
                if (isLightNavBar) {
                    systemVisibilityToolbarExpanded = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    if (changeStatusBarIconColor) {
                        systemVisibilityToolbarCollapsed = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    } else {
                        systemVisibilityToolbarCollapsed = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    }
                } else {
                    systemVisibilityToolbarExpanded = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    if (!changeStatusBarIconColor) {
                        systemVisibilityToolbarCollapsed = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    }
                }
            } else {
                if (isLightNavBar) {
                    systemVisibilityToolbarExpanded = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    if (changeStatusBarIconColor) {
                        systemVisibilityToolbarCollapsed = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    }
                } else {
                    if (changeStatusBarIconColor) {
                        systemVisibilityToolbarCollapsed = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    }
                }
            }
            decorView.setSystemUiVisibility(systemVisibilityToolbarExpanded);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                ViewCompat.setOnApplyWindowInsetsListener(window.getDecorView(), new OnApplyWindowInsetsListener() {
                    @Override
                    public @NonNull WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                        if (!isImmersiveInterface()) {
                            Insets inset = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                            v.setBackgroundColor(customThemeWrapper.getColorPrimary());
                            v.setPadding(inset.left, inset.top, inset.right, 0);
                            // Android 15+ forces edge-to-edge and ignores Window.setNavigationBarColor(),
                            // so when the immersive interface is off we paint our own opaque strip over the
                            // (otherwise transparent) navigation bar region to honor the Navigation Bar Color theme.
                            updateNavBarScrim(inset.bottom);
                        }
                        return insets;
                    }
                });
            }

            if (!isImmersiveInterface()) {
                window.setNavigationBarColor(customThemeWrapper.getNavBarColor());
                if (!hasDrawerLayout) {
                    window.setStatusBarColor(customThemeWrapper.getColorPrimaryDark());
                }
            } else {
                window.setNavigationBarColor(Color.TRANSPARENT);
                window.setStatusBarColor(Color.TRANSPARENT);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (isLightStatusbar) {
                decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                systemVisibilityToolbarExpanded = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (!changeStatusBarIconColor) {
                    systemVisibilityToolbarCollapsed = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
            } else if (changeStatusBarIconColor) {
                systemVisibilityToolbarCollapsed = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
        }

        applyPreferredRefreshRate(window, mSharedPreferences);

        accessToken = getCurrentAccountSharedPreferences().getString(SharedPreferencesUtils.ACCESS_TOKEN, null);
        accountName = Objects.requireNonNull(getCurrentAccountSharedPreferences().getString(SharedPreferencesUtils.ACCOUNT_NAME, Account.ANONYMOUS_ACCOUNT));

        mHandler = new Handler(Looper.getMainLooper());

        // Resume where I left off. Reopening from recents with the process dead recreates only the
        // top screen, so the stack underneath it has to be rebuilt from the snapshot -- otherwise
        // the next capture refuses it for not starting at MainActivity and the user loses it.
        ResumeState.seedFromSnapshot(this);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // The fallback, for every screen with nothing to decide from its recorded state while it is
        // being built. The ones that do have something to decide call claimResumeState() themselves.
        claimResumeState();
    }

    /**
     * Take this screen's recorded state out of the resume snapshot and hand it to
     * {@link #restoreResumeState(Bundle)}, at most once.
     *
     * <p>A screen that decides anything from that state while it is building -- which tab to open
     * on, whether the toolbar starts collapsed -- must call this from its own {@code onCreate},
     * after the content view exists and before it makes the decision. A pager's initial page is
     * chosen once and never revisited, so a claim arriving after that choice restores nothing at
     * all; that is the whole reason this is separate from {@link #onPostCreate}, which runs after
     * {@code onCreate} has finished. Screens with nothing to decide that early need not call it.
     *
     * <p>Calling it twice is harmless and expected: {@link #onPostCreate} always calls it, and finds
     * the claim already made.
     */
    protected final void claimResumeState() {
        if (resumeStateClaimed) {
            return;
        }
        resumeStateClaimed = true;
        Bundle resumeState = ResumeState.claim(this);
        if (resumeState != null) {
            restoreResumeState(resumeState);
        }
    }

    /**
     * Record how far down its scrolling view this screen was.
     *
     * <p>Every screen inherits this, which is the point: the rules screen, the wiki, a sidebar, a
     * settings page and everything else built on a {@code ScrollView} come back where the user left
     * them without any of them knowing this feature exists. A screen with real state of its own --
     * a feed, a comment thread -- overrides this and records that instead.
     */
    @Override
    public void saveResumeState(@NonNull Bundle out) {
        View scrollable = findFirstScrollable(findViewById(android.R.id.content));
        if (scrollable != null && scrollable.getScrollY() > 0) {
            out.putInt(STATE_RESUME_SCROLL_Y, scrollable.getScrollY());
        }
    }

    @Override
    public void restoreResumeState(@NonNull Bundle state) {
        final int scrollY = state.getInt(STATE_RESUME_SCROLL_Y, 0);
        if (scrollY <= 0) {
            return;
        }
        final View content = findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        // One layout pass and one posted tick: the pass gives the scrolling view its children, and
        // scrolling to an offset before they exist is a no-op that silently loses the restore.
        OneShotPreDrawListener.add(content, () -> content.post(() -> {
            View scrollable = findFirstScrollable(content);
            if (scrollable != null) {
                scrollable.scrollTo(0, scrollY);
            }
        }));
    }

    /**
     * Keep {@code appBar}'s offset current, so {@link #saveResumeAppBarOffset} can record where the
     * toolbar actually was rather than which end of its travel it was nearest.
     *
     * <p>Call this once, from {@code onCreate}, on the app bar the feed scrolls under.
     */
    protected final void trackAppBarOffsetForResume(@NonNull AppBarLayout appBar) {
        resumeAppBar = appBar;
        appBar.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                appBarVerticalOffset = verticalOffset;
            }
        });
    }

    /**
     * Record how far the app bar was scrolled off the top, in pixels.
     *
     * <p>Pixels and not a collapsed/expanded flag, because the app bar spends most of its life
     * between those two ends: every one of these screens gives its toolbar {@code enterAlways}, so
     * it tracks the feed one for one in both directions and comes to rest part way in whenever the
     * user scrolls back up a little and stops. A flag cannot say "137px into a 224px travel", and a
     * restore that rounds it to either end puts every row of the feed that far from where the user
     * left it -- while the feed itself is restored to the pixel, which is what makes the miss look
     * like a scroll-position bug rather than a toolbar one.
     *
     * <p>The bottom edge goes with it, and is what the restore actually aims at. The offset alone
     * only means something against the app bar it was measured on: a subreddit's grows as its
     * banner and description arrive, so the same -993px that left the toolbar and tabs showing in
     * one session hides them in the next, and every row of the feed comes back that much too high.
     * The edge is where the feed starts, so putting it back where it was is what puts the rows back
     * where the user left them, whatever the header is doing.
     */
    protected final void saveResumeAppBarOffset(@NonNull Bundle out) {
        if (appBarVerticalOffset >= 0) {
            return;
        }
        out.putInt(STATE_RESUME_APP_BAR_OFFSET, appBarVerticalOffset);
        AppBarLayout appBar = resumeAppBar;
        if (appBar != null) {
            // Read now rather than cached from the last offset change: a header that finishes
            // laying out after the user stops scrolling moves this edge without moving the bar, so
            // a value kept from the last scroll is short by however much arrived since.
            out.putInt(STATE_RESUME_APP_BAR_BOTTOM, appBar.getBottom());
        }
    }

    /**
     * Put the app bar back at the offset {@link #saveResumeAppBarOffset} recorded.
     *
     * <p>Call this from {@code restoreResumeState}. The feed's own restore is independent of it --
     * a row's offset is measured against the RecyclerView, which the app bar moves as a whole -- so
     * the two can land in either order; what they must not do is disagree about the app bar.
     *
     * @param scrollingChild the view that scrolls under the app bar, used only to decide whether the
     *                       bar draws itself lifted; the app bar itself is a fine stand-in.
     */
    protected final void restoreResumeAppBarOffset(@NonNull Bundle state, @NonNull AppBarLayout appBar,
                                                   @Nullable View scrollingChild) {
        final int offset = state.getInt(STATE_RESUME_APP_BAR_OFFSET, 0);
        if (offset >= 0) {
            return;
        }
        // What the restore aims at is the bar's bottom edge, not its offset: that edge is where the
        // feed starts, and it is the offset that has to be worked out from it each time, because
        // the height it is measured against keeps changing. A subreddit's header grows as its
        // banner and description arrive -- measured on device, 246px of it after the restore had
        // already run -- so re-applying the recorded offset against the shorter bar left the feed
        // 246px too high, every row of it, with the rows themselves restored to the pixel. A record
        // written before the edge was is missing it and falls back to the offset it was written
        // under.
        // Absent is -1, not 0: a bar collapsed the whole way has a bottom edge of exactly 0, and
        // reading that as "no edge recorded" sent the one case that is easiest to get right down
        // the fallback path.
        final int recordedBottom = state.getInt(STATE_RESUME_APP_BAR_BOTTOM, -1);
        // Watched across layout passes, not applied once: some of these app bars grow after their
        // first measure -- a subreddit's header and a user's fill in from the network seconds later
        // -- and the height the target is worked out from is not final until they have. A global
        // layout listener and not addOnLayoutChangeListener, because the growth that matters is a
        // child's: it changes what the bar measures without necessarily moving the bar's own
        // bounds, which is the only thing that listener reports. It takes itself off when the user
        // moves the bar, or when a record with no edge in it has been honoured once.
        final ViewTreeObserver observer = appBar.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            /** Where this listener last left the bar, or {@code NO_APP_BAR_OFFSET} for never. */
            private int applied = NO_APP_BAR_OFFSET;
            /** The app bar's height when it was left there, to tell a re-measure from a finger. */
            private int appliedHeight;

            @Override
            public void onGlobalLayout() {
                if (keepWatching()) {
                    return;
                }
                ViewTreeObserver current = appBar.getViewTreeObserver();
                (current.isAlive() ? current : observer).removeOnGlobalLayoutListener(this);
            }

            /** @return whether it is worth looking again on the app bar's next layout. */
            private boolean keepWatching() {
                AppBarLayout.Behavior behavior = appBarBehaviorOf(appBar);
                if (behavior == null) {
                    return false;
                }
                int current = behavior.getTopAndBottomOffset();
                int height = appBar.getHeight();
                if (applied != NO_APP_BAR_OFFSET && current != applied && height == appliedHeight) {
                    // The bar has moved on its own since it was put there, and it is the same bar
                    // it was, so that was the user scrolling and their position beats the record.
                    //
                    // The height has to be in the comparison: an AppBarLayout re-offsets itself
                    // when it grows -- measured on device, a subreddit's header arriving at 1083px
                    // tall and settling at 1329px moved the bar from the -797 it had just been put
                    // at to -1006 on its own. Reading that as a finger left the bar 37px short of
                    // the edge that was recorded, and every row of the feed 37px with it.
                    return false;
                }
                // Worked out again every pass rather than computed once, because the height it is
                // subtracted from is the thing that is still changing: as the header fills in, the
                // offset that keeps the edge where it was grows with it.
                int target;
                if (recordedBottom >= 0) {
                    target = Math.max(-appBar.getTotalScrollRange(),
                            Math.min(0, recordedBottom - height));
                } else {
                    target = offset;
                    if (appBar.getTotalScrollRange() < -target) {
                        // The bar cannot travel that far yet. Collapsing it as far as it will go
                        // instead is worse than waiting: the bottom of the travel is where the
                        // AppBarLayout keeps it as the travel grows, so the bar ends up fully
                        // collapsed rather than the sliver short of it that was recorded. Keep
                        // waiting only while nothing has moved the bar -- once something has, that
                        // is the user scrolling.
                        return current == 0;
                    }
                }
                // Collapsing only. The other direction is clamped to the bar's down-pre-scroll
                // range, which an enterAlwaysCollapsed toolbar -- a subreddit's, a user's --
                // deliberately makes shorter than its travel, so asking to come back part way lands
                // wherever that clamp falls instead of where the record says. A bar already further
                // in than the record is left alone.
                if (current > target) {
                    applyAppBarOffset(appBar, behavior, scrollingChild, current - target);
                }
                applied = behavior.getTopAndBottomOffset();
                appliedHeight = height;
                // Watched until the user moves the bar rather than stopped at the first success:
                // the header is still filling in, and every bit of it that arrives pushes the edge
                // back down unless the offset grows to match. A record with no edge in it has
                // nothing later to improve on, so that one is done.
                return recordedBottom >= 0;
            }
        });
    }

    /** The behavior driving {@code appBar}, or null for an app bar outside a CoordinatorLayout. */
    @Nullable
    private static AppBarLayout.Behavior appBarBehaviorOf(@NonNull AppBarLayout appBar) {
        if (!(appBar.getParent() instanceof CoordinatorLayout)
                || !(appBar.getLayoutParams() instanceof CoordinatorLayout.LayoutParams)) {
            return null;
        }
        CoordinatorLayout.Behavior<?> behavior =
                ((CoordinatorLayout.LayoutParams) appBar.getLayoutParams()).getBehavior();
        return behavior instanceof AppBarLayout.Behavior ? (AppBarLayout.Behavior) behavior : null;
    }

    /**
     * Scroll {@code appBar} up by {@code dy} through the behavior's own nested-scroll entry point.
     *
     * <p>Not by setting the offset on the behavior directly: that moves the bar and leaves
     * everything that follows it behind -- the pinned toolbar, the elevation state, the tab colours
     * driven from an offset listener. This is the path a finger takes, so all of it keeps up.
     */
    private static void applyAppBarOffset(@NonNull AppBarLayout appBar,
                                          @NonNull AppBarLayout.Behavior behavior,
                                          @Nullable View scrollingChild, int dy) {
        if (!(appBar.getParent() instanceof CoordinatorLayout)) {
            return;
        }
        CoordinatorLayout parent = (CoordinatorLayout) appBar.getParent();
        behavior.onNestedPreScroll(parent, appBar,
                scrollingChild == null ? appBar : scrollingChild, 0, dy, new int[2],
                ViewCompat.TYPE_TOUCH);
        // The behavior leaves the views that sit under the app bar to CoordinatorLayout's own
        // pre-draw pass. We may be inside one already, so ask for them now rather than betting on
        // running before it.
        parent.dispatchDependentViewsChanged(appBar);
    }

    /**
     * The first {@link ScrollView} or {@link NestedScrollView} under {@code root}, breadth first so
     * the outermost one wins -- an inner scroller nested inside the page's own is a component's
     * business, not the page's position.
     */
    @Nullable
    private static View findFirstScrollable(@Nullable View root) {
        if (root == null) {
            return null;
        }
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            View view = queue.removeFirst();
            if (view instanceof ScrollView || view instanceof NestedScrollView) {
                return view;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    queue.addLast(group.getChildAt(i));
                }
            }
        }
        return null;
    }

    /**
     * When the "Force Maximum Refresh Rate" setting is enabled, detect the highest refresh rate the
     * current display supports and request it as the window's preferred refresh rate. This is only a
     * hint: the system (and some manufacturers' display schedulers) may ignore it, so it is best
     * effort and varies by make and model. When the setting is disabled the preferred refresh rate
     * is not set, leaving the device to manage the refresh rate as usual.
     */
    private void applyPreferredRefreshRate(Window window, SharedPreferences sharedPreferences) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        boolean enabled = sharedPreferences.getBoolean(SharedPreferencesUtils.FORCE_MAX_REFRESH_RATE_KEY, false);

        Display display;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display = getDisplay();
        } else {
            display = getWindowManager().getDefaultDisplay();
        }
        if (display == null) {
            Log.w(REFRESH_RATE_TAG, "No display available; cannot read or set refresh rate");
            return;
        }

        // Current active rate at activity-create time (the baseline, before this hint is applied).
        float currentRefreshRate = display.getMode().getRefreshRate();
        Log.d(REFRESH_RATE_TAG, this.getClass().getSimpleName()
                + ": setting " + (enabled ? "ON" : "OFF")
                + "; current refresh rate = " + currentRefreshRate + "Hz");

        if (!enabled) {
            Log.d(REFRESH_RATE_TAG, "Force max refresh rate disabled; leaving preferredRefreshRate unset");
            logEffectiveRefreshRateAfterSettle(window, display);
            return;
        }

        float maxRefreshRate = 0f;
        StringBuilder modes = new StringBuilder();
        for (Display.Mode mode : display.getSupportedModes()) {
            modes.append(String.format(Locale.US, "[%dx%d@%.2fHz] ",
                    mode.getPhysicalWidth(), mode.getPhysicalHeight(), mode.getRefreshRate()));
            if (mode.getRefreshRate() > maxRefreshRate) {
                maxRefreshRate = mode.getRefreshRate();
            }
        }
        Log.d(REFRESH_RATE_TAG, "supported modes = " + modes.toString().trim()
                + "; detected max = " + maxRefreshRate + "Hz");
        if (maxRefreshRate > 0f) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.preferredRefreshRate = maxRefreshRate;
            window.setAttributes(params);
            Log.d(REFRESH_RATE_TAG, "Requested preferredRefreshRate = "
                    + window.getAttributes().preferredRefreshRate + "Hz");
        }
        logEffectiveRefreshRateAfterSettle(window, display);
    }

    // The requested rate does not take effect until the window has been laid out and the display
    // scheduler has switched, so we read the actual active rate again a short time after create.
    private void logEffectiveRefreshRateAfterSettle(Window window, Display display) {
        window.getDecorView().postDelayed(() -> {
            float effective = display.getMode().getRefreshRate();
            Log.d(REFRESH_RATE_TAG, this.getClass().getSimpleName()
                    + ": effective refresh rate after settle = " + effective + "Hz");
        }, 1500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mSliderPanel != null) {
            setTranslucent(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mSliderPanel != null && !isFinishing()) {
            mHandler.postDelayed(() -> setTranslucent(false), 500);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (shouldTrackFullscreenMediaPeekTouchEvent) {
            if (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP) {
                shouldTrackFullscreenMediaPeekTouchEvent = false;
                EventBus.getDefault().post(new FinishViewMediaActivityEvent());
            }
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    public abstract SharedPreferences getDefaultSharedPreferences();

    public abstract SharedPreferences getCurrentAccountSharedPreferences();

    public abstract CustomThemeWrapper getCustomThemeWrapper();

    protected abstract void applyCustomTheme();

    protected boolean isChangeStatusBarIconColor() {
        return changeStatusBarIconColor;
    }

    protected int getSystemVisibilityToolbarExpanded() {
        return systemVisibilityToolbarExpanded;
    }

    protected int getSystemVisibilityToolbarCollapsed() {
        return systemVisibilityToolbarCollapsed;
    }

    public boolean isImmersiveInterfaceRespectForcedEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return true;
        }
        return immersiveInterface && isImmersiveInterfaceApplicable;
    }

    private boolean isImmersiveInterface() {
        return immersiveInterface && isImmersiveInterfaceApplicable;
    }

    public boolean isForcedImmersiveInterface() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && !immersiveInterface;
    }

    public boolean isImmersiveInterfaceEnabled() {
        return immersiveInterface;
    }

    /**
     * Draws an opaque strip the height of the bottom system-bar inset, colored with the theme's
     * Navigation Bar Color, on top of the activity content. Used only on Android 15+ where the OS
     * forces edge-to-edge and ignores {@link Window#setNavigationBarColor(int)}; this restores a
     * solid navigation bar for users who have the immersive interface turned off.
     */
    private void updateNavBarScrim(int bottomInset) {
        ViewGroup contentView = findViewById(android.R.id.content);
        if (contentView == null) {
            return;
        }
        if (navBarScrim == null) {
            navBarScrim = new View(this);
            navBarScrim.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, bottomInset, Gravity.BOTTOM));
            contentView.addView(navBarScrim);
        } else {
            ViewGroup.LayoutParams params = navBarScrim.getLayoutParams();
            if (params.height != bottomInset) {
                params.height = bottomInset;
                navBarScrim.setLayoutParams(params);
            }
        }
        navBarScrim.setBackgroundColor(customThemeWrapper.getNavBarColor());
        navBarScrim.setVisibility(bottomInset > 0 ? View.VISIBLE : View.GONE);
    }

    protected void setToolbarGoToTop(Toolbar toolbar) {
        toolbar.setOnLongClickListener(view -> {
            if (BaseActivity.this instanceof ActivityToolbarInterface) {
                ((ActivityToolbarInterface) BaseActivity.this).onLongPress();
            }
            return true;
        });
    }

    /*protected void adjustToolbar(Toolbar toolbar) {
        int statusBarResourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (statusBarResourceId > 0) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) toolbar.getLayoutParams();
            params.topMargin = getResources().getDimensionPixelSize(statusBarResourceId);
            toolbar.setLayoutParams(params);
        }
    }*/

    protected void addOnOffsetChangedListener(AppBarLayout appBarLayout) {
        View decorView = getWindow().getDecorView();
        appBarLayout.addOnOffsetChangedListener(new AppBarStateChangeListener() {
            @Override
            public void onStateChanged(AppBarLayout appBarLayout, AppBarStateChangeListener.State state) {
                if (state == State.COLLAPSED) {
                    decorView.setSystemUiVisibility(getSystemVisibilityToolbarCollapsed());
                } else if (state == State.EXPANDED) {
                    decorView.setSystemUiVisibility(getSystemVisibilityToolbarExpanded());
                }
            }
        });
    }

    public static <T extends View> void setMargins(T view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) lp;

            if (top >= 0) {
                marginParams.topMargin = top;
            }
            if (bottom >= 0) {
                marginParams.bottomMargin = bottom;
            }
            if (left >= 0) {
                marginParams.setMarginStart(left);
            }
            if (right >= 0) {
                marginParams.setMarginEnd(right);
            }

            view.setLayoutParams(marginParams);
        }
    }

    protected void setTransparentStatusBarAfterToolbarCollapsed() {
    }

    protected void setHasDrawerLayout() {
        hasDrawerLayout = true;
    }

    public void setImmersiveModeNotApplicableBelowAndroid16() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return;
        }
        isImmersiveInterfaceApplicable = false;
    }

    protected void applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(AppBarLayout appBarLayout, @Nullable CollapsingToolbarLayout collapsingToolbarLayout, Toolbar toolbar) {
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(appBarLayout, collapsingToolbarLayout, toolbar, true);
    }

    protected void applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(AppBarLayout appBarLayout, @Nullable CollapsingToolbarLayout collapsingToolbarLayout, Toolbar toolbar, boolean setToolbarBackgroundColor) {
        appBarLayout.setBackgroundColor(customThemeWrapper.getColorPrimary());
        if (collapsingToolbarLayout != null) {
            collapsingToolbarLayout.setContentScrimColor(customThemeWrapper.getColorPrimary());
        }
        if (setToolbarBackgroundColor) {
            toolbar.setBackgroundColor(customThemeWrapper.getColorPrimary());
        } else if (!isImmersiveInterface()) {
            int[] colors = {customThemeWrapper.getColorPrimary(), Color.TRANSPARENT};
            GradientDrawable gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors);
            toolbar.setBackground(gradientDrawable);
        }
        toolbar.setTitleTextColor(customThemeWrapper.getToolbarPrimaryTextAndIconColor());
        toolbar.setSubtitleTextColor(customThemeWrapper.getToolbarSecondaryTextColor());
        if (toolbar.getNavigationIcon() != null) {
            toolbar.getNavigationIcon().setColorFilter(customThemeWrapper.getToolbarPrimaryTextAndIconColor(), android.graphics.PorterDuff.Mode.SRC_IN);
        }
        if (toolbar.getOverflowIcon() != null) {
            toolbar.getOverflowIcon().setColorFilter(customThemeWrapper.getToolbarPrimaryTextAndIconColor(), android.graphics.PorterDuff.Mode.SRC_IN);
        }
        if (typeface != null) {
            toolbar.addOnLayoutChangeListener((view, i, i1, i2, i3, i4, i5, i6, i7) -> {
                for (int j = 0; j < toolbar.getChildCount(); j++) {
                    if (toolbar.getChildAt(j) instanceof TextView) {
                        ((TextView) toolbar.getChildAt(j)).setTypeface(typeface);
                    }
                }
            });
        }
    }

    protected void applyAppBarScrollFlagsIfApplicable(CollapsingToolbarLayout collapsingToolbarLayout) {
        applyAppBarScrollFlagsIfApplicable(collapsingToolbarLayout, null);
    }

    protected void applyAppBarScrollFlagsIfApplicable(@NonNull CollapsingToolbarLayout collapsingToolbarLayout, @Nullable TabLayout tabLayout) {
        if (getDefaultSharedPreferences().getBoolean(SharedPreferencesUtils.LOCK_TOOLBAR, false)) {
            AppBarLayout.LayoutParams p = (AppBarLayout.LayoutParams) collapsingToolbarLayout.getLayoutParams();
            p.setScrollFlags(SCROLL_FLAG_SCROLL | SCROLL_FLAG_EXIT_UNTIL_COLLAPSED);
            collapsingToolbarLayout.setLayoutParams(p);

            if (tabLayout != null) {
                AppBarLayout.LayoutParams p1 = (AppBarLayout.LayoutParams) tabLayout.getLayoutParams();
                p1.setScrollFlags(SCROLL_FLAG_SCROLL | SCROLL_FLAG_EXIT_UNTIL_COLLAPSED);
                tabLayout.setLayoutParams(p1);
            }
        }
    }

    @SuppressLint("RestrictedApi")
    protected boolean applyMenuItemTheme(Menu menu) {
        if (customThemeWrapper != null) {
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                if (((MenuItemImpl) item).requestsActionButton()) {
                    MenuItemCompat.setIconTintList(item, ColorStateList
                            .valueOf(customThemeWrapper.getToolbarPrimaryTextAndIconColor()));
                }
                Utils.setTitleWithCustomFontToMenuItem(typeface, item, null);
            }
        }
        return true;
    }

    protected void applyTabLayoutTheme(TabLayout tabLayout) {
        int toolbarAndTabBackgroundColor = customThemeWrapper.getColorPrimary();
        tabLayout.setBackgroundColor(toolbarAndTabBackgroundColor);
        tabLayout.setSelectedTabIndicatorColor(customThemeWrapper.getTabLayoutWithCollapsedCollapsingToolbarTabIndicator());
        tabLayout.setTabTextColors(customThemeWrapper.getTabLayoutWithCollapsedCollapsingToolbarTextColor(),
                customThemeWrapper.getTabLayoutWithCollapsedCollapsingToolbarTextColor());
    }

    protected void applyFABTheme(FloatingActionButton fab) {
        fab.setBackgroundTintList(ColorStateList.valueOf(customThemeWrapper.getColorAccent()));
        fab.setImageTintList(ColorStateList.valueOf(customThemeWrapper.getFABIconColor()));
    }

    protected void fixViewPager2Sensitivity(ViewPager2 viewPager2) {
        try {
            Field recyclerViewField = ViewPager2.class.getDeclaredField("mRecyclerView");
            recyclerViewField.setAccessible(true);

            RecyclerView recyclerView = (RecyclerView) recyclerViewField.get(viewPager2);

            Field touchSlopField = RecyclerView.class.getDeclaredField("mTouchSlop");
            touchSlopField.setAccessible(true);

            Object touchSlopBox = touchSlopField.get(recyclerView);
            if (touchSlopBox != null) {
                int touchSlop = (int) touchSlopBox;
                touchSlopField.set(recyclerView, touchSlop * SharedPreferencesUtils.getInt(getDefaultSharedPreferences(), SharedPreferencesUtils.TAB_SWITCHING_SENSITIVITY, "4"));
            }
        } catch (NoSuchFieldException | IllegalAccessException ignore) {
            Log.d("BaseActivity", "fixViewPager2Sensitivity: ignoring NoSuchFieldException | IllegalAccessException", ignore);
        }
    }

    protected void setOtherActivitiesFabContentDescription(FloatingActionButton fab, int fabOption) {
        switch (fabOption) {
            case SharedPreferencesUtils.OTHER_ACTIVITIES_BOTTOM_APP_BAR_FAB_SUBMIT_POSTS:
                fab.setContentDescription(getString(R.string.content_description_submit_post));
                break;
            case SharedPreferencesUtils.OTHER_ACTIVITIES_BOTTOM_APP_BAR_FAB_REFRESH:
                fab.setContentDescription(getString(R.string.content_description_refresh));
                break;
            case SharedPreferencesUtils.OTHER_ACTIVITIES_BOTTOM_APP_BAR_FAB_CHANGE_SORT_TYPE:
                fab.setContentDescription(getString(R.string.content_description_change_sort_type));
                break;
            case SharedPreferencesUtils.OTHER_ACTIVITIES_BOTTOM_APP_BAR_FAB_CHANGE_POST_LAYOUT:
                fab.setContentDescription(getString(R.string.content_description_change_post_layout));
                break;
            case SharedPreferencesUtils.OTHER_ACTIVITIES_BOTTOM_APP_BAR_FAB_SEARCH:
                fab.setContentDescription(getString(R.string.content_description_search));
                break;
            case SharedPreferencesUtils.OTHER_ACTIVITIES_BOTTOM_APP_BAR_FAB_GO_TO_SUBREDDIT:
                fab.setContentDescription(getString(R.string.content_description_go_to_subreddit));
                break;
            case SharedPreferencesUtils.OTHER_ACTIVITIES_BOTTOM_APP_BAR_FAB_GO_TO_USER:
                fab.setContentDescription(getString(R.string.content_description_go_to_user));
                break;
            case SharedPreferencesUtils.OTHER_ACTIVITIES_BOTTOM_APP_BAR_FAB_HIDE_READ_POSTS:
                fab.setContentDescription(getString(R.string.content_description_hide_read_posts));
                break;
            case SharedPreferencesUtils.OTHER_ACTIVITIES_BOTTOM_APP_BAR_FAB_FILTER_POSTS:
                fab.setContentDescription(getString(R.string.content_description_filter_posts));
                break;
            case SharedPreferencesUtils.OTHER_ACTIVITIES_BOTTOM_APP_BAR_FAB_GO_TO_TOP:
                fab.setContentDescription(getString(R.string.content_description_go_to_top));
                break;
        }
    }

    protected void attachSliderPanelIfApplicable() {
        if (getDefaultSharedPreferences().getBoolean(SharedPreferencesUtils.SWIPE_RIGHT_TO_GO_BACK, true)) {
            mSliderPanel = Slidr.attach(this,
                    SharedPreferencesUtils.getFloat(getDefaultSharedPreferences(), SharedPreferencesUtils.SWIPE_RIGHT_TO_GO_BACK_SENSITIVITY, "0.1")
            );
        }
    }

    @Override
    public void setCustomFont(@Nullable Typeface typeface, @Nullable Typeface titleTypeface, @Nullable Typeface contentTypeface) {
        this.typeface = typeface;
        this.titleTypeface = titleTypeface;
        this.contentTypeface = contentTypeface;
    }


    public void lockSwipeRightToGoBack() {

    }

    public void unlockSwipeRightToGoBack() {

    }

    public void copyLink(String link) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("simple text",
                    RedditLinkUtils.applyLinkDomain(getDefaultSharedPreferences(), link));
            clipboard.setPrimaryClip(clip);
            if (android.os.Build.VERSION.SDK_INT < 33) {
                Toast.makeText(this, R.string.copy_success, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, R.string.copy_link_failed, Toast.LENGTH_SHORT).show();
        }
    }

    public void shareLink(String link) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, RedditLinkUtils.applyLinkDomain(getDefaultSharedPreferences(), link));
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.share)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_activity_found_for_share, Toast.LENGTH_SHORT).show();
        }
    }

    public void triggerBackPress() {
        getOnBackPressedDispatcher().onBackPressed();
    }

    public void setShouldTrackFullscreenMediaPeekTouchEvent(boolean value) {
        shouldTrackFullscreenMediaPeekTouchEvent = value;
    }
}
