package ml.docilealligator.infinityforreddit.markdown

import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import android.text.util.Linkify
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonPlugin
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.SpannableBuilder
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.CoreProps
import org.commonmark.node.Link

/**
 * Links bare web URLs in markdown text, ending each one where Reddit does (issue #480). Stands in for
 * Markwon's `LinkifyPlugin.create(Linkify.WEB_URLS)`.
 *
 * Android's [Linkify] still decides which text is a URL; only the end comes from [RedditUrlEnd].
 * Linkify's pattern (`Patterns.AUTOLINK_WEB_URL`) takes `(`, `)`, `.`, `,`, `!` and `?` as ordinary
 * URL characters and lets a URL end only at a word boundary or the end of the text, so what it keeps
 * depends on the character after the URL rather than on the URL itself:
 *  * `(https://example.com/a)` ending a paragraph linked `https://example.com/a)`, though followed by
 *    a space it linked `https://example.com/a`;
 *  * `see https://example.com/a.` ending a paragraph linked the period;
 *  * `https://en.wikipedia.org/wiki/Foo_(bar) is` linked `…/Foo_(bar`.
 * A share link that picked up a `)` stopped matching `LinkResolverActivity`'s share-link pattern and
 * went to the browser, where Reddit redirects the unknown share id to the subreddit.
 */
class RedditLinkifyPlugin private constructor() : AbstractMarkwonPlugin() {

    override fun configure(registry: MarkwonPlugin.Registry) {
        registry.require(CorePlugin::class.java).addOnTextAddedListener(TextAddedListener)
    }

    private object TextAddedListener : CorePlugin.OnTextAddedListener {
        override fun onTextAdded(visitor: MarkwonVisitor, text: String, start: Int) {
            // The factory a markdown [label](url) is rendered with, so a bare URL looks and taps the
            // same as a written one.
            val spanFactory = visitor.configuration().spansFactory().get(Link::class.java) ?: return
            val linkified = SpannableStringBuilder(text)
            if (!Linkify.addLinks(linkified, Linkify.WEB_URLS)) {
                return
            }

            val spans = linkified.getSpans(0, linkified.length, URLSpan::class.java)
                .sortedBy { linkified.getSpanStart(it) }
            var previousEnd = 0
            for (span in spans) {
                val urlStart = linkified.getSpanStart(span)
                // Running to whitespace can take in a second URL Linkify found in the same run of
                // text, as snudown's scan does; it is already part of the previous link.
                if (urlStart < previousEnd) {
                    continue
                }
                val linkifyEnd = linkified.getSpanEnd(span)
                val urlEnd = RedditUrlEnd.end(text, urlStart, linkifyEnd)
                if (urlEnd <= urlStart) {
                    continue
                }

                val renderProps = visitor.renderProps()
                CoreProps.LINK_DESTINATION.set(renderProps, RedditUrlEnd.resize(span.url, text, linkifyEnd, urlEnd))
                SpannableBuilder.setSpans(
                    visitor.builder(),
                    spanFactory.getSpans(visitor.configuration(), renderProps),
                    start + urlStart,
                    start + urlEnd
                )
                previousEnd = urlEnd
            }
        }
    }

    companion object {
        @JvmStatic
        fun create(): RedditLinkifyPlugin = RedditLinkifyPlugin()
    }
}
