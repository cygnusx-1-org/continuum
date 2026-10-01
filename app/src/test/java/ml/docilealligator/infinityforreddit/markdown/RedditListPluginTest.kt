package ml.docilealligator.infinityforreddit.markdown

import io.noties.markwon.MarkwonPlugin
import ml.docilealligator.infinityforreddit.markdown.spoiler.SpoilerParserPlugin
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * On Reddit a list marker with nothing after it is text: snudown only starts an item when the marker
 * is followed by a space. CommonMark allows the empty item, Markwon renders it as
 * nothing, and a comment reading `404.` came out blank (issue #430). The expectations follow
 * snudown, which Reddit's own `body_html` comes from; the one deliberate difference is marked on its
 * test.
 *
 * Trees are written out as `Node[children]`, with text as its quoted literal, so a failure shows the
 * whole parse at a glance.
 */
class RedditListPluginTest {

    private fun tree(markdown: String, plugins: List<MarkwonPlugin> = listOf(RedditListPlugin())): String {
        val builder = Parser.builder()
        plugins.forEach { it.configureParser(builder) }
        return children(builder.build().parse(markdown))
    }

    private fun children(node: Node): String =
        generateSequence(node.firstChild) { it.next }.joinToString(", ") { describe(it) }

    private fun describe(node: Node): String {
        val name = when (node) {
            is Text -> "\"${node.literal}\""
            is OrderedList -> "OrderedList(${node.startNumber})"
            else -> node.javaClass.simpleName
        }
        val children = children(node)
        return if (children.isEmpty()) name else "$name[$children]"
    }

    @Test
    fun `a number and a period on their own are text`() {
        for (input in listOf("2.", "404.", "123456789.", "1)")) {
            assertEquals(input, "Paragraph[\"$input\"]", tree(input))
        }
    }

    @Test
    fun `a bullet on its own is text`() {
        for (input in listOf("-", "+", "*")) {
            assertEquals(input, "Paragraph[\"$input\"]", tree(input))
        }
    }

    /** Snudown makes an empty item of `2. `; rendering it as nothing would be issue #430 again. */
    @Test
    fun `whitespace after the marker does not make it an item`() {
        assertEquals("Paragraph[\"2.\"]", tree("2. "))
        assertEquals("Paragraph[\"2.\"]", tree("2.\t"))
        assertEquals("Paragraph[\"-\"]", tree("-   "))
    }

    @Test
    fun `ten digits were never a marker`() {
        assertEquals("Paragraph[\"1234567890.\"]", tree("1234567890."))
    }

    @Test
    fun `a marker followed by content is still a list`() {
        assertEquals("OrderedList(1)[ListItem[Paragraph[\"a\"]]]", tree("1. a"))
        assertEquals("OrderedList(404)[ListItem[Paragraph[\"not found\"]]]", tree("404. not found"))
        assertEquals("BulletList[ListItem[Paragraph[\"a\"]], ListItem[Paragraph[\"b\"]]]", tree("- a\n- b"))
    }

    @Test
    fun `an empty marker straight after an item continues its text, as snudown does`() {
        assertEquals(
            "OrderedList(1)[ListItem[Paragraph[\"a\", SoftLineBreak, \"2.\"]], ListItem[Paragraph[\"b\"]]]",
            tree("1. a\n2.\n3. b"),
        )
    }

    @Test
    fun `an empty marker after a blank line is a paragraph of its own`() {
        assertEquals("OrderedList(1)[ListItem[Paragraph[\"a\"]]], Paragraph[\"2.\"]", tree("1. a\n\n2."))
    }

    @Test
    fun `thematic breaks and setext headings still outrank lists`() {
        assertEquals("ThematicBreak", tree("* * *"))
        assertEquals("ThematicBreak", tree("- - -"))
        assertEquals("Heading[\"Title\"]", tree("Title\n-"))
    }

    @Test
    fun `code keeps its markers`() {
        assertEquals("FencedCodeBlock", tree("```\n2.\n```"))
        assertEquals("IndentedCodeBlock", tree("    2."))
    }

    /**
     * [SpoilerParserPlugin] turns off commonmark's block quote and HTML parsers, and this plugin its
     * list parser. [Parser.Builder.enabledBlockTypes] replaces the whole set, so if each handed over a
     * complete set of its own, whichever ran last would switch the other's back on — and a core block
     * quote parser eats the `>!` that opens a spoiler.
     */
    @Test
    fun `plugin order does not re-enable a block type another plugin turned off`() {
        val orders = listOf(
            listOf(SpoilerParserPlugin.create(0, 0), RedditListPlugin()),
            listOf(RedditListPlugin(), SpoilerParserPlugin.create(0, 0)),
        )
        for (plugins in orders) {
            val order = plugins.joinToString(" then ") { it.javaClass.simpleName }
            assertEquals(order, "Paragraph", tree(">!secret!<", plugins).substringBefore('['))
            assertEquals(order, "Paragraph[\"2.\"]", tree("2.", plugins))
        }
    }
}
