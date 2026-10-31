package ml.docilealligator.infinityforreddit.viewmodels

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.comment.Comment
import ml.docilealligator.infinityforreddit.comment.ParseComment
import ml.docilealligator.infinityforreddit.post.Post
import ml.docilealligator.infinityforreddit.thing.SortType
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Pins which endpoint "Load more comments" reaches for. Focused comment listings are the default;
 * `/api/morechildren` is only for someone who turned on Settings > Interface > Comment's option.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class LoadMoreCommentsRouteTest {

    @get:Rule
    val instantRule = InstantTaskExecutorRule()

    private lateinit var db: RedditDataRoomDatabase
    private val requests = CopyOnWriteArrayList<Request>()
    private var responseBody = "{}"

    /** Answers each request; the default serves [responseBody] with a 200. */
    private var respond: (Request) -> Pair<Int, String> = { 200 to responseBody }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), RedditDataRoomDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `the footer uses focused comments by default`() {
        viewModel(useLegacyMoreChildren = false, children = arrayListOf("a")).fetchMoreComments()

        awaitMainUntil { requests.isNotEmpty() }
        assertEquals("GET", requests[0].method)
        assertEquals("/comments/postid/placeholder/a.json", requests[0].url.encodedPath)
    }

    @Test
    fun `the footer uses morechildren when the option is on`() {
        viewModel(useLegacyMoreChildren = true, children = arrayListOf("a", "b")).fetchMoreComments()

        awaitMainUntil { requests.isNotEmpty() }
        assertEquals(1, requests.size)
        assertEquals("POST", requests[0].method)
        assertEquals("/api/morechildren.json", requests[0].url.encodedPath)
    }

    @Test
    fun `a reply placeholder uses focused comments by default`() {
        viewModel(useLegacyMoreChildren = false, comments = threadWithPlaceholder()).fetchMoreChildComments(1)

        awaitMainUntil { requests.isNotEmpty() }
        assertEquals("/comments/postid/placeholder/a.json", requests[0].url.encodedPath)
    }

    @Test
    fun `a reply placeholder uses morechildren when the option is on`() {
        viewModel(useLegacyMoreChildren = true, comments = threadWithPlaceholder()).fetchMoreChildComments(1)

        awaitMainUntil { requests.isNotEmpty() }
        assertEquals("POST", requests[0].method)
        assertEquals("/api/morechildren.json", requests[0].url.encodedPath)
    }

    @Test
    fun `loading replies swaps their ids in the parent's count for the replies themselves`() {
        val thread = threadWithPlaceholder()
        thread[0].childCount = 1
        thread[0].setExpanded(true)
        val reply = JSONObject().put("kind", "t1").put("data", commentData("a", "t1_parent", 0).put(
            "replies", JSONObject().put("kind", "Listing").put("data", JSONObject().put("children", JSONArray().put(
                JSONObject().put("kind", "t1").put("data", commentData("b", "t1_a", 1))
            )))
        ))
        responseBody = JSONArray().put(JSONObject()).put(
            JSONObject().put("kind", "Listing").put("data", JSONObject().put("children", JSONArray().put(reply)))
        ).toString()
        val viewModel = viewModel(useLegacyMoreChildren = false, comments = thread)
        viewModel.dataState.observeForever { }

        viewModel.fetchMoreChildComments(1)

        awaitMainUntil { viewModel.dataState.value?.comments?.any { it.id == "a" } == true }
        assertEquals(2, viewModel.dataState.value!!.comments!![0].childCount)
    }

    @Test
    fun `a collapsed parent and the comment above it are recounted once replies load`() {
        val grand = ParseComment.parseSingleComment(commentData("grand", "t3_postid", 0), 0)
        val parent = ParseComment.parseSingleComment(commentData("parent", "t1_grand", 1), 0)
        val placeholder = Comment(parent.fullName, 2, Comment.PLACEHOLDER_LOAD_MORE_COMMENTS)
        parent.setMoreChildrenIds(arrayListOf("a"))
        parent.moreChildrenCount = 5
        parent.setHasReply(true)
        parent.addChildren(arrayListOf(placeholder))
        parent.setExpanded(false)
        grand.addChildren(arrayListOf(parent))
        grand.setExpanded(true)
        ParseComment.updateChildrenCount(listOf(grand))
        assertEquals(6, grand.childCount)
        val reply = JSONObject().put("kind", "t1").put("data", commentData("a", "t1_parent", 0).put(
            "replies", JSONObject().put("kind", "Listing").put("data", JSONObject().put("children", JSONArray().put(
                JSONObject().put("kind", "t1").put("data", commentData("b", "t1_a", 1))
            )))
        ))
        responseBody = JSONArray().put(JSONObject()).put(
            JSONObject().put("kind", "Listing").put("data", JSONObject().put("children", JSONArray().put(reply)))
        ).toString()
        val viewModel = viewModel(useLegacyMoreChildren = false, comments = arrayListOf(grand, parent, placeholder))
        viewModel.dataState.observeForever { }

        viewModel.fetchMoreChildComments(2)

        awaitMainUntil { viewModel.dataState.value?.comments?.get(1)?.childCount == 2 }
        assertEquals(3, viewModel.dataState.value!!.comments!![0].childCount)
    }

    @Test
    fun `after a partial load the thread's own copy of the parent knows what is left`() {
        // The parent's row is a copy (expanding it makes one), while collapsing and re-expanding the
        // comment above brings back the thread's own object. That one must not ask for "a" again.
        val grand = ParseComment.parseSingleComment(commentData("grand", "t3_postid", 0), 0)
        val parent = ParseComment.parseSingleComment(commentData("parent", "t1_grand", 1), 0)
        val placeholder = Comment(parent.fullName, 2, Comment.PLACEHOLDER_LOAD_MORE_COMMENTS)
        parent.setMoreChildrenIds(arrayListOf("a", "b"))
        parent.moreChildrenCount = 2
        parent.setHasReply(true)
        parent.addChildren(arrayListOf(placeholder))
        grand.setHasReply(true)
        grand.addChildren(arrayListOf(parent))
        grand.setExpanded(true)
        val parentRow = Comment(parent).apply { setExpanded(true) }
        respond = { request ->
            val id = request.url.pathSegments.last().removeSuffix(".json")
            if (id == "b") 403 to "blocked" else 200 to listing(commentData(id, "t1_parent", 0))
        }
        val viewModel = viewModel(useLegacyMoreChildren = false, comments = arrayListOf(grand, parentRow, placeholder))
        viewModel.dataState.observeForever { }

        viewModel.fetchMoreChildComments(2)

        awaitMainUntil { viewModel.dataState.value?.comments?.any { it.id == "a" } == true }
        assertEquals(listOf("b"), parent.moreChildrenIds)
    }

    @Test
    fun `a parent collapsed while its replies load gets a row DiffUtil sees as changed`() {
        val parent = ParseComment.parseSingleComment(commentData("parent", "t3_postid", 0), 0)
        val placeholder = Comment(parent.fullName, 1, Comment.PLACEHOLDER_LOAD_MORE_COMMENTS)
        parent.setMoreChildrenIds(arrayListOf("a"))
        parent.moreChildrenCount = 1
        parent.setHasReply(true)
        parent.addChildren(arrayListOf(placeholder))
        parent.setExpanded(true)
        ParseComment.updateChildrenCount(listOf(parent))
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        respond = {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            200 to listing(commentData("a", "t1_parent", 0).put("replies", JSONObject().put("kind", "Listing")
                .put("data", JSONObject().put("children", JSONArray().put(
                    JSONObject().put("kind", "t1").put("data", commentData("b", "t1_a", 1))
                )))))
        }
        val viewModel = viewModel(useLegacyMoreChildren = false, comments = arrayListOf(parent, placeholder))
        viewModel.dataState.observeForever { }
        viewModel.fetchMoreChildComments(1)
        awaitMainUntil { started.count == 0L }
        viewModel.collapseComment(0)
        val drawnRow = viewModel.dataState.value!!.comments!![0]
        val drawnCount = drawnRow.childCount

        release.countDown()

        awaitMainUntil { viewModel.dataState.value!!.comments!![0] !== drawnRow }
        assertEquals("the row DiffUtil compares against", drawnCount, drawnRow.childCount)
        assertEquals(2, viewModel.dataState.value!!.comments!![0].childCount)
    }

    private fun listing(vararg comments: JSONObject): String = JSONArray().put(JSONObject()).put(
        JSONObject().put("kind", "Listing").put("data", JSONObject().put("children",
            JSONArray(comments.map { JSONObject().put("kind", "t1").put("data", it) })))
    ).toString()

    @Test
    fun `the option is on the comment settings screen and off by default`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parser = context.resources.getXml(R.xml.comment_preferences)
        val android = "http://schemas.android.com/apk/res-auto"
        var found = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG
                && parser.getAttributeValue(android, "key") == SharedPreferencesUtils.USE_LEGACY_MORE_CHILDREN
            ) {
                found = true
                assertEquals("false", parser.getAttributeValue(android, "defaultValue"))
            }
        }
        assertTrue(found)
    }

    private fun threadWithPlaceholder(): ArrayList<Comment> {
        val parent = ParseComment.parseSingleComment(commentData("parent", "t3_postid", 0), 0)
        val placeholder = Comment(parent.fullName, 1, Comment.PLACEHOLDER_LOAD_MORE_COMMENTS)
        parent.setMoreChildrenIds(arrayListOf("a"))
        parent.addChildren(arrayListOf(placeholder))
        return arrayListOf(parent, placeholder)
    }

    private fun commentData(id: String, parentId: String, depth: Int) = JSONObject("""{
        "id":"$id",
        "name":"t1_$id",
        "author":"someone",
        "author_flair_text":null,
        "link_id":"t3_postid",
        "subreddit":"test",
        "parent_id":"$parentId",
        "is_submitter":false,
        "distinguished":null,
        "body":"a comment",
        "body_html":"&lt;p&gt;a comment&lt;/p&gt;",
        "permalink":"/r/test/comments/postid/_/$id/",
        "score":1,
        "likes":null,
        "created_utc":1754236800,
        "score_hidden":false,
        "saved":false,
        "send_replies":true,
        "locked":false,
        "can_mod_post":false,
        "collapsed":false,
        "replies":"",
        "edited":false,
        "depth":$depth
    }""")

    private fun viewModel(
        useLegacyMoreChildren: Boolean,
        children: ArrayList<String>? = null,
        comments: ArrayList<Comment>? = null
    ): ViewPostDetailFragmentViewModelNew {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("load_more_comments_route_test", Context.MODE_PRIVATE)
        val retrofit = recordingRetrofit()
        return ViewPostDetailFragmentViewModelNew(
            retrofit,
            retrofit,
            retrofit,
            db,
            "token",
            "someone",
            post(),
            "postid",
            null,
            comments,
            children,
            SortType.Type.BEST,
            prefs,
            prefs,
            false,
            false,
            SortType.Type.BEST,
            false,
            false,
            "3",
            useLegacyMoreChildren
        )
    }

    private fun post() = Post(
        "postid", "t3_postid", "test", "r/test", "author", "t2_author", "", "", 0L,
        "title", "https://example.com/x", "/r/test/comments/postid/", 10, 0, 0, 0,
        100, "", false, false, false, false, false, false, false, false,
        false, false, false, 0L, null, false, false, "", null,
    )

    private fun recordingRetrofit(): Retrofit {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requests.add(chain.request())
                val (code, body) = respond(chain.request())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return Retrofit.Builder()
            .baseUrl("https://oauth.reddit.com/")
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
    }
}
