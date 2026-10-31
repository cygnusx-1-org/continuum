package ml.docilealligator.infinityforreddit.viewmodels

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.Nullable
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.paging.PagingSource
import com.google.gson.Gson
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ml.docilealligator.infinityforreddit.AppResult
import ml.docilealligator.infinityforreddit.PostDetailCommentsCache
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.RedditError
import ml.docilealligator.infinityforreddit.SingleLiveEvent
import ml.docilealligator.infinityforreddit.account.Account
import ml.docilealligator.infinityforreddit.apis.RedditAPIKt
import ml.docilealligator.infinityforreddit.comment.Comment
import ml.docilealligator.infinityforreddit.comment.FetchRemovedComment
import ml.docilealligator.infinityforreddit.comment.MoreCommentsLoader
import ml.docilealligator.infinityforreddit.comment.ParseComment
import ml.docilealligator.infinityforreddit.comment.RecoverRemovedComments
import ml.docilealligator.infinityforreddit.commentfilter.CommentFilter
import ml.docilealligator.infinityforreddit.commentfilter.CommentFilterUsage
import ml.docilealligator.infinityforreddit.moderation.CommentModerationEvent
import ml.docilealligator.infinityforreddit.moderation.PostModerationEvent
import ml.docilealligator.infinityforreddit.post.FetchRemovedPost
import ml.docilealligator.infinityforreddit.post.ParsePost
import ml.docilealligator.infinityforreddit.post.Post
import ml.docilealligator.infinityforreddit.post.PostPagingSource.PostPagingSourceError
import ml.docilealligator.infinityforreddit.post.RecoverRemovedPost
import ml.docilealligator.infinityforreddit.post.hidePost
import ml.docilealligator.infinityforreddit.post.unhidePost
import ml.docilealligator.infinityforreddit.readpost.ReadPostType
import ml.docilealligator.infinityforreddit.readpost.ReadPostsUtils
import ml.docilealligator.infinityforreddit.readpost.deleteReadPost
import ml.docilealligator.infinityforreddit.readpost.insertReadPost
import ml.docilealligator.infinityforreddit.subreddit.Flair
import ml.docilealligator.infinityforreddit.subreddit.ParseSubredditData
import ml.docilealligator.infinityforreddit.subreddit.SubredditData
import ml.docilealligator.infinityforreddit.thing.SortType
import ml.docilealligator.infinityforreddit.thing.deleteThing
import ml.docilealligator.infinityforreddit.localsaved.LocalSaved
import ml.docilealligator.infinityforreddit.utils.SavedPostCacheNotifier
import ml.docilealligator.infinityforreddit.thing.saveThing
import ml.docilealligator.infinityforreddit.thing.unsaveThing
import ml.docilealligator.infinityforreddit.utils.APIUtils
import ml.docilealligator.infinityforreddit.utils.JSONUtils
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import okio.IOException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

@Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class ViewPostDetailFragmentViewModelNew(
    private val retrofit: Retrofit,
    private val oauthRetrofit: Retrofit,
    private val arcticShiftRetrofit: Retrofit,
    private val redditDataRoomDatabase: RedditDataRoomDatabase,
    private val accessToken: String?,
    private val accountName: String,
    private var post: Post?,
    private var postId: String?,
    private var singleCommentId: String?,
    comments: ArrayList<Comment>?,
    children: ArrayList<String>?,
    sortType: SortType.Type?,
    private val sortTypeSharedPreferences: SharedPreferences,
    private val postHistorySharedPreferences: SharedPreferences,
    private var respectSubredditRecommendedSortType: Boolean,
    private val saveCommentSort: Boolean,
    private val commentDefaultSortType: SortType.Type,
    private val markPostsAsRead: Boolean,
    private val expandChildren: Boolean,
    private val contextNumber: String,
    private val useLegacyMoreChildren: Boolean = false
) : ViewModel() {
    data class UiState(
        val sortType: SortType.Type?,
        val isInitialLoading: Boolean,
        val isInitialLoadingFailed: Boolean,
        val fetchPostFailed: Boolean,
        val isRefreshing: Boolean,
        val isLoadingMoreChildren: Boolean,
        val loadMoreChildrenSuccess: Boolean,
        //val shouldShowErrorView: Boolean,
        val errorViewError: ViewPostDetailFragmentViewModelError?,
        val singleCommentId: String?
    )

    data class DataState(
        val post: Post?,
        val comments: ArrayList<Comment>?,
        val children: ArrayList<String>?,
    ) {
        val hasMoreChildren: Boolean
            get() = children?.isEmpty() == false
    }

    /** [childrenCount] is Reddit's count of the replies behind [children], when it gave one. */
    data class ParseCommentsResult(
        val comments: ArrayList<Comment>,
        val topLevelComments: ArrayList<Comment>,
        val children: ArrayList<String>,
        val childrenCount: Int? = null
    )

    /**
     * Outcome of an explicit comment-recovery pass. [completed] is false when the archive cut the
     * pass short — a rate limit, its own throttle, a network failure — which is a different thing
     * to tell the user than "the archive has nothing for these comments".
     */
    data class CommentRecoveryResult(
        val recovered: Int,
        val attempted: Int,
        val completed: Boolean
    )

    sealed class ViewPostDetailFragmentViewModelError {
        data class Network(val e: Exception?) : ViewPostDetailFragmentViewModelError()
        data class Response(val code: Int, val message: String?) : ViewPostDetailFragmentViewModelError()
        object Quarantined : ViewPostDetailFragmentViewModelError()
        data class Json(val e: JSONException?) : ViewPostDetailFragmentViewModelError()
        data class RedditErrorWrapper(val e: RedditError) : ViewPostDetailFragmentViewModelError()
        object InvalidPostId : ViewPostDetailFragmentViewModelError()

        fun getErrorReason(context: Context): String {
            return when (this) {
                InvalidPostId -> context.getString(R.string.invalid_post_id)
                is Json -> context.getString(R.string.parse_json_response_error)
                is Network -> e?.localizedMessage ?: context.getString(R.string.network_error)
                Quarantined -> context.getString(R.string.quarantined_subreddit)
                is RedditErrorWrapper -> e.reason ?: e.message ?: context.getString(R.string.unknown_error)
                is ViewPostDetailFragmentViewModelError.Response -> message?.let {
                    context.getString(R.string.response_error_with_code_and_message, code, it)
                } ?: context.getString(R.string.response_error_with_code, code)
            }
        }
    }

    private val _uiState: MutableStateFlow<UiState> = MutableStateFlow(
        UiState(
            sortType,
            isInitialLoading = false,
            isInitialLoadingFailed = false,
            fetchPostFailed = false,
            isRefreshing = false,
            isLoadingMoreChildren = false,
            loadMoreChildrenSuccess = true,
            //shouldShowErrorView = false,
            errorViewError = null,
            singleCommentId = singleCommentId
        )
    )
    val uiState: LiveData<UiState> = _uiState.asLiveData()

    private val _dataState: MutableStateFlow<DataState> = MutableStateFlow(DataState(
        post, comments, children
    ))
    val dataState: LiveData<DataState> = _dataState.asLiveData()

    var commentFilter: CommentFilter? = null
    private val moreCommentsSession = MoreCommentsLoader.Session()

    val postModerationEventLiveData: SingleLiveEvent<PostModerationEvent> = SingleLiveEvent()
    val commentModerationEventLiveData: SingleLiveEvent<CommentModerationEvent> = SingleLiveEvent()

    private var fetchCommentJob: Job? = null

    /**
     * Selects which comment `limit` — and so which of Reddit's cached comment trees — a fetch
     * reads. Seeded randomly so that re-opening a post does not return to the slot the previous
     * visit used, and bumped by every explicit refresh. See [commentLimitFor].
     */
    private var commentFetchNonce: Int = Random.nextInt(COMMENT_LIMIT_MAX)

    /** Emitted for the explicit recovery action only; the automatic pass is silent. */
    val commentRecoveryEventLiveData: SingleLiveEvent<CommentRecoveryResult> = SingleLiveEvent()

    /**
     * Comment ids the archive has already answered for, so re-scanning the list after every "load
     * more" does not spend requests re-learning that the same comments are not archived. Ids from a
     * pass that stopped early are deliberately not recorded here, so they are retried rather than
     * written off as absent.
     */
    private val recoveryAttemptedIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Ids an automatic pass is still asking the archive about, so the menu and the explicit action
     * do not ask again. Apart from [recoveryAttemptedIds] because none of these is answered yet: a
     * pass that stops early hands its ids back.
     */
    private val recoveryInFlightIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Archived bodies that arrived after their comments were drawn. Never patched into the list —
     * that would rewrite rows the reader may be looking at — but written onto a comment as its row
     * is bound, once [releaseLateRecoveries] has let it through. Kept after being written, because a
     * copy taken before then (a vote, a collapse) can put an unrecovered instance back on screen.
     */
    private val lateRecoveries = HashMap<String, FetchRemovedComment.Result>()

    /** The [lateRecoveries] whose rows were off screen at some point after the body arrived. */
    private val releasedLateRecoveryIds = HashSet<String>()

    /** Fires when [lateRecoveries] gains bodies, or a pass ends, after its page was drawn. */
    val lateRecoveryLiveData: SingleLiveEvent<Unit> = SingleLiveEvent()

    /** Parent of the automatic passes that outlive their page's draw, so a refresh can stop them. */
    private val backgroundRecovery = SupervisorJob(viewModelScope.coroutineContext.job)

    /** Bumped whenever the comment list is replaced, so a pass for the old list cannot reach the new. */
    @Volatile
    private var recoveryGeneration = 0

    private var commentRecoveryJob: Job? = null

    /**
     * The post [fetchPostAndCommentsById] parsed and is holding back while the archive is asked about
     * it. The comment fetch running alongside reads the subreddit and comment count from here —
     * partly on a parsing thread, hence volatile.
     */
    @Volatile
    private var unpublishedPost: Post? = null

    /**
     * Publishes the post once its archive wait is over. Comments loading alongside join this before
     * they are published, so a thread never draws ahead of its post and is never pushed down by it.
     */
    private var postPublication: Job? = null

    /**
     * The archive's answer, or the wait for it, for the post this view opened with — asked by
     * [recoverPostBeforeFirstRender] or [publishPostAfterRecovery], once per ViewModel.
     */
    private var postRecovery: Deferred<Post>? = null

    /** The archive's answer for this post, once it has given one, for a refresh to reuse. */
    private var postArchive: FetchRemovedPost.Result? = null

    /** An answer for the post that arrived after it was drawn, held until its row is off screen. */
    private var latePostRecovery: FetchRemovedPost.Result? = null

    /** The post comment fetches are for: the published one, else the one about to be. */
    private val commentsPost: Post?
        get() = _dataState.value.post ?: unpublishedPost

    val derivedPostId: String?
        get() = _dataState.value.post?.id ?: postId

    fun setPost(post: Post) {
        _dataState.value = _dataState.value.copy(
            post = post
        )
    }

    fun updatePostFromEvent(post: Post): Boolean {
        if (_dataState.value.post?.id == post.id) {
            _dataState.value = _dataState.value.copy(
                post = post
            )
            return true
        }

        return false
    }

    fun getPost(): Post? {
        return _dataState.value.post
    }

    fun setPostId(postId: String?) {
        this.postId = postId
    }

    fun getSingleCommentId(): String? {
        return _uiState.value.singleCommentId
    }

    fun fetchCommentsRespectRecommendedSort(
        changeRefreshState: Boolean
    ) {
        viewModelScope.launch {
            fetchCommentsRespectRecommendedSortSync(
                _uiState.value.sortType ?: updateSortType(loadSortType()), changeRefreshState,
            )
        }
    }

    fun fetchCommentsRespectRecommendedSort(
        sortType: SortType.Type,
        changeRefreshState: Boolean
    ) {
        viewModelScope.launch {
            fetchCommentsRespectRecommendedSortSync(
                sortType, changeRefreshState,
            )
        }
    }

    /**
     * Called when the user explicitly picks a comment sort type. The chosen sort
     * must always be honored, so stop respecting the subreddit's recommended sort
     * for this view; otherwise the recommended sort would immediately override the
     * user's choice (and again on every refresh). When "Save Comment Sort" is on,
     * the pick is remembered for this subreddit and takes precedence on future visits.
     */
    fun fetchCommentsWithSortType(
        sortType: SortType.Type,
        changeRefreshState: Boolean
    ) {
        respectSubredditRecommendedSortType = false
        updateSortType(sortType)
        if (saveCommentSort) {
            _dataState.value.post?.subredditName?.let { subredditName ->
                sortTypeSharedPreferences.edit {
                    putString(SharedPreferencesUtils.SORT_TYPE_SUBREDDIT_COMMENT_BASE + subredditName, sortType.name)
                }
            }
        }
        viewModelScope.launch {
            fetchComments(sortType, changeRefreshState)
        }
    }

    suspend fun fetchCommentsRespectRecommendedSortSync(
        changeRefreshState: Boolean
    ) {
        fetchCommentsRespectRecommendedSortSync(
            _uiState.value.sortType ?: updateSortType(loadSortType()), changeRefreshState,
        )
    }

    /***
     * Resolves the comment sort with this precedence:
     *   1. the per-subreddit saved comment sort (if "Save Comment Sort" is on and one exists),
     *   2. the recommended sort (post-level, then subreddit-level) when "Respect Recommended"
     *      is on — used live, never auto-saved,
     *   3. the "Comment Default Sort Type" setting.
     *
     * @param sortType fallback used only when there is no post to resolve a subreddit from.
     */
    suspend fun fetchCommentsRespectRecommendedSortSync(
        sortType: SortType.Type,
        changeRefreshState: Boolean,
    ) {
        val post = commentsPost
        val subredditName = post?.subredditName

        // Tier 1: a sort the user explicitly saved for this subreddit wins over everything.
        val savedSubredditSort = subredditName?.let { loadSubredditCommentSort(it) }
        if (savedSubredditSort != null) {
            updateSortType(savedSubredditSort)
            fetchComments(savedSubredditSort, changeRefreshState)
            return
        }

        // Tier 2: the recommended sort, used live and never auto-saved.
        if (respectSubredditRecommendedSortType && post != null) {
            val suggestedSort = post.suggestedSort
            if (suggestedSort != null && !suggestedSort.equals("null") && suggestedSort.isNotEmpty()) {
                try {
                    val recommendedSort = SortType.Type.valueOf(suggestedSort.uppercase())
                    updateSortType(recommendedSort)
                    fetchComments(recommendedSort, changeRefreshState)
                    return
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace()
                }
            }

            when (val subredditResult = fetchSubredditData(post.subredditName)) {
                is AppResult.Success<*> -> {
                    when (subredditResult.data) {
                        is SubredditData -> {
                            val suggestedCommentSort: String? = subredditResult.data.suggestedCommentSort
                            if (suggestedCommentSort == null || suggestedCommentSort == "null" || suggestedCommentSort.isEmpty()) {
                                // No recommendation for this subreddit; stop rechecking on refresh.
                                respectSubredditRecommendedSortType = false
                                updateSortType(commentDefaultSortType)
                            } else {
                                try {
                                    updateSortType(SortType.Type.valueOf(suggestedCommentSort.uppercase()))
                                } catch (e: IllegalArgumentException) {
                                    e.printStackTrace()
                                    updateSortType(commentDefaultSortType)
                                }
                            }

                            fetchComments(changeRefreshState)
                        }
                        else -> {
                            // Null SubredditData!
                            respectSubredditRecommendedSortType = false
                            updateSortType(commentDefaultSortType)
                            fetchComments(changeRefreshState)
                        }
                    }
                }

                is AppResult.Error<*> -> {
                    respectSubredditRecommendedSortType = false
                    updateSortType(commentDefaultSortType)
                    fetchComments(changeRefreshState)
                }
            }
            return
        }

        // Tier 3: the sort already in effect. Callers pass `_uiState.sortType ?: commentDefaultSortType`,
        // so this is the "Comment Default Sort Type" on first load and the user's current pick on a
        // later refresh (preserving a manual pick that tier 1 didn't persist).
        updateSortType(sortType)
        fetchComments(sortType, changeRefreshState)
    }

    private fun loadSubredditCommentSort(subredditName: String): SortType.Type? {
        if (!saveCommentSort) {
            return null
        }
        val sortTypeName = sortTypeSharedPreferences.getString(
            SharedPreferencesUtils.SORT_TYPE_SUBREDDIT_COMMENT_BASE + subredditName, null
        ) ?: return null
        return try {
            SortType.Type.valueOf(sortTypeName)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Picks the `limit` to send with a comment fetch.
     *
     * Reddit builds the comment tree per (link, sort, limit) and caches the result, and that
     * cache can miss an invalidation: a reply lands, the entry is never rebuilt, and the reply
     * is simply absent from the response — with no "more" node to expand, so the post looks
     * like it never received the comment. `limit` therefore does two jobs. It caps how many
     * comments come back, and it selects which cached tree is read; a two-comment thread is
     * hit by the second job alone, which is why asking for 2 can return more than asking for
     * 200. Omitting it puts every client on the shared num=200 slot, which is both the most
     * contended one and the one this app used to read on every fetch *and* every refresh, so
     * a poisoned slot was unrecoverable from the UI.
     *
     * The limit is drawn from the [COMMENT_LIMIT_STEP] band directly above the post's comment
     * count, capped at [COMMENT_LIMIT_MAX] (Reddit clamps anything larger and returns 499
     * comments either way). Every value in that band returns the whole thread, so which one is
     * used is free — but each is a separate cache slot. Staying in the band keeps a small
     * thread's request sized to the thread instead of always asking for 500.
     *
     * [nonce] picks within the band. It is seeded randomly per post view and bumped by every
     * refresh, so neither re-opening a post nor refreshing it re-reads the slot that just came
     * back stale — the "I pull down to refresh but it still doesn't show" in the report. The
     * one thread size with no room to move is exactly [COMMENT_LIMIT_MAX] - 1 comments, where
     * 500 is the only limit that still returns all of them.
     */
    private fun commentLimitFor(numComments: Int, nonce: Int): Int {
        val bucket = minOf(
            COMMENT_LIMIT_MAX,
            (numComments / COMMENT_LIMIT_STEP + 1) * COMMENT_LIMIT_STEP
        )

        if (bucket < COMMENT_LIMIT_MAX) {
            // 200 is Reddit's own slot, shared with every client that omits `limit`. Drop it
            // from the band rather than remapping onto it, which would make two nonces pick
            // the same slot and cost a refresh its escape.
            val skipsDefault = bucket == COMMENT_LIMIT_DEFAULT
            val limit = bucket + nonce.mod(COMMENT_LIMIT_STEP - if (skipsDefault) 1 else 0)
            return if (skipsDefault) limit + 1 else limit
        }

        // The band is already at the ceiling, so move down from it instead — but never below
        // the comment count, or a fetch would push comments that fit before behind a "more"
        // node. A thread at or over the ceiling is truncated at 499 whatever we ask for, so
        // the whole last step is fair game; one just under it has less room, and at exactly
        // 499 comments none at all.
        val lowest = if (numComments >= COMMENT_LIMIT_MAX) {
            COMMENT_LIMIT_MAX - COMMENT_LIMIT_STEP + 1
        } else {
            maxOf(COMMENT_LIMIT_MAX - COMMENT_LIMIT_STEP + 1, numComments + 1)
        }
        return COMMENT_LIMIT_MAX - nonce.mod(COMMENT_LIMIT_MAX - lowest + 1)
    }

    /**
     * The limit for the post currently loaded. When the post is not loaded yet the comment
     * count is unknown, so this falls back to the top band — near as much of the thread as
     * Reddit will return in one response.
     */
    private fun currentCommentLimit(): Int = commentLimitFor(
        commentsPost?.getNComments() ?: COMMENT_LIMIT_MAX,
        commentFetchNonce
    )

    suspend fun fetchComments(
        changeRefreshState: Boolean
    ) {
        fetchComments(_uiState.value.sortType ?: updateSortType(loadSortType()), changeRefreshState)
    }

    suspend fun fetchComments(
        sortType: SortType.Type,
        changeRefreshState: Boolean
    ) {
        // This fetch replaces the comment list outright — a sort change and a refresh both land
        // here — so every recovered body is about to revert to the placeholder Reddit serves. Let
        // the archive be asked about them again; otherwise the ids are remembered as already
        // handled and the bodies never come back.
        resetCommentRecovery()

        /*fetchCommentJob?.cancel()
        fetchCommentJob = viewModelScope.launch {



            fetchCommentJob = null
        }*/

        _uiState.value = _uiState.value.copy(
            isInitialLoading = true,
            isInitialLoadingFailed = false,
            fetchPostFailed = false,
            //shouldShowErrorView = false
            errorViewError = null
        )

        val derivedPostId = derivedPostId
        if (derivedPostId == null) {
            _uiState.value = _uiState.value.copy(
                isInitialLoading = false,
                isInitialLoadingFailed = true,
                isRefreshing = if (changeRefreshState) false else _uiState.value.isRefreshing
            )
            return
        }

        val retrofit: Retrofit = if (accountName == Account.ANONYMOUS_ACCOUNT) retrofit else oauthRetrofit
        val api: RedditAPIKt = retrofit.create(RedditAPIKt::class.java)
        val limit = currentCommentLimit()
        val response: Response<String>
        try {
            if (accountName == Account.ANONYMOUS_ACCOUNT) {
                response = _uiState.value.singleCommentId?.let { commentId ->
                    api.getPostAndCommentsSingleThreadById(
                        derivedPostId,
                        commentId,
                        sortType,
                        contextNumber
                    )
                } ?: api.getPostAndCommentsById(derivedPostId, sortType, limit)
            } else {
                response = _uiState.value.singleCommentId?.let { commentId ->
                    api.getPostAndCommentsSingleThreadByIdOauth(
                        derivedPostId, commentId, sortType, contextNumber,
                        APIUtils.getOAuthHeader(accessToken)
                    )
                } ?: api.getPostAndCommentsByIdOauth(
                    derivedPostId,
                    sortType,
                    limit,
                    APIUtils.getOAuthHeader(accessToken)
                )
            }

            if (response.isSuccessful) {
                commentFilter = fetchCommentFilter(commentsPost?.subredditName ?: "")
                val parseCommentsResult = parseComments(response.body(), commentFilter!!, expandChildren)
                // Never ahead of a post still waiting on the archive: see postPublication.
                postPublication?.join()
                when (parseCommentsResult) {
                    is AppResult.Success<ParseCommentsResult> -> {
                        _uiState.value = _uiState.value.copy(
                            isInitialLoading = false,
                            isInitialLoadingFailed = false,
                            isRefreshing = if (changeRefreshState) false else _uiState.value.isRefreshing
                        )
                        _dataState.value = _dataState.value.copy(
                            comments = parseCommentsResult.data.comments,
                            children =  parseCommentsResult.data.children
                        )
                    }
                    is AppResult.Error<*> -> {
                        _uiState.value = _uiState.value.copy(
                            isInitialLoading = false,
                            isInitialLoadingFailed = true,
                            isRefreshing = if (changeRefreshState) false else _uiState.value.isRefreshing
                        )
                    }
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isInitialLoading = false,
                    isInitialLoadingFailed = true,
                    isRefreshing = if (changeRefreshState) false else _uiState.value.isRefreshing
                )
            }
        } catch (e: IOException) {
            e.printStackTrace()
            _uiState.value = _uiState.value.copy(
                isInitialLoading = false,
                isInitialLoadingFailed = true,
                isRefreshing = if (changeRefreshState) false else _uiState.value.isRefreshing
            )
        }
    }

    fun fetchPostAndCommentsById(
        postId: String?
    ) {
        viewModelScope.launch {
            postId?.let { postId ->
                try {
                    _uiState.value = _uiState.value.copy(
                        isInitialLoading = true,
                        isInitialLoadingFailed = false,
                        fetchPostFailed = false,
                        //shouldShowErrorView = false
                        errorViewError = null
                    )

                    val limit = currentCommentLimit()
                    val response: Response<String>
                    if (accountName == Account.ANONYMOUS_ACCOUNT) {
                        response = _uiState.value.singleCommentId?.let { singleCommentId ->
                            if (!_uiState.value.singleCommentId.isNullOrEmpty()) {
                                retrofit.create(RedditAPIKt::class.java)
                                    .getPostAndCommentsSingleThreadById(
                                        postId, singleCommentId, getSortType(), contextNumber
                                    )
                            } else {
                                retrofit.create(RedditAPIKt::class.java).getPostAndCommentsById(
                                    postId,
                                    getSortType(),
                                    limit
                                )
                            }
                        } ?: run {
                            retrofit.create(RedditAPIKt::class.java).getPostAndCommentsById(
                                postId,
                                getSortType(),
                                limit
                            )
                        }
                    } else {
                        response = _uiState.value.singleCommentId?.let { singleCommentId ->
                            if (!_uiState.value.singleCommentId.isNullOrEmpty()) {
                                oauthRetrofit.create(RedditAPIKt::class.java)
                                    .getPostAndCommentsSingleThreadByIdOauth(
                                        postId,
                                        singleCommentId,
                                        getSortType(),
                                        contextNumber,
                                        APIUtils.getOAuthHeader(accessToken)
                                    )
                            } else {
                                oauthRetrofit.create(RedditAPIKt::class.java)
                                    .getPostAndCommentsByIdOauth(
                                        postId, getSortType(), limit, APIUtils.getOAuthHeader(accessToken)
                                    )
                            }
                        } ?: run {
                            oauthRetrofit.create(RedditAPIKt::class.java)
                                .getPostAndCommentsByIdOauth(
                                    postId, getSortType(), limit, APIUtils.getOAuthHeader(accessToken)
                                )
                        }
                    }

                    if (response.isSuccessful) {
                        val post = parsePost(response.body())
                        post?.let { post ->
                            // Asked about alongside the comments rather than ahead of them: both
                            // wait on the same archive, and queued one behind the other a removed
                            // post with removed comments waited twice. An ordinary post needs no
                            // archive and is published at once, as before.
                            unpublishedPost = post
                            val publication = publishPostAfterRecovery(post)
                            postPublication = publication

                            commentFilter = fetchCommentFilter(post.subredditName)

                            // The combined call above already fetched the comments in the default
                            // sort (getSortType()). If neither a per-subreddit saved sort nor the
                            // recommended sort would change that, reuse them instead of re-fetching;
                            // otherwise resolve the sort through the full precedence and re-fetch.
                            if (loadSubredditCommentSort(post.subredditName) == null
                                && !respectSubredditRecommendedSortType) {
                                val parseCommentsResult = parseComments(response.body(), commentFilter!!, expandChildren)
                                publication.join()
                                when (parseCommentsResult) {
                                    is AppResult.Success<ParseCommentsResult> -> {
                                        _dataState.value = _dataState.value.copy(
                                            comments = parseCommentsResult.data.comments,
                                            children = parseCommentsResult.data.children
                                        )
                                        _uiState.value = _uiState.value.copy(
                                            isInitialLoading = false,
                                            isInitialLoadingFailed = false
                                        )
                                    }
                                    is AppResult.Error<*> -> {
                                        _uiState.value = _uiState.value.copy(
                                            isInitialLoading = false,
                                            isInitialLoadingFailed = true
                                        )
                                    }
                                }
                            } else {
                                fetchCommentsRespectRecommendedSortSync(false)
                            }
                        } ?: run {
                            _uiState.value = _uiState.value.copy(
                                isInitialLoading = false,
                                isInitialLoadingFailed = true,
                                //shouldShowErrorView = true
                                errorViewError = ViewPostDetailFragmentViewModelError.Json(null)
                            )
                        }
                    } else {
                        //{"reason": "banned", "message": "Not Found", "error": 404}
                        try {
                            response.errorBody().use { errorBody ->
                                if (errorBody != null) {
                                    val redditError = Gson().fromJson(
                                        errorBody.string(),
                                        RedditError::class.java
                                    )
                                    _uiState.value = _uiState.value.copy(
                                        isInitialLoading = false,
                                        isInitialLoadingFailed = true,
                                        errorViewError = ViewPostDetailFragmentViewModelError.RedditErrorWrapper(redditError)
                                    )
                                    return@launch
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        _uiState.value = _uiState.value.copy(
                            isInitialLoading = false,
                            isInitialLoadingFailed = true,
                            //shouldShowErrorView = true
                            errorViewError = ViewPostDetailFragmentViewModelError.Response(response.code(), response.message())
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    _uiState.value = _uiState.value.copy(
                        isInitialLoading = false,
                        isInitialLoadingFailed = true,
                        //shouldShowErrorView = true
                        errorViewError = ViewPostDetailFragmentViewModelError.Network(e)
                    )
                }
            } ?: run {
                _uiState.value = _uiState.value.copy(
                    isInitialLoading = false,
                    isInitialLoadingFailed = true,
                    //shouldShowErrorView = true
                    errorViewError = ViewPostDetailFragmentViewModelError.InvalidPostId
                )
            }
        }
    }

    fun fetchMoreComments() {
        viewModelScope.launch {
            // A failed load is deliberately not refused here: this is also the footer's retry button.
            // Scrolling is what must not retry on its own, and the fragment's listener checks for that.
            if (_uiState.value.isInitialLoading || _uiState.value.isLoadingMoreChildren) {
                return@launch
            }

            val childrenIds = _dataState.value.children
            val postId = _dataState.value.post?.id
            if (childrenIds.isNullOrEmpty() || postId == null) {
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isLoadingMoreChildren = true,
                loadMoreChildrenSuccess = true
            )

            try {
                val parseCommentResult = if (useLegacyMoreChildren) {
                    loadWithMoreChildren(childrenIds)
                } else {
                    parseMoreComments(moreCommentsLoader(postId).loadTopLevel(childrenIds))
                }
                if (parseCommentResult != null) {
                    val updatedComments = _dataState.value.comments?.let {
                        ArrayList(it)
                    } ?: run {
                        ArrayList()
                    }
                    updatedComments.addAll(parseCommentResult.comments)
                    _dataState.value = _dataState.value.copy(
                        comments = updatedComments,
                        children = parseCommentResult.children
                    )
                    _uiState.value = _uiState.value.copy(
                        isLoadingMoreChildren = false,
                        loadMoreChildrenSuccess = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMoreChildren = false,
                        loadMoreChildrenSuccess = false
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isLoadingMoreChildren = false,
                    loadMoreChildrenSuccess = false
                )
            }
        }
    }

    /**
     * @param position Placeholder position
     */
    fun fetchMoreChildComments(position: Int) {
        viewModelScope.launch {
            _dataState.value.comments?.getOrNull(position)?.let {
                val parentPosition = getParentPosition(position)
                _dataState.value.comments?.getOrNull(parentPosition)?.let { parentComment ->
                    if (it.isLoadingMoreChildren) {
                        return@launch
                    }

                    val childrenIds = parentComment.moreChildrenIds
                    val postId = _dataState.value.post?.id
                    val parentCommentId = parentComment.id
                    if (childrenIds.isNullOrEmpty() || postId == null || parentCommentId == null) {
                        return@launch
                    }

                    it.isLoadingMoreChildren = true
                    it.isLoadMoreChildrenFailed = false

                    try {
                        val parseCommentResult = if (useLegacyMoreChildren) {
                            loadWithMoreChildren(childrenIds)
                        } else {
                            parseMoreComments(
                                moreCommentsLoader(postId).loadReplies(parentCommentId, parentComment.depth + 1, childrenIds)
                            )
                        }
                        val currentParentCommentPosition = findCommentPosition(parentComment.fullName, parentPosition)

                        val comments = _dataState.value.comments
                        if (parseCommentResult != null && comments != null) {
                            comments.getOrNull(currentParentCommentPosition)?.let { currentParentComment ->
                                // Reddit's count for what is still unloaded where it gave one, otherwise
                                // what was pending less the replies that just loaded.
                                val pendingReplyCount = if (parseCommentResult.children.isEmpty()) {
                                    0
                                } else {
                                    parseCommentResult.childrenCount ?: (currentParentComment.pendingReplyCount
                                        - parseCommentResult.topLevelComments.sumOf { 1 + it.childCount })
                                }
                                // The row may be a copy of the comment the thread's tree holds, which is
                                // the one collapsing and expanding an ancestor brings back, so both learn
                                // what is still unloaded. Copies made below inherit it.
                                for (comment in setOfNotNull(currentParentComment, findInThreadTree(comments, currentParentCommentPosition))) {
                                    if (parseCommentResult.children.isEmpty()) {
                                        comment.removeMoreChildrenIds()
                                    } else {
                                        comment.moreChildrenIds = parseCommentResult.children
                                        comment.moreChildrenCount = pendingReplyCount
                                    }
                                }
                                if (currentParentComment.isExpanded) {
                                    run {
                                        val copiedParentComment = Comment(currentParentComment)
                                        val updatedComments = ArrayList(comments)
                                        updatedComments[currentParentCommentPosition] = copiedParentComment

                                        val placeholderPosition: Int =
                                            findLoadMoreCommentsPlaceholderPosition(
                                                copiedParentComment.fullName,
                                                position
                                            )

                                        if (!parseCommentResult.children.isEmpty()) {
                                            copiedParentComment.children[copiedParentComment.children.size - 1].isLoadingMoreChildren = false
                                            copiedParentComment.children[copiedParentComment.children.size - 1].isLoadMoreChildrenFailed = false

                                            if (placeholderPosition != -1) {
                                                // A new object, as DiffUtil would find nothing to rebind if
                                                // the placeholder were changed in place.
                                                val copiedPlaceholder = Comment(updatedComments[placeholderPosition])
                                                copiedPlaceholder.isLoadingMoreChildren = false
                                                copiedPlaceholder.isLoadMoreChildrenFailed = false
                                                updatedComments[placeholderPosition] = copiedPlaceholder
                                                copiedParentComment.children[copiedParentComment.children.size - 1] = copiedPlaceholder

                                                updatedComments.addAll(placeholderPosition, parseCommentResult.comments)
                                            }
                                        } else {
                                            copiedParentComment.children.removeAt(copiedParentComment.children.size - 1)

                                            if (placeholderPosition != -1) {
                                                updatedComments.removeAt(placeholderPosition)
                                                updatedComments.addAll(placeholderPosition, parseCommentResult.comments)
                                            }
                                        }
                                        copiedParentComment.addChildren(parseCommentResult.topLevelComments)
                                        recountReplies(updatedComments, currentParentCommentPosition)

                                        _dataState.value = _dataState.value.copy(
                                            comments = updatedComments
                                        )
                                    }
                                } else {
                                    // Collapsed, this row shows its count. DiffUtil compares the new row
                                    // with this one, so the change goes on a copy and this one is left as
                                    // it was drawn.
                                    val copiedParentComment = Comment(currentParentComment)
                                    if (!parseCommentResult.children.isEmpty()) {
                                        copiedParentComment.children[copiedParentComment.children.size - 1].isLoadingMoreChildren = false
                                        copiedParentComment.children[copiedParentComment.children.size - 1].isLoadMoreChildrenFailed = false
                                    } else if (copiedParentComment.hasReply()) {
                                        copiedParentComment.children.removeAt(copiedParentComment.children.size - 1)
                                    }
                                    copiedParentComment.addChildren(parseCommentResult.topLevelComments)
                                    val updatedComments = ArrayList(comments)
                                    updatedComments[currentParentCommentPosition] = copiedParentComment
                                    recountReplies(updatedComments, currentParentCommentPosition)
                                    _dataState.value = _dataState.value.copy(
                                        comments = updatedComments
                                    )
                                }
                            }
                        } else {
                            fetchMoreChildCommentsFailed(parentComment, currentParentCommentPosition)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        fetchMoreChildCommentsFailed(parentComment, findCommentPosition(parentComment.fullName, parentPosition))
                    } finally {
                        // Collapsed away mid-load, the parent has no row for this to land on. The
                        // placeholder comes back with it, and must still answer a tap.
                        if (findCommentPosition(parentComment.fullName, parentPosition) == -1) {
                            it.isLoadingMoreChildren = false
                        }
                    }
                } ?: run {
                    _dataState.value.comments?.let { currentComments ->
                        val updatedComments = ArrayList(currentComments)
                        val copiedPlaceholder = Comment(currentComments[position])
                        copiedPlaceholder.isLoadingMoreChildren = false
                        copiedPlaceholder.isLoadMoreChildrenFailed = true
                        currentComments[position] = copiedPlaceholder
                        _dataState.value = _dataState.value.copy(
                            comments = updatedComments
                        )
                    }
                }
            }
        }
    }

    /**
     * The thread's own object for the comment at [position], found under its top-level comment, which
     * the row itself may be a copy of. Null for a top-level row, which no tree holds.
     */
    private fun findInThreadTree(comments: List<Comment>, position: Int): Comment? {
        val fullName = comments[position].fullName
        for (i in position - 1 downTo 0) {
            val comment = comments[i]
            if (comment.depth == 0 && comment.placeholderType == Comment.NOT_PLACEHOLDER) {
                return ParseComment.findCommentByFullName(listOf(comment), fullName)
            }
        }
        return null
    }

    /**
     * Recounts the replies of the row at [position] and of each row it sits under, since a load
     * changes what is below all of them. A row can be a copy the thread's tree does not hold, so the
     * top-level comment's whole tree is recounted too.
     */
    private fun recountReplies(comments: List<Comment>, position: Int) {
        var depth = Int.MAX_VALUE
        for (i in position downTo 0) {
            val comment = comments[i]
            if (comment.placeholderType != Comment.NOT_PLACEHOLDER || comment.depth >= depth) {
                continue
            }
            depth = comment.depth
            if (depth == 0) {
                ParseComment.updateChildrenCount(listOf(comment))
                return
            }
            comment.childCount = ParseComment.getChildCount(comment)
        }
    }

    private fun fetchMoreChildCommentsFailed(parentComment: Comment, parentPosition: Int) {
        _dataState.value.comments?.let { comments ->
            if (parentPosition == -1) {
                // note: returning here is probably a mistake, because
                // parent is just not visible, but it can still exist in the comments tree.
                return
            }
            val currentParentComment: Comment = comments[parentPosition]

            val updatedComments = ArrayList(comments)

            if (currentParentComment.isExpanded) {
                val placeholderPositionHint =
                    parentPosition + currentParentComment.children.size
                val placeholderPosition =
                    findLoadMoreCommentsPlaceholderPosition(
                        parentComment.fullName,
                        placeholderPositionHint
                    )

                if (placeholderPosition != -1) {
                    val copiedPlaceholder = Comment(comments[placeholderPosition])
                    copiedPlaceholder.isLoadingMoreChildren = false
                    copiedPlaceholder.isLoadMoreChildrenFailed = true

                    updatedComments[placeholderPosition] = copiedPlaceholder
                    currentParentComment.children[currentParentComment.children.size - 1] = copiedPlaceholder
                }
            }
            currentParentComment.children[currentParentComment.children.size - 1].isLoadingMoreChildren = false
            currentParentComment.children[currentParentComment.children.size - 1].isLoadMoreChildrenFailed = true

            _dataState.value = _dataState.value.copy(
                comments = updatedComments
            )
        }
    }

    private fun getParentPosition(position: Int): Int {
        _dataState.value.comments?.let {
            if (position >= 0 && position < it.size) {
                val childDepth: Int = it[position].depth
                for (i in position downTo 0) {
                    if (it[i].depth < childDepth) {
                        return i
                    }
                }
            }
        }
        return -1
    }

    private fun findCommentPosition(fullName: String?, positionHint: Int): Int {
        return findCommentPosition(fullName, positionHint, Comment.NOT_PLACEHOLDER)
    }

    private fun findCommentPosition(
        fullName: String?,
        positionHint: Int,
        placeholderType: Int
    ): Int {
        _dataState.value.comments?.let {
            if (0 <= positionHint && positionHint < it.size && it.get(
                    positionHint
                ).fullName == fullName
                && it[positionHint].placeholderType == placeholderType
            ) {
                return positionHint
            }

            for (i in it.indices) {
                val comment: Comment = it[i]
                if (comment.fullName == fullName && comment.placeholderType == placeholderType) {
                    return i
                }
            }
        }
        return -1
    }

    private fun findLoadMoreCommentsPlaceholderPosition(fullName: String?, positionHint: Int): Int {
        return findCommentPosition(fullName, positionHint, Comment.PLACEHOLDER_LOAD_MORE_COMMENTS)
    }

    suspend fun fetchCommentFilter(subredditName: String): CommentFilter {
        val commentFilterList: List<CommentFilter> =
            redditDataRoomDatabase.commentFilterDaoKt()
                .getValidCommentFilters(CommentFilterUsage.SUBREDDIT_TYPE, subredditName, accountName)
        return CommentFilter.mergeCommentFilter(commentFilterList)
    }

    suspend fun fetchSubredditData(subredditName: String): AppResult<SubredditData?, ViewPostDetailFragmentViewModelError> {
        try {
            val response: Response<String> = if (accountName == Account.ANONYMOUS_ACCOUNT) {
                retrofit.create(RedditAPIKt::class.java).getSubredditData(subredditName)
            } else {
                oauthRetrofit.create(RedditAPIKt::class.java).getSubredditDataOauth(
                    subredditName, APIUtils.getOAuthHeader(accessToken)
                )
            }
            if (response.isSuccessful) {
                return withContext(Dispatchers.Default) {
                    try {
                        val data: JSONObject = JSONObject(response.body() ?: throw JSONException("Empty response body")).getJSONObject(JSONUtils.DATA_KEY)
                        AppResult.Success(ParseSubredditData.parseSubredditDataSync(data, true))
                    } catch (e: JSONException) {
                        e.printStackTrace()
                        AppResult.Error(ViewPostDetailFragmentViewModelError.Json(e))
                    }
                }
            } else {
                return if (response.code() == 403) {
                    AppResult.Error(ViewPostDetailFragmentViewModelError.Quarantined)
                } else {
                    AppResult.Error(ViewPostDetailFragmentViewModelError.Response(response.code(), response.errorBody()?.string()))
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return AppResult.Error(ViewPostDetailFragmentViewModelError.Network(e))
        }
    }

    fun refresh(fetchPost: Boolean, fetchComments: Boolean) {
        viewModelScope.launch {
            if (!_uiState.value.isRefreshing) {
                // Move off the comment `limit` the previous read used, so a refresh cannot be
                // answered from the same cached comment tree. See [commentLimitFor].
                commentFetchNonce++

                _uiState.value = _uiState.value.copy(
                    isRefreshing = true,
                    fetchPostFailed = false,
                    //shouldShowErrorView = false
                    errorViewError = null
                )

                if (!fetchPost && fetchComments) {
                    _dataState.value = _dataState.value.copy(
                        comments = null,
                        children = null
                    )

                    fetchCommentsRespectRecommendedSortSync(true)
                }

                if (fetchPost) {
                    derivedPostId?.let {
                        try {
                            val response: Response<String> = if (accountName == Account.ANONYMOUS_ACCOUNT) {
                                retrofit.create(RedditAPIKt::class.java).getPost(it)
                            } else {
                                oauthRetrofit.create(RedditAPIKt::class.java)
                                    .getPostOauth(it, APIUtils.getOAuthHeader(accessToken))
                            }

                            if (response.isSuccessful) {
                                val parsed = withContext(Dispatchers.Default) {
                                    response.body()?.let { ParsePost.parsePostSync(it) }
                                }
                                parsed?.let { parsed ->
                                    val post = recoverRefreshedPost(parsed) ?: _dataState.value.post ?: parsed
                                    if (fetchComments) {
                                        _dataState.value = _dataState.value.copy(
                                            post = post,
                                            comments = null,
                                            children = null
                                        )

                                        fetchCommentsRespectRecommendedSortSync(true)
                                    } else {
                                        setPost(post)

                                        _uiState.value = _uiState.value.copy(
                                            isRefreshing = false
                                        )
                                    }
                                } ?: run {
                                    _uiState.value = _uiState.value.copy(
                                        fetchPostFailed = true,
                                        isRefreshing = false
                                    )
                                }
                            } else {
                                _uiState.value = _uiState.value.copy(
                                    fetchPostFailed = true,
                                    isRefreshing = false
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            _uiState.value = _uiState.value.copy(
                                fetchPostFailed = true,
                                isRefreshing = false
                            )
                        }
                    } ?: run {
                        _uiState.value = _uiState.value.copy(
                            fetchPostFailed = true,
                            isRefreshing = false
                        )
                    }
                }
            }
        }
    }

    private suspend fun parseComments(
        response: String?,
        commentFilter: CommentFilter,
        expandChildren: Boolean
    ): AppResult<ParseCommentsResult, JSONException> {
        // A fresh thread's placeholders have not had a parent listing yet.
        moreCommentsSession.onThreadReloaded()
        return withContext(Dispatchers.Default) {
            try {
                var childrenArray = JSONArray(response)
                val parentId = childrenArray.getJSONObject(0).getJSONObject(JSONUtils.DATA_KEY)
                    .getJSONArray(JSONUtils.CHILDREN_KEY)
                    .getJSONObject(0).getJSONObject(JSONUtils.DATA_KEY).getString(JSONUtils.NAME_KEY)
                childrenArray = childrenArray.getJSONObject(1).getJSONObject(JSONUtils.DATA_KEY)
                    .getJSONArray(JSONUtils.CHILDREN_KEY)

                val expandedNewComments = ArrayList<Comment>()
                val moreChildrenIds = ArrayList<String>()
                val newComments = ArrayList<Comment>()

                ParseComment.parseCommentRecursion(
                    childrenArray,
                    newComments,
                    moreChildrenIds,
                    0,
                    commentFilter
                )
                ParseComment.expandChildren(newComments, expandedNewComments, expandChildren)
                val commentData = if (expandChildren) {
                    expandedNewComments
                } else {
                    newComments
                }

                preRecoverComments(newComments)

                AppResult.Success(ParseCommentsResult(
                    commentData,
                    newComments,
                    moreChildrenIds
                ))
            } catch (e: JSONException) {
                e.printStackTrace()
                AppResult.Error(e)
            }
        }
    }

    /** The old route, kept behind a setting for the accounts Reddit does not block it on. */
    private suspend fun loadWithMoreChildren(ids: List<String>): ParseCommentsResult? {
        val childrenIds = java.lang.String.join(",", ids)
        val response: Response<String> = if (accountName == Account.ANONYMOUS_ACCOUNT) {
            retrofit.create(RedditAPIKt::class.java).moreChildren(_dataState.value.post?.fullName, childrenIds, _uiState.value.sortType)
        } else {
            oauthRetrofit.create(RedditAPIKt::class.java).moreChildrenOauth(
                _dataState.value.post?.fullName, childrenIds,
                _uiState.value.sortType, APIUtils.getOAuthHeader(accessToken)
            )
        }
        if (!response.isSuccessful) {
            return null
        }
        return when (val result = parseMoreChildrenResponse(response.body(), expandChildren)) {
            is AppResult.Success<ParseCommentsResult> -> result.data
            is AppResult.Error<*> -> null
        }
    }

    private fun moreCommentsLoader(postId: String): MoreCommentsLoader {
        val sortType = _uiState.value.sortType
        return MoreCommentsLoader(commentFilter ?: CommentFilter(), moreCommentsSession, if (accountName == Account.ANONYMOUS_ACCOUNT) {
            val api = retrofit.create(RedditAPIKt::class.java)
            ({ commentId, limit -> api.getFocusedComment(postId, commentId, sortType, limit) })
        } else {
            val api = oauthRetrofit.create(RedditAPIKt::class.java)
            val headers = APIUtils.getOAuthHeader(accessToken)
            ({ commentId, limit -> api.getFocusedCommentOauth(postId, commentId, sortType, limit, headers) })
        })
    }

    private suspend fun parseMoreComments(page: MoreCommentsLoader.Page?): ParseCommentsResult? {
        if (page == null) {
            return null
        }
        return withContext(Dispatchers.Default) {
            val expandedNewComments = ArrayList<Comment>()
            ParseComment.expandChildren(page.topLevelComments, expandedNewComments, expandChildren)
            val commentData = if (expandChildren) {
                expandedNewComments
            } else {
                page.topLevelComments
            }

            preRecoverComments(page.topLevelComments)

            ParseCommentsResult(commentData, page.topLevelComments, page.remainingIds, page.remainingCount)
        }
    }

    private suspend fun parseMoreChildrenResponse(
        response: String?,
        expandChildren: Boolean
    ): AppResult<ParseCommentsResult, JSONException> {
        return withContext(Dispatchers.Default) {
            try {
                val childrenArray: JSONArray =
                    JSONObject(response ?: throw JSONException("Empty response body")).getJSONObject(JSONUtils.JSON_KEY)
                        .getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.THINGS_KEY)

                val newComments = ArrayList<Comment>()
                val expandedNewComments = ArrayList<Comment>()
                val moreChildrenIds = ArrayList<String>()
                var moreChildrenCount = 0

                // api response is a flat list of comments tree
                // process it in order and rebuild the tree
                for (i in 0..<childrenArray.length()) {
                    val child = childrenArray.getJSONObject(i)
                    val childData = child.getJSONObject(JSONUtils.DATA_KEY)
                    if (child.getString(JSONUtils.KIND_KEY) == JSONUtils.KIND_VALUE_MORE) {
                        val parentFullName = childData.getString(JSONUtils.PARENT_ID_KEY)
                        val childrenIds = childData.getJSONArray(JSONUtils.CHILDREN_KEY)

                        if (childrenIds.length() != 0) {
                            val localMoreChildrenIds =
                                ArrayList<String>(childrenIds.length())
                            for (j in 0..<childrenIds.length()) {
                                localMoreChildrenIds.add(childrenIds.getString(j))
                            }

                            val parentComment =
                                ParseComment.findCommentByFullName(newComments, parentFullName)
                            if (parentComment != null) {
                                parentComment.setHasReply(true)
                                parentComment.moreChildrenIds = localMoreChildrenIds
                                parentComment.moreChildrenCount = childData.optInt(JSONUtils.COUNT_KEY)
                                parentComment.addChildren(java.util.ArrayList<Comment>()) // ensure children list is not null
                            } else {
                                // assume that it is parent of this call
                                moreChildrenIds.addAll(localMoreChildrenIds)
                                moreChildrenCount += childData.optInt(JSONUtils.COUNT_KEY)
                            }
                        } else {
                            val continueThreadPlaceholder = Comment(
                                parentFullName,
                                childData.getInt(JSONUtils.DEPTH_KEY),
                                Comment.PLACEHOLDER_CONTINUE_THREAD
                            )

                            val parentComment =
                                ParseComment.findCommentByFullName(newComments, parentFullName)
                            if (parentComment != null) {
                                parentComment.setHasReply(true)
                                parentComment.addChild(
                                    continueThreadPlaceholder,
                                    parentComment.getChildCount()
                                )
                                parentComment.setChildCount(parentComment.getChildCount() + 1)
                            } else {
                                // assume that it is parent of this call
                                newComments.add(continueThreadPlaceholder)
                            }
                        }
                    } else {
                        try {
                            val comment = ParseComment.parseSingleComment(childData, 0)
                            val parentFullName = comment.getParentId()

                            val parentComment = parentFullName?.let {
                                ParseComment.findCommentByFullName(newComments, it)
                            }
                            if (parentComment != null) {
                                parentComment.setHasReply(true)
                                parentComment.addChild(comment, parentComment.getChildCount())
                                parentComment.setChildCount(parentComment.getChildCount() + 1)
                            } else {
                                // assume that it is parent of this call
                                newComments.add(comment)
                            }
                        } catch (e: JSONException) {
                            // Well we need to catch and ignore the exception to not show "error loading comments" to users
                            e.printStackTrace()
                        } catch (e: RuntimeException) {
                            e.printStackTrace()
                        }
                    }
                }

                ParseComment.updateChildrenCount(newComments)
                ParseComment.expandChildren(newComments, expandedNewComments, expandChildren)
                val commentData = if (expandChildren) {
                    expandedNewComments
                } else {
                    newComments
                }

                preRecoverComments(newComments)

                AppResult.Success(
                    ParseCommentsResult(commentData, newComments, moreChildrenIds, moreChildrenCount)
                )
            } catch (e: JSONException) {
                e.printStackTrace()
                AppResult.Error(e)
            }
        }
    }

    @Nullable
    private suspend fun parsePost(response: String?): Post? {
        return withContext(Dispatchers.Default) {
            val allData: JSONArray =
                JSONArray(response).getJSONObject(0).getJSONObject(JSONUtils.DATA_KEY)
                    .getJSONArray(JSONUtils.CHILDREN_KEY)
            if (allData.length() == 0) {
                null
            } else {
                val data = allData.getJSONObject(0).getJSONObject(JSONUtils.DATA_KEY)
                ParsePost.parseBasicData(data)
            }
        }
    }

    /**
     * [post] as the archive holds it, or [post] itself when Reddit never scrubbed it, the archive
     * has nothing, or the wait ran out. fetchArchived has no deadline of its own, and the network
     * timeouts behind it are 30s, which is not something a post's first draw may wait on.
     *
     * An answer that lands after the wait is not dropped: it is held in [latePostRecovery], to be
     * written onto whatever the post has become once its row is off screen.
     */
    private suspend fun recoverPostWithinWait(post: Post): Post {
        if (!RecoverRemovedPost.needsRecovery(post)) {
            return post
        }

        val archived = viewModelScope.async { RecoverRemovedPost.fetchArchived(arcticShiftRetrofit, post) }
        if (withTimeoutOrNull(POST_RECOVERY_WAIT_MILLIS) { archived.join() } != null) {
            val result = archived.await() ?: return post
            postArchive = result
            return RecoverRemovedPost.recovered(post, result)
        }

        viewModelScope.launch {
            val result = archived.await() ?: return@launch
            postArchive = result
            latePostRecovery = result
            lateRecoveryLiveData.value = Unit
        }
        return post
    }

    /** Whether the archive answered for the post after it was drawn, and that is yet to be applied. */
    fun hasLatePostRecovery(): Boolean = latePostRecovery != null

    /**
     * Writes the archive's late answer onto the post as it is now — votes, saves and all — and
     * publishes it. Only for while the post's row is off screen, which is why it returns the post:
     * the fragment hands it to the adapter itself, at once, before anything can scroll the row back.
     *
     * @return the recovered post, or null when there is nothing to apply — including a post that
     *         was recovered some other way in the meantime.
     */
    fun applyLatePostRecovery(): Post? {
        val archived = latePostRecovery ?: return null
        latePostRecovery = null
        val current = _dataState.value.post ?: return null
        if (!RecoverRemovedPost.needsRecovery(current)) {
            return null
        }
        val recovered = RecoverRemovedPost.recovered(current, archived)
        setPost(recovered)
        return recovered
    }

    /**
     * A refreshed [post] recovered the way the one on screen was, so a pull-to-refresh never puts
     * "[ Removed by moderator ]" back over content the archive restored. Reuses the archive's
     * earlier answer when there is one; otherwise asks, under the usual wait.
     *
     * @return null to keep the post on screen as it is: it shows archived content this view model
     *         has no answer for — recovered before it got here — and the archive did not answer in
     *         time to rebuild it.
     */
    private suspend fun recoverRefreshedPost(post: Post): Post? {
        // Reddit still serves a removed post's content to its author — and the refresh that follows
        // an edit is the author's — so the archive's older copy must not go back over it.
        if (!RecoverRemovedPost.needsRecovery(post) || post.author == accountName) {
            return post
        }
        postArchive?.let { return RecoverRemovedPost.recovered(post, it) }

        val recovered = recoverPostWithinWait(post)
        if (recovered === post && _dataState.value.post?.isRecovered == true) {
            return null
        }
        return recovered
    }

    /**
     * Publishes [post] once it has been recovered, so a removed post is never drawn as
     * "[ Removed by moderator ]" and then swapped for its real title a moment later. Recorded as
     * [postRecovery], so a rotated view comes back to this answer instead of asking again.
     */
    private fun publishPostAfterRecovery(post: Post): Job {
        val recovery = viewModelScope.async { recoverPostWithinWait(post) }
        postRecovery = recovery
        return viewModelScope.launch {
            setPost(recovery.await())
            unpublishedPost = null
        }
    }

    // The comment sort fallback is the "Comment Default Sort Type" setting, resolved in the
    // fragment and passed in. Per-subreddit saved sorts and recommendations are layered on top
    // of this in fetchCommentsRespectRecommendedSortSync.
    private fun loadSortType(): SortType.Type = commentDefaultSortType

    fun updateSortType(sortType: SortType.Type): SortType.Type {
        _uiState.value = _uiState.value.copy(
            sortType = sortType
        )
        return sortType
    }

    fun getSortType(): SortType.Type {
        return _uiState.value.sortType ?: updateSortType(loadSortType())
    }

    fun clearSingleCommentId(respectSubredditRecommendedSortType: Boolean) {
        this.respectSubredditRecommendedSortType = respectSubredditRecommendedSortType
        _uiState.value = _uiState.value.copy(
            singleCommentId = null
        )
        refresh(fetchPost = false, fetchComments = true)
    }

    fun toggleExpandComment(position: Int): Boolean {
        _dataState.value.comments?.let { comments ->
            val comment = comments.getOrNull(position)
            comment?.let {
                if (it.isExpanded) {
                    return collapseComment(position)
                } else {
                    val updatedComment = Comment(it)
                    updatedComment.setExpanded(true)

                    val newList = ArrayList<Comment>()
                    expandComment(it.children, newList)

                    val updatedComments = ArrayList(comments)
                    updatedComments[position] = updatedComment
                    if (newList.isNotEmpty()) {
                        updatedComments.addAll(position + 1, newList)
                    }

                    _dataState.value = _dataState.value.copy(
                        comments = updatedComments
                    )

                    return newList.isNotEmpty()
                }
            }
        }

        return false
    }

    private fun expandComment(
        comments: ArrayList<Comment>?,
        newList: ArrayList<Comment>
    ) {
        if (!comments.isNullOrEmpty()) {
            for (comment in comments) {
                newList.add(comment)
                expandComment(comment.children, newList)
                comment.setExpanded(true)
            }
        }
    }

    fun collapseComment(position: Int): Boolean {
        _dataState.value.comments?.let { comments ->
            val comment = comments.getOrNull(position)
            comment?.let {
                val updatedComment = Comment(it)
                updatedComment.setExpanded(false)

                val depth: Int = it.depth
                var allChildrenSize = 0
                for (i in position + 1..<comments.size) {
                    if (comments[i].depth > depth) {
                        allChildrenSize++
                    } else {
                        break
                    }
                }

                val updatedComments = ArrayList(comments)
                updatedComments[position] = updatedComment

                if (allChildrenSize > 0) {
                    updatedComments.subList(position + 1, position + 1 + allChildrenSize).clear()
                }

                _dataState.value = _dataState.value.copy(
                    comments = updatedComments
                )

                return allChildrenSize > 0
            }
        }

        return false
    }

    fun addComment(comment: Comment) {
        _dataState.value.comments?.let {
            val updatedPost = _dataState.value.post?.let { post ->
                Post(post).apply {
                    nComments += 1
                }
            }
            val updatedComments = ArrayList(it)
            updatedComments.add(0, comment)
            _dataState.value = _dataState.value.copy(
                post = updatedPost,
                comments = updatedComments
            )
        } ?: run {
            val updatedPost = _dataState.value.post?.let { post ->
                Post(post).apply {
                    nComments += 1
                }
            }
            val updatedComments = ArrayList<Comment>()
            updatedComments.add(comment)
            _dataState.value = _dataState.value.copy(
                post = updatedPost,
                comments = updatedComments
            )
        }
    }

    fun addChildComment(comment: Comment, parentFullname: String?, parentPosition: Int) {
        _dataState.value.comments?.let {
            var finalParentPosition = parentPosition
            var parentComment = it[finalParentPosition]
            if (parentFullname != parentComment.fullName) {
                for (i in it.indices) {
                    if (parentFullname == it[i].fullName) {
                        parentComment = it[i]
                        finalParentPosition = i
                        break
                    }
                }
            }

            val updatedParentComment = Comment(parentComment)
            updatedParentComment.addChild(comment)
            updatedParentComment.setHasReply(true)

            val updatedComments = ArrayList(it)
            updatedComments[finalParentPosition] = updatedParentComment
            if (!updatedParentComment.isExpanded) {
                val newList = ArrayList<Comment>()

                expandComment(updatedParentComment.children, newList)

                updatedParentComment.setExpanded(true)
                updatedComments.addAll(finalParentPosition + 1, newList)
            } else {
                updatedComments.add(finalParentPosition + 1, comment)
            }

            val updatedPost = _dataState.value.post?.let { post ->
                Post(post).apply {
                    nComments += 1
                }
            }
            _dataState.value = _dataState.value.copy(
                post = updatedPost,
                comments = updatedComments
            )
        }
    }

    fun restoreCache(cache: PostDetailCommentsCache) {
        // The cached comments were recovered when they were first parsed, and this is what stops the
        // restored view offering to recover the ones the archive already said it does not have.
        recoveryAttemptedIds.addAll(cache.recoveryAttemptedIds)
        // Held back at the time, but nothing of this view is on screen yet; the fragment lets them
        // through before its first draw.
        lateRecoveries.putAll(cache.lateRecoveries)

        if (_dataState.value.post == null) {
            _dataState.value = _dataState.value.copy(
                post = cache.post,
                comments = cache.visibleComments,
                children = cache.children
            )

            return
        }
        _dataState.value = _dataState.value.copy(
            comments = cache.visibleComments,
            children = cache.children
        )

        _uiState.value = _uiState.value.copy(
            sortType = cache.sortType
        )
    }

    fun tryMarkingPostAsRead() {
        if (markPostsAsRead) {
            viewModelScope.launch {
                _dataState.value.post?.let {
                    if (!it.isRead) {
                        val updatedPost = Post(it).apply {
                            markAsRead()
                        }

                        val readPostsLimit = ReadPostsUtils.GetReadPostsLimit(
                            accountName,
                            postHistorySharedPreferences
                        )
                        insertReadPost(
                            redditDataRoomDatabase,
                            accountName,
                            it.id,
                            ReadPostType.READ_POSTS,
                            readPostsLimit
                        )

                        setPost(updatedPost)
                    }
                }
            }
        }
    }

    fun toggleSavePost(position: Int) {
        viewModelScope.launch {
            _dataState.value.post?.let { post ->
                if (Account.ANONYMOUS_ACCOUNT == accountName) {
                    if (post.isSaved) {
                        deleteReadPost(
                            redditDataRoomDatabase,
                            accountName,
                            post.id,
                            ReadPostType.ANONYMOUS_SAVED_POSTS
                        )

                        postModerationEventLiveData.postValue(
                            PostModerationEvent.Unsaved(
                                post,
                                position
                            )
                        )
                    } else {
                        insertReadPost(
                            redditDataRoomDatabase,
                            accountName,
                            post.id,
                            ReadPostType.ANONYMOUS_SAVED_POSTS,
                            ReadPostsUtils.GetReadPostsLimit(
                                accountName,
                                postHistorySharedPreferences
                            )
                        )

                        postModerationEventLiveData.postValue(
                            PostModerationEvent.Saved(
                                post,
                                position
                            )
                        )
                    }

                    setPost(Post(post).apply {
                        isSaved = !isSaved
                    })
                } else {
                    accessToken?.let { accessToken ->
                        if (post.isSaved) {
                            if (unsaveThing(
                                oauthRetrofit, accessToken, post.fullName
                            )) {
                                LocalSaved.onUnsaved(
                                    redditDataRoomDatabase, accountName, post.fullName
                                )
                                SavedPostCacheNotifier.onSavedPostChanged()
                                setPost(Post(post).apply {
                                    isSaved = !isSaved
                                })

                                postModerationEventLiveData.postValue(
                                    PostModerationEvent.Unsaved(
                                        post,
                                        position
                                    )
                                )
                            } else {
                                postModerationEventLiveData.postValue(
                                    PostModerationEvent.UnsaveFailed(
                                        post,
                                        position
                                    )
                                )
                            }
                        } else {
                            if (saveThing(
                                    oauthRetrofit, accessToken, post.fullName
                            )) {
                                LocalSaved.onSaved(
                                    redditDataRoomDatabase, oauthRetrofit, accessToken,
                                    accountName, post.fullName
                                )
                                SavedPostCacheNotifier.onSavedPostChanged()
                                setPost(Post(post).apply {
                                    isSaved = !isSaved
                                })

                                postModerationEventLiveData.postValue(
                                    PostModerationEvent.Saved(
                                        post,
                                        position
                                    )
                                )
                            } else {
                                postModerationEventLiveData.postValue(
                                    PostModerationEvent.SaveFailed(
                                        post,
                                        position
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun toggleHidePost(position: Int) {
        viewModelScope.launch {
            _dataState.value.post?.let { post ->
                if (Account.ANONYMOUS_ACCOUNT == accountName) {
                    if (post.isHidden) {
                        deleteReadPost(
                            redditDataRoomDatabase,
                            accountName,
                            post.id,
                            ReadPostType.ANONYMOUS_HIDDEN_POSTS
                        )

                        postModerationEventLiveData.postValue(
                            PostModerationEvent.Unhid(
                                post,
                                position
                            )
                        )
                    } else {
                        insertReadPost(
                            redditDataRoomDatabase,
                            accountName,
                            post.id,
                            ReadPostType.ANONYMOUS_HIDDEN_POSTS,
                            ReadPostsUtils.GetReadPostsLimit(
                                accountName,
                                postHistorySharedPreferences
                            )
                        )

                        postModerationEventLiveData.postValue(
                            PostModerationEvent.Hid(
                                post,
                                position
                            )
                        )
                    }

                    setPost(Post(post).apply {
                        isSaved = !isSaved
                    })
                } else {
                    accessToken?.let { accessToken ->
                        if (post.isHidden) {
                            if (unhidePost(
                                    oauthRetrofit, accessToken, post.fullName
                            )) {
                                setPost(Post(post).apply {
                                    isHidden = !isHidden
                                })

                                postModerationEventLiveData.postValue(
                                    PostModerationEvent.Unhid(
                                        post,
                                        position
                                    )
                                )
                            } else {
                                postModerationEventLiveData.postValue(
                                    PostModerationEvent.UnhideFailed(
                                        post,
                                        position
                                    )
                                )
                            }
                        } else {
                            if (hidePost(
                                    oauthRetrofit, accessToken, post.fullName
                            )) {
                                setPost(Post(post).apply {
                                    isHidden = !isHidden
                                })

                                postModerationEventLiveData.postValue(
                                    PostModerationEvent.Hid(
                                        post,
                                        position
                                    )
                                )
                            } else {
                                postModerationEventLiveData.postValue(
                                    PostModerationEvent.HideFailed(
                                        post,
                                        position
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun deletePost(position: Int) {
        viewModelScope.launch {
            _dataState.value.post?.let {
                if (accessToken != null) {
                    if (deleteThing(oauthRetrofit, it.fullName, accessToken)) {
                        postModerationEventLiveData.postValue(
                            PostModerationEvent.Deleted(
                                it, position
                            )
                        )
                    } else {
                        postModerationEventLiveData.postValue(
                            PostModerationEvent.DeleteFailed(
                                it, position
                            )
                        )
                    }
                }
            } ?: run {
                // TODO PostModerationEvent.DeleteFailed
                postModerationEventLiveData.postValue(
                    PostModerationEvent.DeleteFailed(
                        null, position
                    )
                )
            }
        }
    }

    fun toggleSaveComment(comment: Comment, position: Int) {
        viewModelScope.launch {
            _dataState.value.comments?.let {
                if (accessToken != null) {
                    val updatedComments = ArrayList(it)

                    var oldComment: Comment? = it.getOrNull(position)
                    if (!oldComment?.id.equals(comment.id)) {
                        val currentPosition = findCommentPosition(comment.fullName, position)
                        if (currentPosition >= 0 && currentPosition < it.size) {
                            oldComment = it[currentPosition]
                        }
                    }

                    oldComment?.let {
                        if (oldComment.isSaved) {
                            if (unsaveThing(
                                    oauthRetrofit, accessToken, oldComment.fullName
                                )) {
                                val updatedComment = Comment(oldComment)
                                updatedComment.isSaved = !oldComment.isSaved

                                updatedComments[position] = updatedComment

                                _dataState.value = _dataState.value.copy(
                                    comments = updatedComments
                                )

                                commentModerationEventLiveData.postValue(
                                    CommentModerationEvent.Unsaved(
                                        oldComment,
                                        position
                                    )
                                )
                            } else {
                                commentModerationEventLiveData.postValue(
                                    CommentModerationEvent.UnsaveFailed(
                                        oldComment,
                                        position
                                    )
                                )
                            }
                        } else {
                            if (saveThing(
                                    oauthRetrofit, accessToken, oldComment.fullName
                                )) {
                                val updatedComment = Comment(oldComment)
                                updatedComment.isSaved = !oldComment.isSaved

                                updatedComments[position] = updatedComment

                                _dataState.value = _dataState.value.copy(
                                    comments = updatedComments
                                )

                                commentModerationEventLiveData.postValue(
                                    CommentModerationEvent.Saved(
                                        oldComment,
                                        position
                                    )
                                )
                            } else {
                                commentModerationEventLiveData.postValue(
                                    CommentModerationEvent.SaveFailed(
                                        oldComment,
                                        position
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun editComment(comment: Comment, position: Int) {
        _dataState.value.comments?.let {
            val updatedComments = ArrayList(it)

            if (position < it.size && position >= 0) {
                val oldComment: Comment = it[position]
                if (oldComment.id.equals(comment.id)) {
                    val updatedComment = Comment(oldComment)
                    updatedComment.commentMarkdown = comment.commentMarkdown
                    updatedComment.mediaMetadataMap = comment.mediaMetadataMap

                    updatedComments[position] = updatedComment

                    _dataState.value = _dataState.value.copy(
                        comments = updatedComments
                    )
                } else {
                    val currentPosition = findCommentPosition(comment.fullName, position)
                    if (currentPosition >= 0 && currentPosition < it.size) {
                        val updatedComment = Comment(it[currentPosition])
                        updatedComment.commentMarkdown = comment.commentMarkdown
                        updatedComment.mediaMetadataMap = comment.mediaMetadataMap

                        updatedComments[currentPosition] = updatedComment

                        _dataState.value = _dataState.value.copy(
                            comments = updatedComments
                        )
                    }
                }
            }
        }
    }

    fun recoverComment(comment: Comment, recoveredMarkdown: String, recoveredAuthor: String?,
                       recoveredAuthorFlair: String?, recoveredAuthorFlairHTML: String?, position: Int) {
        _dataState.value.comments?.let {
            val updatedComments = ArrayList(it)

            if (position < it.size && position >= 0) {
                val targetPosition = if (it[position].id.equals(comment.id)) {
                    position
                } else {
                    findCommentPosition(comment.fullName, position)
                }
                if (targetPosition in it.indices) {
                    val updatedComment = Comment(it[targetPosition])
                    updatedComment.commentMarkdown = recoveredMarkdown
                    updatedComment.commentRawText = recoveredMarkdown
                    if (recoveredAuthor != null) {
                        updatedComment.setAuthor(recoveredAuthor)
                        updatedComment.setAuthorFlair(recoveredAuthorFlair)
                        updatedComment.setAuthorFlairHTML(recoveredAuthorFlairHTML)
                    }

                    updatedComments[targetPosition] = updatedComment

                    _dataState.value = _dataState.value.copy(
                        comments = updatedComments
                    )
                }
            }
        }
    }

    /**
     * Recovers the post this view was opened with — one the feed handed over already parsed — before
     * the fragment draws it for the first time.
     *
     * [onReady] is called on the main thread with the post to draw: synchronously when Reddit has
     * not scrubbed this post, so an ordinary post's path is exactly as it was, and otherwise once
     * the archive has answered or the wait has run out. The comments may be fetched alongside; they
     * are not published until [onReady] has run.
     */
    fun recoverPostBeforeFirstRender(post: Post, onReady: (Post) -> Unit) {
        if (!RecoverRemovedPost.needsRecovery(post)) {
            onReady(post)
            return
        }

        // Asked once per ViewModel. A rotation brings a new view to the same answer — waiting for it
        // while it is still on its way — instead of asking again, which would hold the redraw of a
        // post already on screen on the archive and swap the post when it answered. A view arriving
        // after the answer draws the post it saved, which carries anything done to it since.
        val asked = postRecovery
        if (asked != null) {
            if (asked.isCompleted) {
                onReady(post)
            } else {
                viewModelScope.launch { onReady(asked.await()) }
            }
            return
        }

        val recovery = viewModelScope.async { recoverPostWithinWait(post) }
        postRecovery = recovery
        postPublication = viewModelScope.launch {
            val recovered = recovery.await()
            if (recovered !== post) {
                // Published before onReady so the adapter is holding the recovered post by the time
                // the fragment attaches it, which is what makes this a first draw and not a redraw.
                _dataState.value = _dataState.value.copy(post = recovered)
            }
            // Either way the post is drawn exactly once. A late answer is held rather than applied
            // here: swapping the title out from under a post already on screen is the redraw this
            // whole path exists to avoid. It goes in once the row is off screen.
            onReady(recovered)
        }
    }

    /**
     * Recovers a freshly parsed page of comments before it is handed to the list, so a removed
     * comment arrives with its archived body already in place rather than appearing as "[deleted]"
     * and swapping a moment later. Covers replies that are still collapsed too — expanding a thread
     * puts those same objects on screen.
     *
     * Waits at most [RecoverRemovedComments.RENDER_WAIT_MILLIS], then lets the page be drawn. The
     * pass itself carries on: what it brings back after that goes to [lateRecoveries], to be written
     * in only as each row is drawn off screen.
     *
     * Skipped above [RecoverRemovedComments.AUTO_RECOVERY_COMMENT_LIMIT] comments, where recovery is
     * the user's call, so opening a mega-thread is never made to wait on the archive.
     */
    private suspend fun preRecoverComments(comments: List<Comment>) {
        if ((commentsPost?.getNComments()
                ?: 0) > RecoverRemovedComments.AUTO_RECOVERY_COMMENT_LIMIT) {
            return
        }

        val all = RecoverRemovedComments.flatten(comments)
        val ids = RecoverRemovedComments.candidateIds(all)
            .filterNot { recoveryAttemptedIds.contains(it) || recoveryInFlightIds.contains(it) }
        if (ids.isEmpty()) {
            return
        }
        recoveryInFlightIds.addAll(ids)

        val generation = recoveryGeneration
        val byId = all.associateBy { it.id }
        // The pass hands chunks over on the main thread while this page waits on a parsing thread.
        // The lock makes "still before the draw" one answer for both: a chunk either lands on the
        // parsed objects before they are published, or goes to lateRecoveries — never onto the
        // objects once they are.
        val lock = Any()
        var beforeRender = true
        val pass = viewModelScope.launch(backgroundRecovery) {
            try {
                RecoverRemovedComments.recover(arcticShiftRetrofit, ids) { requested, recovered ->
                    if (generation != recoveryGeneration) {
                        return@recover
                    }
                    recoveryAttemptedIds.addAll(requested)
                    synchronized(lock) {
                        if (beforeRender) {
                            for ((id, result) in recovered) {
                                byId[id]?.let { RecoverRemovedComments.applyTo(it, result) }
                            }
                            return@recover
                        }
                    }
                    addLateRecoveries(recovered.filter { (id, result) ->
                        byId[id]?.let { RecoverRemovedComments.changes(it, result) } == true
                    })
                }
            } finally {
                if (generation == recoveryGeneration) {
                    recoveryInFlightIds.removeAll(ids.toSet())
                    if (!synchronized(lock) { beforeRender }) {
                        // The menu offers what this pass did not get to; let it look again.
                        lateRecoveryLiveData.value = Unit
                    }
                }
            }
        }

        withTimeoutOrNull(RecoverRemovedComments.RENDER_WAIT_MILLIS) { pass.join() }
        synchronized(lock) { beforeRender = false }
    }

    private fun addLateRecoveries(recovered: Map<String, FetchRemovedComment.Result>) {
        if (recovered.isEmpty()) {
            return
        }
        lateRecoveries.putAll(recovered)
        lateRecoveryLiveData.value = Unit
    }

    /**
     * Forgets every automatic pass and its results, for a comment list that is about to be replaced
     * with Reddit's placeholders again.
     */
    private fun resetCommentRecovery() {
        recoveryGeneration++
        backgroundRecovery.cancelChildren()
        recoveryAttemptedIds.clear()
        recoveryInFlightIds.clear()
        lateRecoveries.clear()
        releasedLateRecoveryIds.clear()
    }

    /**
     * The ids whose archived body arrived after their row was drawn and has not been let through
     * yet — each one on screen when last looked at. Copied, for the caller to whittle down.
     */
    fun getHeldLateRecoveryIds(): MutableSet<String> {
        val held = HashSet(lateRecoveries.keys)
        held.removeAll(releasedLateRecoveryIds)
        return held
    }

    /**
     * Lets the archived bodies for [ids] through to [applyLateRecovery]. Only for comments whose
     * rows are off screen: the next time one is drawn, it is drawn recovered.
     */
    fun releaseLateRecoveries(ids: Collection<String>) {
        releasedLateRecoveryIds.addAll(ids)
    }

    /**
     * Writes a late archived body onto [comment] as its row is bound — in place, because a bind is
     * the one moment nobody is looking at the row, and a copy would make the list see a change and
     * bind it again. Does nothing for a comment still held back, or one already recovered.
     */
    fun applyLateRecovery(comment: Comment) {
        val id = comment.id ?: return
        if (releasedLateRecoveryIds.contains(id)) {
            lateRecoveries[id]?.let { RecoverRemovedComments.applyTo(comment, it) }
        }
    }

    /**
     * The late archived bodies, for the comments cache to carry: a body still held back when the
     * user left is drawn straight in by the view the cache is restored into.
     */
    fun getLateRecoveries(): Map<String, FetchRemovedComment.Result> = HashMap(lateRecoveries)

    /**
     * A snapshot of the ids the archive has answered for, for the comments cache to carry. Copied
     * because the cache outlives this ViewModel and the live set keeps changing.
     */
    fun getRecoveryAttemptedIds(): Set<String> = HashSet(recoveryAttemptedIds)

    /**
     * Whether any loaded comment is still a removal placeholder the archive has not been asked about,
     * or one whose archived body is being held back while its row is on screen — which is exactly
     * when the explicit action has something to do.
     *
     * Ids are recorded as attempted only once the archive has answered for them, so a comment it
     * simply does not hold stops counting after the first pass; what is left are the threads
     * recovery skipped for being over the cap, and the passes the archive cut short.
     */
    fun hasUnrecoveredComments(): Boolean {
        val comments = _dataState.value.comments ?: return false
        return getHeldLateRecoveryIds().isNotEmpty()
            || RecoverRemovedComments.hasCandidateOutside(comments, recoveryAttemptedIds, recoveryInFlightIds)
    }

    /**
     * The explicit "Recover deleted comments" action, and the only path that patches comments
     * already on screen.
     *
     * Automatic recovery writes a body in only where nobody is looking ([preRecoverComments],
     * [applyLateRecovery]); when it cannot — a thread over
     * [RecoverRemovedComments.AUTO_RECOVERY_COMMENT_LIMIT], a pass the archive cut short, or a body
     * held back because its row is on screen — nothing changes behind the user's back. This is what
     * they tap instead, and [hasUnrecoveredComments] is what puts it in the menu.
     *
     * One archive request per 250 ids, and one list update per request: 150 removed comments cost
     * one call, not 150.
     */
    fun recoverRemovedComments() {
        if (commentRecoveryJob?.isActive == true) {
            // The tap already showed "Recovering…", so returning silently would leave that as the
            // last thing the user saw.
            commentRecoveryEventLiveData.value = CommentRecoveryResult(0, 0, completed = false)
            return
        }

        // Bodies already here but held back because their rows are on screen. The user asked, so
        // they are drawn now.
        val held = getHeldLateRecoveryIds()
        releaseLateRecoveries(held)
        val released = applyRecoveredComments(lateRecoveries.filterKeys { held.contains(it) })

        val comments = _dataState.value.comments ?: return
        val ids = RecoverRemovedComments.candidateIds(comments)
            .filterNot { recoveryAttemptedIds.contains(it) || recoveryInFlightIds.contains(it) }
        if (ids.isEmpty()) {
            commentRecoveryEventLiveData.value = CommentRecoveryResult(released, released, completed = true)
            return
        }

        commentRecoveryJob = viewModelScope.launch {
            var recovered = released
            val completed = RecoverRemovedComments.recover(arcticShiftRetrofit, ids) { requested, results ->
                recoveryAttemptedIds.addAll(requested)
                recovered += applyRecoveredComments(results)
            }

            commentRecoveryEventLiveData.value =
                CommentRecoveryResult(recovered, ids.size + released, completed)
            commentRecoveryJob = null
            // No automatic re-sweep for comments that arrived mid-pass: they stay unrecovered, which
            // keeps the menu item showing, so the escape hatch advertises itself rather than the app
            // redrawing the list again unasked.
        }
    }

    /**
     * Splices recovered bodies into the loaded comments, matching on id rather than position — a
     * reply or a "load more" can have shifted every index while the request was in flight. One list
     * emission for the whole batch, never one per comment.
     *
     * @return how many comments were actually updated.
     */
    private fun applyRecoveredComments(recovered: Map<String, FetchRemovedComment.Result>): Int {
        if (recovered.isEmpty()) {
            return 0
        }
        val comments = _dataState.value.comments ?: return 0

        val updatedComments = ArrayList(comments)
        val replacements = HashMap<Comment, Comment>()
        for (i in updatedComments.indices) {
            val existing = updatedComments[i]
            val result = existing.id?.let { recovered[it] } ?: continue

            // Copied rather than written in place: this list is already on screen, and DiffUtil only
            // rebinds a row when the instance it compares against has changed.
            val updatedComment = Comment(existing)
            if (!RecoverRemovedComments.applyTo(updatedComment, result)) {
                continue
            }
            updatedComments[i] = updatedComment
            replacements[existing] = updatedComment
        }

        if (replacements.isEmpty()) {
            return 0
        }

        // A reply lives in two places at once: this list, and its parent's children. Swapping only
        // the list entry leaves the tree holding the un-recovered original, which collapsing the
        // thread and expanding it again puts straight back on screen — recovered body and marker
        // both gone. Comment has identity equality, so the map keys are the instances themselves.
        for (comment in updatedComments) {
            val children = comment.children ?: continue
            for (j in children.indices) {
                replacements[children[j]]?.let { children[j] = it }
            }
        }

        _dataState.value = _dataState.value.copy(
            comments = updatedComments
        )
        return replacements.size
    }

    fun editComment(commentContentMarkdown: String?, position: Int) {
        _dataState.value.comments?.let {
            if (position < it.size && position >= 0) {
                val updatedComments = ArrayList(it)
                val updatedComment = Comment(it[position])
                updatedComment.commentMarkdown = commentContentMarkdown

                updatedComments[position] = updatedComment

                _dataState.value = _dataState.value.copy(
                    comments = updatedComments
                )
            }
        }
    }

    fun deleteComment(fullName: String, position: Int) {
        viewModelScope.launch {
            _dataState.value.comments?.let {
                if (position in it.indices && accessToken != null) {
                    var correctPosition = position
                    val comment = if (it[position].fullName == fullName) it[position] else {
                        correctPosition = findCommentPosition(fullName, position)
                        if (correctPosition in it.indices) {
                            it[correctPosition]
                        } else {
                            null
                        }
                    }
                    comment?.let { comment ->
                        if (deleteThing(oauthRetrofit, comment.fullName, accessToken)) {
                            val updatedComments = ArrayList(it)
                            if (comment.hasReply()) {
                                val updatedComment = Comment(comment)
                                updatedComment.author = "[deleted]"
                                updatedComment.commentMarkdown = "[deleted]"

                                updatedComments[correctPosition] = updatedComment
                            } else {
                                updatedComments.removeAt(correctPosition)
                            }

                            _dataState.value = _dataState.value.copy(
                                comments = updatedComments
                            )

                            commentModerationEventLiveData.postValue(
                                CommentModerationEvent.Deleted(
                                    comment, correctPosition
                                )
                            )
                        } else {
                            commentModerationEventLiveData.postValue(
                                CommentModerationEvent.DeleteFailed(
                                    comment, correctPosition
                                )
                            )
                        }
                    } ?: run {
                        commentModerationEventLiveData.postValue(
                            CommentModerationEvent.DeleteFailed(
                                null, position
                            )
                        )
                    }
                } else {
                    commentModerationEventLiveData.postValue(
                        CommentModerationEvent.DeleteFailed(
                            null, position
                        )
                    )
                }
            } ?: run {
                commentModerationEventLiveData.postValue(
                    CommentModerationEvent.DeleteFailed(
                        null, position
                    )
                )
            }
        }
    }

    fun getNextParentCommentPosition(currentPosition: Int): Int {
        _dataState.value.comments?.let {
            if (!it.isEmpty()) {
                for (i in currentPosition + 1..<it.size) {
                    if (it[i].depth == 0) {
                        return i
                    }
                }
            }
        }

        return -1
    }

    fun getPreviousParentCommentPosition(currentPosition: Int): Int {
        _dataState.value.comments?.let {
            if (!it.isEmpty()) {
                // The caller counts in the adapter's list, which can briefly run ahead of this one.
                for (i in minOf(currentPosition, it.size) - 1 downTo 0) {
                    if (it[i].depth == 0) {
                        return i
                    }
                }
            }
        }

        return -1
    }

    fun getParentCommentPosition(currentPosition: Int, currentDepth: Int): Int {
        _dataState.value.comments?.let {
            if (!it.isEmpty()) {
                for (i in currentPosition - 1 downTo 0) {
                    if (it[i].depth == currentDepth - 1) {
                        return i
                    }
                }
            }
        }

        return -1
    }

    /**
     * Index of the next comment matching [query], or -1 when the thread holds no match at all.
     *
     * Wraps like a browser's find bar: running off the end resumes at the other one, so the last
     * match's "next" is the first, and the first match's "previous" is the last. That also gives
     * the very first press something to find in either direction — with nothing highlighted yet
     * ([currentSearchedPosition] of -1) the backwards pass used to scan an empty range and report
     * no match, so the up arrow did nothing until the down arrow had been used.
     *
     * The wrapped pass re-visits [currentSearchedPosition] itself, so a query with a single match
     * stays on it rather than reporting the thread has none.
     */
    fun getNextSearchedPosition(query: String, currentSearchedPosition: Int, searchNextComment: Boolean): Int {
        _dataState.value.comments?.let {
            if (!it.isEmpty()) {
                // Clamped because a refresh can shrink the list under a stale highlight.
                val from = currentSearchedPosition.coerceIn(-1, it.size - 1)
                // Locale.ROOT: a Turkish/Azeri device lower-cases "I" to dotless "i", so searching
                // a thread for a word containing an I would find nothing. This folds comment text
                // for matching, never for display.
                val loweredQuery = query.lowercase(Locale.ROOT)
                fun matches(i: Int) =
                    it[i].commentRawText?.lowercase(Locale.ROOT)?.contains(loweredQuery) == true

                if (searchNextComment) {
                    for (i in from + 1..<it.size) {
                        if (matches(i)) return i
                    }
                    for (i in 0..from) {
                        if (matches(i)) return i
                    }
                } else {
                    for (i in from - 1 downTo 0) {
                        if (matches(i)) return i
                    }
                    for (i in it.size - 1 downTo from.coerceAtLeast(0)) {
                        if (matches(i)) return i
                    }
                }
            }
        }

        return -1
    }

    fun checkIfNotifyOldSearchedPositionNeeded(searchedPosition: Int): Boolean {
        _dataState.value.comments?.let {
            return searchedPosition in it.indices
        }

        return false
    }

    fun approvePost(post: Post, position: Int) {
        viewModelScope.launch {
            val params: MutableMap<String, String> = HashMap()
            params[APIUtils.ID_KEY] = post.fullName
            try {
                val response = oauthRetrofit.create(RedditAPIKt::class.java)
                    .approveThing(APIUtils.getOAuthHeader(accessToken), params)

                if (response.isSuccessful) {
                    post.isApproved = true
                    post.approvedBy = accountName
                    post.approvedAtUTC = System.currentTimeMillis()
                    post.setRemoved(false, false)

                    setPost(post)

                    postModerationEventLiveData.postValue(PostModerationEvent.Approved(post, position))
                } else {
                    postModerationEventLiveData.postValue(PostModerationEvent.ApproveFailed(post, position))
                }
            } catch (e: Exception) {
                e.printStackTrace()

                postModerationEventLiveData.postValue(PostModerationEvent.ApproveFailed(post, position))
            }
        }
    }

    fun removePost(post: Post, position: Int, isSpam: Boolean) {
        viewModelScope.launch {
            val params: MutableMap<String, String> = HashMap()
            params[APIUtils.ID_KEY] = post.fullName
            params[APIUtils.SPAM_KEY] = isSpam.toString()
            try {
                val response = oauthRetrofit.create(RedditAPIKt::class.java)
                    .removeThing(APIUtils.getOAuthHeader(accessToken), params)

                if (response.isSuccessful) {
                    post.isApproved = false
                    post.approvedBy = null
                    post.approvedAtUTC = 0
                    post.setRemoved(true, isSpam)

                    setPost(post)

                    postModerationEventLiveData.postValue(
                        if (isSpam) PostModerationEvent.MarkedAsSpam(
                            post,
                            position
                        ) else PostModerationEvent.Removed(post, position)
                    )
                } else {
                    postModerationEventLiveData.postValue(
                        if (isSpam) PostModerationEvent.MarkAsSpamFailed(
                            post,
                            position
                        ) else PostModerationEvent.RemoveFailed(post, position)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()

                postModerationEventLiveData.postValue(
                    if (isSpam) PostModerationEvent.MarkAsSpamFailed(
                        post,
                        position
                    ) else PostModerationEvent.RemoveFailed(post, position)
                )
            }
        }
    }

    fun toggleSticky(post: Post, position: Int) {
        viewModelScope.launch {
            val params: MutableMap<String, String> = HashMap()
            params[APIUtils.ID_KEY] = post.fullName
            params[APIUtils.STATE_KEY] = (!post.isStickied).toString()
            params[APIUtils.API_TYPE_KEY] = APIUtils.API_TYPE_JSON
            try {
                val response = oauthRetrofit.create(RedditAPIKt::class.java)
                    .toggleStickyPost(APIUtils.getOAuthHeader(accessToken), params)

                if (response.isSuccessful) {
                    post.setIsStickied(!post.isStickied)

                    setPost(post)

                    postModerationEventLiveData.postValue(
                        if (post.isStickied) PostModerationEvent.SetStickyPost(
                            post,
                            position
                        ) else PostModerationEvent.UnsetStickyPost(post, position)
                    )
                } else {
                    postModerationEventLiveData.postValue(
                        if (post.isStickied) PostModerationEvent.UnsetStickyPostFailed(
                            post,
                            position
                        ) else PostModerationEvent.SetStickyPostFailed(post, position)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                postModerationEventLiveData.postValue(
                    if (post.isStickied) PostModerationEvent.UnsetStickyPostFailed(
                        post,
                        position
                    ) else PostModerationEvent.SetStickyPostFailed(post, position)
                )
            }
        }
    }

    fun toggleLock(post: Post, position: Int) {
        viewModelScope.launch {
            val params: MutableMap<String, String> = HashMap()
            params[APIUtils.ID_KEY] = post.fullName
            try {
                val response = if (post.isLocked) oauthRetrofit.create(
                    RedditAPIKt::class.java
                ).unLockThing(APIUtils.getOAuthHeader(accessToken), params) else oauthRetrofit.create(
                    RedditAPIKt::class.java
                ).lockThing(APIUtils.getOAuthHeader(accessToken), params)

                if (response.isSuccessful) {
                    post.setIsLocked(!post.isLocked)

                    setPost(post)

                    postModerationEventLiveData.postValue(
                        if (post.isLocked) PostModerationEvent.Locked(
                            post,
                            position
                        ) else PostModerationEvent.Unlocked(post, position)
                    )
                } else {
                    postModerationEventLiveData.postValue(
                        if (post.isLocked) PostModerationEvent.UnlockFailed(
                            post,
                            position
                        ) else PostModerationEvent.LockFailed(post, position)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                postModerationEventLiveData.postValue(
                    if (post.isLocked) PostModerationEvent.UnlockFailed(
                        post,
                        position
                    ) else PostModerationEvent.LockFailed(post, position)
                )
            }
        }
    }

    fun toggleNSFW(position: Int) {
        viewModelScope.launch {
            _dataState.value.post?.let { post ->
                val params: MutableMap<String, String> = HashMap()
                params[APIUtils.ID_KEY] = post.fullName
                try {
                    val response = if (post.isNSFW) oauthRetrofit.create(
                        RedditAPIKt::class.java
                    ).unmarkNSFW(APIUtils.getOAuthHeader(accessToken), params) else oauthRetrofit.create(
                        RedditAPIKt::class.java
                    ).markNSFW(APIUtils.getOAuthHeader(accessToken), params)

                    if (response.isSuccessful) {
                        val newPost = Post(post)
                        newPost.isNSFW = !post.isNSFW

                        setPost(newPost)

                        postModerationEventLiveData.postValue(
                            if (post.isNSFW) PostModerationEvent.UnmarkedNSFW(
                                post,
                                position
                            ) else PostModerationEvent.MarkedNSFW(post, position)
                        )
                    } else {
                        postModerationEventLiveData.postValue(
                            if (post.isNSFW) PostModerationEvent.MarkNSFWFailed(
                                post,
                                position
                            ) else PostModerationEvent.UnmarkNSFWFailed(post, position)
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    postModerationEventLiveData.postValue(
                        if (post.isNSFW) PostModerationEvent.MarkNSFWFailed(
                            post,
                            position
                        ) else PostModerationEvent.UnmarkNSFWFailed(post, position)
                    )
                }
            }
        }
    }

    fun toggleSpoiler(position: Int) {
        viewModelScope.launch {
            _dataState.value.post?.let { post ->
                val params: MutableMap<String, String> = HashMap()
                params[APIUtils.ID_KEY] = post.fullName
                try {
                    val response = if (post.isSpoiler) oauthRetrofit.create(
                        RedditAPIKt::class.java
                    ).unmarkSpoiler(
                        APIUtils.getOAuthHeader(accessToken),
                        params
                    ) else oauthRetrofit.create(
                        RedditAPIKt::class.java
                    ).markSpoiler(APIUtils.getOAuthHeader(accessToken), params)

                    if (response.isSuccessful) {
                        val newPost = Post(post)
                        newPost.isSpoiler = !post.isSpoiler

                        setPost(newPost)

                        postModerationEventLiveData.postValue(
                            if (post.isSpoiler) PostModerationEvent.UnmarkedSpoiler(
                                post,
                                position
                            ) else PostModerationEvent.MarkedSpoiler(post, position)
                        )
                    } else {
                        postModerationEventLiveData.postValue(
                            if (post.isSpoiler) PostModerationEvent.MarkSpoilerFailed(
                                post,
                                position
                            ) else PostModerationEvent.UnmarkSpoilerFailed(post, position)
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    postModerationEventLiveData.postValue(
                        if (post.isSpoiler) PostModerationEvent.MarkSpoilerFailed(
                            post,
                            position
                        ) else PostModerationEvent.UnmarkSpoilerFailed(post, position)
                    )
                }
            }
        }
    }

    fun changeFlair(flair: Flair, position: Int) {
        viewModelScope.launch {
            _dataState.value.post?.let { post ->
                val params: MutableMap<String, String> = mutableMapOf()
                params[APIUtils.API_TYPE_KEY] = APIUtils.API_TYPE_JSON
                params[APIUtils.FLAIR_TEMPLATE_ID_KEY] = flair.id
                params[APIUtils.LINK_KEY] = post.fullName
                params[APIUtils.TEXT_KEY] = flair.text

                try {
                    val response = oauthRetrofit.create(RedditAPIKt::class.java).selectFlair(
                        post.subredditNamePrefixed,
                        APIUtils.getOAuthHeader(accessToken), params
                    )
                    if (response.isSuccessful) {
                        refresh(fetchPost = true, fetchComments = false)
                        postModerationEventLiveData.postValue(
                            PostModerationEvent.ChangedFlair(
                                post,
                                position
                            )
                        )
                    } else {
                        postModerationEventLiveData.postValue(
                            PostModerationEvent.ChangeFlairFailed(
                                post,
                                position
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    postModerationEventLiveData.postValue(
                        PostModerationEvent.ChangeFlairFailed(
                            post,
                            position
                        )
                    )
                }
            }
        }
    }

    fun toggleMod(post: Post, position: Int) {
        viewModelScope.launch {
            val params: MutableMap<String, String> = HashMap()
            params[APIUtils.ID_KEY] = post.fullName
            params[APIUtils.HOW_KEY] = if (post.isModerator) APIUtils.HOW_NO else APIUtils.HOW_YES
            try {
                val response = oauthRetrofit.create(RedditAPIKt::class.java)
                    .toggleDistinguishedThing(APIUtils.getOAuthHeader(accessToken), params)

                if (response.isSuccessful) {
                    post.setIsModerator(!post.isModerator)

                    setPost(post)

                    postModerationEventLiveData.postValue(
                        if (post.isModerator) PostModerationEvent.DistinguishedAsMod(
                            post,
                            position
                        ) else PostModerationEvent.UndistinguishedAsMod(post, position)
                    )
                } else {
                    postModerationEventLiveData.postValue(
                        if (post.isModerator) PostModerationEvent.UndistinguishAsModFailed(
                            post,
                            position
                        ) else PostModerationEvent.DistinguishAsModFailed(post, position)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                postModerationEventLiveData.postValue(
                    if (post.isModerator) PostModerationEvent.UndistinguishAsModFailed(
                        post,
                        position
                    ) else PostModerationEvent.DistinguishAsModFailed(post, position)
                )
            }
        }
    }

    fun toggleNotification(post: Post, position: Int) {
        viewModelScope.launch {
            val params: MutableMap<String, String> = HashMap()
            params[APIUtils.ID_KEY] = post.fullName
            params[APIUtils.STATE_KEY] = (!post.isSendReplies).toString()
            try {
                val response = oauthRetrofit.create(RedditAPIKt::class.java)
                    .toggleRepliesNotification(APIUtils.getOAuthHeader(accessToken), params)

                if (response.isSuccessful) {
                    post.isSendReplies = !post.isSendReplies

                    setPost(post)

                    postModerationEventLiveData.postValue(
                        if (post.isSendReplies) PostModerationEvent.SetReceiveNotification(
                            post,
                            position
                        ) else PostModerationEvent.UnsetReceiveNotification(post, position)
                    )
                } else {
                    postModerationEventLiveData.postValue(
                        if (post.isSendReplies) PostModerationEvent.UnsetReceiveNotificationFailed(
                            post,
                            position
                        ) else PostModerationEvent.SetReceiveNotificationFailed(post, position)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                postModerationEventLiveData.postValue(
                    if (post.isSendReplies) PostModerationEvent.UnsetReceiveNotificationFailed(
                        post,
                        position
                    ) else PostModerationEvent.SetReceiveNotificationFailed(post, position)
                )
            }
        }
    }

    fun approveComment(comment: Comment, position: Int) {
        viewModelScope.launch {
            val params: MutableMap<String, String> = HashMap()
            params[APIUtils.ID_KEY] = comment.fullName
            try {
                val response = oauthRetrofit.create(RedditAPIKt::class.java)
                    .approveThing(APIUtils.getOAuthHeader(accessToken), params)

                if (response.isSuccessful) {
                    comment.isApproved = true
                    comment.approvedBy = accountName
                    comment.approvedAtUTC = System.currentTimeMillis()
                    comment.setRemoved(false, false)

                    updateModdedStatus(comment, position)

                    commentModerationEventLiveData.postValue(CommentModerationEvent.Approved(comment, position))
                } else {
                    commentModerationEventLiveData.postValue(CommentModerationEvent.ApproveFailed(comment, position))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                commentModerationEventLiveData.postValue(CommentModerationEvent.ApproveFailed(comment, position))
            }
        }
    }

    fun removeComment(comment: Comment, position: Int, isSpam: Boolean) {
        viewModelScope.launch {
            val params: MutableMap<String, String> = HashMap()
            params[APIUtils.ID_KEY] = comment.fullName
            params[APIUtils.SPAM_KEY] = isSpam.toString()
            try {
                val response = oauthRetrofit.create(RedditAPIKt::class.java)
                    .removeThing(APIUtils.getOAuthHeader(accessToken), params)

                if (response.isSuccessful) {
                    comment.isApproved = false
                    comment.approvedBy = null
                    comment.approvedAtUTC = 0
                    comment.setRemoved(true, isSpam)

                    updateModdedStatus(comment, position)

                    commentModerationEventLiveData.postValue(
                        if (isSpam) CommentModerationEvent.MarkedAsSpam(
                            comment,
                            position
                        ) else CommentModerationEvent.Removed(comment, position)
                    )
                } else {
                    commentModerationEventLiveData.postValue(
                        if (isSpam) CommentModerationEvent.MarkAsSpamFailed(
                            comment,
                            position
                        ) else CommentModerationEvent.RemoveFailed(comment, position)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                commentModerationEventLiveData.postValue(
                    if (isSpam) CommentModerationEvent.MarkAsSpamFailed(
                        comment,
                        position
                    ) else CommentModerationEvent.RemoveFailed(comment, position)
                )
            }
        }
    }

    fun toggleNotification(comment: Comment, position: Int) {
        viewModelScope.launch {
            val params: MutableMap<String, String> = mutableMapOf()
            params[APIUtils.ID_KEY] = comment.fullName
            params[APIUtils.STATE_KEY] = (!comment.isSendReplies).toString()
            try {
                val response = oauthRetrofit.create(RedditAPIKt::class.java)
                    .toggleRepliesNotification(APIUtils.getOAuthHeader(accessToken), params)

                if (response.isSuccessful) {
                    comment.toggleSendReplies()

                    updateModdedStatus(comment, position)

                    commentModerationEventLiveData.postValue(
                        if (comment.isSendReplies) CommentModerationEvent.SetReceiveNotification(
                            comment,
                            position
                        ) else CommentModerationEvent.UnsetReceiveNotification(comment, position)
                    )
                } else {
                    commentModerationEventLiveData.postValue(
                        if (comment.isLocked) CommentModerationEvent.UnsetReceiveNotificationFailed(
                            comment,
                            position
                        ) else CommentModerationEvent.SetReceiveNotificationFailed(comment, position)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                commentModerationEventLiveData.postValue(
                    if (comment.isLocked) CommentModerationEvent.UnsetReceiveNotificationFailed(
                        comment,
                        position
                    ) else CommentModerationEvent.SetReceiveNotificationFailed(comment, position)
                )
            }
        }
    }

    fun toggleLock(comment: Comment, position: Int) {
        viewModelScope.launch {
            val params: MutableMap<String, String> = HashMap()
            params[APIUtils.ID_KEY] = comment.fullName
            try {
                val response = if (comment.isLocked) oauthRetrofit.create(
                    RedditAPIKt::class.java
                ).unLockThing(APIUtils.getOAuthHeader(accessToken), params) else oauthRetrofit.create(
                    RedditAPIKt::class.java
                ).lockThing(APIUtils.getOAuthHeader(accessToken), params)

                if (response.isSuccessful) {
                    comment.isLocked = !comment.isLocked

                    updateModdedStatus(comment, position)

                    commentModerationEventLiveData.postValue(
                        if (comment.isLocked) CommentModerationEvent.Locked(
                            comment,
                            position
                        ) else CommentModerationEvent.Unlocked(comment, position)
                    )
                } else {
                    commentModerationEventLiveData.postValue(
                        if (comment.isLocked) CommentModerationEvent.UnlockFailed(
                            comment,
                            position
                        ) else CommentModerationEvent.LockFailed(comment, position)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                commentModerationEventLiveData.postValue(
                    if (comment.isLocked) CommentModerationEvent.UnlockFailed(
                        comment,
                        position
                    ) else CommentModerationEvent.LockFailed(comment, position)
                )
            }
        }
    }

    fun toggleMod(comment: Comment, position: Int) {
        viewModelScope.launch {
            val params: MutableMap<String, String> = HashMap()
            params[APIUtils.ID_KEY] = comment.fullName
            params[APIUtils.HOW_KEY] = if (comment.isModerator) APIUtils.HOW_NO else APIUtils.HOW_YES
            try {
                val response = oauthRetrofit.create(RedditAPIKt::class.java)
                    .toggleDistinguishedThing(APIUtils.getOAuthHeader(accessToken), params)

                if (response.isSuccessful) {
                    comment.setIsModerator(!comment.isModerator)

                    updateModdedStatus(comment, position)

                    commentModerationEventLiveData.postValue(
                        if (comment.isModerator) CommentModerationEvent.DistinguishedAsMod(
                            comment,
                            position
                        ) else CommentModerationEvent.UndistinguishedAsMod(comment, position)
                    )
                } else {
                    commentModerationEventLiveData.postValue(
                        if (comment.isModerator) CommentModerationEvent.UndistinguishAsModFailed(
                            comment,
                            position
                        ) else CommentModerationEvent.DistinguishAsModFailed(comment, position)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                commentModerationEventLiveData.postValue(
                    if (comment.isModerator) CommentModerationEvent.UndistinguishAsModFailed(
                        comment,
                        position
                    ) else CommentModerationEvent.DistinguishAsModFailed(comment, position)
                )
            }
        }
    }

    fun updateModdedStatus(comment: Comment, position: Int) {
        _dataState.value.comments?.let { comments ->
            comments.getOrNull(position)?.let {
                val updatedComment = Comment(it)
                if (updatedComment.fullName == comment.fullName) {
                    updatedComment.isApproved = comment.isApproved
                    updatedComment.approvedAtUTC = comment.approvedAtUTC
                    updatedComment.approvedBy = comment.approvedBy
                    updatedComment.setRemoved(comment.isRemoved, comment.isSpam)
                    updatedComment.isLocked = comment.isLocked

                    val updatedComments = ArrayList(comments)
                    updatedComments[position] = updatedComment

                    _dataState.value = _dataState.value.copy(
                        comments = updatedComments
                    )
                    return
                }
            }

            val correctPosition = findCommentPosition(comment.fullName, position)
            comments.getOrNull(correctPosition)?.let {
                val updatedComment = Comment(it)
                updatedComment.isApproved = comment.isApproved
                updatedComment.approvedAtUTC = comment.approvedAtUTC
                updatedComment.approvedBy = comment.approvedBy
                updatedComment.setRemoved(comment.isRemoved, comment.isSpam)
                updatedComment.isLocked = comment.isLocked

                val updatedComments = ArrayList(comments)
                updatedComments[correctPosition] = updatedComment

                _dataState.value = _dataState.value.copy(
                    comments = updatedComments
                )
            }
        }
    }

    companion object {
        /** Reddit clamps the comment `limit` here; anything larger returns the same 499 comments. */
        private const val COMMENT_LIMIT_MAX = 500

        /** What Reddit uses when `limit` is omitted — the shared, most contended cache entry. */
        private const val COMMENT_LIMIT_DEFAULT = 200

        /** Width of the band of comment `limit` values a thread's fetches rotate through. */
        private const val COMMENT_LIMIT_STEP = 50

        /**
         * How long a removed post's first draw waits for the archive.
         *
         * Generous on purpose: this is usually the session's first archive request, so it pays DNS
         * and the TLS handshake on top of the call itself, and 2s was short enough to lose that race
         * on mobile data — leaving the post drawn as "[ Removed by moderator ]" until the reader
         * scrolled it away, the only time a late answer may be applied. The network timeouts behind
         * it are 30s, far too long to hold a screen on, so the deadline is here rather than left to
         * them.
         */
        private const val POST_RECOVERY_WAIT_MILLIS = 5_000L

        fun provideFactory(retrofit: Retrofit, oauthRetrofit: Retrofit,
                           arcticShiftRetrofit: Retrofit,
                           redditDataRoomDatabase: RedditDataRoomDatabase,
                           accessToken: String?, accountName: String,
                           post: Post?, postId: String?, commentId: String?,
                           comments: ArrayList<Comment>?, children: ArrayList<String>?,
                           sortType: SortType.Type?, sortTypeSharedPreferences: SharedPreferences,
                           postHistorySharedPreferences: SharedPreferences,
                           respectSubredditRecommendedSortType: Boolean,
                           saveCommentSort: Boolean,
                           commentDefaultSortType: SortType.Type,
                           markPostsAsRead: Boolean,
                           expandChildren: Boolean, contextNumber: String,
                           useLegacyMoreChildren: Boolean) : ViewModelProvider.Factory {
            return object: ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    return ViewPostDetailFragmentViewModelNew(
                        retrofit, oauthRetrofit, arcticShiftRetrofit, redditDataRoomDatabase, accessToken, accountName,
                        post, postId, commentId, comments, children,
                        sortType, sortTypeSharedPreferences,
                        postHistorySharedPreferences, respectSubredditRecommendedSortType,
                        saveCommentSort, commentDefaultSortType,
                        markPostsAsRead, expandChildren, contextNumber, useLegacyMoreChildren
                    ) as T
                }
            }
        }
    }
}