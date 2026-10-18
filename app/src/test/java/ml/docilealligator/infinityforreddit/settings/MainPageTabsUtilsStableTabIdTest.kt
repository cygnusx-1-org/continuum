package ml.docilealligator.infinityforreddit.settings

import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The main page pager's item ids, which FragmentStateAdapter saves each tab's fragment under and
 * restores it by when MainActivity is recreated (issue #455).
 */
class MainPageTabsUtilsStableTabIdTest {

    private fun id(postType: Int, name: String = "") =
        MainPageTabsUtils.stableTabId(MainPageTabsUtils.userKey(postType, name))

    /**
     * Pinned rather than compared with a second call, because a second call in the same process
     * is exactly what an id handed out from in-memory state would also agree with. These are the
     * ids the old activity saved its fragments under, and the new one -- a fresh process with
     * nothing in memory -- has to come up with the same ones.
     */
    @Test
    fun `ids are the same in every process`() {
        assertEquals(0x7fc4c07b4bd7a89L, id(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_HOME))
        assertEquals(0x8034807b4c3926bL, id(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_ALL))
        assertEquals(0x8144607b4d20238L, id(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SAVED))
        assertEquals(0x140f1b0d3f996a83L, id(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT, "pics"))
    }

    @Test
    fun `every tab on a strip gets its own id, and none is NO_ID`() {
        val ids =
            (SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_HOME..SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_GILDED)
                .filterNot { MainPageTabsUtils.isNameBasedType(it) }
                .map { id(it) } +
                listOf("pics", "all", "popular", "fireemblem", "gunpla").map {
                    id(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT, it)
                } +
                listOf("/user/someone/m/gunpla", "/user/-/m/gunpla").map {
                    id(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_MULTIREDDIT, it)
                } +
                id(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_USER, "someone")

        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertTrue("id $it is negative; RecyclerView.NO_ID is -1", it >= 0) }
    }

    /** A tab's identity is its user key, which ignores the case of the name. */
    @Test
    fun `the case of a name does not change the id`() {
        assertEquals(
            id(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT, "pics"),
            id(SharedPreferencesUtils.MAIN_PAGE_TAB_POST_TYPE_SUBREDDIT, "Pics"),
        )
    }
}
