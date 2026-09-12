package ml.docilealligator.infinityforreddit.adapters

import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.Executor
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import ml.docilealligator.infinityforreddit.post.Post
import ml.docilealligator.infinityforreddit.post.RefreshPrewarmer

/**
 * Decodes compact thumbnails into Glide's memory cache before their rows bind, so a row arrives with
 * its picture already in the box rather than a spinner that the picture then replaces.
 *
 * It comes at that from two sides. While the list scrolls, the rows just past the edge it is moving
 * towards are warmed ([PreloadWindow]). And a refresh is held until the screen it opens on has been
 * warmed ([prewarm]), so a feed opens, or a pull-to-refresh swaps, with every thumbnail in place.
 *
 * Every request comes from [PostRecyclerViewAdapter.compactThumbnailPreloadRequest], the builder the
 * row's own load uses. That is the whole trick: Glide's memory cache is keyed on the url, the size and
 * the transformation, so a bitmap warmed any other way would sit unused while the row decoded its own.
 *
 * Main thread only, like the adapter it reads.
 */
class CompactThumbnailPreloader(
    private val recyclerView: RecyclerView,
    private val adapterProvider: () -> PostRecyclerViewAdapter?,
    private val glide: RequestManager,
) : RecyclerView.OnScrollListener(), RefreshPrewarmer<Post> {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { mainHandler.post(it) }

    /**
     * Scroll preloads for the rows in the current window, by post fullname, so the ones the list has
     * moved away from can be cancelled. A preload that has finished has already handed its bitmap to
     * the memory cache; clearing it then costs nothing.
     */
    private val targets = HashMap<String, Target<Drawable>>()

    // What the window was last built from. onScrolled arrives on every frame of a scroll, and the
    // window only moves when a row crosses the edge of the viewport.
    private var windowFirst = RecyclerView.NO_POSITION
    private var windowLast = RecyclerView.NO_POSITION
    private var windowScrollingUp = false
    private var windowItemCount = -1
    private var scrollingUp = false
    private var observedAdapter: PostRecyclerViewAdapter? = null
    private val pendingPrewarms = ArrayList<SettableFuture<Unit>>()
    private var anchorFullName: String? = null
    private var anchorFallbackPosition = RecyclerView.NO_POSITION
    private var released = false

    // Held so the very same instance can be removed again. New pages can put different posts at the
    // same positions -- a refresh, a filter change -- so the window is rebuilt even when its range
    // has not moved.
    private val onPagesUpdated: () -> Unit = {
        windowItemCount = -1
        updateWindow()
    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        if (dy != 0) {
            scrollingUp = dy < 0
        }
        // Also called with no movement when a layout changes which rows are on screen.
        updateWindow()
    }

    /**
     * Where a pending resume will land, so the refresh serving it warms that screen instead of the top
     * of the feed, which the resume jumps straight past. [fallbackPosition] is used when the post is
     * not in the page, the same fallback the restore itself takes.
     */
    fun setAnchorHint(fullName: String?, fallbackPosition: Int) {
        anchorFullName = fullName
        anchorFallbackPosition = fallbackPosition
    }

    fun clearAnchorHint() {
        setAnchorHint(null, RecyclerView.NO_POSITION)
    }

    /**
     * Forget what has been warmed and warm the window again. For when the rows are rebuilt, which is
     * how a change to a setting that shapes the request -- the thumbnail size, the blur -- reaches the
     * list; the preloads already held were built under the old one.
     */
    fun reset() {
        clearTargets()
        windowItemCount = -1
        recyclerView.post { updateWindow() }
    }

    /** Stop everything, for when the view goes. A refresh still held for its prewarm is let through. */
    fun release() {
        released = true
        observedAdapter?.removeOnPagesUpdatedListener(onPagesUpdated)
        observedAdapter = null
        clearTargets()
        for (pending in ArrayList(pendingPrewarms)) {
            pending.set(Unit)
        }
        pendingPrewarms.clear()
    }

    private fun updateWindow() {
        if (released) {
            return
        }
        val adapter = adapterProvider() ?: return
        if (adapter !== observedAdapter) {
            observedAdapter?.removeOnPagesUpdatedListener(onPagesUpdated)
            // A page that lands while the list is at rest fills in rows beyond the viewport, with no
            // scroll to say so.
            adapter.addOnPagesUpdatedListener(onPagesUpdated)
            observedAdapter = adapter
            clearTargets()
            windowItemCount = -1
        }

        val itemCount = adapter.itemCount
        val visible = visiblePositions()
        val first = visible?.first ?: RecyclerView.NO_POSITION
        val last = visible?.second ?: RecyclerView.NO_POSITION
        if (first == windowFirst && last == windowLast && scrollingUp == windowScrollingUp
            && itemCount == windowItemCount
        ) {
            return
        }
        windowFirst = first
        windowLast = last
        windowScrollingUp = scrollingUp
        windowItemCount = itemCount
        val positions = if (visible == null) emptyList() else PreloadWindow.positions(first, last, itemCount, scrollingUp)

        val box = adapter.compactThumbnailBoxSizePx
        val wanted = HashSet<String>()
        for (position in positions) {
            // peek, not getItem: getItem tells Paging the row is being looked at, and would have it
            // fetch pages for rows that are only being warmed.
            val post = adapter.peek(position) ?: continue
            val key = post.fullName
            wanted.add(key)
            if (targets.containsKey(key)) {
                continue
            }
            val request = adapter.compactThumbnailPreloadRequest(post) ?: continue
            targets[key] = request.preload(box, box)
        }

        val iterator = targets.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in wanted) {
                glide.clear(entry.value)
                iterator.remove()
            }
        }
    }

    /**
     * Called on the paging executor with a refreshed page. Warms the screen the page will open on, and
     * completes once every thumbnail on it has arrived or failed, or [REFRESH_HOLD_CAP_MS] has passed,
     * whichever is first -- a slow image delays the feed by that much at most.
     */
    override fun prewarm(items: List<Post>): ListenableFuture<*> {
        val done = SettableFuture.create<Unit>()
        mainHandler.post { startPrewarm(items, done) }
        return done
    }

    private fun startPrewarm(posts: List<Post>, done: SettableFuture<Unit>) {
        val adapter = adapterProvider()
        if (released || adapter == null || done.isDone) {
            done.set(Unit)
            return
        }

        val box = adapter.compactThumbnailBoxSizePx
        val start = anchorIndex(posts)?.let { max(0, it - ANCHOR_ROWS_ABOVE) } ?: 0
        val end = min(posts.size, start + firstScreenPostCount(box))
        // A card layout, or data saving with previews off, has nothing here, and so no wait.
        val requests = (start until end).mapNotNull { adapter.compactThumbnailPreloadRequest(posts[it]) }
        if (requests.isEmpty()) {
            done.set(Unit)
            return
        }

        pendingPrewarms.add(done)
        done.addListener({ pendingPrewarms.remove(done) }, mainExecutor)
        var remaining = requests.size
        val listener = object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean
            ): Boolean {
                if (--remaining == 0) {
                    done.set(Unit)
                }
                return false
            }

            override fun onResourceReady(
                resource: Drawable, model: Any, target: Target<Drawable>,
                dataSource: DataSource, isFirstResource: Boolean
            ): Boolean {
                if (--remaining == 0) {
                    done.set(Unit)
                }
                return false
            }
        }
        mainHandler.postDelayed({ done.set(Unit) }, REFRESH_HOLD_CAP_MS)
        for (request in requests) {
            // A thumbnail already in memory reports inside preload(), before the loop moves on.
            request.addListener(listener).preload(box, box)
        }
    }

    private fun anchorIndex(posts: List<Post>): Int? {
        val name = anchorFullName
        if (name != null) {
            val index = posts.indexOfFirst { it.fullName == name }
            if (index >= 0) {
                return index
            }
        }
        return anchorFallbackPosition.takeIf { it in posts.indices }
    }

    /**
     * An upper bound on how many posts the first screen can show. Every compact row is at least its box
     * plus the 8dp above and below it, so dividing by that overcounts rather than under; the allowance
     * on top covers read posts and non-media posts the feed filters out after the page is loaded.
     */
    private fun firstScreenPostCount(box: Int): Int {
        val metrics = recyclerView.resources.displayMetrics
        val height = recyclerView.height.takeIf { it > 0 } ?: metrics.heightPixels
        val shortestRow = box + (16 * metrics.density).toInt()
        val columns = (recyclerView.layoutManager as? StaggeredGridLayoutManager)?.spanCount ?: 1
        val rows = ceil(height.toDouble() / shortestRow).toInt()
        return min(PREWARM_MAX_POSTS, ceil(rows * columns * PREWARM_FILTER_ALLOWANCE).toInt())
    }

    /** First and last visible adapter positions, across every column of a staggered grid. */
    private fun visiblePositions(): Pair<Int, Int>? {
        val range = when (val layoutManager = recyclerView.layoutManager) {
            is LinearLayoutManager ->
                layoutManager.findFirstVisibleItemPosition() to layoutManager.findLastVisibleItemPosition()
            is StaggeredGridLayoutManager -> {
                val first = layoutManager.findFirstVisibleItemPositions(IntArray(layoutManager.spanCount))
                    .filter { it != RecyclerView.NO_POSITION }.minOrNull() ?: return null
                val last = layoutManager.findLastVisibleItemPositions(IntArray(layoutManager.spanCount))
                    .filter { it != RecyclerView.NO_POSITION }.maxOrNull() ?: return null
                first to last
            }
            else -> return null
        }
        return range.takeIf { it.first != RecyclerView.NO_POSITION && it.second != RecyclerView.NO_POSITION }
    }

    private fun clearTargets() {
        for (target in targets.values) {
            glide.clear(target)
        }
        targets.clear()
    }

    private companion object {
        const val REFRESH_HOLD_CAP_MS = 1000L
        const val PREWARM_MAX_POSTS = 24
        const val PREWARM_FILTER_ALLOWANCE = 1.5
        // A resumed anchor can sit a little below the top edge, with part of the row above it showing.
        const val ANCHOR_ROWS_ABOVE = 1
    }
}

/**
 * Which rows to warm, nearest first: a run past the edge of the viewport the list is moving towards,
 * and a short one behind for a change of direction. The rows on screen are left out; they are bound,
 * and their own loads are already under way.
 */
internal object PreloadWindow {
    /** However few rows fit on screen, at least this many are warmed ahead. */
    const val MIN_AHEAD = 12

    /**
     * However many fit, no more than this. A tablet's staggered grid can show thirty rows at once, and
     * two screens of those would push the thumbnails the user is about to reach out of the cache.
     */
    const val MAX_AHEAD = 40
    const val SCREENS_AHEAD = 2
    const val BEHIND = 4

    /**
     * [lastVisible] may be past the posts -- the load-state footer sits after them in the same list --
     * so it is clamped to the last post.
     */
    fun positions(firstVisible: Int, lastVisible: Int, itemCount: Int, scrollingUp: Boolean): List<Int> {
        if (itemCount <= 0 || firstVisible < 0 || lastVisible < 0) {
            return emptyList()
        }
        val last = min(lastVisible, itemCount - 1)
        val first = min(firstVisible, last)
        val ahead = (SCREENS_AHEAD * (last - first + 1)).coerceIn(MIN_AHEAD, MAX_AHEAD)
        val belowCount = if (scrollingUp) BEHIND else ahead
        val aboveCount = if (scrollingUp) ahead else BEHIND
        val below = (last + 1..min(itemCount - 1, last + belowCount)).toList()
        val above = (first - 1 downTo max(0, first - aboveCount)).toList()
        return if (scrollingUp) above + below else below + above
    }
}
