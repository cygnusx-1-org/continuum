package ml.docilealligator.infinityforreddit.utils

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import ml.docilealligator.infinityforreddit.user.UserTags

/**
 * How a user tag (issue #413) is drawn beside a name: a chip in the theme's flair colours, the
 * same shape as the "Recovered" marker, because a private tag is the closest thing to a flair the
 * user can give someone.
 *
 * Two placements, matching the two kinds of byline the app has. A comment and the post-detail
 * header have a flair line under the author, and the chip goes at its front, ahead of whatever
 * flair Reddit sent — the same line and the same growth as [RecoveredFlair]. Everywhere else the
 * author is one line with nothing under it, and the chip follows the name on that line without
 * changing its height.
 */
object UserTagChip {

    /**
     * [name] followed by the chip for [username]'s tag, or [name] itself when there is no tag.
     *
     * The chip does not grow the line, so the view is the same height tagged or not.
     */
    @JvmStatic
    fun appendTo(
        context: Context,
        name: CharSequence,
        username: String?,
        backgroundColor: Int,
        textColor: Int,
    ): CharSequence {
        val tag = UserTags.get(username) ?: return name
        val builder = SpannableStringBuilder(name).append(" ")
        val start = builder.length
        builder.append(tag)
        builder.setSpan(
            ChipSpan.standard(context, backgroundColor, textColor, growLine = false),
            start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return builder
    }

    /**
     * The chip for [tag] followed by [flair], for the flair line under an author. The caller has
     * already established there is a tag; [flair] is whatever the line held before, or null when it
     * was hidden.
     */
    @JvmStatic
    fun prependTo(
        context: Context,
        tag: String,
        flair: CharSequence?,
        backgroundColor: Int,
        textColor: Int,
    ): CharSequence {
        val builder = SpannableStringBuilder(tag)
        builder.setSpan(
            ChipSpan.standard(context, backgroundColor, textColor, growLine = true),
            0, tag.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        if (!flair.isNullOrEmpty()) {
            builder.append("  ").append(flair)
        }
        return builder
    }
}
