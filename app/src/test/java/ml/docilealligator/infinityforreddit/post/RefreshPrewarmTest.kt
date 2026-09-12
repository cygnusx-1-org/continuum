package ml.docilealligator.infinityforreddit.post

import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A refresh is held until its page has been warmed, and nothing else is: an append, an error or an
 * empty page goes straight through. Whatever the prewarmer does, the page itself is never lost.
 */
class RefreshPrewarmTest {

    private val direct = MoreExecutors.directExecutor()
    private val refresh = LoadParams.Refresh<String>(null, 10, false)
    private val append = LoadParams.Append("t3_after", 100, false)
    private val page: LoadResult<String, String> = LoadResult.Page(listOf("a", "b"), null, "t3_after")

    @Test
    fun refreshWaitsForThePrewarmAndThenDeliversThePage() {
        val warm = SettableFuture.create<Unit>()
        var seen: List<String>? = null
        val result = RefreshPrewarm.wrap(refresh, Futures.immediateFuture(page),
            RefreshPrewarmer<String> { items -> seen = items; warm }, direct)

        assertEquals(listOf("a", "b"), seen)
        assertFalse(result.isDone)
        warm.set(Unit)
        assertTrue(result.isDone)
        assertSame(page, result.get())
    }

    @Test
    fun appendIsNeitherWarmedNorHeld() {
        var called = false
        val result = RefreshPrewarm.wrap(append, Futures.immediateFuture(page),
            RefreshPrewarmer<String> { called = true; SettableFuture.create<Unit>() }, direct)

        assertFalse(called)
        assertTrue(result.isDone)
        assertSame(page, result.get())
    }

    @Test
    fun errorPassesThroughWithoutAPrewarm() {
        var called = false
        val error: LoadResult<String, String> = LoadResult.Error(RuntimeException("offline"))
        val result = RefreshPrewarm.wrap(refresh, Futures.immediateFuture(error),
            RefreshPrewarmer<String> { called = true; SettableFuture.create<Unit>() }, direct)

        assertFalse(called)
        assertSame(error, result.get())
    }

    @Test
    fun emptyPageIsNotHeld() {
        var called = false
        val empty: LoadResult<String, String> = LoadResult.Page(emptyList(), null, null)
        val result = RefreshPrewarm.wrap(refresh, Futures.immediateFuture(empty),
            RefreshPrewarmer<String> { called = true; SettableFuture.create<Unit>() }, direct)

        assertFalse(called)
        assertSame(empty, result.get())
    }

    @Test
    fun failedPrewarmStillDeliversThePage() {
        val warm = SettableFuture.create<Unit>()
        val result = RefreshPrewarm.wrap(refresh, Futures.immediateFuture(page),
            RefreshPrewarmer<String> { warm }, direct)

        warm.setException(IllegalStateException("decode failed"))
        assertSame(page, result.get())
    }

    @Test
    fun cancelledPrewarmStillDeliversThePage() {
        val warm = SettableFuture.create<Unit>()
        val result = RefreshPrewarm.wrap(refresh, Futures.immediateFuture(page),
            RefreshPrewarmer<String> { warm }, direct)

        warm.cancel(false)
        assertSame(page, result.get())
    }

    @Test
    fun throwingPrewarmerStillDeliversThePage() {
        val result = RefreshPrewarm.wrap(refresh, Futures.immediateFuture(page),
            RefreshPrewarmer<String> { throw IllegalStateException("no view") }, direct)

        assertSame(page, result.get())
    }

    @Test
    fun withoutAPrewarmerTheLoadIsReturnedUntouched() {
        val load: ListenableFuture<LoadResult<String, String>> = Futures.immediateFuture(page)
        val prewarmer: RefreshPrewarmer<String>? = null

        assertSame(load, RefreshPrewarm.wrap(refresh, load, prewarmer, direct))
    }

    @Test
    fun aKeyedRefreshIsStillHeld() {
        // It is the load type that counts, not a null key.
        val warm = SettableFuture.create<Unit>()
        val keyedRefresh = LoadParams.Refresh("t3_anchor", 10, false)
        val result = RefreshPrewarm.wrap(keyedRefresh, Futures.immediateFuture(page),
            RefreshPrewarmer<String> { warm }, direct)

        assertFalse(result.isDone)
        warm.set(Unit)
        assertSame(page, result.get())
    }
}
