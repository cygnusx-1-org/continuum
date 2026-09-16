package ml.docilealligator.infinityforreddit.markdown.spoiler

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.Spanned
import android.widget.TextView
import java.lang.ref.WeakReference
import ml.docilealligator.infinityforreddit.R

/**
 * Repaints the block over every still-hidden [SpoilerSpan] in a [TextView], on top of the text
 * instead of behind it.
 *
 * [SpoilerSpan.updateDrawState] hides a spoiler by painting its glyphs in the block's own colour,
 * which only hides what is drawn with the `TextPaint`'s colour. Colour emoji are bitmap glyphs that
 * ignore it, and an [android.text.style.ReplacementSpan] — a Reddit emote or an inline image — does
 * its own drawing and never consults the paint at all, so both stay fully legible through a spoiler
 * nobody has tapped (issue #416). Covering the range afterwards hides what is under it however it
 * was drawn.
 *
 * Attached to the view's overlay, which [android.view.View.draw] paints after the text, by
 * [SpoilerParserPlugin.afterSetText]. It keeps no copy of the text: every draw re-reads the view's
 * current [Layout] and spans, so a single instance stays correct for the life of the view across
 * rebinds and reveal taps.
 */
class SpoilerMaskDrawable private constructor(textView: TextView) : Drawable() {

    private val textViewRef = WeakReference(textView)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val spanPath = Path()
    private val segmentPath = Path()

    override fun draw(canvas: Canvas) {
        val textView = textViewRef.get() ?: return
        val layout = textView.layout ?: return
        // The layout's own text, not the view's: an AppCompat TextView lays out the EmojiCompat
        // transformation of it, and only those offsets line up with the lines measured below.
        val text = layout.text as? Spanned ?: return
        val spans = text.getSpans(0, text.length, SpoilerSpan::class.java)
        if (spans.isEmpty()) {
            return
        }

        var translated = false
        for (span in spans) {
            if (span.isShowing) {
                continue
            }
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            if (start < 0 || end <= start) {
                continue
            }

            spanPath.reset()
            addHiddenRegion(layout, start, end, spanPath)
            if (spanPath.isEmpty) {
                continue
            }

            if (!translated) {
                // The same origin TextView.onDraw lays the text out from; the canvas already
                // carries the view's scroll.
                canvas.save()
                canvas.translate(
                    textView.totalPaddingLeft.toFloat(),
                    textView.totalPaddingTop.toFloat(),
                )
                translated = true
            }
            maskPaint.color = span.backgroundColor
            // Widened by a pixel each way, horizontally only. A run's left and right edges come
            // from Layout.getPrimaryHorizontal and land between pixels, so an antialiased fill
            // leaves the edge column partly transparent. Under ordinary text that is invisible —
            // TextPaint.bgColor has already painted the same colour there — but
            // TextLine.handleReplacement draws no bgColor at all, so a spoiler holding an emote,
            // an inline image or an EmojiCompat glyph has only this mask over it, and a partly
            // covered column is a rim of that content showing through. The vertical edges are line
            // tops and bottoms, whole pixels already: widening those too would put a half-covered
            // row over the neighbouring line's glyphs.
            for (dx in -1..1) {
                canvas.translate(dx.toFloat(), 0f)
                canvas.drawPath(spanPath, maskPaint)
                canvas.translate(-dx.toFloat(), 0f)
            }
        }

        if (translated) {
            canvas.restore()
        }
    }

    /**
     * Adds the region the hidden range `[start, end)` occupies to [out], matching the rows
     * `TextPaint.bgColor` already fills: one rectangle per visual run, spanning the line's full
     * top-to-bottom height.
     */
    private fun addHiddenRegion(layout: Layout, start: Int, end: Int, out: Path) {
        val firstLine = layout.getLineForOffset(start)
        val lastLine = layout.getLineForOffset(end)
        for (line in firstLine..lastLine) {
            val segmentStart = maxOf(start, layout.getLineStart(line))
            val segmentEnd = minOf(end, layout.getLineEnd(line))
            if (segmentEnd <= segmentStart) {
                continue
            }

            if (layout.getLineForOffset(segmentEnd) == line) {
                // Both offsets sit on this line, so getSelectionPath takes its single-line branch:
                // a rectangle per visual run, bidi included. Handing it the whole range at once
                // would instead take the multi-line branch, which pads every row out to the view's
                // full width — much wider than the block bgColor draws.
                segmentPath.reset()
                layout.getSelectionPath(segmentStart, segmentEnd, segmentPath)
                out.addPath(segmentPath)
            } else {
                // The segment runs into a line break, where getLineForOffset — and so
                // getPrimaryHorizontal — resolves to the next line. Cover from the segment's start
                // to the end of this line's text instead.
                val from = layout.getPrimaryHorizontal(segmentStart)
                val to = if (layout.getParagraphDirection(line) == Layout.DIR_LEFT_TO_RIGHT) {
                    layout.getLineRight(line)
                } else {
                    layout.getLineLeft(line)
                }
                out.addRect(
                    minOf(from, to),
                    layout.getLineTop(line).toFloat(),
                    maxOf(from, to),
                    layout.getLineBottom(line).toFloat(),
                    Path.Direction.CW,
                )
            }
        }
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        /**
         * Gives [textView] a mask unless it already has one. The drawable reads the view's live
         * text, so the first one attached keeps working for every later bind; adding one per bind
         * would stack duplicates in the overlay.
         */
        @JvmStatic
        fun attachTo(textView: TextView) {
            if (textView.getTag(R.id.spoiler_mask_drawable) != null) {
                return
            }
            val mask = SpoilerMaskDrawable(textView)
            textView.setTag(R.id.spoiler_mask_drawable, mask)
            textView.overlay.add(mask)
            // ViewOverlay.add() only invalidates the drawable's own bounds, which stay empty
            // because the mask draws from the layout rather than from them.
            textView.invalidate()
        }
    }
}
