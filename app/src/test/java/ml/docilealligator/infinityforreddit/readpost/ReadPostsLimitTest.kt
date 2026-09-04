package ml.docilealligator.infinityforreddit.readpost

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.account.Account
import ml.docilealligator.infinityforreddit.account.AccountScope
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * How many read posts are kept. `-1` means "no limit, keep them all"; anything else is the cap the
 * history is trimmed to.
 *
 * The anonymous account is the case worth pinning: it has no Settings screen of its own to switch
 * the limit off with, so its history is always capped and the per-account "limit enabled" flag does
 * not apply to it. Without the cap, a logged-out install grows its read-post table forever.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class ReadPostsLimitTest {

    private lateinit var preferences: SharedPreferences

    @Before
    fun setUp() {
        preferences = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("post_history_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
    }

    private fun setLimitEnabled(accountName: String, enabled: Boolean) {
        preferences.edit()
            .putBoolean(AccountScope.key(accountName, SharedPreferencesUtils.READ_POSTS_LIMIT_ENABLED), enabled)
            .commit()
    }

    private fun setLimit(accountName: String, limit: Int) {
        preferences.edit()
            .putInt(AccountScope.key(accountName, SharedPreferencesUtils.READ_POSTS_LIMIT), limit)
            .commit()
    }

    @Test
    fun `the anonymous account is capped whatever the limit-enabled flag says`() {
        setLimitEnabled(Account.ANONYMOUS_ACCOUNT, false)

        assertEquals(
            500,
            ReadPostsUtils.GetReadPostsLimit(Account.ANONYMOUS_ACCOUNT, preferences)
        )

        setLimit(Account.ANONYMOUS_ACCOUNT, 42)
        assertEquals(42, ReadPostsUtils.GetReadPostsLimit(Account.ANONYMOUS_ACCOUNT, preferences))
    }

    @Test
    fun `a signed-in account that turned the limit off keeps everything`() {
        setLimit("Alice", 42)
        setLimitEnabled("Alice", false)

        assertEquals(-1, ReadPostsUtils.GetReadPostsLimit("Alice", preferences))

        setLimitEnabled("Alice", true)
        assertEquals(42, ReadPostsUtils.GetReadPostsLimit("Alice", preferences))
    }

    @Test
    fun `a signed-in account is capped at five hundred by default`() {
        assertEquals(500, ReadPostsUtils.GetReadPostsLimit("Alice", preferences))
    }

    @Test
    fun `no post-history preferences at all means no limit`() {
        assertEquals(-1, ReadPostsUtils.GetReadPostsLimit("Alice", null))
    }
}
