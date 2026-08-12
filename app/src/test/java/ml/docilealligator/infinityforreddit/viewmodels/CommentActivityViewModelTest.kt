package ml.docilealligator.infinityforreddit.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.account.Account
import ml.docilealligator.infinityforreddit.comment.Comment
import ml.docilealligator.infinityforreddit.comment.SendComment
import ml.docilealligator.infinityforreddit.repositories.CommentActivityRepository
import ml.docilealligator.infinityforreddit.TestInfinity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import java.util.concurrent.Executor

/**
 * [CommentActivityViewModel.sendComment] owns the send call so its result survives a configuration
 * change (CHUNKS deferred item 4). [SendComment.sendComment] is a static that does real network I/O,
 * so it is mocked and its listener driven directly; these pin that a success delivers the parsed
 * comment through [SendCommentResult] and a failure delivers the error message.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class CommentActivityViewModelTest {

    @get:Rule
    val instantRule = InstantTaskExecutorRule()

    private val inlineExecutor = Executor { it.run() }

    private fun newViewModel() = CommentActivityViewModel(
        mock<CommentActivityRepository>(), inlineExecutor, ApplicationProvider.getApplicationContext()
    )

    private fun send(vm: CommentActivityViewModel): SendCommentResult? {
        var result: SendCommentResult? = null
        vm.sendResult.observeForever { result = it }
        vm.sendComment(mock<Retrofit>(), mock<Account>(), "hello", "t3_post", 0, emptyList(), null)
        return result
    }

    @Test
    fun successDeliversTheComment() {
        val comment = mock<Comment>()
        Mockito.mockStatic(SendComment::class.java).use { mocked ->
            mocked.`when`<Any?> {
                SendComment.sendComment(
                    any(), any(), any(), any(), any(), anyInt(), any(), anyOrNull(), any(), any(), any()
                )
            }.thenAnswer { invocation ->
                (invocation.arguments.last() as SendComment.SendCommentListener).sendCommentSuccess(comment)
                null
            }

            val result = send(newViewModel())
            assertTrue(result is SendCommentResult.Success)
            assertSame(comment, (result as SendCommentResult.Success).comment)
        }
    }

    @Test
    fun failureDeliversTheMessage() {
        Mockito.mockStatic(SendComment::class.java).use { mocked ->
            mocked.`when`<Any?> {
                SendComment.sendComment(
                    any(), any(), any(), any(), any(), anyInt(), any(), anyOrNull(), any(), any(), any()
                )
            }.thenAnswer { invocation ->
                (invocation.arguments.last() as SendComment.SendCommentListener).sendCommentFailed("send failed")
                null
            }

            val result = send(newViewModel())
            assertEquals("send failed", (result as SendCommentResult.Failure).message)
        }
    }
}
