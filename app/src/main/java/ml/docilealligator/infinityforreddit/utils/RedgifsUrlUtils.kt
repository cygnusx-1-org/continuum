package ml.docilealligator.infinityforreddit.utils

import android.net.Uri

/**
 * Picks which of the two files Redgifs publishes for a video should actually be streamed.
 *
 * Reddit-hosted videos honour the resolution preference by overriding an ExoPlayer track: the HLS
 * manifest carries a whole ladder, so [ViewVideoActivity][ml.docilealligator.infinityforreddit.activities.ViewVideoActivity]
 * can walk it and select the best track at or below the wanted height. A Redgifs post plays a
 * progressive MP4 with exactly one video track, so there is no ladder to walk and the preference
 * had no effect at all -- the reason data saving appeared to do nothing on NSFW posts.
 *
 * Redgifs exposes precisely two tiers, and both carry audio:
 *
 *  - `media.redgifs.com/<Name>.mp4` -- the `hd` variant, 1080px on its short edge.
 *  - `media.redgifs.com/<Name>-mobile.mp4` -- the `sd` variant, [SD_RESOLUTION]px on its short
 *    edge and roughly a fifth of the bytes.
 *
 * So the only lever data saving has is which of those two URLs gets played. There is no 720p, 360p,
 * 240p or 144p copy to fall further back to, which is why Redgifs has its own two-entry preference
 * ([SharedPreferencesUtils.REDGIFS_VIDEO_DEFAULT_RESOLUTION]) rather than sharing the seven-entry
 * Reddit one: offering a value that cannot be delivered is worse than not offering it.
 *
 * The `-mobile` name is derived rather than fetched. Over a sample of 336 Redgifs videos the `sd`
 * URL the API returned was the `hd` URL with `.mp4` replaced by `-mobile.mp4` in every single case,
 * so rewriting the suffix avoids an extra API round trip on a path that already has the HD URL in
 * hand. It also keeps both entry points -- the URL
 * [ParsePost][ml.docilealligator.infinityforreddit.post.ParsePost] builds and the one
 * [FetchRedgifsVideoLinks][ml.docilealligator.infinityforreddit.thing.FetchRedgifsVideoLinks]
 * reads out of the API -- going through one rule.
 */
object RedgifsUrlUtils {
    /**
     * Short-edge height of the `-mobile` file. Measured, not assumed: it holds for portrait
     * (480x860, 480x826) and landscape (856x480, 720x480, 622x480) sources alike.
     */
    private const val SD_RESOLUTION = 480

    private const val MEDIA_HOST = "media.redgifs.com"
    private const val MP4_EXTENSION = ".mp4"
    private const val SD_SUFFIX = "-mobile.mp4"

    /**
     * [uri] unchanged, or its `-mobile` counterpart when data saving is on and the user picked the
     * 480p tier.
     *
     * Only *playback* goes through here. The download URL deliberately stays on the HD file: data
     * saving is about what gets streamed while browsing, not about permanently saving a worse copy
     * than the user asked to keep.
     *
     * [redgifsResolution] is [SharedPreferencesUtils.REDGIFS_VIDEO_DEFAULT_RESOLUTION], whose only
     * two values are 1080 and [SD_RESOLUTION]. Anything above the `-mobile` file's height stays on
     * HD, so an unrecognised or stale stored value fails safe onto the better copy rather than
     * silently downgrading.
     *
     * Anything that is not a Redgifs media MP4 -- a v.redd.it fallback, a Streamable or Imgur URL,
     * an already-rewritten `-mobile` URL -- passes straight through, so callers can hand every URI
     * to this without first working out what kind it is.
     */
    fun playbackUri(uri: Uri?, isDataSavingMode: Boolean, redgifsResolution: Int): Uri? {
        if (uri == null || !isDataSavingMode || redgifsResolution !in 1..SD_RESOLUTION) {
            return uri
        }

        if (!MEDIA_HOST.equals(uri.host, ignoreCase = true)) {
            return uri
        }

        val path = uri.path ?: return uri
        if (!path.endsWith(MP4_EXTENSION) || path.endsWith(SD_SUFFIX)) {
            return uri
        }

        // buildUpon() rather than string surgery so any query string survives: unsigned URLs are
        // what Redgifs serves today, but it has handed out `?expires=&token=` ones before.
        return uri.buildUpon()
            .path(path.dropLast(MP4_EXTENSION.length) + SD_SUFFIX)
            .build()
    }

    /**
     * The HD counterpart of an SD URL [playbackUri] produced, or null when [uri] is not one.
     *
     * The `-mobile` file is derived, not confirmed to exist, so a downgrade can in principle
     * point at nothing: every sample checked had one (375 URLs across two probes), but a variant
     * Redgifs never transcoded would 404. That matters because a Redgifs post parsed from a link
     * without a `reddit_video_preview` carries no `videoFallBackDirectUrl`, so a failed SD file
     * would leave the player with nothing to try and show an error where the HD file used to
     * play. This lets the failure walk back up to HD instead.
     */
    fun hdVariant(uri: Uri?): Uri? {
        if (uri == null || !MEDIA_HOST.equals(uri.host, ignoreCase = true)) {
            return null
        }

        val path = uri.path ?: return null
        if (!path.endsWith(SD_SUFFIX)) {
            return null
        }

        return uri.buildUpon()
            .path(path.dropLast(SD_SUFFIX.length) + MP4_EXTENSION)
            .build()
    }
}
