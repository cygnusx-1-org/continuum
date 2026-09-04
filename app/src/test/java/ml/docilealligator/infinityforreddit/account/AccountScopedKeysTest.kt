package ml.docilealligator.infinityforreddit.account

import java.lang.reflect.Modifier
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The classification is written as stored key strings so it can be read against the preference XML
 * it mirrors. That trades a compiler check for a test: a key misspelled here would not fail to
 * build, it would silently leave a setting global.
 */
class AccountScopedKeysTest {

    /** Every string constant in [SharedPreferencesUtils], which is where preference keys live. */
    private val knownKeys: Set<String> =
        SharedPreferencesUtils::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .mapNotNull { it.isAccessible = true; it.get(null) as? String }
            .toSet()

    @Test
    fun `every scoped key is a real preference key`() {
        val unknown = scopedDefaultKeys().filterNot { it in knownKeys }
        assertEquals("not declared in SharedPreferencesUtils, so nothing reads them", emptyList<String>(), unknown)
    }

    @Test
    fun `the link settings stay out, being scoped by their own keys already`() {
        // Listing them here as well would scope them twice: "alice._link_handler" would itself be
        // rewritten to "alice.alice._link_handler".
        for (base in listOf(SharedPreferencesUtils.LINK_HANDLER_BASE,
            SharedPreferencesUtils.EPHEMERAL_CUSTOM_TAB_PACKAGE_BASE,
            SharedPreferencesUtils.SPECIFIC_BROWSER_PACKAGE_BASE)) {
            assertFalse(base, AccountScopedKeys.isScoped(AccountScopedKeys.DEFAULT_PREFERENCES, base))
        }
    }

    @Test
    fun `settings that must not vary by account are global`() {
        // App lock and the API keys are device- and install-level; the download location is a
        // storage grant; notifications are one schedule for the whole process.
        for (key in listOf(SharedPreferencesUtils.APP_LOCK, SharedPreferencesUtils.SECURE_MODE,
            SharedPreferencesUtils.CLIENT_ID_PREF_KEY, SharedPreferencesUtils.IMAGE_DOWNLOAD_LOCATION,
            SharedPreferencesUtils.ENABLE_NOTIFICATION_KEY, SharedPreferencesUtils.ENABLE_FOLD_SUPPORT,
            SharedPreferencesUtils.POST_FEED_MAX_RESOLUTION)) {
            assertFalse(key, AccountScopedKeys.isScoped(AccountScopedKeys.DEFAULT_PREFERENCES, key))
        }
    }

    @Test
    fun `the whole-file scopes cover any key they are asked about`() {
        for (file in listOf(SharedPreferencesUtils.POST_LAYOUT_SHARED_PREFERENCES_FILE,
            SharedPreferencesUtils.SORT_TYPE_SHARED_PREFERENCES_FILE,
            SharedPreferencesUtils.NAVIGATION_DRAWER_SHARED_PREFERENCES_FILE,
            SharedPreferencesUtils.POST_DETAILS_SHARED_PREFERENCES_FILE,
            SharedPreferencesUtils.BOTTOM_APP_BAR_SHARED_PREFERENCES_FILE)) {
            assertTrue(file, AccountScopedKeys.isScoped(file, "anything_at_all"))
        }
    }

    @Test
    fun `a file nobody scoped stays global`() {
        assertFalse(AccountScopedKeys.isScoped(
            SharedPreferencesUtils.SECURITY_SHARED_PREFERENCES_FILE, SharedPreferencesUtils.APP_LOCK))
        assertFalse(AccountScopedKeys.isScoped(
            SharedPreferencesUtils.PROXY_SHARED_PREFERENCES_FILE, SharedPreferencesUtils.PROXY_ENABLED))
    }

    @Test
    fun `scopedKeysIn narrows to the keys actually present`() {
        val present = setOf(SharedPreferencesUtils.LANGUAGE, "definitely_not_a_scoped_key")
        assertEquals(setOf(SharedPreferencesUtils.LANGUAGE),
            AccountScopedKeys.scopedKeysIn(AccountScopedKeys.DEFAULT_PREFERENCES, present))
    }

    private fun scopedDefaultKeys(): Set<String> = AccountScopedKeys.scopedDefaultKeys()
}
