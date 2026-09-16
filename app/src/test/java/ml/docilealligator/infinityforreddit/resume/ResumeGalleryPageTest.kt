package ml.docilealligator.infinityforreddit.resume

import ml.docilealligator.infinityforreddit.TestInfinity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a screen loses when it is not the one the user was looking at.
 *
 * Both gallery keys are stripped by the same call, from code that knows nothing else about the
 * screens it is stripping, so the one thing to pin is that it takes the pages and leaves everything
 * else -- a screen below the top still has to come back where it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class ResumeGalleryPageTest {

    @Test
    fun `both kinds of gallery page are stripped`() {
        // The post-detail screen records one page and a feed records a list of them. A caller that
        // knew about only one would leave the other behind on every screen in the stack.
        val state = JSONObject()
            .put(ResumeGalleryPage.KEY_POST_DETAIL, 3)
            .put(ResumeGalleryPage.KEY_FEED, "t3_abc:2")

        ResumeGalleryPage.stripFrom(state)

        assertFalse(state.has(ResumeGalleryPage.KEY_POST_DETAIL))
        assertFalse(state.has(ResumeGalleryPage.KEY_FEED))
    }

    @Test
    fun `everything else the screen recorded is left alone`() {
        val state = JSONObject()
            .put(ResumeGalleryPage.KEY_POST_DETAIL, 3)
            .put("resumeAnchorFullname", "t3_abc")
            .put("resumeAnchorOffset", -529)

        ResumeGalleryPage.stripFrom(state)

        assertEquals("t3_abc", state.getString("resumeAnchorFullname"))
        assertEquals(-529, state.getInt("resumeAnchorOffset"))
    }

    @Test
    fun `a screen that recorded no page is unchanged`() {
        val state = JSONObject().put("resumeAnchorFullname", "t3_abc")

        ResumeGalleryPage.stripFrom(state)

        assertEquals(1, state.length())
    }

    @Test
    fun `a screen with no state at all is not a problem`() {
        ResumeGalleryPage.stripFrom(null)
    }

    @Test
    fun `the keys are the ones the screens actually write`() {
        // Two files write these and this one strips them, so a rename that missed a site would
        // leave the page in the snapshot on every screen, silently.
        assertEquals(FeedResumeState.KEY_GALLERY_PAGES, ResumeGalleryPage.KEY_FEED)
        assertTrue(ResumeGalleryPage.KEY_POST_DETAIL.isNotEmpty())
    }
}
