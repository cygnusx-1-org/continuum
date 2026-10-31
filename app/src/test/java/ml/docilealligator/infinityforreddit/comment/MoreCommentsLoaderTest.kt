package ml.docilealligator.infinityforreddit.comment

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import ml.docilealligator.infinityforreddit.commentfilter.CommentFilter
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pins [MoreCommentsLoader] against the listing shapes Reddit returned for a focused comment when
 * `/api/morechildren` was blocked (issue #475): the focused comment at depth 0 wherever it sits in
 * the thread, an empty listing for a deleted comment, and a `more` for what a limit cut off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MoreCommentsLoaderTest {
    private val requests = ArrayList<Pair<String, Int?>>()
    private var now = 1_000L
    private val session = MoreCommentsLoader.Session { now }

    private fun loader(
        filter: CommentFilter = CommentFilter(),
        serve: suspend (String) -> Response<String>
    ) = MoreCommentsLoader(filter, session, { id, limit ->
        synchronized(requests) { requests.add(id to limit) }
        serve(id)
    }, Dispatchers.Unconfined)

    @Test
    fun repliesAreRebasedFromFocusedDepthToTheirPlaceInTheThread() = runBlocking {
        val child = comment("a", "t1_parent", depth = 0, replies = listOf(
            comment("b", "t1_a", depth = 1, replies = listOf(more("t1_b", 2, "c", "d")))
        ))

        val page = loader { ok(listing(child)) }.loadReplies("parent", 3, listOf("a"))!!

        val a = page.topLevelComments.single()
        assertEquals(3, a.depth)
        val b = a.children.single()
        assertEquals(4, b.depth)
        assertEquals(listOf("c", "d"), b.moreChildrenIds)
    }

    @Test
    fun rebasedRepliesAttachToTheirParent() = runBlocking {
        // Comment.addChildren asserts child depth == parent depth + 1 in debug builds, and a thrown
        // assertion here is what kept "Load failed" on screen with unrebased depths.
        val parent = ParseComment.parseSingleComment(comment("parent", "t3_post", depth = 2).getJSONObject("data"), 0)

        val page = loader { ok(listing(comment("a", "t1_parent", depth = 0))) }
            .loadReplies("parent", parent.depth + 1, listOf("a"))!!

        parent.addChildren(page.topLevelComments)
        assertEquals("a", parent.children.single().id)
    }

    @Test
    fun manyRepliesComeFromOneParentListingWithoutWhatIsAlreadyOnScreen() = runBlocking {
        val wanted = listOf("a", "b", "c", "d", "e", "f", "g")
        val replies = listOf(comment("onscreen", "t1_parent", depth = 1)) +
            listOf("a", "b", "d", "e").map { comment(it, "t1_parent", depth = 1) } +
            more("t1_parent", 1, "f", "g", "notwanted")
        val root = comment("parent", "t3_post", depth = 0, replies = replies)

        val page = loader { ok(listing(root)) }.loadReplies("parent", 2, wanted)!!

        assertEquals(listOf("parent" to MoreCommentsLoader.PARENT_LISTING_LIMIT), requests)
        assertEquals(listOf("a", "b", "d", "e"), page.topLevelComments.map { it.id })
        assertEquals(listOf(2, 2, 2, 2), page.topLevelComments.map { it.depth })
        // "c" was neither returned nor deferred, which is how a deleted comment shows up.
        assertEquals(listOf("f", "g"), page.remainingIds)
    }

    @Test
    fun aFewRepliesSkipTheParentListing() = runBlocking {
        val page = loader { id -> ok(listing(comment(id, "t1_parent", depth = 0))) }
            .loadReplies("parent", 1, listOf("a", "b"))!!

        assertEquals(setOf("a" to null, "b" to null), requests.toSet())
        assertEquals(listOf("a", "b"), page.topLevelComments.map { it.id })
    }

    @Test
    fun aDeletedParentFallsBackToIndividualListings() = runBlocking {
        val ids = listOf("a", "b", "c", "d", "e")

        val page = loader { id ->
            ok(if (id == "parent") listing() else listing(comment(id, "t1_parent", depth = 0)))
        }.loadReplies("parent", 1, ids)!!

        assertEquals(ids, page.topLevelComments.map { it.id })
        assertEquals(ids.size + 1, requests.size)
    }

    @Test
    fun aParentListingThatDefersEverythingFallsBackToIndividualListings() = runBlocking {
        val ids = listOf("a", "b", "c", "d", "e")
        val root = comment("parent", "t3_post", depth = 0, replies = listOf(
            comment("onscreen", "t1_parent", depth = 1), more("t1_parent", 1, *ids.toTypedArray())
        ))

        val page = loader { id ->
            ok(if (id == "parent") listing(root) else listing(comment(id, "t1_parent", depth = 0)))
        }.loadReplies("parent", 1, ids)!!

        assertEquals(ids, page.topLevelComments.map { it.id })
    }

    @Test
    fun anUnusableParentListingFallsBackToIndividualListings() = runBlocking {
        val ids = listOf("a", "b", "c", "d", "e")

        val page = loader { id ->
            ok(if (id == "parent") "<html>" else listing(comment(id, "t1_parent", depth = 0)))
        }.loadReplies("parent", 1, ids)!!

        assertEquals(ids, page.topLevelComments.map { it.id })
    }

    @Test
    fun aBlockedParentListingFailsWithoutSpendingMoreRequests() = runBlocking {
        val page = loader { blocked() }.loadReplies("parent", 1, listOf("a", "b", "c", "d", "e"))

        assertNull(page)
        assertEquals(listOf("parent" to MoreCommentsLoader.PARENT_LISTING_LIMIT), requests)
    }

    @Test
    fun aParentWithNoRepliesLeftLeavesNothingToLoad() = runBlocking {
        val root = comment("parent", "t3_post", depth = 0)

        val page = loader { ok(listing(root)) }.loadReplies("parent", 1, listOf("a", "b", "c", "d", "e"))!!

        assertEquals(0, page.topLevelComments.size)
        assertEquals(0, page.remainingIds.size)
        assertEquals(1, requests.size)
    }

    @Test
    fun aTopLevelMoreLoadsOnePageAndKeepsTheRestInOrder() = runBlocking {
        val ids = (0 until 60).map { "c$it" }

        val page = loader { id -> ok(listing(comment(id, "t3_post", depth = 0))) }.loadTopLevel(ids)!!

        assertEquals(ids.take(MoreCommentsLoader.PAGE_SIZE), page.topLevelComments.map { it.id })
        assertEquals(ids.drop(MoreCommentsLoader.PAGE_SIZE), page.remainingIds)
        assertEquals(MoreCommentsLoader.PAGE_SIZE, requests.size)
    }

    @Test
    fun responsesArrivingOutOfOrderKeepThreadOrderAndRespectTheConcurrencyCap() = runBlocking {
        val ids = (0 until MoreCommentsLoader.PAGE_SIZE).map { "c$it" }
        val inFlight = AtomicInteger()
        val peak = AtomicInteger()

        val page = MoreCommentsLoader(CommentFilter(), session, { id, _ ->
            val now = inFlight.incrementAndGet()
            peak.updateAndGet { maxOf(it, now) }
            delay((MoreCommentsLoader.PAGE_SIZE - id.drop(1).toInt()).toLong())
            inFlight.decrementAndGet()
            ok(listing(comment(id, "t3_post", depth = 0)))
        }, Dispatchers.Default).loadTopLevel(ids)!!

        assertEquals(ids, page.topLevelComments.map { it.id })
        assertEquals(MoreCommentsLoader.MAX_CONCURRENT_REQUESTS, peak.get())
    }

    @Test
    fun aFailureKeepsWhatCameBeforeItAndRetriesFromThere() = runBlocking {
        val ids = (0 until 10).map { "c$it" }

        val page = loader { id ->
            if (id == "c6") blocked() else ok(listing(comment(id, "t3_post", depth = 0)))
        }.loadTopLevel(ids)!!

        val loaded = page.topLevelComments.map { it.id }
        assertEquals(ids.take(loaded.size), loaded)
        assertEquals(ids.drop(loaded.size), page.remainingIds)
        assertNotNull(page.remainingIds.singleOrNull { it == "c6" })
    }

    @Test
    fun aFailureBeforeAnyCommentReportsFailure() = runBlocking {
        assertNull(loader { blocked() }.loadTopLevel(listOf("a", "b")))
    }

    @Test
    fun aTimeoutCountsAsAFailure() = runBlocking {
        assertNull(loader { throw java.io.IOException("timeout") }.loadTopLevel(listOf("a")))
    }

    @Test
    fun aPageOfDeletedCommentsMovesOnToTheNextPage() = runBlocking {
        val ids = (0 until 30).map { "c$it" }

        val page = loader { id ->
            ok(if (id == "c27") listing(comment(id, "t3_post", depth = 0)) else listing())
        }.loadTopLevel(ids)!!

        assertEquals(listOf("c27"), page.topLevelComments.map { it.id })
        assertEquals(0, page.remainingIds.size)
        assertEquals(30, requests.size)
    }

    @Test
    fun emptyPagesStopAtTheCap() = runBlocking {
        val cap = MoreCommentsLoader.MAX_EMPTY_PAGES * MoreCommentsLoader.PAGE_SIZE
        val ids = (0 until cap + 3).map { "c$it" }

        val page = loader { ok(listing()) }.loadTopLevel(ids)!!

        assertEquals(cap, requests.size)
        assertEquals(ids.drop(cap), page.remainingIds)
    }

    @Test
    fun aDescendantInTheSamePageIsNotShownTwice() = runBlocking {
        val withChild = comment("a", "t3_post", depth = 0, replies = listOf(comment("b", "t1_a", depth = 1)))

        val page = loader { id ->
            ok(if (id == "a") listing(withChild) else listing(comment("b", "t1_a", depth = 0)))
        }.loadTopLevel(listOf("b", "a"))!!

        assertEquals(listOf("a"), page.topLevelComments.map { it.id })
    }

    @Test
    fun aDescendantInALaterPageIsNotFetched() = runBlocking {
        val first = (0 until MoreCommentsLoader.PAGE_SIZE).map { "c$it" }
        val withChild = comment("c0", "t3_post", depth = 0, replies = listOf(comment("later", "t1_c0", depth = 1)))

        val page = loader { id ->
            ok(if (id == "c0") listing(withChild) else listing(comment(id, "t3_post", depth = 0)))
        }.loadTopLevel(first + "later" + "other")!!

        assertEquals(listOf("other"), page.remainingIds)
    }

    @Test
    fun anUnparseableCommentIsDroppedWithoutBlockingTheOthers() = runBlocking {
        val page = loader { id ->
            ok(if (id == "b") "<html>" else listing(comment(id, "t3_post", depth = 0)))
        }.loadTopLevel(listOf("a", "b", "c"))!!

        assertEquals(listOf("a", "c"), page.topLevelComments.map { it.id })
        assertEquals(0, page.remainingIds.size)
    }

    @Test
    fun aListingOfADifferentCommentIsDropped() = runBlocking {
        val page = loader { ok(listing(comment("unexpected", "t3_post", depth = 0))) }.loadTopLevel(listOf("a"))!!

        assertEquals(0, page.topLevelComments.size)
    }

    @Test
    fun commentFiltersApplyToLoadedComments() = runBlocking {
        val filter = CommentFilter().apply {
            displayMode = CommentFilter.DisplayMode.REMOVE_COMMENT
            excludeUsers = "someone"
        }

        val page = loader(filter) { id ->
            ok(listing(comment(id, "t3_post", depth = 0, author = if (id == "a") "someone" else "other")))
        }.loadTopLevel(listOf("a", "b"))!!

        assertEquals(listOf("b"), page.topLevelComments.map { it.id })
    }

    @Test
    fun aCommentWhoseOnlyRepliesAreBehindAMoreStillCountsThem() {
        val comments = ArrayList<Comment>()
        ParseComment.parseCommentRecursion(
            JSONArray().put(comment("a", "t3_post", depth = 0, replies = listOf(more("t1_a", 1, "b", "c")))),
            comments, ArrayList(), 0, CommentFilter()
        )

        assertEquals(2, comments.single().childCount)
    }

    @Test
    fun aCommentCountsWhatIsBehindAMoreFurtherDown() {
        val comments = ArrayList<Comment>()
        ParseComment.parseCommentRecursion(
            JSONArray().put(comment("a", "t3_post", depth = 0, replies = listOf(
                comment("b", "t1_a", depth = 1, replies = listOf(more("t1_b", 2, "c", "d")))
            ))),
            comments, ArrayList(), 0, CommentFilter()
        )

        assertEquals(3, comments.single().childCount)
        assertEquals(2, comments.single().children.single().childCount)
    }

    @Test
    fun aCommentCountsRedditsTotalForWhatIsBehindAMore() {
        val more = more("t1_a", 1, "b", "c")
        more.getJSONObject("data").put("count", 10)
        val comments = ArrayList<Comment>()
        ParseComment.parseCommentRecursion(
            JSONArray().put(comment("a", "t3_post", depth = 0, replies = listOf(more))),
            comments, ArrayList(), 0, CommentFilter()
        )

        assertEquals(10, comments.single().childCount)
    }

    @Test
    fun aCountBelowTheIdsLeftIsRaisedToThem() {
        val more = more("t1_a", 1, "b", "c", "d")
        more.getJSONObject("data").put("count", 1)
        val comments = ArrayList<Comment>()
        ParseComment.parseCommentRecursion(
            JSONArray().put(comment("a", "t3_post", depth = 0, replies = listOf(more))),
            comments, ArrayList(), 0, CommentFilter()
        )

        assertEquals(3, comments.single().childCount)
    }

    @Test
    fun theLoadMorePlaceholderIsNotCountedAsAReply() {
        val comments = ArrayList<Comment>()
        ParseComment.parseCommentRecursion(
            JSONArray().put(comment("a", "t3_post", depth = 0, replies = listOf(
                comment("b", "t1_a", depth = 1), more("t1_a", 1, "c", "d")
            ))),
            comments, ArrayList(), 0, CommentFilter()
        )
        ParseComment.expandChildren(comments, ArrayList(), true)

        assertEquals(3, ParseComment.getChildCount(comments.single()))
    }

    @Test
    fun aParentListingReportsRedditsCountForWhatItDeferred() = runBlocking {
        val deferred = more("t1_parent", 1, "f", "g")
        deferred.getJSONObject("data").put("count", 7)
        val root = comment("parent", "t3_post", depth = 0, replies = listOf(
            comment("a", "t1_parent", depth = 1), comment("b", "t1_parent", depth = 1),
            comment("c", "t1_parent", depth = 1), comment("d", "t1_parent", depth = 1),
            comment("e", "t1_parent", depth = 1), deferred
        ))

        val page = loader { ok(listing(root)) }.loadReplies("parent", 1, listOf("a", "b", "c", "d", "e", "f", "g"))!!

        assertEquals(listOf("f", "g"), page.remainingIds)
        assertEquals(7, page.remainingCount)
    }

    @Test
    fun addingRepliesCountsTheirWholeSubtrees() {
        val comments = ArrayList<Comment>()
        ParseComment.parseCommentRecursion(
            JSONArray().put(comment("a", "t1_parent", depth = 1, replies = listOf(comment("b", "t1_a", depth = 2)))),
            comments, ArrayList(), 1, CommentFilter()
        )
        val parent = ParseComment.parseSingleComment(comment("parent", "t3_post", depth = 0).getJSONObject("data"), 0)

        parent.addChildren(comments)

        assertEquals(2, parent.childCount)
    }

    @Test
    fun newRepliesGoBeforeTheLoadMorePlaceholder() {
        val parent = ParseComment.parseSingleComment(comment("parent", "t3_post", depth = 0).getJSONObject("data"), 0)
        val placeholder = Comment(parent.fullName, 1, Comment.PLACEHOLDER_LOAD_MORE_COMMENTS)
        parent.addChildren(arrayListOf(placeholder))

        val first = ParseComment.parseSingleComment(comment("first", "t1_parent", depth = 1).getJSONObject("data"), 1)
        val second = ParseComment.parseSingleComment(comment("second", "t1_parent", depth = 1).getJSONObject("data"), 1)
        parent.addChildren(arrayListOf(first))
        parent.addChildren(arrayListOf(second))

        assertEquals(listOf(first, second, placeholder), parent.children)
    }

    @Test
    fun aParentIsListedOnceAndWhatItDeferredLoadsIndividually() = runBlocking {
        val ids = listOf("a", "b", "c", "d", "e", "f", "g")
        val root = comment("parent", "t3_post", depth = 0, replies = listOf(
            comment("a", "t1_parent", depth = 1), more("t1_parent", 1, "b", "c", "d", "e", "f", "g")
        ))
        val serve: suspend (String) -> Response<String> = { id ->
            ok(if (id == "parent") listing(root) else listing(comment(id, "t1_parent", depth = 0)))
        }

        val first = loader(serve = serve).loadReplies("parent", 1, ids)!!
        requests.clear()
        val second = loader(serve = serve).loadReplies("parent", 1, first.remainingIds)!!

        assertEquals(first.remainingIds.map { it to null }.toSet(), requests.toSet())
        assertEquals(first.remainingIds, second.topLevelComments.map { it.id })
    }

    @Test
    fun aFailedParentListingIsAskedForAgainOnRetry() = runBlocking {
        val ids = listOf("a", "b", "c", "d", "e")

        assertNull(loader { blocked() }.loadReplies("parent", 1, ids))
        requests.clear()
        loader { ok(listing(comment("parent", "t3_post", depth = 0))) }.loadReplies("parent", 1, ids)

        assertEquals(listOf("parent" to MoreCommentsLoader.PARENT_LISTING_LIMIT), requests)
    }

    @Test
    fun aReloadedThreadListsItsParentsAgain() = runBlocking {
        val ids = listOf("a", "b", "c", "d", "e")
        val serve: suspend (String) -> Response<String> = { ok(listing(comment("parent", "t3_post", depth = 0))) }

        loader(serve = serve).loadReplies("parent", 1, ids)
        session.onThreadReloaded()
        requests.clear()
        loader(serve = serve).loadReplies("parent", 1, ids)

        assertEquals(listOf("parent" to MoreCommentsLoader.PARENT_LISTING_LIMIT), requests)
    }

    @Test
    fun aLowBudgetKeepsWhatLoadedAndStopsSpending() = runBlocking {
        val ids = listOf("a", "b", "c", "d")

        val page = loader { id -> withBudget(listing(comment(id, "t3_post", depth = 0)), "15.0", "120") }
            .loadTopLevel(ids)!!

        assertEquals(1, requests.size)
        assertEquals(listOf("a"), page.topLevelComments.map { it.id })
        assertEquals(listOf("b", "c", "d"), page.remainingIds)
    }

    @Test
    fun theNextLoadWaitsOutTheBudgetAndThenResumes() = runBlocking {
        val serve: suspend (String) -> Response<String> = { id ->
            withBudget(listing(comment(id, "t3_post", depth = 0)), "15.0", "120")
        }
        loader(serve = serve).loadTopLevel(listOf("a", "b"))
        requests.clear()

        assertNull(loader(serve = serve).loadTopLevel(listOf("b")))
        assertNull(loader(serve = serve).loadReplies("parent", 1, listOf("a", "b", "c", "d", "e")))
        assertEquals(0, requests.size)

        now += 120_000
        assertEquals(listOf("b"), loader(serve = serve).loadTopLevel(listOf("b"))!!.topLevelComments.map { it.id })
    }

    @Test
    fun aHealthyBudgetDoesNotPause() = runBlocking {
        val page = loader { id -> withBudget(listing(comment(id, "t3_post", depth = 0)), "9993.0", "3") }
            .loadTopLevel(listOf("a", "b", "c", "d"))!!

        assertEquals(4, page.topLevelComments.size)
    }

    @Test
    fun a429PausesEvenWithoutRateLimitHeaders() = runBlocking {
        assertNull(loader { tooManyRequests() }.loadTopLevel(listOf("a")))
        requests.clear()

        assertNull(loader { ok(listing()) }.loadTopLevel(listOf("a")))
        assertEquals(0, requests.size)

        now += MoreCommentsLoader.MIN_RATE_LIMIT_PAUSE_MILLIS
        assertNotNull(loader { ok(listing()) }.loadTopLevel(listOf("a")))
    }

    private fun ok(body: String): Response<String> = Response.success(body)

    private fun withBudget(body: String, remaining: String, reset: String): Response<String> = Response.success(
        body,
        Headers.headersOf("x-ratelimit-remaining", remaining, "x-ratelimit-reset", reset)
    )

    private fun tooManyRequests(): Response<String> =
        Response.error(429, "Too Many Requests".toResponseBody("text/plain".toMediaType()))

    private fun blocked(): Response<String> =
        Response.error(403, "blocked by network security".toResponseBody("text/html".toMediaType()))

    private fun listing(vararg comments: JSONObject): String = JSONArray()
        .put(JSONObject())
        .put(JSONObject().put("kind", "Listing").put("data", JSONObject().put("children", JSONArray(comments.toList()))))
        .toString()

    private fun more(parentId: String, depth: Int, vararg children: String) = JSONObject()
        .put("kind", "more")
        .put("data", JSONObject()
            .put("count", children.size)
            .put("id", children.firstOrNull() ?: "_")
            .put("name", "t1_" + (children.firstOrNull() ?: "_"))
            .put("parent_id", parentId)
            .put("depth", depth)
            .put("children", JSONArray(children.toList())))

    private fun comment(
        id: String,
        parentId: String,
        depth: Int,
        replies: List<JSONObject> = emptyList(),
        author: String = "someone-else"
    ): JSONObject {
        val data = JSONObject("""{
            "id":"$id",
            "name":"t1_$id",
            "author":"$author",
            "author_flair_text":null,
            "link_id":"t3_post",
            "subreddit":"test",
            "parent_id":"$parentId",
            "is_submitter":false,
            "distinguished":null,
            "body":"a comment",
            "body_html":"&lt;p&gt;a comment&lt;/p&gt;",
            "permalink":"/r/test/comments/post/_/$id/",
            "score":1,
            "likes":null,
            "created_utc":1754236800,
            "score_hidden":false,
            "saved":false,
            "send_replies":true,
            "locked":false,
            "can_mod_post":false,
            "collapsed":false,
            "edited":false,
            "depth":$depth
        }""")
        data.put("replies", if (replies.isEmpty()) "" else JSONObject()
            .put("kind", "Listing")
            .put("data", JSONObject().put("children", JSONArray(replies))))
        return JSONObject().put("kind", "t1").put("data", data)
    }
}
