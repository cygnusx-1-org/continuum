package ml.docilealligator.infinityforreddit.layout

import android.app.Activity
import android.app.Application
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.StyleRes
import androidx.recyclerview.widget.RecyclerView
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.loadingindicator.LoadingIndicator
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper
import ml.docilealligator.infinityforreddit.font.ContentFontFamily
import ml.docilealligator.infinityforreddit.font.ContentFontStyle
import ml.docilealligator.infinityforreddit.font.FontFamily
import ml.docilealligator.infinityforreddit.font.FontStyle
import ml.docilealligator.infinityforreddit.font.TitleFontFamily
import ml.docilealligator.infinityforreddit.font.TitleFontStyle
import ml.docilealligator.infinityforreddit.utils.CustomThemeSharedPreferencesUtils
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot tests that render the feed/list item layouts across every configuration axis that can
 * change how they measure — smallest-width dp, orientation, column count, theme and font scale —
 * without needing an emulator. Robolectric reconfigures the in-process display per case (including
 * selecting -sw600dp resource variants), and Roborazzi captures a PNG that is diffed against a
 * committed golden.
 *
 * Workflow:
 *   ./gradlew recordRoborazziDebug    # write/update goldens in src/test/screenshots/ (commit them)
 *   ./gradlew verifyRoborazziDebug    # fail the build on any pixel diff
 *   ./gradlew compareRoborazziDebug   # write *_compare.png diff images without failing
 *
 * `check` depends on verifyRoborazziDebug, and scripts/verify-goldens.sh is the one-command form.
 *
 * Cases are generated in tiers rather than as one flat cross-product: crossing every axis would be
 * ~4,000 goldens, most of them redundant. Axes that change *measurement* (width, orientation,
 * columns, font scale) are crossed against every layout; axes that only change *colour* (theme) are
 * sampled at a few widths, because a palette swap cannot move a pixel boundary. See [cases].
 *
 * Infinity applies its colours per-view at runtime (CustomThemeWrapper, during adapter binding), not
 * via the layout XML — so an inflated item has no colours of its own. We reproduce that by reading
 * the real default palette straight from [CustomThemeWrapper] (empty prefs → built-in defaults) and
 * applying it generically in [applyTheme] (card surface, text, icon tints, page background). Layouts
 * that *do* read `?attr/` colours directly get them from the theme overlay [themeOverlay] picks, the
 * same way BaseActivity does. Content is generic placeholder text/images, so this is a
 * realistic-but-not-pixel-exact regression tripwire, without coupling to per-layout view ids or the
 * adapters.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
// SDK 33 + stock Application mirror OAuthLoginHelperMessageTest: we only need a themed Activity to
// inflate against, and the real Infinity Application's onCreate installs a global EventBus that
// throws when reused across test methods.
@Config(sdk = [33], application = Application::class)
// Native graphics so view.draw() actually rasterises text (the legacy canvas only paints shapes).
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoborazziLayoutTest(private val case: Case) {

    /** Post layout family, which is what decides the column count. See [columnsFor]. */
    enum class Family { CARD, CARD_2, CARD_3, COMPACT, COMPACT_2, GALLERY, NONE }

    enum class Orientation { PORTRAIT, LANDSCAPE }

    /**
     * Font size overlays, applied as a set the way BaseActivity does from prefs
     * (BaseActivity.java:188-195). Font *family* is deliberately not an axis: it changes glyphs, not
     * the measurement behaviour these tiers exist to catch.
     */
    enum class FontScale(
        @param:StyleRes val fontStyle: Int,
        @param:StyleRes val titleFontStyle: Int,
        @param:StyleRes val contentFontStyle: Int,
    ) {
        NORMAL(FontStyle.Normal.resId, TitleFontStyle.Normal.resId, ContentFontStyle.Normal.resId),
        XLARGE(FontStyle.XLarge.resId, TitleFontStyle.XLarge.resId, ContentFontStyle.XLarge.resId),
    }

    /** One parameterised capture. [goldenName] is both the test name and the PNG filename. */
    data class Case(
        val layoutName: String,
        @param:LayoutRes val layoutRes: Int,
        val family: Family,
        val swDp: Int,
        val orientation: Orientation,
        val themeLabel: String,
        val themeType: Int,
        val fontScale: FontScale,
    ) {
        /**
         * `{layout}_{theme}_sw{n}dp[_land][_xlarge]` — the pre-existing scheme with a suffix per new
         * axis, so portrait/normal-font goldens keep the filenames they have always had and a
         * re-record diff shows which images genuinely changed pixels rather than a mass rename.
         */
        val goldenName: String = buildString {
            append(layoutName).append('_').append(themeLabel).append("_sw").append(swDp).append("dp")
            if (orientation == Orientation.LANDSCAPE) append("_land")
            if (fontScale == FontScale.XLARGE) append("_xlarge")
        }

        override fun toString(): String = goldenName
    }

    private data class LayoutSpec(val name: String, @param:LayoutRes val res: Int, val family: Family)

    companion object {
        /** Long enough to wrap onto multiple lines on narrow widths — the main thing that varies by dp. */
        private const val SAMPLE_TEXT =
            "Sample content long enough to wrap across multiple lines on narrower screens"

        /** Edge of the generated placeholder image, and the stand-in height for an empty gallery page. */
        private const val SAMPLE_IMAGE_SIZE_PX = 240

        /**
         * Feed/list item layouts: everything PostRecyclerViewAdapter.onCreateViewHolder inflates
         * (PostRecyclerViewAdapter.java:687-754), plus the comment rows and the subreddit/multireddit/
         * user listings. Each compact family is covered on both thumbnail sides: the left- and
         * right-thumbnail files are separate layouts that drift apart independently.
         */
        private val FEED_LAYOUTS: List<LayoutSpec> = listOf(
            // Card (POST_LAYOUT_CARD) — one entry per post type the adapter can show.
            LayoutSpec("card", R.layout.item_post_with_preview, Family.CARD),
            LayoutSpec("cardText", R.layout.item_post_text, Family.CARD),
            LayoutSpec("cardGalleryType", R.layout.item_post_gallery_type, Family.CARD),
            LayoutSpec("cardVideo", R.layout.item_post_video_type_autoplay, Family.CARD),
            LayoutSpec("cardVideoLegacy", R.layout.item_post_video_type_autoplay_legacy_controller, Family.CARD),
            // Card 2.
            LayoutSpec("card2", R.layout.item_post_card_2_with_preview, Family.CARD_2),
            LayoutSpec("card2Text", R.layout.item_post_card_2_text, Family.CARD_2),
            LayoutSpec("card2GalleryType", R.layout.item_post_card_2_gallery_type, Family.CARD_2),
            LayoutSpec("card2Video", R.layout.item_post_card_2_video_autoplay, Family.CARD_2),
            LayoutSpec("card2VideoLegacy", R.layout.item_post_card_2_video_autoplay_legacy_controller, Family.CARD_2),
            LayoutSpec("card2CompactLink", R.layout.item_post_card_2_compact_link, Family.CARD_2),
            LayoutSpec("card2CompactLinkRight", R.layout.item_post_card_2_compact_link_right_thumbnail, Family.CARD_2),
            // Card 3 (Material 3).
            LayoutSpec("card3", R.layout.item_post_card_3_with_preview, Family.CARD_3),
            LayoutSpec("card3Text", R.layout.item_post_card_3_text, Family.CARD_3),
            LayoutSpec("card3GalleryType", R.layout.item_post_card_3_gallery_type, Family.CARD_3),
            LayoutSpec("card3Video", R.layout.item_post_card_3_video_type_autoplay, Family.CARD_3),
            LayoutSpec("card3VideoLegacy", R.layout.item_post_card_3_video_type_autoplay_legacy_controller, Family.CARD_3),
            // Compact, both thumbnail sides.
            LayoutSpec("compact", R.layout.item_post_compact, Family.COMPACT),
            LayoutSpec("compactRight", R.layout.item_post_compact_right_thumbnail, Family.COMPACT),
            LayoutSpec("compact2", R.layout.item_post_compact_2, Family.COMPACT_2),
            LayoutSpec("compact2Right", R.layout.item_post_compact_2_right_thumbnail, Family.COMPACT_2),
            // Gallery.
            LayoutSpec("gallery", R.layout.item_post_gallery, Family.GALLERY),
            LayoutSpec("galleryGalleryType", R.layout.item_post_gallery_gallery_type, Family.GALLERY),
            // Listings and comment rows. commentCollapsed must stay pixel-identical to comment for
            // every element they share — that pairing is the reason it is covered.
            LayoutSpec("subreddit", R.layout.item_subreddit_listing, Family.NONE),
            LayoutSpec("multireddit", R.layout.item_multi_reddit, Family.NONE),
            LayoutSpec("profile", R.layout.item_user_listing, Family.NONE),
            LayoutSpec("comment", R.layout.item_comment, Family.NONE),
            LayoutSpec("commentCollapsed", R.layout.item_comment_fully_collapsed, Family.NONE),
        )

        /**
         * Single-column, always-full-width surfaces: the post detail page and assorted list rows.
         * Width matters less here than for the feed, so they run a reduced width set.
         */
        private val SECONDARY_LAYOUTS: List<LayoutSpec> = listOf(
            // Post detail page — one entry per post type ViewPostDetailFragment can show.
            LayoutSpec("detailGallery", R.layout.item_post_detail_gallery, Family.NONE),
            LayoutSpec("detailImage", R.layout.item_post_detail_image_and_gif_autoplay, Family.NONE),
            LayoutSpec("detailLink", R.layout.item_post_detail_link, Family.NONE),
            LayoutSpec("detailNoPreview", R.layout.item_post_detail_no_preview, Family.NONE),
            LayoutSpec("detailText", R.layout.item_post_detail_text, Family.NONE),
            LayoutSpec("detailVideoPreview", R.layout.item_post_detail_video_and_gif_preview, Family.NONE),
            LayoutSpec("detailVideo", R.layout.item_post_detail_video_autoplay, Family.NONE),
            LayoutSpec("detailVideoLegacy", R.layout.item_post_detail_video_autoplay_legacy_controller, Family.NONE),
            // Inbox, subscriptions, nav drawer, and the smaller comment-thread rows.
            LayoutSpec("message", R.layout.item_message, Family.NONE),
            LayoutSpec("privateMessageReceived", R.layout.item_private_message_received, Family.NONE),
            LayoutSpec("privateMessageSent", R.layout.item_private_message_sent, Family.NONE),
            LayoutSpec("subscribedThing", R.layout.item_subscribed_thing, Family.NONE),
            LayoutSpec("navDrawerAccount", R.layout.item_nav_drawer_account, Family.NONE),
            LayoutSpec("award", R.layout.item_award, Family.NONE),
            LayoutSpec("rule", R.layout.item_rule, Family.NONE),
            LayoutSpec("flair", R.layout.item_flair, Family.NONE),
            LayoutSpec("viewAllComments", R.layout.item_view_all_comments, Family.NONE),
            LayoutSpec("loadMoreComments", R.layout.item_load_more_comments_placeholder, Family.NONE),
        )

        /** Common phones (320/360/411/443/448), this dev's phone (527), tablets (600/934). */
        private val FEED_CORE_WIDTHS = listOf(320, 360, 411, 443, 448, 527, 600, 934)
        private val SECONDARY_CORE_WIDTHS = listOf(320, 411, 527, 600)
        private val FEED_AMOLED_WIDTHS = listOf(320, 411, 600)
        private val SECONDARY_AMOLED_WIDTHS = listOf(320, 600)
        private val FEED_LANDSCAPE_WIDTHS = listOf(360, 411, 600)
        private val SECONDARY_LANDSCAPE_WIDTHS = listOf(411)
        private val FEED_FONT_WIDTHS = listOf(320, 411)
        private val SECONDARY_FONT_WIDTHS = listOf(320)

        /**
         * Long edge to pair with each smallest-width in landscape, so `w` is realistically wider than
         * `sw` instead of equal to it. Roughly the real aspect ratio of a phone/tablet in that bucket.
         */
        private val LANDSCAPE_LONG_EDGE_DP = mapOf(360 to 800, 411 to 891, 600 to 960)

        private val LIGHT_DARK = listOf(
            "light" to CustomThemeSharedPreferencesUtils.LIGHT,
            "dark" to CustomThemeSharedPreferencesUtils.DARK,
        )
        private val LIGHT_ONLY = listOf("light" to CustomThemeSharedPreferencesUtils.LIGHT)
        private val AMOLED_ONLY = listOf("amoled" to CustomThemeSharedPreferencesUtils.AMOLED)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = buildList {
            // Core: width x theme, portrait, at each layout's real column count.
            addAll(tier(FEED_LAYOUTS, LIGHT_DARK, FEED_CORE_WIDTHS, Orientation.PORTRAIT, FontScale.NORMAL))
            addAll(tier(SECONDARY_LAYOUTS, LIGHT_DARK, SECONDARY_CORE_WIDTHS, Orientation.PORTRAIT, FontScale.NORMAL))
            // AMOLED: colour-only, so a few widths are enough to catch a palette regression.
            addAll(tier(FEED_LAYOUTS, AMOLED_ONLY, FEED_AMOLED_WIDTHS, Orientation.PORTRAIT, FontScale.NORMAL))
            addAll(tier(SECONDARY_LAYOUTS, AMOLED_ONLY, SECONDARY_AMOLED_WIDTHS, Orientation.PORTRAIT, FontScale.NORMAL))
            // Landscape: wide `w` against a narrow `sw`, and two columns for every feed family.
            addAll(tier(FEED_LAYOUTS, LIGHT_DARK, FEED_LANDSCAPE_WIDTHS, Orientation.LANDSCAPE, FontScale.NORMAL))
            addAll(tier(SECONDARY_LAYOUTS, LIGHT_DARK, SECONDARY_LANDSCAPE_WIDTHS, Orientation.LANDSCAPE, FontScale.NORMAL))
            // Font scale: the overflow stressor, at the narrowest widths where it bites first.
            addAll(tier(FEED_LAYOUTS, LIGHT_ONLY, FEED_FONT_WIDTHS, Orientation.PORTRAIT, FontScale.XLARGE))
            addAll(tier(SECONDARY_LAYOUTS, LIGHT_ONLY, SECONDARY_FONT_WIDTHS, Orientation.PORTRAIT, FontScale.XLARGE))
        }

        private fun tier(
            layouts: List<LayoutSpec>,
            themes: List<Pair<String, Int>>,
            widths: List<Int>,
            orientation: Orientation,
            fontScale: FontScale,
        ): List<Array<Any>> =
            layouts.flatMap { spec ->
                themes.flatMap { (themeLabel, themeType) ->
                    widths.map { swDp ->
                        arrayOf<Any>(
                            Case(spec.name, spec.res, spec.family, swDp, orientation, themeLabel, themeType, fontScale),
                        )
                    }
                }
            }

        /**
         * Columns the app would actually lay this item out in, mirroring the pref defaults in
         * PostFragmentBase.getNColumns (PostFragmentBase.java:466-500). CARD_3 and COMPACT_2 fall into
         * that method's `default` branch (POST_LAYOUT_CARD_3 = 4, POST_LAYOUT_COMPACT_2 = 5), so they
         * follow CARD. Landscape defaults every feed family to 2. Non-feed rows are always single.
         */
        private fun columnsFor(family: Family, orientation: Orientation, isTablet: Boolean): Int =
            when {
                family == Family.NONE -> 1
                orientation == Orientation.LANDSCAPE -> 2
                family == Family.GALLERY -> 2
                family == Family.CARD || family == Family.CARD_3 || family == Family.COMPACT_2 ->
                    if (isTablet) 2 else 1
                else -> 1 // CARD_2, COMPACT
            }

        /**
         * The theme overlay BaseActivity would layer on for this theme type
         * (BaseActivity.java:147-174). It matters because several layouts — the compact family and
         * both card_2 compact-link variants — read `?attr/backgroundColor` and friends straight from
         * the theme rather than being coloured per-view during binding.
         */
        @StyleRes
        private fun themeOverlay(themeType: Int): Int = when (themeType) {
            CustomThemeSharedPreferencesUtils.AMOLED -> R.style.Theme_Normal_AmoledDark
            CustomThemeSharedPreferencesUtils.DARK -> R.style.Theme_Normal_NormalDark
            else -> R.style.Theme_Normal
        }
    }

    /** Real default colours for a theme type, plus a generated sample image for empty image slots. */
    private class Palette(themeType: Int, app: Application, private val res: android.content.res.Resources) {
        private val wrapper = CustomThemeWrapper(
            app.getSharedPreferences("light_theme_test", 0),
            app.getSharedPreferences("dark_theme_test", 0),
            app.getSharedPreferences("amoled_theme_test", 0),
        ).apply { setThemeType(themeType) }

        val pageBackground: Int = wrapper.backgroundColor
        val cardBackground: Int = wrapper.cardViewBackgroundColor
        val textColor: Int = wrapper.primaryTextColor
        val iconColor: Int = wrapper.postIconAndInfoColor

        private val sampleBitmap: Bitmap = makeSampleBitmap()

        /** A fresh BitmapDrawable per slot (shares the bitmap, but bounds are per-instance). */
        fun sampleImage(): Drawable = BitmapDrawable(res, sampleBitmap)

        private fun makeSampleBitmap(): Bitmap {
            val size = SAMPLE_IMAGE_SIZE_PX
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                intArrayOf(Color.rgb(0x3F, 0x51, 0xB5), Color.rgb(0x00, 0xBC, 0xD4), Color.rgb(0xFF, 0x98, 0x00)),
                null, Shader.TileMode.CLAMP,
            )
            canvas.drawPaint(paint)
            // A couple of translucent shapes so it reads as a photo, not a flat fill.
            paint.shader = null
            paint.color = Color.argb(0x88, 0xFF, 0xFF, 0xFF)
            canvas.drawCircle(size * 0.30f, size * 0.30f, size * 0.15f, paint)
            paint.color = Color.argb(0x66, 0x00, 0x00, 0x00)
            canvas.drawRect(0f, size * 0.72f, size.toFloat(), size.toFloat(), paint)
            return bmp
        }
    }

    private var pageBackground: Int = Color.WHITE

    @Before
    fun configureScreen() {
        // xxhdpi (3 px/dp) so text renders at realistic pixel sizes and real-world word-wrap /
        // clipping bugs surface the way users see them. setQualifiers reloads resources, so the
        // -sw600dp variants (including bool/isTablet) apply at 600 and 934.
        // Qualifiers must be in Android's canonical order: smallestWidth, width, height, orientation, density.
        // Portrait: h is a fixed 1600dp, which must stay larger than every swDp (incl. 934) or portrait
        // would clamp the width down to h. It only bounds available height for match_parent, and items
        // wrap their height anyway. Landscape inverts this — the long edge becomes w and swDp becomes h.
        val qualifiers = when (case.orientation) {
            Orientation.PORTRAIT -> "+sw${case.swDp}dp-w${case.swDp}dp-h1600dp-port-xxhdpi"
            Orientation.LANDSCAPE -> {
                val longEdge = LANDSCAPE_LONG_EDGE_DP.getValue(case.swDp)
                "+sw${case.swDp}dp-w${longEdge}dp-h${case.swDp}dp-land-xxhdpi"
            }
        }
        RuntimeEnvironment.setQualifiers(qualifiers)
    }

    @Test
    fun capture() {
        val view = render()
        // Roborazzi's View/Activity overloads render these views fully transparent under Robolectric,
        // so draw the laid-out view onto a bitmap ourselves and capture that. eraseColor (not
        // Canvas.drawColor, which is a no-op on Robolectric's software canvas) paints the real page
        // background; view.draw() then paints the themed item on top.
        val bitmap = Bitmap.createBitmap(
            view.width.coerceAtLeast(1),
            view.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        bitmap.eraseColor(pageBackground)
        view.draw(Canvas(bitmap))
        bitmap.captureRoboImage(
            filePath = "src/test/screenshots/${case.goldenName}.png",
            roborazziOptions = SCREENSHOT_OPTIONS,
        )
        // captureRoboImage writes the file synchronously, so the backing pixels are dead weight from
        // here on. Releasing them keeps peak heap flat across a ~1000-case run instead of leaving a
        // 2802px-wide tablet capture for the collector to find.
        bitmap.recycle()
    }

    /**
     * Inflate against a real, fully-themed Activity and run a genuine layout/draw traversal so the
     * item is realised for native-graphics rendering, then apply the theme palette + placeholder
     * content and lay it out at the width one column of this feed would actually give it.
     */
    private fun render(): View {
        val app = RuntimeEnvironment.getApplication()
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        // AppTheme is deliberately incomplete (font_* attrs unset); BaseActivity completes it at
        // runtime by layering the theme + font size/family overlays. Theme must be set before
        // onCreate creates the themed decor.
        activity.setTheme(R.style.AppTheme)
        activity.theme.applyStyle(themeOverlay(case.themeType), true)
        activity.theme.applyStyle(case.fontScale.fontStyle, true)
        activity.theme.applyStyle(case.fontScale.titleFontStyle, true)
        activity.theme.applyStyle(case.fontScale.contentFontStyle, true)
        activity.theme.applyStyle(FontFamily.Default.resId, true)
        activity.theme.applyStyle(TitleFontFamily.Default.resId, true)
        activity.theme.applyStyle(ContentFontFamily.Default.resId, true)
        controller.create()

        val palette = Palette(case.themeType, app, activity.resources)
        pageBackground = palette.pageBackground

        // One column's worth of the display. The StaggeredGridLayoutManagerItemOffsetDecoration insets
        // (R.dimen.staggeredLayoutManagerItemOffset, plus negative card-3 offsets) are deliberately not
        // modelled: that decoration is a protected static nested class of PostFragmentBase and
        // reproducing it would couple this test to ViewHolder types, which is exactly the coupling
        // applyTheme avoids. A few dp of inset does not change what these goldens are here to catch.
        val isTablet = activity.resources.getBoolean(R.bool.isTablet)
        val columns = columnsFor(case.family, case.orientation, isTablet)
        val itemWidthPx = activity.resources.displayMetrics.widthPixels / columns

        val view = LayoutInflater.from(activity).inflate(case.layoutRes, FrameLayout(activity), false)
        activity.setContentView(
            view,
            ViewGroup.LayoutParams(itemWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        // start→resume→visible attaches the decor to a window and runs a real measure/layout/draw
        // traversal (required for native-graphics rendering to actually paint). Drain the main looper
        // so the pass completes; this first (empty) pass gives each TextView its real width, which
        // applyTheme uses to decide which slots are wide enough to hold a long title/body.
        controller.start().resume().visible()
        shadowOf(Looper.getMainLooper()).idle()

        applyTheme(view, palette, itemWidthPx)

        // Reflow after injecting content so wrapped text grows the layout to its final size.
        view.measure(
            View.MeasureSpec.makeMeasureSpec(itemWidthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        return view
    }

    /**
     * Walk the tree and apply the real theme palette plus generic placeholder content:
     *   - card surfaces get the real card background colour;
     *   - text gets the real primary text colour, and empty wide slots (titles/body) get sample copy
     *     long enough to wrap differently per width (the dp-sensitive behaviour we test) — narrow
     *     labels/counters are left empty so they don't balloon vertically;
     *   - empty image slots show a generated sample image; pre-set icons are tinted the icon colour;
     *   - compact thumbnail boxes, which the XML leaves GONE for the adapter to reveal per post, are
     *     shown so the goldens cover the thumbnail slot and the spacing around it.
     * Generic (no per-layout view-id coupling) so it survives layout changes; a future refinement
     * could bind real Post/Comment fixtures through the adapters for pixel-exact fidelity.
     */
    private fun applyTheme(view: View, palette: Palette, itemWidthPx: Int) {
        // The Material loading indicator animates, so the frame it happens to land on differs run to
        // run and the capture is not reproducible (it is why the gallery golden drifts). INVISIBLE,
        // not GONE: the indicator is the only non-GONE child holding the gallery card and the card_2
        // image slot open, so removing it from layout would collapse those items to nothing. This
        // keeps every measurement identical and only skips the non-reproducible draw.
        if (view is LoadingIndicator) {
            view.visibility = View.INVISIBLE
        }
        // Keyed on the square thumbnail dimen rather than per-layout ids, the same way
        // PostRecyclerViewAdapter sizes these boxes. This matches only the preview wrapper; the
        // no-preview link fallback stays GONE, so exactly one of the two shows, as in the app.
        if (view.visibility == View.GONE && view.isCompactThumbnailBox()) {
            view.visibility = View.VISIBLE
        }
        // The *_gallery_type layouts gate their media block behind a GONE container holding a
        // horizontal RecyclerView of gallery pages, which the adapter reveals for a gallery post. In
        // item_post_gallery_gallery_type that block and a no-preview leaf are the card's *only*
        // children, with no LoadingIndicator to hold it open, so leaving it GONE collapsed the whole
        // card to 1px and the golden tested nothing. Reveal the container (structure, not view id)
        // and leave the leaf fallback GONE — the same one-of-two rule isCompactThumbnailBox follows.
        if (view.visibility == View.GONE && view is ViewGroup) {
            view.findGalleryRecyclerView()?.let { gallery ->
                view.visibility = View.VISIBLE
                // An adapter-less RecyclerView measures to zero, so the revealed block would still be
                // empty. Stand in for a single gallery page. Scoped to this reveal rather than to
                // every empty RecyclerView, so the awards list in item_comment keeps measuring to nil
                // the way it does in the app.
                gallery.minimumHeight = SAMPLE_IMAGE_SIZE_PX
            }
        }
        when (view) {
            is MaterialCardView -> view.setCardBackgroundColor(palette.cardBackground)
            is MaterialButton -> {
                view.setTextColor(palette.textColor)
                view.iconTint = ColorStateList.valueOf(palette.iconColor)
            }
            is Button -> view.setTextColor(palette.textColor)
            is TextView -> {
                // Relative to the item, not the display: in a 2-column feed an item is half the
                // display wide, and a display-relative threshold would stop filling its title.
                val minFillWidth = itemWidthPx * 0.45
                if (view.text.isNullOrEmpty() && view.width >= minFillWidth) {
                    view.text = SAMPLE_TEXT
                }
                view.setTextColor(palette.textColor)
            }
            is ImageView ->
                if (view.drawable == null) {
                    view.setImageDrawable(palette.sampleImage())
                } else {
                    view.imageTintList = ColorStateList.valueOf(palette.iconColor)
                }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) applyTheme(view.getChildAt(i), palette, itemWidthPx)
        }
    }

    /** The gallery-page RecyclerView in this subtree, if this container is a media gallery block. */
    private fun ViewGroup.findGalleryRecyclerView(): RecyclerView? {
        for (i in 0 until childCount) {
            when (val child = getChildAt(i)) {
                is RecyclerView -> return child
                is ViewGroup -> child.findGalleryRecyclerView()?.let { return it }
            }
        }
        return null
    }

    /** True for a view laid out as the square compact-post thumbnail box. */
    private fun View.isCompactThumbnailBox(): Boolean {
        val box = resources.getDimensionPixelSize(R.dimen.post_compact_thumbnail_size)
        val lp = layoutParams ?: return false
        return lp.width == box && lp.height == box
    }
}

private val SCREENSHOT_OPTIONS = RoborazziOptions(
    captureType = RoborazziOptions.CaptureType.Screenshot(),
)
