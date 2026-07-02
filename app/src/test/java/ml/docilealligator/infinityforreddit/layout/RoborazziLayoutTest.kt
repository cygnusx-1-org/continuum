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
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
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
 * Screenshot tests that render the main feed/list item layouts at every smallest-width-dp
 * configuration we care about, in both the light and dark themes, without needing an emulator.
 * Robolectric reconfigures the in-process display per case (including selecting -sw600dp resource
 * variants), and Roborazzi captures a PNG that is diffed against a committed golden.
 *
 * Workflow:
 *   ./gradlew recordRoborazziDebug    # write/update goldens in src/test/screenshots/ (commit them)
 *   ./gradlew verifyRoborazziDebug    # fail the build on any pixel diff (use in CI / pre-commit)
 *   ./gradlew compareRoborazziDebug   # write *_compare.png diff images without failing
 *
 * Each case is parameterized as (layout x theme x smallest-width dp) so a single layout that breaks
 * at one width/theme fails in isolation with a self-describing name, e.g. "card_dark_sw411dp".
 *
 * Infinity applies its colours per-view at runtime (CustomThemeWrapper, during adapter binding), not
 * via the layout XML — so an inflated item has no colours of its own. We reproduce that by reading
 * the real default palette straight from [CustomThemeWrapper] (empty prefs → built-in defaults) and
 * applying it generically in [applyTheme] (card surface, text, icon tints, page background). Content
 * is generic placeholder text/images, so this is a realistic-but-not-pixel-exact regression tripwire
 * across widths and themes, without coupling to per-layout view ids or the adapters.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
// SDK 33 + stock Application mirror OAuthLoginHelperMessageTest: we only need a themed Activity to
// inflate against, and the real Infinity Application's onCreate installs a global EventBus that
// throws when reused across test methods.
@Config(sdk = [33], application = Application::class)
// Native graphics so view.draw() actually rasterises text (the legacy canvas only paints shapes).
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoborazziLayoutTest(
    private val name: String,
    @param:LayoutRes private val layoutRes: Int,
    private val swDp: Int,
    private val themeLabel: String,
    private val themeType: Int,
) {
    companion object {
        /** Long enough to wrap onto multiple lines on narrow widths — the main thing that varies by dp. */
        private const val SAMPLE_TEXT =
            "Sample content long enough to wrap across multiple lines on narrower screens"

        /** Smallest-width dp buckets: common phones (411/443/448), this dev's phone (527), tablets (600/934). */
        private val SMALLEST_WIDTHS_DP = listOf(411, 443, 448, 527, 600, 934)

        /** Light and dark; both read their real default colours from CustomThemeWrapper. */
        private val THEMES = listOf(
            "light" to CustomThemeSharedPreferencesUtils.LIGHT,
            "dark" to CustomThemeSharedPreferencesUtils.DARK,
        )

        /** Feed/list item layouts under test. The *_with_preview cards are the richest (most prone to overflow). */
        private val LAYOUTS: Map<String, Int> = linkedMapOf(
            "card" to R.layout.item_post_with_preview,
            "card2" to R.layout.item_post_card_2_with_preview,
            "card3" to R.layout.item_post_card_3_with_preview,
            "compact" to R.layout.item_post_compact,
            "compact2" to R.layout.item_post_compact_2,
            "gallery" to R.layout.item_post_gallery,
            "subreddit" to R.layout.item_subreddit_listing,
            "multireddit" to R.layout.item_multi_reddit,
            "profile" to R.layout.item_user_listing,
            "comment" to R.layout.item_comment,
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{3}_sw{2}dp")
        fun cases(): List<Array<Any>> =
            LAYOUTS.flatMap { (name, res) ->
                THEMES.flatMap { (label, type) ->
                    SMALLEST_WIDTHS_DP.map { dp -> arrayOf(name, res, dp, label, type) }
                }
            }
    }

    /** Real default colours for [themeType], plus a generated sample image for empty image slots. */
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
            val size = 240
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
        // Portrait at the target smallest-width dp. xxhdpi (3 px/dp) so text renders at realistic
        // pixel sizes and real-world word-wrap / clipping bugs surface the way users see them.
        // setQualifiers reloads resources, so -sw600dp layout/values variants apply at 600 & 934.
        // Qualifiers must be in Android's canonical order: smallestWidth, width, height, orientation, density.
        // h must stay larger than every swDp (incl. 934) or portrait would clamp the width down to h;
        // it only bounds available height for match_parent, and items wrap their height anyway.
        RuntimeEnvironment.setQualifiers("+sw${swDp}dp-w${swDp}dp-h1600dp-port-xxhdpi")
    }

    @Test
    fun capture() {
        val view = render(layoutRes)
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
            filePath = "src/test/screenshots/${name}_${themeLabel}_sw${swDp}dp.png",
            roborazziOptions = SCREENSHOT_OPTIONS,
        )
    }

    /**
     * Inflate against a real, fully-themed Activity and run a genuine layout/draw traversal so the
     * item is realised for native-graphics rendering, then apply the theme palette + placeholder
     * content and lay it out at the configured display width.
     */
    private fun render(@LayoutRes layoutRes: Int): View {
        val app = RuntimeEnvironment.getApplication()
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        // AppTheme is deliberately incomplete (font_* attrs unset); BaseActivity completes it at
        // runtime by layering the font size/family overlays — colours are applied per-view instead,
        // which we do via Palette below. Theme must be set before onCreate creates the themed decor.
        activity.setTheme(R.style.AppTheme)
        activity.theme.applyStyle(R.style.Theme_Normal, true)
        activity.theme.applyStyle(FontStyle.Normal.resId, true)
        activity.theme.applyStyle(TitleFontStyle.Normal.resId, true)
        activity.theme.applyStyle(ContentFontStyle.Normal.resId, true)
        activity.theme.applyStyle(FontFamily.Default.resId, true)
        activity.theme.applyStyle(TitleFontFamily.Default.resId, true)
        activity.theme.applyStyle(ContentFontFamily.Default.resId, true)
        controller.create()

        val palette = Palette(themeType, app, activity.resources)
        pageBackground = palette.pageBackground

        val view = LayoutInflater.from(activity).inflate(layoutRes, FrameLayout(activity), false)
        activity.setContentView(
            view,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        // start→resume→visible attaches the decor to a window and runs a real measure/layout/draw
        // traversal (required for native-graphics rendering to actually paint). Drain the main looper
        // so the pass completes; this first (empty) pass gives each TextView its real width, which
        // applyTheme uses to decide which slots are wide enough to hold a long title/body.
        controller.start().resume().visible()
        shadowOf(Looper.getMainLooper()).idle()

        applyTheme(view, palette)

        // Reflow after injecting content so wrapped text grows the layout to its final size.
        val widthPx = activity.resources.displayMetrics.widthPixels
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
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
     *   - empty image slots show a generated sample image; pre-set icons are tinted the icon colour.
     * Generic (no per-layout view-id coupling) so it survives layout changes; a future refinement
     * could bind real Post/Comment fixtures through the adapters for pixel-exact fidelity.
     */
    private fun applyTheme(view: View, palette: Palette) {
        when (view) {
            is MaterialCardView -> view.setCardBackgroundColor(palette.cardBackground)
            is MaterialButton -> {
                view.setTextColor(palette.textColor)
                view.iconTint = ColorStateList.valueOf(palette.iconColor)
            }
            is Button -> view.setTextColor(palette.textColor)
            is TextView -> {
                val minFillWidth = view.resources.displayMetrics.widthPixels * 0.45
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
            for (i in 0 until view.childCount) applyTheme(view.getChildAt(i), palette)
        }
    }
}

private val SCREENSHOT_OPTIONS = RoborazziOptions(
    captureType = RoborazziOptions.CaptureType.Screenshot(),
)
