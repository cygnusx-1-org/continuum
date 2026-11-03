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
 * The last few subreddits an account opened, newest first, for the navigation drawer's Recents
 * section. Unlike the Recently Visited list this is always on, keeps subscribed subreddits too,
 * and lives in one preference so it needs no database change.
 */
public final class RecentSubreddits {
    public static final int LIMIT = 5;

    private static final String KEY_BASE = "_recent_subreddits";
    private static final String NAME = "name";
    private static final String ICON_URL = "icon_url";
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

        Entry(String name, @Nullable String iconUrl) {
            this.name = name;
            this.iconUrl = iconUrl;
        }
    }

    public static List<Entry> get(SharedPreferences prefs, String accountName) {
        List<Entry> entries = new ArrayList<>();
        String json = prefs.getString(AccountScope.key(accountName, KEY_BASE), null);
        if (json == null) return entries;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length() && entries.size() < LIMIT; i++) {
                JSONObject object = array.getJSONObject(i);
                entries.add(new Entry(object.getString(NAME), object.optString(ICON_URL, null)));
            }
        } catch (JSONException ignored) {
            // A damaged value reads as empty and is replaced by the next visit.
        }
        return entries;
    }

    public static void record(SharedPreferences prefs, String accountName, String subredditName,
                              @Nullable String iconUrl) {
        if (subredditName == null || subredditName.isEmpty()
                || PSEUDO_SUBREDDITS.contains(subredditName.toLowerCase(Locale.US))
                || subredditName.startsWith("u_")) {
            return;
        }
        List<Entry> entries = get(prefs, accountName);
        entries.removeIf(entry -> entry.name.equalsIgnoreCase(subredditName));
        entries.add(0, new Entry(subredditName, iconUrl));

        JSONArray array = new JSONArray();
        try {
            for (int i = 0; i < entries.size() && i < LIMIT; i++) {
                array.put(new JSONObject()
                        .put(NAME, entries.get(i).name)
                        .put(ICON_URL, entries.get(i).iconUrl == null ? "" : entries.get(i).iconUrl));
            }
        } catch (JSONException ex) {
            return;
        }
        prefs.edit().putString(AccountScope.key(accountName, KEY_BASE), array.toString()).apply();
    }
}
