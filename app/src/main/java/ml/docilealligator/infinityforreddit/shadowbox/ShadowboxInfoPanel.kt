package ml.docilealligator.infinityforreddit.shadowbox

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import jp.wasabeef.glide.transformations.RoundedCornersTransformation
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.account.Account
import ml.docilealligator.infinityforreddit.account.AccountScope
import ml.docilealligator.infinityforreddit.activities.BaseActivity
import ml.docilealligator.infinityforreddit.activities.ViewPostDetailActivity
import ml.docilealligator.infinityforreddit.asynctasks.LoadSubredditIcon
import ml.docilealligator.infinityforreddit.bottomsheetfragments.PostOptionsBottomSheetFragment
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper
import ml.docilealligator.infinityforreddit.databinding.ShadowboxInfoPanelBinding
import ml.docilealligator.infinityforreddit.events.PostUpdateEventToPostDetailFragment
import ml.docilealligator.infinityforreddit.events.PostUpdateEventToPostList
import ml.docilealligator.infinityforreddit.localsaved.LocalSaved
import ml.docilealligator.infinityforreddit.post.Post
import ml.docilealligator.infinityforreddit.readpost.ReadPostModification
import ml.docilealligator.infinityforreddit.readpost.ReadPostType
import ml.docilealligator.infinityforreddit.readpost.ReadPostsUtils
import ml.docilealligator.infinityforreddit.thing.SaveThing
import ml.docilealligator.infinityforreddit.thing.VoteThing
import ml.docilealligator.infinityforreddit.utils.APIUtils
import ml.docilealligator.infinityforreddit.utils.SavedPostCacheNotifier
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import ml.docilealligator.infinityforreddit.utils.Utils
import org.greenrobot.eventbus.EventBus
import retrofit2.Retrofit
import java.util.concurrent.Executor

/**
 * The bottom info panel every Shadowbox page shows: title, subreddit and time, score, comment
 * count, and the upvote / downvote / save / fullscreen / more actions. One binder for every page
 * type, so the panel is the same panel on every page.
 *
 * Voting and saving mirror the feed's PostRecyclerViewAdapter: optimistic update, restore on
 * failure, the local read-post tables instead of the API for the anonymous account, and a
 * [PostUpdateEventToPostList] after every outcome so the feed row underneath repaints.
 */
class ShadowboxInfoPanel(
    private val binding: ShadowboxInfoPanelBinding,
    private val host: BaseActivity,
    private val glide: RequestManager,
    private val retrofit: Retrofit,
    private val oauthRetrofit: Retrofit,
    private val redditDataRoomDatabase: RedditDataRoomDatabase,
    private val executor: Executor,
    sharedPreferences: SharedPreferences,
    private val postHistorySharedPreferences: SharedPreferences,
    customThemeWrapper: CustomThemeWrapper,
    private val fragmentManager: FragmentManager,
    private val isNsfwSubreddit: Boolean,
    private val onMarkPostRead: (Post, Int) -> Unit,
    private val onOpenFullViewer: () -> Unit
) {
    private val upvotedColor = customThemeWrapper.upvoted
    private val downvotedColor = customThemeWrapper.downvoted
    private val subredditColor = customThemeWrapper.subreddit
    private val usernameColor = customThemeWrapper.username
    private val postTypeTextColor = customThemeWrapper.postTypeTextColor
    private val flairBackgroundColor = customThemeWrapper.flairBackgroundColor
    private val flairTextColor = customThemeWrapper.flairTextColor
    private val typeBackgroundColors = mapOf(
        Post.VIDEO_TYPE to customThemeWrapper.videoTypeBackgroundColor,
        Post.GIF_TYPE to customThemeWrapper.gifTypeBackgroundColor,
        Post.IMAGE_TYPE to customThemeWrapper.imageTypeBackgroundColor,
        Post.LINK_TYPE to customThemeWrapper.linkTypeBackgroundColor,
        Post.NO_PREVIEW_LINK_TYPE to customThemeWrapper.linkTypeBackgroundColor,
        Post.GALLERY_TYPE to customThemeWrapper.galleryTypeBackgroundColor,
        Post.TEXT_TYPE to customThemeWrapper.textTypeBackgroundColor
    )
    private val showElapsedTime = sharedPreferences.getBoolean(SharedPreferencesUtils.SHOW_ELAPSED_TIME_KEY, false)
    private val timeFormatPattern = sharedPreferences.getString(
        SharedPreferencesUtils.TIME_FORMAT_KEY, SharedPreferencesUtils.TIME_FORMAT_DEFAULT_VALUE
    ) ?: SharedPreferencesUtils.TIME_FORMAT_DEFAULT_VALUE
    private val showAbsoluteNumberOfVotes = sharedPreferences.getBoolean(SharedPreferencesUtils.SHOW_ABSOLUTE_NUMBER_OF_VOTES, true)
    private val hideTheNumberOfVotes = sharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_THE_NUMBER_OF_VOTES, false)
    private val hideTheNumberOfComments = sharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_THE_NUMBER_OF_COMMENTS, false)
    private val hideSubredditAndUserPrefix = sharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_SUBREDDIT_AND_USER_PREFIX, false)
    private val hidePostType = sharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_POST_TYPE, false)
    private val hidePostFlair = sharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_POST_FLAIR, false)
    private val markPostsAsReadAfterVoting = postHistorySharedPreferences.getBoolean(
        AccountScope.key(host.accountName, SharedPreferencesUtils.MARK_POSTS_AS_READ_AFTER_VOTING_BASE), false
    )
    private val locale = host.resources.configuration.locales[0]

    private var post: Post? = null
    private var position = -1

    init {
        host.titleTypeface?.let { binding.titleTextViewShadowboxInfoPanel.typeface = it }
        host.typeface?.let {
            binding.subredditNameTextViewShadowboxInfoPanel.typeface = it
            binding.userTextViewShadowboxInfoPanel.typeface = it
            binding.postTimeTextViewShadowboxInfoPanel.typeface = it
            binding.linkTextViewShadowboxInfoPanel.typeface = it
            binding.typeTextViewShadowboxInfoPanel.typeface = it
            binding.flairCustomTextViewShadowboxInfoPanel.typeface = it
            binding.scoreTextViewShadowboxInfoPanel.typeface = it
            binding.commentsCountButtonShadowboxInfoPanel.typeface = it
        }
        binding.titleTextViewShadowboxInfoPanel.setOnClickListener { openComments() }
        binding.headerShadowboxInfoPanel.setOnClickListener { openComments() }
        binding.commentsCountButtonShadowboxInfoPanel.setOnClickListener { openComments() }
        binding.upvoteButtonShadowboxInfoPanel.setOnClickListener { vote(true) }
        binding.scoreTextViewShadowboxInfoPanel.setOnClickListener { vote(true) }
        binding.downvoteButtonShadowboxInfoPanel.setOnClickListener { vote(false) }
        binding.saveButtonShadowboxInfoPanel.setOnClickListener { toggleSave() }
        binding.fullscreenButtonShadowboxInfoPanel.setOnClickListener { onOpenFullViewer() }
        binding.moreButtonShadowboxInfoPanel.setOnClickListener { showMoreOptions() }
    }

    val root: View
        get() = binding.root

    fun bind(post: Post, position: Int) {
        this.post = post
        this.position = position
        rebind()
    }

    /** Re-reads every field from the bound post; call after anything outside the panel changed it. */
    fun rebind() {
        val post = this.post ?: return
        binding.titleTextViewShadowboxInfoPanel.text = post.title
        binding.titleTextViewShadowboxInfoPanel.alpha = if (post.isRead) 0.6f else 1f

        binding.subredditNameTextViewShadowboxInfoPanel.text =
            if (hideSubredditAndUserPrefix) post.subredditName else post.subredditNamePrefixed
        binding.subredditNameTextViewShadowboxInfoPanel.setTextColor(subredditColor)
        binding.userTextViewShadowboxInfoPanel.text =
            if (hideSubredditAndUserPrefix) post.author else post.authorNamePrefixed
        binding.userTextViewShadowboxInfoPanel.setTextColor(usernameColor)
        binding.postTimeTextViewShadowboxInfoPanel.text = if (showElapsedTime) {
            Utils.getElapsedTime(host, post.postTimeMillis)
        } else {
            Utils.getFormattedTime(locale, post.postTimeMillis, timeFormatPattern)
        }
        loadSubredditIcon(post)
        renderTags(post)
        renderLink(post)

        renderVote(post)

        if (hideTheNumberOfComments) {
            binding.commentsCountButtonShadowboxInfoPanel.text = ""
        } else {
            binding.commentsCountButtonShadowboxInfoPanel.text = post.nComments.toString()
        }

        renderSave(post)
    }

    /**
     * The subreddit avatar, from the post if the feed already resolved one, otherwise through the
     * same cache-then-network loader the feed uses; the icon is written back onto the post so
     * swiping away and back does not ask again.
     */
    private fun loadSubredditIcon(post: Post) {
        val iconUrl = post.subredditIconUrl
        if (iconUrl == null) {
            loadDefaultSubredditIcon()
            val subredditName = post.subredditName
            LoadSubredditIcon.loadSubredditIcon(
                executor, Handler(Looper.getMainLooper()), redditDataRoomDatabase, subredditName,
                host.accessToken, host.accountName, oauthRetrofit, retrofit
            ) { loadedUrl ->
                post.subredditIconUrl = loadedUrl ?: ""
                if (this.post === post) {
                    showSubredditIcon(loadedUrl)
                }
            }
        } else {
            showSubredditIcon(iconUrl)
        }
    }

    private fun showSubredditIcon(iconUrl: String?) {
        if (iconUrl.isNullOrEmpty()) {
            loadDefaultSubredditIcon()
            return
        }
        iconRequest(glide, iconUrl)
            .error(
                glide.load(R.drawable.subreddit_default_icon)
                    .apply(RequestOptions.bitmapTransform(RoundedCornersTransformation(72, 0)))
            )
            .into(binding.iconImageViewShadowboxInfoPanel)
    }

    private fun loadDefaultSubredditIcon() {
        glide.load(R.drawable.subreddit_default_icon)
            .apply(RequestOptions.bitmapTransform(RoundedCornersTransformation(72, 0)))
            .into(binding.iconImageViewShadowboxInfoPanel)
    }

    /** The post-type tag and the post's flair, coloured as the feed colours them. */
    private fun renderTags(post: Post) {
        val typeView = binding.typeTextViewShadowboxInfoPanel
        if (hidePostType) {
            typeView.visibility = View.GONE
        } else {
            typeView.visibility = View.VISIBLE
            typeView.setText(
                when (post.postType) {
                    Post.IMAGE_TYPE -> R.string.image
                    Post.GIF_TYPE -> R.string.gif
                    Post.VIDEO_TYPE -> R.string.video
                    Post.GALLERY_TYPE -> R.string.gallery
                    Post.LINK_TYPE, Post.NO_PREVIEW_LINK_TYPE -> R.string.link
                    else -> R.string.text
                }
            )
            val typeColor = typeBackgroundColors[post.postType] ?: typeBackgroundColors.getValue(Post.TEXT_TYPE)
            typeView.setBackgroundColor(typeColor)
            typeView.setBorderColor(typeColor)
            typeView.setTextColor(postTypeTextColor)
        }

        val flairView = binding.flairCustomTextViewShadowboxInfoPanel
        val flair = post.flair
        if (hidePostFlair || flair.isNullOrEmpty()) {
            flairView.visibility = View.GONE
        } else {
            flairView.visibility = View.VISIBLE
            flairView.setBackgroundColor(flairBackgroundColor)
            flairView.setBorderColor(flairBackgroundColor)
            flairView.setTextColor(flairTextColor)
            Utils.setHTMLWithImageToTextView(flairView, flair, false)
        }
    }

    /**
     * The mute control for a video page, in the tags row where it is reachable with the bar up
     * rather than floating over the media. Hidden, not removed, on every other page.
     */
    fun showMuteControl(muted: Boolean, onToggle: () -> Unit) {
        binding.muteButtonShadowboxInfoPanel.visibility = View.VISIBLE
        binding.muteButtonShadowboxInfoPanel.setOnClickListener { onToggle() }
        setMuted(muted)
    }

    fun hideMuteControl() {
        binding.muteButtonShadowboxInfoPanel.visibility = View.INVISIBLE
        binding.muteButtonShadowboxInfoPanel.setOnClickListener(null)
    }

    fun setMuted(muted: Boolean) {
        binding.muteButtonShadowboxInfoPanel.setIconResource(
            if (muted) R.drawable.ic_mute_24dp else R.drawable.ic_unmute_24dp
        )
    }

    /** The link's domain, where the feed card shows it: under the tags, not over the media. */
    private fun renderLink(post: Post) {
        val url = post.url
        val isLink = post.postType == Post.LINK_TYPE || post.postType == Post.NO_PREVIEW_LINK_TYPE
        if (!isLink || url == null) {
            binding.linkTextViewShadowboxInfoPanel.visibility = View.GONE
            return
        }
        binding.linkTextViewShadowboxInfoPanel.visibility = View.VISIBLE
        binding.linkTextViewShadowboxInfoPanel.text = Uri.parse(url).host ?: url
    }

    private fun renderVote(post: Post) {
        val neutral = Color.WHITE
        when (post.voteType) {
            1 -> {
                binding.upvoteButtonShadowboxInfoPanel.setIconResource(R.drawable.ic_upvote_filled_24dp)
                binding.upvoteButtonShadowboxInfoPanel.iconTint = ColorStateList.valueOf(upvotedColor)
                binding.downvoteButtonShadowboxInfoPanel.setIconResource(R.drawable.ic_downvote_24dp)
                binding.downvoteButtonShadowboxInfoPanel.iconTint = ColorStateList.valueOf(neutral)
                binding.scoreTextViewShadowboxInfoPanel.setTextColor(upvotedColor)
            }
            -1 -> {
                binding.upvoteButtonShadowboxInfoPanel.setIconResource(R.drawable.ic_upvote_24dp)
                binding.upvoteButtonShadowboxInfoPanel.iconTint = ColorStateList.valueOf(neutral)
                binding.downvoteButtonShadowboxInfoPanel.setIconResource(R.drawable.ic_downvote_filled_24dp)
                binding.downvoteButtonShadowboxInfoPanel.iconTint = ColorStateList.valueOf(downvotedColor)
                binding.scoreTextViewShadowboxInfoPanel.setTextColor(downvotedColor)
            }
            else -> {
                binding.upvoteButtonShadowboxInfoPanel.setIconResource(R.drawable.ic_upvote_24dp)
                binding.upvoteButtonShadowboxInfoPanel.iconTint = ColorStateList.valueOf(neutral)
                binding.downvoteButtonShadowboxInfoPanel.setIconResource(R.drawable.ic_downvote_24dp)
                binding.downvoteButtonShadowboxInfoPanel.iconTint = ColorStateList.valueOf(neutral)
                binding.scoreTextViewShadowboxInfoPanel.setTextColor(neutral)
            }
        }
        if (hideTheNumberOfVotes) {
            // INVISIBLE, not GONE: the downvote button must not slide over by the score's width.
            binding.scoreTextViewShadowboxInfoPanel.visibility = View.INVISIBLE
        } else {
            binding.scoreTextViewShadowboxInfoPanel.visibility = View.VISIBLE
            binding.scoreTextViewShadowboxInfoPanel.text =
                Utils.getNVotes(showAbsoluteNumberOfVotes, post.score + post.voteType)
        }
    }

    private fun renderSave(post: Post) {
        binding.saveButtonShadowboxInfoPanel.setIconResource(
            if (post.isSaved) R.drawable.ic_bookmark_grey_24dp else R.drawable.ic_bookmark_border_grey_24dp
        )
    }

    private fun isAnonymous(): Boolean = host.accountName == Account.ANONYMOUS_ACCOUNT

    /** Whether callbacks landing later still belong to the post this panel shows. */
    private fun stillShowing(post: Post): Boolean = this.post === post && binding.root.isAttachedToWindow

    private fun postUpdated(post: Post) {
        EventBus.getDefault().post(PostUpdateEventToPostDetailFragment(post))
        EventBus.getDefault().post(PostUpdateEventToPostList(post, position))
    }

    private fun vote(up: Boolean) {
        val post = this.post ?: return
        if (!isAnonymous() && post.isArchived) {
            Toast.makeText(host, R.string.archived_post_vote_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        if (markPostsAsReadAfterVoting) {
            onMarkPostRead(post, position)
            binding.titleTextViewShadowboxInfoPanel.alpha = 0.6f
        }

        val target = if (up) 1 else -1
        val previousVoteType = post.voteType
        val newVoteType: String
        if (previousVoteType != target) {
            post.voteType = target
            newVoteType = if (up) APIUtils.DIR_UPVOTE else APIUtils.DIR_DOWNVOTE
        } else {
            post.voteType = 0
            newVoteType = APIUtils.DIR_UNVOTE
        }
        renderVote(post)

        if (isAnonymous()) {
            val readPostType = if (up) ReadPostType.ANONYMOUS_UPVOTED_POSTS else ReadPostType.ANONYMOUS_DOWNVOTED_POSTS
            if (previousVoteType == target) {
                ReadPostModification.deleteReadPost(
                    redditDataRoomDatabase, executor, host.accountName, post.id, readPostType
                )
            } else {
                ReadPostModification.insertReadPost(
                    redditDataRoomDatabase, executor, host.accountName, post.id, readPostType,
                    ReadPostsUtils.GetReadPostsLimit(host.accountName, postHistorySharedPreferences)
                )
            }
            postUpdated(post)
            return
        }

        VoteThing.voteThing(host, oauthRetrofit, host.accessToken, object : VoteThing.VoteThingListener {
            override fun onVoteThingSuccess(position: Int) {
                post.voteType = if (newVoteType == APIUtils.DIR_UNVOTE) 0 else target
                if (stillShowing(post)) {
                    renderVote(post)
                }
                postUpdated(post)
            }

            override fun onVoteThingFail(position: Int) {
                Toast.makeText(host, R.string.vote_failed, Toast.LENGTH_SHORT).show()
                post.voteType = previousVoteType
                if (stillShowing(post)) {
                    renderVote(post)
                }
                postUpdated(post)
            }
        }, post.fullName, newVoteType, position)
    }

    private fun toggleSave() {
        val post = this.post ?: return
        if (post.isSaved) {
            if (isAnonymous()) {
                ReadPostModification.deleteReadPost(
                    redditDataRoomDatabase, executor, host.accountName, post.id, ReadPostType.ANONYMOUS_SAVED_POSTS
                )
                post.isSaved = false
                renderSave(post)
                Toast.makeText(host, R.string.post_unsaved_success, Toast.LENGTH_SHORT).show()
                postUpdated(post)
            } else {
                post.isSaved = false
                renderSave(post)
                SaveThing.unsaveThing(oauthRetrofit, host.accessToken, post.fullName, object : SaveThing.SaveThingListener {
                    override fun success() {
                        post.isSaved = false
                        LocalSaved.onUnsaved(redditDataRoomDatabase, executor, host.accountName, post.fullName)
                        SavedPostCacheNotifier.onSavedPostChanged()
                        if (stillShowing(post)) {
                            renderSave(post)
                        }
                        Toast.makeText(host, R.string.post_unsaved_success, Toast.LENGTH_SHORT).show()
                        postUpdated(post)
                    }

                    override fun failed() {
                        post.isSaved = true
                        if (stillShowing(post)) {
                            renderSave(post)
                        }
                        Toast.makeText(host, R.string.post_unsaved_failed, Toast.LENGTH_SHORT).show()
                        postUpdated(post)
                    }
                })
            }
        } else {
            if (isAnonymous()) {
                ReadPostModification.insertReadPost(
                    redditDataRoomDatabase, executor, host.accountName, post.id, ReadPostType.ANONYMOUS_SAVED_POSTS,
                    ReadPostsUtils.GetReadPostsLimit(host.accountName, postHistorySharedPreferences)
                )
                post.isSaved = true
                renderSave(post)
                Toast.makeText(host, R.string.post_saved_success, Toast.LENGTH_SHORT).show()
                postUpdated(post)
            } else {
                post.isSaved = true
                renderSave(post)
                SaveThing.saveThing(oauthRetrofit, host.accessToken, post.fullName, object : SaveThing.SaveThingListener {
                    override fun success() {
                        post.isSaved = true
                        LocalSaved.onSaved(
                            redditDataRoomDatabase, executor, oauthRetrofit, host.accessToken,
                            host.accountName, post.fullName
                        )
                        SavedPostCacheNotifier.onSavedPostChanged()
                        if (stillShowing(post)) {
                            renderSave(post)
                        }
                        Toast.makeText(host, R.string.post_saved_success, Toast.LENGTH_SHORT).show()
                        postUpdated(post)
                    }

                    override fun failed() {
                        post.isSaved = false
                        if (stillShowing(post)) {
                            renderSave(post)
                        }
                        Toast.makeText(host, R.string.post_saved_failed, Toast.LENGTH_SHORT).show()
                        postUpdated(post)
                    }
                })
            }
        }
    }

    /**
     * Opens the post's comments as a standalone screen, the way "Open in new window" does: the list
     * position lets its vote / save / moderation updates reach the feed row, but no fragment id, so
     * the detail pager does not try to size itself by a feed list this page may have swiped past.
     */
    fun openComments() {
        val post = this.post ?: return
        val intent = Intent(host, ViewPostDetailActivity::class.java)
        intent.putExtra(ViewPostDetailActivity.EXTRA_POST_DATA, post)
        intent.putExtra(ViewPostDetailActivity.EXTRA_POST_LIST_POSITION, position)
        intent.putExtra(ViewPostDetailActivity.EXTRA_IS_NSFW_SUBREDDIT, isNsfwSubreddit)
        host.startActivity(intent)
    }

    private fun showMoreOptions() {
        val post = this.post ?: return
        val sheet = PostOptionsBottomSheetFragment.newInstance(post, position, true)
        sheet.show(fragmentManager, sheet.tag)
    }

    companion object {
        /** The one request the panel makes for a subreddit icon. */
        fun iconRequest(glide: RequestManager, url: String): RequestBuilder<Drawable> =
            glide.load(url)
                .apply(RequestOptions.bitmapTransform(RoundedCornersTransformation(72, 0)))
                .transition(DrawableTransitionOptions.withCrossFade(ShadowboxPreviews.CROSS_FADE_MS))
    }
}
