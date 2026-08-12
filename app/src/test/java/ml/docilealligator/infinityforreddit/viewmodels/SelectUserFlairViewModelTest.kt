package ml.docilealligator.infinityforreddit.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.user.UserFlair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * [SelectUserFlairViewModel] owns the select/clear-flair request so its result survives a
 * configuration change (CHUNKS deferred item 4) — previously the callback fired on the dead
 * instance and the live screen stayed on the list showing nothing. These pin that a clean response
 * reports success (with the cleared flag) and a `json.errors` body reports the Reddit message.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class SelectUserFlairViewModelTest {

    @get:Rule
    val instantRule = InstantTaskExecutorRule()

    private val inlineExecutor = Executor { it.run() }

    private fun select(code: Int, body: String, userFlair: UserFlair?): SelectFlairResult {
        val vm = SelectUserFlairViewModel(inlineExecutor, retrofitRespondingWith(code, body))
        var result: SelectFlairResult? = null
        vm.selectResult.observeForever { result = it }
        vm.selectUserFlair("token", userFlair, "test", "account")
        awaitMainUntil { result != null }
        return result!!
    }

    @Test
    fun clearingFlairReportsClearedSuccess() {
        val result = select(200, """{"json":{"errors":[]}}""", null)
        assertTrue("a null flair is a clear", (result as SelectFlairResult.Success).cleared)
    }

    @Test
    fun apiErrorReportsFailureWithMessage() {
        val result = select(200, """{"json":{"errors":[["BAD_FLAIR_TEXT","bad flair text","reason"]]}}""", null)
        assertEquals("Bad flair text", (result as SelectFlairResult.Failure).message)
    }

    @Test
    fun httpErrorReportsFailure() {
        assertTrue(select(403, "", null) is SelectFlairResult.Failure)
    }
}
