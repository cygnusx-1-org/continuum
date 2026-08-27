package ml.docilealligator.infinityforreddit.randomsubreddit

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What survives one `/api/info?sr_name=` batch, which is the whole reason a random pick can be
 * trusted: the name lists are archive-derived and roughly one in six of the subreddits in the
 * source archive was already banned when it was checked, so the response -- not the file -- decides
 * where anyone is sent.
 *
 * Reddit says no in two different ways and only one of them is visible in the response, so the
 * absent-name case below is the one worth guarding: it looks like nothing at all.
 *
 * Robolectric supplies Android's real {@code org.json}; the JVM stub would return defaults and
 * quietly agree with any assertion here. The stock [Application] is enough -- no Dagger graph is
 * involved in parsing a string.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RandomSubredditInfoParserTest {

    private fun child(name: String, subscribers: String, over18: String) =
        """{"kind": "t5", "data": {"display_name": "$name", "subscribers": $subscribers, "over18": $over18}}"""

    private fun listing(vararg children: String) =
        """{"kind": "Listing", "data": {"after": null, "dist": ${children.size}, "children": [${children.joinToString(",")}]}}"""

    private fun post() = """{"kind": "t3", "data": {"id": "abc123", "title": "a post"}}"""

    @Test
    fun countsThePostsInAListing() {
        assertEquals(0, RandomSubredditInfoParser.countPosts(listing()))
        assertEquals(1, RandomSubredditInfoParser.countPosts(listing(post())))
        assertEquals(3, RandomSubredditInfoParser.countPosts(listing(post(), post(), post())))
    }

    @Test
    fun anUnreadableListingIsUnknownRatherThanEmpty() {
        // The distinction is the whole point: 0 discards the subreddit, UNKNOWN keeps it. A body
        // we could not parse is our problem, not the subreddit's, so it must never read as "empty"
        // -- that would let one bad response permanently skip a perfectly good subreddit.
        val broken = listOf("", "{", "null", """{"error": 404}""", """{"data": {}}""")
        for (body in broken) {
            // Asserted against the literal 0, not against UNKNOWN_POST_COUNT: comparing the
            // constant to itself would still pass if the constant were changed to 0, which is
            // precisely the regression this guards.
            assertNotEquals(
                "an unparseable body must not read as empty: $body",
                0,
                RandomSubredditInfoParser.countPosts(body)
            )
            assertEquals(
                RandomSubredditInfoParser.UNKNOWN_POST_COUNT,
                RandomSubredditInfoParser.countPosts(body)
            )
        }
    }

    @Test
    fun keepsOnlyLiveSubredditsMatchingTheRequestedFlag() {
        val response = listing(
            child("AskReddit", subscribers = "59482818", over18 = "false"),
            child("pics", subscribers = "33512996", over18 = "false"),
            child("someNsfwSub", subscribers = "1234", over18 = "true")
        )

        assertEquals(
            listOf("AskReddit", "pics"),
            RandomSubredditInfoParser.parseLiveSubredditNames(response, requireOver18 = false)
        )
        assertEquals(
            listOf("someNsfwSub"),
            RandomSubredditInfoParser.parseLiveSubredditNames(response, requireOver18 = true)
        )
    }

    @Test
    fun dropsBannedSubredditsWhoseSubscriberCountIsNull() {
        // How a banned or private name comes back: present, a t5, and null right through.
        val response = listing(
            child("AskReddit", subscribers = "59482818", over18 = "false"),
            child("random", subscribers = "null", over18 = "null")
        )

        assertEquals(
            listOf("AskReddit"),
            RandomSubredditInfoParser.parseLiveSubredditNames(response, requireOver18 = false)
        )
    }

    @Test
    fun namesMissingFromTheResponseAreTreatedTheSameAsNullSubscribers() {
        // A deleted or never-existing subreddit is not reported as dead -- it is simply not
        // mentioned. Asking for four names and being told about one must yield exactly that one.
        val response = listing(child("pics", subscribers = "33512996", over18 = "false"))

        val survivors = RandomSubredditInfoParser.parseLiveSubredditNames(response, requireOver18 = false)

        assertEquals(listOf("pics"), survivors)
        for (requestedButAbsent in listOf("thisdefinitelydoesnotexist12345", "alsoGone", "andThisOne")) {
            assertTrue(
                "$requestedButAbsent was never in the response and must not be picked",
                !survivors.contains(requestedButAbsent)
            )
        }
    }

    @Test
    fun anEmptyOrUnreadableResponseYieldsNothingRatherThanThrowing() {
        // A pick with no survivors is a failed pick, which the caller turns into a toast. A crash
        // on a truncated body would not be.
        assertEquals(emptyList<String>(), RandomSubredditInfoParser.parseLiveSubredditNames("", false))
        assertEquals(emptyList<String>(), RandomSubredditInfoParser.parseLiveSubredditNames("{\"error\": 404}", false))
        assertEquals(emptyList<String>(), RandomSubredditInfoParser.parseLiveSubredditNames(listing(), false))
    }

    @Test
    fun ignoresChildrenThatAreNotSubreddits() {
        val response =
            """{"kind": "Listing", "data": {"children": [
                {"kind": "t3", "data": {"display_name": "notASubreddit", "subscribers": 5, "over18": false}},
                ${child("pics", subscribers = "33512996", over18 = "false")}
            ]}}"""

        assertEquals(
            listOf("pics"),
            RandomSubredditInfoParser.parseLiveSubredditNames(response, requireOver18 = false)
        )
    }
}
