package ml.docilealligator.infinityforreddit.utils

import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import java.io.IOException
import java.nio.charset.Charset

/**
 * Reads a third-party HTML page safely enough to run a regex over it.
 *
 * Two callers scrape pages the app does not control: the link-post title suggester
 * ([TitleSuggestionUtils]) and the short-clip video resolver
 * ([FetchShortClipVideo][ml.docilealligator.infinityforreddit.post.FetchShortClipVideo]). Both hand
 * an arbitrary user- or poster-supplied URL to OkHttp, so both face the same three hazards, and the
 * rules below are the ones the title suggester arrived at:
 *
 *  - the response may not be markup at all (a direct link to a video or a disk image), so
 *    [isMarkup] rejects it before a byte is downloaded;
 *  - it may be enormous, so [readBoundedText] stops at [MAX_BODY_BYTES];
 *  - it may not be UTF-8, so [readBoundedText] sniffs the encoding rather than assuming one.
 *
 * Anything that parses the resulting string must also assume the page is hostile. A lazy quantifier
 * over a whole document is the specific trap: see the comment on
 * [TitleSuggestionUtils.parseHtmlTitle] for the page that took ~55 seconds.
 */
object HtmlBodyUtils {

    /**
     * Neither caller needs to see deep into a page. The deepest `<title>` seen on a real page is
     * ~636 KB in (the desktop youtube.com watch page) and the clip hosts put their `<video>` tag
     * and their `og:` meta tags in the first few KB, so this comfortably clears both.
     */
    const val MAX_BODY_BYTES = 1L * 1024 * 1024

    private const val CHARSET_SNIFF_BYTES = 4096

    private val META_CHARSET = Regex("charset\\s*=\\s*[\"']?([A-Za-z0-9_:.+-]+)", RegexOption.IGNORE_CASE)

    /**
     * A missing Content-Type is given the benefit of the doubt; `video/mp4` is not. The `+xml`
     * family covers RSS and Atom feeds (e.g. Reddit's `application/atom+xml` .rss endpoints), which
     * carry a `<title>` the old unconditional scraper picked up.
     */
    @JvmStatic
    fun isMarkup(contentType: MediaType?): Boolean {
        if (contentType == null) {
            return true
        }
        val subtype = contentType.subtype.lowercase()
        return contentType.type.lowercase() == "text" ||
            subtype == "html" || subtype == "xml" || subtype.endsWith("+xml")
    }

    /**
     * Decodes at most [MAX_BODY_BYTES] of [body]. Does not close it — the caller owns the body and
     * is expected to have it in a `use` block.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun readBoundedText(body: ResponseBody): String {
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
    @JvmStatic
    fun charsetOf(contentType: MediaType?, bytes: ByteArray): Charset {
        contentType?.charset()?.let { return it }

        val head = String(bytes, 0, minOf(bytes.size, CHARSET_SNIFF_BYTES), Charsets.ISO_8859_1)
        val name = META_CHARSET.find(head)?.groupValues?.get(1) ?: return Charsets.UTF_8

        return try {
            if (Charset.isSupported(name)) Charset.forName(name) else Charsets.UTF_8
        } catch (e: IllegalArgumentException) {
            Charsets.UTF_8
        }
    }
}
