package ml.docilealligator.infinityforreddit.utils

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.appcompat.content.res.AppCompatResources
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.user.UserMark

/**
 * How a followed, saved or favourited user is marked beside their name (issue #415).
 *
 * The three glyphs are the ones the Followed Users list already puts against those same flags, so
 * the marker teaches nothing new: the F badge for a follow, the bookmark for a local save, the
 * heart for a favourite. Drawn at 14dp, the size of the OP and moderator badges, or smaller
 * where the chosen font size leaves less room; see [IconSpan].
 *
 * The marker follows the name and the OP/moderator/current-user badge keeps its place in front of
 * it, so the two never compete: a followed user who is also the OP shows both, and the marker
 * never enters that badge's priority chain.
 */
object UserMarkIcon {

    private const val SIZE_DP = 14f
    private const val GAP_DP = 4f

    /**
     * The character the icon is drawn over. A space and not a zero-width one: if the view ever
     * falls back to plain text — a copy, a TalkBack read — a word break is what belongs there.
     */
    private const val PLACEHOLDER = " "

    /**
     * [name] with [mark]'s glyph after it, or [name] itself when the user carries no mark.
     *
     * [tintColor] is the colour the name is drawn in, which the F badge and the bookmark take so
     * that a marker on an OP or a moderator matches their name rather than fighting it. The heart
     * is left as it is: `ic_favorite_24dp` carries its own red, the Followed Users list draws it
     * untinted, and a favourite that changed colour with the author's role would stop reading as
     * one.
     */
    @JvmStatic
    fun appendTo(
        context: Context,
        name: CharSequence,
        mark: UserMark?,
        tintColor: Int,
    ): CharSequence {
        val badge = badgeFor(context, mark, tintColor, leadingGap = true) ?: return name
        return SpannableStringBuilder(name).append(badge)
    }

    /**
     * The glyph on its own, or null when the user carries no mark — for a caller assembling the
     * badges itself, such as the compact row that moves them to a second line when the name fills
     * the first. [leadingGap] is the 4dp that separates the glyph from a name in front of it, and
     * is dropped when nothing precedes it on the line.
     */
    @JvmStatic
    fun badgeFor(
        context: Context,
        mark: UserMark?,
        tintColor: Int,
        leadingGap: Boolean,
    ): CharSequence? {
        if (mark == null) {
            return null
        }
        val drawable = drawableFor(context, mark, tintColor) ?: return null
        val maxSize = Utils.convertDpToPixel(SIZE_DP, context).toInt()
        val gap = if (leadingGap) Utils.convertDpToPixel(GAP_DP, context).toInt() else 0
        val builder = SpannableStringBuilder(PLACEHOLDER)
        builder.setSpan(
            IconSpan(drawable, maxSize, gap),
            0, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return builder
    }

    /**
     * Mutated before tinting, unlike the badges set straight onto a text view: this drawable is
     * held by a span for as long as the text lives, and a tint written into the shared constant
     * state would follow the icon everywhere else it is used.
     */
    private fun drawableFor(context: Context, mark: UserMark, tintColor: Int) =
        when (mark) {
            UserMark.FAVORITED -> AppCompatResources.getDrawable(context, R.drawable.ic_favorite_24dp)
                ?.mutate()

            UserMark.FOLLOWED -> AppCompatResources.getDrawable(context, R.drawable.ic_follow_24dp)
                ?.mutate()?.apply { setTint(tintColor) }

            UserMark.SAVED -> AppCompatResources.getDrawable(context, R.drawable.ic_bookmark_day_night_24dp)
                ?.mutate()?.apply { setTint(tintColor) }
        }
}
