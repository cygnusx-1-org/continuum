package ml.docilealligator.infinityforreddit.utils

import java.util.Locale
import ml.docilealligator.infinityforreddit.comment.Comment
import ml.docilealligator.infinityforreddit.post.Post
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Saved screen's search. Reddit has no server-side search over a user's /saved listing, so this
 * matcher is the whole feature: what it rejects simply is not in the results, however many pages
 * the tab goes on to load.
 *
 * The rules being pinned here are the ones stated in [SavedThingSearchFilter]'s contract — every
 * term has to match (AND), `/r/name` means that subreddit and not a subreddit whose name merely
 * starts with it, and a blank query is not a filter at all.
 */
class SavedThingSearchFilterTest {

    private fun post(title: String, subreddit: String, author: String) = Post(
        "abc123", "t3_abc123", subreddit, "r/$subreddit", author, "t2_$author", "", "", 0L,
        title, "/r/$subreddit/comments/abc123/", 0, Post.TEXT_TYPE, 0, 0, 100, "",
        false, false, false, false, false, false, false, false,
        false, false, false, 0L, null, false, false, "", null
    )

    private fun comment(body: String, subreddit: String, author: String) = Comment(
        "c1", "t1_c1", author, "t2_$author", "", "", null, 0L,
        body, body, "t3_abc123", subreddit, "t3_abc123", 0,
        0, false, "", "/r/$subreddit/comments/abc123/c1/", 0, false, false,
        false, false, false, false, false, false, 0L, null, false, false, 0L, null
    )

    private val cats = post("Cats of Iceland", "aww", "bob")
    private val catsComment = comment("Cats of Iceland are great", "aww", "bob")

    @Test
    fun `every term of a multi-word query has to match`() {
        assertTrue(SavedThingSearchFilter.matches(cats, "cats iceland"))
        assertTrue(SavedThingSearchFilter.matches(cats, "cats aww bob"))

        // "cats" matches; "dogs" does not. One out of two is not a match.
        assertFalse(SavedThingSearchFilter.matches(cats, "cats dogs"))
        assertFalse(SavedThingSearchFilter.matches(cats, "dogs cats"))

        assertTrue(SavedThingSearchFilter.matches(catsComment, "cats great"))
        assertFalse(SavedThingSearchFilter.matches(catsComment, "cats dogs"))
    }

    @Test
    fun `a slash-r term names one subreddit, not everything starting with it`() {
        val pics = post("Sunset", "pics", "bob")

        assertTrue(SavedThingSearchFilter.matches(pics, "/r/pics"))
        assertTrue(SavedThingSearchFilter.matches(pics, "/R/PICS"))

        assertFalse(SavedThingSearchFilter.matches(pics, "/r/pic"))
        assertFalse(SavedThingSearchFilter.matches(pics, "/r/picsofcats"))
        assertFalse(SavedThingSearchFilter.matches(pics, "/r/aww"))

        val picsComment = comment("Nice shot", "pics", "bob")
        assertTrue(SavedThingSearchFilter.matches(picsComment, "/r/pics"))
        assertFalse(SavedThingSearchFilter.matches(picsComment, "/r/pic"))
    }

    @Test
    fun `a blank query is not a filter`() {
        listOf(null, "", "   ", "\t").forEach { query ->
            assertTrue("post kept for query <$query>", SavedThingSearchFilter.matches(cats, query))
            assertTrue(
                "comment kept for query <$query>",
                SavedThingSearchFilter.matches(catsComment, query)
            )
        }
    }

    @Test
    fun `a query still matches under a locale with its own case rules`() {
        // Scoped with try/finally rather than @Before/@After because the rest of this class must
        // keep the default locale; one JVM runs the whole suite, so an escaped locale is contagious.
        val originalLocale = Locale.getDefault()
        try {
            // Turkish lower-cases "I" to dotless "i". The query and the text differ in case by
            // construction, so folding both with the device locale changes only one of them and a
            // saved search for a word containing an I would find nothing.
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))

            assertTrue(SavedThingSearchFilter.matches(cats, "iceland"))
            assertTrue(SavedThingSearchFilter.matches(cats, "ICELAND"))
            assertTrue(SavedThingSearchFilter.matches(cats, "Iceland"))
            assertFalse(SavedThingSearchFilter.matches(cats, "greenland"))

            assertTrue(SavedThingSearchFilter.matches(catsComment, "iceland"))
            assertTrue(SavedThingSearchFilter.matches(catsComment, "ICELAND"))

            val pics = post("Sunset", "PICS", "bob")
            assertTrue(SavedThingSearchFilter.matches(pics, "/r/pics"))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `a query matches the title, the subreddit or the author`() {
        assertTrue(SavedThingSearchFilter.matches(cats, "iceland"))
        assertTrue(SavedThingSearchFilter.matches(cats, "aww"))
        assertTrue(SavedThingSearchFilter.matches(cats, "bob"))
        assertFalse(SavedThingSearchFilter.matches(cats, "carol"))

        assertTrue(SavedThingSearchFilter.matches(catsComment, "great"))
        assertTrue(SavedThingSearchFilter.matches(catsComment, "aww"))
        assertTrue(SavedThingSearchFilter.matches(catsComment, "bob"))
        assertFalse(SavedThingSearchFilter.matches(catsComment, "carol"))
    }
}
