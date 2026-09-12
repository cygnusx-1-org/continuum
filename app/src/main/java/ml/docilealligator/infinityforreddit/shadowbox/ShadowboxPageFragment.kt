package ml.docilealligator.infinityforreddit.shadowbox

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.core.graphics.Insets
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.RequestOptions
import jp.wasabeef.glide.transformations.BlurTransformation
import ml.docilealligator.infinityforreddit.Infinity
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper
import ml.docilealligator.infinityforreddit.databinding.FragmentShadowboxPageBinding
import ml.docilealligator.infinityforreddit.managers.VideoMuteManager
import ml.docilealligator.infinityforreddit.post.Post
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import ml.docilealligator.infinityforreddit.utils.Utils
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Named

/**
 * One Shadowbox page: the post's media on black with the shared info panel over its bottom edge.
 *
 * Subclasses put their media view into the media slot and load it in [loadMedia]; the base class
 * owns the panel, the NSFW/spoiler blur overlay, the chrome toggle, the insets and the
 * active-page notifications that gate playback.
 */
@OptIn(UnstableApi::class)
abstract class ShadowboxPageFragment : Fragment() {

    @Inject
    @Named("default")
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    @Named("post_history")
    lateinit var postHistorySharedPreferences: SharedPreferences

    @Inject
    @Named("no_oauth")
    lateinit var retrofit: Retrofit

    @Inject
    @Named("oauth")
    lateinit var oauthRetrofit: Retrofit

    @Inject
    lateinit var redditDataRoomDatabase: RedditDataRoomDatabase

    @Inject
    lateinit var executor: Executor

    @Inject
    lateinit var customThemeWrapper: CustomThemeWrapper

    @Inject
    @Named("media3")
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var simpleCache: SimpleCache

    @Inject
    lateinit var videoMuteManager: VideoMuteManager

    protected lateinit var host: ShadowboxActivity
    protected lateinit var glide: RequestManager

    /** Index of this page's post in the activity's list. */
    var position = -1
        private set

    /** The post this page shows; set before [onCreateMediaView] runs. */
    protected lateinit var post: Post

    /** Whether the media is behind the NSFW/spoiler overlay until tapped. */
    protected var blur = false
        private set

    /** Whether the overlay has been tapped away (always true for pages that never had one). */
    protected var revealed = false
        private set

    private var _binding: FragmentShadowboxPageBinding? = null
    private val binding: FragmentShadowboxPageBinding
        get() = _binding!!
    /** This page's info panel, from just after [onCreateMediaView] until the view goes away. */
    protected var panel: ShadowboxInfoPanel? = null
        private set
    private var active = false
    protected var maxResolution = 5000000
        private set
    protected var dataSavingMode = false
        private set

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (context.applicationContext as Infinity).appComponent.inject(this)
        host = context as ShadowboxActivity
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        position = requireArguments().getInt(ARG_POSITION)
        blur = requireArguments().getBoolean(ARG_BLUR, false)
        maxResolution = SharedPreferencesUtils.getInt(sharedPreferences, SharedPreferencesUtils.POST_FEED_MAX_RESOLUTION, "5000000")
        dataSavingMode = when (sharedPreferences.getString(SharedPreferencesUtils.DATA_SAVING_MODE, SharedPreferencesUtils.DATA_SAVING_MODE_OFF)) {
            SharedPreferencesUtils.DATA_SAVING_MODE_ALWAYS -> true
            SharedPreferencesUtils.DATA_SAVING_MODE_ONLY_ON_CELLULAR_DATA ->
                Utils.getConnectedNetwork(requireContext()) == Utils.NETWORK_TYPE_CELLULAR
            else -> false
        }
    }

    final override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentShadowboxPageBinding.inflate(inflater, container, false)
        glide = Glide.with(this)
        // The list is gone only after a process death, and the activity finishes itself before the
        // pager is rebuilt; a page restored by the fragment manager in between just stays blank.
        val post = host.viewModel.getPost(position) ?: return binding.root
        this.post = post

        onCreateMediaView(inflater, binding.mediaContainerShadowboxPageFragment)

        val panel = ShadowboxInfoPanel(
            binding.infoPanelShadowboxPageFragment, host, glide, retrofit, oauthRetrofit,
            redditDataRoomDatabase, executor, sharedPreferences, postHistorySharedPreferences,
            customThemeWrapper, host.supportFragmentManager, host.isNsfwSubreddit,
            host::markPostReadAfterVoting, ::openFullViewer
        )
        panel.bind(post, position)
        this.panel = panel
        onPanelReady(panel)

        val panelRoot = binding.infoPanelShadowboxPageFragment.root
        // A shown panel swallows presses on its own background, so a miss next to the upvote
        // button does not reach the media behind it and collapse the bar.
        panelRoot.isClickable = true
        panelRoot.alpha = if (host.panelVisible.value == true) 1f else 0f
        panelRoot.visibility = if (host.panelVisible.value == true) View.VISIBLE else View.INVISIBLE
        host.panelVisible.observe(viewLifecycleOwner) { visible ->
            // A hidden panel goes INVISIBLE once it has faded, so it stops taking presses meant
            // for the media underneath -- an INVISIBLE view is not offered them at all. It is the
            // panel as a whole that is taken out, never its children's clickability: a TextView
            // made clickable swallows the press the row around it would have handled, which is
            // what stopped the header from opening the comments. INVISIBLE is still measured and
            // laid out, so the panel height the pages pad themselves by does not move.
            if (visible) {
                panelRoot.visibility = View.VISIBLE
                panelRoot.animate().alpha(1f).setDuration(PANEL_FADE_MS).start()
            } else {
                panelRoot.animate().alpha(0f).setDuration(PANEL_FADE_MS).withEndAction {
                    // Cancelling an animation runs its end action too, so a fade-out interrupted
                    // by a fade-in would otherwise hide the panel it was bringing back.
                    if (host.panelVisible.value == false) {
                        panelRoot.visibility = View.INVISIBLE
                    }
                }.start()
            }
            onChromeVisibilityChanged(visible)
        }
        host.insetsViewModel.insets.observe(viewLifecycleOwner) { insets -> applyInsets(insets) }
        host.currentPage.observe(viewLifecycleOwner) { page ->
            // Re-asserted on every page change rather than only on a transition. Playback has to
            // follow from "is this the page in front", and a flag that ever fell out of step --
            // a page built while it was already current, say -- left a video playing behind the
            // page the user had swiped to, with no way to reach it.
            active = page == position
            if (active) onPageActive() else onPageInactive()
        }
        binding.infoPanelShadowboxPageFragment.root.addOnLayoutChangeListener { v, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                onPanelHeightChanged(bottom - top)
            }
        }

        if (blur) {
            showBlurOverlay()
        } else {
            revealed = true
            loadMedia()
        }
        return binding.root
    }

    private fun showBlurOverlay() {
        val overlay = binding.blurOverlayShadowboxPageFragment
        overlay.visibility = View.VISIBLE
        binding.blurLabelTextViewShadowboxPageFragment.setText(if (post.isNSFW) R.string.nsfw else R.string.spoiler)
        host.typeface?.let { binding.blurLabelTextViewShadowboxPageFragment.typeface = it }
        val preview = ShadowboxPreviews.bestPreview(post, maxResolution, dataSavingMode)
        if (preview != null) {
            glide.load(preview.previewUrl)
                .apply(RequestOptions.bitmapTransform(BlurTransformation(50, 10)))
                .into(binding.blurImageViewShadowboxPageFragment)
        }
        overlay.setOnClickListener {
            revealed = true
            glide.clear(binding.blurImageViewShadowboxPageFragment)
            overlay.visibility = View.GONE
            loadMedia()
            if (active) {
                onPageActive()
            }
        }
    }

    private fun applyInsets(insets: Insets) {
        binding.infoPanelShadowboxPageFragment.root.updatePadding(
            left = insets.left, right = insets.right, bottom = insets.bottom
        )
        onInsetsChanged(insets)
    }

    /** Redraws the panel from the post after something outside this page changed it. */
    fun rebindPanel() {
        panel?.rebind()
    }

    protected fun toggleChrome() {
        host.toggleChrome()
    }

    /**
     * The app's rule for a full-screen media screen -- a single tap on the content hides the bar
     * and the system bars, another brings them back -- applied to a scrolling page.
     *
     * A plain click listener is not enough on a RecyclerView: its children take the touch first,
     * so a markdown block with a movement method, or a gallery tile, would swallow the tap. This
     * watches the touch stream on the way in and never consumes it, so scrolling and whatever a
     * child does with the tap still work.
     */
    protected fun addTapToToggleChrome(recyclerView: RecyclerView) {
        val detector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                toggleChrome()
                return false
            }
        })
        recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                detector.onTouchEvent(e)
                return false
            }
        })
    }

    protected fun openComments() {
        panel?.openComments()
    }

    /** Inflate the media view into [container]; [post] is set. Do not load anything yet. */
    protected abstract fun onCreateMediaView(inflater: LayoutInflater, container: ViewGroup)

    /** Start loading the media; called once, after any blur overlay has been tapped away. */
    protected abstract fun loadMedia()

    /** Open the existing full-screen viewer for this post (the panel's fullscreen button). */
    protected abstract fun openFullViewer()

    /** The pager landed on this page (and the media is revealed): start playing. */
    protected open fun onPageActive() {}

    /** The pager left this page: stop playing. */
    protected open fun onPageInactive() {
        pausePlayback()
    }

    /**
     * Stop whatever this page is playing, right now.
     *
     * Public and called by the host on every page change, for every page that is not the one in
     * front. Page-change notifications reach a page through a LiveData observer tied to its view
     * lifecycle, and anything that keeps one from arriving -- a view not yet created, a page the
     * pager rebuilt -- used to leave a video playing behind the page the user had swiped to. This
     * does not depend on that, or on any flag the page keeps.
     */
    open fun pausePlayback() {}

    /** The page's panel has been built and bound; a page can add its own controls to it here. */
    protected open fun onPanelReady(panel: ShadowboxInfoPanel) {}

    /** The bar and the system bars were just shown or hidden. */
    protected open fun onChromeVisibilityChanged(visible: Boolean) {}

    protected open fun onInsetsChanged(insets: Insets) {}

    protected open fun onPanelHeightChanged(height: Int) {}

    override fun onDestroyView() {
        panel = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_POSITION = "AP"
        const val ARG_BLUR = "AB"
        private const val PANEL_FADE_MS = 150L

        @JvmStatic
        protected fun baseArguments(position: Int, blur: Boolean): Bundle {
            val args = Bundle()
            args.putInt(ARG_POSITION, position)
            args.putBoolean(ARG_BLUR, blur)
            return args
        }
    }
}
