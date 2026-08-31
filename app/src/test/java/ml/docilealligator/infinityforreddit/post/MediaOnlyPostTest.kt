package ml.docilealligator.infinityforreddit.post

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Media Posts Only" (issue #377) is a filter over post types, and the whole of its policy is
 * [Post.isMediaPost]. The line it draws is not the one a preview would draw: a link post gets a
 * preview from Reddit, so a filter keyed off previews would leave the gallery full of news articles,
 * which is the thing the setting exists to remove. Polls have no type of their own here -- this app
 * never parses `poll_data`, so they arrive as self posts and are covered by the text case.
 */
class MediaOnlyPostTest {

    @Test
    fun `image gif video and gallery posts are media`() {
        assertTrue(postOfType(Post.IMAGE_TYPE).isMediaPost)
        assertTrue(postOfType(Post.GIF_TYPE).isMediaPost)
        assertTrue(postOfType(Post.VIDEO_TYPE).isMediaPost)
        assertTrue(postOfType(Post.GALLERY_TYPE).isMediaPost)
    }

    @Test
    fun `a link post is not media even though reddit gives it a preview`() {
        assertFalse(postOfType(Post.LINK_TYPE).isMediaPost)
        assertFalse(postOfType(Post.NO_PREVIEW_LINK_TYPE).isMediaPost)
    }

    @Test
    fun `text posts are not media, polls among them`() {
        assertFalse(postOfType(Post.TEXT_TYPE).isMediaPost)
    }

    @Test
    fun `a preview does not make a post media`() {
        val link = postOfType(Post.LINK_TYPE)
        link.previews = arrayListOf(Post.Preview("https://example.com/preview.jpg", 1200, 630, "", ""))
        assertFalse("the preview belongs to the article, not to the post", link.isMediaPost)
    }

    private fun postOfType(postType: Int) = Post(
        "abc123", "t3_abc123", "test", "r/test",
        "someone", "t2_someone", "", "", 0L,
        "A title", "/r/test/comments/abc123/a_title/", 0, postType, 0, 0,
        0, "", false, false,
        false, false, false, false, false, false,
        false, false, false, 0L, null,
        false, false, "", null
    )
}
