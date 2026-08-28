package ml.docilealligator.infinityforreddit.viewmodels

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.comment.Comment
import ml.docilealligator.infinityforreddit.thing.SortType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * In-thread comment search matches a query against comment text case-insensitively. Both sides are
 * case-folded first, and the fold has to be locale-independent: on a Turkish or Azeri device an
 * uppercase `I` lower-cases to dotless `ı`, which never equals the `i` the query already had, so a
 * search for a word containing an I would find nothing at all (defect D1).
 *
 * The other three D1 sites are pinned by [ml.docilealligator.infinityforreddit.postfilter.PostFilterKeywordCaseTest],
 * [ml.docilealligator.infinityforreddit.commentfilter.CommentFilterKeywordCaseTest] and
 * `SavedThingSearchFilterTest`; this is the fourth.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class ViewPostDetailSearchLocaleTest {

    @get:Rule
    val instantRule = InstantTaskExecutorRule()

    private lateinit var defaultLocale: Locale
    private lateinit var db: RedditDataRoomDatabase

    @Before
    fun setUp() {
        // Captured first: a locale that escapes this class is contagious, one JVM runs the suite.
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), RedditDataRoomDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
        db.close()
    }

    private fun comment(rawText: String): Comment =
        Comment("t1_parent", 0, Comment.NOT_PLACEHOLDER).apply { setCommentRawText(rawText) }

    private fun viewModelOver(vararg rawTexts: String): ViewPostDetailFragmentViewModelNew {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("search_locale_test", Context.MODE_PRIVATE)
        return ViewPostDetailFragmentViewModelNew(
            retrofitRespondingWith(200, "{}"),
            retrofitRespondingWith(200, "{}"),
            retrofitRespondingWith(200, "{}"),
            db,
            null,
            "-",
            null,
            "postid",
            null,
            ArrayList(rawTexts.map { comment(it) }),
            null,
            SortType.Type.BEST,
            prefs,
            prefs,
            false,
            false,
            SortType.Type.BEST,
            false,
            false,
            "3"
        )
    }

    @Test
    fun `searching forward finds a comment that capitalises the query under a locale with its own case rules`() {
        val viewModel = viewModelOver("nothing to see", "This is INTERESTING", "trailing")

        assertEquals(1, viewModel.getNextSearchedPosition("interesting", -1, true))
    }

    @Test
    fun `searching backward finds a comment that capitalises the query under a locale with its own case rules`() {
        val viewModel = viewModelOver("nothing to see", "This is INTERESTING", "trailing")

        assertEquals(1, viewModel.getNextSearchedPosition("interesting", 2, false))
    }

    @Test
    fun `a query that capitalises the comment's own wording matches too`() {
        val viewModel = viewModelOver("nothing to see", "an interesting reply")

        assertEquals(1, viewModel.getNextSearchedPosition("INTERESTING", -1, true))
    }
}
