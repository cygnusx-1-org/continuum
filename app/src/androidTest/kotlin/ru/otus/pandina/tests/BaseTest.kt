package ru.otus.pandina.tests

import android.content.Context
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kaspersky.components.alluresupport.withForcedAllureSupport
import com.kaspersky.kaspresso.kaspresso.Kaspresso
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import ml.docilealligator.infinityforreddit.account.Account
import ml.docilealligator.infinityforreddit.activities.MainActivity
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import org.junit.Assert.fail
import org.junit.Rule

open class BaseTest : TestCase(
    kaspressoBuilder = Kaspresso.Builder.withForcedAllureSupport()
) {

    @get:Rule
    val activityRule = ActivityScenarioRule<MainActivity>(MainActivity::class.java)

    /** The account the app is signed in as, or [Account.ANONYMOUS_ACCOUNT] ("-") when signed out. */
    protected fun currentAccountName(): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.getSharedPreferences(
            SharedPreferencesUtils.CURRENT_ACCOUNT_SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE
        ).getString(SharedPreferencesUtils.ACCOUNT_NAME, Account.ANONYMOUS_ACCOUNT)
            ?: Account.ANONYMOUS_ACCOUNT
    }

    /**
     * Fails immediately, and legibly, when the app is signed in.
     *
     * Some of these tests only describe the anonymous app, and signing in changes the screen out
     * from under them — see the callers for what each one depends on. Without this they still fail,
     * but as a bare Espresso "no views in hierarchy matched" or "doesn't match the selected view"
     * several steps in, which reads like a broken test rather than a wrong starting state.
     *
     * Call it at the top of the test, before any UI interaction.
     */
    protected fun requireAnonymousInstall() {
        val account = currentAccountName()
        if (account != Account.ANONYMOUS_ACCOUNT) {
            val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
            fail(
                "This test requires a logged-out (anonymous) install, but the app is signed in as " +
                    "\"$account\". Sign out in the app, or clear its data with " +
                    "`adb shell pm clear $packageName`, and run it again."
            )
        }
    }
}
