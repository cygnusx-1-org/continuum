package ml.docilealligator.infinityforreddit.adapters

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.text.StaticLayout
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Lines a compact row's thumbnail up with the top of its title's capital letters (issue #500).
 *
 * The layouts pin the title view and the thumbnail to the same top edge, but a TextView's first line
 * starts with blank space above the capitals: the font's extra top padding when includeFontPadding
 * is on, plus the ascender room that accents use. That leaves the visible text about a third of an em
 * below a thumbnail sitting at the view's edge -- 15 px at the smallest title size on a 3x screen, 23
 * at the largest, and different again for each typeface. Size and typeface are both settings, so the
 * gap is measured from the title itself rather than fixed in the layout XML, and the thumbnail moves
 * down to meet the text. Moving the text up instead would clip anything taller than a capital on the
 * first line (accents, brackets, emoji), because the row clips its children.
 *
 * A first line holding a glyph from a taller fallback font, such as an emoji, is laid out with extra
 * room above it, so on those rows the capitals sit a few pixels below the thumbnail.
 */
object CompactTitleAlignment {

    /** The capital measured: flat-topped, so its top is the cap line itself. */
    private const val CAPITAL = "H"

    /** How far below the top of [title] the top of its first line's capital letters falls. */
    @JvmStatic
    fun capTopOffset(title: TextView): Int {
        val paint = title.paint
        // The capital's outline in fractions of a pixel. getTextBounds rounds outward to whole
        // pixels, which put the thumbnail up to a pixel above the letter; rounding the exact edge
        // to the nearest pixel row keeps the two within half a pixel.
        val outline = Path()
        paint.getTextPath(CAPITAL, 0, CAPITAL.length, 0f, 0f, outline)
        val capital = RectF()
        @Suppress("DEPRECATION")
        outline.computeBounds(capital, true)
        if (capital.isEmpty) {
            return 0 // a typeface with no Latin capitals has no cap line to meet
        }
        // Where the first baseline lands is the layout's call, not the font's top metric alone: a
        // font whose tallest glyph stops below its declared ascent (Atkinson Hyperlegible, Noto
        // Sans) is laid out from the ascent, which put its text 1-3 px under the thumbnail. So lay a
        // capital out the way the title's own layout is built and read the baseline it gets.
        val width = ceil(paint.measureText(CAPITAL)).toInt().coerceAtLeast(1)
        val builder = StaticLayout.Builder.obtain(CAPITAL, 0, CAPITAL.length, paint, width)
            .setIncludePad(title.includeFontPadding)
            .setLineSpacing(title.lineSpacingExtra, title.lineSpacingMultiplier)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUseLineSpacingFromFallbacks(title.isFallbackLineSpacing)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // TextView's own resolution of the minimum line height, which can lower the baseline.
            val minimum = title.minimumFontMetrics
                ?: if (title.isLocalePreferredLineHeightForMinimumUsed) {
                    Paint.FontMetrics().also { paint.getFontMetricsForLocale(it) }
                } else {
                    null
                }
            builder.setMinimumFontMetrics(minimum)
        }
        val baseline = builder.build().getLineBaseline(0)
        // capital.top is the cap height, negative because it is measured up from the baseline.
        return (title.compoundPaddingTop + baseline + capital.top).roundToInt().coerceAtLeast(0)
    }

    /**
     * Pushes each of [thumbnails] down from the top of the row so its top edge meets the capitals of
     * [title]. The title and the thumbnails must be laid out from the same top edge.
     */
    @JvmStatic
    fun alignThumbnailsToTitle(title: TextView, vararg thumbnails: View) {
        val offset = capTopOffset(title)
        for (thumbnail in thumbnails) {
            val params = thumbnail.layoutParams as? ViewGroup.MarginLayoutParams ?: continue
            if (params.topMargin != offset) {
                params.topMargin = offset
                thumbnail.layoutParams = params
            }
        }
    }
}
