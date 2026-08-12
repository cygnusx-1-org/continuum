package ml.docilealligator.infinityforreddit.utils

import android.app.Application
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicLong

/**
 * Covers [TitleSuggestionUtils.readTitle], which consumes a streaming response body. Robolectric
 * supplies the real HtmlCompat used to unescape the extracted title.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class TitleSuggestionReaderTest {

    private fun body(content: String, contentType: String?) =
        content.toResponseBody(contentType?.toMediaTypeOrNull())

    /** An 8 MiB body of filler, counting the bytes actually pulled from it. */
    private fun countingBody(contentType: String?, served: AtomicLong) =
        object : Source {
            private var remaining = 8L * 1024 * 1024

            override fun read(sink: Buffer, byteCount: Long): Long {
                if (remaining == 0L) return -1L
                val n = minOf(byteCount, 8192L, remaining)
                sink.write(ByteArray(n.toInt()) { 'x'.code.toByte() })
                remaining -= n
                served.addAndGet(n)
                return n
            }

            override fun timeout(): Timeout = Timeout.NONE
            override fun close() = Unit
        }.buffer().asResponseBody(contentType?.toMediaTypeOrNull(), -1L)

    @Test
    fun htmlBodyYieldsItsTitle() {
        assertEquals("Hello", TitleSuggestionUtils.readTitle(
            body("<html><head><title>Hello</title></head></html>", "text/html"), false))
    }

    @Test
    fun oEmbedBodyYieldsItsTitle() {
        assertEquals("Never Gonna Give You Up", TitleSuggestionUtils.readTitle(
            body("{\"title\":\"Never Gonna Give You Up\"}", "application/json"), true))
    }

    @Test
    fun nonMarkupBodiesAreRejectedWithoutBeingRead() {
        // A direct link to a video must not be pulled into memory looking for a <title>.
        for (type in listOf("video/mp4", "image/jpeg", "application/octet-stream", "application/zip")) {
            val served = AtomicLong(0)
            assertNull(type, TitleSuggestionUtils.readTitle(countingBody(type, served), false))
            assertEquals("$type must not be read at all", 0L, served.get())
        }
    }

    @Test
    fun markupContentTypesAreAccepted() {
        val types = listOf(
            "text/html", "TEXT/HTML; charset=utf-8", "application/xhtml+xml", "text/plain",
            "application/xml", "text/xml",
            // RSS/Atom feeds carry a <title>; Reddit's own .rss is application/atom+xml.
            "application/rss+xml", "application/atom+xml"
        )
        for (type in types) {
            assertEquals(type, "Hi", TitleSuggestionUtils.readTitle(
                body("<head><title>Hi</title></head>", type), false))
        }
    }

    @Test
    fun missingContentTypeIsGivenTheBenefitOfTheDoubt() {
        assertEquals("Hi", TitleSuggestionUtils.readTitle(
            body("<head><title>Hi</title></head>", null), false))
    }

    @Test
    fun oEmbedPathIgnoresContentType() {
        // YouTube's oEmbed answers application/json; the gate must not apply to it.
        assertEquals("Vid", TitleSuggestionUtils.readTitle(body("{\"title\":\"Vid\"}", null), true))
    }

    @Test
    fun titleDeepInsideALargePageIsStillFound() {
        // Mirrors the desktop youtube.com watch page: <title> ~636 KB in.
        val html = "<html><head>" + "<meta name=\"x\" content=\"y\">".repeat(24_000) +
            "<title>Deep Title</title></head><body>hi</body></html>"
        assertTrue("fixture must exceed 600 KB", html.length > 600_000)
        assertEquals("Deep Title", TitleSuggestionUtils.readTitle(body(html, "text/html"), false))
    }

    @Test
    fun atMostOneMebibyteIsReadFromAHugeBody() {
        // Offered 8 MiB; an unbounded read would consume all of it.
        val served = AtomicLong(0)
        assertNull(TitleSuggestionUtils.readTitle(countingBody("text/html", served), false))
        assertEquals("read must stop at 1 MiB", 1024L * 1024L, served.get())
    }

    @Test
    fun titleBeyondTheReadLimitIsNotFound() {
        val html = "<html><head>" + "x".repeat(1024 * 1024) + "<title>Too Deep</title></head>"
        assertNull(TitleSuggestionUtils.readTitle(body(html, "text/html"), false))
    }

    @Test
    fun charsetFromContentTypeHeaderIsHonoured() {
        val bytes = "<head><title>Grüße</title></head>".toByteArray(Charsets.ISO_8859_1)
        val rb = bytes.toResponseBody("text/html; charset=ISO-8859-1".toMediaTypeOrNull())
        assertEquals("Grüße", TitleSuggestionUtils.readTitle(rb, false))
    }

    @Test
    fun charsetIsSniffedFromMetaWhenTheHeaderOmitsIt() {
        // Without the sniff this decodes as UTF-8 and yields mojibake.
        val html = "<html><head><meta charset=\"ISO-8859-1\"><title>Grüße</title></head>"
        val rb = html.toByteArray(Charsets.ISO_8859_1).toResponseBody("text/html".toMediaTypeOrNull())
        assertEquals("Grüße", TitleSuggestionUtils.readTitle(rb, false))
    }

    @Test
    fun anUnknownSniffedCharsetFallsBackToUtf8() {
        val html = "<html><head><meta charset=\"not a charset!\"><title>Hi</title></head>"
        val rb = html.toByteArray(Charsets.UTF_8).toResponseBody("text/html".toMediaTypeOrNull())
        assertEquals("Hi", TitleSuggestionUtils.readTitle(rb, false))
    }
}
