package ml.docilealligator.infinityforreddit.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.TestInfinity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The channel every live setting rides on. A screen observes one of these and the change reaches it
 * without a restart; when the screen goes away the LiveData has to let go of the preferences file,
 * because the listener is registered on a process-lifetime object and nothing else ever removes it.
 *
 * There are six typed subclasses over one abstract base, and only the Int one is exercised by the
 * rest of the suite -- so the read of each type is checked here rather than assumed from its
 * neighbour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class SharedPreferenceLiveDataTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var preferences: SharedPreferences
    private val observed = mutableListOf<Pair<androidx.lifecycle.LiveData<*>, Observer<*>>>()

    @Before
    fun setUp() {
        preferences = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("prefs_live_data_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        @Suppress("UNCHECKED_CAST")
        observed.forEach { (liveData, observer) ->
            (liveData as androidx.lifecycle.LiveData<Any?>).removeObserver(observer as Observer<Any?>)
        }
        observed.clear()
        preferences.edit().clear().commit()
    }

    private fun <T> observe(liveData: SharedPreferenceLiveData<T>): Observer<T> {
        val observer = Observer<T> { }
        liveData.observeForever(observer)
        observed += liveData to observer
        return observer
    }

    @Test
    fun `each typed live data reads its own key with its own type`() {
        preferences.edit()
            .putInt("i", 7)
            .putLong("l", 8L)
            .putFloat("f", 1.5f)
            .putBoolean("b", true)
            .putString("s", "hello")
            .putStringSet("ss", setOf("a", "b"))
            .commit()

        val ints = preferences.intLiveData("i", 0)
        val longs = preferences.longLiveData("l", 0L)
        val floats = preferences.floatLiveData("f", 0f)
        val booleans = preferences.booleanLiveData("b", false)
        val strings = preferences.stringLiveData("s", "")
        val stringSets = preferences.stringSetLiveData("ss", emptySet())

        listOf(ints, longs, floats, booleans, strings, stringSets).forEach { observe(it) }

        assertEquals(7, ints.value)
        assertEquals(8L, longs.value)
        assertEquals(1.5f, floats.value)
        assertEquals(true, booleans.value)
        assertEquals("hello", strings.value)
        assertEquals(setOf("a", "b"), stringSets.value)
    }

    @Test
    fun `a change to the key reaches an observed live data`() {
        val ints = preferences.intLiveData("i", 0)
        observe(ints)
        assertEquals(0, ints.value)

        preferences.edit().putInt("i", 12).commit()
        assertEquals(12, ints.value)

        // Another key in the same file is not this LiveData's business.
        preferences.edit().putInt("other", 99).commit()
        assertEquals(12, ints.value)
    }

    @Test
    fun `a live data with no observers left stops listening to the preferences`() {
        val ints = preferences.intLiveData("i", 0)
        val observer = observe(ints)

        preferences.edit().putInt("i", 12).commit()
        assertEquals(12, ints.value)

        ints.removeObserver(observer)
        preferences.edit().putInt("i", 34).commit()

        assertEquals(
            "an unobserved LiveData is still following the preferences file",
            12, ints.value
        )
    }
}
