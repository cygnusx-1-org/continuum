package ml.docilealligator.infinityforreddit.viewmodels

import android.os.Handler
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.account.Account
import ml.docilealligator.infinityforreddit.post.Post
import ml.docilealligator.infinityforreddit.post.PostType
import ml.docilealligator.infinityforreddit.readpost.ReadPost
import ml.docilealligator.infinityforreddit.readpost.ReadPostType
import ml.docilealligator.infinityforreddit.user.UserProfileImagesBatchLoader
import ml.docilealligator.infinityforreddit.utils.APIUtils
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * The read-posts ("History") listing is the only one with no sort of its own: HistoryPostFragment
 * sends `sortType = null`, and the branch that loads it never asks for one. Declaring that parameter
 * non-null crashed the app on entry — Kotlin's own parameter check, thrown before a line of the body
 * ran — as soon as a post opened from History was swiped to within five of the end of the loaded
 * list and `onPageSelected` reached for more.
 *
 * Returning from the call at all is the assertion here, because the crash happened before the
 * coroutine the body launches was ever scheduled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class LoadMorePostsWithoutSortTest {

    private lateinit var db: RedditDataRoomDatabase

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
    fun `loading more read posts does not need a sort type`() {
        val retrofit = retrofitRespondingWith(200, "{}")
        val viewModel = ViewPostDetailActivityViewModel(
            retrofit,
            retrofit,
            db,
            null,
            UserProfileImagesBatchLoader(
                Executor { it.run() }, Handler(Looper.getMainLooper()), db, retrofit, retrofit
            )
        )

        viewModel.fetchMorePosts(
            null, "-", false, PostType.READ_POSTS,
            null, null, null, null, null, null,
            null, null, null,
            ReadPostType.READ_POSTS, null, false
        )
    }

    @Test
    fun `a logged-in account loads more read posts through its own OAuth client`() {
        val (plainRequests, oauthRequests) = fetchMoreReadPosts("someone", "token")

        assertEquals(0, plainRequests.size)
        val request = oauthRequests.single()
        assertEquals("/api/info.json", request.url.encodedPath)
        assertEquals("t3_older", request.url.queryParameter("id"))
        assertNotNull(request.header(APIUtils.AUTHORIZATION_KEY))
    }

    @Test
    fun `an anonymous account loads more read posts without an account's credentials`() {
        val (plainRequests, oauthRequests) = fetchMoreReadPosts(Account.ANONYMOUS_ACCOUNT, null)

        assertEquals(0, oauthRequests.size)
        val request = plainRequests.single()
        assertEquals("/api/info.json", request.url.encodedPath)
        assertEquals("t3_older", request.url.queryParameter("id"))
        assertNull(request.header(APIUtils.AUTHORIZATION_KEY))
    }

    private fun fetchMoreReadPosts(accountName: String, accessToken: String?): Pair<List<Request>, List<Request>> {
        val plainRequests = CopyOnWriteArrayList<Request>()
        val oauthRequests = CopyOnWriteArrayList<Request>()
        val plain = recordingRetrofit(plainRequests)
        val oauth = recordingRetrofit(oauthRequests)
        val viewModel = ViewPostDetailActivityViewModel(
            plain,
            oauth,
            db,
            accessToken,
            UserProfileImagesBatchLoader(
                Executor { it.run() }, Handler(Looper.getMainLooper()), db, plain, oauth
            )
        )
        // A page with nothing on it sends no request for the anonymous account (AnonymousPostLookup),
        // so the list holds one post with an older one recorded behind it.
        db.accountDao().insert(Account(accountName, null, null, null, null, null, 0, false, false))
        for ((postId, time) in listOf("shown" to 2_000L, "older" to 1_000L)) {
            db.readPostDao().insert(ReadPost(accountName, postId, ReadPostType.READ_POSTS).apply { setTime(time) })
        }
        viewModel.posts = arrayListOf(post("shown"))

        viewModel.fetchMorePosts(
            accessToken, accountName, false, PostType.READ_POSTS,
            null, null, null, null, null, null,
            null, null, null,
            ReadPostType.READ_POSTS, null, false
        )
        awaitMainUntil { plainRequests.isNotEmpty() || oauthRequests.isNotEmpty() }
        return plainRequests to oauthRequests
    }

    private fun post(id: String) = Post(
        id, "t3_$id", "test", "r/test", "author", "t2_author", "", "", 0L,
        "a post", "/r/test/comments/$id/", 0, Post.TEXT_TYPE, 0, 0, 100, "",
        false, false, false, false, false, false, false, false,
        false, false, false, 0L, null, false, false, "", null
    )

    private fun recordingRetrofit(requests: MutableList<Request>): Retrofit {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requests.add(chain.request())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("")
                    .body("{}".toResponseBody("application/json".toMediaType()))
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
