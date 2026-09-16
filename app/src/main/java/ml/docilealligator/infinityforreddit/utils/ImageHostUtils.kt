package ml.docilealligator.infinityforreddit.utils

import android.net.Uri

/**
 * Recognises the image hosts whose links are landing pages rather than images, and pulls the id out
 * of one.
 *
 * Reddit classifies `imgchest.com/p/<id>` and `imgbb.com/<id>` as `post_hint: link`, because the URL
 * really is an HTML page — the image lives on a separate CDN host. Continuum therefore rendered them
 * as link cards that opened a browser, which is issue #412's second half. It does still generate a
 * `preview` for them, so the card has something to draw; only the tap target was wrong.
 *
 * Everything here is a pure function over a URL: no network, no Android context beyond [Uri]. The
 * network half lives in
 * [FetchImageHostMedia][ml.docilealligator.infinityforreddit.post.FetchImageHostMedia], which
 * fetches the page this identifies and scrapes the image list out of it.
 *
 * Two things this must never match, because both are already direct images that the normal
 * extension-based branches in `ParsePost` handle correctly:
 *
 *  - `cdn.imgchest.com/files/<file>.png`
 *  - `i.ibb.co/<hash>/<name>.jpg`
 *
 * They fall out for free — both are different authorities from the landing-page hosts below, so
 * neither is in [HOSTS_BY_AUTHORITY].
 *
 * Unlike [ShortClipHostUtils], an id here can address a whole **album**: `imgchest.com/p/<id>` is an
 * album URL, and real posts run to twenty images (r/anime's Megami Magazine threads). Nothing at
 * parse time can know the count without a network round trip, which is why the tap opens
 * `ViewImgurMediaActivity` — a pager that copes with one image or many — rather than the
 * single-image viewer.
 *
 * Recipes last verified against live posts on 2026-09-16. As with the clip hosts, treat a failure as
 * expected rather than exceptional. Unlike them it cannot demote the post, because nothing resolves
 * an album until it is opened: a failed scrape surfaces as the album viewer's retry view, and a
 * failed download as a download error.
 */
object ImageHostUtils {

    /** Which of the two sites a URL belongs to. */
    enum class Host {
        IMGCHEST,
        IMGBB
    }

    /**
     * Authority to host, `www.` already stripped and lowercased. `ibb.co` is imgbb's own short
     * domain for the same page and is what its share button produces, so both spellings appear on
     * Reddit.
     */
    private val HOSTS_BY_AUTHORITY = mapOf(
        "imgchest.com" to Host.IMGCHEST,
        "imgbb.com" to Host.IMGBB,
        "ibb.co" to Host.IMGBB
    )

    /** imgchest addresses an album as `/p/<id>`. */
    private const val IMGCHEST_ALBUM_SEGMENT = "p"

    /**
     * Ids are alphanumeric on both hosts: imgchest runs 11 characters (`9rydn3x8d4k`), imgbb 7
     * (`zNBxjX8`). The bounds are deliberately loose — the cost of accepting a non-id is one failed
     * fetch that demotes to a link, whereas rejecting a real id loses the feature silently.
     */
    private val ID = Regex("[A-Za-z0-9]{5,24}")

    /**
     * imgbb puts its own pages at the same single-segment depth as an image (`imgbb.com/about`), so
     * the id shape alone is not enough to tell them apart. This is every single-segment path the
     * site serves that [ID] would otherwise accept.
     */
    private val IMGBB_RESERVED_PATHS = setOf(
        "about", "login", "signup", "upload", "faq", "tos", "terms", "privacy",
        "contact", "explore", "search", "settings", "premium", "codex", "languages"
    )

    /** The [Host] this URL belongs to, or null when it is not one of them. */
    @JvmStatic
    fun hostOf(uri: Uri): Host? {
        val authority = try {
            uri.authority
        } catch (e: IllegalArgumentException) {
            // Uri parses lazily, so a malformed URL throws on the first accessor rather than at
            // Uri.parse(). Matches the guard in ParsePost.applyExternalVideoHost.
            return null
        } ?: return null

        return HOSTS_BY_AUTHORITY[authority.lowercase().removePrefix("www.")]
    }

    /**
     * The album/image id in this URL, or null when the path is not the shape that host uses for
     * one. Read from the path segments only, so a query string cannot contribute an id.
     */
    @JvmStatic
    fun albumIdOf(host: Host, uri: Uri): String? {
        val segments = try {
            uri.pathSegments
        } catch (e: IllegalArgumentException) {
            return null
        } ?: return null

        return when (host) {
            Host.IMGCHEST -> {
                if (segments.size != 2 || !segments[0].equals(IMGCHEST_ALBUM_SEGMENT, ignoreCase = true)) {
                    null
                } else {
                    segments[1].takeIf { ID.matches(it) }
                }
            }

            Host.IMGBB -> {
                if (segments.size != 1) {
                    null
                } else {
                    segments[0].takeIf {
                        ID.matches(it) && it.lowercase() !in IMGBB_RESERVED_PATHS
                    }
                }
            }
        }
    }
}
