package ml.docilealligator.infinityforreddit.utils

import ml.docilealligator.infinityforreddit.post.ImgurMedia
import ml.docilealligator.infinityforreddit.post.Post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The filenames "save" and "share" hand to the system. [MediaFileNameUtils] is the single source of
 * truth for them, and the parts that matter are the ones a post title can attack: a title is
 * arbitrary user text on its way into a path.
 */
class MediaFileNameUtilsTest {

    private fun galleryPost(title: String, id: String) = Post(
        id, "t3_$id", "pics", "r/pics", "bob", "t2_bob", "", "", 0L,
        title, "/r/pics/comments/$id/", 0, Post.GALLERY_TYPE, 0, 0, 100, "",
        false, false, false, false, false, false, false, false,
        false, false, false, 0L, null, false, false, "", null
    ).apply {
        gallery = arrayListOf(
            Post.Gallery("image/jpg", "https://i.redd.it/one.jpg", "", "one.jpg", "", ""),
            Post.Gallery("image/jpg", "https://i.redd.it/two.jpg", "", "two.jpg", "", ""),
            Post.Gallery("image/jpg", "https://i.redd.it/three.jpg", "", "three.jpg", "", "")
        )
    }

    @Test
    fun `a title can never put a path separator in the filename`() {
        val name = MediaFileNameUtils.sanitizeFilename("AC/DC live \\ backstage: 1979?")

        assertFalse("contains a forward slash: $name", name.contains('/'))
        assertFalse("contains a backslash: $name", name.contains('\\'))
        assertFalse("contains a colon: $name", name.contains(':'))
        assertFalse("contains a question mark: $name", name.contains('?'))
        assertTrue("lost the title entirely: $name", name.contains("AC"))
    }

    @Test
    fun `a title too long for a filename is cut down to a hundred characters`() {
        assertEquals(100, MediaFileNameUtils.sanitizeFilename("a".repeat(250)).length)

        // A cut that lands on a word break loses the trailing separator too, so a long title is
        // "at most a hundred" rather than always exactly a hundred.
        val fromWords = MediaFileNameUtils.sanitizeFilename("word ".repeat(100))
        assertTrue("not cut down at all: ${fromWords.length}", fromWords.length <= 100)
        assertTrue("cut far shorter than the limit: $fromWords", fromWords.length >= 95)

        // A title that already fits keeps all of its words (spaces become underscores).
        assertEquals("Short_title", MediaFileNameUtils.sanitizeFilename("Short title"))
    }

    @Test
    fun `a title that sanitizes away still gets a name`() {
        assertEquals("reddit_media", MediaFileNameUtils.sanitizeFilename(null))
        assertEquals("reddit_media", MediaFileNameUtils.sanitizeFilename(""))
        assertTrue(MediaFileNameUtils.sanitizeFilename("///").startsWith("reddit_media_"))
    }

    @Test
    fun `gallery items are numbered from one, the way the user sees them`() {
        val post = galleryPost("Holiday", "abc123")

        assertEquals("Holiday_abc123_1.jpg", MediaFileNameUtils.getDownloadFileName(post, 0))
        assertEquals("Holiday_abc123_2.jpg", MediaFileNameUtils.getDownloadFileName(post, 1))
        assertEquals("Holiday_abc123_3.jpg", MediaFileNameUtils.getDownloadFileName(post, 2))
    }

    @Test
    fun `a post id in the name is what stops two posts from colliding`() {
        val first = galleryPost("Holiday", "abc123")
        val second = galleryPost("Holiday", "xyz789")

        assertEquals("Holiday_abc123_1.jpg", MediaFileNameUtils.getDownloadFileName(first, 0))
        assertEquals("Holiday_xyz789_1.jpg", MediaFileNameUtils.getDownloadFileName(second, 0))
    }

    @Test
    fun `imgur media carries its id, so two albums sharing a title do not collide`() {
        val fromOneAlbum = ImgurMedia("aaa111", "", "", "image/jpeg", "https://i.imgur.com/aaa111.jpg")
        val fromAnother = ImgurMedia("bbb222", "", "", "image/jpeg", "https://i.imgur.com/bbb222.jpg")

        assertEquals(
            "Holiday_aaa111.jpg",
            MediaFileNameUtils.getDownloadFileName(fromOneAlbum, "Holiday")
        )
        assertEquals(
            "Holiday_bbb222.jpg",
            MediaFileNameUtils.getDownloadFileName(fromAnother, "Holiday")
        )
    }

    @Test
    fun `imgur album items keep their one-based index alongside the id`() {
        val media = ImgurMedia("aaa111", "", "", "image/jpeg", "https://i.imgur.com/aaa111.jpg")

        assertEquals("Holiday_aaa111_1.jpg", MediaFileNameUtils.getDownloadFileName(media, "Holiday", 0))
        assertEquals("Holiday_aaa111_2.jpg", MediaFileNameUtils.getDownloadFileName(media, "Holiday", 1))
    }
}
