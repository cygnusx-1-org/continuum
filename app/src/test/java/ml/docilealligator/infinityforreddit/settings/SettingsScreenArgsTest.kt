package ml.docilealligator.infinityforreddit.settings

import android.app.Application
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SettingsScreenArgsTest {

    @Test
    fun roundTripsTheScreenTitle() {
        val fragment = MainPreferenceFragment()
        val args = Bundle()
        SettingsScreenArgs.putScreenTitle(args, "Gestures & Buttons")
        fragment.arguments = args

        assertEquals("Gestures & Buttons", SettingsScreenArgs.screenTitle(fragment))
    }

    @Test
    fun reportsNoTitleWhenNoneWasRecorded() {
        assertNull(SettingsScreenArgs.screenTitle(null))
        assertNull(SettingsScreenArgs.screenTitle(MainPreferenceFragment()))
        assertNull(SettingsScreenArgs.screenTitle(MainPreferenceFragment().apply {
            arguments = Bundle()
        }))
    }

    /** Callers pass bundles owned by a Preference, which must not be written through. */
    @Test
    fun withScrollTargetCopiesRatherThanWritingThrough() {
        val owned = Bundle()
        owned.putString("existing", "value")

        val args = SettingsScreenArgs.withScrollTarget(owned, "lock_bottom_app_bar")

        assertEquals("value", args.getString("existing"))
        assertFalse("the source bundle was modified", owned.keySet().contains("ASTK"))
    }

    @Test
    fun withScrollTargetToleratesNoBaseAndNoKey() {
        assertNull(SettingsScreenArgs.takeScrollTarget(SettingsScreenArgs.withScrollTarget(null, null)))
        assertEquals(
            "app_lock",
            SettingsScreenArgs.takeScrollTarget(SettingsScreenArgs.withScrollTarget(null, "app_lock")),
        )
    }

    /** Consumed on read, so returning to the screen or rotating it does not scroll again. */
    @Test
    fun takeScrollTargetClearsTheKey() {
        val args = SettingsScreenArgs.withScrollTarget(null, "secure_mode")

        assertEquals("secure_mode", SettingsScreenArgs.takeScrollTarget(args))
        assertNull(SettingsScreenArgs.takeScrollTarget(args))
    }

    @Test
    fun takeScrollTargetToleratesNullArguments() {
        assertNull(SettingsScreenArgs.takeScrollTarget(null))
    }

    /** The title has to survive the scroll target being consumed -- both live in one bundle. */
    @Test
    fun consumingTheScrollTargetLeavesTheTitle() {
        val fragment = MainPreferenceFragment()
        val args = SettingsScreenArgs.withScrollTarget(null, "secure_mode")
        SettingsScreenArgs.putScreenTitle(args, "Security")
        fragment.arguments = args

        SettingsScreenArgs.takeScrollTarget(fragment.arguments)

        assertEquals("Security", SettingsScreenArgs.screenTitle(fragment))
    }
}
