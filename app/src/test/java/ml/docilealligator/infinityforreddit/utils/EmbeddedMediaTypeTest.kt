package ml.docilealligator.infinityforreddit.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.TestInfinity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Embedded Media Type, as listed in Settings and as read by [SharedPreferencesUtils]. Comment
 * faces (issue #432) brought it to four kinds and every combination of them; the values stored
 * before that must keep meaning what they meant.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class EmbeddedMediaTypeTest {

    private data class Shown(val image: Boolean, val gif: Boolean, val emote: Boolean, val face: Boolean)

    private fun shown(type: Int) = Shown(
        SharedPreferencesUtils.canShowImage(type),
        SharedPreferencesUtils.canShowGif(type),
        SharedPreferencesUtils.canShowEmote(type),
        SharedPreferencesUtils.canShowCommentFace(type)
    )

    @Test
    fun `values stored before comment faces keep their meaning, and All gains faces`() {
        assertEquals(Shown(true, true, true, true), shown(15))
        assertEquals(Shown(true, true, false, false), shown(7))
        assertEquals(Shown(true, false, true, false), shown(6))
        assertEquals(Shown(false, true, true, false), shown(5))
        assertEquals(Shown(true, false, false, false), shown(3))
        assertEquals(Shown(false, true, false, false), shown(2))
        assertEquals(Shown(false, false, true, false), shown(1))
        assertEquals(Shown(false, false, false, false), shown(0))
    }

    @Test
    fun `every entry in the list shows what its label says`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val labels = context.resources.getStringArray(R.array.settings_embedded_media_type)
        val values = context.resources.getStringArray(R.array.settings_embedded_media_type_values)
        assertEquals(labels.size, values.size)

        fun label(id: Int) = context.getString(id)
        val expected = mapOf(
            label(R.string.all) to Shown(true, true, true, true),
            label(R.string.image_gif_and_emote) to Shown(true, true, true, false),
            label(R.string.image_gif_and_comment_face) to Shown(true, true, false, true),
            label(R.string.image_emote_and_comment_face) to Shown(true, false, true, true),
            label(R.string.gif_emote_and_comment_face) to Shown(false, true, true, true),
            label(R.string.image_and_gif) to Shown(true, true, false, false),
            label(R.string.image_and_emote) to Shown(true, false, true, false),
            label(R.string.image_and_comment_face) to Shown(true, false, false, true),
            label(R.string.gif_and_emote) to Shown(false, true, true, false),
            label(R.string.gif_and_comment_face) to Shown(false, true, false, true),
            label(R.string.emote_and_comment_face) to Shown(false, false, true, true),
            label(R.string.image_embedded_media) to Shown(true, false, false, false),
            label(R.string.gif) to Shown(false, true, false, false),
            label(R.string.emote) to Shown(false, false, true, false),
            label(R.string.comment_face) to Shown(false, false, false, true),
            label(R.string.none) to Shown(false, false, false, false),
        )
        assertEquals("one entry per combination of the four kinds", 16, expected.size)
        assertEquals(expected.keys.toList(), labels.toList())
        labels.forEachIndexed { index, text ->
            assertEquals(text, expected.getValue(text), shown(values[index].toInt()))
        }
        assertEquals("no two entries share a value", values.size, values.toSet().size)
    }
}
