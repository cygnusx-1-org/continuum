package ml.docilealligator.infinityforreddit.utils

import android.net.Uri
import ml.docilealligator.infinityforreddit.TestInfinity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * r/baseball posts highlights as direct MP4 links, and the rendition the poster copies is almost
 * always the largest one: measured over four clips the high rung ran 32.7 MB to 89.5 MB against
 * 8.6 MB to 23.3 MB for the low one, at the same 1280x720 frame. These pin that data saving
 * actually reaches the smaller file, and that nothing else in the app's URI traffic is rewritten.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class MlbUrlUtilsTest {

    private val base = "https://mlb-cuts-diamond.mlb.com/FORGE/2026/2026-09/09/" +
        "4b7a1a43-10dff22b-30483077-csvm-diamondgcp-asset_1280x720_59"

    private val high = Uri.parse("${base}_16000K.mp4")
    private val low = Uri.parse("${base}_4000K.mp4")

    @Test
    fun theLowRungSettingPicksTheSmallerFile() {
        assertEquals(low, MlbUrlUtils.playbackUri(high, isDataSavingMode = true, mlbBitrate = MlbUrlUtils.BITRATE_LOW))
    }

    @Test
    fun theHighRungSettingKeepsThePostedFile() {
        assertEquals(high, MlbUrlUtils.playbackUri(high, isDataSavingMode = true, mlbBitrate = MlbUrlUtils.BITRATE_HIGH))
    }

    @Test
    fun dataSavingOffKeepsThePostedFile() {
        assertEquals(high, MlbUrlUtils.playbackUri(high, isDataSavingMode = false, mlbBitrate = MlbUrlUtils.BITRATE_LOW))
    }

    @Test
    fun rewriteIsIdempotent() {
        // Posters do sometimes copy the low rung directly; it must not be mangled further.
        assertEquals(low, MlbUrlUtils.playbackUri(low, isDataSavingMode = true, mlbBitrate = MlbUrlUtils.BITRATE_LOW))
    }

    @Test
    fun theWalkBackUpRecoversThePostedFile() {
        // The low rung is derived rather than confirmed, and an MLB post carries no
        // videoFallBackDirectUrl, so without this a missing rendition would turn a playable clip
        // into an error.
        assertEquals(high, MlbUrlUtils.postedVariant(low))
    }

    @Test
    fun thePostedFileHasNothingToWalkBackUpTo() {
        assertNull(MlbUrlUtils.postedVariant(high))
        assertNull(MlbUrlUtils.postedVariant(Uri.parse("https://media.redgifs.com/Name-mobile.mp4")))
    }

    @Test
    fun theOtherMlbHostHasOnlyOneRendition() {
        // bdata-producedclips URLs are a bare UUID at around 11 MB, with no ladder to walk.
        val produced = Uri.parse("https://bdata-producedclips.mlb.com/9b3c60cf-3dee-4a85-95cd-23d7f015f5e6.mp4")
        assertTrue(MlbUrlUtils.isMlbClip(produced))
        assertEquals(produced, MlbUrlUtils.playbackUri(produced, isDataSavingMode = true, mlbBitrate = MlbUrlUtils.BITRATE_LOW))
    }

    @Test
    fun onlyTheTwoClipHostsCount() {
        // Deliberately narrower than "any link ending in .mp4": promoting every one of those would
        // change how unrelated link posts render across the whole app.
        assertTrue(MlbUrlUtils.isMlbClip(high))
        assertFalse(MlbUrlUtils.isMlbClip(Uri.parse("https://www.mlb.com/news/some-story")))
        assertFalse(MlbUrlUtils.isMlbClip(Uri.parse("https://mlb-cuts-diamond.mlb.com/FORGE/clip.m3u8")))
        assertFalse(MlbUrlUtils.isMlbClip(Uri.parse("https://example.com/asset_1280x720_59_16000K.mp4")))
    }

    @Test
    fun nonMlbUrisPassStraightThrough() {
        // Every URI the fullscreen ViewModel publishes goes through playbackUri, not just MLB ones.
        val untouched = listOf(
            "https://v.redd.it/abc123/DASH_480.mp4",
            "https://cdn.streamain.com/guests/tdlhGetpSBdLulv_1789017834.mp4",
            "https://media.redgifs.com/Name.mp4"
        )
        for (url in untouched) {
            val uri = Uri.parse(url)
            assertEquals(
                "$url should not be rewritten",
                uri,
                MlbUrlUtils.playbackUri(uri, isDataSavingMode = true, mlbBitrate = MlbUrlUtils.BITRATE_LOW)
            )
        }
    }

    @Test
    fun nullPassesThrough() {
        assertNull(MlbUrlUtils.playbackUri(null, isDataSavingMode = true, mlbBitrate = MlbUrlUtils.BITRATE_LOW))
        assertFalse(MlbUrlUtils.isMlbClip(null))
    }
}
