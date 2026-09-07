package ml.docilealligator.infinityforreddit.resume

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import java.io.File
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.account.Account
import ml.docilealligator.infinityforreddit.account.AccountScope
import ml.docilealligator.infinityforreddit.activities.MainActivity
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the snapshot refuses is as much of the feature as what it restores.
 *
 * A snapshot that is read too freely does not fail visibly -- it drops the user into somebody
 * else's session, or onto a stack with nothing underneath it, or replays their history on top of
 * itself. Each rejection below is one of those, so each is tested by writing a document that should
 * be turned down and checking that nothing comes back rather than something plausible.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestInfinity::class)
class ResumeStateTest {

    /** A stand-in for any screen the user could have had open above the feed. */
    class UpperScreenActivity : Activity()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val snapshot = File(context.filesDir, "resume_state.json")

    @Before
    fun setUp() {
        ResumeState.resetForTests()
        snapshot.delete()
        signInAs("alice")
        setEnabled("alice", true)
    }

    @After
    fun tearDown() {
        ResumeState.resetForTests()
        snapshot.delete()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        currentAccountFile().edit().clear().commit()
    }

    // ------------------------------------------------------------------ fixtures

    private fun currentAccountFile() =
        context.getSharedPreferences(
            SharedPreferencesUtils.CURRENT_ACCOUNT_SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)

    private fun signInAs(accountName: String) {
        currentAccountFile().edit()
            .putString(SharedPreferencesUtils.ACCOUNT_NAME, accountName).commit()
    }

    private fun setEnabled(accountName: String, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(
                AccountScope.key(accountName, SharedPreferencesUtils.RESUME_WHERE_I_LEFT_OFF),
                enabled)
            .commit()
    }

    /** One stack entry, as the codec writes it. [state] is the screen's own note to itself. */
    private fun entry(
        cls: String,
        extras: Map<String, String> = emptyMap(),
        data: String? = null,
        state: Map<String, Int>? = null,
    ) =
        JSONObject().apply {
            put("cls", cls)
            data?.let { put("data", it) }
            if (extras.isNotEmpty()) {
                put("extras", JSONObject().apply {
                    extras.forEach { (key, value) ->
                        put(key, JSONObject().put("t", "s").put("v", value))
                    }
                })
            }
            state?.let { recorded ->
                put("state", JSONObject().apply {
                    recorded.forEach { (key, value) ->
                        put(key, JSONObject().put("t", "i").put("v", value))
                    }
                })
            }
        }

    private fun writeSnapshot(
        account: String = "alice",
        version: Int = 1,
        vararg stack: JSONObject,
    ) {
        snapshot.writeText(
            JSONObject()
                .put("version", version)
                .put("savedAt", System.currentTimeMillis())
                .put("account", account)
                .put("stack", JSONArray().apply { stack.forEach { put(it) } })
                .toString())
    }

    private val mainActivity = MainActivity::class.java.name
    private val upperScreen = UpperScreenActivity::class.java.name

    // ------------------------------------------------------------------ replay

    @Test
    fun `the screens above the feed come back in order`() {
        writeSnapshot(
            stack = arrayOf(
                entry(mainActivity),
                entry(upperScreen, mapOf("EN" to "pics")),
                entry(upperScreen, mapOf("EN" to "aww"))))

        val intents = ResumeState.buildRestoreIntents(context)

        assertNotNull(intents)
        assertEquals(2, intents!!.size)
        assertEquals("pics", intents[0].getStringExtra("EN"))
        assertEquals("aww", intents[1].getStringExtra("EN"))
    }

    @Test
    fun `an intent data uri is replayed alongside the extras`() {
        // Not an extra, and lost entirely if it is not carried separately: a screen opened from a
        // link would come back with no idea what it was showing.
        writeSnapshot(
            stack = arrayOf(
                entry(mainActivity),
                entry(upperScreen, data = "https://www.reddit.com/r/pics/comments/abc/")))

        val intents = ResumeState.buildRestoreIntents(context)!!

        assertEquals("https://www.reddit.com/r/pics/comments/abc/", intents[0].data.toString())
    }

    @Test
    fun `a snapshot with only the feed replays nothing`() {
        writeSnapshot(stack = arrayOf(entry(mainActivity)))

        assertNull(ResumeState.buildRestoreIntents(context))
    }

    @Test
    fun `the stack is replayed at most once per process`() {
        // A theme or account change relaunches the feed from inside the app. Replaying again would
        // stack the user's history on top of itself.
        writeSnapshot(stack = arrayOf(entry(mainActivity), entry(upperScreen)))

        assertNotNull(ResumeState.buildRestoreIntents(context))
        assertNull(ResumeState.buildRestoreIntents(context))
    }

    @Test
    fun `a snapshot from an older version is discarded`() {
        // Discarded, never migrated: an older document describes a stack this build may not have.
        writeSnapshot(version = 0, stack = arrayOf(entry(mainActivity), entry(upperScreen)))

        assertNull(ResumeState.buildRestoreIntents(context))
    }

    @Test
    fun `another account's snapshot is discarded`() {
        // The one that would actually show somebody the wrong content: restoring one user's feed
        // and inbox into another user's session.
        writeSnapshot(account = "bob", stack = arrayOf(entry(mainActivity), entry(upperScreen)))

        assertNull(ResumeState.buildRestoreIntents(context))
    }

    @Test
    fun `a snapshot not rooted at the feed is discarded`() {
        // Backing out of a restored screen would otherwise leave the user nowhere.
        writeSnapshot(stack = arrayOf(entry(upperScreen), entry(upperScreen)))

        assertNull(ResumeState.buildRestoreIntents(context))
    }

    @Test
    fun `a renamed screen discards the whole replay rather than half of it`() {
        // A partial stack would drop the user somewhere arbitrary every launch, with no way to tell
        // why, until they cleared the data.
        writeSnapshot(
            stack = arrayOf(
                entry(mainActivity),
                entry(upperScreen),
                entry("ml.docilealligator.infinityforreddit.activities.RemovedInSomeFutureBuild")))

        assertNull(ResumeState.buildRestoreIntents(context))
    }

    @Test
    fun `nothing is replayed while the setting is off`() {
        setEnabled("alice", false)
        writeSnapshot(stack = arrayOf(entry(mainActivity), entry(upperScreen)))

        assertNull(ResumeState.buildRestoreIntents(context))
    }

    @Test
    fun `the setting is read per account`() {
        // It sits on a "This account" screen, so one account turning it on must not commit the
        // others to it.
        setEnabled("bob", true)
        signInAs("bob")
        assertTrue(ResumeState.isEnabled(context))

        setEnabled("bob", false)
        signInAs("alice")
        assertTrue("alice still has it on", ResumeState.isEnabled(context))
    }

    // ------------------------------------------------------------------ claim

    @Test
    fun `a screen claims the state recorded for it`() {
        writeSnapshot(
            stack = arrayOf(
                entry(mainActivity),
                entry(upperScreen, mapOf("EN" to "pics"), state = mapOf("RP" to 2))))

        val activity = Robolectric.buildActivity(
            UpperScreenActivity::class.java,
            Intent(context, UpperScreenActivity::class.java).putExtra("EN", "pics")).get()

        val state = ResumeState.claim(activity)

        assertNotNull(state)
        assertEquals(2, state!!.getInt("RP"))
    }

    @Test
    fun `an entry is handed out only once`() {
        // A second screen of the same class is one the user opened themselves, not the one being
        // restored, and giving it the same state would scroll it somewhere they never were.
        writeSnapshot(
            stack = arrayOf(
                entry(mainActivity),
                entry(upperScreen, mapOf("EN" to "pics"), state = mapOf("RP" to 2))))
        val intent = Intent(context, UpperScreenActivity::class.java).putExtra("EN", "pics")

        assertNotNull(
            ResumeState.claim(Robolectric.buildActivity(UpperScreenActivity::class.java, intent).get()))
        assertNull(
            ResumeState.claim(Robolectric.buildActivity(UpperScreenActivity::class.java, intent).get()))
    }

    @Test
    fun `a screen showing something else does not claim the entry`() {
        writeSnapshot(
            stack = arrayOf(
                entry(mainActivity),
                entry(upperScreen, mapOf("EN" to "pics"), state = mapOf("RP" to 2))))

        val elsewhere = Robolectric.buildActivity(
            UpperScreenActivity::class.java,
            Intent(context, UpperScreenActivity::class.java).putExtra("EN", "aww")).get()

        assertNull(ResumeState.claim(elsewhere))

        // And the entry it did not match is still there for the screen it belongs to.
        val itsOwn = Robolectric.buildActivity(
            UpperScreenActivity::class.java,
            Intent(context, UpperScreenActivity::class.java).putExtra("EN", "pics")).get()
        assertNotNull(ResumeState.claim(itsOwn))
    }

    // ------------------------------------------------------------------ clearing

    @Test
    fun `clearing an account removes its snapshot`() {
        writeSnapshot(stack = arrayOf(entry(mainActivity), entry(upperScreen)))

        ResumeState.clearAccount(context, "alice")

        assertFalse(snapshot.exists())
    }

    @Test
    fun `clearing one account leaves another's snapshot alone`() {
        // The file is shared between accounts, so the deletion has to check whose it is. Turning the
        // setting off for one account used to throw away every other account's cached feeds too.
        writeSnapshot(account = "bob", stack = arrayOf(entry(mainActivity), entry(upperScreen)))

        ResumeState.clearAccount(context, "alice")

        assertTrue(snapshot.exists())
    }

    @Test
    fun `clearing the anonymous account matches however it is spelled`() {
        writeSnapshot(account = Account.ANONYMOUS_ACCOUNT, stack = arrayOf(entry(mainActivity)))

        ResumeState.clearAccount(context, "")

        assertFalse(snapshot.exists())
    }

    @Test
    fun `an unreadable snapshot is thrown away rather than kept`() {
        snapshot.writeText("{ this is not json")

        ResumeState.clearAccount(context, "alice")

        assertFalse(snapshot.exists())
    }
}
