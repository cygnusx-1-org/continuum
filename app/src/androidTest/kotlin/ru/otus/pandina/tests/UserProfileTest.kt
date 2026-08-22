package ru.otus.pandina.tests

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.activities.ViewUserDetailActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Opens a user profile in both orientations and checks that the header is fully there.
 *
 * This is the on-device half of the issue #369 regression net. That bug made
 * `ViewUserDetailActivity.onCreate` throw before it drew anything, on every configuration that
 * resolves to `layout-land` or `layout-sw600dp` — so opening your own or anyone else's profile
 * killed the app on tablets and on any phone in landscape. Nothing in the suite opened a profile,
 * and nothing ran in landscape, so it shipped.
 *
 * Two things are asserted, and the second matters as much as the first:
 *
 *  - the activity starts at all (a throwing `onCreate` fails [ActivityScenario.launch]); and
 *  - the save ribbon is actually present in the inflated hierarchy.
 *
 * The presence check is what stops the tempting non-fix. Wrapping every use of the ribbon in a null
 * check would also stop the crash, while quietly dropping the feature on exactly the devices that
 * were crashing. `assertNotNull` fails on that, where a crash-only test would pass.
 *
 * Orientation is driven with `requestedOrientation` rather than by rotating the device: it forces
 * the configuration change from inside the app, so it does not depend on the device's rotation
 * settings (an emulator with `accelerometer_rotation` off ignores rotation requests aimed at the
 * display). `ViewUserDetailActivity` declares no `configChanges`, so the switch destroys and
 * recreates the activity, which is what makes it re-inflate against `layout-land`. Each test
 * asserts the orientation it ended up in, so a device that refuses to rotate fails loudly instead
 * of silently re-running the portrait case.
 *
 * `layout-sw600dp` cannot be reached this way on a phone — that configuration is covered by
 * `RoborazziLayoutTest`'s `userProfileHeader` goldens at sw600dp, and by
 * `LayoutVariantIdParityTest`, which is configuration-agnostic.
 */
class UserProfileTest : BaseTest() {

    @Test
    fun otherUserProfileHeaderIsCompleteInPortrait() =
        onProfile(OTHER_USER, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) { activity ->
            assertHeaderComplete(activity)
            assertEquals(
                "The save ribbon should be shown on another user's profile.",
                View.VISIBLE,
                saveRibbon(activity).visibility,
            )
        }

    @Test
    fun otherUserProfileHeaderIsCompleteInLandscape() =
        onProfile(OTHER_USER, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) { activity ->
            assertHeaderComplete(activity)
            assertEquals(
                "The save ribbon should be shown on another user's profile in landscape too.",
                View.VISIBLE,
                saveRibbon(activity).visibility,
            )
        }

    @Test
    fun ownProfileHeaderIsCompleteInPortrait() =
        onProfile(currentAccountName(), ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) { activity ->
            assertHeaderComplete(activity)
            assertOwnProfileHidesRibbon(activity)
        }

    @Test
    fun ownProfileHeaderIsCompleteInLandscape() =
        onProfile(currentAccountName(), ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) { activity ->
            assertHeaderComplete(activity)
            assertOwnProfileHidesRibbon(activity)
        }

    /**
     * Every header view the activity dereferences unconditionally in `applyCustomTheme`. A view
     * missing from this configuration's layout gives ViewBinding a null field, which is the #369
     * crash; here it is a legible assertion failure naming the view instead.
     */
    private fun assertHeaderComplete(activity: ViewUserDetailActivity) {
        for ((name, id) in HEADER_VIEWS) {
            assertNotNull(
                "$name is missing from the layout inflated for this configuration " +
                    "(${orientationName(activity)}). ViewBinding types it @Nullable when a " +
                    "configuration omits it, and the activity dereferences it unconditionally.",
                activity.findViewById<View>(id),
            )
        }
    }

    /** Saving yourself to your own list means nothing, so the ribbon is hidden — but still there. */
    private fun assertOwnProfileHidesRibbon(activity: ViewUserDetailActivity) {
        assertEquals(
            "The save ribbon should be hidden on your own profile.",
            View.GONE,
            saveRibbon(activity).visibility,
        )
    }

    private fun saveRibbon(activity: ViewUserDetailActivity): View =
        activity.findViewById(R.id.save_user_image_view_view_user_detail_activity)

    private fun orientationName(activity: ViewUserDetailActivity): String =
        if (activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            "landscape"
        } else {
            "portrait"
        }

    /**
     * Launches the profile of [username] in [orientation] and runs [block] against the activity.
     * The activity is launched by explicit intent, the same component the feed's username link and
     * the navigation drawer's Profile row both start.
     */
    private fun onProfile(
        username: String,
        orientation: Int,
        block: (ViewUserDetailActivity) -> Unit,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, ViewUserDetailActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(ViewUserDetailActivity.EXTRA_USER_NAME_KEY, username)

        val expected = if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            Configuration.ORIENTATION_LANDSCAPE
        } else {
            Configuration.ORIENTATION_PORTRAIT
        }

        ActivityScenario.launch<ViewUserDetailActivity>(intent).use { scenario ->
            scenario.onActivity { it.requestedOrientation = orientation }
            awaitOrientation(scenario, expected)
            scenario.onActivity(block)
        }
    }

    /**
     * Blocks until the activity reports [expected], or fails.
     *
     * A single `waitForIdleSync()` is not enough. Requesting an orientation destroys and recreates
     * the activity, and on a cold start — the first launch after an install, while the process is
     * still warming up — the recreation had not finished by the time the assertion ran, so
     * `onActivity` handed back the pre-rotation instance and the landscape cases intermittently
     * asserted against a portrait configuration. Polling removes the timing assumption instead of
     * padding it with a sleep that is either flaky or slow.
     */
    private fun awaitOrientation(
        scenario: ActivityScenario<ViewUserDetailActivity>,
        expected: Int,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + ORIENTATION_TIMEOUT_MS
        var actual = Configuration.ORIENTATION_UNDEFINED

        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { actual = it.resources.configuration.orientation }
            if (actual == expected) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        }

        assertEquals(
            "The activity did not settle into the requested orientation within " +
                "${ORIENTATION_TIMEOUT_MS}ms, so this run did not exercise the layout " +
                "configuration it is named for.",
            expected,
            actual,
        )
    }

    private companion object {
        /**
         * The account from issue #369's screen recording. Nothing here depends on the profile
         * actually loading — the assertions are about the inflated hierarchy, not the response — so
         * this stays deterministic whether or not the network is up, and whether or not the account
         * still exists.
         */
        const val OTHER_USER = "Alternative-Dot-34"

        /** Generous: a cold start plus an activity recreation, on a loaded emulator. */
        const val ORIENTATION_TIMEOUT_MS = 10_000L
        const val POLL_INTERVAL_MS = 50L

        val HEADER_VIEWS = listOf(
            "The save ribbon (save_user_image_view_view_user_detail_activity)" to
                R.id.save_user_image_view_view_user_detail_activity,
            "The Follow chip (subscribe_user_chip_view_user_detail_activity)" to
                R.id.subscribe_user_chip_view_user_detail_activity,
            "The user name (user_name_text_view_view_user_detail_activity)" to
                R.id.user_name_text_view_view_user_detail_activity,
            "The karma line (karma_text_view_view_user_detail_activity)" to
                R.id.karma_text_view_view_user_detail_activity,
            "The cakeday line (cakeday_text_view_view_user_detail_activity)" to
                R.id.cakeday_text_view_view_user_detail_activity,
        )
    }
}
