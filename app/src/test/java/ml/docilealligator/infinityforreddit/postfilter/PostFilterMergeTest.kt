package ml.docilealligator.infinityforreddit.postfilter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When more than one saved filter applies to a feed, [PostFilter.mergePostFilter] folds them into a
 * single filter that the paging source then applies once. The merge is what makes "two filters are
 * both in force" true, so it has to be strictest-wins: a post type either filter bans stays banned,
 * the highest minimum wins, an "only" switch either filter set stays set, and no filter's terms are
 * dropped on the way in.
 *
 * The merge already ran in [PostFilterWildcardTest], which asserts only the wildcard-ownership map
 * it builds; nothing looked at the merged filter itself.
 */
class PostFilterMergeTest {

    private fun filter(name: String, configure: PostFilter.() -> Unit = {}) =
        PostFilter().apply { this.name = name }.apply(configure)

    @Test
    fun `a post type either filter bans stays banned`() {
        val merged = PostFilter.mergePostFilter(
            listOf(
                filter("no images") { containImageType = false },
                filter("no videos") { containVideoType = false }
            )
        )

        assertFalse(merged.containImageType)
        assertFalse(merged.containVideoType)
        assertTrue("a type neither filter banned is still allowed", merged.containTextType)
        assertTrue(merged.containGalleryType)
    }

    @Test
    fun `the strictest minimum wins`() {
        val merged = PostFilter.mergePostFilter(
            listOf(
                filter("lenient") { minVote = 50; minComments = 2 },
                filter("strict") { minVote = 200; minComments = 30 }
            )
        )

        assertEquals(200, merged.minVote)
        assertEquals(30, merged.minComments)
    }

    @Test
    fun `an only-switch either filter set stays set`() {
        val spoilerFirst = PostFilter.mergePostFilter(
            listOf(filter("spoilers") { onlySpoiler = true }, filter("plain"))
        )
        val spoilerLast = PostFilter.mergePostFilter(
            listOf(filter("plain"), filter("spoilers") { onlySpoiler = true })
        )
        val nsfwFirst = PostFilter.mergePostFilter(
            listOf(filter("nsfw") { onlyNSFW = true }, filter("plain"))
        )

        assertTrue("set by the first of the two", spoilerFirst.onlySpoiler)
        assertTrue("set by the second of the two", spoilerLast.onlySpoiler)
        assertTrue(nsfwFirst.onlyNSFW)
    }

    @Test
    fun `no filter's terms are dropped by the merge`() {
        val merged = PostFilter.mergePostFilter(
            listOf(
                filter("first") { containSubreddits = "pics"; excludeUsers = "spammer" },
                filter("second") { containSubreddits = "aww"; excludeUsers = "botaccount" }
            )
        )

        val subreddits = merged.containSubreddits!!.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val users = merged.excludeUsers!!.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals(listOf("pics", "aww"), subreddits)
        assertEquals(listOf("spammer", "botaccount"), users)
    }

    @Test
    fun `the strictest maximum wins`() {
        val merged = PostFilter.mergePostFilter(
            listOf(
                filter("lenient") { maxVote = 5000; maxComments = 900 },
                filter("strict") { maxVote = 500; maxComments = 40 }
            )
        )

        assertEquals(500, merged.maxVote)
        assertEquals(40, merged.maxComments)
    }

    @Test
    fun `a maximum only one filter sets is still in force after the merge`() {
        val merged = PostFilter.mergePostFilter(
            listOf(filter("capped") { maxVote = 5000 }, filter("uncapped"))
        )

        assertEquals(5000, merged.maxVote)
    }

    @Test
    fun `no maximum anywhere leaves the no-bound sentinel alone`() {
        val merged = PostFilter.mergePostFilter(listOf(filter("one"), filter("two")))

        assertEquals(-1, merged.maxVote)
        assertEquals(-1, merged.maxComments)
        assertEquals(-1, merged.minVote)
    }

    @Test
    fun `a maximum stays in force whichever filter is first`() {
        val capFirst = PostFilter.mergePostFilter(
            listOf(filter("capped") { maxComments = 40 }, filter("uncapped"))
        )
        val capLast = PostFilter.mergePostFilter(
            listOf(filter("uncapped"), filter("capped") { maxComments = 40 })
        )

        assertEquals(40, capFirst.maxComments)
        assertEquals(40, capLast.maxComments)
    }

    @Test
    fun `merging one filter hands back that filter unchanged`() {
        val only = filter("only") { minVote = 10; containImageType = false }

        val merged = PostFilter.mergePostFilter(listOf(only))

        assertEquals(10, merged.minVote)
        assertFalse(merged.containImageType)
        assertEquals("only", merged.name)
    }
}
