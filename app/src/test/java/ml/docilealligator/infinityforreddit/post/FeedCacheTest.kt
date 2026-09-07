package ml.docilealligator.infinityforreddit.post

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import ml.docilealligator.infinityforreddit.Infinity
import ml.docilealligator.infinityforreddit.TestInfinity
import ml.docilealligator.infinityforreddit.account.Account
import ml.docilealligator.infinityforreddit.postfilter.PostFilter
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The cache exists so a resume costs no network call, which makes "it came back wrong" the failure
 * to guard against rather than "it came back slowly".
 *
 * Posts are kept as the raw t3 listing children and rebuilt through [ParsePost] on load, so the post
 * filter is re-applied at load time and nothing needs invalidating when it changes. The other half
 * is the window: a feed longer than the cap is trimmed from the FRONT, because everything below the
 * anchor can be fetched again from the stored cursor and everything above it cannot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestInfinity::class)
class FeedCacheTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // FeedCache reads its directory from Infinity.getAppContext(), which TestInfinity leaves
        // null on purpose (see its class comment). Nothing else here needs the real application, so
        // the one field is set rather than running the real onCreate.
        val instance = Infinity::class.java.getDeclaredField("instance")
        instance.isAccessible = true
        instance.set(null, ApplicationProvider.getApplicationContext<Infinity>())
        File(context.filesDir, "feed_cache").deleteRecursively()
    }

    @After
    fun tearDown() {
        File(context.filesDir, "feed_cache").deleteRecursively()
        val instance = Infinity::class.java.getDeclaredField("instance")
        instance.isAccessible = true
        instance.set(null, null)
    }

    // ------------------------------------------------------------------ fixtures

    /** A listing entry with every field [ParsePost.parseBasicData] reads unconditionally. */
    private fun child(id: String, nsfw: Boolean = false) = JSONObject().apply {
        put("kind", "t3")
        put("data", JSONObject().apply {
            put("id", id)
            put("name", "t3_$id")
            put("subreddit", "pics")
            put("subreddit_name_prefixed", "r/pics")
            put("author", "someone")
            put("distinguished", JSONObject.NULL)
            put("created_utc", 1700000000L)
            put("title", "Title $id")
            put("score", 10)
            put("num_comments", 3)
            put("upvote_ratio", 0.95)
            put("hidden", false)
            put("spoiler", false)
            put("over_18", nsfw)
            put("stickied", false)
            put("archived", false)
            put("locked", false)
            put("saved", false)
            put("send_replies", true)
            put("can_mod_post", false)
            put("likes", JSONObject.NULL)
            put("permalink", "/r/pics/comments/$id/title/")
            put("thumbnail", "self")
            put("domain", "self.pics")
            put("is_video", false)
            put("url", "https://www.reddit.com/r/pics/comments/$id/title/")
            put("selftext", "")
            put("selftext_html", JSONObject.NULL)
        })
    }

    private fun children(vararg ids: String) = JSONArray().apply { ids.forEach { put(child(it)) } }

    private fun everything() = PostFilter().apply { allowNSFW = true }

    /** [FeedCache.store] writes on its own executor; give it a moment to land. */
    private fun storeAndWait(
        key: String,
        children: JSONArray,
        afterToken: String?,
        anchorFullname: String? = null,
    ) {
        FeedCache.store(key, children, afterToken, anchorFullname)
        val deadline = System.currentTimeMillis() + 5000
        while (!FeedCache.has(key) && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
    }

    // ------------------------------------------------------------------ round trip

    @Test
    fun `a stored feed comes back with its posts in order and its cursor`() {
        val key = FeedCache.key("alice", "1|pics")
        storeAndWait(key, children("a", "b", "c"), "t3_c")

        val cached = FeedCache.load(key, everything(), null)

        assertNotNull(cached)
        assertEquals(listOf("t3_a", "t3_b", "t3_c"), cached!!.posts.map { it.fullName })
        assertEquals("t3_c", cached.afterToken)
    }

    @Test
    fun `the raw children come back so a re-store writes the whole feed`() {
        // Without them, storing again after a resume -- having paged a little further -- would write
        // only the pages fetched since, and the feed would shrink each time the user came back.
        val key = FeedCache.key("alice", "1|pics")
        storeAndWait(key, children("a", "b"), "t3_b")

        assertEquals(2, FeedCache.load(key, everything(), null)!!.children.length())
    }

    @Test
    fun `a feed with no cursor left comes back saying so`() {
        val key = FeedCache.key("alice", "1|pics")
        storeAndWait(key, children("a"), null)

        assertNull(FeedCache.load(key, everything(), null)!!.afterToken)
    }

    @Test
    fun `the post filter is applied when the feed is read, not when it is written`() {
        // Why the bodies are kept as raw listing JSON rather than as parsed posts: changing the
        // filter needs no invalidation, because the filter never touched what was stored.
        val key = FeedCache.key("alice", "1|pics")
        val listing = JSONArray().apply {
            put(child("safe"))
            put(child("adult", nsfw = true))
        }
        storeAndWait(key, listing, "t3_adult")

        val unfiltered = FeedCache.load(key, everything(), null)!!
        val filtered = FeedCache.load(key, PostFilter().apply { allowNSFW = false }, null)!!

        assertEquals(2, unfiltered.posts.size)
        assertEquals(listOf("t3_safe"), filtered.posts.map { it.fullName })
    }

    // ------------------------------------------------------------------ misses

    @Test
    fun `nothing stored is a miss, not an empty feed`() {
        assertNull(FeedCache.load(FeedCache.key("alice", "1|never-visited"), everything(), null))
    }

    @Test
    fun `an unreadable entry is a miss`() {
        // A partial read must never be mistaken for the feed the user left: they would come back to
        // a feed missing everything above where they were, with no way to scroll up to it.
        val key = FeedCache.key("alice", "1|pics")
        storeAndWait(key, children("a", "b"), "t3_b")
        entryFileFor(key).writeText("{ this is not json")

        assertNull(FeedCache.load(key, everything(), null))
    }

    @Test
    fun `an entry from an older version is discarded`() {
        // Discarded, never migrated: an older layout describes posts this build cannot rebuild.
        val key = FeedCache.key("alice", "1|pics")
        storeAndWait(key, children("a", "b"), "t3_b")
        val file = entryFileFor(key)
        file.writeText(JSONObject(file.readText()).put("version", 0).toString())

        assertNull(FeedCache.load(key, everything(), null))
    }

    // ------------------------------------------------------------------ isolation

    @Test
    fun `one account's feeds are not another's`() {
        val alice = FeedCache.key("alice", "1|pics")
        val bob = FeedCache.key("bob", "1|pics")
        storeAndWait(alice, children("a"), "t3_a")
        storeAndWait(bob, children("b"), "t3_b")

        assertEquals(listOf("t3_a"), FeedCache.load(alice, everything(), null)!!.posts.map { it.fullName })
        assertEquals(listOf("t3_b"), FeedCache.load(bob, everything(), null)!!.posts.map { it.fullName })
    }

    @Test
    fun `clearing an account leaves every other account's feeds alone`() {
        // The bug this fixes: turning the setting off for one account used to clear the whole cache,
        // costing every other account a full refetch for a choice they never made.
        val alice = FeedCache.key("alice", "1|pics")
        val bob = FeedCache.key("bob", "1|pics")
        storeAndWait(alice, children("a"), "t3_a")
        storeAndWait(bob, children("b"), "t3_b")

        FeedCache.clearAccount("alice")
        waitUntil { !FeedCache.has(alice) }

        assertNull(FeedCache.load(alice, everything(), null))
        assertNotNull(FeedCache.load(bob, everything(), null))
    }

    @Test
    fun `anonymous browsing has a namespace of its own`() {
        val anonymous = FeedCache.key(Account.ANONYMOUS_ACCOUNT, "1|pics")
        val named = FeedCache.key("alice", "1|pics")
        storeAndWait(anonymous, children("a"), "t3_a")
        storeAndWait(named, children("b"), "t3_b")

        FeedCache.clearAccount("")
        waitUntil { !FeedCache.has(anonymous) }

        assertNull(FeedCache.load(anonymous, everything(), null))
        assertNotNull(FeedCache.load(named, everything(), null))
    }

    @Test
    fun `clearing one feed leaves the account's other feeds alone`() {
        val pics = FeedCache.key("alice", "1|pics")
        val aww = FeedCache.key("alice", "1|aww")
        storeAndWait(pics, children("a"), "t3_a")
        storeAndWait(aww, children("b"), "t3_b")

        FeedCache.clear(pics)
        waitUntil { !FeedCache.has(pics) }

        assertFalse(FeedCache.has(pics))
        assertTrue(FeedCache.has(aww))
    }

    // ------------------------------------------------------------------ the window

    @Test
    fun `a feed longer than the cap keeps the anchor and what is above it`() {
        // Everything below the anchor can be fetched again from the stored cursor. Everything above
        // it cannot, which is the whole reason the trim is biased this way.
        val key = FeedCache.key("alice", "1|pics")
        val ids = (0 until 600).map { "p$it" }
        storeAndWait(key, children(*ids.toTypedArray()), "t3_p599", "t3_p400")

        val kept = FeedCache.load(key, everything(), null)!!.posts.map { it.fullName }

        assertTrue("the anchor itself must survive the trim", kept.contains("t3_p400"))
        assertEquals("everything above the anchor is kept", "t3_p0", kept.first())
        assertEquals("and a margin below it", "t3_p449", kept.last())
    }

    @Test
    fun `trimming the tail moves the cursor back to the last post kept`() {
        // Otherwise the cursor points past posts that are no longer stored: the user scrolls off the
        // end of what came back and lands a hundred and fifty posts further down the listing than
        // they were, with no sign that anything was skipped.
        val key = FeedCache.key("alice", "1|pics")
        val ids = (0 until 600).map { "p$it" }
        storeAndWait(key, children(*ids.toTypedArray()), "t3_p599", "t3_p400")

        val cached = FeedCache.load(key, everything(), null)!!

        assertEquals(cached.posts.last().fullName, cached.afterToken)
    }

    @Test
    fun `an untrimmed feed keeps the cursor it was given`() {
        // The end of a listing has no cursor at all, and inventing one from the last post would ask
        // Reddit for a page that does not exist.
        val key = FeedCache.key("alice", "1|pics")
        storeAndWait(key, children("a", "b", "c"), null, "t3_b")

        assertNull(FeedCache.load(key, everything(), null)!!.afterToken)
    }

    @Test
    fun `a feed with no anchor keeps its tail`() {
        // Nothing to centre on: the tail is the better guess, since the user got there by paging
        // down.
        val key = FeedCache.key("alice", "1|pics")
        val ids = (0 until 600).map { "p$it" }
        storeAndWait(key, children(*ids.toTypedArray()), "t3_p599")

        val kept = FeedCache.load(key, everything(), null)!!.posts.map { it.fullName }

        assertEquals(500, kept.size)
        assertEquals("t3_p599", kept.last())
    }

    @Test
    fun `a feed within the cap is kept whole`() {
        val key = FeedCache.key("alice", "1|pics")
        val ids = (0 until 120).map { "p$it" }
        storeAndWait(key, children(*ids.toTypedArray()), "t3_p119", "t3_p60")

        assertEquals(120, FeedCache.load(key, everything(), null)!!.posts.size)
    }

    // ------------------------------------------------------------------ helpers

    private fun entryFileFor(key: String): File {
        val root = File(context.filesDir, "feed_cache")
        val file = root.walkTopDown().firstOrNull { it.isFile && it.name.startsWith(sanitize(key)) }
        assertNotNull("no cache entry written for $key", file)
        return file!!
    }

    private fun sanitize(name: String) = name.replace(Regex("[^a-zA-Z0-9_]"), "_")

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
    }
}
