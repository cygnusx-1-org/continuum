package ml.docilealligator.infinityforreddit.utils

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.text.Spanned
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.account.AccountScopedSharedPreferences
import ml.docilealligator.infinityforreddit.user.UserTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The two placements of a user tag (issue #413). The property that matters most is the one a
 * screenshot cannot show: on a one-line author view the chip leaves the line's metrics alone, so a
 * tagged author's row is the same height as an untagged one.
 */
@RunWith(RobolectricTestRunner::class)
class UserTagChipTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        val raw = context.getSharedPreferences("user_tag_chip_test", Context.MODE_PRIVATE)
        raw.edit().clear().commit()
        UserTags.install(AccountScopedSharedPreferences(raw, { true }, { "alice" }))
    }

    @After
    fun tearDown() {
        UserTags.remove("carol")
    }

    @Test
    fun `an untagged author is handed back as given`() {
        val name = "u/carol"

        assertSame(name, UserTagChip.appendTo(context, name, "carol", Color.BLUE, Color.WHITE))
    }

    @Test
    fun `a tagged author gets the tag after the name, as a chip`() {
        UserTags.set("carol", "helpful")

        val text = UserTagChip.appendTo(context, "u/carol", "carol", Color.BLUE, Color.WHITE)

        assertEquals("u/carol helpful", text.toString())
        val spanned = text as Spanned
        val chips = spanned.getSpans(0, spanned.length, ChipSpan::class.java)
        assertEquals(1, chips.size)
        assertEquals("helpful", spanned.subSequence(
            spanned.getSpanStart(chips[0]), spanned.getSpanEnd(chips[0])).toString())
    }

    @Test
    fun `the chip after a name does not change the line's height`() {
        UserTags.set("carol", "helpful")
        val spanned = UserTagChip.appendTo(context, "u/carol", "carol", Color.BLUE, Color.WHITE) as Spanned
        val chip = spanned.getSpans(0, spanned.length, ChipSpan::class.java)[0]

        val paint = Paint().apply { textSize = 42f }
        val plain = paint.fontMetricsInt
        val measured = Paint.FontMetricsInt()
        chip.getSize(paint, spanned, spanned.getSpanStart(chip), spanned.getSpanEnd(chip), measured)

        assertEquals(plain.ascent, measured.ascent)
        assertEquals(plain.descent, measured.descent)
        assertEquals(plain.top, measured.top)
        assertEquals(plain.bottom, measured.bottom)
    }

    @Test
    fun `the chip in a flair line asks for room above and below`() {
        val spanned = UserTagChip.prependTo(context, "helpful", null, Color.BLUE, Color.WHITE) as Spanned
        val chip = spanned.getSpans(0, spanned.length, ChipSpan::class.java)[0]

        val paint = Paint().apply { textSize = 42f }
        val plain = paint.fontMetricsInt
        val measured = Paint.FontMetricsInt()
        chip.getSize(paint, spanned, 0, spanned.length, measured)

        assertTrue("ascent grew", measured.ascent < plain.ascent)
        assertTrue("descent grew", measured.descent > plain.descent)
    }

    @Test
    fun `in a flair line the tag leads and the flair follows`() {
        val text = UserTagChip.prependTo(context, "helpful", "Verified", Color.BLUE, Color.WHITE)

        assertEquals("helpful  Verified", text.toString())
        val spanned = text as Spanned
        val chips = spanned.getSpans(0, spanned.length, ChipSpan::class.java)
        assertEquals(1, chips.size)
        assertEquals(0, spanned.getSpanStart(chips[0]))
        assertEquals("helpful".length, spanned.getSpanEnd(chips[0]))
    }

    @Test
    fun `a tag alone in a flair line is just the chip`() {
        assertEquals("helpful",
            UserTagChip.prependTo(context, "helpful", null, Color.BLUE, Color.WHITE).toString())
        assertEquals("helpful",
            UserTagChip.prependTo(context, "helpful", "", Color.BLUE, Color.WHITE).toString())
    }
}
