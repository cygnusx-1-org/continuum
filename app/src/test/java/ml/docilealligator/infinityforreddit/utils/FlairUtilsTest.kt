package ml.docilealligator.infinityforreddit.utils

import android.os.Parcel
import ml.docilealligator.infinityforreddit.subreddit.Flair
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Flair richtext rendering (issue #458): a flair made only of emoji has nothing but their
 * `:shortcode:` names in its plain text, so the picker and the submit screen's flair chip have to
 * draw it from the richtext.
 */
@RunWith(RobolectricTestRunner::class)
class FlairUtilsTest {

    @Test
    fun emojiOnlyFlairBecomesImages() {
        // r/gtaonline's "Bug" flair, whose plain text is ":Bug1::Bug2:".
        val richtext = JSONArray(
            """[{"a":":Bug1:","e":"emoji","u":"https://emoji.redditmedia.com/xtjr6wvu11v71_t5_2xrd1/Bug1"},""" +
                """{"a":":Bug2:","e":"emoji","u":"https://emoji.redditmedia.com/4cpat3rh11v71_t5_2xrd1/Bug2"}]"""
        )

        assertEquals(
            "<img src=\"https://emoji.redditmedia.com/xtjr6wvu11v71_t5_2xrd1/Bug1\">" +
                "<img src=\"https://emoji.redditmedia.com/4cpat3rh11v71_t5_2xrd1/Bug2\">",
            FlairUtils.richtextToHtml(richtext)
        )
    }

    @Test
    fun textRunsAndUrlsAreEscaped() {
        val richtext = JSONArray(
            """[{"e":"text","t":"Q&A <b>"},{"a":":x:","e":"emoji","u":"https://emoji.example/x?a=1&b=2"}]"""
        )

        assertEquals(
            "Q&amp;A &lt;b&gt;<img src=\"https://emoji.example/x?a=1&amp;b=2\">",
            FlairUtils.richtextToHtml(richtext)
        )
    }

    @Test
    fun noRichtextIsEmpty() {
        assertEquals("", FlairUtils.richtextToHtml(null))
        assertEquals("", FlairUtils.richtextToHtml(JSONArray()))
    }

    @Test
    fun malformedPartsAreSkippedRatherThanDroppingTheFlair() {
        val richtext = JSONArray(
            """["junk",{"e":"emoji","a":":noUrl:"},{"e":"gif","t":"x"},{"e":"text","t":"kept"}]"""
        )

        assertEquals("kept", FlairUtils.richtextToHtml(richtext))
    }

    @Test
    fun editingAFlairsTextDropsItsRichtext() {
        val flair = Flair("id", ":Bug1:", "<img src=\"https://emoji.example/Bug1\">", true)

        flair.text = "typed by the user"

        assertEquals("typed by the user", flair.text)
        assertEquals("", flair.htmlText)
    }

    @Test
    fun htmlTextSurvivesParceling() {
        val flair = Flair("id", ":Bug1:", "<img src=\"https://emoji.example/Bug1\">", false)
        val parcel = Parcel.obtain()
        try {
            flair.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)

            val restored = Flair.CREATOR.createFromParcel(parcel)

            assertEquals("id", restored.id)
            assertEquals(":Bug1:", restored.text)
            assertEquals("<img src=\"https://emoji.example/Bug1\">", restored.htmlText)
            assertEquals(false, restored.isEditable)
        } finally {
            parcel.recycle()
        }
    }
}
