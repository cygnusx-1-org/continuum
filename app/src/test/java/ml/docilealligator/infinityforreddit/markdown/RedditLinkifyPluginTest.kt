package ml.docilealligator.infinityforreddit.markdown

import android.content.Context
import android.text.Spanned
import androidx.test.core.app.ApplicationProvider
import io.noties.markwon.Markwon
import io.noties.markwon.core.spans.LinkSpan
import io.noties.markwon.inlineparser.BangInlineProcessor
import io.noties.markwon.inlineparser.CloseBracketInlineProcessor
import io.noties.markwon.inlineparser.HtmlInlineProcessor
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.markdown.emote.EmoteCloseBracketInlineProcessor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Bare URLs rendered through Markwon with [RedditLinkifyPlugin] (issue #480): Android's real `Linkify`
 * finds them, and each link ends where Reddit ends it. The pure end rule is pinned by
 * [RedditUrlEndTest]; this checks the spans the app actually draws and the URL a tap opens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class RedditLinkifyPluginTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // The inline parser set up as MarkdownUtils sets it up, so text nodes split where the app's do.
    private val markwon: Markwon = Markwon.builder(context)
        .usePlugin(MarkwonInlineParserPlugin.create { builder ->
            builder.excludeInlineProcessor(HtmlInlineProcessor::class.java)
            builder.excludeInlineProcessor(BangInlineProcessor::class.java)
            builder.excludeInlineProcessor(CloseBracketInlineProcessor::class.java)
            builder.addInlineProcessor(EmoteCloseBracketInlineProcessor())
        })
        .usePlugin(RedditLinkifyPlugin.create())
        .build()

    /** Every link in the rendering of [markdown], as `linked text -> destination`. */
    private fun links(markdown: String): List<String> {
        val rendered = markwon.toMarkdown(markdown)
        return rendered.getSpans(0, rendered.length, LinkSpan::class.java)
            .sortedBy { rendered.getSpanStart(it) }
            .map { "${rendered.subSequence(rendered.getSpanStart(it), rendered.getSpanEnd(it))} -> ${it.link}" }
    }

    private fun rendered(markdown: String): Spanned = markwon.toMarkdown(markdown)

    @Test
    fun `the issue's share link ends before the closing parenthesis`() {
        // The post's selftext, verbatim.
        val markdown = "\\[Previous post\\] (https://www.reddit.com/r/BoyDinnerDiaries/s/MsFP5ijHqA)"
        val url = "https://www.reddit.com/r/BoyDinnerDiaries/s/MsFP5ijHqA"
        assertEquals(listOf("$url -> $url"), links(markdown))
        assertEquals("[Previous post] ($url)", rendered(markdown).toString().trim())
    }

    @Test
    fun `a sentence's closing punctuation is not linked`() {
        assertEquals(listOf("https://example.com/a -> https://example.com/a"), links("See https://example.com/a."))
        assertEquals(listOf("https://example.com/a -> https://example.com/a"), links("(See https://example.com/a)."))
    }

    @Test
    fun `parentheses the URL opens itself are linked even mid-sentence`() {
        val url = "https://en.wikipedia.org/wiki/Foo_(bar)"
        assertEquals(listOf("$url -> $url"), links("$url is a page"))
        assertEquals(listOf("$url -> $url"), links("($url)"))
    }

    @Test
    fun `the destination keeps Linkify's scheme`() {
        assertEquals(listOf("example.com/a -> http://example.com/a"), links("(example.com/a)"))
        assertEquals(listOf("HTTPS://Example.com/a -> https://Example.com/a"), links("HTTPS://Example.com/a."))
    }

    @Test
    fun `every URL in a paragraph is trimmed, wherever the paragraph puts it`() {
        assertEquals(
            listOf("https://a.com/x -> https://a.com/x", "https://b.com/y -> https://b.com/y"),
            links("**First** (https://a.com/x), then *second* https://b.com/y.")
        )
    }

    @Test
    fun `a written markdown link is left alone`() {
        assertEquals(listOf("text -> https://example.com/a"), links("[text](https://example.com/a)."))
    }
}
