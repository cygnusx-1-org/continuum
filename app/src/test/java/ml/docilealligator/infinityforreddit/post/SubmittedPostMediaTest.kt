package ml.docilealligator.infinityforreddit.post

import ml.docilealligator.infinityforreddit.TestInfinity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [SubmittedPostMedia]: a just-submitted gallery reports `"unprocessed"` items for about a second
 * (measured on r/test: unprocessed at +0.5 s, valid at +1.2 s), and opening the post in that window
 * showed it as a text post.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class SubmittedPostMediaTest {

    private fun info(vararg statuses: String): String {
        val metadata = statuses.mapIndexed { i, status -> "\"m$i\":{\"status\":\"$status\"}" }.joinToString(",")
        return """{"kind":"Listing","data":{"children":[{"kind":"t3","data":{"media_metadata":{$metadata}}}]}}"""
    }

    @Test
    fun `an unprocessed item is still processing, valid and failed ones are not`() {
        assertTrue(SubmittedPostMedia.hasUnprocessedMedia(info("valid", "unprocessed")))
        assertFalse(SubmittedPostMedia.hasUnprocessedMedia(info("valid", "valid")))
        assertFalse(SubmittedPostMedia.hasUnprocessedMedia(info("failed")))
        assertFalse(SubmittedPostMedia.hasUnprocessedMedia("""{"data":{"children":[]}}"""))
        assertFalse(SubmittedPostMedia.hasUnprocessedMedia("""{"data":{"children":[{"data":{}}]}}"""))
    }

    @Test
    fun `the wait ends as soon as the images are processed`() {
        val replies = ArrayDeque(listOf(info("unprocessed", "unprocessed"), info("valid", "valid"), info("valid")))

        assertEquals(2, SubmittedPostMedia.awaitProcessed(0) { replies.removeFirst() })
    }

    @Test
    fun `the wait gives up after its attempts`() {
        var fetches = 0

        assertEquals(10, SubmittedPostMedia.awaitProcessed(0) { fetches++; info("unprocessed") })
        assertEquals(10, fetches)
    }

    @Test
    fun `an unreadable reply ends the wait`() {
        assertEquals(1, SubmittedPostMedia.awaitProcessed(0) { null })
        assertEquals(1, SubmittedPostMedia.awaitProcessed(0) { "not json" })
    }
}
