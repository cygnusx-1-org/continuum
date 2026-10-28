package ml.docilealligator.infinityforreddit.post

import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.thing.SortType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A multireddit with users in it is one feed woven from several listings, and the sort the user
 * picked has to hold across all of them: the merge used to put every page newest first whatever the
 * sort (issue #469). Each listing keeps its own order; the merge only decides where one listing's
 * posts fall among another's, and holds back what a listing with pages left might yet outrank.
 *
 * Robolectric only for org.json, which the listing pages are read from.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestInfinity::class)
class MergedListingTest {

    @Test
    fun `top ranks by score across listings`() {
        val merge = MergedListing(2, SortType.Type.TOP)
        merge.add(0, null, post("a", score = 900, time = 10), post("b", score = 500, time = 30), post("c", score = 100, time = 20))
        merge.add(1, null, post("u1", score = 700, time = 40), post("u2", score = 50, time = 50))

        assertEquals(listOf("a", "u1", "b", "c", "u2"), merge.drain().ids())
        assertFalse(merge.hasMore())
    }

    @Test
    fun `a listing with pages left holds back what its next page might outrank`() {
        val merge = MergedListing(2, SortType.Type.NEW)
        merge.add(0, "m1", post("a", time = 100), post("b", time = 90), post("c", time = 80))
        merge.add(1, null, post("u1", time = 95), post("u2", time = 70), post("u3", time = 20))

        assertEquals(listOf("a", "u1", "b", "c"), merge.drain().ids())
        assertEquals(listOf(0), merge.sourcesToFetch())
        assertEquals("m1", merge.cursor(0))

        merge.add(0, null, post("d", time = 60), post("e", time = 10))

        assertEquals(listOf("u2", "d", "u3", "e"), merge.drain().ids())
        assertFalse(merge.hasMore())
    }

    @Test
    fun `nothing is placed until every listing has given its first page`() {
        val merge = MergedListing(2, SortType.Type.NEW)
        merge.add(0, "m1", post("a", time = 100))

        assertEquals(emptyList<String>(), merge.drain().ids())
        assertEquals(listOf(1), merge.sourcesToFetch())
    }

    @Test
    fun `a listing that failed to load stops holding the feed`() {
        val merge = MergedListing(2, SortType.Type.NEW)
        merge.add(0, null, post("a", time = 100))
        merge.endSource(1)

        assertEquals(listOf("a"), merge.drain().ids())
        assertFalse(merge.hasMore())
    }

    @Test
    fun `a page the post filter emptied still says how far down its listing has got`() {
        val merge = MergedListing(2, SortType.Type.NEW)
        merge.add(0, null, post("a", time = 100), post("b", time = 50))
        merge.addPage(1, emptyList(), listing(post("x", time = 90), post("y", time = 60)), "u1")

        assertEquals(listOf("a"), merge.drain().ids())
        assertEquals(listOf(1), merge.sourcesToFetch())
    }

    @Test
    fun `a post pinned to a profile is placed by rank, not at the head of its listing`() {
        val merge = MergedListing(2, SortType.Type.NEW)
        merge.add(0, null, post("a", time = 100), post("b", time = 60))
        merge.add(1, null, post("pinned", time = 1, pinned = true), post("u1", time = 90), post("u2", time = 50))

        assertEquals(listOf("a", "u1", "b", "u2", "pinned"), merge.drain().ids())
    }

    @Test
    fun `a pinned post does not stand for how far down its listing has got`() {
        // Last on the page, where it would otherwise set the floor and let b through ahead of
        // whatever the next page holds.
        val merge = MergedListing(2, SortType.Type.NEW)
        merge.add(0, null, post("a", time = 100), post("b", time = 60))
        merge.add(1, "u1", post("u1", time = 90), post("pinned", time = 1, pinned = true))

        assertEquals(listOf("a", "u1"), merge.drain().ids())
    }

    @Test
    fun `a listing keeps its own order where the ranking disagrees with it`() {
        // Reddit's hot is not the published formula it is weighed with here, so a multireddit's hot
        // listing is out of order by it. The merge only decides where the other listing's posts fall.
        val merge = MergedListing(2, SortType.Type.TOP)
        merge.add(0, null, post("a", score = 10), post("b", score = 50))
        merge.add(1, null, post("u", score = 30))

        assertEquals(listOf("u", "a", "b"), merge.drain().ids())
    }

    @Test
    fun `hot trades a tenfold score for 12 and a half hours`() {
        val now = 1_700_000_000L
        val older = MergedListing(2, SortType.Type.HOT)
        older.add(0, null, post("old", score = 1000, time = now - 12 * 3600))
        older.add(1, null, post("new", score = 100, time = now))
        assertEquals(listOf("old", "new"), older.drain().ids())

        val oldest = MergedListing(2, SortType.Type.HOT)
        oldest.add(0, null, post("old", score = 1000, time = now - 13 * 3600))
        oldest.add(1, null, post("new", score = 100, time = now))
        assertEquals(listOf("new", "old"), oldest.drain().ids())
    }

    @Test
    fun `controversial ranks an even split above a lopsided vote`() {
        val merge = MergedListing(2, SortType.Type.CONTROVERSIAL)
        // 100 votes each: 95 up and 5 down, against 55 up and 45 down.
        merge.add(0, null, post("lopsided", score = 90, upvoteRatio = 95))
        merge.add(1, null, post("split", score = 10, upvoteRatio = 55))

        assertEquals(listOf("split", "lopsided"), merge.drain().ids())
    }

    // ------------------------------------------------------------------ fixtures

    private fun MergedListing.add(source: Int, after: String?, vararg posts: Post) =
        addPage(source, posts.toList(), listing(*posts), after)

    private fun List<Post>.ids() = map { it.id }

    /** The page as Reddit sent it, carrying the fields the merge reads off its last post. */
    private fun listing(vararg posts: Post) = JSONObject().put(
        "data", JSONObject().put("children", JSONArray().apply {
            posts.forEach {
                put(JSONObject().put("kind", "t3").put("data", JSONObject()
                    .put("created_utc", it.postTimeMillis / 1000)
                    .put("score", it.score)
                    .put("upvote_ratio", it.upvoteRatio / 100.0)
                    .put("stickied", it.isStickied)))
            }
        })
    )

    private fun post(
        id: String,
        time: Long = 0L,
        score: Int = 0,
        upvoteRatio: Int = 100,
        pinned: Boolean = false,
    ) = Post(
        id, "t3_$id", "test", "r/test",
        "someone", "t2_someone", "", "", time * 1000,
        "A title", "/r/test/comments/$id/a_title/", score, Post.TEXT_TYPE, 0, 0,
        upvoteRatio, "", false, false,
        false, pinned, false, false, false, false,
        false, false, false, 0L, null,
        false, false, "", null
    )
}
