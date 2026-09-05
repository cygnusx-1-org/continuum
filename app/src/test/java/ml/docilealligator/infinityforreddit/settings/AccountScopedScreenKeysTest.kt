package ml.docilealligator.infinityforreddit.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.account.AccountScopedKeys
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * That every setting on a per-account screen is actually per-account.
 *
 * [AccountScopedKeys] is a hand-written list and the screens are hand-written XML, so the two can
 * drift silently in a way neither file shows on its own: a switch under "This account" whose key is
 * missing from the list keeps working, reads and writes the same unscoped key, and is simply shared
 * by every account. That is how the bottom app bar toggle sat on a per-account screen while one
 * account switching the bar off switched it off for all of them.
 *
 * Reading the XML rather than restating the list is the point — a preference added to one of these
 * screens later fails here until it is classified. Both directions fail closed: an unfamiliar
 * widget type is checked rather than skipped, and a per-account screen missing from the lists below
 * is caught by `every per-account screen is checked here`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestInfinity::class)
class AccountScopedScreenKeysTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * Tags that group rows rather than being one. Everything else carrying a key is treated as a
     * setting, so a preference of a type this test has not seen before — a plain
     * `SwitchPreferenceCompat`, a new slider — is checked rather than quietly skipped.
     */
    private val containerTypes = setOf(
        "PreferenceScreen",
        "CustomFontPreferenceCategory",
    )

    /**
     * Rows that carry a key but store nothing under it: they open a dialog or another activity, and
     * the fragment finds them by key to hang a click listener on. Listed key by key rather than by
     * widget type, because the same widget stores a value elsewhere on these same screens.
     */
    private val clickTargets = setOf(
        "customize_light_theme",
        "customize_dark_theme",
        "customize_amoled_theme",
        "manage_themes",
        "copy_settings_from_account",
        "reset_account_settings",
        "delete_account_subreddits",
        "delete_account_users",
        "delete_account_sort_types",
        "delete_account_post_layouts",
        "delete_account_front_page_scrolled_position",
        "delete_account_read_posts",
    )

    /** The screens under "This account" whose settings live in the default preferences file. */
    private val defaultFileScreens = listOf(
        R.xml.interface_preferences,
        R.xml.post_preferences,
        R.xml.comment_preferences,
        R.xml.font_preferences,
        R.xml.immersive_interface_preferences,
        R.xml.time_format_preferences,
        R.xml.number_of_columns_in_post_feed_preferences,
        R.xml.theme_preferences,
        R.xml.video_preferences,
        R.xml.gestures_and_buttons_preferences,
        R.xml.swipe_action_preferences,
        R.xml.sort_type_preferences,
        R.xml.miscellaneous_preferences,
        R.xml.account_settings_management_preferences,
    )

    /** The per-account screens that name a file of their own, every key in which is scoped. */
    private val ownFileScreens = mapOf(
        R.xml.navigation_drawer_preferences to
            SharedPreferencesUtils.NAVIGATION_DRAWER_SHARED_PREFERENCES_FILE,
        R.xml.post_details_preferences to
            SharedPreferencesUtils.POST_DETAILS_SHARED_PREFERENCES_FILE,
    )

    /**
     * The three the fragment stores itself: they are `setPersistent(false)` and written under their
     * own [ml.docilealligator.infinityforreddit.account.AccountScope] keys, so listing them in
     * [AccountScopedKeys] would scope them twice.
     */
    private val storedByTheFragment = setOf(
        SharedPreferencesUtils.LINK_HANDLER,
        SharedPreferencesUtils.EPHEMERAL_CUSTOM_TAB_PACKAGE,
        SharedPreferencesUtils.SPECIFIC_BROWSER_PACKAGE,
    )

    @Test
    fun `every setting on a per-account screen on the default file is scoped`() {
        val unscoped = mutableListOf<String>()
        for (screen in defaultFileScreens) {
            for (key in valueKeysIn(screen)) {
                if (key in storedByTheFragment) {
                    continue
                }
                if (!AccountScopedKeys.isScoped(AccountScopedKeys.DEFAULT_PREFERENCES, key)) {
                    unscoped.add(key)
                }
            }
        }
        assertEquals("on a \"This account\" screen but shared by every account",
            emptyList<String>(), unscoped)
    }

    @Test
    fun `the bottom app bar toggle travels with the bar it switches on`() {
        // The bar's contents are a whole per-account file; the toggle is one key in another.
        assertEquals(true, AccountScopedKeys.isScoped(
            AccountScopedKeys.DEFAULT_PREFERENCES, SharedPreferencesUtils.BOTTOM_APP_BAR_KEY))
    }

    @Test
    fun `every setting on a per-account screen with its own file is scoped`() {
        val unscoped = mutableListOf<String>()
        for ((screen, fileName) in ownFileScreens) {
            for (key in valueKeysIn(screen)) {
                if (!AccountScopedKeys.isScoped(fileName, key)) {
                    unscoped.add(key)
                }
            }
        }
        assertEquals(emptyList<String>(), unscoped)
    }

    @Test
    fun `every per-account screen is checked here`() {
        // The two tests above read hand-written lists, so a screen added under "This account" later
        // would simply not be examined -- passing while its settings stay global. The settings tree
        // itself says which screens those are, so it is asked rather than trusted.
        val checked = (defaultFileScreens + ownFileScreens.keys).toSet()
        val missing = perAccountScreens()
            .filterNot { it in checked }
            .map { context.resources.getResourceEntryName(it) }
            .sorted()
        assertEquals("reachable under \"This account\" but in neither list above",
            emptyList<String>(), missing)
    }

    @Test
    fun `the screens really do declare rows`() {
        // Guards the tests above against passing because the XML was read as empty. Counted before
        // the click targets are dropped: Account Settings Management is eight actions and no setting,
        // which is a screen read correctly, not a screen read as nothing.
        for (screen in defaultFileScreens + ownFileScreens.keys) {
            if (keyedRowsIn(screen).isEmpty()) {
                throw AssertionError("no keyed preferences read from ${context.resources.getResourceEntryName(screen)}")
            }
        }
    }

    @Test
    fun `the crawl really does reach the per-account screens`() {
        // Same guard for `every per-account screen is checked here`, which passes trivially if the
        // walk down from the settings root finds nothing.
        val reached = perAccountScreens()
        assertEquals("the This account group leads to the screens under it",
            true, R.xml.interface_preferences in reached && R.xml.font_preferences in reached)
    }

    /**
     * Every preference screen reachable from the "This account" group of the settings root.
     *
     * A fragment with no entry in [SettingsSearchRegistry]'s fragment-to-XML map is a leaf here, as
     * it is for the search index: the screens built in code rather than XML (NSFW and Spoiler, Post
     * History, Recently Visited) have no preferences to read.
     */
    private fun perAccountScreens(): Set<Int> {
        val screens = mutableSetOf<Int>()
        val pending = ArrayDeque(fragmentsUnderThisAccount().mapNotNull(::screenResourceOf))
        while (pending.isNotEmpty()) {
            val screen = pending.removeFirst()
            if (!screens.add(screen)) {
                continue
            }
            fragmentsIn(screen).mapNotNull(::screenResourceOf).forEach(pending::addLast)
        }
        return screens
    }

    /** The fragments the settings root links to under the "This account" heading. */
    private fun fragmentsUnderThisAccount(): List<String> {
        val fragments = mutableListOf<String>()
        var underThisAccount = false
        forEachStartTag(R.xml.main_preferences) { tag, parser ->
            if (tag == "CustomFontPreferenceCategory") {
                underThisAccount = attributeResourceOf(parser, "title") ==
                    R.string.settings_group_account
            } else if (underThisAccount) {
                attributeOf(parser, "fragment")?.let(fragments::add)
            }
        }
        return fragments
    }

    /** The fragments [screen] itself links on to. */
    private fun fragmentsIn(screen: Int): List<String> {
        val fragments = mutableListOf<String>()
        forEachStartTag(screen) { _, parser ->
            attributeOf(parser, "fragment")?.let(fragments::add)
        }
        return fragments
    }

    /** The XML a preference fragment inflates, or null for one that has none. */
    private fun screenResourceOf(fragmentClassName: String): Int? =
        SettingsSearchRegistry.getScreenResources().entries
            .firstOrNull { it.key.name == fragmentClassName }?.value

    /** The keys of the preferences in [screen] that store a value. */
    private fun valueKeysIn(screen: Int): List<String> =
        keyedRowsIn(screen).filterNot { it in clickTargets }

    /**
     * Every key on [screen] that is a row rather than a group or a link to another screen -- the
     * ones that store a value, plus the [clickTargets] that only look as though they do.
     */
    private fun keyedRowsIn(screen: Int): List<String> {
        val keys = mutableListOf<String>()
        forEachStartTag(screen) { tag, parser ->
            if (tag !in containerTypes && attributeOf(parser, "fragment") == null) {
                attributeOf(parser, "key")?.let(keys::add)
            }
        }
        return keys
    }

    /** Runs [body] over every start tag in [screen], with the tag's simple class name. */
    private fun forEachStartTag(screen: Int, body: (String, XmlPullParser) -> Unit) {
        context.resources.getXml(screen).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    body(parser.name.substringAfterLast('.'), parser)
                }
            }
        }
    }

    /**
     * An attribute's literal value, in either namespace: `android:key` on some screens, `app:key`
     * on others, and the parser reports both simply as "key".
     */
    private fun attributeOf(parser: XmlPullParser, name: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i) == name) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    /** An attribute holding a resource reference, as the resource id it points at. */
    private fun attributeResourceOf(parser: XmlPullParser, name: String): Int {
        val resourceParser = parser as android.content.res.XmlResourceParser
        for (i in 0 until resourceParser.attributeCount) {
            if (resourceParser.getAttributeName(i) == name) {
                return resourceParser.getAttributeResourceValue(i, 0)
            }
        }
        return 0
    }
}
