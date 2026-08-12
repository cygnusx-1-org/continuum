package ml.docilealligator.infinityforreddit.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.post.ParsePost;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.postfilter.PostFilter;
import ml.docilealligator.infinityforreddit.readpost.ReadPostsListInterface;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Hard-TTL cache for the Profile "Saved" tab (server-side saved posts). Continuum has no per-post
 * offline body store to reuse (unlike Slide's {@code OfflineSubreddit}) and {@link Post} has no
 * JSON serializer, so bodies are cached as the raw {@code /saved} listing JSON in the app cache dir
 * and rebuilt through the existing {@link ParsePost#parsePostsSync} on load. A small per-(account,
 * category) index plus freshness metadata lives in this class's own {@link SharedPreferences}.
 *
 * <p>Within {@link #SAVED_CACHE_TTL_MS} the cached list is served with no network call; past it the
 * caller refetches. In-app save/unsave and account switches call {@link #invalidate()}. The cache is
 * keyed by username so one account never sees another's saved items even before invalidation.
 */
public final class SavedPostCache {

    /** How long a cached saved list is trusted before a refetch is required. */
    public static final long SAVED_CACHE_TTL_MS = 30 * 60 * 1000L; // 30 minutes

    private static final String PREFS = "saved_post_cache";
    private static final String CACHE_SUBDIR = "saved_post_cache";
    private static final String SUFFIX_TIME = ".t"; // cachedAt, millis (long)
    private static final String SUFFIX_COMPLETE = ".c"; // whole saved list captured (boolean)

    private static final ExecutorService WRITE_EXECUTOR = Executors.newSingleThreadExecutor();

    // Bumped synchronously by invalidate() so a freshness check right after it returns false even
    // before the background prefs clear has run -- an entry stamped before this instant is stale.
    // Process-lifetime only; after a restart the persisted (cleared) timestamp governs.
    private static volatile long lastInvalidatedAt = 0L;

    private SavedPostCache() {
    }

    @Nullable
    private static SharedPreferences prefs() {
        Context c = Infinity.getAppContext();
        return c == null ? null : c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(String username, @Nullable String category) {
        return (username == null ? "" : username) + "|" + (category == null ? "" : category);
    }

    private static File cacheFile(Context context, String key) {
        File dir = new File(context.getCacheDir(), CACHE_SUBDIR);
        return new File(dir, sanitize(key) + ".json");
    }

    private static String sanitize(String key) {
        // Keep the name readable but filesystem-safe; the hash disambiguates otherwise-equal names.
        return key.replaceAll("[^a-zA-Z0-9_]", "_") + "_" + Integer.toHexString(key.hashCode());
    }

    /** Result of a successful {@link #load}. */
    public static class Cached {
        public final ArrayList<Post> posts;
        public final long cachedAt;
        public final boolean complete;

        Cached(ArrayList<Post> posts, long cachedAt, boolean complete) {
            this.posts = posts;
            this.cachedAt = cachedAt;
            this.complete = complete;
        }
    }

    /** True if a cache entry for this key exists and is still within the TTL. */
    public static boolean isFresh(String username, @Nullable String category) {
        SharedPreferences prefs = prefs();
        if (prefs == null) {
            return false;
        }
        long t = prefs.getLong(key(username, category) + SUFFIX_TIME, 0L);
        return t > 0 && t > lastInvalidatedAt && (System.currentTimeMillis() - t) < SAVED_CACHE_TTL_MS;
    }

    /**
     * Rebuild the cached saved list. Reads the cache file, so it MUST be called off the main thread.
     * Returns {@code null} on a miss -- including when the body file is gone (the OS can clear the
     * cache dir), so a partial list is never mistaken for the complete one. The current
     * {@code postFilter} is re-applied on load, so filter changes need no invalidation.
     */
    @WorkerThread
    @Nullable
    public static Cached load(String username, @Nullable String category, PostFilter postFilter,
                              @Nullable ReadPostsListInterface readPostsList) {
        Context context = Infinity.getAppContext();
        SharedPreferences prefs = prefs();
        if (context == null || prefs == null) {
            return null;
        }
        String k = key(username, category);
        long t = prefs.getLong(k + SUFFIX_TIME, 0L);
        if (t <= 0) {
            return null;
        }
        boolean complete = prefs.getBoolean(k + SUFFIX_COMPLETE, false);

        File file = cacheFile(context, k);
        if (!file.exists()) {
            return null; // the body file is missing -> treat the whole cache as a miss
        }
        String json = readTextFromFile(file);
        if (json == null || json.isEmpty()) {
            return null;
        }
        java.util.LinkedHashSet<Post> parsed = ParsePost.parsePostsSync(json, -1, postFilter, readPostsList);
        if (parsed == null) {
            return null;
        }
        ArrayList<Post> posts = new ArrayList<>(parsed.size());
        for (Post p : parsed) {
            p.setSaved(true);
            posts.add(p);
        }
        return new Cached(posts, t, complete);
    }

    /**
     * Persist {@code children} (the ordered, unfiltered t3 listing children) as the saved cache for
     * this key, stamping "now". Both the JSON serialization and the file write run on a background
     * executor, so this is safe (and cheap) to call from the main thread or the paging thread.
     * {@code complete} records whether the entire saved list was captured (required before search /
     * instant-open trust the cache).
     */
    public static void store(String username, @Nullable String category, JSONArray children, boolean complete) {
        if (children == null) {
            return;
        }
        final String k = key(username, category);
        // Stamp the moment the snapshot was taken, not when the background write lands, so that an
        // invalidate() racing this store correctly wins: a cachedAt captured before the invalidate is
        // < lastInvalidatedAt, so isFresh() rejects it (see the guard there). The source stops mutating
        // `children` once it calls store() (walk done / nextKey null), so serializing off-thread is safe.
        final long cachedAt = System.currentTimeMillis();
        WRITE_EXECUTOR.execute(() -> writeNow(k, children, complete, cachedAt));
    }

    @WorkerThread
    private static void writeNow(String k, JSONArray children, boolean complete, long cachedAt) {
        Context context = Infinity.getAppContext();
        SharedPreferences prefs = prefs();
        if (context == null || prefs == null) {
            return;
        }
        final String body;
        try {
            JSONObject data = new JSONObject();
            data.put(JSONUtils.CHILDREN_KEY, children);
            data.put(JSONUtils.AFTER_KEY, JSONObject.NULL);
            JSONObject listing = new JSONObject();
            listing.put(JSONUtils.DATA_KEY, data);
            body = listing.toString();
        } catch (JSONException e) {
            return;
        }
        File dir = new File(context.getCacheDir(), CACHE_SUBDIR);
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        if (!writeTextToFile(body, cacheFile(context, k))) {
            return;
        }
        prefs.edit()
                .putBoolean(k + SUFFIX_COMPLETE, complete)
                .putLong(k + SUFFIX_TIME, cachedAt)
                .apply();
    }

    @WorkerThread
    @Nullable
    private static String readTextFromFile(File file) {
        StringBuilder sb = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            return sb.toString();
        } catch (java.io.IOException e) {
            return null;
        }
    }

    @WorkerThread
    private static boolean writeTextToFile(String text, File file) {
        try (java.io.Writer writer = new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write(text);
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    /**
     * Drop every cached saved index and its body files. Safe to call from any thread, including the
     * main thread: only a volatile timestamp is set synchronously (which makes {@link #isFresh}
     * return false immediately), while the prefs clear and file deletion are done off-thread.
     */
    public static void invalidate() {
        lastInvalidatedAt = System.currentTimeMillis();
        WRITE_EXECUTOR.execute(() -> {
            SharedPreferences prefs = prefs();
            if (prefs != null) {
                prefs.edit().clear().apply();
            }
            Context context = Infinity.getAppContext();
            if (context == null) {
                return;
            }
            File dir = new File(context.getCacheDir(), CACHE_SUBDIR);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        });
    }
}
