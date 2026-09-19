package ml.docilealligator.infinityforreddit.customviews

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import ml.docilealligator.infinityforreddit.utils.SwipeActionLevels
import ml.docilealligator.infinityforreddit.utils.Utils
import kotlin.math.abs

/**
 * What a multi-level swipe looks and feels like: the band behind the row, the icon trailing its
 * moving edge, and one haptic pulse per band boundary.
 *
 * The three swipe callbacks -- post feed, post comments, profile comments -- used to carry a
 * copy of this each, and the copies had already drifted. They share this one instead; the only
 * thing that differs between them is which action list [comments] picks.
 */
class SwipeActionPainter(
    private val context: Context,
    private val customThemeWrapper: CustomThemeWrapper,
    private val comments: Boolean,
) {

    val levels = SwipeActionLevels()

    /** The `vibrate_when_action_triggered` setting, which also overrides system haptics. */
    var vibrateWhenActionTriggered = true

    private val horizontalOffset = Utils.convertDpToPixel(16f, context).toInt()
    private val backgrounds = SparseArray<ColorDrawable>()
    private val icons = SparseArray<Drawable>()

    /** Latches the band under the finger, pulsing once each time it changes. Drag frames only. */
    fun onDrag(itemView: View, dX: Float) {
        val level = levels.levelFor(dX, itemView.right - itemView.left)
        if (levels.arm(level, dX) && vibrateWhenActionTriggered) {
            itemView.isHapticFeedbackEnabled = true
            itemView.performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
            )
        }
    }

    /**
     * Draws the band and its icon for the current drag, and answers how far the row itself may be
     * translated. Only the translation and the band's colour ever differ between levels: the row
     * keeps its own geometry at rest and at every level.
     */
    fun draw(canvas: Canvas, itemView: View, dX: Float): Float {
        if (dX == 0f) {
            // A cancelled swipe leaves nothing armed.
            levels.reset()
            return 0f
        }

        val rowWidth = itemView.right - itemView.left
        val clamped = levels.clamp(dX, rowWidth)
        val level = levels.levelFor(dX, rowWidth)
        // Below the first band the icon is already on its way in, so it shows what level 1 would do.
        val action = levels.actionFor(dX, if (level < 1) 1 else level)
        if (action == SwipeActionLevels.NONE) return clamped

        val background = background(action)
        val icon = icon(action)
        if (level >= 1) {
            background.setBounds(0, itemView.top, itemView.right, itemView.bottom)
        } else {
            background.setBounds(0, 0, 0, 0)
        }

        val iconTop = (itemView.bottom + itemView.top - icon.intrinsicHeight) / 2
        val iconBottom = (itemView.bottom + itemView.top + icon.intrinsicHeight) / 2
        // The strip the row has uncovered so far, which is all the icon has to sit in.
        val strip = abs(clamped).toInt()
        // Normally the icon trails the row's moving edge by a fixed margin. That needs the strip
        // to be wider than the icon and its margin together, and it is not always: one action
        // bound to a side stops the row at a quarter of the threshold, which on a phone is
        // narrower than the icon itself, and trailing the edge put most of it off the screen.
        // Centred in the strip instead, so it comes out from under the row rather than past it.
        val trailsTheEdge = strip >= horizontalOffset + icon.intrinsicWidth
        val iconLeft = if (dX > 0) {
            if (trailsTheEdge) itemView.left + strip - horizontalOffset - icon.intrinsicWidth
            else itemView.left + (strip - icon.intrinsicWidth) / 2
        } else {
            if (trailsTheEdge) itemView.right - strip + horizontalOffset
            else itemView.right - strip + (strip - icon.intrinsicWidth) / 2
        }
        icon.setBounds(iconLeft, iconTop, iconLeft + icon.intrinsicWidth, iconBottom)

        background.draw(canvas)
        icon.draw(canvas)
        return clamped
    }

    /** The action the release should run, or [SwipeActionLevels.NONE]. Runs exactly once. */
    fun consumeAction(): Int = levels.consume()

    private fun background(action: Int): ColorDrawable = backgrounds.get(action)
        ?: ColorDrawable(colorFor(action)).also { backgrounds.put(action, it) }

    private fun icon(action: Int): Drawable = icons.get(action)
        ?: requireNotNull(ResourcesCompat.getDrawable(context.resources, iconResFor(action), null))
            .also { icons.put(action, it) }

    private fun colorFor(action: Int): Int {
        if (comments) {
            return when (action) {
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_UPVOTE -> customThemeWrapper.upvoted
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_DOWNVOTE -> customThemeWrapper.downvoted
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_SAVE -> color(R.color.swipeActionSave)
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_REPLY -> color(R.color.swipeActionReply)
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_SHARE -> color(R.color.swipeActionShare)
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_PROFILE -> color(R.color.swipeActionProfile)
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_SHARE_AS_IMAGE ->
                    color(R.color.swipeActionShareAsImage)
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_SHARE_AS_IMAGE_WITH_THREAD ->
                    color(R.color.swipeActionShareAsImageWithThread)
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_SET_REMINDER ->
                    color(R.color.swipeActionSetReminder)
                else -> throw IllegalArgumentException("No colour for comment swipe action $action")
            }
        }
        return when (action) {
            SharedPreferencesUtils.SWIPE_ACITON_UPVOTE -> customThemeWrapper.upvoted
            SharedPreferencesUtils.SWIPE_ACITON_DOWNVOTE -> customThemeWrapper.downvoted
            SharedPreferencesUtils.SWIPE_ACITON_SAVE -> color(R.color.swipeActionSave)
            SharedPreferencesUtils.SWIPE_ACITON_HIDE -> color(R.color.swipeActionHide)
            SharedPreferencesUtils.SWIPE_ACITON_MARK_AS_READ_AND_HIDE ->
                color(R.color.swipeActionMarkAsReadAndHide)
            SharedPreferencesUtils.SWIPE_ACITON_MARK_AS_READ -> color(R.color.swipeActionMarkAsRead)
            SharedPreferencesUtils.SWIPE_ACITON_MARK_AS_UNREAD -> color(R.color.swipeActionMarkAsUnread)
            SharedPreferencesUtils.SWIPE_ACITON_TOGGLE_READ -> color(R.color.swipeActionToggleRead)
            SharedPreferencesUtils.SWIPE_ACITON_SHARE -> color(R.color.swipeActionShare)
            SharedPreferencesUtils.SWIPE_ACITON_PROFILE -> color(R.color.swipeActionProfile)
            SharedPreferencesUtils.SWIPE_ACITON_COMMENT -> color(R.color.swipeActionComment)
            SharedPreferencesUtils.SWIPE_ACITON_OPEN_IN_NEW_WINDOW ->
                color(R.color.swipeActionOpenInNewWindow)
            SharedPreferencesUtils.SWIPE_ACITON_CROSSPOST -> color(R.color.swipeActionCrosspost)
            else -> throw IllegalArgumentException("No colour for post swipe action $action")
        }
    }

    private fun iconResFor(action: Int): Int {
        if (comments) {
            return when (action) {
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_UPVOTE -> R.drawable.ic_arrow_upward_day_night_24dp
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_DOWNVOTE -> R.drawable.ic_arrow_downward_day_night_24dp
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_SAVE -> R.drawable.ic_bookmark_day_night_24dp
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_REPLY -> R.drawable.ic_reply_day_night_24dp
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_SHARE -> R.drawable.ic_share_day_night_24dp
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_PROFILE -> R.drawable.ic_account_circle_day_night_24dp
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_SHARE_AS_IMAGE,
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_SHARE_AS_IMAGE_WITH_THREAD ->
                    R.drawable.ic_image_day_night_24dp
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_SET_REMINDER -> R.drawable.ic_reminder_day_night_24dp
                else -> throw IllegalArgumentException("No icon for comment swipe action $action")
            }
        }
        return when (action) {
            SharedPreferencesUtils.SWIPE_ACITON_UPVOTE -> R.drawable.ic_arrow_upward_day_night_24dp
            SharedPreferencesUtils.SWIPE_ACITON_DOWNVOTE -> R.drawable.ic_arrow_downward_day_night_24dp
            SharedPreferencesUtils.SWIPE_ACITON_SAVE -> R.drawable.ic_bookmark_day_night_24dp
            SharedPreferencesUtils.SWIPE_ACITON_HIDE -> R.drawable.ic_hide_post_day_night_24dp
            SharedPreferencesUtils.SWIPE_ACITON_MARK_AS_READ_AND_HIDE ->
                R.drawable.ic_hide_read_posts_day_night_24dp
            SharedPreferencesUtils.SWIPE_ACITON_MARK_AS_READ -> R.drawable.ic_check_circle_day_night_24dp
            SharedPreferencesUtils.SWIPE_ACITON_MARK_AS_UNREAD -> R.drawable.ic_mark_as_unread_day_night_24dp
            SharedPreferencesUtils.SWIPE_ACITON_TOGGLE_READ -> R.drawable.ic_toggle_read_day_night_24dp
            SharedPreferencesUtils.SWIPE_ACITON_SHARE -> R.drawable.ic_share_day_night_24dp
            SharedPreferencesUtils.SWIPE_ACITON_PROFILE -> R.drawable.ic_account_circle_day_night_24dp
            SharedPreferencesUtils.SWIPE_ACITON_COMMENT -> R.drawable.ic_reply_day_night_24dp
            SharedPreferencesUtils.SWIPE_ACITON_OPEN_IN_NEW_WINDOW ->
                R.drawable.ic_open_in_new_window_day_night_24dp
            // Fixed white rather than a day/night pair, which is what a coloured band wants.
            SharedPreferencesUtils.SWIPE_ACITON_CROSSPOST -> R.drawable.ic_crosspost_24dp
            else -> throw IllegalArgumentException("No icon for post swipe action $action")
        }
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(context, resId)
}
