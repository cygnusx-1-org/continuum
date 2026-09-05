package ml.docilealligator.infinityforreddit.utils

import android.net.Uri
import ml.docilealligator.infinityforreddit.TestInfinity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [RedgifsUrlUtils.playbackUri] is the whole of issue #388's fix: a Redgifs post plays a
 * progressive MP4 with one video track, so the resolution preference can only be honoured by
 * swapping the file. These pin both halves -- that the 480p setting actually reaches the `-mobile`
 * file, and that nothing else in the app's URI traffic gets rewritten on the way past.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class RedgifsUrlUtilsTest {

    private val hd = Uri.parse("https://media.redgifs.com/LightslategrayWaryIndianabat.mp4")
    private val sd = Uri.parse("https://media.redgifs.com/LightslategrayWaryIndianabat-mobile.mp4")

    @Test
    fun the480pSettingPicksTheMobileFile() {
        assertEquals(sd, RedgifsUrlUtils.playbackUri(hd, isDataSavingMode = true, redgifsResolution = 480))
    }

    @Test
    fun the1080pSettingStaysOnHd() {
        assertEquals(hd, RedgifsUrlUtils.playbackUri(hd, isDataSavingMode = true, redgifsResolution = 1080))
    }

    @Test
    fun anUnrecognisedStoredValueFailsSafeOntoHd() {
        // The preference only ever writes 1080 or 480, but a value left behind by an older build
        // must not be read as licence to downgrade. Anything outside 1..480 keeps the better copy.
        for (resolution in intArrayOf(0, -1, 360, 720)) {
            val expected = if (resolution == 360) sd else hd
            assertEquals(
                "stored value $resolution",
                expected,
                RedgifsUrlUtils.playbackUri(hd, isDataSavingMode = true, redgifsResolution = resolution)
            )
        }
    }

    @Test
    fun dataSavingOffStaysOnHd() {
        assertEquals(hd, RedgifsUrlUtils.playbackUri(hd, isDataSavingMode = false, redgifsResolution = 480))
    }

    @Test
    fun rewriteIsIdempotent() {
        assertEquals(sd, RedgifsUrlUtils.playbackUri(sd, isDataSavingMode = true, redgifsResolution = 480))
    }

    @Test
    fun queryStringSurvivesTheRewrite() {
        // Redgifs serves these unsigned today but has handed out expiring signed URLs before.
        val signed = Uri.parse("https://media.redgifs.com/Name.mp4?expires=123&token=abc")
        assertEquals(
            Uri.parse("https://media.redgifs.com/Name-mobile.mp4?expires=123&token=abc"),
            RedgifsUrlUtils.playbackUri(signed, isDataSavingMode = true, redgifsResolution = 480)
        )
    }

    @Test
    fun nonRedgifsUrisPassStraightThrough() {
        // Every URI the ViewModel publishes goes through playbackUri, not just the Redgifs ones:
        // the v.redd.it fallback, Streamable, Imgur and Reddit's own HLS all have to come back
        // untouched, or data saving would mangle links that have a real track ladder.
        val untouched = listOf(
            "https://v.redd.it/abc123/DASH_480.mp4",
            "https://v.redd.it/abc123/HLSPlaylist.m3u8",
            "https://cdn-cf-east.streamable.com/video/mp4/abc.mp4",
            "https://i.imgur.com/abc.mp4",
            "https://i.redgifs.com/i/Name.jpg"
        )
        for (url in untouched) {
            val uri = Uri.parse(url)
            assertEquals(
                "$url should not be rewritten",
                uri,
                RedgifsUrlUtils.playbackUri(uri, isDataSavingMode = true, redgifsResolution = 480)
            )
        }
    }

    @Test
    fun nullPassesThrough() {
        assertEquals(null, RedgifsUrlUtils.playbackUri(null, isDataSavingMode = true, redgifsResolution = 480))
    }
}
