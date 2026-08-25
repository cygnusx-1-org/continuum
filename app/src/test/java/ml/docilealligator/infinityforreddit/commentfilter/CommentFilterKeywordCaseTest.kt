package ml.docilealligator.infinityforreddit.commentfilter

import java.util.Locale
import ml.docilealligator.infinityforreddit.comment.Comment
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The comment-body half of the same rule [PostFilter][ml.docilealligator.infinityforreddit.postfilter.PostFilter]
 * follows for titles: the keyword and the text it is matched against differ in case by
 * construction, so the fold has to be locale-independent. On a Turkish or Azeri device the device
 * locale turns `I` into dotless `ı` on one side only and the keyword stops matching.
 */
class CommentFilterKeywordCaseTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        // Captured before anything that can throw -- one JVM runs the whole suite.
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    private fun comment(body: String) = Comment(
        "c1", "t1_c1", "author", "t2_author", "", "", null, 0L,
        body, body, "t3_id", "android", "t3_id", 0,
        0, false, "", "/r/android/comments/id/c1/", 0, false, false,
        false, false, false, false, false, false, 0L, null, false, false, 0L, null
    )

    @Test
    fun `an excluded keyword still matches a comment that capitalises it`() {
        val commentFilter = CommentFilter().apply {
            name = "Test"
            excludeStrings = "iceland"
        }

        assertFalse(CommentFilter.isCommentAllowed(comment("Iceland is great"), commentFilter))
        assertFalse(CommentFilter.isCommentAllowed(comment("ICELAND IS GREAT"), commentFilter))
        assertTrue(CommentFilter.isCommentAllowed(comment("Norway is great"), commentFilter))
    }
}
