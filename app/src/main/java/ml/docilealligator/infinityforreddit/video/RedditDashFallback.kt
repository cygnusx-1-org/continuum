package ml.docilealligator.infinityforreddit.video

import android.net.Uri
import android.util.Log
import androidx.annotation.VisibleForTesting
import ml.docilealligator.infinityforreddit.utils.VideoHttpErrorLog

/**
 * The DASH manifest a Reddit video can fall back to when its HLS playlist fails.
 *
 * Reddit stores a video's master HLS playlist separately from its media, and for some videos
 * that file is missing for hours after posting: the CDN answers 403 with S3's "AccessDenied"
 * body and keeps that answer for ten minutes. Issue #498 traced "Error fetching video" to exactly
 * this. The DASH manifest beside it was there in every such case, with the same video and audio,
 * so a player whose HLS playlist fails tries DASH once before giving up.
 *
 * The URL is derived rather than read from the post's `dash_url`, which keeps embedded videos
 * covered: they reach the player as a built `v.redd.it/link/<post>/asset/<id>/HLSPlaylist.m3u8`
 * URL with no JSON behind it. The derived URL carries no `a=` signature, which v.redd.it does not
 * check.
 *
 * A video that has fallen back is remembered for the rest of the process, so the next player to
 * open it, or the same card rebound, goes straight to DASH instead of failing on HLS again. The
 * post keeps its HLS URL: the feed, the post page and Shadowbox share Post objects, and what is
 * shared should stay what was posted.
 */
object RedditDashFallback {

    private const val HLS_PLAYLIST = "HLSPlaylist.m3u8"
    private const val DASH_MANIFEST = "DASHPlaylist.mpd"

    /** Enough for a long session of scrolling; each entry is one short string. */
    private const val REMEMBERED_VIDEOS = 128

    /** DASH manifest URLs of videos whose HLS playlist has failed, oldest first. */
    private val fellBack = object : LinkedHashMap<String, Unit>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean {
            return size > REMEMBERED_VIDEOS
        }
    }

    /**
     * The DASH manifest beside [uri] when [uri] is a v.redd.it HLS master playlist, or null for
     * anything else, including a DASH manifest, so a failed fallback never falls back again.
     */
    @JvmStatic
    fun dashUriFor(uri: Uri?): Uri? {
        if (uri == null || !"v.redd.it".equals(uri.host, ignoreCase = true)) {
            return null
        }
        val segments = uri.pathSegments
        if (segments.isEmpty() || !HLS_PLAYLIST.equals(segments.last(), ignoreCase = true)) {
            return null
        }
        val builder = Uri.Builder().scheme(uri.scheme).encodedAuthority(uri.encodedAuthority)
        segments.dropLast(1).forEach { builder.appendPath(it) }
        return builder.appendPath(DASH_MANIFEST).build()
    }

    /**
     * What a player should open for [uri]: the DASH manifest once this video's HLS playlist has
     * failed in this process, and [uri] itself otherwise.
     */
    @JvmStatic
    fun playbackUri(uri: Uri): Uri {
        val dashUri = dashUriFor(uri) ?: return uri
        val fellBackBefore = synchronized(fellBack) { fellBack.containsKey(dashUri.toString()) }
        return if (fellBackBefore) dashUri else uri
    }

    /**
     * Called from a player's `onPlayerError`: the DASH manifest to retry on, or null when [failed]
     * has none -- it is not a v.redd.it HLS playlist, or it is the DASH manifest already. The
     * video is remembered for [playbackUri], and the switch logged under [VideoHttpErrorLog.TAG]
     * beside the failure that caused it.
     */
    @JvmStatic
    fun fallbackFor(player: String, failed: Uri?): Uri? {
        val dashUri = dashUriFor(failed) ?: return null
        synchronized(fellBack) { fellBack[dashUri.toString()] = Unit }
        Log.w(VideoHttpErrorLog.TAG, "[$player] HLS playlist failed; retrying on $dashUri")
        return dashUri
    }

    @VisibleForTesting
    @JvmStatic
    fun forgetAll() {
        synchronized(fellBack) { fellBack.clear() }
    }
}
