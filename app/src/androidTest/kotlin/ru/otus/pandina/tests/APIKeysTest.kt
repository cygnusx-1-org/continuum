package ru.otus.pandina.tests

import android.content.Context
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.endsWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import ru.otus.pandina.screens.MainScreen
import ru.otus.pandina.screens.navigation.NavigationViewLayout
import ru.otus.pandina.screens.navigation.settings.APIKeysScreen
import ru.otus.pandina.screens.navigation.settings.SettingsScreen
import ru.otus.pandina.utils.NotificationDialogHelper

class APIKeysTest : BaseTest() {

    private companion object {
        /**
         * APIKeysPreferenceFragment.CLIENT_ID_LENGTH. The dialog's OK button stays disabled until
         * the entered value is exactly this long and is not the built-in default, so a client ID of
         * any other shape makes the whole "save it" half of this test unreachable.
         */
        const val CLIENT_ID_LENGTH = 22

        /** R.string.default_client_id — saving this is rejected, so it cannot be the test value. */
        const val BUILT_IN_CLIENT_ID = "yH0aTnJEt6qUgGn835B4vg"

        /** Matches the fallback in app/build.gradle, for runs started without the runner argument. */
        const val PLACEHOLDER_CLIENT_ID = "TestClientId0000000000"
    }

    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun defaultPreferences() = targetContext.getSharedPreferences(
        SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE, Context.MODE_PRIVATE
    )

    fun openSettings() {
        run {
            step("Open navigation drawer") {
                MainScreen {
                    navButton {
                        isVisible()
                        click()
                    }
                }
                NavigationViewLayout {
                    navBanner.isVisible()
                    settings.isVisible()
                    nawDrawerRecyclerView {
                        scrollToEnd()
                    }
                }
            }
            step("Open settings") {
                NavigationViewLayout.settings.click()
                SettingsScreen {
                    screenTittle {
                        isVisible()
                        hasText("Settings")
                    }
                }
            }
        }
    }

    /**
     * Sets a Reddit API client ID and checks it was actually stored.
     *
     * The previous version stopped one step short of the thing it was named for: it typed into the
     * dialog and asserted the OK button was *displayed*, then returned. That assertion could not
     * fail — the button is displayed whether or not the value is acceptable — and because the
     * default argument was a 29-character string the button was in fact disabled the whole time, so
     * nothing was ever saved. It also never enabled the overrides switch the Client ID row depends
     * on, which leaves that row disabled and unclickable.
     */
    @Test
    fun addRedditClientIdTest() {
        val clientId = InstrumentationRegistry.getArguments().getString("REDDIT_CLIENT_ID")
            ?: PLACEHOLDER_CLIENT_ID
        // Fail here rather than let a malformed argument silently disable the OK button and turn the
        // rest of the test into a no-op, which is exactly how the old default went unnoticed.
        assertEquals(
            "REDDIT_CLIENT_ID must be $CLIENT_ID_LENGTH characters to be accepted",
            CLIENT_ID_LENGTH, clientId.length
        )
        assertNotEquals("REDDIT_CLIENT_ID must not be the built-in default", BUILT_IN_CLIENT_ID, clientId)

        // Handle notification dialog immediately after app starts
        NotificationDialogHelper.handleNotificationDialog()

        before {
            openSettings()
        }.after {
            // Leave the install as it was found: a stored override would otherwise change which API
            // credentials every later test (and the app itself) authenticates with.
            defaultPreferences().edit()
                .remove(SharedPreferencesUtils.CLIENT_ID_PREF_KEY)
                .remove(SharedPreferencesUtils.ENABLE_API_KEY_OVERRIDES_PREF_KEY)
                .commit()
        }.run {
            step("Open API Keys screen") {
                SettingsScreen.apiKeys.click()
                APIKeysScreen {
                    screenTitle {
                        isVisible()
                        hasText("API Keys")
                    }
                    enableOverrides.isVisible()
                    // The Client ID row declares android:dependency on the overrides switch, so it
                    // starts disabled — clicking it here would do nothing at all.
                    clientId {
                        isVisible()
                        isDisabled()
                    }
                }
            }
            step("Enable API key overrides") {
                APIKeysScreen.enableOverrides.click()
                APIKeysScreen.clientId.isEnabled()
            }
            step("Set the Reddit API Client ID") {
                APIKeysScreen.clientId.click()

                onView(allOf(withClassName(endsWith("EditText")), isDisplayed()))
                    .perform(replaceText(clientId), closeSoftKeyboard())

                // Enabled, not merely displayed: the fragment gates OK on the value being valid, so
                // "displayed" is true even for input it will refuse to save.
                onView(withText("OK")).check(matches(isEnabled())).perform(click())
            }
            step("Client ID was stored") {
                // useSimpleSummaryProvider echoes the stored value into the row's summary.
                APIKeysScreen.clientIdSummary(clientId).isVisible()
                assertEquals(
                    clientId,
                    defaultPreferences().getString(SharedPreferencesUtils.CLIENT_ID_PREF_KEY, null)
                )
            }
        }
    }
}
