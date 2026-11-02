package ml.docilealligator.infinityforreddit.post

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.postfilter.PostFilter
import ml.docilealligator.infinityforreddit.readpost.ReadPost
import ml.docilealligator.infinityforreddit.readpost.ReadPostType
import ml.docilealligator.infinityforreddit.utils.APIUtils
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/**
 * The History screen's Read Posts list for a logged-in account. Reddit answers `/api/info` with a
 * 403 "blocked by network security" page for the default client ID, logged in as well as
 * anonymously (issue #476), which left the list at "Error loading posts". So by default a logged-in
 * account reads each post's own listing instead, and only "Use /api/info the Old Way" asks
 * `/api/info`, alone, as before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class HistoryPostPagingSourceTest {
    private lateinit var db: RedditDataRoomDatabase
    private val requests = CopyOnWriteArrayList<Request>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        APIUtils.initConfigurableFields(context)
        db = Room.inMemoryDatabaseBuilder(context, RedditDataRoomDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        useOldApiInfo(false)
        db.close()
    }

    /** The switch, and Enable Overrides, without which it counts for nothing. */
    private fun useOldApiInfo(on: Boolean, overrides: Boolean = on) {
        context.getSharedPreferences(SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(SharedPreferencesUtils.ENABLE_API_KEY_OVERRIDES_PREF_KEY, overrides)
            .putBoolean(SharedPreferencesUtils.USE_OLD_API_INFO_PREF_KEY, on).commit()
    }

    @Test
    fun `a logged-in account's read posts load one by one without asking api info`() {
        val posts = RedditPreviewFixtures.NAMES.map { RedditPreviewFixtures.json(it) }.associateBy { it.getString("id") }
        val source = historySource { request ->
            200 to commentsListing(posts.getValue(request.url.encodedPath.removePrefix("/comments/").removeSuffix(".json")))
        }
        val readPosts = posts.keys.mapIndexed { i, id ->
            ReadPost(ACCOUNT, id, ReadPostType.READ_POSTS).apply { setTime(3_000L - i) }
        }

        val result = source.transformData(readPosts)

        assertTrue(result.toString(), result is PagingSource.LoadResult.Page)
        assertEquals(posts.keys.toList(), (result as PagingSource.LoadResult.Page).data.map { it.id })
        assertEquals(posts.keys.map { "/comments/$it.json" }.toSet(), requests.map { it.url.encodedPath }.toSet())
        assertEquals(posts.size, requests.size)
        for (request in requests) {
            assertEquals(APIUtils.AUTHORIZATION_BASE + TOKEN, request.header(APIUtils.AUTHORIZATION_KEY))
        }
    }

    @Test
    fun `the old way reads a logged-in account's posts from api info alone`() {
        useOldApiInfo(true)
        val source = historySource { 200 to """{"kind":"Listing","data":{"children":[],"after":null}}""" }

        val result = source.transformData(listOf(ReadPost(ACCOUNT, "a", ReadPostType.READ_POSTS)))

        assertTrue(result.toString(), result is PagingSource.LoadResult.Page)
        assertEquals("t3_a", requests.single().url.queryParameter("id"))
    }

    @Test
    fun `the old way counts for nothing while overrides are off, where it is greyed out`() {
        useOldApiInfo(true, overrides = false)
        val source = historySource { 200 to commentsListing(RedditPreviewFixtures.json(RedditPreviewFixtures.NAMES[0])) }

        source.transformData(listOf(ReadPost(ACCOUNT, "a", ReadPostType.READ_POSTS)))

        assertEquals("/comments/a.json", requests.single().url.encodedPath)
    }

    @Test
    fun `the old way fails while api info is blocked, as it always did`() {
        useOldApiInfo(true)
        val source = historySource { 403 to "<body>blocked by network security</body>" }

        val result = source.transformData(listOf(ReadPost(ACCOUNT, "a", ReadPostType.READ_POSTS)))

        assertTrue(result.toString(), result is PagingSource.LoadResult.Error)
        assertEquals("/api/info.json", requests.single().url.encodedPath)
    }

    private fun historySource(respond: (Request) -> Pair<Int, String>) = HistoryPostPagingSource(
        recordingRetrofit(respond), Executor { it.run() }, db, TOKEN, ACCOUNT,
        context.getSharedPreferences("test", Context.MODE_PRIVATE),
        ACCOUNT, ReadPostType.READ_POSTS, PostFilter()
    )

    /** What `/comments/<id>?limit=1` returns: the post alone in the first listing. */
    private fun commentsListing(post: JSONObject): String = JSONArray()
        .put(listing(JSONObject().put("kind", "t3").put("data", post)))
        .put(listing(null))
        .toString()

    private fun listing(child: JSONObject?) = JSONObject()
        .put("kind", "Listing")
        .put("data", JSONObject().put("children", JSONArray().apply { child?.let { put(it) } }).put("after", JSONObject.NULL))

    private fun recordingRetrofit(respond: (Request) -> Pair<Int, String>): Retrofit {
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

    private companion object {
        const val ACCOUNT = "someone"
        const val TOKEN = "token"
    }
}
