package ml.docilealligator.infinityforreddit.postfilter

import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * Records which subreddits each wildcard "Exclude subreddits" rule actually hid.
 *
 * The set a broad term like `*irl*` will match cannot be predicted when the term is written — it
 * depends on subreddit names nobody has an index of, including ones that do not exist yet. It is
 * however perfectly observable afterwards, on the user's own feed, which is what this records so the
 * Post Filter screen can show a rule's real damage next to the rule that caused it.
 *
 * [PostFilter.isPostAllowed] is static, runs on the paging worker thread, and is called once per
 * parsed post, so [record] only touches an in-memory map. Rows are written in batches after a quiet
 * period, on the app's own executor.
 */
object PostFilterBlockRecorder {

    /**
     * How long to wait after the last block before writing. Long enough that scrolling a page of
     * posts produces one batch, short enough that a user who filters and then immediately opens the
     * Post Filter screen sees current numbers.
     */
    private const val FLUSH_DELAY_MILLIS = 3000L

    private val pending = ConcurrentHashMap<String, PendingBlock>()

    // Written once from Application.onCreate before any feed can load, read from the paging thread.
    @Volatile
    private var writer: Writer? = null

    @Volatile
    private var flushScheduled = false

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Persists a batch of blocks. Implemented over the DAO by the installer so this class needs no
     * Room types and stays unit-testable.
     */
    fun interface Writer {
        fun write(blocks: Collection<PendingBlock>)
    }

    /** One (rule, subreddit) pair and how many posts it has hidden since the last write. */
    data class PendingBlock(
        val filterName: String,
        val ruleValue: String,
        val subredditName: String,
        val count: Int,
    )

    /**
     * Wires the recorder to storage. Called once from the application; until then [record] is a
     * no-op, so unit tests exercising [PostFilter.isPostAllowed] need no setup.
     */
    @JvmStatic
    fun install(executor: Executor, writer: Writer) {
        this.writer = Writer { blocks ->
            executor.execute {
                try {
                    writer.write(blocks)
                } catch (e: Exception) {
                    // A batch can outlive the filter that produced it by up to FLUSH_DELAY_MILLIS,
                    // and these rows have a foreign key onto that filter: rename or delete a filter
                    // just after one of its rules hid something and the insert fails the key. These
                    // are observations the user can regenerate by browsing, so losing a batch is the
                    // right price -- an uncaught throw here would take the process down.
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Notes that [subredditName] was hidden by [ruleValue] of the filter named [filterName]. Cheap
     * enough to call per post: one map lookup and, at most, one scheduled callback.
     */
    @JvmStatic
    fun record(filterName: String, ruleValue: String, subredditName: String) {
        if (writer == null) {
            return
        }
        val key = PostFilter.exceptionKey(filterName, ruleValue, subredditName)
        pending.compute(key) { _, existing ->
            existing?.copy(count = existing.count + 1)
                ?: PendingBlock(filterName, ruleValue, subredditName, 1)
        }
        scheduleFlush()
    }

    /**
     * Writes anything pending now instead of waiting out the delay. For a screen that is about to
     * display the counts.
     */
    @JvmStatic
    fun flushNow() {
        handler.removeCallbacks(flushRunnable)
        flushScheduled = false
        flush()
    }

    /** Drops everything pending without writing it. Tests only. */
    @JvmStatic
    fun clearForTesting() {
        handler.removeCallbacks(flushRunnable)
        flushScheduled = false
        pending.clear()
        writer = null
    }

    private fun scheduleFlush() {
        if (flushScheduled) {
            return
        }
        flushScheduled = true
        handler.postDelayed(flushRunnable, FLUSH_DELAY_MILLIS)
    }

    private val flushRunnable = Runnable {
        flushScheduled = false
        flush()
    }

    private fun flush() {
        val target = writer ?: return
        if (pending.isEmpty()) {
            return
        }
        // Take the whole batch by removing each key, so blocks recorded while the write is in
        // flight accumulate into the next batch rather than being lost or double-counted.
        val batch = ArrayList<PendingBlock>(pending.size)
        for (key in pending.keys.toList()) {
            pending.remove(key)?.let { batch.add(it) }
        }
        if (batch.isNotEmpty()) {
            target.write(batch)
        }
    }

    /** Lower-cased name as stored, so display and exception lookups agree on casing. */
    @JvmStatic
    fun normalizeSubredditName(subredditName: String): String =
        subredditName.lowercase(Locale.ENGLISH)
}
