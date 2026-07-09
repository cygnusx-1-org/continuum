package ml.docilealligator.infinityforreddit.utils

import androidx.core.text.HtmlCompat
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.Charset

/**
 * Resolves the title to suggest for a link post.
 *
 * YouTube videos and playlists are resolved through YouTube's oEmbed endpoint rather than by
 * scraping the watch page: the page is ~1.3 MB, its `<title>` carries a " - YouTube" suffix, the
 * mobile page's `<head>` title is the bare string "YouTube", and cookieless requests can be bounced
 * to consent.youtube.com, which answers 400. oEmbed answers with <1 KB of JSON holding the exact
 * title.
 *
 * oEmbed only serves videos and playlists, so YouTube URLs of any other shape (channels, search
 * results, feeds) fall through to the HTML scrape, as does every non-YouTube host.
 *
 * [readTitle] reads the response and must be called off the main thread.
 */
object TitleSuggestionUtils {

    private const val YOUTU_BE_HOST = "youtu.be"

    private val YOUTUBE_HOSTS = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "youtube-nocookie.com",
        "www.youtube-nocookie.com",
        YOUTU_BE_HOST
    )

    /** `/embed/videoseries?list=…` addresses a playlist rather than a video with this id. */
    private const val EMBED_PLAYLIST_ID = "videoseries"

    /**
     * The link field accepts any URL, including direct links to videos and disk images, so the body
     * is never read whole. The deepest `<title>` seen on a real page is ~636 KB in (the desktop
     * youtube.com watch page), which this comfortably clears.
     */
    private const val MAX_BODY_BYTES = 1L * 1024 * 1024

    private const val CHARSET_SNIFF_BYTES = 4096

    private val META_CHARSET = Regex("charset\\s*=\\s*[\"']?([A-Za-z0-9_:.+-]+)", RegexOption.IGNORE_CASE)

    /** The attribute list must start on whitespace, otherwise `<titlecase>` would match too. */
    private val TITLE_OPEN = Regex("<title(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE)

    private const val TITLE_CLOSE = "</title>"

    /** `\p{Z}` catches U+00A0 (decoded from `&nbsp;`) and the Unicode spaces that `\s` misses. */
    private val WHITESPACE = Regex("[\\s\\p{Z}]+")

    /**
     * Returns the oEmbed URL to fetch the title of [url], or null when [url] is not a YouTube video
     * or playlist and the HTML `<title>` should be scraped instead.
     */
    @JvmStatic
    fun youTubeOEmbedUrl(url: String): String? {
        // toHttpUrlOrNull canonicalizes the host to lowercase.
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        if (httpUrl.host !in YOUTUBE_HOSTS) {
            return null
        }
        val embeddableUrl = embeddableUrl(httpUrl) ?: return null

        return HttpUrl.Builder()
            .scheme("https")
            .host("www.youtube.com")
            .addPathSegment("oembed")
            .addQueryParameter("format", "json")
            .addQueryParameter("url", embeddableUrl)
            .build()
            .toString()
    }

    /**
     * Narrows a YouTube URL to one oEmbed actually accepts, or null if it accepts none. oEmbed 404s
     * on channels, handles, search results and feeds, and — despite serving the videos they play —
     * on `/embed/` URLs.
     *
     * Accepted URLs are rebuilt in canonical lowercase `www.youtube.com` form. The route keyword is
     * matched case-insensitively: YouTube itself misroutes a hand-typed `/WATCH` (channel lookup)
     * and 404s `/Shorts`, but the URL still unambiguously names its video, and oEmbed only accepts
     * the lowercase spelling. Video and playlist ids are case-sensitive and pass through untouched —
     * lowercasing `dQw4w9WgXcQ` would name a different video.
     */
    private fun embeddableUrl(url: HttpUrl): String? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }

        if (url.host == YOUTU_BE_HOST) {
            // youtu.be/{videoId}, and nothing else. The whole path is a case-sensitive id.
            return if (segments.size == 1) url.toString() else null
        }

        return when (segments.firstOrNull()?.lowercase()) {
            "watch" -> queryUrlOrNull("watch", "v", url.queryParameter("v"))
            "playlist" -> queryUrlOrNull("playlist", "list", url.queryParameter("list"))
            "shorts", "live" -> segments.getOrNull(1)?.let { id ->
                youTubePathUrl(segments[0].lowercase(), id)
            }
            "embed" -> canonicalEmbedUrl(url, segments)
            else -> null
        }
    }

    private fun queryUrlOrNull(pathSegment: String, queryName: String, queryValue: String?): String? {
        return if (queryValue.isNullOrEmpty()) null else youTubeUrl(pathSegment, queryName, queryValue)
    }

    private fun youTubePathUrl(first: String, second: String): String {
        return HttpUrl.Builder()
            .scheme("https")
            .host("www.youtube.com")
            .addPathSegment(first)
            .addPathSegment(second)
            .build()
            .toString()
    }

    private fun canonicalEmbedUrl(url: HttpUrl, segments: List<String>): String? {
        val id = segments.getOrNull(1) ?: return null
        if (id != EMBED_PLAYLIST_ID) {
            return youTubeUrl("watch", "v", id)
        }

        val listId = url.queryParameter("list")
        return if (listId.isNullOrEmpty()) null else youTubeUrl("playlist", "list", listId)
    }

    private fun youTubeUrl(pathSegment: String, queryName: String, queryValue: String): String {
        return HttpUrl.Builder()
            .scheme("https")
            .host("www.youtube.com")
            .addPathSegment(pathSegment)
            .addQueryParameter(queryName, queryValue)
            .build()
            .toString()
    }

    /**
     * Consumes and closes [body], returning the suggested title or null. Blocks on the network, so
     * it must not run on the main thread. At most [MAX_BODY_BYTES] are read, and a body that is not
     * markup is rejected before any of it is downloaded.
     */
    @JvmStatic
    fun readTitle(body: ResponseBody, oEmbed: Boolean): String? {
        body.use {
            if (!oEmbed && !isMarkup(it.contentType())) {
                return null
            }

            val text = try {
                readBoundedText(it)
            } catch (e: IOException) {
                return null
            }

            return if (oEmbed) parseOEmbedTitle(text) else parseHtmlTitle(text)
        }
    }

    /**
     * A missing Content-Type is given the benefit of the doubt; `video/mp4` is not. The `+xml`
     * family covers RSS and Atom feeds (e.g. Reddit's `application/atom+xml` .rss endpoints), which
     * carry a `<title>` the old unconditional scraper picked up.
     */
    private fun isMarkup(contentType: MediaType?): Boolean {
        if (contentType == null) {
            return true
        }
        val subtype = contentType.subtype.lowercase()
        return contentType.type.lowercase() == "text" ||
            subtype == "html" || subtype == "xml" || subtype.endsWith("+xml")
    }

    @Throws(IOException::class)
    private fun readBoundedText(body: ResponseBody): String {
        val source = body.source()
        val buffer = Buffer()
        var read = 0L
        while (read < MAX_BODY_BYTES) {
            val count = source.read(buffer, MAX_BODY_BYTES - read)
            if (count == -1L) {
                break
            }
            read += count
        }

        val bytes = buffer.readByteArray()
        return String(bytes, charsetOf(body.contentType(), bytes))
    }

    /**
     * Prefers the charset from the Content-Type header, then a `<meta charset>` declaration near the
     * top of the document. Without the sniff, pages that only declare their encoding in markup —
     * still common on older Shift_JIS and ISO-8859-1 sites — decode to mojibake.
     */
    private fun charsetOf(contentType: MediaType?, bytes: ByteArray): Charset {
        contentType?.charset()?.let { return it }

        val head = String(bytes, 0, minOf(bytes.size, CHARSET_SNIFF_BYTES), Charsets.ISO_8859_1)
        val name = META_CHARSET.find(head)?.groupValues?.get(1) ?: return Charsets.UTF_8

        return try {
            if (Charset.isSupported(name)) Charset.forName(name) else Charsets.UTF_8
        } catch (e: IllegalArgumentException) {
            Charsets.UTF_8
        }
    }

    @JvmStatic
    fun parseOEmbedTitle(json: String?): String? {
        if (json.isNullOrBlank()) {
            return null
        }

        return try {
            val response = JSONObject(json)
            // Android's optString renders a JSON null as the literal string "null".
            if (response.isNull("title")) null else normalizeTitle(response.optString("title"))
        } catch (e: JSONException) {
            null
        }
    }

    @JvmStatic
    fun parseHtmlTitle(html: String?): String? {
        if (html.isNullOrEmpty()) {
            return null
        }

        // Find the open tag, then scan forward for the close. A lazy `<title...>(.*?)</title>`
        // rescans the rest of the document once per open tag that never closes: 20k of them in a
        // 340 KB page took ~55 seconds. Taking the first match is also what confines the result to
        // the head, since <head> precedes <body>, so an inline <svg><title> logo cannot outrank it.
        val open = TITLE_OPEN.find(html) ?: return null
        val start = open.range.last + 1
        val end = html.indexOf(TITLE_CLOSE, start, ignoreCase = true)
        if (end < 0) {
            return null
        }

        return normalizeTitle(
            HtmlCompat.fromHtml(html.substring(start, end), HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        )
    }

    private fun normalizeTitle(title: String): String? {
        return title
            .replace(WHITESPACE, " ")
            .trim()
            .ifEmpty { null }
    }
}
