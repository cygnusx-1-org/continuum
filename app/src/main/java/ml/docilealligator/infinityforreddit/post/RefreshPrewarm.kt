package ml.docilealligator.infinityforreddit.post

import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import com.google.common.util.concurrent.AsyncFunction
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Callable
import java.util.concurrent.Executor

/**
 * Gets a refreshed page ready to draw before Paging hands it to the feed.
 *
 * Called on the paging executor with the page's items. The future it returns is the moment the page
 * may be shown, so it has to complete on its own within a bounded time: the feed's refresh spinner
 * stays up until it does. A future that fails or is cancelled counts as done.
 */
fun interface RefreshPrewarmer<T : Any> {
    fun prewarm(items: List<T>): ListenableFuture<*>
}

object RefreshPrewarm {
    /**
     * [load], held back until [prewarmer] has had the page it produced.
     *
     * Only a refresh waits. That is the load that replaces what is on screen -- the first screen of a
     * feed, or the old list under a pull-to-refresh -- and holding it while Paging still reports it
     * as loading swaps the list in whole, where hiding the list at the swap would flash it blank. An
     * append lands below the viewport, where the feed's scroll preloader already has it covered, so
     * delaying one would only hold up the rows. An error or an empty page has nothing to warm.
     *
     * Nothing a prewarmer does can lose the page: a prewarmer that throws, fails or is cancelled
     * still lets the page through.
     */
    @JvmStatic
    fun <K : Any, T : Any> wrap(
        params: LoadParams<K>,
        load: ListenableFuture<LoadResult<K, T>>,
        prewarmer: RefreshPrewarmer<T>?,
        executor: Executor,
    ): ListenableFuture<LoadResult<K, T>> {
        if (prewarmer == null || params !is LoadParams.Refresh) {
            return load
        }
        return Futures.transformAsync(load, AsyncFunction<LoadResult<K, T>, LoadResult<K, T>> { result ->
            if (result !is LoadResult.Page || result.data.isEmpty()) {
                return@AsyncFunction Futures.immediateFuture(result)
            }
            val warmed: ListenableFuture<*> = try {
                prewarmer.prewarm(result.data)
            } catch (e: RuntimeException) {
                Futures.immediateFuture(Unit)
            }
            Futures.whenAllComplete(warmed)
                .call(Callable<LoadResult<K, T>> { result }, MoreExecutors.directExecutor())
        }, executor)
    }
}
