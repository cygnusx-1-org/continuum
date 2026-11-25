package ml.docilealligator.infinityforreddit.video

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * A refused playlist gives up at once, so the DASH retry starts without the three-second wait
 * issue #498's logs showed; everything else keeps media3's retries.
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class RefusedPlaylistLoadErrorPolicyTest {

    private val playlist = DataSpec(Uri.parse("https://v.redd.it/ezb7hozc3i3i1/HLSPlaylist.m3u8"))
    private val policy = RefusedPlaylistLoadErrorPolicy()
    private val default = DefaultLoadErrorHandlingPolicy()

    private fun status(code: Int) = HttpDataSource.InvalidResponseCodeException(
        code, null, null, emptyMap(), playlist, ByteArray(0)
    )

    private fun info(dataType: Int, error: IOException, errorCount: Int = 1) =
        LoadErrorHandlingPolicy.LoadErrorInfo(
            LoadEventInfo(1, playlist, 0), MediaLoadData(dataType), error, errorCount
        )

    @Test
    fun mediaThreeRetriesARefusedPlaylistByDefault() {
        // The wait this policy removes; if media3 stops retrying, the policy is no longer needed.
        assertNotEquals(C.TIME_UNSET, default.getRetryDelayMsFor(info(C.DATA_TYPE_MANIFEST, status(403))))
    }

    @Test
    fun aRefusedOrMissingPlaylistIsNotRetried() {
        for (code in listOf(403, 404, 410)) {
            for (errorCount in 1..3) {
                assertEquals(
                    "HTTP $code, attempt $errorCount",
                    C.TIME_UNSET,
                    policy.getRetryDelayMsFor(info(C.DATA_TYPE_MANIFEST, status(code), errorCount))
                )
            }
        }
    }

    @Test
    fun everythingElseKeepsTheDefaultRetries() {
        val cases = listOf(
            info(C.DATA_TYPE_MANIFEST, status(500)),
            info(C.DATA_TYPE_MANIFEST, status(503), errorCount = 2),
            info(C.DATA_TYPE_MANIFEST, SocketTimeoutException("timeout")),
            info(C.DATA_TYPE_MEDIA, status(403)),
            info(C.DATA_TYPE_MEDIA_INITIALIZATION, status(404)),
        )
        for (case in cases) {
            assertEquals(default.getRetryDelayMsFor(case), policy.getRetryDelayMsFor(case))
        }
    }
}
