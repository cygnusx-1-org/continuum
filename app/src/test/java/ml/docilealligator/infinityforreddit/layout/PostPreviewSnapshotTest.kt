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
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StyleRes
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.loadingindicator.LoadingIndicator
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.adapters.PostCardPreviewStyle
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper
import ml.docilealligator.infinityforreddit.databinding.ItemPostWithPreviewBinding
import ml.docilealligator.infinityforreddit.databinding.MarkdownImageAndGifBlockBinding
import ml.docilealligator.infinityforreddit.font.ContentFontFamily
import ml.docilealligator.infinityforreddit.font.ContentFontStyle
import ml.docilealligator.infinityforreddit.font.FontFamily
import ml.docilealligator.infinityforreddit.font.FontStyle
import ml.docilealligator.infinityforreddit.font.TitleFontFamily
import ml.docilealligator.infinityforreddit.font.TitleFontStyle
import ml.docilealligator.infinityforreddit.markdown.imageandgif.MarkdownMediaSize
import ml.docilealligator.infinityforreddit.post.InlineBodyImageFixtures
import ml.docilealligator.infinityforreddit.post.Post
import ml.docilealligator.infinityforreddit.post.RedditPreviewFixtures
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
 * What a text post with an image actually looks like on a card, for both kinds of image a text post
 * can have: one its own body embeds, which keeps its place in the body (the six r/test posts in
 * [InlineBodyImageFixtures]), and one Reddit built from a link in the body, which the post detail
 * draws above the selftext and so the card does too (the three r/copypasta posts in
 * [RedditPreviewFixtures]). Plus the block the post detail and the comments draw a body image in.
 *
 * [RoborazziLayoutTest] renders the same card with placeholder copy, which is the right tool for
 * "did this layout move". It cannot answer this one, because everything at stake here is decided per
 * post: which of the two snippet slots is used, whether the preview is squared, and whether a
 * squared preview is fitted or cropped. So these cases bind the parsed fixture and shape the image
 * through [PostCardPreviewStyle] and [MarkdownMediaSize] -- the same calls
 * `PostRecyclerViewAdapter` and `ImageAndGifEntry` make, rather than a second copy of their rules
 * that could agree with the golden while the app disagreed with both.
 *
 * The sample image carries a border and a marker in each corner, so a centre crop shows up in the
 * golden as missing corners rather than as something to take on trust.
 *
 * Recording and verifying these is [RoborazziLayoutTest]'s workflow, and the same two tasks.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
// Stock Application for the same reason as RoborazziLayoutTest: only a themed Activity is needed,
// and the real Infinity.onCreate installs a process-global EventBus that throws on reuse. ParsePost
// reads no context, so the fixtures parse without one.
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PostPreviewSnapshotTest(private val case: Case) {

    enum class Kind { CARD, LINK_PREVIEW_CARD, BODY_BLOCK }

    /** One parameterised capture. [goldenName] is both the test name and the PNG filename. */
    data class Case(
        val kind: Kind,
        /** A fixture name for either card kind; one of the body shapes for [Kind.BODY_BLOCK]. */
        val subject: String,
        val swDp: Int,
        val themeLabel: String,
        val themeType: Int,
        val fixedHeight: Boolean,
        val hideTextPostContent: Boolean,
    ) {
        /**
         * `{prefix}{Subject}_{theme}_sw{n}dp[_fixed][_hidetext]` -- the scheme the existing goldens
         * use, with a suffix per setting so a post's four combinations sort together.
         */
        val goldenName: String = buildString {
            append(
                when (kind) {
                    Kind.CARD -> "inlineCard"
                    Kind.LINK_PREVIEW_CARD -> "previewCard"
                    Kind.BODY_BLOCK -> "inlineBlock"
                },
            )
            append(subject.split('_').joinToString("") { it.replaceFirstChar(Char::uppercase) })
            append('_').append(themeLabel).append("_sw").append(swDp).append("dp")
            if (fixedHeight) append("_fixed")
            if (hideTextPostContent) append("_hidetext")
        }

        override fun toString(): String = goldenName
    }

    companion object {
        /** The distinct shapes the fixtures' body images come in, as width to height. */
        private val BODY_SHAPES = mapOf(
            "tall" to (1344 to 2992),
            "wide" to (2948 to 2020),
            "square" to (640 to 657),
        )

        /** Narrow phone, common phone, this dev's phone. A card is one column at every one of them. */
        private val CARD_WIDTHS = listOf(320, 411, 527)
        private val BLOCK_WIDTHS = listOf(320, 411)

        private const val LIGHT = "light"
        private const val DARK = "dark"

        private val SCREENSHOT_OPTIONS = RoborazziOptions(
            captureType = RoborazziOptions.CaptureType.Screenshot(),
        )

        /** Longest edge of the generated sample image, in pixels. */
        private const val SAMPLE_IMAGE_LONG_EDGE = 480

        /** Deterministic stand-ins, so a golden does not move with the clock or a vote count. */
        private const val SAMPLE_TIME = "3 hours ago"
        private const val SAMPLE_SCORE = "42"
        private const val SAMPLE_COMMENTS = "7"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = buildList {
            // Every post against both settings, at the widths a phone can be.
            val cards = InlineBodyImageFixtures.NAMES.map { Kind.CARD to it } +
                RedditPreviewFixtures.NAMES.map { Kind.LINK_PREVIEW_CARD to it }
            cards.forEach { (kind, fixture) ->
                CARD_WIDTHS.forEach { swDp ->
                    listOf(false, true).forEach { fixedHeight ->
                        listOf(false, true).forEach { hideText ->
                            add(
                                arrayOf<Any>(
                                    Case(
                                        kind, fixture, swDp, LIGHT,
                                        CustomThemeSharedPreferencesUtils.LIGHT, fixedHeight, hideText,
                                    ),
                                ),
                            )
                        }
                    }
                }
                // Dark is a palette swap, which cannot move a pixel boundary, so one width with the
                // settings at their defaults is enough to catch a colour regression.
                add(
                    arrayOf<Any>(
                        Case(
                            kind, fixture, 411, DARK,
                            CustomThemeSharedPreferencesUtils.DARK,
                            fixedHeight = false, hideTextPostContent = false,
                        ),
                    ),
                )
            }
            // The body block, which is what the post detail and the comments draw.
            BODY_SHAPES.keys.forEach { shape ->
                BLOCK_WIDTHS.forEach { swDp ->
                    listOf(false, true).forEach { fixedHeight ->
                        add(
                            arrayOf<Any>(
                                Case(
                                    Kind.BODY_BLOCK, shape, swDp, LIGHT,
                                    CustomThemeSharedPreferencesUtils.LIGHT, fixedHeight,
                                    hideTextPostContent = false,
                                ),
                            ),
                        )
                    }
                }
            }
        }

        /** The theme overlay BaseActivity would layer on, as in [RoborazziLayoutTest]. */
        @StyleRes
        private fun themeOverlay(themeType: Int): Int = when (themeType) {
            CustomThemeSharedPreferencesUtils.AMOLED -> R.style.Theme_Normal_AmoledDark
            CustomThemeSharedPreferencesUtils.DARK -> R.style.Theme_Normal_NormalDark
            else -> R.style.Theme_Normal
        }
    }

    private var pageBackground: Int = Color.WHITE

    @Before
    fun configureScreen() {
        RuntimeEnvironment.setQualifiers("+sw${case.swDp}dp-w${case.swDp}dp-h1600dp-port-xxhdpi")
    }

    @Test
    fun capture() {
        val view = render()
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
        bitmap.recycle()
    }

    private fun render(): View {
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

        val palette = Palette(case.themeType, activity.application)
        pageBackground = palette.pageBackground

        // One column: a card is single-column at every phone width these cases use.
        val itemWidthPx = activity.resources.displayMetrics.widthPixels
        val view = when (case.kind) {
            Kind.CARD, Kind.LINK_PREVIEW_CARD -> bindCard(activity, palette)
            Kind.BODY_BLOCK -> bindBodyBlock(activity, palette)
        }

        activity.setContentView(
            view,
            ViewGroup.LayoutParams(itemWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        controller.start().resume().visible()
        shadowOf(Looper.getMainLooper()).idle()

        view.measure(
            View.MeasureSpec.makeMeasureSpec(itemWidthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        return view
    }

    /** The feed card, bound the way `PostRecyclerViewAdapter` binds a text post with a preview. */
    private fun bindCard(activity: Activity, palette: Palette): View {
        val post = when (case.kind) {
            Kind.LINK_PREVIEW_CARD -> RedditPreviewFixtures.post(case.subject)
            else -> InlineBodyImageFixtures.post(case.subject)
        }
        val binding = ItemPostWithPreviewBinding.inflate(
            LayoutInflater.from(activity), FrameLayout(activity), false,
        )

        binding.subredditNameTextViewItemPostWithPreview.text = post.subredditNamePrefixed
        binding.userTextViewItemPostWithPreview.text = "u/${post.author}"
        binding.postTimeTextViewItemPostWithPreview.text = SAMPLE_TIME
        binding.titleTextViewItemPostWithPreview.text = post.title
        binding.typeTextViewItemPostWithPreview.text = activity.getString(R.string.text)
        binding.scoreTextViewItemPostWithPreview.text = SAMPLE_SCORE
        binding.commentsCountButtonItemPostWithPreview.text = SAMPLE_COMMENTS

        val preview: Post.Preview = post.previews[0]
        binding.imageWrapperRelativeLayoutItemPostWithPreview.visibility = View.VISIBLE
        binding.imageViewItemPostWithPreview.visibility = View.VISIBLE
        binding.imageViewItemPostWithPreview.setImageDrawable(
            sampleImage(activity, preview.previewWidth, preview.previewHeight),
        )
        PostCardPreviewStyle.applyPreviewShape(
            binding.imageViewItemPostWithPreview, preview, post, case.fixedHeight,
            autoplay = false, maxPreviewHeight = maxPreviewHeight(activity),
        )

        // "Hide Text Post Content" leaves both slots hidden, exactly as the adapter does; otherwise
        // each slot takes its half of the body, and a half the body does not have stays hidden.
        if (!case.hideTextPostContent) {
            bindSnippet(
                binding.contentTextViewItemPostWithPreview,
                PostCardPreviewStyle.snippetAbovePreview(post, hasBelowSlot = true),
            )
            bindSnippet(
                binding.contentTextViewBelowPreviewItemPostWithPreview,
                PostCardPreviewStyle.snippetBelowPreview(post, hasBelowSlot = true),
            )
        }

        applyPalette(binding.root, palette, skip = binding.imageViewItemPostWithPreview)
        return binding.root
    }

    private fun bindSnippet(slot: TextView, text: String?) {
        if (text.isNullOrEmpty()) {
            return
        }
        slot.text = text
        slot.visibility = View.VISIBLE
    }

    /** One image of a post body or a comment, as the markdown adapter draws it. */
    private fun bindBodyBlock(activity: Activity, palette: Palette): View {
        val (width, height) = BODY_SHAPES.getValue(case.subject)
        val binding = MarkdownImageAndGifBlockBinding.inflate(
            LayoutInflater.from(activity), FrameLayout(activity), false,
        )

        binding.imageViewMarkdownImageAndGifBlock.setImageDrawable(sampleImage(activity, width, height))
        MarkdownMediaSize.applyTo(
            binding.imageViewMarkdownImageAndGifBlock, width, height, case.fixedHeight,
            maxPreviewHeight(activity),
        )

        applyPalette(binding.root, palette, skip = binding.imageViewMarkdownImageAndGifBlock)
        return binding.root
    }

    /** Half the viewport, the ceiling both the feed and the body blocks cap a square at. */
    private fun maxPreviewHeight(activity: Activity): Int =
        activity.resources.displayMetrics.heightPixels / 2

    /**
     * Infinity colours its views during binding rather than from the layout XML, so an inflated item
     * has no colours of its own. Only the palette is applied here -- the content is bound above, per
     * post -- and the preview image is left alone, since it is the subject.
     */
    private fun applyPalette(view: View, palette: Palette, skip: View) {
        if (view === skip) {
            return
        }
        // The Material loading indicator animates, so a capture that included it would land on a
        // different frame every run. The body block ships its indicator visible (the card ships
        // its own gone), so hide it -- INVISIBLE, not GONE, because it sits in the same frame as
        // the image and removing it from layout is a change this capture has no business making.
        if (view is LoadingIndicator && view.visibility == View.VISIBLE) {
            view.visibility = View.INVISIBLE
        }
        when (view) {
            is MaterialCardView -> view.setCardBackgroundColor(palette.cardBackground)
            is MaterialButton -> {
                view.setTextColor(palette.textColor)
                view.iconTint = ColorStateList.valueOf(palette.iconColor)
            }
            is TextView -> view.setTextColor(palette.textColor)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) applyPalette(view.getChildAt(i), palette, skip)
        }
    }

    /**
     * A stand-in for the post's image at that image's real aspect ratio, with a border and a marker
     * in each corner: a centre crop loses the corners, so the golden shows the difference between
     * fitting the image inside its square and cropping it to fill.
     */
    private fun sampleImage(activity: Activity, srcWidth: Int, srcHeight: Int): Drawable {
        val longEdge = maxOf(srcWidth, srcHeight, 1)
        val width = (SAMPLE_IMAGE_LONG_EDGE.toLong() * srcWidth / longEdge).toInt().coerceAtLeast(1)
        val height = (SAMPLE_IMAGE_LONG_EDGE.toLong() * srcHeight / longEdge).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(
                Color.rgb(0x3F, 0x51, 0xB5),
                Color.rgb(0x00, 0xBC, 0xD4),
                Color.rgb(0xFF, 0x98, 0x00),
            ),
            null, Shader.TileMode.CLAMP,
        )
        canvas.drawPaint(paint)

        paint.shader = null
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        canvas.drawRect(6f, 6f, width - 6f, height - 6f, paint)

        paint.style = Paint.Style.FILL
        val marker = minOf(width, height) * 0.12f
        listOf(
            marker to marker,
            width - marker to marker,
            marker to height - marker,
            width - marker to height - marker,
        ).forEach { (x, y) -> canvas.drawCircle(x, y, marker * 0.5f, paint) }

        return BitmapDrawable(activity.resources, bitmap)
    }

    /** The real default palette for a theme type, read the way the app reads it. */
    private class Palette(themeType: Int, app: Application) {
        private val wrapper = CustomThemeWrapper(
            app.getSharedPreferences("light_theme_test", 0),
            app.getSharedPreferences("dark_theme_test", 0),
            app.getSharedPreferences("amoled_theme_test", 0),
        ).apply { setThemeType(themeType) }

        val pageBackground: Int = wrapper.backgroundColor
        val cardBackground: Int = wrapper.cardViewBackgroundColor
        val textColor: Int = wrapper.primaryTextColor
        val iconColor: Int = wrapper.postIconAndInfoColor
    }
}
