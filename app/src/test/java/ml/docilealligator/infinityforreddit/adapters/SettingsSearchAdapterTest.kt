package ml.docilealligator.infinityforreddit.adapters

import android.app.Application
import android.content.Context
import java.util.Locale
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.settings.SettingsSearchRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SettingsSearchAdapterTest {

    private lateinit var context: Context
    private lateinit var adapter: SettingsSearchAdapter

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SettingsSearchRegistry.getInstance().buildRegistry(context)
        adapter = SettingsSearchAdapter { }
    }

    @Test
    fun anEmptyQueryOffersEverything() {
        adapter.filter("")

        assertEquals(SettingsSearchRegistry.getInstance().items.size, adapter.itemCount)
    }

    /**
     * Breadcrumbs carry screen and section names, so a common word matches many rows through the
     * breadcrumb alone. Those still belong in the results, just not above the rows actually named
     * for what was typed.
     */
    @Test
    fun ranksTitleMatchesAboveBreadcrumbOnlyMatches() {
        adapter.filter("post")

        val results = adapter.filteredItems
        val (titleMatches, otherMatches) = results.partition { it.titleLower.contains("post") }
        // Both kinds must actually be present: a partition with nothing on one side is trivially
        // ordered, so without these the ranking assertion below would hold for free -- including in
        // the case it exists to catch, where title matches are dropped from the results entirely.
        assertTrue("no title matches for \"post\"", titleMatches.isNotEmpty())
        assertTrue("no breadcrumb-only matches for \"post\"", otherMatches.isNotEmpty())

        val lastTitleMatch = results.indexOfLast { it.titleLower.contains("post") }
        val firstOtherMatch = results.indexOfFirst { !it.titleLower.contains("post") }
        assertTrue(
            "title matches must come first, got ${results.map { it.title }}",
            lastTitleMatch < firstOtherMatch,
        )
    }

    @Test
    fun matchesSummariesAndBreadcrumbsToo() {
        adapter.filter(context.getString(R.string.settings_gestures_and_buttons_title))

        // Every row on that screen carries it in the breadcrumb, so this is far from title-only.
        assertTrue(adapter.itemCount > 5)
    }

    @Test
    fun reportsEmptyOnlyWhenNothingMatches() {
        adapter.filter("zzzz-no-such-setting")
        assertTrue(adapter.isEmpty)

        adapter.filter("lock")
        assertTrue(!adapter.isEmpty)
    }

    /**
     * Matching folds with Locale.ROOT on both sides, so it cannot start depending on the in-app
     * language -- Turkish would otherwise lowercase "I" to a dotless "ı" and lose the match.
     */
    @Test
    fun matchingDoesNotFollowTheDefaultLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            adapter.filter("INTERFACE")

            assertTrue(adapter.itemCount > 0)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun trimsTheQuery() {
        adapter.filter("  lock  ")
        val padded = adapter.itemCount
        // Otherwise a filter that matched nothing at all would satisfy the equality below with 0 == 0.
        assertTrue("padded query matched nothing", padded > 0)

        adapter.filter("lock")
        assertEquals(padded, adapter.itemCount)
    }
}
