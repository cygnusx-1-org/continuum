package ml.docilealligator.infinityforreddit.message

import android.content.Context
import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The inbox badge is drawn from the stored count in every screen that shows it, so the count a
 * screen reads when it is created and the one delivered to a screen that is already running have to
 * be the same number. Issue #361 was the two disagreeing: reading a message moved the badge on the
 * screens that were running, but the stored count kept the pre-read value, so the next screen that
 * opened showed it again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class InboxCountTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val preferences: SharedPreferences =
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("current_account_test", Context.MODE_PRIVATE)

    private val observers = mutableListOf<Pair<androidx.lifecycle.LiveData<Int>, Observer<Int>>>()

    @After
    fun tearDown() {
        observers.forEach { (liveData, observer) -> liveData.removeObserver(observer) }
        preferences.edit().clear().apply()
    }

    /** Observes a count the way a screen that is being created does, recording what it is told. */
    private fun openScreen(): List<Int> {
        val seen = mutableListOf<Int>()
        val liveData = InboxCount.liveData(preferences)
        val observer = Observer<Int> { seen.add(it) }
        liveData.observeForever(observer)
        observers.add(liveData to observer)
        return seen
    }

    @Test
    fun readingAMessageTakesTheStoredCountDown() {
        InboxCount.set(preferences, 3)

        InboxCount.decrement(preferences)

        assertEquals(2, InboxCount.get(preferences))
        assertEquals(2, preferences.getInt(SharedPreferencesUtils.INBOX_COUNT, -1))
    }

    @Test
    fun readingAThreadTakesDownEveryMessageInIt() {
        InboxCount.set(preferences, 5)

        InboxCount.decrement(preferences, 3)

        assertEquals(2, InboxCount.get(preferences))
    }

    @Test
    fun theCountNeverGoesNegative() {
        InboxCount.set(preferences, 1)

        InboxCount.decrement(preferences, 4)

        assertEquals(0, InboxCount.get(preferences))
        InboxCount.set(preferences, -7)
        assertEquals(0, InboxCount.get(preferences))
    }

    @Test
    fun aRunningScreenIsToldAboutEveryChange() {
        InboxCount.set(preferences, 2)
        val mainPage = openScreen()

        InboxCount.decrement(preferences)
        InboxCount.set(preferences, 0)

        assertEquals(listOf(2, 1, 0), mainPage)
    }

    /** The regression: the screen opened after the read has to agree with the one that was open. */
    @Test
    fun aScreenOpenedAfterAReadSeesTheSameCountAsTheRunningOne() {
        InboxCount.set(preferences, 1)
        val mainPage = openScreen()

        InboxCount.decrement(preferences)
        val otherPage = openScreen()

        assertEquals(0, mainPage.last())
        assertEquals(listOf(0), otherPage)
    }

    @Test
    fun loggingOutClearsTheBadgeOfTheAccountThatWasSignedIn() {
        InboxCount.set(preferences, 4)
        val mainPage = openScreen()

        // What switching to the anonymous account does to the current account's preferences.
        preferences.edit().clear().apply()

        assertEquals(0, mainPage.last())
    }
}
