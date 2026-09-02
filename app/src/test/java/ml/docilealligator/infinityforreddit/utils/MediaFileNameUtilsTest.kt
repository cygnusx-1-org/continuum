package ml.docilealligator.infinityforreddit.utils

import ml.docilealligator.infinityforreddit.comment.Comment
import ml.docilealligator.infinityforreddit.post.ImgurMedia
import ml.docilealligator.infinityforreddit.post.Post
import ml.docilealligator.infinityforreddit.services.DownloadMediaService
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

    private fun comment(author: String, id: String) = Comment(
        id, "t1_$id", author, "t2_$author", "", "", null, null,
        0L, "", "",
        "t3_abc123", "pics", "t3_abc123", 0,
        0, false, "", "/r/pics/comments/abc123/_/$id/",
        0, false, false,
        false, false, false, false, false,
        false, 0L, null, false, false,
        0L, null
    )

    /** A self post -- the host for a video embedded in a post body or a comment. */
    private fun textPost(title: String, id: String) = Post(
        id, "t3_$id", "pics", "r/pics", "bob", "t2_bob", "", "", 0L,
        title, "/r/pics/comments/$id/", 0, Post.TEXT_TYPE, 0, 0, 100, "",
        false, false, false, false, false, false, false, false,
        false, false, false, 0L, null, false, false, "", null
    )

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
    fun `quotes are stripped, not turned into separators`() {
        // The real title behind the issue #389 device run: the typographic quotes used to survive
        // into the filename, because only the ASCII one was in the illegal-character class.
        assertEquals(
            "Front_Blunt_from_Today_Tomorrow_Forever",
            MediaFileNameUtils.sanitizeFilename("Front Blunt from \u201CToday Tomorrow Forever\u201D")
        )
        assertEquals("He_said_hi", MediaFileNameUtils.sanitizeFilename("He said \"hi\""))
        // Between two word characters a separator would invent a break the title never had.
        assertEquals("RocknRoll", MediaFileNameUtils.sanitizeFilename("Rock\"n\"Roll"))
    }

    @Test
    fun `an apostrophe inside a word closes the word up rather than splitting it`() {
        assertEquals(
            "Today_is_September_1st_and_its_my_22nd_birthday",
            MediaFileNameUtils.sanitizeFilename("Today is September 1st and it\u2019s my 22nd birthday")
        )
        assertEquals("dont_stop", MediaFileNameUtils.sanitizeFilename("don't stop"))
        assertEquals("Reddits_finest", MediaFileNameUtils.sanitizeFilename("Reddit\u2019s finest"))
    }

    @Test
    fun `every quote form is stripped, but the primes are not`() {
        val quotes = listOf(
            '"', '\u201C', '\u201D', '\u201E', '\u201F',
            '\u00AB', '\u00BB', '\u301D', '\u301E', '\u301F', '\uFF02',
            '\'', '\u2018', '\u2019', '\u201A', '\u201B', '\u2039', '\u203A', '\uFF07'
        )
        for (quote in quotes) {
            val name = MediaFileNameUtils.sanitizeFilename("a${quote}b")
            assertFalse("kept $quote: $name", name.contains(quote))
            assertEquals("ab", name)
        }
        // The primes measure feet and inches; stripping them would lose the meaning.
        assertEquals("24\u2033_monitor", MediaFileNameUtils.sanitizeFilename("24\u2033 monitor"))
        assertEquals("6\u2032_fence", MediaFileNameUtils.sanitizeFilename("6\u2032 fence"))
    }

    @Test
    fun `a title that is nothing but quotes still gets a name`() {
        val name = MediaFileNameUtils.sanitizeFilename("\u201C\u201D")

        assertTrue("lost its fallback: $name", name.startsWith("reddit_media"))
        assertTrue(
            "lost its fallback: $name",
            MediaFileNameUtils.sanitizeFilename("\u2018\u2019").startsWith("reddit_media")
        )
    }

    @Test
    fun `a long title is cut to a hundred BYTES, not a hundred UTF-16 units`() {
        // Every filesystem Android exposes caps a path component at 255 bytes, so a UTF-16 count
        // under-measures anything non-Latin: 100 units of CJK is 300 bytes and overflows once the
        // ids and extension are appended.
        for (title in listOf("a".repeat(250), "\u6f22".repeat(250), "\uD83D\uDC9A".repeat(250))) {
            val name = MediaFileNameUtils.sanitizeFilename(title)
            assertTrue(
                "over budget: ${name.toByteArray(Charsets.UTF_8).size} bytes",
                name.toByteArray(Charsets.UTF_8).size <= 100
            )
        }
    }

    @Test
    fun `truncation never splits a surrogate pair`() {
        // 100 bytes is 25 emoji exactly; the boundary cases either side must stay whole, because a
        // lone surrogate encodes to '?' -- a character sanitizing had just removed as FAT-illegal.
        for (count in 24..27) {
            val name = MediaFileNameUtils.sanitizeFilename("\uD83D\uDC9A".repeat(count))
            assertFalse("split a pair: $name", name.any { it.isHighSurrogate() && name.indexOf(it) == name.length - 1 })
            assertEquals("uneven UTF-16 length: $name", 0, name.length % 2)
            assertTrue(name.toByteArray(Charsets.UTF_8).size <= 100)
        }
    }

    @Test
    fun `an ASCII title is byte-for-byte what it was before the budget became bytes`() {
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
    fun `the ascii fallback keeps the extension and drops what a filesystem may refuse`() {
        assertEquals(
            "My_sweet_boy_Jess_passed_away_1w4j2sq.jpg",
            MediaFileNameUtils.toAsciiFilename("My_sweet_boy_Jess_passed_away_\uD83D\uDC9A_1w4j2sq.jpg")
        )
        assertEquals("Cafe_abc123.mp4", MediaFileNameUtils.toAsciiFilename("Caf\u00E9_abc123.mp4"))
    }

    @Test
    fun `the ascii fallback still names a title written entirely in another script`() {
        val name = MediaFileNameUtils.toAsciiFilename("\u6f22\u5b57\u6f22\u5b57.mp4")

        assertTrue("lost its extension: $name", name.endsWith(".mp4"))
        assertTrue("lost its fallback: $name", name.startsWith("reddit_media"))
    }

    @Test
    fun `the ascii fallback leaves an already-ascii name untouched`() {
        // Equality with the input is what tells the caller not to bother retrying.
        assertEquals(
            "Front_Blunt_from_Today_Tomorrow_Forever_1w4kbjv_p7a81jf.mp4",
            MediaFileNameUtils.toAsciiFilename("Front_Blunt_from_Today_Tomorrow_Forever_1w4kbjv_p7a81jf.mp4")
        )

        // At and past the title budget too. A short name cannot catch a length cap applied to the
        // whole filename, and that is exactly the bug this missed: the ids sit past the budget, so
        // a cap here silently cut them off and made an ASCII name differ from itself.
        val atBudget = "a".repeat(100) + "_1w4kbjv_p7a81jf.mp4"
        assertEquals(atBudget, MediaFileNameUtils.toAsciiFilename(atBudget))

        val wellPast = "a".repeat(240) + "_1w4kbjv_p7a81jf.mp4"
        assertEquals(wellPast, MediaFileNameUtils.toAsciiFilename(wellPast))
    }

    @Test
    fun `the ascii fallback keeps the ids when it does rewrite the name`() {
        // The ids are what stop two posts colliding, so they have to survive the rewrite even when
        // the title in front of them is long enough to reach any budget.
        val name = "\u6f22".repeat(33) + "_1w4kbjv_p7a81jf.mp4"

        assertTrue(
            "lost the ids: ${MediaFileNameUtils.toAsciiFilename(name)}",
            MediaFileNameUtils.toAsciiFilename(name).endsWith("1w4kbjv_p7a81jf.mp4")
        )
    }

    @Test
    fun `the post id survives a title long enough to hit the length budget`() {
        // The collision test below uses short titles, so it could not catch a cap that trims the
        // name from the right -- which is where the id is.
        val name = MediaFileNameUtils.getDownloadFileName(galleryPost("a".repeat(250), "abc123"), 0)

        assertTrue("lost the post id: $name", name.contains("abc123"))
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

    @Test
    fun `an embedded comment video is named title, post id and comment id`() {
        assertEquals(
            "Cool_clip_abc123_def456.mp4",
            MediaFileNameUtils.getEmbeddedMediaFileName(
                "Cool clip", "abc123", "def456",
                "https://v.redd.it/link/abc123/asset/xyz/CMAF_1080.mp4",
                DownloadMediaService.EXTRA_MEDIA_TYPE_VIDEO
            )
        )
    }

    @Test
    fun `an embedded post-body video has no comment id`() {
        assertEquals(
            "Cool_clip_abc123.mp4",
            MediaFileNameUtils.getEmbeddedMediaFileName(
                "Cool clip", "abc123", null,
                "https://v.redd.it/link/abc123/asset/xyz/CMAF_1080.mp4",
                DownloadMediaService.EXTRA_MEDIA_TYPE_VIDEO
            )
        )
    }

    @Test
    fun `an embedded video on a text post gets mp4, not the host post type's extension`() {
        // The regression behind issue #389: routing an embedded item through the post-type switch
        // yields ".unknown", because a self post matches none of its arms.
        val textPost = textPost("Cool clip", "abc123")
        assertTrue(MediaFileNameUtils.getDownloadFileName(textPost, 0).endsWith(".unknown"))

        assertEquals(
            "Cool_clip_abc123_def456.mp4",
            MediaFileNameUtils.getEmbeddedMediaFileName(
                textPost.title, textPost.id, "def456",
                "https://v.redd.it/link/abc123/asset/xyz/CMAF_1080.mp4",
                DownloadMediaService.EXTRA_MEDIA_TYPE_VIDEO
            )
        )
    }

    @Test
    fun `an embedded name falls back to the media type when the url carries no extension`() {
        assertEquals(
            "Cool_clip_abc123_def456.mp4",
            MediaFileNameUtils.getEmbeddedMediaFileName(
                "Cool clip", "abc123", "def456",
                "https://v.redd.it/link/abc123/asset/xyz/DASH_240",
                DownloadMediaService.EXTRA_MEDIA_TYPE_VIDEO
            )
        )
    }

    @Test
    fun `an embedded name sanitizes the title and both ids into the path`() {
        val name = MediaFileNameUtils.getEmbeddedMediaFileName(
            "../../etc/passwd", "a/b", "c:d",
            "https://v.redd.it/x/CMAF_720.mp4",
            DownloadMediaService.EXTRA_MEDIA_TYPE_VIDEO
        )

        assertFalse(name.contains("/"))
        assertFalse(name.contains(":"))
        assertTrue(name.endsWith(".mp4"))
    }

    @Test
    fun `a viewed image is named title, post id and comment id`() {
        assertEquals(
            "Cool_shot_abc123_def456.jpg",
            MediaFileNameUtils.getViewedImageFileName(
                "Cool shot", "abc123", "def456",
                "https://i.redd.it/xyz.jpg", false, false
            )
        )
    }

    @Test
    fun `a viewed post image has no comment id`() {
        assertEquals(
            "Cool_shot_abc123.jpg",
            MediaFileNameUtils.getViewedImageFileName(
                "Cool shot", "abc123", null,
                "https://i.redd.it/xyz.jpg", false, false
            )
        )
    }

    @Test
    fun `a viewed image takes its extension from the url, not from the media kind`() {
        assertEquals(
            "Cool_shot_abc123.png",
            MediaFileNameUtils.getViewedImageFileName(
                "Cool shot", "abc123", null,
                "https://i.redd.it/xyz.png", false, false
            )
        )
        // The double-extension bug: appending an extension to a name that already carries one.
        assertFalse(
            MediaFileNameUtils.getViewedImageFileName(
                "Cool shot", "abc123", null, "https://i.redd.it/xyz.png", false, false
            ).endsWith(".png.jpg")
        )
    }

    @Test
    fun `an apng keeps an extension no media type can express`() {
        // ".apng" exists nowhere in DownloadMediaService's media types, which is why the viewer
        // names its own media instead of going through getEmbeddedMediaFileName.
        assertEquals(
            "Cool_shot_abc123.apng",
            MediaFileNameUtils.getViewedImageFileName(
                "Cool shot", "abc123", null,
                "https://i.redd.it/xyz", false, true
            )
        )
        assertEquals(
            "Cool_shot_abc123.gif",
            MediaFileNameUtils.getViewedImageFileName(
                "Cool shot", "abc123", null,
                "https://i.redd.it/xyz", true, false
            )
        )
    }

    @Test
    fun `a viewed image with no title still says which kind it was`() {
        assertEquals(
            "reddit_image_abc123.jpg",
            MediaFileNameUtils.getViewedImageFileName(null, "abc123", null, null, false, false)
        )
        assertEquals(
            "reddit_gif_abc123.gif",
            MediaFileNameUtils.getViewedImageFileName("", "abc123", null, null, true, false)
        )
        assertEquals(
            "reddit_gif_abc123.apng",
            MediaFileNameUtils.getViewedImageFileName("", "abc123", null, null, false, true)
        )
    }

    @Test
    fun `every name sanitizes the title first and appends the ids after`() {
        // Joining first and sanitizing the result puts the ids inside the byte budget, so a title
        // that reaches the cap -- 34 CJK characters is already 102 bytes -- truncates them away.
        // The ids are the only thing that stops two posts colliding, so this is the order, not a
        // preference. Every entry point shares one join, and this pins all of them.
        val longTitle = "\u6f22".repeat(80)

        val viewed = MediaFileNameUtils.getViewedImageFileName(
            longTitle, "abc123", "def456", "https://i.redd.it/xyz.jpg", false, false
        )
        assertTrue("lost the post id: $viewed", viewed.contains("_abc123_"))
        assertTrue("lost the comment id: $viewed", viewed.endsWith("_def456.jpg"))

        val embedded = MediaFileNameUtils.getEmbeddedMediaFileName(
            longTitle, "abc123", "def456", "https://v.redd.it/x/CMAF_720.mp4",
            DownloadMediaService.EXTRA_MEDIA_TYPE_VIDEO
        )
        assertTrue("lost the post id: $embedded", embedded.contains("_abc123_"))
        assertTrue("lost the comment id: $embedded", embedded.endsWith("_def456.mp4"))

        val downloaded = MediaFileNameUtils.getDownloadFileName(galleryPost(longTitle, "abc123"), 0)
        assertTrue("lost the post id: $downloaded", downloaded.contains("_abc123_"))

        val screenshot = MediaFileNameUtils.getScreenshotFileName(textPost(longTitle, "abc123"), false)
        assertTrue("lost the post id: $screenshot", screenshot.endsWith("_abc123.png"))
    }

    @Test
    fun `a post screenshot is named after the post, and says when it carries comments`() {
        val post = textPost("Cool shot", "abc123")

        assertEquals("Cool_shot_abc123.png", MediaFileNameUtils.getScreenshotFileName(post, false))
        assertEquals(
            "Cool_shot_abc123_comments.png",
            MediaFileNameUtils.getScreenshotFileName(post, true)
        )
    }

    @Test
    fun `a screenshot of a post whose title is a path is still one path component`() {
        val name = MediaFileNameUtils.getScreenshotFileName(textPost("../../etc/passwd", "abc123"), true)

        assertFalse("contains a separator: $name", name.contains('/'))
        assertTrue(name.endsWith("_abc123_comments.png"))
    }

    @Test
    fun `a comment screenshot is named after its author and id`() {
        assertEquals(
            "comment_bob_def456.png",
            MediaFileNameUtils.getScreenshotFileName(comment("bob", "def456"))
        )
    }
}
