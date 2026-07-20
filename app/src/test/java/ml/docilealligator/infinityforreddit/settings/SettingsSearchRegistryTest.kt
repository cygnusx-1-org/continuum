package ml.docilealligator.infinityforreddit.settings

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import ml.docilealligator.infinityforreddit.R
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
 * The settings search index is crawled out of the preference XML at runtime, so these tests are
 * what catch a screen dropping out of the index -- e.g. a fragment missing from
 * SettingsSearchRegistry.SCREEN_RESOURCES, or a preference attribute the crawler stops recognising.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SettingsSearchRegistryTest {

    private lateinit var context: Context
    private lateinit var items: List<SettingsSearchItem>

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SettingsSearchRegistry.getInstance().buildRegistry(context)
        items = SettingsSearchRegistry.getInstance().items
    }

    private fun itemTitled(titleRes: Int) = items.single { it.title == context.getString(titleRes) }

    @Test
    fun everyPreferenceScreenContributesItems() {
        val screensWithItems = items.map { it.fragmentClass }.toSet()
        val missing = SettingsSearchRegistry.getScreenResources().keys
            .filterNot { it in screensWithItems }
            .map { it.simpleName }
            .sorted()

        assertEquals("preference screens that produced no searchable items", emptyList<String>(), missing)
    }

    @Test
    fun indexesLockBottomNavigationBar() {
        val item = itemTitled(R.string.settings_lock_bottom_app_bar_title)

        assertEquals(context.getString(R.string.settings_gestures_and_buttons_title), item.breadcrumb)
        assertEquals(GesturesAndButtonsPreferenceFragment::class.java, item.fragmentClass)
        assertEquals(context.getString(R.string.settings_gestures_and_buttons_title), item.fragmentTitle)
        assertEquals("lock_bottom_app_bar", item.key)
    }

    @Test
    fun indexesPreferenceThatOpensAnotherScreen() {
        val item = itemTitled(R.string.settings_swipe_action_title)

        // It sits under the "Post" heading of the Gestures & Buttons screen.
        val expected = context.getString(R.string.settings_gestures_and_buttons_title) +
            " › " + context.getString(R.string.settings_category_post_title)
        assertEquals(expected, item.breadcrumb)
        assertEquals(SwipeActionPreferenceFragment::class.java, item.fragmentClass)
        assertEquals(context.getString(R.string.settings_swipe_action_title), item.fragmentTitle)
    }

    /**
     * A navigation row that starts hidden still gets indexed, along with everything on the screen
     * it opens: search instantiates the fragment directly, so the row's visibility is irrelevant.
     * The Immersive Interface entry is hidden below API 26 and would otherwise take a whole screen
     * of settings out of the index.
     */
    @Test
    fun indexesScreensBehindHiddenNavigationRows() {
        // The title is used twice: the entry on Interface, and the switch on the screen it opens.
        val entry = items.single {
            it.title == context.getString(R.string.settings_immersive_interface_title) &&
                it.breadcrumb == context.getString(R.string.settings_interface_title)
        }
        assertEquals(ImmersiveInterfacePreferenceFragment::class.java, entry.fragmentClass)

        val screenItems = items.filter { it.fragmentClass == ImmersiveInterfacePreferenceFragment::class.java }
        assertTrue("immersive interface screen contributed ${screenItems.size} items", screenItems.size > 1)
    }

    /** Four rows on this screen are titled "Portrait"; only the heading tells them apart. */
    @Test
    fun qualifiesBreadcrumbsWithTheirCategory() {
        val portrait = items.filter {
            it.title == context.getString(R.string.settings_number_of_columns_in_post_feed_portrait_title)
        }

        assertEquals(4, portrait.size)
        assertEquals("breadcrumbs must distinguish them", 4, portrait.map { it.breadcrumb }.toSet().size)
        assertTrue(
            portrait.any { it.breadcrumb.endsWith(context.getString(R.string.post_layout_gallery)) }
        )
    }

    @Test
    fun buildsNestedBreadcrumbs() {
        val item = itemTitled(R.string.settings_credits_material_icons_title)

        val expected = context.getString(R.string.settings_about_master_title) +
            " › " + context.getString(R.string.settings_credits_master_title)
        assertEquals(expected, item.breadcrumb)
    }

    @Test
    fun topLevelScreensAreReachableFromTheRootBreadcrumb() {
        val item = itemTitled(R.string.settings_interface_title)

        assertEquals(context.getString(R.string.settings_activity_label), item.breadcrumb)
        assertEquals(InterfacePreferenceFragment::class.java, item.fragmentClass)
    }

    @Test
    fun capturesSummariesForSearching() {
        val item = itemTitled(R.string.settings_secure_mode_title)

        assertEquals(context.getString(R.string.settings_secure_mode_summary), item.summary)
    }

    /** Empty summaries exist in the XML and would otherwise match every query. */
    @Test
    fun treatsBlankSummaryAsAbsent() {
        val item = itemTitled(R.string.settings_delete_read_posts_in_database_title)

        assertNull(item.summary)
    }

    /**
     * Preferences hidden until some other setting is on are revealed by setVisible() at runtime,
     * which the XML crawl cannot see -- indexing them would offer results that lead nowhere.
     */
    @Test
    fun skipsPreferencesHiddenByDefault() {
        val hidden = listOf(
            R.string.settings_swipe_up_to_hide_jump_to_next_top_level_comment_button_title,
            R.string.settings_custom_font_family_title,
            R.string.settings_customize_light_theme_title,
            R.string.settings_notification_interval_title,
        ).map { context.getString(it) }

        val indexed = items.map { it.title }.filter { it in hidden }
        assertEquals(emptyList<String>(), indexed)
    }

    /** Categories are not preferences, but naming one is a reasonable way to search for a screen. */
    @Test
    fun indexesCategoryHeadings() {
        val item = itemTitled(R.string.settings_category_gesture_sensitivity_title)

        assertEquals(context.getString(R.string.settings_gestures_and_buttons_title), item.breadcrumb)
        assertEquals(GesturesAndButtonsPreferenceFragment::class.java, item.fragmentClass)
        assertNull("categories have no key to scroll to", item.key)
    }

    @Test
    fun indexesCategoryOnASubScreen() {
        val item = itemTitled(R.string.settings_category_backup_and_restore_title)

        assertEquals(context.getString(R.string.settings_advanced_master_title), item.breadcrumb)
        assertEquals(AdvancedPreferenceFragment::class.java, item.fragmentClass)
    }

    /**
     * A row that opens another screen keeps its key for de-duplication, but must not hand it to
     * the destination as a scroll target: the key names the row on the screen that declares it.
     * "security" is a row on the settings root, and means nothing on the Security screen.
     */
    @Test
    fun navigationRowsCarryNoScrollKey() {
        // app:key="security" on the settings root, opening the Security screen.
        val security = itemTitled(R.string.settings_security_title)
        assertEquals(SecurityPreferenceFragment::class.java, security.fragmentClass)
        assertNull(security.key)

        // app:key="data_saving_mode_preference" on the root; the title is reused by a switch on
        // the screen it opens, so pick the root's row by its breadcrumb.
        val dataSaving = items.single {
            it.title == context.getString(R.string.settings_data_saving_mode) &&
                it.breadcrumb == context.getString(R.string.settings_activity_label)
        }
        assertEquals(DataSavingModePreferenceFragment::class.java, dataSaving.fragmentClass)
        assertNull(dataSaving.key)
    }

    /** Rows that do live on the screen they point at keep their key, so search can scroll to them. */
    @Test
    fun ownRowsKeepTheirScrollKey() {
        assertEquals("secure_mode", itemTitled(R.string.settings_secure_mode_title).key)
        assertEquals("app_lock", itemTitled(R.string.settings_app_lock_title).key)
    }

    @Test
    fun hasNoDuplicateEntries() {
        val duplicates = items
            .groupBy { Triple(it.title, it.breadcrumb, it.fragmentClass) }
            .filterValues { it.size > 1 }
            .keys

        assertEquals(emptySet<Any>(), duplicates)
    }

    /**
     * Filtering matches against this instead of lowercasing each field per keystroke, so it has to
     * carry every searchable field -- and fold with Locale.ROOT, or matching would change meaning
     * with the in-app language (Turkish lowercases "I" to a dotless "ı").
     */
    @Test
    fun buildsALocaleStableSearchHaystack() {
        val item = itemTitled(R.string.settings_secure_mode_title)

        assertTrue(item.searchHaystack.contains(item.title.lowercase(Locale.ROOT)))
        assertTrue(item.searchHaystack.contains(item.summary!!.lowercase(Locale.ROOT)))
        assertTrue(item.searchHaystack.contains(item.breadcrumb.lowercase(Locale.ROOT)))
        assertEquals(item.searchHaystack, item.searchHaystack.lowercase(Locale.ROOT))
    }

    @Test
    fun haystackFoldingDoesNotFollowTheDefaultLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            val item = SettingsSearchItem(
                "Immersive Interface", null, "Interface", null,
                InterfacePreferenceFragment::class.java, "Interface",
            )
            // Turkish folding would give "ımmersive ınterface" and stop matching a query of "i".
            assertTrue(item.searchHaystack.contains("immersive interface"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    /**
     * The index caches per locale, so a language change has to invalidate it -- otherwise settings
     * search keeps answering in the previous language for the life of the process.
     */
    @Test
    fun rebuildsWhenTheLocaleChanges() {
        val registry = SettingsSearchRegistry.getInstance()
        val german = germanContext()
        val germanTitle = german.getString(R.string.settings_lock_bottom_app_bar_title)
        assertNotEquals(context.getString(R.string.settings_lock_bottom_app_bar_title), germanTitle)

        registry.buildRegistry(german)

        assertTrue(registry.items.any { it.title == germanTitle })
        assertTrue(registry.hasIndexFor(german))
        assertFalse("the English index should now be stale", registry.hasIndexFor(context))
    }

    /**
     * The index is built on a background thread while the UI reads it, and opening search twice
     * quickly can put two builds in flight. Neither should throw or produce a torn index.
     */
    @Test
    fun concurrentBuildsProduceOneConsistentIndex() {
        val registry = SettingsSearchRegistry.getInstance()
        val german = germanContext()
        val threadCount = 8
        val start = CountDownLatch(1)
        val finished = CountDownLatch(threadCount)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val sizes = Collections.synchronizedList(mutableListOf<Int>())

        repeat(threadCount) {
            Thread {
                try {
                    start.await()
                    registry.buildRegistry(german)
                    sizes.add(registry.items.size)
                } catch (t: Throwable) {
                    failures.add(t)
                } finally {
                    finished.countDown()
                }
            }.start()
        }
        start.countDown()

        assertTrue("threads did not finish", finished.await(30, TimeUnit.SECONDS))
        assertEquals(emptyList<Throwable>(), failures)
        assertEquals("racing builds disagreed on the index", 1, sizes.toSet().size)
        // Against the index that was actually built, not the English one from setUp: dedup keys
        // off translated breadcrumbs, so index size is locale-dependent in principle.
        assertEquals(registry.items.size, sizes.first())
    }

    private fun germanContext(): Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(Locale.GERMAN) },
    )

    @Test
    fun indexIsSubstantial() {
        assertTrue("only ${items.size} settings indexed", items.size > 200)
    }
}
