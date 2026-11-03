package ml.docilealligator.infinityforreddit.settings

import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The page the main screen is left with when "Enable Tabs" is off (issue #482): one page, and none
 * of the items the "Show ..." toggles pulled into the tab list.
 */
class MainPageTabsUtilsSinglePageTest {

    private fun user(postType: Int, name: String = "") =
        MainPageTabInput(postType, name, SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_USER)

    private fun subscribed(name: String) =
        MainPageTabInput(
            SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT,
            name,
            SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_SUBSCRIBED_SUBREDDITS,
        )

    private fun multireddit(path: String) =
        MainPageTabInput(
            SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT,
            path,
            SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_GROUP_MULTIREDDITS,
        )

    @Test
    fun `the first tab is the page when the user added it`() {
        val popular = user(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_POPULAR)
        val order = listOf(popular, user(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_HOME), subscribed("pics"))

        assertSame(popular, MainPageTabsUtils.singlePage(order))
    }

    @Test
    fun `toggle items dragged to the front are passed over`() {
        val pics = user(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT, "pics")
        val order = listOf(
            subscribed("gunpla"),
            multireddit("/user/someone/m/gunpla"),
            pics,
            user(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_ALL),
        )

        assertSame(pics, MainPageTabsUtils.singlePage(order))
    }

    @Test
    fun `an order of toggle items alone falls back to Home`() {
        val page = MainPageTabsUtils.singlePage(listOf(subscribed("gunpla"), multireddit("/user/someone/m/gunpla")))

        assertEquals(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_HOME, page.postType)
        assertEquals(SharedPreferencesUtils.MAIN_PAGE_TAB_SOURCE_USER, page.source)
    }

    @Test
    fun `an empty order falls back to Home`() {
        assertEquals(
            SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_HOME,
            MainPageTabsUtils.singlePage(emptyList()).postType,
        )
    }
}
