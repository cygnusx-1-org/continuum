package ml.docilealligator.infinityforreddit.user

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.account.AccountScopedSharedPreferences
import ml.docilealligator.infinityforreddit.events.UserTagChangedEvent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The store behind the user tagger (issue #413). What matters: a tag is found under any spelling
 * of the name, an emptied tag is a removed one, every change is announced so the open screens
 * redraw, and — through the account-scoped file it is given — one account never sees another's.
 */
@RunWith(RobolectricTestRunner::class)
class UserTagsTest {

    private lateinit var raw: SharedPreferences
    private var currentAccount: String? = "alice"
    private val events = mutableListOf<UserTagChangedEvent>()

    private val subscriber = object {
        @Subscribe
        fun onEvent(event: UserTagChangedEvent) {
            events.add(event)
        }
    }

    @Before
    fun setUp() {
        raw = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("user_tags_test", Context.MODE_PRIVATE)
        raw.edit().clear().commit()
        currentAccount = "alice"
        UserTags.install(AccountScopedSharedPreferences(raw, { true }, { currentAccount }))
        EventBus.getDefault().register(subscriber)
    }

    @After
    fun tearDown() {
        EventBus.getDefault().unregister(subscriber)
        // The store is process-wide and stays pointed at this file, so it is left empty for
        // whatever binds an author next.
        raw.edit().clear().commit()
    }

    @Test
    fun `a tag is found under any capitalisation of the name`() {
        UserTags.set("Carol", "helpful")

        assertEquals("helpful", UserTags.get("carol"))
        assertEquals("helpful", UserTags.get("CAROL"))
        assertTrue(UserTags.isTagged("Carol"))
    }

    @Test
    fun `an untagged user, and no user at all, have no tag`() {
        assertNull(UserTags.get("carol"))
        assertNull(UserTags.get(null))
        assertNull(UserTags.get(""))
        assertFalse(UserTags.isTagged("carol"))
    }

    @Test
    fun `whitespace around a tag is not part of it`() {
        UserTags.set("carol", "  helpful ")

        assertEquals("helpful", UserTags.get("carol"))
    }

    @Test
    fun `setting a blank tag removes the tag`() {
        UserTags.set("carol", "helpful")
        UserTags.set("carol", "   ")

        assertNull(UserTags.get("carol"))
        assertFalse("nothing is left in the file", raw.contains("alice.carol"))
    }

    @Test
    fun `removing a tag takes it out of the file`() {
        UserTags.set("carol", "helpful")
        UserTags.remove("carol")

        assertNull(UserTags.get("carol"))
        assertTrue(raw.all.isEmpty())
    }

    @Test
    fun `every change is announced with the user it was for`() {
        UserTags.set("carol", "helpful")
        UserTags.set("carol", "")
        UserTags.remove("dave")

        assertEquals(listOf("carol", "carol", "dave"), events.map { it.username })
    }

    @Test
    fun `each account has its own tags`() {
        UserTags.set("carol", "helpful")
        currentAccount = "bob"

        assertNull("bob does not see alice's tag", UserTags.get("carol"))

        UserTags.set("carol", "expert")
        currentAccount = "alice"

        assertEquals("alice's tag is untouched by bob's", "helpful", UserTags.get("carol"))
        assertEquals(setOf("alice.carol", "bob.carol"), raw.all.keys)
    }
}
