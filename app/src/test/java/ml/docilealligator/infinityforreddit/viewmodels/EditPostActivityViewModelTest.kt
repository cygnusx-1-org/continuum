package ml.docilealligator.infinityforreddit.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import ml.docilealligator.infinityforreddit.TestInfinity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [EditPostActivityViewModel] owns the edit-post call so its result survives a configuration change
 * (CHUNKS deferred item 4). These pin the routing the Activity relies on: a clean 200 is a success,
 * an api_type=json error inside a 200 body is a failure carrying the Reddit message (item 5), and an
 * HTTP error is a generic failure — none of which may reach the wrong terminal state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class EditPostActivityViewModelTest {

    @get:Rule
    val instantRule = InstantTaskExecutorRule()

    private fun edit(code: Int, body: String): EditPostResult {
        val vm = EditPostActivityViewModel(retrofitRespondingWith(code, body))
        var result: EditPostResult? = null
        vm.editResult.observeForever { result = it }
        vm.editPost("token", "t3_post", "new text")
        awaitMainUntil { result != null }
        return result!!
    }

    @Test
    fun cleanTwoHundredReportsSuccess() {
        assertEquals(EditPostResult.Success, edit(200, "{}"))
    }

    @Test
    fun apiErrorInsideTwoHundredReportsFailureWithMessage() {
        val result = edit(200, """{"json":{"errors":[["ARCHIVED","that comment is archived","reason"]]}}""")
        assertEquals("That comment is archived", (result as EditPostResult.Failure).message)
    }

    @Test
    fun httpErrorReportsGenericFailure() {
        val result = edit(403, "{}")
        assertNull((result as EditPostResult.Failure).message)
    }

    @Test
    fun isSubmittingClearsAfterCompletion() {
        val vm = EditPostActivityViewModel(retrofitRespondingWith(200, "{}"))
        var result: EditPostResult? = null
        vm.editResult.observeForever { result = it }
        vm.editPost("token", "t3_post", "new text")
        awaitMainUntil { result != null }
        assertFalse("isSubmitting must clear once the edit finished", vm.isSubmitting.value == true)
    }
}
