package ml.docilealligator.infinityforreddit.activities

import android.os.Looper
import com.google.android.material.textfield.TextInputEditText
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.postfilter.PostFilterUsage
import ml.docilealligator.infinityforreddit.shadows.ShadowContextImplWithDisplay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * The "Applies to" name dialog has to survive the activity being destroyed under it.
 *
 * It is opened over a subreddit/user picker that is itself a whole activity, so a rotation — or an
 * eviction — while the user is choosing names destroys this one. The dialog is a raw AlertDialog
 * that nothing but a tap creates, so before it was written to the instance state the recreated
 * activity came back without it: the picked names landed on a null field and were dropped without a
 * word, taking whatever had already been typed with them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestInfinity::class, shadows = [ShadowContextImplWithDisplay::class])
class CustomizePostFilterUsageDialogTest {

    private lateinit var controller: ActivityController<CustomizePostFilterActivity>

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(CustomizePostFilterActivity::class.java).setup()
        idle()
    }

    @After
    fun tearDown() {
        controller.close()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** The name field of whichever dialog is currently on screen, or null if none is. */
    private fun dialogEditText(): TextInputEditText? =
        ShadowDialog.getLatestDialog()
            ?.takeIf { it.isShowing }
            ?.findViewById(R.id.text_input_edit_text_edit_post_or_comment_filter_name_of_usage_dialog)

    @Test
    fun typedNameSurvivesRecreation() {
        controller.get().newPostFilterUsage(PostFilterUsage.SUBREDDIT_TYPE)
        idle()
        assertNotNull("the dialog should be showing before recreation", dialogEditText())
        dialogEditText()!!.setText("androiddev")

        controller.recreate()
        idle()

        val restored = dialogEditText()
        assertNotNull("the dialog must come back so the picker result has somewhere to land", restored)
        assertEquals("androiddev", restored!!.text.toString())
    }

    /**
     * Editing an existing feed pre-fills the field with its name; the recreated dialog must show what
     * the user last had in it, not re-seed itself from the row it started out from.
     */
    @Test
    fun editedNameSurvivesRecreationRatherThanReverting() {
        val existing = PostFilterUsage("filter", "-", PostFilterUsage.SUBREDDIT_TYPE, "pics")
        controller.get().onUsageClicked(existing)
        idle()
        assertEquals("pics", dialogEditText()!!.text.toString())
        dialogEditText()!!.setText("pics,aww")

        controller.recreate()
        idle()

        assertEquals("pics,aww", dialogEditText()!!.text.toString())
    }

    @Test
    fun noDialogIsRestoredWhenNoneWasOpen() {
        controller.recreate()
        idle()

        assertNull("nothing should open a dialog on a plain recreation", dialogEditText())
    }

    @Test
    fun dismissedDialogDoesNotComeBack() {
        controller.get().newPostFilterUsage(PostFilterUsage.SUBREDDIT_TYPE)
        idle()
        ShadowDialog.getLatestDialog().dismiss()
        idle()

        controller.recreate()
        idle()

        assertNull("a dialog the user closed must stay closed", dialogEditText())
    }
}
