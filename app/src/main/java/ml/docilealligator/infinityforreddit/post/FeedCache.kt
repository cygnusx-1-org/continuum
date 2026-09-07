package ml.docilealligator.infinityforreddit.post

import androidx.annotation.WorkerThread
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.Executors
import ml.docilealligator.infinityforreddit.Infinity
import ml.docilealligator.infinityforreddit.account.AccountScope
import ml.docilealligator.infinityforreddit.postfilter.PostFilter
import ml.docilealligator.infinityforreddit.readpost.ReadPostsListInterface
import ml.docilealligator.infinityforreddit.utils.JSONUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The posts a feed had loaded when the user last left it, so a resume can put them back without a
 * network call.
 *
 * Same trick as [ml.docilealligator.infinityforreddit.utils.SavedPostCache], for the same reason:
 * [Post] has no JSON serializer, so the bodies are kept as the raw t3 listing children and rebuilt
 * through [ParsePost.parsePostsSync] on load. The post filter and read-post metadata are therefore
 * re-applied at load time, and changing either needs no invalidation.
 *
 * Two things differ from that cache, both because this one backs a user-visible setting rather than
 * an optimisation:
 *
 * - **`filesDir`, not `cacheDir`.** Losing a cached saved list costs a refetch. Losing this one
 *   silently turns "Resume where I left off" off, so it does not live somewhere the OS reclaims.
 * - **No TTL.** A resume is valid however long the app was closed; staleness is the user's problem
 *   to fix with a pull-to-refresh, exactly as it is in Slide.
 *
 * Metadata (version, timestamp, `after` cursor) lives inside each entry's own file rather than in a
 * SharedPreferences index. A new prefs file would have to be registered in the settings
 * backup/restore plumbing, and a resume snapshot has no business travelling in a settings backup.
 */
object FeedCache {

    private const val VERSION = 1
    private const val CACHE_SUBDIR = "feed_cache"

    /**
     * Most posts kept for one feed. A raw t3 child runs 5-15 KB, so this is a few MB per entry --
     * enough for the four-or-so pages a long session actually accumulates.
     */
    private const val MAX_POSTS = 500

    /** Posts kept below the anchor when the window has to be trimmed. */
    private const val ANCHOR_TAIL_MARGIN = 50

    /** Total disk the whole cache may occupy before the least recently written entries go. */
    private const val MAX_TOTAL_BYTES = 16L * 1024 * 1024

    private const val KEY_VERSION = "version"
    private const val KEY_CACHED_AT = "cachedAt"
    private const val KEY_AFTER = "after"
    private const val KEY_LISTING = "listing"

    /**
     * The alternative body: posts already parsed, serialized as themselves.
     *
     * A feed normally caches the raw t3 children, so the post filter and read-post metadata are
     * re-applied on load and changing either needs no invalidation. That only works while the raw
     * children are still in hand, and they are not when the user turns the setting on mid-session:
     * the pages already fetched were parsed and their JSON discarded, so there is nothing raw left
     * to write. The parsed posts are still in memory, so they are written instead -- the filter is
     * applied to them on load all the same, just to a set already narrowed by whatever filter was
     * in force when they were fetched.
     */
    private const val KEY_POSTS = "posts"

    private val writeExecutor = Executors.newSingleThreadExecutor()

    /** Gson needs the element type spelled out to read a list back. */
    private val POST_LIST = object : TypeToken<List<Post>>() {}.type

    /**
     * A rebuilt feed. [afterToken] is the paging cursor to continue the listing from.
     *
     * [children] is the raw listing the posts were parsed from. The caller seeds its own
     * accumulator with it so that storing the cache again -- after a resume, having paged a little
     * further -- writes the whole feed rather than only the pages fetched since the resume.
     */
    class Cached(
        @JvmField val posts: ArrayList<Post>,
        @JvmField val children: JSONArray,
        @JvmField val afterToken: String?,
        @JvmField val cachedAt: Long,
    )

    private fun rootDir(): File? =
        Infinity.getAppContext()?.let { File(it.filesDir, CACHE_SUBDIR) }

    /**
     * Entries are filed under the account that made them, so one account can be forgotten without
     * touching another's. A flat directory could only be cleared wholesale, which made turning the
     * setting off for one account throw away every other account's cached posts too.
     */
    private fun accountDir(key: String): File? {
        val namespace = AccountScope.namespaceOf(key) ?: return null
        return rootDir()?.let { File(it, sanitize(namespace)) }
    }

    private fun cacheFile(key: String): File? = accountDir(key)?.let { File(it, fileName(key)) }

    private fun fileName(key: String): String = sanitize(key) + "_" + Integer.toHexString(key.hashCode()) + ".json"

    /** Filesystem-safe, and readable enough to recognise in a bug report. */
    private fun sanitize(name: String): String = name.replace(Regex("[^a-zA-Z0-9_]"), "_")

    /** The cache key a feed is stored under. */
    @JvmStatic
    fun key(accountName: String?, feedKey: String): String = AccountScope.key(accountName, feedKey)

    /** True if anything is stored for this key. Cheap: a file existence check, no parse. */
    @JvmStatic
    fun has(key: String): Boolean = cacheFile(key)?.exists() == true

    /**
     * Rebuild a cached feed. Reads and parses a file, so it MUST be called off the main thread.
     * Returns `null` on any miss -- absent, unreadable, wrong version, or malformed -- so a partial
     * read is never mistaken for the feed the user left.
     */
    @WorkerThread
    @JvmStatic
    fun load(
        key: String,
        postFilter: PostFilter?,
        readPostsList: ReadPostsListInterface?,
    ): Cached? {
        val file = cacheFile(key) ?: return null
        if (!file.exists()) {
            return null
        }
        val root =
            try {
                JSONObject(file.readText())
            } catch (e: JSONException) {
                null
            } catch (e: java.io.IOException) {
                null
            } ?: return null

        if (root.optInt(KEY_VERSION, 0) != VERSION) {
            // Discarded, never migrated: an older layout describes posts this build cannot rebuild.
            return null
        }
        val listing = root.optJSONObject(KEY_LISTING)
        val parsed: Collection<Post>
        val children: JSONArray
        if (listing != null) {
            parsed = ParsePost.parsePostsSync(listing, -1, postFilter, readPostsList) ?: return null
            children =
                listing.optJSONObject(JSONUtils.DATA_KEY)?.optJSONArray(JSONUtils.CHILDREN_KEY)
                    ?: JSONArray()
        } else {
            // No raw children were available when this was written. The filter still applies -- it
            // just has parsed posts to judge rather than listing entries. children stays empty,
            // which is what tells the caller it has nothing raw to accumulate onto, so the next
            // store writes this shape again rather than shrinking the feed to whatever arrived
            // since.
            parsed = readParsedPosts(root, postFilter, readPostsList) ?: return null
            children = JSONArray()
        }

        return Cached(
            ArrayList(parsed),
            children,
            // isNull first: optString on a JSON null hands back the four characters "null", which
            // would be sent to Reddit as a cursor and quietly restart the listing from the top.
            if (root.isNull(KEY_AFTER)) null else root.optString(KEY_AFTER, "").ifEmpty { null },
            root.optLong(KEY_CACHED_AT, 0L),
        )
    }

    /**
     * Persist [children] (the ordered, unfiltered t3 listing children) as this feed's cache, with
     * [afterToken] as the cursor the listing continues from.
     *
     * Trimming, serialization and the write all happen on a background executor, so this is cheap to
     * call from the paging thread. The caller must stop mutating [children] once it calls this.
     *
     * When there are more posts than [MAX_POSTS], the window kept is the one around
     * [anchorFullname] -- biased to keep what is ABOVE the anchor, because everything below it can
     * be fetched again from the cursor while everything above it cannot. Trimming the front shifts
     * every index, which is why a restore matches its anchor by fullname and treats the recorded
     * index as a fallback only; trimming the tail moves the stored cursor back to the last post
     * kept, so that paging on from the end of the window continues the listing rather than jumping
     * over the posts the trim dropped.
     */
    /**
     * Read back a [KEY_POSTS] body, dropping anything the current filter would not show and marking
     * what has been read since it was written.
     *
     * Both are what the raw path gets from [ParsePost], and a restored feed should not differ from a
     * fetched one in either: a post read in a later session would otherwise come back looking
     * unread, because the copy on disk remembers only how it looked when it was stored.
     */
    private fun readParsedPosts(
        root: JSONObject,
        postFilter: PostFilter?,
        readPostsList: ReadPostsListInterface?,
    ): List<Post>? {
        val array = root.optJSONArray(KEY_POSTS) ?: return null
        val posts =
            try {
                Gson().fromJson<List<Post>>(array.toString(), POST_LIST)
            } catch (e: RuntimeException) {
                // Every other way this file can be malformed returns null and is treated as a miss.
                // Gson is reflective and throws more than JsonSyntaxException, and this runs on the
                // paging executor inside the load, where a throw would surface as a failed feed
                // rather than as the cache miss it actually is.
                null
            } ?: return null
        val allowed = posts.filter { it != null && PostFilter.isPostAllowed(it, postFilter) }
        if (readPostsList != null) {
            val read = readPostsList.getReadPostsIdsByIds(allowed.map { it.id })
            allowed.forEach { if (read.contains(it.id)) it.markAsRead() }
        }
        return allowed
    }

    /**
     * Persist [posts] as this feed's cache, for a feed with no raw children left to write.
     *
     * Used when the setting is turned on mid-session: the pages already fetched were parsed and
     * their JSON discarded, so this is the only record of them there is. Trimmed on the same rule
     * as [store] -- everything above the anchor is what cannot be fetched again.
     */
    @JvmStatic
    @JvmOverloads
    fun storeParsed(
        key: String,
        posts: List<Post>,
        afterToken: String?,
        anchorFullname: String? = null,
    ) {
        val cachedAt = System.currentTimeMillis()
        val copy = ArrayList(posts)
        writeExecutor.execute { writeParsedNow(key, copy, afterToken, anchorFullname, cachedAt) }
    }

    @WorkerThread
    private fun writeParsedNow(
        key: String,
        posts: List<Post>,
        afterToken: String?,
        anchorFullname: String?,
        cachedAt: Long,
    ) {
        val dir = accountDir(key) ?: return
        if (!dir.exists() && !dir.mkdirs()) {
            return
        }
        val anchorIndex =
            if (anchorFullname.isNullOrEmpty()) -1 else posts.indexOfFirst { it.fullName == anchorFullname }
        val end =
            if (anchorIndex >= 0) minOf(posts.size, anchorIndex + ANCHOR_TAIL_MARGIN) else posts.size
        val window = posts.subList(maxOf(0, end - MAX_POSTS), end)
        // Always the last post actually stored, never the caller's own cursor.
        //
        // Two reasons, and the second is why a null token is not passed through. The posts come from
        // the adapter's snapshot, which lags the source by however long Paging's diff took, so
        // [afterToken] can already point past posts this window does not contain, and paging on from
        // it would step over them. And a null token here does not mean the end of the listing: this
        // shape is written for a feed whose pages were fetched before the setting was on, so the
        // cursor for them was never recorded. Treating that as the end left the restored feed unable
        // to page at all. Asking for the page after the last stored post costs one empty response at
        // a listing that really has ended, and Paging stops on it exactly as it would have.
        val cursor = window.lastOrNull()?.fullName ?: afterToken

        val body =
            try {
                JSONObject()
                    .apply {
                        put(KEY_VERSION, VERSION)
                        put(KEY_CACHED_AT, cachedAt)
                        put(KEY_AFTER, cursor ?: JSONObject.NULL)
                        put(KEY_POSTS, JSONArray(Gson().toJson(window)))
                    }
                    .toString()
            } catch (e: JSONException) {
                return
            } catch (e: RuntimeException) {
                // Gson is reflective; a post it cannot serialize costs this feed its cache, not the
                // write that is happening around it.
                return
            }

        try {
            File(dir, fileName(key)).writeText(body)
        } catch (e: java.io.IOException) {
            return
        }
        rootDir()?.let { evict(it) }
    }

    @JvmStatic
    @JvmOverloads
    fun store(
        key: String,
        children: JSONArray,
        afterToken: String?,
        anchorFullname: String? = null,
    ) {
        val cachedAt = System.currentTimeMillis()
        writeExecutor.execute { writeNow(key, children, afterToken, anchorFullname, cachedAt) }
    }

    @WorkerThread
    private fun writeNow(
        key: String,
        children: JSONArray,
        afterToken: String?,
        anchorFullname: String?,
        cachedAt: Long,
    ) {
        val dir = accountDir(key) ?: return
        if (!dir.exists() && !dir.mkdirs()) {
            return
        }
        val file = File(dir, fileName(key))

        val body =
            try {
                val window = trim(children, anchorFullname)
                // A trimmed tail leaves the caller's cursor pointing past posts that are no longer
                // stored, and resuming from it would silently skip every one of them -- the user
                // scrolls off the end of what came back and lands a hundred posts further down the
                // listing than they were. Reddit's cursor is a fullname, so the last post actually
                // kept is exactly the right one to continue from.
                val cursor =
                    if (window.length() == children.length()) {
                        afterToken
                    } else {
                        lastFullname(window) ?: afterToken
                    }
                val data =
                    JSONObject().apply {
                        put(JSONUtils.CHILDREN_KEY, window)
                        put(JSONUtils.AFTER_KEY, cursor ?: JSONObject.NULL)
                    }
                JSONObject()
                    .apply {
                        put(KEY_VERSION, VERSION)
                        put(KEY_CACHED_AT, cachedAt)
                        put(KEY_AFTER, cursor ?: JSONObject.NULL)
                        put(KEY_LISTING, JSONObject().put(JSONUtils.DATA_KEY, data))
                    }
                    .toString()
            } catch (e: JSONException) {
                return
            }

        try {
            file.writeText(body)
        } catch (e: java.io.IOException) {
            // A cache that could not be written just means no resume for this feed.
            return
        }
        rootDir()?.let { evict(it) }
    }

    /** The at-most-[MAX_POSTS] window of [children] to keep. See [store]. */
    private fun trim(children: JSONArray, anchorFullname: String?): JSONArray {
        if (children.length() <= MAX_POSTS) {
            return children
        }
        val anchorIndex = if (anchorFullname.isNullOrEmpty()) -1 else indexOf(children, anchorFullname)
        val end =
            if (anchorIndex >= 0) {
                minOf(children.length(), anchorIndex + ANCHOR_TAIL_MARGIN)
            } else {
                // No anchor to centre on: the tail is the better guess, since the user got there by
                // paging down.
                children.length()
            }
        val start = maxOf(0, end - MAX_POSTS)
        val window = JSONArray()
        for (i in start until end) {
            window.put(children.opt(i))
        }
        return window
    }

    /** The fullname of the last entry in [children], or null if it has none to give. */
    private fun lastFullname(children: JSONArray): String? {
        val data =
            children.optJSONObject(children.length() - 1)?.optJSONObject(JSONUtils.DATA_KEY)
                ?: return null
        return data.optString(JSONUtils.NAME_KEY).ifEmpty { null }
    }

    private fun indexOf(children: JSONArray, fullname: String): Int {
        for (i in 0 until children.length()) {
            val data = children.optJSONObject(i)?.optJSONObject(JSONUtils.DATA_KEY) ?: continue
            if (fullname == data.optString(JSONUtils.NAME_KEY)) {
                return i
            }
        }
        return -1
    }

    /**
     * Drop the least recently written entries until the whole cache is under [MAX_TOTAL_BYTES].
     * Budgeted across accounts rather than per account, because the budget is the device's disk.
     */
    @WorkerThread
    private fun evict(root: File) {
        val files = root.walkTopDown().filter { it.isFile }.toList()
        var total = files.sumOf { it.length() }
        if (total <= MAX_TOTAL_BYTES) {
            return
        }
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_TOTAL_BYTES) {
                return
            }
            val size = file.length()
            if (file.delete()) {
                total -= size
            }
        }
    }

    /** Forget one feed. */
    @JvmStatic
    fun clear(key: String) {
        writeExecutor.execute { cacheFile(key)?.delete() }
    }

    /**
     * Forget every feed belonging to [accountName], and nothing belonging to anyone else. Called
     * when that account turns the setting off, is logged out of, or is deleted.
     */
    @JvmStatic
    fun clearAccount(accountName: String?) {
        val dir = rootDir()?.let { File(it, sanitize(AccountScope.namespace(accountName))) } ?: return
        writeExecutor.execute { dir.deleteRecursively() }
    }

    /** Forget every feed of every account. */
    @JvmStatic
    fun clearAll() {
        writeExecutor.execute { rootDir()?.deleteRecursively() }
    }
}
