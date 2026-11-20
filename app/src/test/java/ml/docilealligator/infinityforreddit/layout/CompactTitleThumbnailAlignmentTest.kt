package ml.docilealligator.infinityforreddit.layout

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.adapters.CompactTitleAlignment
import ml.docilealligator.infinityforreddit.font.ContentFontFamily
import ml.docilealligator.infinityforreddit.font.ContentFontStyle
import ml.docilealligator.infinityforreddit.font.FontFamily
import ml.docilealligator.infinityforreddit.font.FontStyle
import ml.docilealligator.infinityforreddit.font.TitleFontFamily
import ml.docilealligator.infinityforreddit.font.TitleFontStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Issue #500: in the Compact and Compact 2 layouts the top of the thumbnail must meet the top of the
 * title's capital letters, whatever the post. Before the fix each column floated on its own: the
 * Compact text column was a `spread` chain, so a short title was pushed down by the slack the
 * thumbnail left, and the thumbnail was centred, so a tall title column pushed it down instead. With
 * both pinned to the top, the text still sat a constant ~0.35 em below the thumbnail, the blank space
 * a TextView keeps above its capitals, which [CompactTitleAlignment] now closes.
 *
 * Measured rather than screenshotted, because the defect is a relationship between two tops and
 * [RoborazziLayoutTest] fills every title with the same long sample, which never produces the
 * short-title row. The text side is read from the pixels the title actually draws, not from the font
 * metrics the alignment is computed from, so a wrong formula cannot agree with itself. Each case is
 * also checked against the regime it is meant to exercise, so a fixture change cannot quietly stop
 * covering either half of the bug.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
// Stock Application and SDK 33 for the same reasons as RoborazziLayoutTest. A typical phone width at
// xxhdpi, so a pixel here is a third of a dp and an off-by-one dp would show.
@Config(sdk = [33], application = Application::class, qualifiers = "sw411dp-w411dp-h1600dp-port-xxhdpi")
// Native graphics, or text is measured by the legacy shadow: every title lays out as one short line
// whatever its length, and the long-title cases never get taller than the thumbnail.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompactTitleThumbnailAlignmentTest(private val case: Case) {

    /** View ids of one compact layout. [flowId] and [linkId] are null in Compact 2, which has neither. */
    data class Layout(
        val name: String,
        @param:LayoutRes val layoutRes: Int,
        @param:IdRes val titleId: Int,
        @param:IdRes val previewBoxId: Int,
        @param:IdRes val noPreviewBoxId: Int,
        @param:IdRes val noPreviewImageId: Int,
        @param:IdRes val flowId: Int?,
        @param:IdRes val typeId: Int?,
        @param:IdRes val linkId: Int?,
    )

    enum class Title(val text: String) {
        // Shorter than the thumbnail in every layout (one or two lines): post 3 in the issue's screenshot.
        SHORT("Arguments on Reddit are like"),
        // Wraps to several lines: post 2.
        LONG(
            "Denmark celebrates its oldest woman, then finds out she was a Nazi, and the story " +
                "keeps going long enough to wrap onto a fourth and fifth line of the compact title",
        ),
    }

    /** Which of the two thumbnail boxes the adapter shows. Exactly one is ever visible. */
    enum class Box { PREVIEW, NO_PREVIEW }

    /**
     * The title typeface, set on the title the way the adapter sets a custom one
     * (PostCompactBaseViewHolder.setBaseView). Loaded from the font file rather than through the
     * theme's fontFamily, which Robolectric does not resolve: the title silently keeps the default.
     */
    enum class TitleFont(val file: String?) {
        DEFAULT(null),

        // Its tallest glyph stops below its declared ascent, and the layout puts the first baseline
        // at the larger of the two, so an offset worked out from the font's top metric alone left
        // the text 3 px under the thumbnail at the Normal title size on device.
        ATKINSON_HYPERLEGIBLE("src/main/res/font/atkinson_hyperlegible_regular.ttf"),
    }

    data class Case(
        val layout: Layout,
        val title: Title,
        val box: Box,
        val showLink: Boolean,
        val thumbnailSizeDp: Int,
        val titleSize: TitleFontStyle,
        val titleFont: TitleFont,
    ) {
        override fun toString(): String =
            "${layout.name}_${title.name.lowercase()}_${box.name.lowercase()}" +
                (if (showLink) "_link" else "") + "_thumb${thumbnailSizeDp}_title${titleSize.name}" +
                "_font${titleFont.name.lowercase()}"
    }

    companion object {
        private val LAYOUTS = listOf(
            Layout(
                "compact", R.layout.item_post_compact,
                R.id.title_text_view_item_post_compact,
                R.id.image_view_wrapper_item_post_compact,
                R.id.frame_layout_image_view_no_preview_link_item_post_compact,
                R.id.image_view_no_preview_link_item_post_compact,
                R.id.flow_layout_item_post_compact,
                R.id.type_text_view_item_post_compact,
                R.id.link_text_view_item_post_compact,
            ),
            Layout(
                "compactRight", R.layout.item_post_compact_right_thumbnail,
                R.id.title_text_view_item_post_compact_right_thumbnail,
                R.id.image_view_wrapper_item_post_compact_right_thumbnail,
                R.id.frame_layout_image_view_no_preview_link_item_post_compact_right_thumbnail,
                R.id.image_view_no_preview_link_item_post_compact_right_thumbnail,
                R.id.flow_layout_item_post_compact_right_thumbnail,
                R.id.type_text_view_item_post_compact_right_thumbnail,
                R.id.link_text_view_item_post_compact_right_thumbnail,
            ),
            Layout(
                "compact2", R.layout.item_post_compact_2,
                R.id.title_text_view_item_post_compact_2,
                R.id.image_view_wrapper_item_post_compact_2,
                R.id.frame_layout_image_view_no_preview_link_item_post_compact_2,
                R.id.image_view_no_preview_link_item_post_compact_2,
                flowId = null, typeId = null, linkId = null,
            ),
            Layout(
                "compact2Right", R.layout.item_post_compact_2_right_thumbnail,
                R.id.title_text_view_item_post_compact_2_right_thumbnail,
                R.id.image_view_wrapper_item_post_compact_2_right_thumbnail,
                R.id.frame_layout_image_view_no_preview_link_item_post_compact_2_right_thumbnail,
                R.id.image_view_no_preview_link_item_post_compact_2_right_thumbnail,
                flowId = null, typeId = null, linkId = null,
            ),
        )

        /** The smallest and largest Thumbnail size choices: the two ends of the slack between columns. */
        private val THUMBNAIL_SIZES_DP = listOf(72, 112)

        /**
         * The smallest and largest Title font size choices. The blank space above the capitals scales
         * with the text size, so these are the two ends of what the alignment has to absorb.
         */
        private val TITLE_SIZES = listOf(TitleFontStyle.XSmall, TitleFontStyle.XLarge)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = LAYOUTS.flatMap { layout ->
            val linkStates = if (layout.linkId == null) listOf(false) else listOf(false, true)
            Title.entries.flatMap { title ->
                Box.entries.flatMap { box ->
                    linkStates.flatMap { showLink ->
                        THUMBNAIL_SIZES_DP.flatMap { size ->
                            TITLE_SIZES.flatMap { titleSize ->
                                TitleFont.entries.map { titleFont ->
                                    arrayOf<Any>(Case(layout, title, box, showLink, size, titleSize, titleFont))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun thumbnailMeetsTheTopOfTheTitleCapitals() {
        val root = render()
        val layout = case.layout
        val title = root.findViewById<TextView>(layout.titleId)
        val box = root.findViewById<View>(
            if (case.box == Box.PREVIEW) layout.previewBoxId else layout.noPreviewBoxId,
        )
        val row = title.parent as ViewGroup
        assertSame("title and thumbnail share one row", row, box.parent)

        // The bottom of the text column: the last visible view of title → flair row → domain line.
        val textBottom = listOfNotNull(layout.flowId, layout.linkId)
            .map { root.findViewById<View>(it) }
            .filter { it.visibility != View.GONE }
            .maxOfOrNull { it.bottom } ?: title.bottom

        // Prove the case reaches the half of the bug it is named for, or the assertions below could
        // pass on a row where the two columns are the same height and nothing can float.
        if (case.title == Title.SHORT && case.thumbnailSizeDp == 112) {
            assertTrue("$case: text column should be shorter than the thumbnail", textBottom < box.bottom)
        }
        if (case.title == Title.LONG && case.thumbnailSizeDp == 72) {
            assertTrue("$case: text column should be taller than the thumbnail", textBottom > box.bottom)
        }
        // And that the font case really has the shape it is here for, rather than the default font
        // having stood in for one that failed to load.
        if (case.titleFont == TitleFont.ATKINSON_HYPERLEGIBLE) {
            val metrics = title.paint.fontMetricsInt
            assertTrue(
                "$case: the title font's top (${metrics.top}) should lie below its ascent (${metrics.ascent})",
                metrics.top > metrics.ascent,
            )
        }

        assertEquals("$case: title top", row.paddingTop, title.top)
        // Within a pixel, because the capital's edge falls between two pixel rows: the thumbnail
        // takes the row nearest the exact edge, the ink reading takes the first row over half
        // covered, and the two can differ by one.
        val capitalTop = firstCapitalInkTop(title)
        assertTrue(
            "$case: thumbnail top ${box.top} should meet the title's capitals at $capitalTop",
            abs(capitalTop - box.top) <= 1,
        )

        // Compact's text column is a packed chain: each line follows the one above with no gap, so
        // spare height collects below the column instead of being spread between its lines.
        if (layout.flowId != null && layout.linkId != null) {
            val flow = root.findViewById<View>(layout.flowId)
            assertEquals("$case: flair row follows the title", title.bottom, flow.top)
            if (case.showLink) {
                assertEquals("$case: domain line follows the flair row", flow.bottom, root.findViewById<View>(layout.linkId).top)
            }
        }
    }

    /**
     * Where the first character of [title] starts drawing, in its parent's coordinates: the first
     * pixel row in that character's columns with more than half coverage. Both fixture titles start
     * with a capital, so this is the cap line as it appears on screen. Only the first character is
     * read, because lowercase ascenders later on the line (l, d, k) rise above the capitals.
     */
    private fun firstCapitalInkTop(title: TextView): Int {
        val bitmap = Bitmap.createBitmap(title.width, title.height, Bitmap.Config.ARGB_8888)
        try {
            title.draw(Canvas(bitmap))
            val left = title.totalPaddingLeft
            val right = minOf(bitmap.width, left + ceil(title.paint.measureText(title.text, 0, 1)).toInt())
            for (y in 0 until bitmap.height) {
                for (x in left until right) {
                    if (Color.alpha(bitmap.getPixel(x, y)) >= 128) {
                        return title.top + y
                    }
                }
            }
        } finally {
            bitmap.recycle()
        }
        throw AssertionError("$case: the title drew nothing")
    }

    /**
     * Inflate the item against a fully themed Activity, show what the adapter would show for this
     * post (PostRecyclerViewAdapter: the thumbnail box, the type chip, the domain line for a link,
     * the Thumbnail size preference written to both boxes in PostCompactBaseViewHolder.setBaseView,
     * and the thumbnail moved down to the title's capitals in alignThumbnailToTitle) and lay it out
     * at the display width, which is one compact column on a phone.
     */
    private fun render(): View {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.AppTheme)
        activity.theme.applyStyle(R.style.Theme_Normal, true)
        activity.theme.applyStyle(FontStyle.Normal.resId, true)
        activity.theme.applyStyle(case.titleSize.resId, true)
        activity.theme.applyStyle(ContentFontStyle.Normal.resId, true)
        activity.theme.applyStyle(FontFamily.Default.resId, true)
        activity.theme.applyStyle(TitleFontFamily.Default.resId, true)
        activity.theme.applyStyle(ContentFontFamily.Default.resId, true)
        controller.create()

        val layout = case.layout
        val root = LayoutInflater.from(activity).inflate(layout.layoutRes, FrameLayout(activity), false)

        val title = root.findViewById<TextView>(layout.titleId)
        title.text = case.title.text
        case.titleFont.file?.let { title.typeface = Typeface.createFromFile(it) }
        // Opaque, so the ink reading in firstCapitalInkTop does not depend on the theme's colour.
        title.setTextColor(Color.BLACK)
        layout.typeId?.let { root.findViewById<TextView>(it).text = "IMAGE" }
        layout.linkId?.let {
            root.findViewById<TextView>(it).apply {
                text = "www.example.com"
                visibility = if (case.showLink) View.VISIBLE else View.GONE
            }
        }

        val sizePx = (case.thumbnailSizeDp * activity.resources.displayMetrics.density).roundToInt()
        listOf(layout.previewBoxId, layout.noPreviewImageId).forEach { id ->
            val view = root.findViewById<View>(id)
            view.layoutParams = view.layoutParams.apply {
                width = sizePx
                height = sizePx
            }
        }
        val previewBox = root.findViewById<View>(layout.previewBoxId)
        val noPreviewBox = root.findViewById<View>(layout.noPreviewBoxId)
        previewBox.visibility = if (case.box == Box.PREVIEW) View.VISIBLE else View.GONE
        noPreviewBox.visibility = if (case.box == Box.NO_PREVIEW) View.VISIBLE else View.GONE
        // The same call, on the same two boxes, as PostCompactBaseViewHolder.alignThumbnailToTitle.
        CompactTitleAlignment.alignThumbnailsToTitle(title, previewBox, noPreviewBox)

        val widthPx = activity.resources.displayMetrics.widthPixels
        activity.setContentView(root, ViewGroup.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT))
        controller.start().resume().visible()
        shadowOf(Looper.getMainLooper()).idle()
        return root
    }
}
