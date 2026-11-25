package ml.docilealligator.infinityforreddit.utils

import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * The line [VideoHttpErrorLog] writes is the evidence issue #498 is waiting on, so what it keeps
 * and what it drops is pinned here against the exceptions media3 really throws.
 */
@RunWith(RobolectricTestRunner::class)
class VideoHttpErrorLogTest {

    private val playlist =
        "https://v.redd.it/8h9a39u76d3i1/HLSPlaylist.m3u8?a=1798059246%2CZTI1&v=1&f=sd"

    // The cast is how a null header name gets in: Kotlin will not build that map with the
    // declared key type, but HttpURLConnection hands media3 exactly that.
    @Suppress("UNCHECKED_CAST")
    private fun refusal(
        headers: Map<String?, List<String>>,
        body: ByteArray,
    ): HttpDataSource.InvalidResponseCodeException =
        HttpDataSource.InvalidResponseCodeException(
            403, null, null, headers as Map<String, List<String>>, DataSpec(Uri.parse(playlist)),
            body
        )

    private fun playbackError(cause: Throwable) = PlaybackException(
        "Source error", cause, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
    )

    @Test
    fun refusalNamesTheUrlStatusCdnHeadersAndBody() {
        // OkHttp lower-cases header names and HttpURLConnection does not, and the latter also
        // puts the status line under a null key: both have to be read.
        val headers = hashMapOf<String?, List<String>>(
            null to listOf("HTTP/1.1 403 Forbidden"),
            "X-Reddit-CT" to listOf("v=1,dn=FT,p=PHX,cs=HIT"),
            "age" to listOf("12"),
            "cache-control" to listOf("public, max-age=600, s-maxage=600"),
            "set-cookie" to listOf("session=secret"),
        )

        val line = VideoHttpErrorLog.describe(
            "fullscreen", playbackError(refusal(headers, "Access denied".toByteArray()))
        )

        assertEquals(
            "[fullscreen] ERROR_CODE_IO_BAD_HTTP_STATUS: HTTP 403 for $playlist" +
                "; x-reddit-ct=v=1,dn=FT,p=PHX,cs=HIT; age=12" +
                "; cache-control=public, max-age=600, s-maxage=600" +
                "; body=\"Access denied\" (13 bytes)",
            line
        )
    }

    @Test
    fun refusalIsFoundBelowAnIntermediateCause() {
        val buried = RuntimeException(IOException(refusal(emptyMap(), ByteArray(0))))

        val line = VideoHttpErrorLog.describe("feed", playbackError(buried))

        assertEquals(
            "[feed] ERROR_CODE_IO_BAD_HTTP_STATUS: HTTP 403 for $playlist; body=empty", line
        )
    }

    @Test
    fun networkFailureNamesTheStageUrlAndCause() {
        val timeout = HttpDataSource.HttpDataSourceException(
            SocketTimeoutException("timeout"), DataSpec(Uri.parse(playlist)),
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            HttpDataSource.HttpDataSourceException.TYPE_OPEN
        )
        val error = PlaybackException(
            "Source error", timeout, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        )

        assertEquals(
            "[shadowbox] ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT: open failed for $playlist" +
                ": SocketTimeoutException: timeout",
            VideoHttpErrorLog.describe("shadowbox", error)
        )
    }

    @Test
    fun errorWithNoHttpRequestBehindItLogsNothing() {
        val decoder = PlaybackException(
            "Decoder init failed", IllegalStateException("no codec"),
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        )

        assertNull(VideoHttpErrorLog.describe("fullscreen", decoder))
    }

    @Test
    fun htmlBlockPageIsFlattenedToItsText() {
        val page = "<!doctype html><html><head><style>body { color: red }</style>" +
            "<script>var x = 1;</script></head><body><h1>Blocked</h1>\n" +
            "<p>You&#39;ve been blocked by network security.</p></body></html>"

        assertEquals(
            "\"Blocked You've been blocked by network security.\" (${page.length} bytes)",
            VideoHttpErrorLog.summarizeBody(page.toByteArray())
        )
    }

    @Test
    fun binaryBodyIsReportedBySizeOnly() {
        val bytes = ByteArray(64) { it.toByte() }

        assertEquals("(64 bytes, not text)", VideoHttpErrorLog.summarizeBody(bytes))
    }

    @Test
    fun longBodyIsCut() {
        val summary = VideoHttpErrorLog.summarizeBody("x".repeat(1000).toByteArray())

        assertEquals("\"" + "x".repeat(300) + "…\" (1000 bytes)", summary)
    }

    @Test
    fun clientAddressesAreRedactedFromTheBody() {
        val summary = VideoHttpErrorLog.summarizeBody(
            "Your IP 203.0.113.7 or 2001:db8::1 was blocked".toByteArray()
        )

        assertTrue(summary, summary.contains("Your IP <ip> or <ip> was blocked"))
        assertFalse(summary, summary.contains("203.0.113.7"))
    }

    @Test
    fun redactionCatchesIpv6FormsAndSparesTimesAndHeaderValues() {
        assertEquals("<ip>", VideoHttpErrorLog.redactAddresses("2a04:4e42:200::396"))
        assertEquals(
            "<ip>", VideoHttpErrorLog.redactAddresses("2001:0db8:85a3:0000:0000:8a2e:0370:7334")
        )
        assertEquals("<ip>", VideoHttpErrorLog.redactAddresses("2001:0:0:0:0:0:0:1"))
        assertEquals("at 12:30:45 today", VideoHttpErrorLog.redactAddresses("at 12:30:45 today"))
        assertEquals("v=1,dn=FT,p=PHX", VideoHttpErrorLog.redactAddresses("v=1,dn=FT,p=PHX"))
    }
}
