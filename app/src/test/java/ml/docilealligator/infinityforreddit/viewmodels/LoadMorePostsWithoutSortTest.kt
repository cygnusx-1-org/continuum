package ml.docilealligator.infinityforreddit.viewmodels

import android.os.Handler
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.post.PostType
import ml.docilealligator.infinityforreddit.readpost.ReadPostType
import ml.docilealligator.infinityforreddit.user.UserProfileImagesBatchLoader
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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
}
