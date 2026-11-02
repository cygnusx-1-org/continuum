package ml.docilealligator.infinityforreddit.adapters

import android.app.Activity
import android.widget.EditText
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.adapters.MarkdownBottomBarRecyclerViewAdapter.BOLD
import ml.docilealligator.infinityforreddit.adapters.MarkdownBottomBarRecyclerViewAdapter.CODE_BLOCK
import ml.docilealligator.infinityforreddit.adapters.MarkdownBottomBarRecyclerViewAdapter.ITALIC
import ml.docilealligator.infinityforreddit.adapters.MarkdownBottomBarRecyclerViewAdapter.ORDERED_LIST
import ml.docilealligator.infinityforreddit.adapters.MarkdownBottomBarRecyclerViewAdapter.QUOTE
import ml.docilealligator.infinityforreddit.adapters.MarkdownBottomBarRecyclerViewAdapter.SPOILER
import ml.docilealligator.infinityforreddit.adapters.MarkdownBottomBarRecyclerViewAdapter.STRIKE_THROUGH
import ml.docilealligator.infinityforreddit.adapters.MarkdownBottomBarRecyclerViewAdapter.SUPERSCRIPT
import ml.docilealligator.infinityforreddit.adapters.MarkdownBottomBarRecyclerViewAdapter.UNORDERED_LIST
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The toolbar under every composer, driven on a real [EditText]: the list buttons mark each selected
 * line (issue #447), and no button crashes on a selection made backwards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestInfinity::class)
class MarkdownBottomBarRecyclerViewAdapterTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun press(item: Int, text: String, selectionStart: Int, selectionEnd: Int): EditText {
        val editText = EditText(activity)
        editText.setText(text)
        editText.setSelection(selectionStart, selectionEnd)
        MarkdownBottomBarRecyclerViewAdapter.bindEditTextWithItemClickListener(activity, editText, item)
        return editText
    }

    @Test
    fun `the bullet button marks every selected line and leaves the caret after them`() {
        val editText = press(UNORDERED_LIST, "one\ntwo\nthree", 0, "one\ntwo".length)
        assertEquals("* one\n* two\nthree", editText.text.toString())
        assertEquals("* one\n* two".length, editText.selectionStart)
        assertEquals("* one\n* two".length, editText.selectionEnd)
    }

    @Test
    fun `the numbered list button numbers every selected line`() {
        val editText = press(ORDERED_LIST, "one\ntwo", 0, "one\ntwo".length)
        assertEquals("1. one\n2. two", editText.text.toString())
    }

    @Test
    fun `with no selection the list buttons still insert a marker at the caret`() {
        assertEquals("* one", press(UNORDERED_LIST, "one", 0, 0).text.toString())
        assertEquals("1. one", press(ORDERED_LIST, "one", 0, 0).text.toString())
    }

    /**
     * Shift+Left on a hardware keyboard moves only the selection's edge, leaving its start after its
     * end, and taking a substring that way round throws. A backwards selection has to come out just
     * like the same selection made forwards.
     */
    @Test
    fun `a backwards selection formats like a forwards one`() {
        val text = "one\ntwo"
        for (item in listOf(
            BOLD, ITALIC, STRIKE_THROUGH, SUPERSCRIPT, ORDERED_LIST, UNORDERED_LIST, SPOILER, QUOTE, CODE_BLOCK,
        )) {
            val forwards = press(item, text, 1, text.length)
            val backwards = press(item, text, text.length, 1)
            assertEquals("item $item", forwards.text.toString(), backwards.text.toString())
            assertEquals("item $item", forwards.selectionStart, backwards.selectionStart)
            assertEquals("item $item", forwards.selectionEnd, backwards.selectionEnd)
        }
    }
}
