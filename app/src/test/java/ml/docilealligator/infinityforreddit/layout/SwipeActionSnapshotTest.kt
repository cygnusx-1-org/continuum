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
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.StyleRes
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.android.material.card.MaterialCardView
import com.google.android.material.loadingindicator.LoadingIndicator
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper
import ml.docilealligator.infinityforreddit.customviews.SwipeActionPainter
import ml.docilealligator.infinityforreddit.font.ContentFontFamily
import ml.docilealligator.infinityforreddit.font.ContentFontStyle
import ml.docilealligator.infinityforreddit.font.FontFamily
import ml.docilealligator.infinityforreddit.font.FontStyle
import ml.docilealligator.infinityforreddit.font.TitleFontFamily
import ml.docilealligator.infinityforreddit.font.TitleFontStyle
import ml.docilealligator.infinityforreddit.utils.CustomThemeSharedPreferencesUtils
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
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
 * A post row and a comment row caught mid-swipe, on every level of both sides.
 *
 * What these pin is which side an action shows on. The settings head the two ladders "Left Side"
 * and "Right Side", and the left ladder is the one a drag to the *right* uncovers, at the row's
 * left edge -- the opposite of the gesture-named convention the keys were born with. A unit test
 * can check which ladder [ml.docilealligator.infinityforreddit.utils.SwipeActionLevels] reads for a
 * sign of `dX`; only a picture shows the band and icon landing on the edge the heading names, with
 * the row pushed the other way. Each golden is named for the side and level it shows, so a golden
 * of "left" with the band on the right is the failure, whichever file it is in.
 *
 * The two sides bind different actions at every level, so a golden cannot pass by drawing the
 * wrong ladder in the right place. The strip's two icon placements are both reached: at 320dp the
 * first band is narrower than the icon and it centres, at 411dp and up it trails the row's edge.
 *
 * Drawn the way `ItemTouchHelper` draws a swipe: the painter first, over the page, then the row
 * translated by however far the painter let it go. The row itself is [RoborazziLayoutTest]'s
 * placeholder rendering of the same layout, which is not what these are about.
 *
 * Recording and verifying these is [RoborazziLayoutTest]'s workflow, and the same two tasks.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
// Stock Application for the same reason as RoborazziLayoutTest: only a themed Activity is needed,
// and the real Infinity.onCreate installs a process-global EventBus that throws on reuse.
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SwipeActionSnapshotTest(private val case: Case) {

    /** The two surfaces with a ladder each, at the slot count each one offers. */
    enum class Surface(
        val label: String,
        @param:LayoutRes val layoutRes: Int,
        val comments: Boolean,
        val leftLevels: IntArray,
        val rightLevels: IntArray,
    ) {
        POST(
            "Post", R.layout.item_post_with_preview, comments = false,
            leftLevels = intArrayOf(
                SharedPreferencesUtils.SWIPE_ACITON_UPVOTE,
                SharedPreferencesUtils.SWIPE_ACITON_SAVE,
                SharedPreferencesUtils.SWIPE_ACITON_HIDE,
                SharedPreferencesUtils.SWIPE_ACITON_TOGGLE_READ,
            ),
            rightLevels = intArrayOf(
                SharedPreferencesUtils.SWIPE_ACITON_DOWNVOTE,
                SharedPreferencesUtils.SWIPE_ACITON_SHARE,
                SharedPreferencesUtils.SWIPE_ACITON_PROFILE,
                SharedPreferencesUtils.SWIPE_ACITON_COMMENT,
            ),
        ),
        COMMENT(
            "Comment", R.layout.item_comment, comments = true,
            leftLevels = intArrayOf(
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_UPVOTE,
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_SAVE,
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_REPLY,
            ),
            rightLevels = intArrayOf(
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_DOWNVOTE,
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_SHARE,
                SharedPreferencesUtils.COMMENT_SWIPE_ACITON_PROFILE,
            ),
        );

        val slots: Int get() = leftLevels.size
    }

    /** The side of the row the band shows on, and so the sign of the drag that reveals it. */
    enum class Side(val label: String, val sign: Int) { LEFT("Left", +1), RIGHT("Right", -1) }

    /** One parameterised capture. [goldenName] is both the test name and the PNG filename. */
    data class Case(
        val surface: Surface,
        val side: Side,
        val level: Int,
        val swDp: Int,
        val themeLabel: String,
        val themeType: Int,
    ) {
        /** `swipe{Post|Comment}{Left|Right}{level}_{theme}_sw{n}dp`, in the other goldens' scheme. */
        val goldenName: String = buildString {
            append("swipe").append(surface.label).append(side.label).append(level)
            append('_').append(themeLabel).append("_sw").append(swDp).append("dp")
        }

        override fun toString(): String = goldenName
    }

    companion object {
        private const val SAMPLE_TEXT =
            "Sample content long enough to wrap across multiple lines on narrower screens"

        private const val SAMPLE_IMAGE_SIZE_PX = 240

        /** The `swipe_action_threshold` default, which is what the ladders divide between them. */
        private const val THRESHOLD = 0.3f

        /** Narrow phone, common phone, this dev's phone: one column at every one of them. */
        private val WIDTHS = listOf(320, 411, 527)

        private val LIGHT = "light" to CustomThemeSharedPreferencesUtils.LIGHT
        private val DARK = "dark" to CustomThemeSharedPreferencesUtils.DARK

        private val SCREENSHOT_OPTIONS = RoborazziOptions(
            captureType = RoborazziOptions.CaptureType.Screenshot(),
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = buildList {
            for (surface in Surface.entries) {
                for (side in Side.entries) {
                    for (level in 1..surface.slots) {
                        for (swDp in WIDTHS) {
                            // Light everywhere; dark once, at the common width. The vote bands are
                            // the only ones whose colour comes from the theme.
                            val themes = if (swDp == 411) listOf(LIGHT, DARK) else listOf(LIGHT)
                            for ((themeLabel, themeType) in themes) {
                                add(arrayOf<Any>(Case(surface, side, level, swDp, themeLabel, themeType)))
                            }
                        }
                    }
                }
            }
        }

        @StyleRes
        private fun themeOverlay(themeType: Int): Int = when (themeType) {
            CustomThemeSharedPreferencesUtils.AMOLED -> R.style.Theme_Normal_AmoledDark
            CustomThemeSharedPreferencesUtils.DARK -> R.style.Theme_Normal_NormalDark
            else -> R.style.Theme_Normal
        }
    }

    @Before
    fun configureScreen() {
        // xxhdpi and a tall portrait display, as RoborazziLayoutTest sets up a portrait case.
        RuntimeEnvironment.setQualifiers("+sw${case.swDp}dp-w${case.swDp}dp-h1600dp-port-xxhdpi")
    }

    @Test
    fun capture() {
        val app = RuntimeEnvironment.getApplication()
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.AppTheme)
        activity.theme.applyStyle(themeOverlay(case.themeType), true)
        activity.theme.applyStyle(FontStyle.Normal.resId, true)
        activity.theme.applyStyle(TitleFontStyle.Normal.resId, true)
        activity.theme.applyStyle(ContentFontStyle.Normal.resId, true)
        activity.theme.applyStyle(FontFamily.Default.resId, true)
        activity.theme.applyStyle(TitleFontFamily.Default.resId, true)
        activity.theme.applyStyle(ContentFontFamily.Default.resId, true)
        controller.create()

        // Empty theme files, so the wrapper answers with the built-in palette for this theme type,
        // for the row's colours and for the painter's vote bands alike.
        val wrapper = CustomThemeWrapper(
            app.getSharedPreferences("light_theme_test", 0),
            app.getSharedPreferences("dark_theme_test", 0),
            app.getSharedPreferences("amoled_theme_test", 0),
        ).apply { setThemeType(case.themeType) }

        val rowWidthPx = activity.resources.displayMetrics.widthPixels
        val view = LayoutInflater.from(activity).inflate(case.surface.layoutRes, FrameLayout(activity), false)
        activity.setContentView(view, ViewGroup.LayoutParams(rowWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT))
        // Attach and run a real traversal, so the text views have widths for fill() to go by.
        controller.start().resume().visible()
        shadowOf(Looper.getMainLooper()).idle()

        // The comment adapter paints the row's own background at bind time
        // (CommentsRecyclerViewAdapterNew.java:997); item_comment's root is transparent without it,
        // and the band behind would show straight through the row instead of only through the
        // strip it has uncovered. The post card paints itself, being a MaterialCardView.
        if (case.surface == Surface.COMMENT) {
            view.setBackgroundColor(wrapper.commentBackgroundColor)
        }
        fill(view, wrapper, rowWidthPx, activity.resources)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(rowWidthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val painter = SwipeActionPainter(activity, wrapper, case.surface.comments)
        painter.levels.configure(case.surface.leftLevels, case.surface.rightLevels, THRESHOLD)
        // Halfway into the band for this level: the slots divide the threshold evenly, so band n
        // starts at n slots' worth of the row. Signed for the side, since a positive drag moves
        // the row right and uncovers its left edge.
        val bandFraction = THRESHOLD / case.surface.slots
        val dX = case.side.sign * (case.level + 0.5f) * bandFraction * rowWidthPx

        val bitmap = Bitmap.createBitmap(
            view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888,
        )
        bitmap.eraseColor(wrapper.backgroundColor)
        val canvas = Canvas(bitmap)
        // As ItemTouchHelper does it: the callback paints under the row, then the row is drawn
        // translated by the distance the callback allowed.
        val travel = painter.draw(canvas, view, dX)
        canvas.save()
        canvas.translate(travel, 0f)
        view.draw(canvas)
        canvas.restore()

        bitmap.captureRoboImage(
            filePath = "src/test/screenshots/${case.goldenName}.png",
            roborazziOptions = SCREENSHOT_OPTIONS,
        )
        bitmap.recycle()
    }

    /**
     * The row's colours and placeholder content, the short form of RoborazziLayoutTest.applyTheme:
     * card surface, text colour, sample copy in the wide text slots so the row has its real height,
     * a sample image in the empty image slots, and the icon tint on the rest.
     */
    private fun fill(view: View, wrapper: CustomThemeWrapper, rowWidthPx: Int, res: android.content.res.Resources) {
        if (view is LoadingIndicator) {
            // Animates, so its frame is not reproducible. INVISIBLE keeps the slot it holds open.
            view.visibility = View.INVISIBLE
        }
        when (view) {
            is MaterialCardView -> view.setCardBackgroundColor(wrapper.cardViewBackgroundColor)
            is TextView -> {
                if (view.text.isNullOrEmpty() && view.width >= rowWidthPx * 0.45) {
                    view.text = SAMPLE_TEXT
                }
                view.setTextColor(wrapper.primaryTextColor)
            }
            is ImageView ->
                if (view.drawable == null) {
                    view.setImageDrawable(BitmapDrawable(res, sampleBitmap()))
                } else {
                    view.imageTintList = ColorStateList.valueOf(wrapper.postIconAndInfoColor)
                }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) fill(view.getChildAt(i), wrapper, rowWidthPx, res)
        }
    }

    private fun sampleBitmap(): Bitmap {
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
        return bmp
    }
}
