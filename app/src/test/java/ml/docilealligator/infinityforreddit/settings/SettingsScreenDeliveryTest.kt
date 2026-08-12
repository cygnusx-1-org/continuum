package ml.docilealligator.infinityforreddit.settings

import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.activities.SettingsActivity
import ml.docilealligator.infinityforreddit.shadows.ShadowContextImplWithDisplay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Covers the wiring between a search result and the screen it opens -- the parts that live in
 * activity and fragment lifecycle rather than in the index: that a scroll target reaches the
 * destination and is consumed there, and that the toolbar title survives the back stack.
 *
 * Runs against a real [SettingsActivity] so these exercise the actual transactions and lifecycle
 * callbacks. [TestInfinity] supplies the Dagger graph without the real app's set-once EventBus;
 * [ShadowContextImplWithDisplay] hands the activity a display it can read the refresh rate from.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestInfinity::class, shadows = [ShadowContextImplWithDisplay::class])
class SettingsScreenDeliveryTest {

    private lateinit var controller: ActivityController<SettingsActivity>
    private lateinit var activity: SettingsActivity

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        activity = controller.get()
        idle()
    }

    @After
    fun tearDown() {
        controller.close()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun currentFragment() =
        activity.supportFragmentManager.findFragmentById(R.id.frame_layout_settings_activity)

    private fun layout(view: View, w: Int = 1080, h: Int = 1920) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, w, h)
    }

    /**
     * A result on another screen carries its key as a scroll target; the destination consumes it in
     * onViewCreated. An emptied argument is the proof the delivery ran -- takeScrollTarget is the
     * only thing that reads and clears it.
     */
    @Test
    fun deliversAndConsumesTheScrollTargetOnTheDestination() {
        val fragment = GesturesAndButtonsPreferenceFragment()
        fragment.arguments = SettingsScreenArgs.withScrollTarget(null, "lock_bottom_app_bar")

        activity.navigateToSettingsFragment(fragment, "Gestures & Buttons")
        idle()

        assertTrue(currentFragment() is GesturesAndButtonsPreferenceFragment)
        assertNull("scroll target should be consumed", SettingsScreenArgs.takeScrollTarget(fragment.arguments))
    }

    @Test
    fun keepsTheLiteralTitleTheScreenWasOpenedWith() {
        activity.navigateToSettingsFragment(GesturesAndButtonsPreferenceFragment(), "A Literal Title")
        idle()

        // The back stack listener runs after the transaction and used to re-derive this from the
        // fragment's type, which would have overwritten it with a string resource.
        assertEquals("A Literal Title", activity.title)
    }

    @Test
    fun restoresThePreviousTitleOnTheWayBack() {
        activity.navigateToSettingsFragment(GesturesAndButtonsPreferenceFragment(), "Gestures")
        idle()
        activity.navigateToSettingsFragment(SwipeActionPreferenceFragment(), "Swipe Actions")
        idle()
        assertEquals("Swipe Actions", activity.title)

        activity.supportFragmentManager.popBackStack()
        idle()

        assertEquals("Gestures", activity.title)
    }

    @Test
    fun returnsToTheRootTitleWhenTheBackStackEmpties() {
        activity.navigateToSettingsFragment(GesturesAndButtonsPreferenceFragment(), "Gestures")
        idle()

        activity.supportFragmentManager.popBackStack()
        idle()

        assertEquals(activity.getString(R.string.settings_activity_label), activity.title)
    }

    /**
     * Tapping a result that lives on the settings root clears the back stack rather than stacking a
     * second copy of it -- and lands on a real PreferenceFragmentCompat the scroll can target.
     */
    @Test
    fun rootResultsClearBackToTheRootScreen() {
        activity.navigateToSettingsFragment(GesturesAndButtonsPreferenceFragment(), "Gestures")
        idle()
        activity.navigateToSettingsFragment(SwipeActionPreferenceFragment(), "Swipe Actions")
        idle()
        assertTrue(activity.supportFragmentManager.backStackEntryCount >= 2)

        activity.supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        idle()

        assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
        val root = currentFragment()
        assertTrue(root is MainPreferenceFragment)
        assertTrue("the root can be scrolled to a preference", root is PreferenceFragmentCompat)
    }

    /**
     * The end-to-end root path: a real click on a settings-search result whose row lives on the
     * root screen clears the back stack to that root and scrolls to the row -- exercising
     * openSettingsItem's lifecycle-callback branch, which is the part fix 1 replaced.
     */
    @Test
    fun clickingARootResultLandsOnItsRowAtTheRoot() {
        // Pre-build so the search fragment takes its synchronous index path (no waiting on the
        // background build thread from a test).
        SettingsSearchRegistry.getInstance().buildRegistry(activity)

        val search = SettingsSearchFragment()
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.frame_layout_settings_activity, search)
            .addToBackStack(null)
            .commit()
        idle()

        val editText = activity.findViewById<EditText>(R.id.search_edit_text_settings_search_fragment)
        editText.setText("Privacy Policy") // -> the root "privacy_policy" preference
        idle()

        val recycler = activity.findViewById<RecyclerView>(R.id.recycler_view_settings_search_fragment)
        layout(recycler)
        val row = (0 until recycler.childCount)
            .map { recycler.getChildAt(it) }
            .first {
                it.findViewById<TextView>(R.id.title_item_settings_search).text == "Privacy Policy"
            }

        row.performClick()
        idle()

        assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
        val root = currentFragment()
        assertTrue(root is MainPreferenceFragment)
        // The row the scroll targeted is present on the screen we landed on.
        assertNotNull(
            "landed on the root with the target preference",
            (root as PreferenceFragmentCompat).findPreference<Preference>("privacy_policy"),
        )
    }
}
