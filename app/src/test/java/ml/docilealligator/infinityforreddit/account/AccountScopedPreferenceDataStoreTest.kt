package ml.docilealligator.infinityforreddit.account

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The hook that makes a preference *screen* land on the account's copy.
 *
 * Without it the split is only half done: androidx reaches its file by name, so a screen would show
 * the current account's value — it reads those through the injected preferences — and then write the
 * edit back to the unscoped file, where nothing reads it. Everything asserted here is about the raw
 * file underneath, because "the screen looked right" is exactly the symptom that bug had.
 */
@RunWith(RobolectricTestRunner::class)
class AccountScopedPreferenceDataStoreTest {

    private lateinit var raw: SharedPreferences
    private var currentAccount: String? = "alice"

    /** "layout" is per-account here; "global_toggle" is not. */
    private val scoped = setOf("layout")

    private lateinit var store: AccountScopedPreferenceDataStore

    @Before
    fun setUp() {
        raw = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("data_store_test", Context.MODE_PRIVATE)
        raw.edit().clear().commit()
        currentAccount = "alice"
        store = AccountScopedPreferenceDataStore(
            AccountScopedSharedPreferences(raw, { it in scoped }, { currentAccount }))
    }

    @Test
    fun `an edit lands under the account, not the bare key`() {
        store.putString("layout", "2")

        assertEquals("2", raw.getString("alice.layout", null))
        assertFalse("the bare key is where nothing reads", raw.contains("layout"))
    }

    @Test
    fun `a screen reads back what it wrote`() {
        store.putString("layout", "2")

        assertEquals("2", store.getString("layout", null))
    }

    @Test
    fun `a screen never sees another account's value`() {
        raw.edit().putString("bob.layout", "9").commit()

        assertEquals("bob's setting is not a default for alice",
            "fallback", store.getString("layout", "fallback"))
    }

    @Test
    fun `an unscoped key is stored exactly as the screen asked for it`() {
        store.putBoolean("global_toggle", true)

        assertTrue(raw.getBoolean("global_toggle", false))
        assertTrue(store.getBoolean("global_toggle", false))
    }

    @Test
    fun `every type a preference screen can store goes through`() {
        // A ListPreference writes a String, a SeekBarPreference an Int, a SwitchPreference a
        // Boolean, a MultiSelectListPreference a Set: a type that failed to route would be one
        // silently global screen.
        store.putString("layout", "s")
        assertEquals("s", store.getString("layout", null))

        store.putInt("layout", 7)
        assertEquals(7, store.getInt("layout", 0))

        store.putLong("layout", 8L)
        assertEquals(8L, store.getLong("layout", 0L))

        store.putFloat("layout", 1.5f)
        assertEquals(1.5f, store.getFloat("layout", 0f), 0f)

        store.putBoolean("layout", true)
        assertTrue(store.getBoolean("layout", false))

        store.putStringSet("layout", mutableSetOf("a", "b"))
        assertEquals(setOf("a", "b"), store.getStringSet("layout", null))

        assertFalse("whichever type it was, it must not be the bare key",
            raw.contains("layout"))
    }

    @Test
    fun `the same screen follows a switch of account`() {
        store.putString("layout", "alice's")

        currentAccount = "bob"
        store.putString("layout", "bob's")

        assertEquals("alice's", raw.getString("alice.layout", null))
        assertEquals("bob's", raw.getString("bob.layout", null))
    }

    @Test
    fun `anonymous gets a namespace of its own rather than the bare key`() {
        currentAccount = null

        store.putString("layout", "2")

        assertEquals("2", raw.getString(AccountScope.key(null, "layout"), null))
        assertFalse(raw.contains("layout"))
    }
}
