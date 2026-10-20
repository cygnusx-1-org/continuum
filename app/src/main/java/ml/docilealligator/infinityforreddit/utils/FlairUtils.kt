package ml.docilealligator.infinityforreddit.utils

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.Html
import android.text.Spannable
import android.text.style.ImageSpan
import android.widget.TextView
import androidx.core.graphics.withTranslation
import androidx.core.text.HtmlCompat
import ml.docilealligator.infinityforreddit.subreddit.Flair
import org.json.JSONArray

/**
 * Reddit's flair `richtext`: a flair broken into text runs and custom emoji. A flair made of emoji
 * alone carries only their `:shortcode:` names in its plain `text`, so anything that shows a flair
 * needs the richtext to draw the images instead of those names (issue #458).
 */
object FlairUtils {

    /**
     * [richtext] as HTML for [setFlairText] or [Utils.setHTMLWithImageToTextView]: text runs
     * escaped, each emoji an `<img>`. Returns "" when there is no richtext. A part that is malformed
     * or of an unknown kind is skipped rather than costing the whole flair.
     */
    @JvmStatic
    fun richtextToHtml(richtext: JSONArray?): String {
        if (richtext == null) {
            return ""
        }
        val html = StringBuilder()
        for (i in 0 until richtext.length()) {
            val part = richtext.optJSONObject(i) ?: continue
            when (part.optString(JSONUtils.E_KEY)) {
                "text" -> html.append(Html.escapeHtml(part.optString(JSONUtils.T_KEY)))
                "emoji" -> {
                    val url = part.optString(JSONUtils.U_KEY)
                    if (url.isNotEmpty()) {
                        html.append("<img src=\"").append(Html.escapeHtml(url)).append("\">")
                    }
                }
            }
        }
        return html.toString()
    }

    /**
     * Shows [flair] in [textView], with its emoji drawn when it has any and as plain text otherwise,
     * and sets the view's content description to match.
     * [enlargeImage] draws emoji at 1.5x the text size, as a flair picker row does, instead of at the
     * text size, as a flair chip does.
     */
    @JvmStatic
    fun setFlairText(textView: TextView, flair: Flair, enlargeImage: Boolean) {
        if (flair.htmlText.isEmpty()) {
            textView.text = flair.text
            textView.contentDescription = null
            return
        }
        val html = HtmlCompat.fromHtml(
            flair.htmlText, HtmlCompat.FROM_HTML_MODE_LEGACY,
            GlideImageGetter(textView, enlargeImage), null
        ) as Spannable
        for (span in html.getSpans(0, html.length, ImageSpan::class.java)) {
            val start = html.getSpanStart(span)
            val end = html.getSpanEnd(span)
            val flags = html.getSpanFlags(span)
            html.removeSpan(span)
            html.setSpan(TextLineImageSpan(span.drawable), start, end, flags)
        }
        textView.text = html
        // Emoji have no text for TalkBack to read, so give it their :shortcode: names. A caller that
        // later replaces the flair with a plain label has to clear this again.
        textView.contentDescription = flair.text
    }

    /**
     * An emoji that gives its line the height of a line of text. A plain ImageSpan reports the
     * image's height as the line's whole extent above the baseline, so a line of emoji drawn at the
     * text size comes out shorter than a line of text, and a line mixing the two comes out taller
     * with its baseline lower, moving the chips aligned beside it. This keeps the text's metrics,
     * raises the line only by however much the image is taller than it, and centres the image in it.
     */
    private class TextLineImageSpan(drawable: Drawable) : ImageSpan(drawable, ALIGN_BOTTOM) {

        override fun getSize(
            paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?
        ): Int {
            val bounds = drawable.bounds
            if (fm != null) {
                paint.getFontMetricsInt(fm)
                val overflow = bounds.height() - (fm.bottom - fm.top)
                if (overflow > 0) {
                    fm.top -= overflow
                    fm.ascent -= overflow
                }
            }
            return bounds.right
        }

        override fun draw(
            canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float,
            top: Int, y: Int, bottom: Int, paint: Paint
        ) {
            val image = drawable
            canvas.withTranslation(x, (top + (bottom - top - image.bounds.height()) / 2).toFloat()) {
                image.draw(this)
            }
        }
    }
}
