package ml.docilealligator.infinityforreddit.markdown

import io.noties.markwon.AbstractMarkwonPlugin
import org.commonmark.internal.ListBlockParser
import org.commonmark.internal.ThematicBreakParser
import org.commonmark.internal.util.Parsing
import org.commonmark.node.ListBlock
import org.commonmark.parser.Parser
import org.commonmark.parser.block.AbstractBlockParserFactory
import org.commonmark.parser.block.BlockStart
import org.commonmark.parser.block.MatchedBlockParser
import org.commonmark.parser.block.ParserState

/**
 * Parses list items the way Reddit does: a marker with nothing after it — `2.`, `404.`, `-` — is
 * text, not an empty list item (issue #430).
 *
 * CommonMark allows an empty item and Markwon renders one as nothing at all, so a comment reading
 * just `404.` came out blank. Snudown only starts an item when the marker is followed by a space,
 * and reddit.com and the official app both show `404.` as plain text. A
 * marker followed by nothing but whitespace is text here too: snudown would emit an empty `<li>` for
 * that, which a browser at least draws a number for, and rendering nothing is never right.
 *
 * Add this plugin after every other one. Its factory stands in for commonmark's list parser, which
 * runs after all custom factories; registered any earlier, it would claim `1. ![img](…)` as a list
 * item before `ImageAndGifPlugin` and the other media plugins see the line, and a media block nested
 * in a list is not rendered.
 */
class RedditListPlugin : AbstractMarkwonPlugin() {
    override fun configureParser(builder: Parser.Builder) {
        builder.customBlockParserFactory(RedditListBlockParserFactory())
        CoreBlockTypes.disable(builder, ListBlock::class.java)
    }
}

/**
 * commonmark's own list factory, minus empty items. It only ever returns what that factory would, or
 * nothing, so every list with content parses exactly as before.
 *
 * Being a custom factory, it is tried ahead of all of commonmark's core factories, where the list
 * parser used to come after the thematic break one; so it defers to that explicitly (`* * *` is a
 * rule, not an item). The other core parsers that outrank lists can't collide with it: ATX headings,
 * fences, HTML and block quotes never start with a list marker, and the only setext underline that
 * is also a marker is a lone `-`, which is an empty item and therefore already declined.
 */
internal class RedditListBlockParserFactory : AbstractBlockParserFactory() {
    private val lists = ListBlockParser.Factory()
    private val thematicBreaks = ThematicBreakParser.Factory()

    override fun tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): BlockStart? {
        if (isEmptyItem(state.line, state.nextNonSpaceIndex) ||
            thematicBreaks.tryStart(state, matchedBlockParser) != null
        ) {
            return BlockStart.none()
        }
        return lists.tryStart(state, matchedBlockParser)
    }

    /**
     * Whether [line] holds a list marker — `-`, `+`, `*`, or 1–9 digits then `.` or `)` — at
     * [markerIndex] and nothing after it but spaces or tabs.
     */
    private fun isEmptyItem(line: CharSequence, markerIndex: Int): Boolean {
        if (markerIndex >= line.length) {
            return false
        }
        var index = markerIndex
        when (line[index]) {
            '-', '+', '*' -> index++
            else -> {
                // Not Char.isDigit(): that also matches non-ASCII digits, which are no marker.
                while (index < line.length && line[index] in '0'..'9') {
                    index++
                }
                val digits = index - markerIndex
                if (digits !in 1..9 || index >= line.length || (line[index] != '.' && line[index] != ')')) {
                    return false
                }
                index++
            }
        }
        return Parsing.skipSpaceTab(line, index, line.length) == line.length
    }
}
