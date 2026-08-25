package ml.docilealligator.infinityforreddit.post

import ml.docilealligator.infinityforreddit.TestInfinity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Recovering a removed post starts by deciding that a post really is removed, and ends by rebuilding
 * the pieces the removal stripped. Both halves are prose contracts in [FetchRemovedPost] that nothing
 * enforced: the placeholder text comes in several shapes (a bare `[removed]`, and the sentences a
 * moderator or a Reddit admin leaves, which carry a reason after the words the check matches on), and
 * flair is user-written text on its way into HTML.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class RemovedPostRecoveryTest {

    @Test
    fun `the placeholders reddit leaves behind are recognised whatever they carry after them`() {
        assertTrue(FetchRemovedPost.isRemovalPlaceholder("[removed]"))
        assertTrue(FetchRemovedPost.isRemovalPlaceholder("[deleted]"))
        assertTrue("case is not part of the sentinel", FetchRemovedPost.isRemovalPlaceholder("[Removed]"))
        assertTrue(
            "a moderator removal names its reason after the words matched on",
            FetchRemovedPost.isRemovalPlaceholder("[ Removed by moderator: spam ]")
        )
        assertTrue(
            "an admin takedown names its policy after the words matched on",
            FetchRemovedPost.isRemovalPlaceholder("[ Removed by Reddit on behalf of a legal request ]")
        )
    }

    @Test
    fun `a placeholder still counts when the archive left whitespace around it`() {
        assertTrue(FetchRemovedPost.isRemovalPlaceholder("  [removed]\n"))
        assertTrue(FetchRemovedPost.isRemovalPlaceholder("\n[ Removed by moderator ]  "))
    }

    @Test
    fun `an ordinary body is not a placeholder`() {
        assertFalse(FetchRemovedPost.isRemovalPlaceholder(null))
        assertFalse(FetchRemovedPost.isRemovalPlaceholder(""))
        assertFalse(FetchRemovedPost.isRemovalPlaceholder("a normal comment"))
        assertFalse(
            "a post that merely talks about removals is not itself removed",
            FetchRemovedPost.isRemovalPlaceholder("the mods wrote [removed] under my post")
        )
    }

    @Test
    fun `recovered flair text cannot put markup into the rendered flair`() {
        val obj = JSONObject().put(
            "author_flair_richtext",
            JSONArray().put(JSONObject().put("e", "text").put("t", "<b>mod</b> & friends"))
        )

        assertEquals("&lt;b&gt;mod&lt;/b&gt; &amp; friends", FetchRemovedPost.parseAuthorFlairHtml(obj))
    }

    @Test
    fun `a flair emoji is rebuilt as an image and a missing flair is empty`() {
        val emoji = JSONObject().put(
            "author_flair_richtext",
            JSONArray()
                .put(JSONObject().put("e", "emoji").put("u", "https://emoji.redditmedia.com/x_t5_2.png"))
                .put(JSONObject().put("e", "text").put("t", " verified"))
        )

        assertEquals(
            "<img src=\"https://emoji.redditmedia.com/x_t5_2.png\"> verified",
            FetchRemovedPost.parseAuthorFlairHtml(emoji)
        )
        assertEquals("", FetchRemovedPost.parseAuthorFlairHtml(JSONObject()))
    }

    @Test
    fun `an absent plain flair is empty rather than the word null`() {
        assertEquals("", FetchRemovedPost.parseAuthorFlairText(JSONObject().put("author_flair_text", JSONObject.NULL)))
        assertEquals("", FetchRemovedPost.parseAuthorFlairText(JSONObject()))
        assertEquals("Sponsor", FetchRemovedPost.parseAuthorFlairText(JSONObject().put("author_flair_text", "Sponsor")))
    }
}
