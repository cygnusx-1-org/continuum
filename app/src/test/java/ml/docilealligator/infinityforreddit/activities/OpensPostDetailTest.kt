package ml.docilealligator.infinityforreddit.activities

import ml.docilealligator.infinityforreddit.TestInfinity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [LinkResolverActivity.opensPostDetail] decides whether a long-pressed link is offered an "Open in
 * New Window" entry, so it has to agree with where `handleUri` would actually send that link. The
 * two are separate code, and the failure when they disagree is silent: the menu entry appears and
 * opens a post-detail window on something that is not a post.
 *
 * The site-wide `/search` is the case that makes this worth pinning. It is a single path segment,
 * which is exactly the shape of the subreddit-less post permalink `POST_PATTERN_3` matches, so it
 * takes a deliberate check ahead of the post patterns in both places to keep a search link out of
 * the post-detail screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class OpensPostDetailTest {

    @Test
    fun `search links do not open post detail`() {
        assertFalse(LinkResolverActivity.opensPostDetail("https://www.reddit.com/search/?q=vivaldi"))
        assertFalse(LinkResolverActivity.opensPostDetail("https://www.reddit.com/search?q=vivaldi"))
        assertFalse(
            LinkResolverActivity.opensPostDetail(
                "https://old.reddit.com/r/ArcBrowser/search/?q=vivaldi&restrict_sr=on&sort=new"
            )
        )
        assertFalse(LinkResolverActivity.opensPostDetail("https://www.reddit.com/user/GallowBoob/search/?q=the"))
        assertFalse(
            LinkResolverActivity.opensPostDetail(
                "https://www.reddit.com/user/someone/m/pictures/search/?q=sunset&restrict_sr=on"
            )
        )
        assertFalse(LinkResolverActivity.opensPostDetail("https://www.reddit.com/me/m/pictures/search/?q=sunset"))
    }

    @Test
    fun `post links still open post detail`() {
        assertTrue(
            LinkResolverActivity.opensPostDetail(
                "https://www.reddit.com/r/ArcBrowser/comments/1n2p4qk/some_title/"
            )
        )
        assertTrue(LinkResolverActivity.opensPostDetail("https://www.reddit.com/comments/1n2p4qk"))
        assertTrue(LinkResolverActivity.opensPostDetail("https://redd.it/1n2p4qk"))
    }

    @Test
    fun `listing links do not open post detail`() {
        assertFalse(LinkResolverActivity.opensPostDetail("https://www.reddit.com/r/ArcBrowser/new/"))
        assertFalse(LinkResolverActivity.opensPostDetail("https://www.reddit.com/user/someone/m/pictures/"))
    }
}
