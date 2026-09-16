package ml.docilealligator.infinityforreddit.post

import android.os.Handler
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import ml.docilealligator.infinityforreddit.utils.HtmlBodyUtils
import ml.docilealligator.infinityforreddit.utils.ImageHostUtils
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The network half of [ImageHostUtils]: fetches an imgchest or imgbb landing page and scrapes the
 * images out of it.
 *
 * Scraping rather than an API, for both hosts, and not by preference:
 *
 *  - imgchest's documented API (`api.imgchest.com/v1/post/<id>`) answers `302 -> /login` without a
 *    token, and the token is per-user. There is no anonymous read.
 *  - imgbb's API is upload-only and also keyed.
 *
 * So the page is what there is. Both hosts sit behind the same two-tier extraction: a precise route
 * that yields every image, and `og:image` as the fallback that always yields the first one.
 *
 * Callers must run this off the main thread — it performs a synchronous HTTP round trip. These are
 * third-party pages whose markup can change any day, so treat a null return as expected rather than
 * exceptional. Unlike [FetchShortClipVideo] it has no feed-side caller to demote, because a card
 * draws Reddit's own preview and only opening the album needs the real images:
 * `ViewImgurMediaActivity` answers a null with its retry view, and `DownloadMediaService` with a
 * download error.
 *
 * Results are cached for [CACHE_TTL_MILLIS], which is what makes the feed's `1/N` badge affordable:
 * a card that scrolls off and back does not re-scrape, and the album the user then opens is already
 * resolved, so the viewer shows it without a second round trip.
 */
object FetchImageHostMedia {

    /**
     * How long a scrape is trusted. Albums are effectively immutable once posted, and the cost of
     * being wrong is one stale tile list until this expires, so this is long enough to be worth
     * keeping across restarts -- which is the whole point of persisting it.
     */
    private const val CACHE_TTL_MILLIS = 24L * 60L * 60L * 1000L

    /** Where the resolved albums are kept between runs. See [init]. */
    private const val CACHE_FILE_NAME = "image_host_albums.json"
    private const val KEY_STORED_AT = "t"
    private const val KEY_LINKS = "l"

    @Volatile
    private var storeDir: File? = null

    @Volatile
    private var loaded = false

    @Volatile
    private var dirty = false

    /**
     * Where to keep resolved albums between runs; called once from the application.
     *
     * Without this the cache dies with the process, and that is exactly when it is needed most. A
     * post is re-parsed from scratch on a cold start, so an album that is not remembered comes back
     * as the single-tile placeholder it was seeded with: the card binds on image one whatever page
     * the user was on, the carousel has one tile and cannot be swiped, and when the scrape lands a
     * beat later the picture shifts under them. Remembering the album turns all of that into a post
     * that is simply a gallery from its first frame.
     */
    @JvmStatic
    fun init(filesDir: File) {
        storeDir = filesDir
    }

    /**
     * Entries are only evicted when they are read, so a long session that scrolls past thousands of
     * albums would otherwise keep every one. Small because the cache exists to survive a row being
     * recycled and rebound, not to remember a whole session.
     */
    private const val MAX_CACHE_ENTRIES = 256

    /** A resolved album; [media] is null for one that could not be read. */
    private data class CacheEntry(val media: ArrayList<ImgurMedia>?, val storedAtMillis: Long)

    /** Keyed by page URL, which is what addresses an album on both hosts. */
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /**
     * Page URLs a prefetch is already reading, so several rows asking for the same album at once
     * read the page once between them.
     */
    private val prefetching = ConcurrentHashMap.newKeySet<String>()

    /** Hands a resolved album to a feed or post-detail row. Never called for one that failed. */
    fun interface AlbumListener {
        fun onAlbum(media: List<ImgurMedia>)
    }

    /**
     * Cancels the request the scrape is currently blocked on, and stops it starting another.
     *
     * A feed scrolls faster than a page loads, so without this a fling leaves a live request behind
     * every row it passed.
     */
    class Cancellable {
        private val cancelled = AtomicBoolean(false)

        @Volatile
        private var call: Call? = null

        fun isCanceled(): Boolean = cancelled.get()

        fun cancel() {
            cancelled.set(true)
            call?.cancel()
        }

        internal fun track(newCall: Call): Call? {
            if (cancelled.get()) {
                newCall.cancel()
                return null
            }
            call = newCall
            return newCall
        }
    }

    /**
     * imgchest renders through Inertia.js, which serialises the entire page model into one
     * HTML-escaped JSON attribute on the root div. `props.post.files` is the ordered image list, so
     * this is the only route that sees past the first image of an album — `og:image` carries just
     * the cover. Verified against a 20-image album (`imgchest.com/p/n87wl2angyx`) on 2026-09-16.
     */
    private val INERTIA_DATA_PAGE = Regex("data-page=\"([^\"]*)\"")

    /**
     * Deliberately not lazy and bounded to the tag: an unanchored lazy quantifier over a whole
     * document is the shape that took ~55 seconds on a hostile page, per the warning on
     * [HtmlBodyUtils].
     */
    private val OG_IMAGE = Regex(
        "<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']",
        RegexOption.IGNORE_CASE
    )

    /**
     * Same tag with the attributes the other way round. imgbb emits `property` first, but neither
     * host guarantees the order and swapping it would silently cost the fallback.
     */
    private val OG_IMAGE_REVERSED = Regex(
        "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']",
        RegexOption.IGNORE_CASE
    )

    /**
     * Both hosts serve their pages happily to the app's own User-Agent, but they front them with
     * the same CDNs the clip hosts use, so a browser-shaped agent is the safer default here too.
     * Set explicitly per request, which wins over the base client's interceptor.
     */
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/127.0.0.0 Mobile Safari/537.36"

    /**
     * The images behind [pageUrl], in album order, or null when the page could not be read or
     * carried none.
     *
     * The returned media are typed [ImgurMedia.TYPE_IMAGE] unconditionally. Neither host is a video
     * host, and `ViewImgurMediaActivity` keys its per-page fragment off that type, so mistyping one
     * would hand a still image to the video fragment.
     */
    @JvmStatic
    @JvmOverloads
    @WorkerThread
    fun fetchSync(
        okHttpClient: OkHttpClient,
        host: ImageHostUtils.Host,
        pageUrl: String,
        cancellable: Cancellable = Cancellable()
    ): ArrayList<ImgurMedia>? {
        ensureLoaded()
        cached(pageUrl)?.let { return it.media }

        val resolved = resolve(okHttpClient, host, pageUrl, cancellable)

        // A cancelled scrape says nothing about the album, so it must not be remembered as one that
        // failed -- the next bind of that card would show no badge and never try again.
        if (!cancellable.isCanceled()) {
            if (cache.size >= MAX_CACHE_ENTRIES) {
                evict()
            }
            cache[pageUrl] = CacheEntry(resolved, System.currentTimeMillis())
            // Only a real album is worth keeping: a page that could not be read says nothing about
            // the album, and remembering the failure for a day would keep a card as a placeholder
            // long after the host recovered.
            if (resolved != null && resolved.size > 1) {
                scheduleSave()
            }
        }
        return resolved
    }

    /**
     * Read the stored albums, once, on whichever background thread asks first.
     *
     * Parsing and prefetching both run off the main thread, and a resolved album is only ever read
     * through one of the two entry points below, so there is no path from the main thread into this.
     */
    private fun ensureLoaded() {
        if (loaded) {
            return
        }
        synchronized(this) {
            if (loaded) {
                return
            }
            loaded = true
            val file = File(storeDir ?: return, CACHE_FILE_NAME)
            if (!file.exists()) {
                return
            }
            try {
                val root = JSONObject(file.readText())
                val now = System.currentTimeMillis()
                for (url in root.keys()) {
                    val entry = root.optJSONObject(url) ?: continue
                    val storedAt = entry.optLong(KEY_STORED_AT)
                    if (now - storedAt > CACHE_TTL_MILLIS) {
                        continue
                    }
                    val links = entry.optJSONArray(KEY_LINKS) ?: continue
                    val media = ArrayList<ImgurMedia>(links.length())
                    for (i in 0 until links.length()) {
                        val link = links.optString(i)
                        if (link.isNotEmpty()) {
                            media.add(ImgurMedia(mediaIdOf(link, i), "", "", "image", link))
                        }
                    }
                    if (media.size > 1) {
                        cache.putIfAbsent(url, CacheEntry(media, storedAt))
                    }
                }
            } catch (e: JSONException) {
                // A file this cannot read is one to replace, not to fail on.
            } catch (e: IOException) {
                // Likewise unreadable; the albums will simply be scraped again.
            }
        }
    }

    /** Queue a write of everything resolved so far, coalescing a burst of albums into one file. */
    private fun scheduleSave() {
        if (storeDir == null || dirty) {
            return
        }
        dirty = true
        try {
            prefetchExecutor.execute { save() }
        } catch (e: RejectedExecutionException) {
            // The queue is busy reading pages; the next album resolved will try again.
            dirty = false
        }
    }

    private fun save() {
        dirty = false
        val dir = storeDir ?: return
        val root = JSONObject()
        val now = System.currentTimeMillis()
        try {
            for ((url, entry) in cache) {
                val media = entry.media ?: continue
                if (media.size <= 1 || now - entry.storedAtMillis > CACHE_TTL_MILLIS) {
                    continue
                }
                val links = JSONArray()
                for (item in media) {
                    links.put(item.link)
                }
                root.put(url, JSONObject().put(KEY_STORED_AT, entry.storedAtMillis).put(KEY_LINKS, links))
            }
            File(dir, CACHE_FILE_NAME).writeText(root.toString())
        } catch (e: JSONException) {
            // Nothing to do but leave whatever was written last time in place.
        } catch (e: IOException) {
            // Likewise.
        }
    }

    /**
     * Scrapes on [executor] and delivers the album on [handler]'s thread, for a row whose card is
     * already showing the cover and wants the rest of the images.
     *
     * Only a resolved album is reported: a card whose page cannot be read keeps the single cover
     * tile it already draws, which is what it would have shown anyway. The caller keeps
     * [cancellable] so it can drop the work when the row is recycled.
     */
    @JvmStatic
    fun fetchAlbumInRecyclerViewAdapter(
        executor: Executor,
        handler: Handler,
        okHttpClient: OkHttpClient,
        host: ImageHostUtils.Host,
        pageUrl: String,
        cancellable: Cancellable,
        listener: AlbumListener
    ) {
        executor.execute {
            val media = fetchSync(okHttpClient, host, pageUrl, cancellable)
            if (cancellable.isCanceled() || media == null || media.size <= 1) {
                // One image is not an album: the cover tile the card already has is the whole of it.
                return@execute
            }
            handler.post { listener.onAlbum(media) }
        }
    }

    /**
     * How many pages may be queued for prefetching before the rest are dropped.
     *
     * A fling crosses far more rows than the user will look at, and a dropped prefetch costs
     * nothing: the row resolves itself on bind exactly as it did before any of this existed.
     */
    private const val PREFETCH_QUEUE_LIMIT = 16

    /**
     * One low-priority thread of its own, deliberately not the app's shared [Executor].
     *
     * That pool is four threads wide and carries work the user is waiting on -- comments, votes,
     * database reads. Prefetching is speculative and each task blocks on a third-party page load,
     * so queueing a screenful of them there would hand all four threads to pages nobody has asked
     * for yet and stall everything behind them. Serial is also the right shape: the rows are warmed
     * nearest-first, so reading them in order is reading them in the order they are wanted.
     */
    private val prefetchExecutor: ThreadPoolExecutor by lazy {
        ThreadPoolExecutor(
            1,
            1,
            30L,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(PREFETCH_QUEUE_LIMIT),
            { runnable ->
                Thread(runnable, "album-prefetch").apply {
                    isDaemon = true
                    priority = Thread.MIN_PRIORITY
                }
            },
        ).apply { allowCoreThreadTimeOut(true) }
    }

    /**
     * Read the album behind [pageUrl] into the cache before anything draws it.
     *
     * This is what closes the window in which an album is on screen as the single cover tile it was
     * seeded with: one tile cannot be swiped and the badge reads 1/1, so a card that only starts
     * reading the page once it has been bound spends a whole page load looking like a broken
     * gallery. Warming the rows the feed is about to reach means the card is a real carousel in its
     * first frame, and the post opened from it is too.
     *
     * Fire and forget: nothing is reported, and a page that cannot be read leaves the row exactly
     * as it would have been.
     */
    @JvmStatic
    fun prefetch(okHttpClient: OkHttpClient, host: ImageHostUtils.Host, pageUrl: String) {
        // Loaded first: without it the very first prefetch of a run reads an empty map and queues
        // work for albums that are already on disk. fetchSync would find them and do no network, but
        // "is this album known" must answer the same here as it does everywhere else.
        ensureLoaded()
        if (cached(pageUrl) != null || !prefetching.add(pageUrl)) {
            return
        }
        try {
            prefetchExecutor.execute {
                try {
                    fetchSync(okHttpClient, host, pageUrl)
                } finally {
                    prefetching.remove(pageUrl)
                }
            }
        } catch (e: RejectedExecutionException) {
            // The queue is full. Released rather than left marked in flight, so the album can be
            // warmed again the next time the feed comes near it.
            prefetching.remove(pageUrl)
        }
    }

    /**
     * The album behind [pageUrl] as gallery tiles if it has already been read, without starting a
     * scrape, or null when it has not.
     *
     * For [ParsePost][ml.docilealligator.infinityforreddit.post.ParsePost], which re-parses the same
     * post many times over a session and would otherwise hand every screen a fresh one-tile
     * placeholder in place of an album this process already has.
     */
    @JvmStatic
    fun cachedGallery(pageUrl: String, subredditName: String, postId: String): ArrayList<Post.Gallery>? {
        ensureLoaded()
        val media = cached(pageUrl)?.media ?: return null
        // One image is not an album: the cover tile the seed already carries is the whole of it, and
        // it is the preview Reddit served rather than a CDN file, which is the better tile to draw.
        if (media.size <= 1) {
            return null
        }
        return toGallery(media, subredditName, postId)
    }

    private fun resolve(
        okHttpClient: OkHttpClient,
        host: ImageHostUtils.Host,
        pageUrl: String,
        cancellable: Cancellable
    ): ArrayList<ImgurMedia>? {
        val page = readPage(okHttpClient, pageUrl, cancellable) ?: return null

        val links = when (host) {
            ImageHostUtils.Host.IMGCHEST -> imgchestFiles(page) ?: firstOgImage(page)?.let { listOf(it) }
            ImageHostUtils.Host.IMGBB -> firstOgImage(page)?.let { listOf(it) }
        } ?: return null

        if (links.isEmpty()) {
            return null
        }

        val media = ArrayList<ImgurMedia>(links.size)
        for ((index, link) in links.withIndex()) {
            // ImgurMedia derives its type from this string; "image" keeps it TYPE_IMAGE.
            media.add(ImgurMedia(mediaIdOf(link, index), "", "", "image", link))
        }
        return media
    }

    /**
     * The album as gallery tiles, which is what the feed card and the post-detail card draw.
     *
     * One conversion for both screens: they show the same carousel and the same `1/N` badge, and two
     * copies of this would be two chances for them to disagree about what the album holds.
     *
     * The mime type is taken from the CDN filename because it is the only thing that carries it --
     * nothing on these hosts reports a type -- and `Post.Gallery` reads it to decide whether a tile
     * is a still or an animation.
     */
    @JvmStatic
    fun toGallery(media: List<ImgurMedia>, subredditName: String, postId: String): ArrayList<Post.Gallery> {
        val tiles = ArrayList<Post.Gallery>(media.size)
        for ((index, item) in media.withIndex()) {
            val extension = item.link.substringBefore('?').substringAfterLast('.', "jpg").lowercase()
            tiles.add(
                Post.Gallery(
                    mimeTypeFor(extension),
                    item.link,
                    item.link,
                    "$subredditName-$postId-${index + 1}.$extension",
                    "",
                    ""
                )
            )
        }
        return tiles
    }

    /**
     * The media type for a CDN file extension.
     *
     * `Post.Gallery` decides from this string whether a tile is a still, an animation or a video, so
     * an extension it cannot read as an image would put a magazine scan in the video player. Only
     * gif is animated on these hosts and neither serves video at all, so everything else is a still
     * and gets a type that says so, whatever the extension turns out to be.
     */
    private fun mimeTypeFor(extension: String): String =
        if (extension == "gif") "image/gif" else "image/$extension"

    /**
     * The id [MediaFileNameUtils][ml.docilealligator.infinityforreddit.utils.MediaFileNameUtils]
     * puts in a downloaded file's name, taken from the CDN filename
     * (`cdn.imgchest.com/files/95805b54f207.png` -> `95805b54f207`).
     *
     * The position in the album is the obvious id and the wrong one: it is what two albums that
     * happen to share a title collide on, which is the exact case that id exists to separate. The
     * CDN name is per-file and stable. It falls back to the position only for a link whose last
     * segment is empty, which no real one has.
     */
    private fun mediaIdOf(link: String, index: Int): String {
        val lastSegment = link.substringBefore('?').substringAfterLast('/')
        val name = lastSegment.substringBeforeLast('.')
        return name.ifEmpty { index.toString() }
    }

    /**
     * Every image in an imgchest album, from the Inertia payload, or null when the attribute is
     * absent or not the shape expected. Returning null (rather than an empty list) is what lets
     * [fetchSync] fall through to `og:image`.
     */
    private fun imgchestFiles(page: String): List<String>? {
        val raw = INERTIA_DATA_PAGE.find(page)?.groupValues?.get(1) ?: return null
        val json = unescapeAttribute(raw)

        return try {
            val files = JSONObject(json)
                .getJSONObject("props")
                .getJSONObject("post")
                .getJSONArray("files")
            val links = ArrayList<String>(files.length())
            for (i in 0 until files.length()) {
                val link = files.getJSONObject(i).optString("link")
                if (link.isNotEmpty()) {
                    links.add(link)
                }
            }
            links.ifEmpty { null }
        } catch (e: JSONException) {
            null
        }
    }

    /**
     * Decodes the five XML entities an HTML attribute can carry, and nothing else.
     *
     * Deliberately not `Html.fromHtml`: that decodes the entities and then *parses the result as
     * HTML*, so a post title containing `&lt;b&gt;` would come back with the tag stripped and the
     * surrounding JSON silently mangled. It also collapses whitespace, which is not safe inside
     * string literals. Only `&quot;` and `&#039;` appear on these pages today, but a title is
     * user-supplied and the other three are one keystroke away.
     *
     * `&amp;` is decoded last so that `&amp;quot;` yields the literal text `&quot;` rather than a
     * quote that would terminate a JSON string early.
     */
    private fun unescapeAttribute(raw: String): String =
        raw.replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")

    private fun firstOgImage(page: String): String? {
        val raw = OG_IMAGE.find(page)?.groupValues?.get(1)
            ?: OG_IMAGE_REVERSED.find(page)?.groupValues?.get(1)
            ?: return null
        return raw.ifEmpty { null }
    }

    /**
     * Forget every resolved album.
     *
     * The cache is process-global and a unit-test JVM runs the whole class in one process, so
     * without this the first test to resolve a page URL answers for every test that uses it.
     */
    @VisibleForTesting
    @JvmStatic
    fun clearCacheForTest() {
        resetForTest(null)
    }

    /**
     * Forget every resolved album and read [dir] again on the next access.
     *
     * Passing the same directory twice is how a test reproduces a restart, which is the only thing
     * that distinguishes this from a plain in-memory cache: within one session an album stays
     * resolved whether or not it was ever written down.
     */
    @VisibleForTesting
    @JvmStatic
    fun resetForTest(dir: File?) {
        cache.clear()
        prefetching.clear()
        storeDir = dir
        // Nothing to read back when there is nowhere to read from, so treat that as already loaded.
        loaded = dir == null
        dirty = false
    }

    /** Write now, on the calling thread, instead of waiting for the background write. */
    @VisibleForTesting
    @JvmStatic
    fun flushForTest() {
        save()
    }

    /** Drops what has expired, and everything else too if that was not enough. */
    private fun evict() {
        val now = System.currentTimeMillis()
        cache.entries.removeAll { now - it.value.storedAtMillis > CACHE_TTL_MILLIS }
        if (cache.size >= MAX_CACHE_ENTRIES) {
            cache.clear()
        }
    }

    private fun cached(pageUrl: String): CacheEntry? {
        val entry = cache[pageUrl] ?: return null
        if (System.currentTimeMillis() - entry.storedAtMillis > CACHE_TTL_MILLIS) {
            cache.remove(pageUrl, entry)
            return null
        }
        return entry
    }

    /** The decoded body of [url] when it answers 2xx with markup, or null. */
    private fun readPage(client: OkHttpClient, url: String, cancellable: Cancellable): String? {
        val request = try {
            Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()
        } catch (e: IllegalArgumentException) {
            return null
        }

        val call = cancellable.track(client.newCall(request)) ?: return null
        val response: Response = try {
            call.execute()
        } catch (e: IOException) {
            return null
        }

        return response.use {
            val body = it.body
            if (!it.isSuccessful || !HtmlBodyUtils.isMarkup(body.contentType())) {
                null
            } else {
                try {
                    HtmlBodyUtils.readBoundedText(body)
                } catch (e: IOException) {
                    null
                }
            }
        }
    }
}
