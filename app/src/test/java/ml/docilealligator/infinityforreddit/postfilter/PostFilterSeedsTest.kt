package ml.docilealligator.infinityforreddit.postfilter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.post.Post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Add to Post Filter" reports what the user ticked as indexes into
 * `R.array.add_to_post_filter_options`, and [PostFilterSeeds] is the only thing that knows what each
 * index means. Get one wrong and the user silently filters the wrong thing — excluding the author
 * where they asked to exclude the subreddit — so the mapping is pinned here term by term.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class PostFilterSeedsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun post(
        subredditName: String = "politics",
        author: String = "spez",
        flair: String = "Discussion",
        url: String = "https://www.bbc.co.uk/news/123",
    ) = Post(
        "id", "t3_id", subredditName, "r/$subredditName", author, "", "", 0L,
        "A title", url, "/r/$subredditName/comments/id/", 10, 0, 0, 0,
        100, flair, false, false, false, false, false, false, false, false,
        false, false, false, 0L, null, false, false, "", null,
    )

    private fun selection(vararg checked: Int) =
        BooleanArray(PostFilterSeeds.optionCount) { it in checked }

    @Test
    fun everyDialogOptionHasARule() {
        val options = context.resources.getStringArray(R.array.add_to_post_filter_options)
        assertEquals(options.size, PostFilterSeeds.optionCount)
    }

    @Test
    fun eachOptionSeedsItsOwnTerm() {
        val expected = listOf(
            FilterRule(RuleField.SUBREDDIT, true, "politics"),
            FilterRule(RuleField.USER, true, "spez"),
            FilterRule(RuleField.FLAIR, true, "Discussion"),
            FilterRule(RuleField.FLAIR, false, "Discussion"),
            FilterRule(RuleField.DOMAIN, true, "www.bbc.co.uk"),
            FilterRule(RuleField.DOMAIN, false, "www.bbc.co.uk"),
            FilterRule(RuleField.SUBREDDIT, false, "politics"),
            FilterRule(RuleField.USER, false, "spez"),
        )
        for (i in expected.indices) {
            assertEquals(
                "option $i",
                listOf(expected[i]),
                PostFilterSeeds.rulesForPost(post(), selection(i)),
            )
        }
    }

    @Test
    fun tickingSeveralOptionsSeedsThemAll() {
        val rules = PostFilterSeeds.rulesForPost(post(), selection(0, 1, 4))
        assertEquals(
            listOf(
                FilterRule(RuleField.SUBREDDIT, true, "politics"),
                FilterRule(RuleField.USER, true, "spez"),
                FilterRule(RuleField.DOMAIN, true, "www.bbc.co.uk"),
            ),
            rules,
        )
    }

    @Test
    fun nothingTickedSeedsNothing() {
        assertTrue(PostFilterSeeds.rulesForPost(post(), selection()).isEmpty())
    }

    /** A box ticked for something the post hasn't got must not become an empty term. */
    @Test
    fun aPostWithoutAFlairSeedsNoFlairRule() {
        assertTrue(PostFilterSeeds.rulesForPost(post(flair = ""), selection(2)).isEmpty())
        assertTrue(PostFilterSeeds.rulesForPost(post(flair = "   "), selection(3)).isEmpty())
    }

    @Test
    fun subredditAndUserPagesSeedOneExcludeRule() {
        assertEquals(
            listOf(FilterRule(RuleField.SUBREDDIT, true, "politics")),
            PostFilterSeeds.excludeRule(RuleField.SUBREDDIT, " politics "),
        )
        assertEquals(
            listOf(FilterRule(RuleField.USER, true, "spez")),
            PostFilterSeeds.excludeRule(RuleField.USER, "spez"),
        )
        assertTrue(PostFilterSeeds.excludeRule(RuleField.USER, null).isEmpty())
        assertTrue(PostFilterSeeds.excludeRule(RuleField.USER, " ").isEmpty())
    }

    @Test
    fun aUrlIsReducedToItsHost() {
        assertEquals("www.bbc.co.uk", PostFilterSeeds.domainTerm("https://www.bbc.co.uk/news/123?x=1"))
        assertEquals("example.com", PostFilterSeeds.domainTerm("example.com"))
        assertNull(PostFilterSeeds.domainTerm("https://"))
        assertNull(PostFilterSeeds.domainTerm(null))
        assertNull(PostFilterSeeds.domainTerm(""))
    }
}
