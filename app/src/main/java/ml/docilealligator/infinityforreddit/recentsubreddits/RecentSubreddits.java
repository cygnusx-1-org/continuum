package ml.docilealligator.infinityforreddit.recentsubreddits;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import ml.docilealligator.infinityforreddit.account.AccountScope;

/**
 * The subreddits and users an account opened, newest first, for the navigation drawer's Recents
 * section. Unlike the Recently Visited list this is always on, keeps subscribed subreddits too,
 * and lives in one preference so it needs no database change.
 *
 * <p>Pinned entries sit above the rest and stay until they are unpinned or dismissed. The
 * unpinned ones are capped at {@link #STORE_LIMIT}; how many of them the drawer shows is the
 * user's setting, applied by {@link #visible}.
 */
public final class RecentSubreddits {
    public static final int DEFAULT_LIMIT = 5;
    public static final int STORE_LIMIT = 15;
    public static final int TYPE_SUBREDDIT = 0;
    public static final int TYPE_USER = 1;

    private static final String KEY_BASE = "_recent_subreddits";
    private static final String NAME = "name";
    private static final String ICON_URL = "icon_url";
    private static final String TYPE = "type";
    private static final String PINNED = "pinned";
    // Reddit's own listing endpoints dressed up as subreddits, not communities.
    private static final Set<String> PSEUDO_SUBREDDITS = new HashSet<>(
            Arrays.asList("popular", "all", "random", "randnsfw", "mod", "friends"));

    private RecentSubreddits() {
        // Prevent instantiation
    }

    public static final class Entry {
        public final String name;
        @Nullable
        public final String iconUrl;
        public final int type;
        public final boolean pinned;

        Entry(String name, @Nullable String iconUrl, int type, boolean pinned) {
            this.name = name;
            this.iconUrl = iconUrl;
            this.type = type;
            this.pinned = pinned;
        }

        boolean is(String otherName, int otherType) {
            return type == otherType && name.equalsIgnoreCase(otherName);
        }
    }

    /**
     * Everything stored: the pinned entries first, then the rest newest first.
     */
    public static List<Entry> get(SharedPreferences prefs, String accountName) {
        List<Entry> entries = new ArrayList<>();
        String json = prefs.getString(AccountScope.key(accountName, KEY_BASE), null);
        if (json == null) return entries;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                entries.add(new Entry(object.getString(NAME), object.optString(ICON_URL, null),
                        object.optInt(TYPE, TYPE_SUBREDDIT), object.optBoolean(PINNED, false)));
            }
        } catch (JSONException ignored) {
            // A damaged value reads as empty and is replaced by the next visit.
        }
        return entries;
    }

    /**
     * What the drawer shows: every pinned entry, then the first {@code limit} of the others.
     */
    public static List<Entry> visible(List<Entry> all, int limit) {
        List<Entry> shown = new ArrayList<>();
        for (Entry entry : all) {
            if (entry.pinned) shown.add(entry);
        }
        int others = 0;
        for (Entry entry : all) {
            if (!entry.pinned && others++ < limit) shown.add(entry);
        }
        return shown;
    }

    public static void record(SharedPreferences prefs, String accountName, String subredditName,
                              @Nullable String iconUrl) {
        if (subredditName == null || subredditName.isEmpty()
                || PSEUDO_SUBREDDITS.contains(subredditName.toLowerCase(Locale.US))
                || subredditName.startsWith("u_")) {
            return;
        }
        recordEntry(prefs, accountName, subredditName, iconUrl, TYPE_SUBREDDIT);
    }

    /**
     * Your own profile is left out, since opening it is not a place to go back to.
     */
    public static void recordUser(SharedPreferences prefs, String accountName, String username,
                                  @Nullable String iconUrl) {
        if (username == null || username.isEmpty() || username.equalsIgnoreCase(accountName)) {
            return;
        }
        recordEntry(prefs, accountName, username, iconUrl, TYPE_USER);
    }

    public static void remove(SharedPreferences prefs, String accountName, String name, int type) {
        List<Entry> entries = get(prefs, accountName);
        entries.removeIf(entry -> entry.is(name, type));
        save(prefs, accountName, entries);
    }

    /**
     * A newly pinned entry goes to the end of the pinned ones; an unpinned one goes back to the
     * front of the rest.
     */
    public static void setPinned(SharedPreferences prefs, String accountName, String name, int type,
                                 boolean pinned) {
        List<Entry> entries = get(prefs, accountName);
        Entry target = null;
        for (Entry entry : entries) {
            if (entry.is(name, type)) target = entry;
        }
        if (target == null || target.pinned == pinned) return;
        entries.remove(target);
        entries.add(pinnedCount(entries), new Entry(target.name, target.iconUrl, target.type, pinned));
        save(prefs, accountName, entries);
    }

    private static void recordEntry(SharedPreferences prefs, String accountName, String name,
                                    @Nullable String iconUrl, int type) {
        List<Entry> entries = get(prefs, accountName);
        int existing = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).is(name, type)) existing = i;
        }
        if (existing >= 0 && entries.get(existing).pinned) {
            // Already kept at the top: only the icon can have changed.
            entries.set(existing, new Entry(name, iconUrl, type, true));
        } else {
            if (existing >= 0) entries.remove(existing);
            entries.add(pinnedCount(entries), new Entry(name, iconUrl, type, false));
        }

        int others = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (!entries.get(i).pinned && ++others > STORE_LIMIT) {
                entries.remove(i--);
            }
        }
        save(prefs, accountName, entries);
    }

    private static int pinnedCount(List<Entry> entries) {
        int count = 0;
        for (Entry entry : entries) {
            if (entry.pinned) count++;
        }
        return count;
    }

    private static void save(SharedPreferences prefs, String accountName, List<Entry> entries) {
        JSONArray array = new JSONArray();
        try {
            for (Entry entry : entries) {
                array.put(new JSONObject()
                        .put(NAME, entry.name)
                        .put(ICON_URL, entry.iconUrl == null ? "" : entry.iconUrl)
                        .put(TYPE, entry.type)
                        .put(PINNED, entry.pinned));
            }
        } catch (JSONException ex) {
            return;
        }
        prefs.edit().putString(AccountScope.key(accountName, KEY_BASE), array.toString()).apply();
    }
}
