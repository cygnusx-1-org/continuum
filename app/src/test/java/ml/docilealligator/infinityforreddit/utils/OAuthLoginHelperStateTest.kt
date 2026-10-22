package ml.docilealligator.infinityforreddit.utils

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The login attempt's random OAuth `state`: handed out by [OAuthLoginHelper.newPendingState],
 * checked and spent by [OAuthLoginHelper.consumePendingState]. This is what stops another app or a
 * link from logging the app into someone else's account, so it runs against real SharedPreferences
 * rather than a mock.
 *
 * The stock [Application] is used, as in the other OAuthLoginHelper tests — only a Context is
 * needed, not the Dagger graph or the global EventBus the app's Application installs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class OAuthLoginHelperStateTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private val internalPreferences
        get() = context.getSharedPreferences(SharedPreferencesUtils.INTERNAL_SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        internalPreferences.edit().clear().commit()
    }

    @Test
    fun issuedStateIsAccepted() {
        val state = OAuthLoginHelper.newPendingState(context)
        assertTrue(OAuthLoginHelper.consumePendingState(context, state))
    }

    @Test
    fun issuedStateIsAcceptedOnlyOnce() {
        val state = OAuthLoginHelper.newPendingState(context)
        assertTrue(OAuthLoginHelper.consumePendingState(context, state))
        assertFalse(OAuthLoginHelper.consumePendingState(context, state))
        assertNull(internalPreferences.getString(SharedPreferencesUtils.OAUTH_PENDING_STATE, null))
    }

    @Test
    fun oldFixedStateIsRejected() {
        // The constant every build used to send, which anyone could put on a forged redirect.
        OAuthLoginHelper.newPendingState(context)
        assertFalse(OAuthLoginHelper.consumePendingState(context, "23ro8xlxvzp4asqd"))
    }

    @Test
    fun nothingIsAcceptedWithoutAnAttemptInFlight() {
        assertFalse(OAuthLoginHelper.consumePendingState(context, "23ro8xlxvzp4asqd"))
        assertFalse(OAuthLoginHelper.consumePendingState(context, null))
    }

    @Test
    fun forgedRedirectDoesNotCancelTheRealLogin() {
        val state = OAuthLoginHelper.newPendingState(context)
        assertFalse(OAuthLoginHelper.consumePendingState(context, "forged"))
        assertFalse(OAuthLoginHelper.consumePendingState(context, null))
        assertTrue(OAuthLoginHelper.consumePendingState(context, state))
    }

    @Test
    fun newAttemptReplacesTheEarlierOne() {
        val first = OAuthLoginHelper.newPendingState(context)
        val second = OAuthLoginHelper.newPendingState(context)
        assertNotEquals(first, second)
        assertFalse(OAuthLoginHelper.consumePendingState(context, first))
        assertTrue(OAuthLoginHelper.consumePendingState(context, second))
    }

    @Test
    fun stateIsPersistedForAProcessThatDiesInTheBrowser() {
        val state = OAuthLoginHelper.newPendingState(context)
        assertEquals(state, internalPreferences.getString(SharedPreferencesUtils.OAUTH_PENDING_STATE, null))
    }

    @Test
    fun stateIsUrlSafeAndCarries128Bits() {
        val state = OAuthLoginHelper.newPendingState(context)
        // 16 random bytes, unpadded URL-safe Base64: nothing that needs escaping in a query string.
        assertTrue(state, state.matches(Regex("[A-Za-z0-9_-]{22}")))
    }
}
