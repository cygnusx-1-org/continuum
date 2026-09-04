package ml.docilealligator.infinityforreddit.settings

import android.content.Context
import android.os.Looper
import androidx.preference.ListPreference
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.account.AccountScope
import ml.docilealligator.infinityforreddit.activities.SettingsActivity
import ml.docilealligator.infinityforreddit.shadows.ShadowContextImplWithDisplay
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * That a settings *screen* reads and writes the signed-in account's copy.
 *
 * The data store this rests on is covered on its own, and passed while every screen on the default
 * file bypassed it: androidx starts a `PreferenceManager` on that file **by name** rather than
 * leaving the name null, so the hook that installs the data store never fired for them. Screens
 * showed and wrote the unscoped key while the app read the scoped one — which looks, from the
 * outside, exactly like a setting that will not stick.
 *
 * So these drive a real screen against a real activity, and assert on the file underneath.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestInfinity::class, shadows = [ShadowContextImplWithDisplay::class])
class AccountScopedSettingsScreenTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var controller: ActivityController<SettingsActivity>
    private lateinit var activity: SettingsActivity
    private lateinit var defaultPreferences: android.content.SharedPreferences

    @Before
    fun setUp() {
        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        defaultPreferences.edit().clear().commit()
        context.getSharedPreferences(
            SharedPreferencesUtils.CURRENT_ACCOUNT_SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
            .edit().putString(SharedPreferencesUtils.ACCOUNT_NAME, "alice").commit()
    }

    @After
    fun tearDown() {
        if (::controller.isInitialized) {
            controller.close()
        }
    }

    private fun launch() {
        controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        activity = controller.get()
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun openFontScreen(): FontPreferenceFragment {
        val fragment = FontPreferenceFragment()
        activity.navigateToSettingsFragment(fragment, "Font")
        idle()
        return fragment
    }

    @Test
    fun `the default file is recognised by the name androidx gives it, not only by null`() {
        launch()

        // The seam the whole split rests on. Null is the documented answer; the name is the one
        // androidx actually supplies, and missing it silently unscopes every screen on that file.
        assertNotNull("a screen that never named a file",
            activity.accountScopedPreferencesFor(null))
        assertNotNull("a screen whose PreferenceManager named the default file itself",
            activity.accountScopedPreferencesFor(context.packageName + "_preferences"))
    }

    @Test
    fun `a screen on a file that is not per-account is left on androidx's own lookup`() {
        launch()

        assertEquals(null, activity.accountScopedPreferencesFor(
            SharedPreferencesUtils.PROXY_SHARED_PREFERENCES_FILE))
        assertEquals(null, activity.accountScopedPreferencesFor(
            SharedPreferencesUtils.SECURITY_SHARED_PREFERENCES_FILE))
    }

    @Test
    fun `an edit on a screen lands under the account`() {
        launch()
        val fragment = openFontScreen()

        val fontSize = fragment.findPreference<ListPreference>(SharedPreferencesUtils.FONT_SIZE_KEY)
        assertNotNull(fontSize)
        fontSize!!.value = "Large"
        idle()

        assertEquals("Large", defaultPreferences.getString(
            AccountScope.key("alice", SharedPreferencesUtils.FONT_SIZE_KEY), null))
        assertFalse("the bare key is where nothing reads",
            defaultPreferences.contains(SharedPreferencesUtils.FONT_SIZE_KEY))
    }

    @Test
    fun `a screen shows the account's value, not the global one`() {
        defaultPreferences.edit()
            .putString(SharedPreferencesUtils.FONT_SIZE_KEY, "XSmall")
            .putString(AccountScope.key("alice", SharedPreferencesUtils.FONT_SIZE_KEY), "XLarge")
            .commit()

        launch()
        val fragment = openFontScreen()

        val fontSize = fragment.findPreference<ListPreference>(SharedPreferencesUtils.FONT_SIZE_KEY)
        assertEquals("XLarge", fontSize!!.value)
    }

    @Test
    fun `one account's edit leaves another's alone`() {
        defaultPreferences.edit()
            .putString(AccountScope.key("bob", SharedPreferencesUtils.FONT_SIZE_KEY), "XSmall")
            .commit()

        launch()
        openFontScreen().findPreference<ListPreference>(SharedPreferencesUtils.FONT_SIZE_KEY)!!
            .value = "Large"
        idle()

        assertEquals("XSmall", defaultPreferences.getString(
            AccountScope.key("bob", SharedPreferencesUtils.FONT_SIZE_KEY), null))
    }
}
