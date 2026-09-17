package ml.docilealligator.infinityforreddit.utils

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import ml.docilealligator.infinityforreddit.R

/**
 * The "Recovered" marker for content that came from the Arctic Shift archive rather than from
 * Reddit.
 *
 * A post shows it as a chip in the flair row it already has. A comment has no chip row, and its
 * byline is a tightly constrained layout where an added view would move its neighbours, so there it
 * is drawn as a span inside the author-flair line instead: nothing is added to the layout, and a
 * recovered comment lands on the same pixels as an ordinary one.
 *
 * Its colours are passed in rather than read from a resource, so the marker follows the theme like
 * the chips it sits among (issue #387). Every caller hands it the NSFW chip's colours: the marker
 * has no theme entry of its own, and of everything a theme already defines that is the closest match
 * to the fixed red this used to draw.
 */
object RecoveredFlair {

    /**
     * The label on its own, for a comment with no flair of its own.
     */
    @JvmStatic
    fun label(context: Context, backgroundColor: Int, textColor: Int): CharSequence =
        prependTo(context, null, backgroundColor, textColor)

    /**
     * The label followed by [flair], so a recovered comment keeps whatever flair its author had.
     */
    @JvmStatic
    fun prependTo(
        context: Context,
        flair: CharSequence?,
        backgroundColor: Int,
        textColor: Int
    ): CharSequence {
        val text = context.getString(R.string.recovered)
        val builder = SpannableStringBuilder(text)
        builder.setSpan(
            ChipSpan.standard(context, backgroundColor, textColor, growLine = true),
            0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        if (!flair.isNullOrEmpty()) {
            builder.append("  ").append(flair)
        }
        return builder
    }
}
