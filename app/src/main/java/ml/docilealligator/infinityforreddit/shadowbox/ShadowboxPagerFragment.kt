package ml.docilealligator.infinityforreddit.shadowbox

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.graphics.Insets
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.github.piasy.biv.BigImageViewer
import com.github.piasy.biv.loader.glide.GlideImageLoader
import ml.docilealligator.infinityforreddit.ImageOkHttpClient
import ml.docilealligator.infinityforreddit.Infinity
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.account.AccountScope
import ml.docilealligator.infinityforreddit.activities.BaseActivity
import ml.docilealligator.infinityforreddit.databinding.FragmentShadowboxPagerBinding
import ml.docilealligator.infinityforreddit.events.NeedForPostListFromPostFragmentEvent
import ml.docilealligator.infinityforreddit.events.PostPositionUpdateEventToPostList
import ml.docilealligator.infinityforreddit.events.PostUpdateEventToPostList
import ml.docilealligator.infinityforreddit.events.ProvidePostListToViewPostDetailActivityEvent
import ml.docilealligator.infinityforreddit.post.LoadingMorePostsStatus
import ml.docilealligator.infinityforreddit.post.Post
import ml.docilealligator.infinityforreddit.post.PostType
import ml.docilealligator.infinityforreddit.postfilter.PostFilter
import ml.docilealligator.infinityforreddit.readpost.ReadPostModification
import ml.docilealligator.infinityforreddit.readpost.ReadPostType
import ml.docilealligator.infinityforreddit.readpost.ReadPostsListInterface
import ml.docilealligator.infinityforreddit.readpost.ReadPostsUtils
import ml.docilealligator.infinityforreddit.thing.SortType
import ml.docilealligator.infinityforreddit.user.UserProfileImagesBatchLoader
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import ml.docilealligator.infinityforreddit.viewmodels.ViewGalleryViewModel
import ml.docilealligator.infinityforreddit.viewmodels.ViewPostDetailActivityViewModel
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import retrofit2.Retrofit
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Named

/**
 * The Slide layout: a pager that flips through the current feed one post per page, sitting in the
 * feed's own fragment so the screen's top bar stays where it is.
 *
 * The post list is not passed in the arguments -- a feed's worth of Parcelable posts blows the
 * Binder limit -- but requested from the feed fragment over EventBus, exactly as
 * ViewPostDetailActivity does for swipe-between-posts, and kept in the same
 * [ViewPostDetailActivityViewModel] so paging past the feed's snapshot reuses its load-more code.
 * Because the handoff is a shallow copy, the pages share Post objects with the feed: a vote here
 * changes the feed's row as soon as it is told to repaint.
 */
class ShadowboxPagerFragment : Fragment() {

    /** Implemented by the feed fragment that holds this one, to say how much the system covers at its bottom edge. */
    interface SlideHost {
        fun getSlideBottomInset(): Int

        /** There is nothing to slide through (no posts, or every one filtered out): show the feed's own list. */
        fun onSlideUnavailable()
    }

    @Inject
    @Named("no_oauth")
    lateinit var retrofit: Retrofit

    @Inject
    @Named("oauth")
    lateinit var oauthRetrofit: Retrofit

    @Inject
    lateinit var redditDataRoomDatabase: RedditDataRoomDatabase

    @Inject
    @Named("default")
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    @Named("post_history")
    lateinit var postHistorySharedPreferences: SharedPreferences

    @Inject
    @Named("nsfw_and_spoiler")
    lateinit var nsfwAndSpoilerSharedPreferences: SharedPreferences

    @Inject
    lateinit var executor: Executor

    @Inject
    lateinit var loader: UserProfileImagesBatchLoader

    private lateinit var host: BaseActivity

    lateinit var viewModel: ViewPostDetailActivityViewModel
        private set
    lateinit var insetsViewModel: ViewGalleryViewModel
        private set

    private var _binding: FragmentShadowboxPagerBinding? = null
    private val binding: FragmentShadowboxPagerBinding
        get() = _binding!!
    private var adapter: ShadowboxPagerAdapter? = null

    /** Index of the post the pager is on; only that page plays media. */
    val currentPage = MutableLiveData<Int>()

    /** Whether the info panel is shown; pages fade their panel on it. */
    val panelVisible = MutableLiveData(true)

    var feedFragmentId = 0L
        private set
    var isNsfwSubreddit = false
        private set
    @PostType
    var postType = PostType.FRONT_PAGE
        private set

    private var launchPosition = 0
    private var pagerScrollState = ViewPager2.SCROLL_STATE_IDLE
    private var swipedAway = false
    private var markPostsAsRead = false
    private var hideTextAndPreviewlessPosts = false

    /** Consecutive fetched pages that this mode's filter emptied, so one run cannot go on forever. */
    private var barrenFetches = 0

    /** How many posts the pages were last built from, so a re-delivered state is recognised. */
    private var builtPostCount = 0

    // The feed's listing parameters, copied from the handoff so load-more asks for the same list.
    private var subredditName: String? = null
    private var concatenatedSubredditNames: String? = null
    private var username: String? = null
    private var userWhere: String? = null
    private var multiPath: String? = null
    private var query: String? = null
    private var sortType: SortType.Type? = null
    private var sortTime: SortType.Time? = null
    private var postFilter: PostFilter? = null
    @ReadPostType
    private var readPostType = ReadPostType.INVALID
    private var readPostsList: ReadPostsListInterface? = null
    private var mediaOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireContext().applicationContext as Infinity).appComponent.inject(this)
        host = requireActivity() as BaseActivity

        // Image pages inflate a BigImageView, which takes the loader installed here.
        BigImageViewer.initialize(
            GlideImageLoader.with(requireContext().applicationContext, ImageOkHttpClient.get(requireContext().applicationContext))
        )

        markPostsAsRead = postHistorySharedPreferences.getBoolean(
            AccountScope.key(host.accountName, SharedPreferencesUtils.MARK_POSTS_AS_READ_BASE), false
        )
        hideTextAndPreviewlessPosts = sharedPreferences.getBoolean(
            SharedPreferencesUtils.SHADOWBOX_HIDE_TEXT_AND_PREVIEWLESS_POSTS, false
        )

        val args = requireArguments()
        feedFragmentId = args.getLong(ARG_POST_FRAGMENT_ID, 0L)
        launchPosition = args.getInt(ARG_POST_LIST_POSITION, 0)
        isNsfwSubreddit = args.getBoolean(ARG_IS_NSFW_SUBREDDIT, false)

        viewModel = ViewModelProvider(
            this,
            ViewPostDetailActivityViewModel.provideFactory(
                retrofit, oauthRetrofit, redditDataRoomDatabase, host.accessToken, loader
            )
        )[ViewPostDetailActivityViewModel::class.java]
        insetsViewModel = ViewModelProvider(this)[ViewGalleryViewModel::class.java]

        // Registered before the request below, which is answered on the spot.
        EventBus.getDefault().register(this)
        requestPostList()
    }

    /**
     * Asks the feed fragment for its posts and listing parameters. Done on every start rather than
     * once: a fragment restored after a configuration change comes back with none of the
     * parameters, and without them every later fetch would give up.
     */
    private fun requestPostList() {
        if (feedFragmentId > 0) {
            EventBus.getDefault().post(NeedForPostListFromPostFragmentEvent(feedFragmentId))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentShadowboxPagerBinding.inflate(inflater, container, false)
        buildPager()
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        requestPostList()
        applyHostInsets()
        if (adapter == null && viewModel.posts.isNullOrEmpty()) {
            buildPager()
        }
        if (adapter == null) {
            (parentFragment as? SlideHost)?.onSlideUnavailable()
        }
    }

    private fun applyHostInsets() {
        val bottom = (parentFragment as? SlideHost)?.getSlideBottomInset() ?: 0
        setBottomInset(bottom)
    }

    /** The system bars cover this much of the bottom edge; the pages keep their panels clear of it. */
    fun setBottomInset(bottom: Int) {
        insetsViewModel.setInsets(Insets.of(0, 0, 0, bottom))
    }

    private fun buildPager() {
        val posts = viewModel.posts
        if (_binding == null || posts.isNullOrEmpty()) {
            return
        }
        val adapter = ShadowboxPagerAdapter(
            childFragmentManager, viewLifecycleOwner.lifecycle, viewModel, ::shouldBlur, ::shouldShowPost
        )
        adapter.buildPages()
        builtPostCount = posts.size
        if (adapter.pageCount == 0) {
            // Every post the feed handed over was filtered out by the hide-text-and-previewless
            // setting.
            Toast.makeText(requireContext(), R.string.shadowbox_no_posts, Toast.LENGTH_SHORT).show()
            return
        }
        this.adapter = adapter
        val pager = binding.viewPager2ShadowboxPager
        pager.adapter = adapter
        // Build the page either side of the one in front, rather than when the swipe starts.
        //
        // This is what stops a page from drawing its picture in place after the swipe has landed.
        // A pager left at its default builds the next page during the drag, so its images are
        // fetched with a frame or two to spare, and a gallery -- three full-width tiles, fifteen
        // megabytes decoded -- cannot be held in Glide's memory cache across two neighbours to
        // make up for it. A page built early has its pictures in its own views, which no cache
        // evicts. The cost is that a video page either side prepares its player, the same trade
        // the feed makes for autoplay.
        pager.offscreenPageLimit = 1
        // Swiping sideways here is for the next post, and only that. The screen this sits in may
        // be a pager of its own -- a subreddit's Posts and About tabs -- which would otherwise take
        // over a swipe that starts on the last slide, or one that drifts, and change tabs. Taking
        // the gesture on the way down keeps the outer pager out of it; the tabs are still there to
        // tap.
        (pager.getChildAt(0) as? RecyclerView)?.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                    rv.parent?.requestDisallowInterceptTouchEvent(true)
                }
                return false
            }
        })
        // A view rebuilt around a surviving model goes back to the post it was on.
        val resumeAt = currentPage.value ?: launchPosition
        val startPosition = resumeAt.coerceIn(0, posts.size - 1)
        val launchPage = adapter.pageForPostIndex(startPosition)
        // The post the feed was on may itself be filtered out; the page the pager opens on is the
        // first one kept at or after it, and that is the post to measure a swipe against.
        launchPosition = adapter.postIndexForPage(launchPage)
        pager.setCurrentItem(launchPage, false)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                pagerScrollState = state
            }

            override fun onPageSelected(position: Int) {
                onPageShown(position)
            }
        })
        // setCurrentItem(launch, false) before the callback was registered dispatched nothing to
        // it, so the launch page gets its side effects by hand.
        onPageShown(launchPage)

        viewModel.loadMorePostsState.observe(viewLifecycleOwner) { state -> onLoadMoreState(state) }
    }

    /** [page] indexes the pager; the post it shows may sit further along the list when filtering. */
    private fun onPageShown(page: Int) {
        val adapter = this.adapter ?: return
        val postIndex = adapter.postIndexForPage(page)
        currentPage.value = postIndex
        val posts = viewModel.posts ?: return
        if (page > adapter.pageCount - LOAD_MORE_THRESHOLD) {
            fetchMorePosts()
        }
        stopPlaybackExcept(postIndex)
        if (postIndex in posts.indices) {
            markPostRead(posts[postIndex], postIndex)
        }
        notifyFeedOfCurrentPost(posts, postIndex)
    }

    /**
     * Stops every page except the one in front. Driven from here rather than left to each page's
     * own page-change observer: only one page may play at a time, and that has to hold even if a
     * page never hears that it is no longer current.
     */
    private fun stopPlaybackExcept(postIndex: Int) {
        for (fragment in childFragmentManager.fragments) {
            if (fragment is ShadowboxPageFragment && fragment.position != postIndex) {
                fragment.pausePlayback()
            }
        }
    }

    /** Asks for the next page of the feed's listing; the model ignores the call while one is in flight or the list is done. */
    fun fetchMorePosts() {
        viewModel.fetchMorePosts(
            host.accessToken, host.accountName, false, postType,
            subredditName, concatenatedSubredditNames, username,
            userWhere, multiPath, query, sortType, sortTime, postFilter,
            readPostType, readPostsList, mediaOnly
        )
    }

    private fun onLoadMoreState(state: ViewPostDetailActivityViewModel.LoadMorePostsState) {
        if (state.status != LoadingMorePostsStatus.LOADED) {
            return
        }
        val adapter = this.adapter ?: return
        // The state is a StateFlow behind a LiveData, so leaving this screen and coming back to it
        // re-delivers whatever it last held. Nothing was appended in between, and treating that as
        // a fetch this mode emptied would both spend a barren attempt and go asking for a page the
        // user never swiped towards.
        val postCount = viewModel.posts?.size ?: 0
        if (postCount == builtPostCount) {
            return
        }
        builtPostCount = postCount
        // Read the page the user is on before the insert moves the end page along, and take the
        // count from the adapter rather than from the event: while this screen is stopped the
        // model can append more than once and LiveData delivers only the last of those.
        val oldPageCount = adapter.pageCount
        val wasOnEndPage = binding.viewPager2ShadowboxPager.currentItem == oldPageCount
        val added = adapter.appendPages()
        if (added == 0) {
            // The whole fetched page was filtered out. Keep asking, bounded, or the user is left
            // on the end page with posts loaded behind it.
            if (++barrenFetches < MAX_BARREN_FETCHES) {
                fetchMorePosts()
            }
            return
        }
        barrenFetches = 0
        if (wasOnEndPage) {
            // Parked on the end page while it loaded: slide onto the first post that arrived.
            binding.viewPager2ShadowboxPager.post {
                _binding?.viewPager2ShadowboxPager?.setCurrentItem(oldPageCount, true)
            }
        }
    }

    /**
     * Whether this mode shows the post at all: everything, unless "hide text posts and posts with
     * no preview" is on, in which case a post has to be something other than a text post and have
     * a preview image.
     */
    private fun shouldShowPost(post: Post): Boolean =
        !hideTextAndPreviewlessPosts || ShadowboxPreviews.hasPreviewToShow(post)

    /**
     * Marks the post read the way opening it from the feed would, under the same "mark posts as
     * read" setting, and tells the feed row to recolour.
     */
    private fun markPostRead(post: Post, position: Int) {
        if (post.isRead || !markPostsAsRead) {
            return
        }
        markRead(post, position)
    }

    /** The panel's read-after-voting path; that setting is checked by the panel itself. */
    fun markPostReadAfterVoting(post: Post, position: Int) {
        if (post.isRead) {
            return
        }
        markRead(post, position)
    }

    private fun markRead(post: Post, position: Int) {
        post.markAsRead()
        ReadPostModification.insertReadPost(
            redditDataRoomDatabase, executor, host.accountName, post.id, ReadPostType.READ_POSTS,
            ReadPostsUtils.GetReadPostsLimit(host.accountName, postHistorySharedPreferences)
        )
        EventBus.getDefault().post(PostUpdateEventToPostList(post, position))
    }

    /**
     * Keeps the feed in step with the pager, so a switch back to a list layout lands on the post
     * being looked at. Ported from ViewPostDetailActivity.notifyPostListOfCurrentPost, guards
     * included: nothing is reported until the pager has actually left the launch page, and never
     * for an idle-state callback, which is a layout or data-set change substituting a page rather
     * than a swipe.
     */
    private fun notifyFeedOfCurrentPost(posts: List<Post>, position: Int) {
        if (feedFragmentId <= 0 || posts.isEmpty() || position < 0) {
            return
        }
        if (pagerScrollState == ViewPager2.SCROLL_STATE_IDLE) {
            return
        }
        val postPosition = minOf(position, posts.size - 1)
        if (postPosition != launchPosition) {
            swipedAway = true
        }
        if (!swipedAway) {
            return
        }
        val post = posts[postPosition]
        EventBus.getDefault().postSticky(
            PostPositionUpdateEventToPostList(feedFragmentId, postPosition, post.fullName)
        )
    }

    private fun shouldBlur(post: Post): Boolean {
        val needBlurNsfw = nsfwAndSpoilerSharedPreferences.getBoolean(
            AccountScope.key(host.accountName, SharedPreferencesUtils.BLUR_NSFW_BASE), true
        )
        val doNotBlurNsfwInNsfwSubreddits = nsfwAndSpoilerSharedPreferences.getBoolean(
            AccountScope.key(host.accountName, SharedPreferencesUtils.DO_NOT_BLUR_NSFW_IN_NSFW_SUBREDDITS), false
        )
        val needBlurSpoiler = nsfwAndSpoilerSharedPreferences.getBoolean(
            AccountScope.key(host.accountName, SharedPreferencesUtils.BLUR_SPOILER_BASE), false
        )
        return (post.isNSFW && needBlurNsfw && !(doNotBlurNsfwInNsfwSubreddits && isNsfwSubreddit))
                || (post.isSpoiler && needBlurSpoiler)
    }

    /** Shows or hides the info panel; the screen's top bar and the system bars stay as they are. */
    fun toggleChrome() {
        panelVisible.value = panelVisible.value == false
    }

    /** The page fragment showing [position], if the pager has one built. */
    private fun pageAt(position: Int): ShadowboxPageFragment? {
        for (fragment in childFragmentManager.fragments) {
            if (fragment is ShadowboxPageFragment && fragment.position == position) {
                return fragment
            }
        }
        return null
    }

    @Subscribe
    fun onProvidePostListEvent(event: ProvidePostListToViewPostDetailActivityEvent) {
        if (event.postFragmentId != feedFragmentId) {
            return
        }
        // The list is taken only once: the model's own may already have grown past the feed's
        // snapshot, and replacing it would throw those posts away. The listing parameters are
        // taken every time, because they are this fragment's and a recreated one has none.
        if (viewModel.posts == null) {
            viewModel.posts = event.posts
        }
        postType = event.postType
        subredditName = event.subredditName
        concatenatedSubredditNames = event.concatenatedSubredditNames
        username = event.username
        userWhere = event.userWhere
        multiPath = event.multiPath
        query = event.query
        readPostType = event.readPostType
        postFilter = event.postFilter
        mediaOnly = event.mediaOnly
        event.sortType?.let {
            sortType = it.type
            sortTime = it.time
        }
        readPostsList = event.readPostsList
    }

    /**
     * A vote, save, hide or moderation action from the comments screen or the options sheet. The
     * comments screen sends a copy of the post rather than ours, so the fields are copied over
     * before the page's panel is redrawn.
     */
    @Subscribe
    fun onPostUpdateEvent(event: PostUpdateEventToPostList) {
        val posts = viewModel.posts ?: return
        val index = posts.indexOfFirst { it.fullName == event.post.fullName }
        if (index < 0) {
            return
        }
        val post = posts[index]
        if (post !== event.post) {
            post.voteType = event.post.voteType
            post.score = event.post.score
            post.nComments = event.post.nComments
            post.isSaved = event.post.isSaved
            post.isHidden = event.post.isHidden
            if (event.post.isRead) {
                post.markAsRead()
            }
        }
        pageAt(index)?.rebindPanel()
    }

    override fun onResume() {
        super.onResume()
        for (fragment in childFragmentManager.fragments) {
            if (fragment is ShadowboxPageFragment) {
                fragment.restoreMedia()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Leaving the screen -- the full viewer, the comments, anywhere -- silences the pages that
        // are not in front: the page in front remembers to resume on the way back; these have
        // nothing to come back to. Then every page lets go of its player, so the screen opening
        // on top finds the decoders free; see ShadowboxPageFragment.releaseMedia.
        stopPlaybackExcept(currentPage.value ?: -1)
        for (fragment in childFragmentManager.fragments) {
            if (fragment is ShadowboxPageFragment) {
                fragment.releaseMedia()
            }
        }
    }

    override fun onDestroyView() {
        adapter = null
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    companion object {
        private const val ARG_POST_FRAGMENT_ID = "EPFI"
        private const val ARG_POST_LIST_POSITION = "EPLP"
        private const val ARG_IS_NSFW_SUBREDDIT = "EINS"

        /** How close to the end of the list a page has to be before the next page is requested. */
        private const val LOAD_MORE_THRESHOLD = 5

        /** How many fetches this mode's filter may empty before it stops asking for more. */
        private const val MAX_BARREN_FETCHES = 5

        /** [feedFragmentId] is the feed fragment to ask for posts; [position] the post to open on. */
        @JvmStatic
        fun newInstance(feedFragmentId: Long, position: Int, isNsfwSubreddit: Boolean): ShadowboxPagerFragment {
            val fragment = ShadowboxPagerFragment()
            fragment.arguments = Bundle().apply {
                putLong(ARG_POST_FRAGMENT_ID, feedFragmentId)
                putInt(ARG_POST_LIST_POSITION, position)
                putBoolean(ARG_IS_NSFW_SUBREDDIT, isNsfwSubreddit)
            }
            return fragment
        }
    }
}
