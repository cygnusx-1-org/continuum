package ml.docilealligator.infinityforreddit.postfilter

import ml.docilealligator.infinityforreddit.post.Post
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * How wildcard "Exclude subreddits" terms behave inside [PostFilter.isPostAllowed].
 *
 * The scoping is the point: a term like `*irl*` also matches r/airlines and r/Hairloss, which nobody
 * writing it would predict. Confining it to one feed means that damage cannot reach Home, Search or
 * a subreddit page, where a missing post actually costs the user something.
 */
class PostFilterWildcardTest {

    @Before
    @After
    fun resetStaticState() {
        PostFilter.neverHideSubredditsLowerCase = emptySet()
        PostFilter.wildcardExceptionKeys = emptySet()
        PostFilterBlockRecorder.clearForTesting()
    }

    private fun post(subredditName: String) = Post(
        "id", "t3_id", subredditName, "r/$subredditName", "author", "t2_author", "", "", 0L,
        "A title", "https://example.com/x", "/r/$subredditName/comments/id/", 10, 0, 0, 0,
        100, "", false, false, false, false, false, false, false, false,
        false, false, false, 0L, null, false, false, "", null,
    )

    private fun filter(excludeSubreddits: String, wildcardsEnabled: Boolean) = PostFilter().apply {
        name = "Test"
        this.excludeSubreddits = excludeSubreddits
        wildcardSubredditMatchingEnabled = wildcardsEnabled
        subredditTermOwners = PostFilter.mergePostFilter(listOf(this)).subredditTermOwners
    }

    @Test
    fun `a wildcard term hides matching subreddits on the scoped feed`() {
        val postFilter = filter("*irl*", wildcardsEnabled = true)
        assertFalse(PostFilter.isPostAllowed(post("IRLPeople"), postFilter))
        assertFalse(PostFilter.isPostAllowed(post("airlines"), postFilter))
        assertTrue(PostFilter.isPostAllowed(post("android"), postFilter))
    }

    @Test
    fun `the same term is inert everywhere else`() {
        val postFilter = filter("*irl*", wildcardsEnabled = false)
        assertTrue(PostFilter.isPostAllowed(post("IRLPeople"), postFilter))
        assertTrue(PostFilter.isPostAllowed(post("airlines"), postFilter))
    }

    @Test
    fun `an exact term still filters on every feed`() {
        // Nothing about this change may alter what a plain name does; that is what makes the two
        // removed Settings toggles safe to drop without migrating anyone's stored terms.
        val scoped = filter("politics", wildcardsEnabled = true)
        val unscoped = filter("politics", wildcardsEnabled = false)
        assertFalse(PostFilter.isPostAllowed(post("politics"), scoped))
        assertFalse(PostFilter.isPostAllowed(post("Politics"), unscoped))
        assertTrue(PostFilter.isPostAllowed(post("politicshumor"), unscoped))
    }

    @Test
    fun `a subscribed subreddit is never swept up by a wildcard`() {
        PostFilter.neverHideSubredditsLowerCase = setOf("history")
        val postFilter = filter("*story*", wildcardsEnabled = true)
        assertTrue(PostFilter.isPostAllowed(post("history"), postFilter))
        assertFalse(PostFilter.isPostAllowed(post("storytime"), postFilter))
    }

    @Test
    fun `naming a subscribed subreddit exactly is still honoured`() {
        // The never-hide set protects against collateral, not against a deliberate block.
        PostFilter.neverHideSubredditsLowerCase = setOf("history")
        val postFilter = filter("history", wildcardsEnabled = true)
        assertFalse(PostFilter.isPostAllowed(post("history"), postFilter))
    }

    @Test
    fun `an exception releases one subreddit and leaves the rule working`() {
        val postFilter = filter("*porn*", wildcardsEnabled = true)
        PostFilter.wildcardExceptionKeys = setOf(PostFilter.exceptionKey("Test", "*porn*", "EarthPorn"))
        assertTrue(PostFilter.isPostAllowed(post("EarthPorn"), postFilter))
        assertFalse(PostFilter.isPostAllowed(post("CumPorn"), postFilter))
    }

    @Test
    fun `a term under the floor is ignored even when scoped`() {
        val postFilter = filter("*a*", wildcardsEnabled = true)
        assertTrue(PostFilter.isPostAllowed(post("android"), postFilter))
    }

    @Test
    fun `merging keeps each wildcard term attributed to its own filter`() {
        // isPostAllowed sees one "Merged" filter, so without this the blocked list could not say
        // which rule of which filter hid a subreddit.
        val a = PostFilter().apply {
            name = "Anime"
            excludeSubreddits = "*yuri*,politics"
        }
        val b = PostFilter().apply {
            name = "Sports"
            excludeSubreddits = "*nba*"
        }
        val merged = PostFilter.mergePostFilter(listOf(a, b))
        assertEquals("Anime", merged.subredditTermOwners["*yuri*"])
        assertEquals("Sports", merged.subredditTermOwners["*nba*"])
        // Exact terms are not recorded or excepted, so they stay out of the map.
        assertFalse(merged.subredditTermOwners.containsKey("politics"))
    }

    @Test
    fun `a single filter is attributed to itself`() {
        val only = PostFilter().apply {
            name = "Anime"
            excludeSubreddits = "*yuri*"
        }
        assertEquals("Anime", PostFilter.mergePostFilter(listOf(only)).subredditTermOwners["*yuri*"])
    }
}
