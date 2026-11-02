package ml.docilealligator.infinityforreddit.markdown

import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The toolbar's list buttons used to put one marker in front of the whole selection, so selecting
 * three lines and tapping the bullet button made one item out of all three (issue #447).
 *
 * Inputs mark the selection with a pair of `|`s; the expected strings are the whole text afterwards.
 */
class MarkdownListFormatterTest {

    private fun apply(
        marked: String,
        format: (CharSequence, Int, Int) -> MarkdownListFormatter.Edit?,
    ): String? {
        val start = marked.indexOf('|')
        val end = marked.indexOf('|', start + 1) - 1
        val text = marked.replace("|", "")
        val edit = format(text, start, end) ?: return null
        return text.replaceRange(edit.start, edit.end, edit.replacement)
    }

    private fun bullet(marked: String) = apply(marked, MarkdownListFormatter::bulletLines)

    private fun number(marked: String) = apply(marked, MarkdownListFormatter::numberLines)

    @Test
    fun `every selected line gets a bullet`() {
        assertEquals("* one\n* two\n* three", bullet("|one\ntwo\nthree|"))
    }

    @Test
    fun `a selection starting mid-line marks that whole line`() {
        assertEquals("* foo bar\n* baz", bullet("foo b|ar\nbaz|"))
        assertEquals("* foo bar", bullet("foo |bar|"))
    }

    @Test
    fun `a selection ending just after a newline stops at that newline`() {
        assertEquals("* one\ntwo", bullet("|one\n|two"))
    }

    @Test
    fun `a selection starting at the end of a line still marks that line`() {
        assertEquals("* one\n* two", bullet("one|\ntwo|"))
    }

    @Test
    fun `lines outside the selection are left alone`() {
        assertEquals("zero\n* one\ntwo", bullet("zero\n|one|\ntwo"))
    }

    /** A bare marker is text on Reddit, so it would show up as a stray `*` in the item above. */
    @Test
    fun `blank lines get no marker`() {
        assertEquals("* a\n\n* b", bullet("|a\n\nb|"))
        assertEquals("* a\n  \n* b", bullet("|a\n  \nb|"))
    }

    @Test
    fun `lines that are already list items are not nested`() {
        assertEquals("* a\n* b\n- c\n+\td\n1. e\n2) f", bullet("|* a\nb\n- c\n+\td\n1. e\n2) f|"))
    }

    @Test
    fun `a marker without a space after it is not an existing item`() {
        assertEquals("* *emphasis*\n* 2026.\n* -", bullet("|*emphasis*\n2026.\n-|"))
    }

    @Test
    fun `indentation is kept and the marker goes after it`() {
        assertEquals("* a\n  * b\n\t* c", bullet("|a\n  b\n\tc|"))
    }

    @Test
    fun `nothing to mark is no edit`() {
        assertNull(bullet("|* a\n\n- b|"))
        assertNull(bullet("a\n|\n|b"))
        assertNull(number("|1. a\n2. b|"))
    }

    @Test
    fun `numbered lines count up from one`() {
        assertEquals("1. a\n2. b\n\n3. c", number("|a\nb\n\nc|"))
    }

    @Test
    fun `numbering carries on from a line that is already numbered`() {
        assertEquals("3. a\n4. b\n5. c", number("|3. a\nb\nc|"))
        assertEquals("1) a\n2. b", number("|1) a\nb|"))
    }

    @Test
    fun `a bullet in a numbered selection keeps its bullet and does not take a number`() {
        assertEquals("1. a\n- b\n2. c", number("|a\n- b\nc|"))
    }

    @Test
    fun `the edit covers only the touched lines`() {
        val text = "zero\none\ntwo\nthree"
        val edit = MarkdownListFormatter.bulletLines(text, text.indexOf("ne"), text.indexOf("wo"))!!
        assertEquals(text.indexOf("one"), edit.start)
        assertEquals(text.indexOf("\nthree"), edit.end)
        assertEquals("* one\n* two", edit.replacement)
    }

    /** What the issue is about: each selected line has to come out as an item of its own. */
    @Test
    fun `the result renders as one item per line`() {
        assertEquals(
            "BulletList[ListItem[Paragraph[\"one\"]], ListItem[Paragraph[\"two\"]], ListItem[Paragraph[\"three\"]]]",
            tree(bullet("|one\ntwo\n\nthree|")!!),
        )
        assertEquals(
            "OrderedList[ListItem[Paragraph[\"one\"]], ListItem[Paragraph[\"two\"]]]",
            tree(number("|one\ntwo|")!!),
        )
    }

    private fun tree(markdown: String): String {
        val builder = Parser.builder()
        RedditListPlugin().configureParser(builder)
        return children(builder.build().parse(markdown))
    }

    private fun children(node: Node): String =
        generateSequence(node.firstChild) { it.next }.joinToString(", ") { describe(it) }

    private fun describe(node: Node): String {
        val name = if (node is Text) "\"${node.literal}\"" else node.javaClass.simpleName
        val children = children(node)
        return if (children.isEmpty()) name else "$name[$children]"
    }
}
