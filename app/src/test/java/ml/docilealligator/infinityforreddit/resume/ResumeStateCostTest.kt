package ml.docilealligator.infinityforreddit.resume

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.account.AccountScope
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What recording a screen costs, and who pays it.
 *
 * The feature is off by default, so the thing that has to be true is that a user who never turns it
 * on is not charged for it. "Not charged" is not a wall-clock claim here -- this runs on a desktop
 * JVM under Robolectric, where a measured millisecond means nothing about a phone. It is a claim
 * about *what work happens*: with the setting off, recording a screen must not ask that screen to
 * describe itself, because describing means calling [ResumeLaunchExtras.resumeLaunchExtras], and
 * three screens in this app answer that by running a whole `Post`, `PostFilter` or `MultiReddit`
 * through Gson. Reflective serialization on the main thread of every gallery open is the regression
 * these tests exist to prevent.
 *
 * So the assertions that matter are counts, which are exact and do not flake. The two timing tests
 * are there only to catch work that scales with a screen's content sneaking back in: they use
 * bundles far larger than any real intent and bounds far looser than any real budget, so they fail
 * on an algorithmic mistake and never on a slow machine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestInfinity::class)
class ResumeStateCostTest {

    /** A screen that reports how often it has been asked to describe itself, and from where. */
    class DescribeCountingActivity : Activity(), ResumeLaunchExtras {
        override fun resumeLaunchExtras(): Bundle? {
            describeCalls++
            describedOn = Thread.currentThread().name
            describeReached.countDown()
            return Bundle(intent.extras ?: Bundle())
        }

        companion object {
            var describeCalls = 0

            /** The thread the last describe ran on. Written off the main thread, so volatile. */
            @Volatile
            var describedOn: String? = null

            /** Opened when a describe happens, so a real background one can be waited for. */
            var describeReached = CountDownLatch(1)
        }
    }

    /**
     * A screen that can say who it is cheaply, as the gallery, filtered-posts and search screens do.
     * The two answers are counted separately, because the whole point is that adoption uses the
     * cheap one and never reaches the expensive one.
     */
    class IdentifiedActivity : Activity(), ResumeLaunchExtras {
        override fun resumeLaunchExtras(): Bundle? {
            expensiveCalls++
            return Bundle(intent.extras ?: Bundle())
        }

        override fun resumeIdentity(): String? {
            cheapCalls++
            return intent.getStringExtra("id")
        }

        companion object {
            var expensiveCalls = 0
            var cheapCalls = 0
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val snapshot = File(context.filesDir, "resume_state.json")

    /**
     * The describe executor, held rather than run, so a test decides when the background work
     * happens. Without this the assertions would race a real thread and the counts would flake.
     */
    private val pending = ArrayDeque<Runnable>()

    /** Run everything queued, and anything those tasks queue in turn. */
    private fun drainDescribes() {
        while (pending.isNotEmpty()) {
            pending.removeFirst().run()
        }
    }

    @Before
    fun setUp() {
        ResumeState.resetForTests()
        DescribeCountingActivity.describeCalls = 0
        DescribeCountingActivity.describedOn = null
        IdentifiedActivity.expensiveCalls = 0
        IdentifiedActivity.cheapCalls = 0
        DescribeCountingActivity.describeReached = CountDownLatch(1)
        pending.clear()
        ResumeState.describeExecutor = Executor { pending.addLast(it) }
        // Publication is synchronous: in the app it hops back to the main thread, and in a test the
        // caller already is that thread.
        ResumeState.publishToMainThread = { it.run() }
        snapshot.delete()
        context.getSharedPreferences(
            SharedPreferencesUtils.CURRENT_ACCOUNT_SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
            .edit().putString(SharedPreferencesUtils.ACCOUNT_NAME, "alice").commit()
    }

    @After
    fun tearDown() {
        ResumeState.resetForTests()
        snapshot.delete()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        context.getSharedPreferences(
            SharedPreferencesUtils.CURRENT_ACCOUNT_SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun setEnabled(enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(
                AccountScope.key("alice", SharedPreferencesUtils.RESUME_WHERE_I_LEFT_OFF), enabled)
            .commit()
    }

    /** A screen carrying [keys] string extras, to make content-proportional work visible. */
    private fun screen(keys: Int): Activity {
        val intent = Intent(context, DescribeCountingActivity::class.java)
        for (i in 0 until keys) {
            intent.putExtra("key$i", "value$i")
        }
        return Robolectric.buildActivity(DescribeCountingActivity::class.java, intent).get()
    }

    // ------------------------------------------------------------------ what the off state pays

    @Test
    fun `with the setting off, recording a screen never asks it to describe itself`() {
        setEnabled(false)

        repeat(50) { ResumeState.recordCreated(screen(keys = 5)) }

        assertEquals(0, DescribeCountingActivity.describeCalls)
    }

    @Test
    fun `with the setting off, capturing asks nothing either`() {
        setEnabled(false)
        ResumeState.recordCreated(screen(keys = 5))

        ResumeState.capture(context)

        assertEquals(0, DescribeCountingActivity.describeCalls)
    }

    @Test
    fun `recording is not proportional to what a screen carries`() {
        // A bundle two orders of magnitude larger than any real intent this app builds. If
        // recording copied or encoded the extras -- which it did before this was deferred -- two
        // hundred screens of two hundred keys would be forty thousand encodes and would not come
        // close to finishing in the budget below.
        setEnabled(false)
        val screens = (0 until 200).map { screen(keys = 200) }

        val start = System.nanoTime()
        screens.forEach { ResumeState.recordCreated(it) }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        assertEquals(0, DescribeCountingActivity.describeCalls)
        assertTrue("200 screens took ${elapsedMs}ms to record", elapsedMs < 100)
    }

    // ------------------------------------------------------------------ when the on state pays it

    @Test
    fun `with the setting on, a screen is described off the main thread, not on it`() {
        // The point of the whole arrangement. Creating the screen queues the work and asks the
        // screen nothing yet; the describe happens when that queued task runs, which in the app is
        // a background thread. By the time a capture comes past, there is nothing left to do.
        setEnabled(true)

        ResumeState.recordCreated(screen(keys = 5))
        assertEquals("creation must not describe inline", 0, DescribeCountingActivity.describeCalls)
        assertEquals("creation must queue the describe", 1, pending.size)

        drainDescribes()
        assertEquals("the queued task describes", 1, DescribeCountingActivity.describeCalls)

        ResumeState.capture(context)
        assertEquals("capture finds it already described", 1, DescribeCountingActivity.describeCalls)
    }

    @Test
    fun `the describe really does happen on another thread`() {
        // The claim this whole arrangement makes, checked against a real executor rather than the
        // hand-driven one: whatever thread creates the screen, something else asks it to describe
        // itself. Traced on a device that call cost 192.9 ms for a gallery, so where it runs is the
        // difference between a dropped frame and no dropped frame.
        setEnabled(true)
        val caller = Thread.currentThread().name
        val real = Executors.newSingleThreadExecutor { r -> Thread(r, "resume-describe-under-check") }
        ResumeState.describeExecutor = real
        try {
            ResumeState.recordCreated(screen(keys = 5))

            assertTrue(
                "the describe never ran",
                DescribeCountingActivity.describeReached.await(10, TimeUnit.SECONDS))
            assertEquals("resume-describe-under-check", DescribeCountingActivity.describedOn)
            assertNotEquals(caller, DescribeCountingActivity.describedOn)
        } finally {
            real.shutdown()
        }
    }

    @Test
    fun `a capture that arrives first still describes inline`() {
        // The fallback: a user who opens a screen and leaves again before the background work lands
        // must still get a correct snapshot, at the old cost. Correctness never waits on the
        // optimisation.
        setEnabled(true)
        ResumeState.recordCreated(screen(keys = 5))

        ResumeState.capture(context) // queued task deliberately not drained
        assertEquals("capture describes what nothing else has", 1,
            DescribeCountingActivity.describeCalls)

        // And the queued task, arriving late, must not ask the screen a second time.
        drainDescribes()
        assertEquals(1, DescribeCountingActivity.describeCalls)
    }

    @Test
    fun `a screen is described once however often the stack is captured`() {
        // capture runs on every screen transition, not only when the app is backgrounded, so
        // re-describing each time would put Gson on a path the user crosses constantly.
        setEnabled(true)
        ResumeState.recordCreated(screen(keys = 5))
        drainDescribes()

        repeat(20) { ResumeState.capture(context) }

        assertEquals(1, DescribeCountingActivity.describeCalls)
    }

    @Test
    fun `a rebuilt screen adopts its old entry rather than being described afresh`() {
        // Adoption has to prove the rebuilt screen is the same screen, not merely the same class,
        // and the only identity a screen has is how it was launched -- so it is asked once, here.
        // That one call is the whole cost: the capture that follows finds the entry already
        // described and asks nothing. Three calls would mean adoption failed and the stack grew a
        // second entry for a screen the user only has one of.
        setEnabled(true)
        val intent = Intent(context, DescribeCountingActivity::class.java).putExtra("k", "v")
        val before = Robolectric.buildActivity(DescribeCountingActivity::class.java, intent).get()
        ResumeState.recordCreated(before)
        ResumeState.capture(context)
        assertEquals(1, DescribeCountingActivity.describeCalls)

        // Destroyed for a configuration change: not finishing, so the entry stays.
        ResumeState.recordDestroyed(before)
        ResumeState.recordCreated(
            Robolectric.buildActivity(DescribeCountingActivity::class.java, intent).get())
        assertEquals("adoption asks once, to confirm identity", 2,
            DescribeCountingActivity.describeCalls)

        ResumeState.capture(context)
        assertEquals("and the capture after it asks nothing", 2,
            DescribeCountingActivity.describeCalls)
    }

    @Test
    fun `with the setting off, a screen is not even asked who it is`() {
        // resumeIdentity is cheap next to a describe, not free: the gallery's answer unparcels a
        // whole Post to read one field off it. A user who never turned the feature on must not pay
        // that on every gallery open, which is what happened when recording asked unconditionally.
        setEnabled(false)

        repeat(20) {
            ResumeState.recordCreated(
                Robolectric.buildActivity(IdentifiedActivity::class.java,
                    Intent(context, IdentifiedActivity::class.java).putExtra("id", "t3_abc")).get())
        }

        assertEquals("the cheap question is still a question", 0, IdentifiedActivity.cheapCalls)
        assertEquals(0, IdentifiedActivity.expensiveCalls)
        assertTrue("and nothing may be queued either", pending.isEmpty())
    }

    @Test
    fun `a rotation adopts on the cheap identity, without serializing anything`() {
        // Rotation was the one place the describe could not be moved off the main thread: adoption
        // decides where the entry sits in the stack, so it cannot wait for a background answer. It
        // used to answer by comparing rewritten extras, which for a gallery meant a Gson call on the
        // main thread in the middle of a rotation. A screen that can name itself is asked that.
        setEnabled(true)
        val intent = Intent(context, IdentifiedActivity::class.java).putExtra("id", "t3_abc")
        val before = Robolectric.buildActivity(IdentifiedActivity::class.java, intent).get()
        ResumeState.recordCreated(before)
        drainDescribes()
        val expensiveAfterDescribe = IdentifiedActivity.expensiveCalls
        val cheapAfterDescribe = IdentifiedActivity.cheapCalls

        ResumeState.recordDestroyed(before) // configuration change: not finishing
        ResumeState.recordCreated(
            Robolectric.buildActivity(IdentifiedActivity::class.java, intent).get())

        assertEquals("adoption must not serialize anything",
            expensiveAfterDescribe, IdentifiedActivity.expensiveCalls)
        assertTrue("adoption must ask the cheap question instead",
            IdentifiedActivity.cheapCalls > cheapAfterDescribe)
    }

    @Test
    fun `a different screen of the same class is not adopted on a mismatched identity`() {
        // Cheap is no use if it is wrong. With "Don't keep activities" on, opening a second gallery
        // finds the first one's destroyed entry, and adopting it would record the screen the user is
        // actually on under the previous one's name.
        setEnabled(true)
        val first = Robolectric.buildActivity(IdentifiedActivity::class.java,
            Intent(context, IdentifiedActivity::class.java).putExtra("id", "t3_first")).get()
        ResumeState.recordCreated(first)
        drainDescribes()
        ResumeState.recordDestroyed(first)
        val describesBefore = IdentifiedActivity.expensiveCalls

        ResumeState.recordCreated(
            Robolectric.buildActivity(IdentifiedActivity::class.java,
                Intent(context, IdentifiedActivity::class.java).putExtra("id", "t3_second")).get())
        drainDescribes()

        // Adoption and rejection are told apart by what happens next: an adopted entry is already
        // described, so nothing is scheduled and the screen is never asked. A rejected one means a
        // second entry was created, and that entry schedules a describe of its own.
        assertTrue("a mismatched identity must not adopt",
            IdentifiedActivity.expensiveCalls > describesBefore)
    }

    @Test
    fun `a dismissed screen is described again when reopened`() {
        // The other half of the rule: finishing removes the entry, so a screen the user opens again
        // is a new screen and gets looked at afresh.
        setEnabled(true)
        val first = screen(keys = 5)
        ResumeState.recordCreated(first)
        ResumeState.capture(context)
        first.finish()
        ResumeState.recordDestroyed(first)

        ResumeState.recordCreated(screen(keys = 5))
        ResumeState.capture(context)

        assertEquals(2, DescribeCountingActivity.describeCalls)
    }

    @Test
    fun `walking a deep stack on every transition costs a fraction of a frame`() {
        // capture runs on the main thread at every screen transition, so it has one 60Hz frame.
        //
        // What this bounds is the work this change added: walking the stack, and asking each screen
        // whether it still needs describing. It does not reach the JSON encode or the file write,
        // because a stack that is not rooted at MainActivity is refused before those, and building
        // a real MainActivity here would measure Dagger and view inflation rather than anything
        // this test is about. Those two are measured in ResumeCaptureCostTest, which gets a rooted
        // stack by seeding one from a snapshot document instead of constructing the activity.
        setEnabled(true)
        repeat(8) { ResumeState.recordCreated(screen(keys = 20)) }
        ResumeState.capture(context) // describe once, so this measures the steady state

        val runs = (0 until 50).map {
            val start = System.nanoTime()
            ResumeState.capture(context)
            (System.nanoTime() - start) / 1_000_000.0
        }.sorted()
        val median = runs[runs.size / 2]

        assertTrue("median walk of an 8-deep stack was ${median}ms", median < 4.0)
    }
}
