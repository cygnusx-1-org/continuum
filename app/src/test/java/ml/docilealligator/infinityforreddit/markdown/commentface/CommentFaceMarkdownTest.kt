package ml.docilealligator.infinityforreddit.markdown.commentface

import android.content.Context
import android.graphics.Rect
import android.text.Spanned
import androidx.test.core.app.ApplicationProvider
import io.noties.markwon.Markwon
import io.noties.markwon.core.spans.LinkSpan
import io.noties.markwon.image.AsyncDrawableSpan
import io.noties.markwon.inlineparser.BangInlineProcessor
import io.noties.markwon.inlineparser.CloseBracketInlineProcessor
import io.noties.markwon.inlineparser.HtmlInlineProcessor
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import kotlin.math.roundToInt
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.markdown.emote.EmoteCloseBracketInlineProcessor
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comment faces in the markdown pipeline (issue #432): the inline parser turns face links into
 * [CommentFaceNode]s through the bound [CommentFaceLookup], and [CommentFacePlugin] removes the
 * links that draw nothing and renders the rest as a box of the face's size.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class CommentFaceMarkdownTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val processor = EmoteCloseBracketInlineProcessor()
    private val markwon: Markwon = Markwon.builder(context)
        .usePlugin(MarkwonInlineParserPlugin.create { builder ->
            builder.excludeInlineProcessor(HtmlInlineProcessor::class.java)
            builder.excludeInlineProcessor(BangInlineProcessor::class.java)
            builder.excludeInlineProcessor(CloseBracketInlineProcessor::class.java)
            builder.addInlineProcessor(processor)
        })
        .usePlugin(CommentFacePlugin(null, context.resources))
        .build()

    private val bottomCaption = CommentFaceCaptionStyle(
        18f, 0xFFFFFFFF.toInt(), CommentFaceCaptionStyle.Outline(0xFF000000.toInt()), false,
        CommentFaceCaptionStyle.Align.CENTER,
        CommentFaceStrongStyle(CommentFaceStrongStyle.Anchor.BOTTOM, 4f, true, false, 18f, null)
    )
    private val face = CommentFace(
        "https://b.thumbs.redditmedia.com/sprite.png", 129f, 121f, 0f, 0f, 0f, 0f,
        CommentFaceBackground(CssLength(140f, 0f), CssLength.ZERO, CommentFaceBackground.SizeMode.EXPLICIT,
            null, null, repeatX = true, repeatY = true, color = null),
        1f, bottomCaption, CommentFace.VerticalAlign.BASELINE
    )

    private class Call(val destination: String, val title: String?, val hasText: Boolean)

    private val calls = ArrayList<Call>()

    /** Binds a lookup that knows [faces] and records every link it is asked about. */
    private fun bind(faces: Map<String, CommentFace>) {
        processor.setCommentFaceLookup { destination, title, hasText ->
            calls.add(Call(destination, title, hasText))
            faces[destination]
        }
    }

    private fun children(node: Node): List<Node> {
        val out = ArrayList<Node>()
        var child = node.firstChild
        while (child != null) {
            out.add(child)
            child = child.next
        }
        return out
    }

    private fun faceNode(document: Node): CommentFaceNode {
        val paragraph = children(document).single() as Paragraph
        return children(paragraph).single() as CommentFaceNode
    }

    @Test
    fun `an empty face link becomes a face`() {
        bind(mapOf("#schemingsaten" to face))
        val node = faceNode(markwon.parse("[](#schemingsaten)"))
        assertEquals(face, node.face)
        assertEquals("#schemingsaten", node.destination)
        assertNull(node.title)
        assertEquals(false, calls.single().hasText)
    }

    @Test
    fun `the link title comes with the face`() {
        bind(mapOf("#bonk" to face))
        assertEquals("Hugh Laurie", faceNode(markwon.parse("[](#bonk \"Hugh Laurie\")")).title)
    }

    @Test
    fun `a captioned face keeps its text as children`() {
        bind(mapOf("#bonk" to face))
        val node = faceNode(markwon.parse("[**bonk**](#bonk)"))
        assertTrue(calls.single().hasText)
        val strong = children(node).single() as StrongEmphasis
        assertEquals("bonk", (strong.firstChild as Text).literal)
    }

    @Test
    fun `strong caption text goes to the anchored line, the rest flows from the top`() {
        bind(mapOf("#bonk" to face))
        val node = faceNode(markwon.parse("[top **bottom**](#bonk)"))
        val caption = checkNotNull(CommentFaceCaption.of(node, bottomCaption))
        assertEquals("top ", caption.flow.toString())
        assertEquals("bottom", caption.anchored.toString())
    }

    @Test
    fun `a face whose stylesheet hides the text has no caption`() {
        bind(mapOf("#bonk" to face))
        val node = faceNode(markwon.parse("[**bonk**](#bonk)"))
        assertNull(CommentFaceCaption.of(node, null))
    }

    @Test
    fun `an unresolved empty face link leaves no blank paragraph behind`() {
        // The issue's first comment: before, the empty link kept its paragraph, which the comment
        // drew as a blank line under the text.
        bind(emptyMap())
        val document = markwon.parse("Do they know who Holmes is\n\n[](#schemingsaten)")
        val paragraph = children(document).single() as Paragraph
        assertEquals("Do they know who Holmes is", (paragraph.firstChild as Text).literal)
        assertEquals(1, children(paragraph).size)
    }

    @Test
    fun `an empty link with faces off is removed the same way`() {
        processor.setCommentFaceLookup(null)
        val document = markwon.parse("Text\n[](#schemingsaten)")
        val paragraph = children(document).single() as Paragraph
        assertEquals(listOf("Text"), children(paragraph).map { (it as Text).literal })
    }

    @Test
    fun `a hidden face goes with its text`() {
        bind(mapOf("#country-argentina" to CommentFace.HIDDEN))
        val document = markwon.parse("Before\n\n[Argentina](#country-argentina)")
        assertEquals(1, children(document).size)
    }

    @Test
    fun `a link that is not a face stays a link`() {
        bind(emptyMap())
        val paragraph = children(markwon.parse("[r/anime](/r/anime)")).single() as Paragraph
        val link = children(paragraph).single() as Link
        assertEquals("/r/anime", link.destination)
        assertEquals(true, calls.single().hasText)
    }

    @Test
    fun `a face renders as one character holding a box the face's size`() {
        bind(mapOf("#schemingsaten" to face))
        val rendered = markwon.render(markwon.parse("[](#schemingsaten \"title\")")) as Spanned
        assertEquals("￼", rendered.toString().trim())
        val span = rendered.getSpans(0, rendered.length, AsyncDrawableSpan::class.java).single()
        val drawable = span.drawable as CommentFaceDrawable
        // Sized before anything loads, so the line never changes height when the sprite arrives.
        val density = context.resources.displayMetrics.density
        assertEquals(Rect(0, 0, (129f * density).roundToInt(), (121f * density).roundToInt()), drawable.bounds)
        assertEquals(1, rendered.getSpans(0, rendered.length, CommentFaceTitleSpan::class.java).size)
    }

    @Test
    fun `a face that links to a path is still a link`() {
        // r/nba's sidebar: `[](/BOS)` is the Celtics logo, and still a link.
        bind(mapOf("/BOS" to face))
        val rendered = markwon.render(markwon.parse("[](/BOS \"Boston\")")) as Spanned
        val link = rendered.getSpans(0, rendered.length, LinkSpan::class.java).single()
        assertEquals("/BOS", link.link)
        assertEquals(0, rendered.getSpans(0, rendered.length, CommentFaceTitleSpan::class.java).size)
    }

    @Test
    fun `a face without a title is not tappable`() {
        bind(mapOf("#schemingsaten" to face))
        val rendered = markwon.render(markwon.parse("[](#schemingsaten)")) as Spanned
        assertEquals(0, rendered.getSpans(0, rendered.length, CommentFaceTitleSpan::class.java).size)
    }
}
