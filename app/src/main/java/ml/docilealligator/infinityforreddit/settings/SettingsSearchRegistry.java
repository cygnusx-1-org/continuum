package ml.docilealligator.infinityforreddit.settings;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.os.LocaleList;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import ml.docilealligator.infinityforreddit.R;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Singleton that holds a flat list of all searchable settings items.
 *
 * <p>The list is derived by walking the preference XML tree, starting at
 * {@link R.xml#main_preferences} and descending through every {@code app:fragment} link, so
 * adding a preference to an XML file is all it takes to make it searchable.
 *
 * <p>The one thing the XML cannot tell us is which XML resource each preference fragment
 * inflates -- that lives in Java, in each fragment's {@code setPreferencesFromResource} call --
 * so {@link #SCREEN_RESOURCES} mirrors it. A fragment missing from that map is still indexed as
 * a destination; the crawl just stops there rather than descending into its preferences.
 *
 * <p>Call {@link #buildRegistry(Context)} once per settings screen (e.g. in
 * SettingsActivity.onCreate) to populate; it is cheap after the first successful build.
 */
public class SettingsSearchRegistry {

    private static final String TAG = "SettingsSearchRegistry";
    private static final String BREADCRUMB_SEPARATOR = " › ";
    /** Separates the parts of an item identity; cannot occur in a key or title. */
    private static final String IDENTITY_SEPARATOR = "\u0000";

    /**
     * Preference fragment -> the XML resource it inflates. Must match each fragment's
     * {@code setPreferencesFromResource} call; a stale entry only costs coverage, never a crash.
     */
    private static final Map<Class<? extends Fragment>, Integer> SCREEN_RESOURCES;

    static {
        Map<Class<? extends Fragment>, Integer> m = new HashMap<>();
        m.put(MainPreferenceFragment.class, R.xml.main_preferences);
        m.put(APIKeysPreferenceFragment.class, R.xml.api_keys_preferences);
        m.put(NotificationPreferenceFragment.class, R.xml.notification_preferences);
        m.put(InterfacePreferenceFragment.class, R.xml.interface_preferences);
        m.put(FontPreferenceFragment.class, R.xml.font_preferences);
        m.put(ImmersiveInterfacePreferenceFragment.class, R.xml.immersive_interface_preferences);
        m.put(NavigationDrawerPreferenceFragment.class, R.xml.navigation_drawer_preferences);
        m.put(TimeFormatPreferenceFragment.class, R.xml.time_format_preferences);
        m.put(PostPreferenceFragment.class, R.xml.post_preferences);
        m.put(NumberOfColumnsInPostFeedPreferenceFragment.class,
                R.xml.number_of_columns_in_post_feed_preferences);
        m.put(PostDetailsPreferenceFragment.class, R.xml.post_details_preferences);
        m.put(CommentPreferenceFragment.class, R.xml.comment_preferences);
        m.put(ThemePreferenceFragment.class, R.xml.theme_preferences);
        m.put(VideoPreferenceFragment.class, R.xml.video_preferences);
        m.put(GesturesAndButtonsPreferenceFragment.class, R.xml.gestures_and_buttons_preferences);
        m.put(SwipeActionPreferenceFragment.class, R.xml.swipe_action_preferences);
        m.put(SecurityPreferenceFragment.class, R.xml.security_preferences);
        m.put(DataSavingModePreferenceFragment.class, R.xml.data_saving_mode_preferences);
        m.put(ProxyPreferenceFragment.class, R.xml.proxy_preferences);
        m.put(SortTypePreferenceFragment.class, R.xml.sort_type_preferences);
        m.put(DownloadLocationPreferenceFragment.class, R.xml.download_location_preferences);
        m.put(MiscellaneousPreferenceFragment.class, R.xml.miscellaneous_preferences);
        m.put(AdvancedPreferenceFragment.class, R.xml.advanced_preferences);
        m.put(AboutPreferenceFragment.class, R.xml.about_preferences);
        m.put(CreditsPreferenceFragment.class, R.xml.credits_preferences);
        m.put(DebugPreferenceFragment.class, R.xml.debug_preferences);
        SCREEN_RESOURCES = Collections.unmodifiableMap(m);
    }

    // Eagerly created: the constructor does nothing, and lazy init here would need locking now
    // that the index is built on a background thread while the UI reads it on the main one.
    private static final SettingsSearchRegistry sInstance = new SettingsSearchRegistry();

    /** Written on a background thread by {@link #buildRegistry}, read on the main thread. */
    @Nullable
    private volatile List<SettingsSearchItem> mItems;
    /**
     * Locale the cached items were resolved in, so a language change rebuilds them. Left null
     * when a build did not finish, which forces the next call to try again.
     */
    @Nullable
    private volatile Locale mItemsLocale;

    private SettingsSearchRegistry() {}

    public static SettingsSearchRegistry getInstance() {
        return sInstance;
    }

    public List<SettingsSearchItem> getItems() {
        return mItems != null ? mItems : Collections.emptyList();
    }

    /**
     * Whether a finished index for {@code ctx}'s language is already in hand, so callers can skip
     * handing the build to a background thread that would have nothing to do.
     *
     * <p>Shares {@link #buildRegistry}'s lock: the items and the locale they were resolved in are
     * written one after the other, and reading them unlocked can catch a new index paired with the
     * previous language.
     */
    public synchronized boolean hasIndexFor(Context ctx) {
        return mItems != null && localeOf(ctx).equals(mItemsLocale);
    }

    private static Locale localeOf(Context ctx) {
        LocaleList locales = ctx.getResources().getConfiguration().getLocales();
        return locales.isEmpty() ? Locale.getDefault() : locales.get(0);
    }

    /**
     * Builds the index if it is not already current. Safe to call off the main thread, and
     * synchronized so two callers racing to open search cannot both do the work.
     *
     * @param ctx an Activity context -- the app-wide language override is applied to the
     *            activity's resources, so an application context can resolve stale strings.
     */
    public synchronized void buildRegistry(Context ctx) {
        Locale locale = localeOf(ctx);
        if (mItems != null && locale.equals(mItemsLocale)) {
            return;
        }

        List<SettingsSearchItem> items = new ArrayList<>();
        boolean complete = crawl(ctx, R.xml.main_preferences, MainPreferenceFragment.class,
                ctx.getString(R.string.settings_activity_label), "", items,
                new HashSet<>(), new HashSet<>());

        // A truncated index is still worth serving, but must not be cached as final.
        mItems = Collections.unmodifiableList(items);
        mItemsLocale = complete ? locale : null;
    }

    @VisibleForTesting
    static Map<Class<? extends Fragment>, Integer> getScreenResources() {
        return SCREEN_RESOURCES;
    }

    // -------------------------------------------------------------------------
    // Crawl
    // -------------------------------------------------------------------------

    /**
     * Indexes every preference in {@code xmlResId}, then recurses into the screens they link to.
     *
     * @param screenFragment fragment hosting this XML, where non-navigating preferences live
     * @param screenTitle    toolbar title for {@code screenFragment}
     * @param breadcrumb     " > "-joined ancestor screen titles, empty at the root
     * @param visitedScreens XML resources already crawled, to stop cycles
     * @param seenItems      identity of items already added, to drop duplicates
     * @return whether the whole subtree was indexed without a parse error
     */
    private boolean crawl(Context c, int xmlResId, Class<? extends Fragment> screenFragment,
                          String screenTitle, String breadcrumb, List<SettingsSearchItem> items,
                          Set<Integer> visitedScreens, Set<String> seenItems) {
        if (!visitedScreens.add(xmlResId)) {
            return true;
        }

        String screenBreadcrumb = breadcrumb.isEmpty()
                ? c.getString(R.string.settings_activity_label) : breadcrumb;
        // Section heading the crawl is currently under, to tell same-titled rows apart -- the
        // Number of Columns screen alone has four "Portrait" rows, one per post layout.
        String currentCategory = null;
        boolean complete = true;

        XmlResourceParser parser = c.getResources().getXml(xmlResId);
        try {
            for (int event = parser.next(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                if (event != XmlPullParser.START_TAG) {
                    continue;
                }
                // Screens are containers, never settings.
                String tag = parser.getName();
                if (tag == null || tag.endsWith("PreferenceScreen")) {
                    continue;
                }

                PreferenceAttributes attrs = readAttributes(c, parser);
                String title = attrs.title;
                if (title == null || title.isEmpty()) {
                    continue;
                }

                // A category is not a preference, but naming one is a fair way to look for the
                // screen it heads, so index it and qualify the rows that follow it. A hidden
                // heading still groups its rows, it just is not a result in its own right.
                if (tag.endsWith("PreferenceCategory")) {
                    if (attrs.visible) {
                        add(items, seenItems, title, attrs, screenBreadcrumb, screenFragment,
                                screenTitle, attrs.key);
                    }
                    currentCategory = title;
                    continue;
                }

                String itemBreadcrumb = currentCategory == null
                        ? screenBreadcrumb : screenBreadcrumb + BREADCRUMB_SEPARATOR + currentCategory;

                Class<? extends Fragment> target = attrs.fragment == null
                        ? null : resolveFragment(attrs.fragment);
                if (target == null) {
                    // Rows that start hidden are revealed at runtime only when some other setting
                    // is on, so a result would land on a screen that does not show the row.
                    if (attrs.visible) {
                        add(items, seenItems, title, attrs, itemBreadcrumb, screenFragment,
                                screenTitle, attrs.key);
                    }
                    continue;
                }

                // A preference that opens another screen: index it as a way to reach that screen,
                // then index the screen's own preferences underneath it. Worth doing even when the
                // row starts hidden, because search opens the fragment directly rather than
                // tapping the row, so the destination is reachable either way.
                //
                // No scroll key: this row's key names it on *this* screen, and the item points at
                // the screen it opens, where that key means nothing.
                add(items, seenItems, title, attrs, itemBreadcrumb, target, title, null);

                Integer targetXmlResId = SCREEN_RESOURCES.get(target);
                if (targetXmlResId != null) {
                    complete &= crawl(c, targetXmlResId, target, title,
                            breadcrumb.isEmpty() ? title : breadcrumb + BREADCRUMB_SEPARATOR + title,
                            items, visitedScreens, seenItems);
                }
            }
        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Failed to index preference screen " + xmlResId, e);
            complete = false;
        } finally {
            parser.close();
        }

        return complete;
    }

    /**
     * @param scrollKey key to scroll to on {@code fragmentClass}'s screen, or null when this row
     *                  does not live there -- see the navigation case in {@link #crawl}.
     */
    private static void add(List<SettingsSearchItem> items, Set<String> seenItems, String title,
                            PreferenceAttributes attrs, String breadcrumb,
                            Class<? extends Fragment> fragmentClass, String fragmentTitle,
                            @Nullable String scrollKey) {
        // app:key is a preference's real identity, but only within the screen that declares it,
        // hence pairing it with the breadcrumb. Keyless rows fall back to the title.
        String identity = breadcrumb + IDENTITY_SEPARATOR + (attrs.key != null ? attrs.key : title);
        if (!seenItems.add(identity)) {
            return;
        }
        items.add(new SettingsSearchItem(title, attrs.summary, breadcrumb, scrollKey,
                fragmentClass, fragmentTitle));
    }

    @Nullable
    private static Class<? extends Fragment> resolveFragment(String className) {
        try {
            return Class.forName(className).asSubclass(Fragment.class);
        } catch (ClassNotFoundException | ClassCastException e) {
            Log.w(TAG, "Cannot index destination " + className, e);
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Attributes
    // -------------------------------------------------------------------------

    /** The attributes of one preference element that the index cares about. */
    private static final class PreferenceAttributes {
        @Nullable
        String title;
        @Nullable
        String summary;
        @Nullable
        String key;
        @Nullable
        String fragment;
        boolean visible = true;
    }

    /**
     * Reads a preference element in a single pass. These files mix the android: and app:
     * namespaces for the same attribute, so match on the local name and take whichever
     * namespace carries it.
     */
    private static PreferenceAttributes readAttributes(Context c, XmlResourceParser parser) {
        PreferenceAttributes attrs = new PreferenceAttributes();
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String name = parser.getAttributeName(i);
            if (name == null) {
                continue;
            }
            switch (name) {
                case "title":
                    attrs.title = stringAttribute(c, parser, i);
                    break;
                case "summary":
                    String summary = stringAttribute(c, parser, i);
                    attrs.summary = summary == null || summary.isEmpty() ? null : summary;
                    break;
                case "key":
                    attrs.key = parser.getAttributeValue(i);
                    break;
                case "fragment":
                    attrs.fragment = parser.getAttributeValue(i);
                    break;
                case "isPreferenceVisible":
                    attrs.visible = parser.getAttributeBooleanValue(i, true);
                    break;
                default:
                    break;
            }
        }
        return attrs;
    }

    /** Resolves an attribute that may be either a string resource or a literal. */
    @Nullable
    private static String stringAttribute(Context c, XmlResourceParser parser, int index) {
        int resId = parser.getAttributeResourceValue(index, 0);
        return resId != 0 ? c.getString(resId) : parser.getAttributeValue(index);
    }
}
