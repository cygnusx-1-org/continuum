package ml.docilealligator.infinityforreddit.customviews

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Empty space above the first item, so a one-column post feed starts far enough down the screen to
 * be swiped comfortably with one thumb.
 *
 * A decoration rather than top padding on the RecyclerView: the post feed rewrites its padding on
 * every window-inset pass, which would wipe a top padding out again. It offsets the first row and
 * nothing else -- every other row keeps the bounds it had.
 */
class TopBufferItemDecoration(private val bufferPx: Int) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        outRect.top = if (parent.getChildAdapterPosition(view) == 0) bufferPx else 0
    }
}
