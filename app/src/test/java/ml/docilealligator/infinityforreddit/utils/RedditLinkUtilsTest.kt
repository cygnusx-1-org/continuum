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
 * The Reddit-link helpers: recognizing a link as Reddit's, and Settings > Miscellaneous > Use
 * old.reddit.com applied to links on their way out of the app.
 *
 * The setting is opt-in and deliberately narrow: only the hosts that serve the same paths as
 * old.reddit.com may be rewritten. Sending a media or share-shortener host to old.reddit.com
 * produces a URL that does not resolve, and the person who receives the shared link is the one who
 * finds out.
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

    private fun turnOldRedditOn() {
        preferences.edit().putBoolean(SharedPreferencesUtils.USE_OLD_REDDIT_DOMAIN, true).commit()
    }

    @Test
    fun `links are left alone until the setting is turned on`() {
        val url = "https://www.reddit.com/r/pics/comments/abc123/sunset/"

        assertEquals(url, RedditLinkUtils.applyLinkDomain(preferences, url))

        preferences.edit().putBoolean(SharedPreferencesUtils.USE_OLD_REDDIT_DOMAIN, false).commit()
        assertEquals(url, RedditLinkUtils.applyLinkDomain(preferences, url))
    }

    @Test
    fun `only the hosts that serve old reddit's paths are rewritten`() {
        turnOldRedditOn()

        assertEquals(
            "https://old.reddit.com/r/pics/comments/abc123/sunset/",
            RedditLinkUtils.applyLinkDomain(
                preferences, "https://www.reddit.com/r/pics/comments/abc123/sunset/"
            )
        )
        assertEquals(
            "https://old.reddit.com/r/pics/",
            RedditLinkUtils.applyLinkDomain(preferences, "https://reddit.com/r/pics/")
        )
    }

    @Test
    fun `media and share-shortener hosts are not old reddit and pass through`() {
        turnOldRedditOn()

        val untouched = listOf(
            "https://i.redd.it/abc123.jpg",
            "https://v.redd.it/abc123/DASH_720.mp4",
            "https://preview.redd.it/abc123.jpg?width=640",
            "https://redd.it/abc123",
            "https://s.reddit.com/abc123",
            "https://reddit.app.link/abc123",
            "https://example.com/reddit.com/not-reddit",
            "https://www.reddit.example.com/r/pics/"
        )

        untouched.forEach { url ->
            assertEquals(url, RedditLinkUtils.applyLinkDomain(preferences, url))
        }
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
        turnOldRedditOn()

        assertEquals(null, RedditLinkUtils.applyLinkDomainOrNull(preferences, null))
        assertEquals(
            "https://old.reddit.com/r/pics/",
            RedditLinkUtils.applyLinkDomainOrNull(preferences, "https://www.reddit.com/r/pics/")
        )
    }
}
