package ml.docilealligator.infinityforreddit.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [AccountScope] replaced five hand-rolled key conventions, so the cases that matter are the ones
 * those conventions disagreed about: how anonymous browsing is spelled, and whether a username can
 * be confused with part of a setting name.
 */
class AccountScopeTest {

    @Test
    fun `every spelling of anonymous reaches one namespace`() {
        val expected = AccountScope.ANONYMOUS_NAMESPACE
        // "-" from BaseActivity, "" from a cleared current-account file, null from a missing value.
        assertEquals(expected, AccountScope.namespace(Account.ANONYMOUS_ACCOUNT))
        assertEquals(expected, AccountScope.namespace(""))
        assertEquals(expected, AccountScope.namespace(null))
    }

    @Test
    fun `a signed-in account keeps its own namespace`() {
        assertEquals("alice", AccountScope.namespace("alice"))
    }

    @Test
    fun `the anonymous namespace cannot collide with a username`() {
        // Reddit usernames are letters, digits, underscores and hyphens, so no account can be
        // named ".anonymous" and take an anonymous user's settings.
        assert(AccountScope.ANONYMOUS_NAMESPACE.contains('.'))
    }

    @Test
    fun `a username ending in an underscore cannot be confused with a setting`() {
        // The bug the separator exists for: without it, account "foo" blurring NSFW and account
        // "foo_blur" enabling NSFW both write "foo_blur_nsfw".
        val fooBlurNsfw = AccountScope.key("foo", "_blur_nsfw")
        val fooBlurAccountNsfw = AccountScope.key("foo_blur", "_nsfw")
        assertNotEquals(fooBlurNsfw, fooBlurAccountNsfw)
    }

    @Test
    fun `a key splits back into the namespace and base it was built from`() {
        for (accountName in listOf("alice", "foo_blur", null, "")) {
            val key = AccountScope.key(accountName, "_nsfw")
            assertEquals(AccountScope.namespace(accountName), AccountScope.namespaceOf(key))
            assertEquals("_nsfw", AccountScope.baseOf(key))
        }
    }

    @Test
    fun `an anonymous key splits on the separator before the base, not the one starting it`() {
        val key = AccountScope.key(null, "_nsfw")
        assertEquals(".anonymous._nsfw", key)
        assertEquals(AccountScope.ANONYMOUS_NAMESPACE, AccountScope.namespaceOf(key))
        assertEquals("_nsfw", AccountScope.baseOf(key))
    }

    @Test
    fun `a key that was never scoped reports no namespace`() {
        assertNull(AccountScope.baseOf("link_handler"))
        assertNull(AccountScope.namespaceOf("link_handler"))
    }
}
