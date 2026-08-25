package ml.docilealligator.infinityforreddit.postfilter

import java.util.Locale
import ml.docilealligator.infinityforreddit.post.Post
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Title keyword filters fold case before comparing, and the two sides of that comparison differ in
 * case by construction: a keyword the user typed in lower case against a title that capitalises it.
 *
 * Folding with the *device* locale is therefore not safe. In Turkish and Azeri, `I` lower-cases to
 * dotless `ı`, so only the side that was capitalised changes and the keyword stops matching. The
 * user sees a filter that works on every other phone quietly doing nothing on theirs, with nothing
 * in the app to suggest why -- so the locale is varied here rather than left at the JVM default,
 * which no other test in the suite moves off English.
 */
class PostFilterKeywordCaseTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        // Captured before anything that can throw: the whole suite shares one JVM, so a locale left
        // behind here would follow every test that runs after it.
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        resetStaticState()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
        resetStaticState()
    }

    private fun resetStaticState() {
        PostFilter.neverHideSubredditsLowerCase = emptySet()
        PostFilter.wildcardExceptionKeys = emptySet()
        PostFilterBlockRecorder.clearForTesting()
    }

    private fun post(title: String) = Post(
        "id", "t3_id", "android", "r/android", "author", "", "", 0L,
        title, "https://example.com/x", "/r/android/comments/id/", 10, 0, 0, 0,
        100, "", false, false, false, false, false, false, false, false,
        false, false, false, 0L, null, false, false, "", null,
    )

    @Test
    fun `an excluded keyword still matches a title that capitalises it`() {
        val postFilter = PostFilter().apply {
            name = "Test"
            postTitleExcludesStrings = "iceland"
        }

        assertFalse(PostFilter.isPostAllowed(post("Iceland trip"), postFilter))
        assertFalse(PostFilter.isPostAllowed(post("ICELAND TRIP"), postFilter))
        assertTrue(PostFilter.isPostAllowed(post("Norway trip"), postFilter))
    }

    @Test
    fun `a required keyword still matches a title that capitalises it`() {
        val postFilter = PostFilter().apply {
            name = "Test"
            postTitleContainsStrings = "iceland"
        }

        assertTrue(PostFilter.isPostAllowed(post("Iceland trip"), postFilter))
        assertTrue(PostFilter.isPostAllowed(post("ICELAND TRIP"), postFilter))
        assertFalse(PostFilter.isPostAllowed(post("Norway trip"), postFilter))
    }
}
