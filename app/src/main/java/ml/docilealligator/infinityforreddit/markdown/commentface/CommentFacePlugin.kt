package ml.docilealligator.infinityforreddit.markdown.commentface

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.core.CoreProps
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.AsyncDrawableLoader
import io.noties.markwon.image.AsyncDrawableScheduler
import io.noties.markwon.image.AsyncDrawableSpan
import io.noties.markwon.image.ImageSize
import io.noties.markwon.image.ImageSizeResolver
import kotlin.math.max
import kotlin.math.roundToInt
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.customviews.SpoilerOnClickTextView
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.CustomNode
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.parser.PostProcessor

/**
 * Draws [CommentFaceNode]s, and removes the links that draw nothing.
 *
 * A face is one replacement character carrying an [AsyncDrawableSpan] whose box is the face's size
 * from the start: the placeholder is an empty box of that size, so the text around it is laid out
 * once and does not move when the sprite arrives. The link's text is drawn over the box as its
 * caption. Tapping a face that links to a path follows the link, as on old Reddit; tapping a `#`
 * face shows its link title, which old Reddit showed on hover.
 *
 * An empty link that is not a face — `[](#face)` in a subreddit with no such face, or with faces
 * turned off — renders nothing either way, but left in place it still made its paragraph a blank
 * line under the comment. The parser's post-processing step takes it out, and the paragraph with it
 * when nothing else is left.
 */
class CommentFacePlugin internal constructor(
    requestManager: RequestManager?,
    resources: Resources,
) : AbstractMarkwonPlugin() {

    private val density = resources.displayMetrics.density
    private val loader = CommentFaceLoader(requestManager, resources)
    private val sizeResolver = CommentFaceSizeResolver()

    override fun configureParser(builder: Parser.Builder) {
        builder.postProcessor(EmptyLinkRemover)
    }

    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        builder.on(CommentFaceNode::class.java,
                MarkwonVisitor.NodeVisitor<CommentFaceNode> { visitor, node -> visit(visitor, node) })
    }

    private fun visit(visitor: MarkwonVisitor, node: CommentFaceNode) {
        val face = node.face
        if (face.hidden) {
            return
        }
        val start = visitor.length()
        visitor.builder().append('\uFFFC')
        val theme = visitor.configuration().theme()
        val linkPaint = TextPaint()
        theme.applyLinkStyle(linkPaint)
        val drawable = CommentFaceDrawable(
            face, CommentFaceCaption.of(node, face.caption), linkPaint.color, loader, sizeResolver,
            ImageSize(
                ImageSize.Dimension(face.boxWidth * density, "px"),
                ImageSize.Dimension(face.boxHeight * density, "px")
            )
        )
        val alignment = when (face.verticalAlign) {
            CommentFace.VerticalAlign.MIDDLE -> AsyncDrawableSpan.ALIGN_CENTER
            CommentFace.VerticalAlign.BOTTOM -> AsyncDrawableSpan.ALIGN_BOTTOM
            CommentFace.VerticalAlign.BASELINE -> AsyncDrawableSpan.ALIGN_BASELINE
        }
        visitor.setSpans(start, AsyncDrawableSpan(theme, drawable, alignment, false))
        val title = node.title
        if (node.destination.startsWith("/")) {
            // A path is a real link on old Reddit too — r/hockey's team logos go to the team's
            // subreddit — so tapping the face follows it. A # face goes nowhere.
            CoreProps.LINK_DESTINATION.set(visitor.renderProps(), node.destination)
            visitor.setSpansForNodeOptional(Link(node.destination, title), start)
        } else if (!title.isNullOrBlank()) {
            visitor.setSpans(start, CommentFaceTitleSpan(title))
        }
    }

    override fun beforeSetText(textView: TextView, markdown: Spanned) {
        AsyncDrawableScheduler.unschedule(textView)
    }

    override fun afterSetText(textView: TextView) {
        AsyncDrawableScheduler.schedule(textView)
        rescheduleOnReattach(textView)
    }

    /**
     * Markwon cancels a view's images when it leaves the window and never starts them again, so a
     * row that scrolled off and came back without being rebound would show its faces as empty
     * boxes. The face's bitmap goes back to Glide on cancel (see [CommentFaceLoader.cancel]), so
     * this brings it back — from memory, normally — whenever the view returns.
     */
    private fun rescheduleOnReattach(textView: TextView) {
        if (textView.getTag(R.id.comment_face_reattach_listener) != null) {
            return
        }
        val text = textView.text as? Spanned ?: return
        if (text.getSpans(0, text.length, AsyncDrawableSpan::class.java).none { it.drawable is CommentFaceDrawable }) {
            return
        }
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                AsyncDrawableScheduler.schedule(textView)
            }

            override fun onViewDetachedFromWindow(view: View) {}
        }
        textView.addOnAttachStateChangeListener(listener)
        textView.setTag(R.id.comment_face_reattach_listener, listener)
    }
}

/**
 * A face's box: an [AsyncDrawable] that shows an empty box of the face's size until the cut-out
 * sprite arrives, with the caption drawn on top either way.
 */
internal class CommentFaceDrawable(
    val face: CommentFace,
    private val caption: CommentFaceCaption?,
    private val linkColor: Int,
    loader: AsyncDrawableLoader,
    sizeResolver: ImageSizeResolver,
    imageSize: ImageSize,
) : AsyncDrawable(face.imageUrl, loader, sizeResolver, imageSize) {

    /** The loader's placeholder, which AsyncDrawable installs as the result while constructing. */
    private val placeholderResult: Drawable? = result

    /** Puts the empty box back, so the next attach loads the face again instead of reusing a released bitmap. */
    fun showPlaceholder() {
        val placeholder = placeholderResult ?: return
        if (result !== placeholder) {
            setResult(placeholder)
        }
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        caption?.draw(canvas, bounds, face, linkColor)
    }
}

/** Sizes a face to its CSS size in dp, shrunk proportionally only if it is wider than the text. */
private class CommentFaceSizeResolver : ImageSizeResolver() {
    override fun resolveImageSize(drawable: AsyncDrawable): Rect {
        val size = drawable.imageSize
        var width = size?.width?.value ?: 0f
        var height = size?.height?.value ?: 0f
        val canvasWidth = drawable.lastKnownCanvasWidth
        if (canvasWidth in 1 until width.roundToInt()) {
            height = height * canvasWidth / width
            width = canvasWidth.toFloat()
        }
        return Rect(0, 0, max(1, width.roundToInt()), max(1, height.roundToInt()))
    }
}

/**
 * Loads a face's sprite through Glide, cut down to the face by [CommentFaceBackgroundTransformation].
 *
 * The sprite is decoded at its own size — downsampling would move every face in it — and never in
 * hardware, which the transformation cannot read. The target is kept until [cancel], so Glide gets
 * the bitmap back when the view lets go of it rather than when the screen is destroyed.
 *
 * Without a [requestManager] (the screen was already finishing) nothing loads, but faces still get
 * their empty, correctly sized box.
 */
private class CommentFaceLoader(
    private val requestManager: RequestManager?,
    private val resources: Resources,
) : AsyncDrawableLoader() {

    private val targets = HashMap<AsyncDrawable, FaceTarget>()

    override fun load(drawable: AsyncDrawable) {
        val requestManager = requestManager ?: return
        if (drawable !is CommentFaceDrawable) {
            return
        }
        val target = FaceTarget(drawable)
        targets.put(drawable, target)?.let { requestManager.clear(it) }
        requestManager.asBitmap()
            .load(drawable.face.imageUrl)
            .override(Target.SIZE_ORIGINAL)
            .downsample(DownsampleStrategy.NONE)
            .disallowHardwareConfig()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .transform(CommentFaceBackgroundTransformation(drawable.face))
            .into(target)
    }

    override fun cancel(drawable: AsyncDrawable) {
        val target = targets.remove(drawable)
        (drawable as? CommentFaceDrawable)?.showPlaceholder()
        if (target != null) {
            requestManager?.clear(target)
        }
    }

    /** An empty box already the face's size, so the line is laid out for the face before it loads. */
    override fun placeholder(drawable: AsyncDrawable): Drawable? {
        val size = drawable.imageSize ?: return null
        val width = size.width?.value ?: return null
        val height = size.height?.value ?: return null
        val placeholder = ColorDrawable(Color.TRANSPARENT)
        placeholder.setBounds(0, 0, max(1, width.roundToInt()), max(1, height.roundToInt()))
        return placeholder
    }

    private inner class FaceTarget(private val drawable: CommentFaceDrawable) : CustomTarget<Bitmap>() {
        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
            if (targets[drawable] !== this || !drawable.isAttached) {
                return
            }
            val result = BitmapDrawable(resources, resource)
            result.alpha = (drawable.face.opacity * 255f).roundToInt()
            // Given the box's bounds up front so AsyncDrawable does not fall back to the bitmap's
            // own size, and relayout the line, when this arrives before the first draw.
            result.bounds = drawable.bounds
            drawable.setResult(result)
        }

        override fun onLoadCleared(placeholder: Drawable?) {}

        override fun onLoadFailed(errorDrawable: Drawable?) {
            if (targets[drawable] === this) {
                targets.remove(drawable)
            }
        }
    }
}

/**
 * A face link's text, laid out over the face. Text in `**strong**` goes where the stylesheet puts
 * a `<strong>` inside the link, which on r/anime and its imitators is pinned to the bottom edge.
 */
internal class CommentFaceCaption private constructor(
    private val style: CommentFaceCaptionStyle,
    /** Text laid out from the top of the content box. */
    val flow: CharSequence?,
    /** Text pinned to an edge, from a `<strong>` the stylesheet positions absolutely. */
    val anchored: CharSequence?,
) {
    private val flowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val anchoredPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var layoutScale = -1f
    private var flowLayout: StaticLayout? = null
    private var anchoredLayout: StaticLayout? = null

    fun draw(canvas: Canvas, bounds: Rect, face: CommentFace, linkColor: Int) {
        if (bounds.isEmpty) {
            return
        }
        val scale = bounds.width() / face.boxWidth
        if (scale != layoutScale) {
            buildLayouts(scale, face)
            layoutScale = scale
        }
        val fill = style.color ?: linkColor
        val saved = canvas.save()
        canvas.clipRect(bounds)
        flowLayout?.let {
            canvas.save()
            canvas.translate(bounds.left + face.paddingLeft * scale, bounds.top + face.paddingTop * scale)
            drawOutlined(canvas, it, flowPaint, fill, scale)
            canvas.restore()
        }
        anchoredLayout?.let {
            val strong = style.strong
            val y = if (strong.anchor == CommentFaceStrongStyle.Anchor.BOTTOM) {
                bounds.bottom - strong.offset * scale - it.height
            } else {
                bounds.top + strong.offset * scale
            }
            canvas.save()
            canvas.translate(bounds.left.toFloat(), y)
            drawOutlined(canvas, it, anchoredPaint, strong.color ?: fill, scale)
            canvas.restore()
        }
        canvas.restoreToCount(saved)
    }

    private fun buildLayouts(scale: Float, face: CommentFace) {
        flowPaint.textSize = style.fontSize * scale
        flowPaint.typeface = Typeface.create(Typeface.SANS_SERIF, if (style.bold) Typeface.BOLD else Typeface.NORMAL)
        anchoredPaint.textSize = style.strong.fontSize * scale
        anchoredPaint.typeface =
                Typeface.create(Typeface.SANS_SERIF, if (style.strong.bold) Typeface.BOLD else Typeface.NORMAL)
        val contentWidth = max(1, ((face.boxWidth - face.paddingLeft - face.paddingRight) * scale).toInt())
        // An absolutely positioned <strong> spans the padding box, not the content box.
        val boxWidth = max(1, (face.boxWidth * scale).toInt())
        flowLayout = flow?.let { layout(it, flowPaint, contentWidth) }
        anchoredLayout = anchored?.let { layout(it, anchoredPaint, boxWidth) }
    }

    private fun layout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout {
        val alignment = when (style.align) {
            CommentFaceCaptionStyle.Align.LEFT -> Layout.Alignment.ALIGN_NORMAL
            CommentFaceCaptionStyle.Align.CENTER -> Layout.Alignment.ALIGN_CENTER
            CommentFaceCaptionStyle.Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment)
            .setIncludePad(false)
            .build()
    }

    /** Draws [layout] filled with [fill], over an outline when the stylesheet gives the text a shadow. */
    private fun drawOutlined(canvas: Canvas, layout: StaticLayout, paint: TextPaint, fill: Int, scale: Float) {
        val outline = style.outline
        if (outline != null) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * scale
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = outline.color ?: fill
            layout.draw(canvas)
        }
        paint.style = Paint.Style.FILL
        paint.color = fill
        layout.draw(canvas)
    }

    companion object {
        /** The caption for [node]'s text, or null when it has none or the stylesheet hides it. */
        fun of(node: CommentFaceNode, style: CommentFaceCaptionStyle?): CommentFaceCaption? {
            if (style == null) {
                return null
            }
            val strong = style.strong
            val anchoredStrong = strong.anchor != CommentFaceStrongStyle.Anchor.FLOW
            val flow = SpannableStringBuilder()
            val anchored = StringBuilder()

            fun append(text: String, inStrong: Boolean) {
                when {
                    !inStrong -> flow.append(text)
                    !strong.visible -> {}
                    anchoredStrong -> anchored.append(text)
                    else -> {
                        val start = flow.length
                        flow.append(text)
                        if (strong.bold && !style.bold) {
                            flow.setSpan(StyleSpan(Typeface.BOLD), start, flow.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                }
            }

            fun collect(parent: Node, inStrong: Boolean) {
                var child = parent.firstChild
                while (child != null) {
                    when (child) {
                        is Text -> append(child.literal, inStrong)
                        is Code -> append(child.literal, inStrong)
                        is SoftLineBreak -> append(" ", inStrong)
                        is HardLineBreak -> append("\n", inStrong)
                        is StrongEmphasis -> collect(child, true)
                        else -> collect(child, inStrong)
                    }
                    child = child.next
                }
            }

            collect(node, false)
            val flowText = flow.takeIf { it.isNotBlank() }
            val anchoredText = anchored.toString().takeIf { it.isNotBlank() }
            if (flowText == null && anchoredText == null) {
                return null
            }
            return CommentFaceCaption(style, flowText, anchoredText)
        }
    }
}

/** A face's link title, shown when the face is tapped; old Reddit showed it on hover. */
internal class CommentFaceTitleSpan(private val title: String) : ClickableSpan() {
    override fun onClick(widget: View) {
        Toast.makeText(widget.context, title, Toast.LENGTH_LONG).show()
        // The tap was this span's, as a spoiler's is: the comment around it must not also take
        // it as a tap on itself and collapse its replies.
        (widget as? SpoilerOnClickTextView)?.isSpoilerOnClick = true
    }

    // The face draws itself; there is no text here to colour or underline.
    override fun updateDrawState(ds: TextPaint) {}
}

/**
 * Takes out links that render nothing — empty ones, and faces the stylesheet hides — and the
 * paragraph around one when nothing else is left in it, so they do not leave a blank line behind.
 */
internal object EmptyLinkRemover : PostProcessor {
    override fun process(node: Node): Node {
        val doomed = ArrayList<Node>()
        node.accept(object : AbstractVisitor() {
            override fun visit(link: Link) {
                if (link.firstChild == null) {
                    doomed.add(link)
                } else {
                    visitChildren(link)
                }
            }

            override fun visit(customNode: CustomNode) {
                if (customNode is CommentFaceNode) {
                    if (customNode.face.hidden) {
                        doomed.add(customNode)
                    }
                } else {
                    visitChildren(customNode)
                }
            }
        })
        for (gone in doomed) {
            val parent = gone.parent
            gone.unlink()
            if (parent is Paragraph) {
                tidy(parent)
            }
        }
        return node
    }

    private fun tidy(paragraph: Paragraph) {
        while (paragraph.firstChild?.let { isBlank(it) } == true) {
            paragraph.firstChild.unlink()
        }
        while (paragraph.lastChild?.let { isBlank(it) } == true) {
            paragraph.lastChild.unlink()
        }
        if (paragraph.firstChild == null) {
            paragraph.unlink()
        }
    }

    private fun isBlank(node: Node): Boolean =
        node is SoftLineBreak || node is HardLineBreak || (node is Text && node.literal.isBlank())
}
