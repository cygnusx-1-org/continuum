package ml.docilealligator.infinityforreddit.post

import android.net.Uri
import android.os.Handler
import androidx.annotation.WorkerThread
import ml.docilealligator.infinityforreddit.FetchVideoLinkListener
import ml.docilealligator.infinityforreddit.utils.HtmlBodyUtils
import ml.docilealligator.infinityforreddit.utils.ShortClipHostUtils
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Turns a clip-host share page into the MP4 behind it.
 *
 * The shape follows [FetchStreamableVideo]: an executor-plus-handler entry point for the feed and
 * post detail, which hand in something they can cancel when the row is recycled, and a synchronous
 * one for the download service and the fullscreen player.
 *
 * It talks to five unrelated websites rather than one typed API, so it uses OkHttp directly. Two
 * kinds of request go out, and both matter:
 *
 *  - **A page or API read**, for the three hosts whose MP4 name cannot be derived from the share
 *    URL. Third-party HTML gets read through [HtmlBodyUtils], which bounds and decodes it safely.
 *  - **A HEAD probe**, for every candidate before it reaches ExoPlayer. This is not optional. The
 *    single largest class of bug in this feature is a plausible-looking URL that 404s, which shows
 *    the user an empty player rather than an error, and both the streamin and dubz storage splits
 *    were originally reported as "the feed shows a grey box but the link plays in a browser".
 *
 * Resolution failure is ordinary here, not exceptional: streamff purges clips within days, and any
 * of the five can move infrastructure without notice. Callers are expected to demote the post back
 * to a link card rather than show a broken player.
 */
object FetchShortClipVideo {

    /**
     * How long a resolved URL is trusted. Long enough that scrolling a row off and back does not
     * re-probe, short enough that a CDN rotation heals within a session.
     */
    private const val CACHE_TTL_MILLIS = 10L * 60L * 1000L

    /**
     * An expired streamff clip 302s to a Cloudflare placeholder MP4 that plays perfectly well and
     * is not the clip. A 2xx alone is therefore not enough; the final host has to be checked.
     */
    private const val CLOUDFLARE_ABUSE_MARKER = "cloudflare-terms-of-service-abuse"

    /**
     * Clip hosts serve their pages through Cloudflare and are happier with a browser-shaped agent
     * than with the app's own. Set explicitly per request, which wins over the base client's
     * interceptor because that one only fills in a User-Agent when the request has none.
     */
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/127.0.0.0 Mobile Safari/537.36"

    /** Separates the two halves of a cache key. Neither a host name nor a clip id contains one. */
    private const val CACHE_KEY_SEPARATOR = "/"

    /**
     * Entries are only evicted when they are read, so a long session that scrolls past thousands of
     * clips would otherwise keep every one. Small because the cache exists to survive a row being
     * recycled and rebound, not to remember a whole session.
     */
    private const val MAX_CACHE_ENTRIES = 256

    /**
     * `<video ... data-link="https://cdn.streamain.com/...">`. The attributes are spread over
     * several lines on the real page, so this matches the attribute alone rather than the tag.
     */
    private val STREAMAIN_DATA_LINK =
        Regex("""data-link\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    /**
     * An `og:video` meta tag, in either attribute order and with either quote character.
     *
     * All three spellings occur in the wild: dropr writes `property="og:video" content="…"` with
     * double quotes, streamin writes the same with single quotes, and the reversed order is common
     * enough elsewhere to be worth accepting. Every wildcard is `[^>]`, so a match cannot run past
     * the end of one tag the way a lazy `.*?` between the two attributes would.
     */
    private val OG_VIDEO = Regex(
        """<meta[^>]*?property\s*=\s*["']og:video["'][^>]*?content\s*=\s*["']([^"']+)["']""" +
            """|<meta[^>]*?content\s*=\s*["']([^"']+)["'][^>]*?property\s*=\s*["']og:video["']""",
        RegexOption.IGNORE_CASE
    )

    private data class CacheEntry(val videoUrl: String?, val storedAtMillis: Long)

    /** Keyed by host *and* id, so the same id on two hosts cannot collide. */
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /**
     * Cancels whatever request the resolution is currently blocked on, and stops it starting
     * another.
     *
     * Resolution is a short chain of requests rather than one, so a bare [Call] is not enough to
     * cancel it: aborting the page read has to stop the probes that would have followed.
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
     * Resolves on [executor] and delivers the result on [handler]'s thread.
     *
     * The caller keeps [cancellable] so it can drop the work when the row that wanted it is
     * recycled. A fast scroll would otherwise leave a pile of live requests behind it.
     */
    @JvmStatic
    fun fetchShortClipVideoInRecyclerViewAdapter(
        executor: Executor,
        handler: Handler,
        okHttpClient: OkHttpClient,
        host: ShortClipHostUtils.Host,
        clipId: String,
        pageUrl: String?,
        cancellable: Cancellable,
        fetchVideoLinkListener: FetchVideoLinkListener
    ) {
        executor.execute {
            val videoUrl = fetchShortClipVideoSync(okHttpClient, host, clipId, pageUrl, cancellable)
            if (cancellable.isCanceled()) {
                return@execute
            }
            handler.post {
                if (videoUrl == null) {
                    fetchVideoLinkListener.failed(null)
                } else {
                    fetchVideoLinkListener.onFetchShortClipVideoLinkSuccess(videoUrl)
                }
            }
        }
    }

    /** The resolved MP4 URL, or null when the clip is gone or the host has changed shape. */
    @JvmStatic
    @JvmOverloads
    @WorkerThread
    fun fetchShortClipVideoSync(
        okHttpClient: OkHttpClient,
        host: ShortClipHostUtils.Host,
        clipId: String,
        pageUrl: String?,
        cancellable: Cancellable = Cancellable()
    ): String? {
        val key = host.name + CACHE_KEY_SEPARATOR + clipId
        cached(key)?.let { return it.videoUrl }

        val resolved = resolve(okHttpClient, host, clipId, pageUrl, cancellable)

        // A cancelled resolution says nothing about the clip, so it must not be remembered as one
        // that failed.
        if (!cancellable.isCanceled()) {
            if (cache.size >= MAX_CACHE_ENTRIES) {
                evict()
            }
            cache[key] = CacheEntry(resolved, System.currentTimeMillis())
        }
        return resolved
    }

    /** Drops what has expired, and everything else too if that was not enough. */
    private fun evict() {
        val now = System.currentTimeMillis()
        cache.entries.removeAll { now - it.value.storedAtMillis > CACHE_TTL_MILLIS }
        if (cache.size >= MAX_CACHE_ENTRIES) {
            cache.clear()
        }
    }

    private fun cached(key: String): CacheEntry? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() - entry.storedAtMillis > CACHE_TTL_MILLIS) {
            cache.remove(key, entry)
            return null
        }
        return entry
    }

    private fun resolve(
        client: OkHttpClient,
        host: ShortClipHostUtils.Host,
        clipId: String,
        pageUrl: String?,
        cancellable: Cancellable
    ): String? {
        val uri = pageUrl?.let { runCatching { Uri.parse(it) }.getOrNull() }

        if (host == ShortClipHostUtils.Host.STREAMFF) {
            return resolveStreamff(client, clipId, cancellable)
        }

        if (host == ShortClipHostUtils.Host.STREAMAIN) {
            // The MP4 filename is unrelated to the page id, so the embed page is the only source.
            // A dead clip answers 404 here, which readPage reports as null.
            val page = readPage(client, ShortClipHostUtils.streamainEmbedUrl(clipId), cancellable)
            val dataLink = page?.let { STREAMAIN_DATA_LINK.find(it)?.groupValues?.get(1) }
            return dataLink?.let { probe(client, it, cancellable) }
        }

        // Derived candidates first where there are any: they cost one HEAD each and cover almost
        // every clip, which saves reading a page in the common case.
        for (candidate in ShortClipHostUtils.candidateVideoUrls(host, clipId, uri)) {
            probe(client, candidate, cancellable)?.let { return it }
            if (cancellable.isCanceled()) {
                return null
            }
        }

        val sharePageUrl = ShortClipHostUtils.sharePageUrl(host, clipId, uri) ?: return null
        val page = readPage(client, sharePageUrl, cancellable) ?: return null
        val ogVideo = firstOgVideo(page) ?: return null

        return probe(client, ogVideo, cancellable)
    }

    /**
     * streamff's share page is a single-page app whose `og:video` points back at the page, so its
     * JSON endpoint is the only usable source. An empty array is how it spells a purged clip, which
     * is most of them: it now keeps clips for days rather than the two months it once did.
     */
    private fun resolveStreamff(
        client: OkHttpClient,
        clipId: String,
        cancellable: Cancellable
    ): String? {
        val body = readBody(client, ShortClipHostUtils.streamffApiUrl(clipId), cancellable, false)
            ?: return null

        val externalUrl = try {
            val array = JSONArray(body)
            if (array.length() == 0) {
                return null
            }
            array.getJSONObject(0).optString("external_url").ifEmpty { null }
        } catch (e: JSONException) {
            null
        }

        if (externalUrl != null) {
            probe(client, externalUrl, cancellable)?.let { return it }
            if (cancellable.isCanceled()) {
                return null
            }
        }

        // Pre-2026-07 storage, kept only for the rare entry that carries no external_url.
        return probe(client, ShortClipHostUtils.streamffLegacyVideoUrl(clipId), cancellable)
    }

    /**
     * The first `og:video` in [page], with any query stripped.
     *
     * streamin appends a cosmetic cache-buster that names the clip's age, `?2daysago` on the sample
     * this was written against and `?7hoursago` on a fresher one, so the query cannot be matched
     * against a fixed suffix and is dropped whole. None of these hosts puts a signed token there.
     */
    private fun firstOgVideo(page: String): String? {
        val match = OG_VIDEO.find(page) ?: return null
        val raw = match.groupValues[1].ifEmpty { match.groupValues[2] }
        return raw.ifEmpty { null }?.substringBefore('?')
    }

    /**
     * The final URL of [url] after redirects when it answers 2xx and is not the Cloudflare abuse
     * placeholder, or null.
     *
     * HEAD rather than a ranged GET. A range would work on all five hosts today, since squeelab
     * used to ignore `Range` and stream the whole file but answers 206 correctly as of 2026-09-09,
     * and HEAD is both cheaper and unambiguous.
     *
     * Content type is deliberately not checked: `w-cdn.streamin.top` serves
     * `application/octet-stream` for files that play perfectly well.
     */
    private fun probe(client: OkHttpClient, url: String, cancellable: Cancellable): String? {
        val request = try {
            Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()
        } catch (e: IllegalArgumentException) {
            return null
        }

        return execute(client, request, cancellable)?.use { response ->
            if (!response.isSuccessful) {
                return null
            }
            val finalUrl = response.request.url
            if (finalUrl.host.contains(CLOUDFLARE_ABUSE_MARKER)) {
                return null
            }
            finalUrl.toString()
        }
    }

    /** The decoded body of [url] when it is markup, or null. */
    private fun readPage(client: OkHttpClient, url: String, cancellable: Cancellable): String? =
        readBody(client, url, cancellable, true)

    private fun readBody(
        client: OkHttpClient,
        url: String,
        cancellable: Cancellable,
        requireMarkup: Boolean
    ): String? {
        val request = try {
            Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()
        } catch (e: IllegalArgumentException) {
            return null
        }

        return execute(client, request, cancellable)?.use { response ->
            val body = response.body
            if (!response.isSuccessful) {
                return null
            }
            if (requireMarkup && !HtmlBodyUtils.isMarkup(body.contentType())) {
                return null
            }
            try {
                HtmlBodyUtils.readBoundedText(body)
            } catch (e: IOException) {
                null
            }
        }
    }

    private fun execute(client: OkHttpClient, request: Request, cancellable: Cancellable): Response? {
        val call = cancellable.track(client.newCall(request)) ?: return null
        return try {
            call.execute()
        } catch (e: IOException) {
            null
        }
    }
}
