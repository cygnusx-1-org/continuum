package ml.docilealligator.infinityforreddit.utils

import android.net.Uri

/**
 * Recognises the throwaway clip sites that sports subreddits post highlights to, and works out
 * where the MP4 behind one of their share pages lives.
 *
 * Reddit's own video host barely appears on r/soccer, r/formula1, r/MMA or r/baseball. Goals,
 * overtakes and knockouts go up on a rotating cast of free clip hosts instead, and Continuum used
 * to render every one of those posts as a link card that opened a browser tab. Five hosts carry
 * essentially all of that volume:
 *
 *  - **streamain**, and its share domain `streama.in`, which outnumbers the main domain by roughly
 *    sixteen to one on r/soccer. The busiest of the five.
 *  - **streamin**, across five top-level domains.
 *  - **streamff**, dominant on r/MMA.
 *  - **dubz**, the second clip host on r/formula1.
 *  - **dropr**, lower volume on r/soccer and r/MMA.
 *
 * Everything here is a pure function over a URL: no network, no Android context beyond [Uri]. The
 * network half lives in
 * [FetchShortClipVideo][ml.docilealligator.infinityforreddit.post.FetchShortClipVideo], which takes
 * the candidates this produces and probes them.
 *
 * Two rules are worth stating because breaking either produces the same symptom, an empty player
 * where the browser plays the clip fine. Read the URL shape from the *path* only, so a query string
 * containing `/v/` cannot flip a host's CDN choice. And never treat a derived URL as known-good:
 * both the streamin and dubz storage splits below were found as "new clips show a grey box in the
 * feed", and the only defence is probing every candidate before it reaches ExoPlayer.
 *
 * Recipes last verified against live posts on 2026-09-09. These hosts move infrastructure often, so
 * treat a failure here as expected rather than exceptional. That is why resolution failure demotes
 * the post back to a link card instead of showing a broken player.
 */
object ShortClipHostUtils {

    /** Which of the five sites a URL belongs to. */
    enum class Host {
        STREAMAIN,
        STREAMIN,
        STREAMFF,
        DUBZ,
        DROPR
    }

    /**
     * Authority to host, `www.` already stripped and lowercased. One map so adding a domain, which
     * these sites do regularly, is a single line.
     *
     * `bangr.im` is deliberately absent: it has answered 410 Gone since 2026-07-16.
     */
    private val HOSTS_BY_AUTHORITY = mapOf(
        "streamain.com" to Host.STREAMAIN,
        "streama.in" to Host.STREAMAIN,

        "streamin.link" to Host.STREAMIN,
        "streamin.me" to Host.STREAMIN,
        "streamin.one" to Host.STREAMIN,
        "streamin.fun" to Host.STREAMIN,
        "streamin.top" to Host.STREAMIN,

        "streamff.pro" to Host.STREAMFF,
        "streamff.com" to Host.STREAMFF,
        "streamff.link" to Host.STREAMFF,

        "dubz.link" to Host.DUBZ,
        "dubz.co" to Host.DUBZ,
        "dubz.live" to Host.DUBZ,

        "dropr.co" to Host.DROPR
    )

    /** streamain addresses a clip as `<id>/watch` or `<lang>/<id>/watch`. */
    private const val STREAMAIN_WATCH_SEGMENT = "watch"

    private const val STREAMAIN_EMBED_PREFIX = "https://streamain.com/embed/"

    private const val STREAMIN_C_CDN = "https://c-cdn.streamin.top/uploads/"
    private const val STREAMIN_W_CDN = "https://w-cdn.streamin.top/uploads/"
    private const val STREAMIN_SHARE_PREFIX = "https://streamin.top/v/"

    private const val STREAMFF_API_PREFIX = "https://ffedge.streamff.com/share/"

    /** Pre-2026-07 storage. Kept only for an API entry that carries no `external_url`. */
    private const val STREAMFF_LEGACY_PREFIX = "https://storage.streamff.com/"

    private const val DUBZ_MAKEVOS_PREFIX = "https://cdn.makevos.com/videos/"
    private const val DUBZ_SQUEELAB_PREFIX = "https://cdn.squeelab.com/guest/videos/"

    private const val DROPR_SHARE_PREFIX = "https://dropr.co/v/"

    private const val MP4_EXTENSION = ".mp4"

    /**
     * Whether the app should promote these posts to video at all.
     *
     * [ParsePost][ml.docilealligator.infinityforreddit.post.ParsePost] is entirely static and holds
     * no `SharedPreferences`; threading one in would touch its eleven call sites for a single
     * boolean. So the preference is mirrored here instead, seeded at startup and updated by a
     * preference-change listener. Volatile because parsing happens on background executors while
     * the write comes from the settings screen on the main thread.
     */
    @JvmStatic
    @Volatile
    var inlinePlaybackEnabled: Boolean = true

    /**
     * The clip host [uri] belongs to, or null for every other URL, so callers can hand any URI to
     * this without first working out what kind it is.
     */
    @JvmStatic
    fun hostOf(uri: Uri?): Host? {
        val authority = uri?.authority?.lowercase()?.removePrefix("www.") ?: return null
        return HOSTS_BY_AUTHORITY[authority]
    }

    /**
     * The clip id inside [uri], or null when the URL names no clip — a bare `dropr.co/`, a
     * `streamff.pro/about`, the site root.
     *
     * Every host but streamain puts the id last, under a `/v/` or `/c/` prefix or on its own.
     * streamain puts a literal `watch` after it, optionally behind a two-letter language segment.
     */
    @JvmStatic
    fun clipIdOf(host: Host, uri: Uri?): String? {
        val segments = uri?.pathSegments?.filter { it.isNotEmpty() } ?: return null
        if (segments.isEmpty()) {
            return null
        }

        if (host == Host.STREAMAIN) {
            val watchIndex = segments.indexOf(STREAMAIN_WATCH_SEGMENT)
            // streama.in share links are the bare id with no /watch, so fall back to the last
            // segment rather than rejecting them.
            return if (watchIndex > 0) segments[watchIndex - 1] else segments.last()
        }

        return segments.last()
    }

    /**
     * The MP4 URLs to try for [clipId], in the order they should be probed, or an empty list when
     * the host publishes no derivable URL and the resolver has to read its page.
     *
     * Order matters on both hosts that return more than one. streamin spreads uploads over two CDNs
     * that are not mirrors of each other — as of 2026-09-09 the newest clips sat on `c-cdn` only and
     * month-old ones on `w-cdn` only, with plenty on both — and a feed shows new clips, so `c-cdn`
     * goes first. dubz splits by URL shape instead, `/c/` on one CDN and `/v/` on the other with
     * strictly disjoint id sets; the wrong one answers 404. The other CDN stays in the list as a
     * fallback so a future re-shuffle costs one extra probe rather than dead clips.
     */
    @JvmStatic
    fun candidateVideoUrls(host: Host, clipId: String, uri: Uri?): List<String> {
        return when (host) {
            // The video filename is unrelated to the page id, so there is nothing to derive.
            Host.STREAMAIN -> emptyList()

            Host.STREAMIN -> listOf(
                STREAMIN_C_CDN + clipId + MP4_EXTENSION,
                STREAMIN_W_CDN + clipId + MP4_EXTENSION
            )

            // The API answer is authoritative and names a host that has moved once already.
            Host.STREAMFF -> emptyList()

            Host.DUBZ -> if (isDubzGuestShape(uri)) {
                listOf(
                    DUBZ_SQUEELAB_PREFIX + clipId + MP4_EXTENSION,
                    DUBZ_MAKEVOS_PREFIX + clipId + MP4_EXTENSION
                )
            } else {
                listOf(
                    DUBZ_MAKEVOS_PREFIX + clipId + MP4_EXTENSION,
                    DUBZ_SQUEELAB_PREFIX + clipId + MP4_EXTENSION
                )
            }

            // The CDN filename is an unrelated 16-character hex string.
            Host.DROPR -> emptyList()
        }
    }

    /**
     * `/v/` clips live on the squeelab CDN and `/c/` clips on the makevos one. A bare
     * `dubz.link/<id>` gets the makevos-first order, which is also what an unrecognised shape gets.
     *
     * Read from the path segments rather than the whole URL so `dubz.link/c/x?ref=/v/y` cannot pick
     * the wrong CDN.
     */
    private fun isDubzGuestShape(uri: Uri?): Boolean {
        val segments = uri?.pathSegments?.filter { it.isNotEmpty() } ?: return false
        return segments.size >= 2 && segments[segments.size - 2].equals("v", ignoreCase = true)
    }

    /**
     * The small static page whose `<video>` tag carries streamain's real MP4 URL in a `data-link`
     * attribute. A dead clip answers 404 here.
     */
    @JvmStatic
    fun streamainEmbedUrl(clipId: String): String = STREAMAIN_EMBED_PREFIX + clipId

    /**
     * streamff's JSON endpoint. Its share page is a single-page app whose `og:video` points back at
     * itself, so the API is the only usable source. It answers with a JSON array, empty for a clip
     * that has been purged.
     */
    @JvmStatic
    fun streamffApiUrl(clipId: String): String = STREAMFF_API_PREFIX + clipId

    /** Where streamff clips lived before roughly 2026-07. 404s even for live clips. */
    @JvmStatic
    fun streamffLegacyVideoUrl(clipId: String): String =
        STREAMFF_LEGACY_PREFIX + clipId + MP4_EXTENSION

    /**
     * The share page to scrape for an `og:video`, or null for the three hosts that publish none
     * worth reading.
     *
     * Authoritative for streamin, and self-healing: if uploads later move to a CDN this file does
     * not list, the share page still names it. A missing `og:video` is how both hosts spell a clip
     * that is gone — for dropr that also covers the permanent "video is processing" state.
     *
     * The three that return null each have their own reason. streamff's points back at the page
     * itself, dubz publishes none at all, and streamain's watch page carries only an `og:image`, so
     * its embed page is the source instead.
     *
     * The posted URL is preferred over a rebuilt one. Every alias resolves, and rebuilding would
     * throw away the domain the poster actually used for no gain.
     */
    @JvmStatic
    fun sharePageUrl(host: Host, clipId: String, uri: Uri?): String? {
        return when (host) {
            Host.STREAMIN -> uri?.toString() ?: (STREAMIN_SHARE_PREFIX + clipId)
            Host.DROPR -> uri?.toString() ?: (DROPR_SHARE_PREFIX + clipId)
            Host.STREAMAIN, Host.STREAMFF, Host.DUBZ -> null
        }
    }
}
