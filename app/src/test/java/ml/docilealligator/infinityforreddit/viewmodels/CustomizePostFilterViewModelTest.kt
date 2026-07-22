package ml.docilealligator.infinityforreddit.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.postfilter.PostFilter
import ml.docilealligator.infinityforreddit.postfilter.PostFilterDao
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * [CustomizePostFilterViewModel] owns the save-filter DB write so its result survives a
 * configuration change (CHUNKS deferred item 4); the Activity shows the duplicate-name dialog from
 * the observer. These pin the two terminal outcomes against a real (in-memory) Room DB: a save
 * under a free name succeeds, and renaming onto an existing name reports Duplicate (no overwrite).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class CustomizePostFilterViewModelTest {

    @get:Rule
    val instantRule = InstantTaskExecutorRule()

    private val inlineExecutor = Executor { it.run() }
    private lateinit var db: RedditDataRoomDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), RedditDataRoomDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        if (db.isOpen) db.close()
    }

    private fun filter(name: String) = PostFilter().apply { this.name = name }

    private fun save(postFilter: PostFilter, originalName: String): SavePostFilterResult {
        val vm = CustomizePostFilterViewModel(inlineExecutor, db)
        var result: SavePostFilterResult? = null
        vm.saveResult.observeForever { result = it }
        vm.savePostFilter(postFilter, originalName)
        awaitMainUntil { result != null }
        return result!!
    }

    @Test
    fun savingUnderAFreeNameReportsSuccess() {
        assertEquals(SavePostFilterResult.Success, save(filter("fresh"), "fresh"))
    }

    @Test
    fun renamingOntoAnExistingNameReportsDuplicate() {
        db.postFilterDao().insert(filter("existing"))
        assertEquals(SavePostFilterResult.Duplicate, save(filter("existing"), "original-name"))
    }

    @Test
    fun dbFailureDeliversFailureAndReleasesTheGuard() {
        // A Room DAO that throws, so SavePostFilter's DB work fails and must still report a terminal
        // outcome. originalName != name below ensures getPostFilter (the throwing call) is reached.
        val throwingDao = mock<PostFilterDao>()
        whenever(throwingDao.getPostFilter(any())).thenThrow(RuntimeException("boom"))
        val throwingDb = mock<RedditDataRoomDatabase>()
        whenever(throwingDb.postFilterDao()).thenReturn(throwingDao)

        val vm = CustomizePostFilterViewModel(inlineExecutor, throwingDb)
        var last: SavePostFilterResult? = null
        var resultCount = 0
        vm.saveResult.observeForever {
            last = it
            resultCount++
        }

        // The DB error must still produce a terminal outcome (not silently swallowed)...
        vm.savePostFilter(filter("x"), "orig")
        awaitMainUntil { resultCount >= 1 }
        assertTrue(last is SavePostFilterResult.Failure)

        // ...and the guard must be released, so a later save is not permanently blocked.
        vm.savePostFilter(filter("x"), "orig")
        awaitMainUntil { resultCount >= 2 }
        assertEquals(2, resultCount)
    }

    @Test
    fun secondSaveWhileFirstInFlightIsDropped() {
        val vm = CustomizePostFilterViewModel(inlineExecutor, db)
        var resultCount = 0
        vm.saveResult.observeForever { resultCount++ }

        // Two taps before the first save's posted result runs: the in-flight guard must drop the
        // second, so exactly one outcome is delivered (without the guard, both would fire).
        vm.savePostFilter(filter("fresh"), "fresh")
        vm.savePostFilter(filter("fresh"), "fresh")
        awaitMainUntil { resultCount >= 1 }

        assertEquals(1, resultCount)
    }
}
