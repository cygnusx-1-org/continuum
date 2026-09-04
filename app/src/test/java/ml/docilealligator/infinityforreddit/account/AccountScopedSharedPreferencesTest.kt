package ml.docilealligator.infinityforreddit.account

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The façade is what keeps per-account settings out of a hundred read sites, so what matters is
 * that a caller cannot tell it is there: unscoped keys behave exactly as before, scoped ones follow
 * whoever is signed in, and one account can never read another's value.
 */
@RunWith(RobolectricTestRunner::class)
class AccountScopedSharedPreferencesTest {

    private lateinit var raw: SharedPreferences
    private var currentAccount: String? = "alice"

    /** "layout" is per-account here; "language_of_app" is not. */
    private val scoped = setOf("layout")

    private fun facade() = AccountScopedSharedPreferences(raw, { it in scoped }, { currentAccount })

    /** A file where every key is per-account, as sort_type and post_layout are. */
    private fun wholeFileFacade() = AccountScopedSharedPreferences(raw, { true }, { currentAccount })

    @Before
    fun setUp() {
        raw = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("facade_test", Context.MODE_PRIVATE)
        raw.edit().clear().commit()
        currentAccount = "alice"
    }

    @Test
    fun `a scoped write lands under the account, not the bare key`() {
        facade().edit().putInt("layout", 2).commit()

        assertEquals(2, raw.getInt("alice.layout", -1))
        assertFalse("the bare key must stay untouched", raw.contains("layout"))
    }

    @Test
    fun `an unscoped key is stored exactly as it was asked for`() {
        facade().edit().putBoolean("global_toggle", true).commit()

        assertTrue(raw.getBoolean("global_toggle", false))
    }

    @Test
    fun `two accounts do not see each other's value`() {
        val preferences = facade()
        preferences.edit().putInt("layout", 2).commit()

        currentAccount = "bob"
        assertEquals("bob has never set this, so it is the caller's default",
            0, preferences.getInt("layout", 0))

        preferences.edit().putInt("layout", 5).commit()
        assertEquals(5, preferences.getInt("layout", 0))

        currentAccount = "alice"
        assertEquals("alice's choice survived bob writing his own", 2, preferences.getInt("layout", 0))
    }

    @Test
    fun `anonymous is an account like any other`() {
        currentAccount = Account.ANONYMOUS_ACCOUNT
        facade().edit().putInt("layout", 3).commit()

        assertEquals(3, raw.getInt(AccountScope.key(null, "layout"), -1))
    }

    @Test
    fun `contains follows the account too`() {
        val preferences = facade()
        preferences.edit().putInt("layout", 2).commit()

        assertTrue(preferences.contains("layout"))
        currentAccount = "bob"
        assertFalse(preferences.contains("layout"))
    }

    @Test
    fun `getAll shows this account's keys under the names the caller uses`() {
        val preferences = facade()
        preferences.edit().putInt("layout", 2).putBoolean("global_toggle", true).commit()
        currentAccount = "bob"
        preferences.edit().putInt("layout", 5).commit()
        currentAccount = "alice"

        val all = preferences.all
        assertEquals(2, all["layout"])
        assertEquals(true, all["global_toggle"])
        assertNull("bob's key must not appear", all["bob.layout"])
        assertEquals(2, all.size)
    }

    @Test
    fun `the pre-migration copy of a scoped key is not visible`() {
        // What an upgrade leaves behind: the old global value, kept in place but no longer read.
        raw.edit().putInt("layout", 9).commit()

        val preferences = facade()
        assertEquals("alice has her own copy or nothing at all",
            0, preferences.getInt("layout", 0))
        assertFalse(preferences.all.containsKey("layout"))
    }

    @Test
    fun `a listener hears its own account's changes under the caller's key`() {
        val preferences = facade()
        val heard = mutableListOf<String?>()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> heard += key }
        preferences.registerOnSharedPreferenceChangeListener(listener)

        preferences.edit().putInt("layout", 2).commit()
        assertEquals(listOf("layout"), heard)

        // A key belonging to another account is not this caller's business. Written straight to the
        // file, because that is how it reaches a listener: the account is read when the change is
        // delivered, and in the app an account switch recreates the screens holding the listeners.
        heard.clear()
        raw.edit().putInt("bob.layout", 5).commit()
        assertEquals(emptyList<String?>(), heard)

        // Nor is the leftover global copy of a scoped key.
        raw.edit().putInt("layout", 9).commit()
        assertEquals(emptyList<String?>(), heard)

        preferences.unregisterOnSharedPreferenceChangeListener(listener)
        preferences.edit().putInt("layout", 7).commit()
        assertEquals(emptyList<String?>(), heard)
    }

    @Test
    fun `clear wipes every account, because Reset All Settings means all of them`() {
        // Deliberately unlike the rest of the facade, which routes by account: the only callers are
        // the global "Reset All Settings" and the logout path. A later change narrowing this to the
        // current account would turn that row into a per-account reset with nothing to say so.
        val preferences = facade()
        preferences.edit().putInt("layout", 2).putBoolean("global_toggle", true).commit()
        currentAccount = "bob"
        preferences.edit().putInt("layout", 5).commit()
        currentAccount = "alice"

        preferences.edit().clear().commit()

        assertEquals("nothing of any account is left", emptyMap<String, Any?>(), raw.all)
    }

    @Test
    fun `getAll works on a file where every key is scoped`() {
        // isScoped answers true for every key on these files, the namespaced spellings included, so
        // deciding on the key as it arrives unresolves all of them to null and empties the map.
        val preferences = wholeFileFacade()
        preferences.edit().putInt("sort_type_best_post", 2).commit()
        currentAccount = "bob"
        preferences.edit().putInt("sort_type_best_post", 5).commit()
        currentAccount = "alice"

        val all = preferences.all
        assertEquals(2, all["sort_type_best_post"])
        assertEquals("only alice's key, under the name she asked for", 1, all.size)
    }

    @Test
    fun `a listener on a file where every key is scoped still hears its own changes`() {
        val preferences = wholeFileFacade()
        val heard = mutableListOf<String?>()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> heard += key }
        preferences.registerOnSharedPreferenceChangeListener(listener)

        preferences.edit().putInt("sort_type_best_post", 2).commit()
        assertEquals(listOf("sort_type_best_post"), heard)

        heard.clear()
        raw.edit().putInt("bob.sort_type_best_post", 5).commit()
        assertEquals(emptyList<String?>(), heard)
    }

    @Test
    fun `registering the same listener twice is one registration`() {
        // What the platform class does, keyed as it is by the listener itself. Replacing the entry
        // instead would strand the first wrapper on the file: undeliverable to unregister, and
        // doubling every callback until then.
        val preferences = facade()
        val heard = mutableListOf<String?>()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> heard += key }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        preferences.registerOnSharedPreferenceChangeListener(listener)

        preferences.edit().putInt("layout", 2).commit()
        assertEquals(listOf("layout"), heard)

        heard.clear()
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
        preferences.edit().putInt("layout", 7).commit()
        assertEquals("one unregister has to undo one register", emptyList<String?>(), heard)
    }
}
