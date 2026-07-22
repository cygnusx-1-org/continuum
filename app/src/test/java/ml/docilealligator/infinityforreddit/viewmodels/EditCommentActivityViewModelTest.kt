package ml.docilealligator.infinityforreddit.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.repositories.EditCommentActivityRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * [EditCommentActivityViewModel.editComment] owns the edit call so its result survives a
 * configuration change (CHUNKS deferred item 4), and parses the api_type=json envelope (item 5)
 * rather than reporting a false success. These pin: a success envelope surfaces Success echoing the
 * edited text, an error body surfaces the Reddit message, and an HTTP error is a generic failure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class EditCommentActivityViewModelTest {

    @get:Rule
    val instantRule = InstantTaskExecutorRule()

    private val inlineExecutor = Executor { it.run() }
    private lateinit var db: RedditDataRoomDatabase
    private lateinit var repository: EditCommentActivityRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), RedditDataRoomDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = EditCommentActivityRepository(db.commentDraftDao())
    }

    @After
    fun tearDown() = db.close()

    private fun edit(code: Int, body: String): EditCommentResult {
        val vm = EditCommentActivityViewModel(repository, inlineExecutor, retrofitRespondingWith(code, body))
        var result: EditCommentResult? = null
        vm.editResult.observeForever { result = it }
        vm.editComment("token", mapOf("thing_id" to "t1_comment", "text" to "edited-body"), "edited-body")
        awaitMainUntil { result != null }
        return result!!
    }

    @Test
    fun successEnvelopeReportsSuccessWithEditedContent() {
        val result = edit(200, """{"json":{"errors":[],"data":{"things":[{"kind":"t1","data":{"id":"abc"}}]}}}""")
        assertEquals("edited-body", (result as EditCommentResult.Success).editedContent)
    }

    @Test
    fun apiErrorReportsFailureWithMessage() {
        val result = edit(200, """{"json":{"errors":[["ARCHIVED","that comment is archived","reason"]]}}""")
        assertEquals("That comment is archived", (result as EditCommentResult.Failure).message)
    }

    @Test
    fun httpErrorReportsGenericFailure() {
        val result = edit(403, "{}")
        assertNull((result as EditCommentResult.Failure).message)
    }
}
