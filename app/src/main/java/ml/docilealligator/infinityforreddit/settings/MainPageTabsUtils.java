package ml.docilealligator.infinityforreddit.settings;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ml.docilealligator.infinityforreddit.Constants;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.multireddit.MultiReddit;
import ml.docilealligator.infinityforreddit.subscribedsubreddit.SubscribedSubredditData;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

/**
 * Load/save/migrate/merge the main page's ordered tab list — a {@code List<MainPageTabInput>}
 * stored as JSON in the {@code main_activity_tabs} SharedPreferences under
 * {@link SharedPreferencesUtils#MAIN_PAGE_TABS_ORDER}.
 *
 * <p>The list holds user-added tabs plus the individual items pulled in by the "Show ..." toggles
 * (each subscribed subreddit / multireddit is its own draggable entry, tagged with the source
 * toggle it came from). {@link #merge} reconciles a saved order against the current live toggle
 * lists: it keeps the user's arrangement, drops items whose toggle is off or that no longer exist,
 * and appends newly-available items. The legacy {@code MAIN_PAGE_TAB_N_*} keys are read once to
 * migrate the old configuration.
 */
public class MainPageTabsUtils {
    private static final Gson gson = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<MainPageTabInput>>() {
    }.getType();

    /** Group sources in the order they were historically appended after the fixed tabs. */
    public static final int[] GROUP_SOURCES = {
            SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_FAVORITE_MULTIREDDITS,
            SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_MULTIREDDITS,
            SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_FAVORITE_SUBSCRIBED_SUBREDDITS,
            SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_SUBSCRIBED_SUBREDDITS,
            SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_FAVORITE_USERS_MULTIREDDITS,
            SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_USERS_MULTIREDDITS,
    };

    private static String prefix(String accountName) {
        return accountName.equals(Account.ANONYMOUS_ACCOUNT) ? "" : accountName;
    }

    public static String toggleKeyForGroupSource(int source) {
        switch (source) {
            case SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_FAVORITE_MULTIREDDITS:
                return SharedPreferencesUtils.MAIN_PAGE_SHOW_FAVORITE_MULTIREDDITS;
            case SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_MULTIREDDITS:
                return SharedPreferencesUtils.MAIN_PAGE_SHOW_MULTIREDDITS;
            case SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_FAVORITE_SUBSCRIBED_SUBREDDITS:
                return SharedPreferencesUtils.MAIN_PAGE_SHOW_FAVORITE_SUBSCRIBED_SUBREDDITS;
            case SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_SUBSCRIBED_SUBREDDITS:
                return SharedPreferencesUtils.MAIN_PAGE_SHOW_SUBSCRIBED_SUBREDDITS;
            case SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_FAVORITE_USERS_MULTIREDDITS:
                return SharedPreferencesUtils.MAIN_PAGE_SHOW_FAVORITE_USERS_MULTIREDDITS;
            case SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_USERS_MULTIREDDITS:
                return SharedPreferencesUtils.MAIN_PAGE_SHOW_USERS_MULTIREDDITS;
            default:
                return null;
        }
    }

    public static boolean isGroupToggleEnabled(SharedPreferences prefs, String accountName, int source) {
        String key = toggleKeyForGroupSource(source);
        return key != null && prefs.getBoolean(prefix(accountName) + key, false);
    }

    public static Set<Integer> enabledSources(SharedPreferences prefs, String accountName) {
        Set<Integer> enabled = new HashSet<>();
        for (int source : GROUP_SOURCES) {
            if (isGroupToggleEnabled(prefs, accountName, source)) {
                enabled.add(source);
            }
        }
        return enabled;
    }

    /**
     * Load the saved order, migrating from the legacy keys on first run. Does NOT reconcile against
     * the live toggle lists — call {@link #merge} with the current toggle data for that.
     */
    public static List<MainPageTabInput> load(SharedPreferences prefs, String accountName) {
        String json = prefs.getString(prefix(accountName) + SharedPreferencesUtils.MAIN_PAGE_TABS_ORDER, null);
        if (json == null) {
            List<MainPageTabInput> migrated = migrateFromLegacy(prefs, accountName);
            save(prefs, accountName, migrated);
            return migrated;
        }
        List<MainPageTabInput> tabs = null;
        try {
            tabs = gson.fromJson(json, LIST_TYPE);
        } catch (Exception ignored) {
        }
        return tabs == null ? defaultTabs() : tabs;
    }

    public static void save(SharedPreferences prefs, String accountName, List<MainPageTabInput> tabs) {
        prefs.edit().putString(prefix(accountName) + SharedPreferencesUtils.MAIN_PAGE_TABS_ORDER, gson.toJson(tabs)).apply();
    }

    private static List<MainPageTabInput> defaultTabs() {
        List<MainPageTabInput> tabs = new ArrayList<>();
        tabs.add(new MainPageTabInput(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_HOME, "", SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_USER));
        tabs.add(new MainPageTabInput(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_POPULAR, "", SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_USER));
        tabs.add(new MainPageTabInput(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_ALL, "", SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_USER));
        return tabs;
    }

    private static List<MainPageTabInput> migrateFromLegacy(SharedPreferences prefs, String accountName) {
        String p = prefix(accountName);
        if (!prefs.contains(p + SharedPreferencesUtils.MAIN_PAGE_TAB_COUNT)
                && !prefs.contains(p + SharedPreferencesUtils.MAIN_PAGE_TAB_1_POST_TYPE)) {
            return defaultTabs();
        }

        String[] postTypeKeys = {
                SharedPreferencesUtils.MAIN_PAGE_TAB_1_POST_TYPE, SharedPreferencesUtils.MAIN_PAGE_TAB_2_POST_TYPE,
                SharedPreferencesUtils.MAIN_PAGE_TAB_3_POST_TYPE, SharedPreferencesUtils.MAIN_PAGE_TAB_4_POST_TYPE,
                SharedPreferencesUtils.MAIN_PAGE_TAB_5_POST_TYPE, SharedPreferencesUtils.MAIN_PAGE_TAB_6_POST_TYPE,
        };
        String[] nameKeys = {
                SharedPreferencesUtils.MAIN_PAGE_TAB_1_NAME, SharedPreferencesUtils.MAIN_PAGE_TAB_2_NAME,
                SharedPreferencesUtils.MAIN_PAGE_TAB_3_NAME, SharedPreferencesUtils.MAIN_PAGE_TAB_4_NAME,
                SharedPreferencesUtils.MAIN_PAGE_TAB_5_NAME, SharedPreferencesUtils.MAIN_PAGE_TAB_6_NAME,
        };
        int[] defaultTypes = {
                SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_HOME, SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_POPULAR,
                SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_ALL, SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_ALL,
                SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_ALL, SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_ALL,
        };

        int tabCount = prefs.getInt(p + SharedPreferencesUtils.MAIN_PAGE_TAB_COUNT, Constants.DEFAULT_TAB_COUNT);
        List<MainPageTabInput> tabs = new ArrayList<>();
        for (int i = 0; i < tabCount && i < postTypeKeys.length; i++) {
            int postType = prefs.getInt(p + postTypeKeys[i], defaultTypes[i]);
            String name = prefs.getString(p + nameKeys[i], "");
            tabs.add(new MainPageTabInput(postType, name, SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_USER));
        }
        return tabs;
    }

    /**
     * Reconcile a saved order against the current live toggle items.
     *
     * @param order        the saved order (user tabs + previously-materialized toggle items)
     * @param liveBySource for each source whose live data has loaded, the current items (already
     *                     favorite-filtered); a source absent from the map is treated as not-yet-loaded
     * @param enabled      the currently-enabled toggle sources
     * @return a new list: the saved arrangement with disabled/stale toggle items removed, duplicates
     * collapsed, and newly-available toggle items appended per source
     */
    public static List<MainPageTabInput> merge(List<MainPageTabInput> order,
                                               Map<Integer, List<MainPageTabInput>> liveBySource,
                                               Set<Integer> enabled) {
        List<MainPageTabInput> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (MainPageTabInput e : order) {
            String key = userKey(e.postType, e.name);
            if (!e.isGroup()) {
                if (seen.add(key)) {
                    out.add(e);
                }
            } else if (enabled.contains(e.source)) {
                List<MainPageTabInput> live = liveBySource.get(e.source);
                if (live == null) {
                    // Enabled but not loaded yet — keep the saved entry (its name/label are stored).
                    if (seen.add(key)) {
                        out.add(e);
                    }
                } else if (containsKey(live, key) && seen.add(key)) {
                    out.add(e);
                }
            }
            // Disabled toggle source — drop.
        }
        // Append items that are live now but not already in the saved order.
        for (int source : GROUP_SOURCES) {
            List<MainPageTabInput> live = liveBySource.get(source);
            if (live != null) {
                for (MainPageTabInput item : live) {
                    if (seen.add(userKey(item.postType, item.name))) {
                        out.add(item);
                    }
                }
            }
        }
        return out;
    }

    private static boolean containsKey(List<MainPageTabInput> list, String key) {
        for (MainPageTabInput item : list) {
            if (userKey(item.postType, item.name).equals(key)) {
                return true;
            }
        }
        return false;
    }

    public static List<MainPageTabInput> fromMultiReddits(List<MultiReddit> multiReddits, int source) {
        List<MainPageTabInput> out = new ArrayList<>();
        if (multiReddits != null) {
            for (MultiReddit m : multiReddits) {
                MainPageTabInput t = new MainPageTabInput(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT, m.getPath(), source);
                t.displayName = m.getDisplayName();
                out.add(t);
            }
        }
        return out;
    }

    public static List<MainPageTabInput> fromSubreddits(List<SubscribedSubredditData> subreddits, int source) {
        List<MainPageTabInput> out = new ArrayList<>();
        if (subreddits != null) {
            for (SubscribedSubredditData s : subreddits) {
                MainPageTabInput t = new MainPageTabInput(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT, s.getName(), source);
                t.displayName = s.getName();
                out.add(t);
            }
        }
        return out;
    }

    /** Favorites are surfaced by the "Show Favorite ..." toggles, so keep them out of the
     *  non-favorite sections to avoid duplicate tabs (mirrors MainActivity's filtering). */
    public static List<MultiReddit> excludeFavoriteMultiReddits(List<MultiReddit> multiReddits) {
        List<MultiReddit> result = new ArrayList<>();
        if (multiReddits != null) {
            for (MultiReddit multiReddit : multiReddits) {
                if (!multiReddit.isFavorite()) {
                    result.add(multiReddit);
                }
            }
        }
        return result;
    }

    public static List<SubscribedSubredditData> excludeFavoriteSubscribedSubreddits(List<SubscribedSubredditData> subreddits) {
        List<SubscribedSubredditData> result = new ArrayList<>();
        if (subreddits != null) {
            for (SubscribedSubredditData subreddit : subreddits) {
                if (!subreddit.isFavorite()) {
                    result.add(subreddit);
                }
            }
        }
        return result;
    }

    /** Remove all entries that came from the given toggle source. Returns true if anything changed. */
    public static boolean removeSource(List<MainPageTabInput> tabs, int source) {
        boolean changed = false;
        for (int i = tabs.size() - 1; i >= 0; i--) {
            if (tabs.get(i).source == source) {
                tabs.remove(i);
                changed = true;
            }
        }
        return changed;
    }

    public static boolean isNameBasedType(int postType) {
        return postType == SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT
                || postType == SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT
                || postType == SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_USER;
    }

    /** Dedup key for a tab: type + case-insensitive name. Name-based types key on their name; the
     *  rest key on the type alone (only one Home / Popular / All / ... allowed). */
    public static String userKey(int postType, String name) {
        String n = isNameBasedType(postType) && name != null ? name.toLowerCase() : "";
        return postType + "|" + n;
    }

    /**
     * Friendly label for a "User MultiReddit" whose path is {@code /user/<username>/m/<name>}:
     * "username name". Falls back to the raw path if it can't be parsed.
     */
    public static String userMultiRedditDisplayName(String path) {
        if (path == null) {
            return "";
        }
        String[] parts = path.split("/");
        String username = null;
        String multiName = null;
        for (int i = 0; i + 1 < parts.length; i++) {
            if ((parts[i].equals("user") || parts[i].equals("u")) && username == null) {
                username = parts[i + 1];
            } else if (parts[i].equals("m")) {
                multiName = parts[i + 1];
            }
        }
        if (username != null && !username.isEmpty() && multiName != null && !multiName.isEmpty()) {
            return username + " " + multiName;
        }
        return path;
    }

    /** True if a user tab with the same type (and, for name-based types, the same name) already exists. */
    public static boolean isDuplicate(List<MainPageTabInput> tabs, int postType, String name) {
        String key = userKey(postType, name);
        for (MainPageTabInput tab : tabs) {
            if (!tab.isGroup() && userKey(tab.postType, tab.name).equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** Human-readable label for a list row (also the main page tab title). */
    public static String getTabLabel(Context context, MainPageTabInput tab) {
        if (tab.displayName != null && !tab.displayName.isEmpty()) {
            return tab.displayName;
        }
        switch (tab.postType) {
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_HOME:
                return context.getString(R.string.home);
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_POPULAR:
                return context.getString(R.string.popular);
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_ALL:
                return context.getString(R.string.all);
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_UPVOTED:
                return context.getString(R.string.upvoted);
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_DOWNVOTED:
                return context.getString(R.string.downvoted);
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_HIDDEN:
                return context.getString(R.string.hidden);
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SAVED:
                return context.getString(R.string.saved_posts);
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SAVED_COMMENTS:
                return context.getString(R.string.saved_comments);
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT:
                return tab.name != null && !tab.name.isEmpty() ? tab.name : context.getString(R.string.subreddit);
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT:
                return tab.name != null && !tab.name.isEmpty() ? tab.name : context.getString(R.string.multi_reddit);
            case SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_USER:
                return tab.name != null && !tab.name.isEmpty() ? tab.name : context.getString(R.string.user);
            default:
                return context.getString(R.string.popular);
        }
    }
}
