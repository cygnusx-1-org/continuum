package ml.docilealligator.infinityforreddit.shadowbox

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import ml.docilealligator.infinityforreddit.post.Post
import ml.docilealligator.infinityforreddit.viewmodels.ViewPostDetailActivityViewModel
import java.util.IdentityHashMap

/**
 * One page per post, plus a trailing end page that shows the load-more state.
 *
 * Item ids are stable and keyed by post identity rather than position, so appending a page of
 * posts is a plain [notifyItemRangeInserted]: the end page keeps its fragment and moves to the
 * new tail instead of being rebound as the first new post. Identity, not fullName, because the
 * swipe list model only dedupes new posts against each other, so a post could in principle appear
 * twice and two positions must still not share an id.
 */
class ShadowboxPagerAdapter(
    activity: FragmentActivity,
    private val viewModel: ViewPostDetailActivityViewModel,
    private val blurPolicy: (Post) -> Boolean,
    private val showPost: (Post) -> Boolean
) : FragmentStateAdapter(activity) {

    private val idsByPost = IdentityHashMap<Post, Long>()
    private val postsById = HashMap<Long, Post>()
    private var nextId = 0L

    /**
     * Indices into the post list, in order, of the posts this mode actually shows.
     *
     * Filtering happens here rather than on the list itself so that a page still knows its post's
     * real index: the feed underneath is keyed on those indices -- the events that repaint a voted
     * row and scroll the feed to the post being read both carry one -- and dropping posts from the
     * list would silently misaddress every one of them.
     */
    private val pages = mutableListOf<Int>()

    /** How many posts are on screen in this mode, excluding the trailing end page. */
    val pageCount: Int
        get() = pages.size

    private fun posts(): List<Post> = viewModel.posts ?: emptyList()

    /** Builds the page list from scratch. Call before the adapter is attached. */
    fun buildPages() {
        pages.clear()
        val posts = posts()
        for (i in posts.indices) {
            if (showPost(posts[i])) {
                pages.add(i)
            }
        }
    }

    /**
     * Takes in posts appended to the list and notifies the pages they add, which is none when a
     * fetched page was all filtered away. Returns how many pages appeared.
     */
    fun appendPages(): Int {
        val oldSize = pages.size
        buildPages()
        val added = pages.size - oldSize
        if (added > 0) {
            notifyItemRangeInserted(oldSize, added)
        }
        return added
    }

    /** The post index a page shows, or -1 for the end page. */
    fun postIndexForPage(page: Int): Int = pages.getOrElse(page) { -1 }

    /** The page showing [postIndex], or the first one after it when that post is filtered out. */
    fun pageForPostIndex(postIndex: Int): Int {
        val page = pages.indexOfFirst { it >= postIndex }
        return if (page < 0) (pages.size - 1).coerceAtLeast(0) else page
    }

    override fun getItemCount(): Int = pages.size + 1

    override fun getItemId(position: Int): Long {
        if (position >= pages.size) {
            return END_PAGE_ID
        }
        val post = posts()[pages[position]]
        return idsByPost.getOrPut(post) {
            val id = nextId++
            postsById[id] = post
            id
        }
    }

    override fun containsItem(itemId: Long): Boolean {
        if (itemId == END_PAGE_ID) {
            return true
        }
        val post = postsById[itemId] ?: return false
        return posts().any { it === post }
    }

    override fun createFragment(position: Int): Fragment {
        if (position >= pages.size) {
            return ShadowboxEndPageFragment()
        }
        // The page's own index into the post list, which is what the page reports back.
        val postIndex = pages[position]
        val post = posts()[postIndex]
        val blur = blurPolicy(post)
        return when (post.postType) {
            Post.IMAGE_TYPE -> {
                val url = post.url
                if (url != null) {
                    ShadowboxImagePageFragment.newInstance(postIndex, blur, url, false)
                } else {
                    ShadowboxPreviewPageFragment.newInstance(postIndex, blur, ShadowboxPreviewPageFragment.KIND_LINK)
                }
            }
            Post.GIF_TYPE -> {
                val mp4 = post.mp4Variant
                val gifUrl = post.videoUrl ?: post.url
                if (mp4 != null) {
                    ShadowboxVideoPageFragment.newInstance(postIndex, blur, mp4, true)
                } else if (gifUrl != null) {
                    ShadowboxImagePageFragment.newInstance(postIndex, blur, gifUrl, true)
                } else {
                    ShadowboxPreviewPageFragment.newInstance(postIndex, blur, ShadowboxPreviewPageFragment.KIND_LINK)
                }
            }
            Post.VIDEO_TYPE -> {
                val videoUrl = post.videoUrl
                // Streamable and short-clip posts carry a page URL until the feed's resolver has
                // run; the full player resolves them itself, so those pages show the preview and
                // hand off on tap. Everything else (v.redd.it HLS, Redgifs, Imgur, MLB) is a direct
                // media URL and plays inline.
                val needsResolver = (post.isStreamable || post.isShortClip) && !post.isLoadedStreamableVideoAlready
                if (videoUrl != null && !needsResolver) {
                    ShadowboxVideoPageFragment.newInstance(postIndex, blur, videoUrl, false)
                } else {
                    ShadowboxPreviewPageFragment.newInstance(postIndex, blur, ShadowboxPreviewPageFragment.KIND_VIDEO)
                }
            }
            Post.GALLERY_TYPE -> ShadowboxGalleryPageFragment.newInstance(postIndex, blur)
            Post.TEXT_TYPE -> ShadowboxTextPageFragment.newInstance(postIndex, blur)
            else -> ShadowboxPreviewPageFragment.newInstance(postIndex, blur, ShadowboxPreviewPageFragment.KIND_LINK)
        }
    }

    companion object {
        private const val END_PAGE_ID = Long.MIN_VALUE
    }
}
