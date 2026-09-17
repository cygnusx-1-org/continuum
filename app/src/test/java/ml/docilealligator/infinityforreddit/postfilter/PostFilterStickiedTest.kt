package ml.docilealligator.infinityforreddit.postfilter

import ml.docilealligator.infinityforreddit.post.Post
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A pinned post is filtered like any other.
 *
 * It was exempt from every rule but NSFW once, so a subreddit's stickied daily thread survived the
 * author, flair and keyword exclusions written to hide it and the comment limit that should have
 * caught it, while the same thread from the day before -- identical but no longer pinned -- was
 * hidden (issue #420). Nothing on screen distinguished the two, and on a multireddit, where the pin
 * is not even drawn, the post looked like a filter that had simply stopped working.
 *
 * Staying visible after it has been read is the part that is deliberate, and it lives in
 * `PostViewModel#isPostVisible` rather than here.
 */
class PostFilterStickiedTest {

    private fun stickiedPost(
        title: String = "General Discussion - Daily Thread",
        author: String = "AutoModerator",
        flair: String = "🗨️",
        nComments: Int = 6,
    ) = Post(
        "id", "t3_id", "bapcsalescanada", "r/bapcsalescanada", author, "t2_author", "", "", 0L,
        title, "https://example.com/x", "/r/bapcsalescanada/comments/id/", 10, 0, 0, nComments,
        100, flair, false, false, false, /* stickied = */ true, false, false, false, false,
        false, false, false, 0L, null, false, false, "", null,
    )

    @Test
    fun `an excluded author hides a pinned post`() {
        val postFilter = PostFilter().apply {
            name = "Test"
            excludeUsers = "AutoModerator"
        }

        assertFalse(PostFilter.isPostAllowed(stickiedPost(), postFilter))
        assertTrue(PostFilter.isPostAllowed(stickiedPost(author = "someone_else"), postFilter))
    }

    @Test
    fun `an excluded flair hides a pinned post`() {
        val postFilter = PostFilter().apply {
            name = "Test"
            excludeFlairs = "🗨️"
        }

        assertFalse(PostFilter.isPostAllowed(stickiedPost(), postFilter))
        assertTrue(PostFilter.isPostAllowed(stickiedPost(flair = "Sale"), postFilter))
    }

    @Test
    fun `an excluded keyword hides a pinned post`() {
        val postFilter = PostFilter().apply {
            name = "Test"
            postTitleExcludesStrings = "daily thread"
        }

        assertFalse(PostFilter.isPostAllowed(stickiedPost(), postFilter))
        assertTrue(PostFilter.isPostAllowed(stickiedPost(title = "Deal of the day"), postFilter))
    }

    @Test
    fun `a comment limit hides a pinned post`() {
        val postFilter = PostFilter().apply {
            name = "Test"
            minComments = 10
        }

        assertFalse(PostFilter.isPostAllowed(stickiedPost(), postFilter))
        assertTrue(PostFilter.isPostAllowed(stickiedPost(nComments = 21), postFilter))
    }

    @Test
    fun `a pinned post no rule names is left alone`() {
        val postFilter = PostFilter().apply {
            name = "Test"
            excludeUsers = "someone_else"
            excludeFlairs = "Sale"
            postTitleExcludesStrings = "giveaway"
        }

        assertTrue(PostFilter.isPostAllowed(stickiedPost(), postFilter))
    }
}
