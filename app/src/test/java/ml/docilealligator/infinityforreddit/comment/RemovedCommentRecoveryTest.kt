package ml.docilealligator.infinityforreddit.comment

import ml.docilealligator.infinityforreddit.TestInfinity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Whole-thread comment recovery rests on two decisions nothing else checks: which of the loaded
 * comments are worth asking the archive about, and which of the records that come back are a real
 * original rather than a copy of the same scrubbing Reddit already showed. Both are silent when they
 * go wrong — a mis-keyed batch splices a body onto the wrong comment, and a placeholder accepted as
 * a recovery looks exactly like the archive having nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class RemovedCommentRecoveryTest {

    private fun comment(
        id: String,
        body: String,
        author: String = "someone",
        removed: Boolean = false,
    ): Comment = Comment(
        id, "t1_$id", author, "t2_author", "", "", null, null,
        0L, body, body,
        "t3_link", "subreddit", "t3_link", 0,
        0, false, "", "/r/subreddit/comments/link/_/$id/",
        0, false, false,
        false, false, false, false, false,
        false, 0L, null, removed, false,
        0L, null
    )

    private fun archived(id: String, body: String, author: String = "someone"): JSONObject =
        JSONObject().put("id", id).put("body", body).put("author", author)

    private fun payload(vararg comments: JSONObject): JSONObject {
        val data = JSONArray()
        comments.forEach { data.put(it) }
        return JSONObject().put("data", data)
    }

    @Test
    fun `a batch is keyed by comment id, not by the order it came back in`() {
        // The request asks for several ids at once; nothing promises the archive answers in that
        // order, or answers for every id, so position is never a safe way to match them up.
        val results = FetchRemovedComment.parseResults(
            payload(
                archived("ccc", "third"),
                archived("aaa", "first"),
            ).toString()
        )

        assertEquals(setOf("aaa", "ccc"), results.keys)
        assertEquals("first", results["aaa"]!!.body)
        assertEquals("third", results["ccc"]!!.body)
    }

    @Test
    fun `an id the archive has nothing for is absent rather than present and empty`() {
        val results = FetchRemovedComment.parseResults(
            payload(archived("aaa", "a real body")).toString()
        )

        assertEquals(setOf("aaa"), results.keys)
        assertNull("an id that was asked about but not answered must not appear", results["bbb"])
    }

    @Test
    fun `a record the archive only ever saw scrubbed is not offered as a recovery`() {
        val results = FetchRemovedComment.parseResults(
            payload(
                archived("aaa", "[removed]"),
                archived("bbb", "[ Removed by moderator: spam ]"),
                archived("ccc", "[deleted]"),
                archived("ddd", "a real body"),
            ).toString()
        )

        assertEquals(setOf("ddd"), results.keys)
    }

    @Test
    fun `a deleted account is not restored over the deleted author already showing`() {
        val results = FetchRemovedComment.parseResults(
            payload(archived("aaa", "a real body", author = "[deleted]")).toString()
        )

        assertEquals("the body is still worth recovering", "a real body", results["aaa"]!!.body)
        assertNull("restoring [deleted] over [deleted] is a no-op dressed as a recovery", results["aaa"]!!.author)
    }

    @Test
    fun `an error envelope or a broken payload recovers nothing rather than something empty`() {
        assertTrue(
            "the archive signals its own throttle with an HTTP 200 and a null data",
            FetchRemovedComment.parseResults("""{"data":null,"error":"Timeout. Maybe slow down a bit"}""").isEmpty()
        )
        assertTrue(FetchRemovedComment.parseResults("not json at all").isEmpty())
        assertTrue(FetchRemovedComment.parseResults("""{"data":[]}""").isEmpty())
    }

    @Test
    fun `every shape of scrubbing reddit leaves is worth asking the archive about`() {
        val ids = RecoverRemovedComments.candidateIds(
            listOf(
                comment("aaa", "[removed]"),
                comment("bbb", "[deleted]"),
                comment("ccc", "[ Removed by Reddit ]"),
                comment("ddd", "still here", author = "[deleted]"),
                comment("eee", "still here", removed = true),
            )
        )

        assertEquals(listOf("aaa", "bbb", "ccc", "ddd", "eee"), ids)
    }

    @Test
    fun `an ordinary comment is not asked about`() {
        val ids = RecoverRemovedComments.candidateIds(
            listOf(
                comment("aaa", "an ordinary comment"),
                comment("bbb", "the mods wrote [removed] under my comment"),
            )
        )

        assertEquals(emptyList<String>(), ids)
    }

    @Test
    fun `rows with no comment behind them are not asked about`() {
        // The "load more" and "continue thread" rows are UI, not comments, and a comment with no id
        // is not addressable in the archive — there is nothing to ask for in either case.
        val idlessRemovedComment = Comment("t1_parent", 0, Comment.NOT_PLACEHOLDER)
            .apply { setCommentRawText("[removed]") }

        val ids = RecoverRemovedComments.candidateIds(
            listOf(
                Comment("t1_parent", 0, Comment.PLACEHOLDER_LOAD_MORE_COMMENTS),
                Comment("t1_parent", 0, Comment.PLACEHOLDER_CONTINUE_THREAD),
                idlessRemovedComment,
            )
        )

        assertEquals(emptyList<String>(), ids)
    }
}
