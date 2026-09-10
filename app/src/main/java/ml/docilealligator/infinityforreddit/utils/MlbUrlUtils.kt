package ml.docilealligator.infinityforreddit.utils

import android.net.Uri

/**
 * Recognises the two hosts MLB highlight clips are posted from, and picks which rendition of one
 * gets streamed.
 *
 * r/baseball posts highlights as direct MP4 links rather than through a share page, so unlike the
 * clip hosts in [ShortClipHostUtils] there is nothing to resolve. What there is instead is a size
 * problem. Reddit generates no preview for these links, and
 * [ParsePost][ml.docilealligator.infinityforreddit.post.ParsePost] only promotes a bare `.mp4` link
 * to video on the branch for posts that *have* a preview, so these arrived as link cards. Promoting
 * them means the app starts streaming whichever rendition the poster happened to copy, and on
 * `mlb-cuts-diamond` that is almost always the largest one.
 *
 * That host publishes exactly two rungs of the same 1280x720 frame, differing only in bitrate.
 * Measured over four clips:
 *
 * | Rendition | Sizes |
 * |---|---|
 * | `_16000K` | 32.7 MB, 56.2 MB, 65.1 MB, 89.5 MB |
 * | `_4000K` | 8.6 MB, 14.7 MB, 16.9 MB, 23.3 MB |
 *
 * Both rungs are 720p, so the preference is expressed in bitrate rather than resolution — offering
 * a resolution that is not on the ladder would be the same mistake
 * [RedgifsUrlUtils] exists to avoid. `bdata-producedclips` URLs are a bare UUID with a single
 * rendition and around 11 MB, so they pass straight through.
 */
object MlbUrlUtils {

    /** The FORGE host, which publishes a two-rung bitrate ladder. */
    private const val CUTS_HOST = "mlb-cuts-diamond.mlb.com"

    /** The other MLB clip host. Single rendition, nothing to choose. */
    private const val PRODUCED_CLIPS_HOST = "bdata-producedclips.mlb.com"

    private const val MP4_EXTENSION = ".mp4"

    /** The rungs, highest first. Values match `R.array.settings_mlb_video_default_bitrate_values`. */
    const val BITRATE_HIGH = 16000

    const val BITRATE_LOW = 4000

    private const val HIGH_SUFFIX = "_16000K.mp4"
    private const val LOW_SUFFIX = "_4000K.mp4"

    /**
     * Whether [uri] is an MLB highlight clip, i.e. a direct MP4 on one of the two hosts.
     *
     * Deliberately narrower than "any link post ending in `.mp4`". Promoting every one of those
     * would change how unrelated posts render across the whole app, which is well beyond what this
     * is for.
     */
    @JvmStatic
    fun isMlbClip(uri: Uri?): Boolean {
        val authority = uri?.authority?.lowercase()?.removePrefix("www.") ?: return false
        if (authority != CUTS_HOST && authority != PRODUCED_CLIPS_HOST) {
            return false
        }
        return uri.path?.lowercase()?.endsWith(MP4_EXTENSION) == true
    }

    /**
     * [uri] unchanged, or its smaller rendition when data saving is on and the user picked the low
     * rung.
     *
     * Only *playback* goes through here. The download URL stays on whatever the poster linked, on
     * the same reasoning as [RedgifsUrlUtils.playbackUri]: data saving governs what gets streamed
     * while browsing, not what gets kept.
     *
     * The low rung is derived rather than confirmed, so it can in principle point at nothing. It
     * was present on every clip sampled, and [postedVariant] walks a failure back to the URL the
     * poster actually linked.
     *
     * Anything that is not an `mlb-cuts-diamond` MP4 — a `bdata-producedclips` UUID, a clip already
     * posted at the low rung, any other URL — passes straight through.
     */
    @JvmStatic
    fun playbackUri(uri: Uri?, isDataSavingMode: Boolean, mlbBitrate: Int): Uri? {
        if (uri == null || !isDataSavingMode || mlbBitrate >= BITRATE_HIGH) {
            return uri
        }

        if (!CUTS_HOST.equals(uri.host, ignoreCase = true)) {
            return uri
        }

        val path = uri.path ?: return uri
        if (!path.endsWith(HIGH_SUFFIX)) {
            return uri
        }

        return uri.buildUpon()
            .path(path.dropLast(HIGH_SUFFIX.length) + LOW_SUFFIX)
            .build()
    }

    /**
     * The high-bitrate counterpart of a URL [playbackUri] produced, or null when [uri] is not one.
     *
     * Mirrors [RedgifsUrlUtils.hdVariant]: an MLB post carries no `videoFallBackDirectUrl`, so a
     * downshifted URL that 404s would leave the player with nothing to fall back to and show an
     * error where the posted file used to play.
     */
    @JvmStatic
    fun postedVariant(uri: Uri?): Uri? {
        if (uri == null || !CUTS_HOST.equals(uri.host, ignoreCase = true)) {
            return null
        }

        val path = uri.path ?: return null
        if (!path.endsWith(LOW_SUFFIX)) {
            return null
        }

        return uri.buildUpon()
            .path(path.dropLast(LOW_SUFFIX.length) + HIGH_SUFFIX)
            .build()
    }
}
