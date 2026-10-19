package ml.docilealligator.infinityforreddit.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.TestInfinity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Reddit-link helpers: recognizing a link as Reddit's, and Settings > Miscellaneous > Domain
 * for shared links applied to links on their way out of the app.
 *
 * The setting is opt-in and deliberately narrow: only the hosts that serve the same paths as
 * www.reddit.com may be rewritten. Sending a media or share-shortener host elsewhere produces a URL
 * that does not resolve, and the person who receives the shared link is the one who finds out. An
 * embed fixer is narrower still — it only understands posts and comments, and only for links sent
 * to someone else, never for one opened in a browser.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class RedditLinkUtilsTest {

    private lateinit var preferences: SharedPreferences

    @Before
    fun setUp() {
        preferences = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("reddit_link_utils_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
    }

    private fun chooseDomain(domain: String) {
        preferences.edit().putString(SharedPreferencesUtils.SHARED_LINK_DOMAIN, domain).commit()
    }

    private fun chooseCustomDomain(domain: String) {
        chooseDomain(RedditLinkUtils.CUSTOM_SHARED_LINK_DOMAIN)
        preferences.edit().putString(SharedPreferencesUtils.SHARED_LINK_CUSTOM_DOMAIN, domain).commit()
    }

    private fun shared(url: String) = RedditLinkUtils.applyShareDomain(preferences, url)

    private fun opened(url: String) = RedditLinkUtils.applyBrowserDomain(preferences, url)

    @Test
    fun `links are left alone until a domain is chosen`() {
        val url = "https://www.reddit.com/r/pics/comments/abc123/sunset/"

        assertEquals(url, shared(url))
        assertEquals(url, opened(url))

        chooseDomain(RedditLinkUtils.WWW_REDDIT_HOST)
        assertEquals(url, shared(url))
        assertEquals(url, opened(url))
    }

    @Test
    fun `the old switch becomes old reddit at start-up, over a list value already there`() {
        // As a restore leaves it: an older backup's switch merged in beside this device's list.
        chooseDomain(RedditLinkUtils.WWW_REDDIT_HOST)
        preferences.edit().putBoolean(SharedPreferencesUtils.USE_OLD_REDDIT_DOMAIN, true).commit()

        SharedLinkDomainMigration.migrate(preferences)

        assertEquals(RedditLinkUtils.OLD_REDDIT_HOST, RedditLinkUtils.sharedLinkDomainChoice(preferences))
        assertEquals("https://old.reddit.com/r/pics/", shared("https://www.reddit.com/r/pics/"))
        assertEquals(false, preferences.contains(SharedPreferencesUtils.USE_OLD_REDDIT_DOMAIN))
    }

    @Test
    fun `old reddit applies to every reddit path, shared or opened`() {
        chooseDomain(RedditLinkUtils.OLD_REDDIT_HOST)

        assertEquals(
            "https://old.reddit.com/r/pics/comments/abc123/sunset/",
            shared("https://www.reddit.com/r/pics/comments/abc123/sunset/")
        )
        assertEquals("https://old.reddit.com/r/pics/", shared("https://reddit.com/r/pics/"))
        assertEquals("https://old.reddit.com/user/spez", opened("https://www.reddit.com/user/spez"))
    }

    @Test
    fun `media and share-shortener hosts pass through whatever the domain`() {
        val untouched = listOf(
            "https://i.redd.it/abc123.jpg",
            "https://v.redd.it/abc123/DASH_720.mp4",
            "https://preview.redd.it/abc123.jpg?width=640",
            "https://redd.it/abc123",
            "https://s.reddit.com/abc123",
            "https://reddit.app.link/abc123",
            "https://example.com/reddit.com/not-reddit",
            "https://www.reddit.example.com/r/pics/comments/abc123/"
        )

        for (domain in listOf(RedditLinkUtils.OLD_REDDIT_HOST, "fxreddit.seria.moe")) {
            chooseDomain(domain)
            untouched.forEach { url ->
                assertEquals(domain, url, shared(url))
                assertEquals(domain, url, opened(url))
            }
        }
    }

    @Test
    fun `an embed fixer is used for post and comment links`() {
        chooseDomain("www.vxreddit.com")

        assertEquals(
            "https://www.vxreddit.com/r/pics/comments/abc123/sunset/",
            shared("https://www.reddit.com/r/pics/comments/abc123/sunset/")
        )
        assertEquals(
            "https://www.vxreddit.com/r/pics/comments/abc123/sunset/def456/?context=3",
            shared("https://www.reddit.com/r/pics/comments/abc123/sunset/def456/?context=3")
        )
        assertEquals("https://www.vxreddit.com/comments/abc123", shared("https://reddit.com/comments/abc123"))
        assertEquals(
            "https://www.vxreddit.com/user/spez/comments/abc123/",
            shared("https://www.reddit.com/user/spez/comments/abc123/")
        )
    }

    @Test
    fun `an embed fixer leaves every other reddit link on reddit`() {
        // vxReddit answers each of these with an "Invalid post path" page.
        chooseDomain("www.vxreddit.com")

        val untouched = listOf(
            "https://www.reddit.com/r/pics",
            "https://www.reddit.com/r/pics/",
            "https://www.reddit.com/user/spez",
            "https://www.reddit.com/user/spez/comments/",
            "https://www.reddit.com/r/pics/comments/",
            "https://www.reddit.com/gallery/abc123",
            "https://www.reddit.com/r/pics/s/AbCdEf",
            "https://www.reddit.com/r/pics/wiki/index",
            "https://www.reddit.com/search/?q=comments",
            "https://www.reddit.com/"
        )

        untouched.forEach { url -> assertEquals(url, shared(url)) }
    }

    @Test
    fun `an embed fixer is never used for a link opened outside the app`() {
        val url = "https://www.reddit.com/r/pics/comments/abc123/sunset/"

        chooseDomain("fxreddit.seria.moe")
        assertEquals(url, opened(url))

        chooseCustomDomain("rxddit.com")
        assertEquals(url, opened(url))
    }

    @Test
    fun `a custom domain is used once Custom is chosen`() {
        val url = "https://www.reddit.com/r/pics/comments/abc123/sunset/"

        preferences.edit().putString(SharedPreferencesUtils.SHARED_LINK_CUSTOM_DOMAIN, "rxddit.com").commit()
        assertEquals("typed in but not chosen", url, shared(url))

        chooseCustomDomain("rxddit.com")
        assertEquals("https://rxddit.com/r/pics/comments/abc123/sunset/", shared(url))
        // Custom is an embed fixer like the presets: posts and comments only.
        assertEquals("https://www.reddit.com/r/pics/", shared("https://www.reddit.com/r/pics/"))
    }

    @Test
    fun `a custom domain of reddit's own applies to every path, like old reddit`() {
        chooseCustomDomain("new.reddit.com")

        assertEquals("https://new.reddit.com/r/pics/", shared("https://www.reddit.com/r/pics/"))
        assertEquals("https://new.reddit.com/r/pics/", opened("https://www.reddit.com/r/pics/"))
    }

    @Test
    fun `an empty or unusable custom domain leaves links on reddit`() {
        val url = "https://www.reddit.com/r/pics/comments/abc123/sunset/"

        for (custom in listOf("", "   ", "not a domain", "localhost", "-bad-.com", "host.com:8080")) {
            chooseCustomDomain(custom)
            assertEquals(custom, url, shared(url))
        }
        preferences.edit().remove(SharedPreferencesUtils.SHARED_LINK_CUSTOM_DOMAIN).commit()
        assertEquals(url, shared(url))
    }

    @Test
    fun `a pasted address is cut down to its host`() {
        assertEquals("fxreddit.seria.moe", RedditLinkUtils.normalizeDomain("fxreddit.seria.moe"))
        assertEquals("fxreddit.seria.moe", RedditLinkUtils.normalizeDomain("  https://FxReddit.Seria.Moe/ "))
        assertEquals(
            "www.vxreddit.com",
            RedditLinkUtils.normalizeDomain("https://www.vxreddit.com/r/pics/comments/abc123/?context=3")
        )
        assertEquals("rxddit.com", RedditLinkUtils.normalizeDomain("rxddit.com?x=1"))
        assertEquals(null, RedditLinkUtils.normalizeDomain(null))
        assertEquals(null, RedditLinkUtils.normalizeDomain(""))
        assertEquals(null, RedditLinkUtils.normalizeDomain("https://"))
        assertEquals(null, RedditLinkUtils.normalizeDomain("fx reddit.com"))
    }

    @Test
    fun `reddit's own hosts are recognized as reddit links`() {
        val links = listOf(
            "https://www.reddit.com/r/pics/comments/abc123/sunset/",
            "https://reddit.com/r/pics/",
            "https://old.reddit.com/r/pics/",
            "http://www.reddit.com/r/pics/",
            "https://i.redd.it/abc123.jpg",
            "https://v.redd.it/abc123",
            "https://redd.it/abc123",
            "https://reddit.app.link/abc123"
        )

        links.forEach { url ->
            assertEquals(url, RedditLinkUtils.redditLinkOrNull(url))
        }
    }

    @Test
    fun `a link copied without its scheme comes back with one`() {
        assertEquals(
            "https://www.reddit.com/r/pics/comments/abc123/sunset/",
            RedditLinkUtils.redditLinkOrNull("www.reddit.com/r/pics/comments/abc123/sunset/")
        )
        assertEquals("https://redd.it/abc123", RedditLinkUtils.redditLinkOrNull("redd.it/abc123"))
    }

    @Test
    fun `scheme and host come back lowercased, the rest untouched`() {
        assertEquals(
            "https://www.reddit.com/r/Leathercraft/comments/1qo3jrv/",
            RedditLinkUtils.redditLinkOrNull("HTTPS://WWW.REDDIT.COM/r/Leathercraft/comments/1qo3jrv/")
        )
        assertEquals(
            "https://reddit.com/r/pics/",
            RedditLinkUtils.redditLinkOrNull("Reddit.com/r/pics/")
        )
        // A query keeps its case, and a port and userinfo survive the rebuild.
        assertEquals(
            "https://www.reddit.com/search/?q=Arc+Browser",
            RedditLinkUtils.redditLinkOrNull("https://WWW.Reddit.com/search/?q=Arc+Browser")
        )
        assertEquals(
            "https://www.reddit.com:8443/r/pics/",
            RedditLinkUtils.redditLinkOrNull("https://WWW.REDDIT.COM:8443/r/pics/")
        )
    }

    @Test
    fun `surrounding whitespace is trimmed off`() {
        assertEquals(
            "https://www.reddit.com/r/pics/",
            RedditLinkUtils.redditLinkOrNull("  https://www.reddit.com/r/pics/\n")
        )
    }

    @Test
    fun `a look-alike domain is not a reddit link`() {
        val notReddit = listOf(
            "https://www.reddit.example.com/r/pics/",
            "https://reddit.com.example.net/r/pics/",
            "https://example.com/reddit.com/not-reddit",
            "https://notreddit.com/r/pics/",
            "https://example.com/"
        )

        notReddit.forEach { url ->
            assertEquals(null, RedditLinkUtils.redditLinkOrNull(url))
        }
    }

    @Test
    fun `text that is not an http link is not a reddit link`() {
        val notLinks = listOf(
            null,
            "",
            "   ",
            "just some copied text",
            "ftp://www.reddit.com/r/pics/",
            "mailto:someone@reddit.com",
            "content://media/external/images/media/1",
            "look at this https://www.reddit.com/r/pics/"
        )

        notLinks.forEach { text ->
            assertEquals(null, RedditLinkUtils.redditLinkOrNull(text))
        }
    }

    @Test
    fun `a null permalink stays null`() {
        chooseDomain(RedditLinkUtils.OLD_REDDIT_HOST)

        assertEquals(null, RedditLinkUtils.applyShareDomainOrNull(preferences, null))
        assertEquals(
            "https://old.reddit.com/r/pics/",
            RedditLinkUtils.applyShareDomainOrNull(preferences, "https://www.reddit.com/r/pics/")
        )
    }
}
