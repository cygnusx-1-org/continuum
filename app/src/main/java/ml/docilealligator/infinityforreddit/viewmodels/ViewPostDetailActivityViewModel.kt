package ml.docilealligator.infinityforreddit.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.account.Account
import ml.docilealligator.infinityforreddit.apis.RedditAPIKt
import ml.docilealligator.infinityforreddit.comment.Comment
import ml.docilealligator.infinityforreddit.post.LoadingMorePostsStatus
import ml.docilealligator.infinityforreddit.post.ParsePost
import ml.docilealligator.infinityforreddit.post.Post
import ml.docilealligator.infinityforreddit.post.PostType
import ml.docilealligator.infinityforreddit.postfilter.PostFilter
import ml.docilealligator.infinityforreddit.readpost.ReadPost
import ml.docilealligator.infinityforreddit.readpost.ReadPostType
import ml.docilealligator.infinityforreddit.readpost.ReadPostsListInterface
import ml.docilealligator.infinityforreddit.thing.SortType
import ml.docilealligator.infinityforreddit.user.UserProfileImagesBatchLoader
import ml.docilealligator.infinityforreddit.utils.APIUtils
import ml.docilealligator.infinityforreddit.utils.JSONUtils
import ml.docilealligator.infinityforreddit.utils.TextToSpeechHelper
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit

class ViewPostDetailActivityViewModel(
    private val retrofit: Retrofit,
    private val oauthRetrofit: Retrofit,
    private val redditDataRoomDatabase: RedditDataRoomDatabase,
    private val accessToken: String?,
    private val loader: UserProfileImagesBatchLoader
) : ViewModel() {
    var post: Post? = null

    var posts: ArrayList<Post>? = null

    // Held here (not on the activity) so Read Aloud survives configuration changes such as rotation.
    private var textToSpeechHelper: TextToSpeechHelper? = null

    fun getTextToSpeechHelper(context: Context): TextToSpeechHelper {
        return textToSpeechHelper ?: TextToSpeechHelper(context).also { textToSpeechHelper = it }
    }

    fun stopTextToSpeech() {
        textToSpeechHelper?.stop()
    }

    fun shutdownTextToSpeech() {
        textToSpeechHelper?.shutdown()
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeechHelper?.shutdown()
        textToSpeechHelper = null
    }

    /**
     * The listing cursor from the last response, rather than the last post that survived filtering.
     * A page can be fetched and kept in full, in part, or not at all -- "Media Posts Only" on a page
     * of link posts keeps none of it -- and paging from the last *kept* post would then re-request
     * the page just read and never move. Reddit's own `after` is what actually advances.
     */
    private var lastListingCursor: String? = null

    private var _loadMorePostsState = MutableStateFlow(LoadMorePostsState(LoadingMorePostsStatus.NOT_LOADING, 0))
    val loadMorePostsState = _loadMorePostsState.asLiveData()

    data class LoadMorePostsState(
        val status: Int,
        val nNewPosts: Int = 0,
        val changePage: Boolean = false
    )

    fun getPost(index: Int): Post? {
        return posts?.getOrNull(index)
    }

    fun loadAuthorImages(comments: List<Comment>, loadIconListener: UserProfileImagesBatchLoader.LoadIconListener) {
        loader.loadAuthorImagesInComments(accessToken, comments, loadIconListener)
    }

    fun fetchMorePosts(
        accessToken: String?,
        accountName: String,
        changePage: Boolean,
        postType: Int,
        subredditName: String?,
        concatenatedSubredditNames: String?,
        username: String?,
        userWhere: String?,
        multiPath: String?,
        query: String?,
        sortType: SortType.Type?,
        sortTime: SortType.Time?,
        postFilter: PostFilter?,
        @ReadPostType readPostType: Int,
        readPostsList: ReadPostsListInterface?,
        mediaOnly: Boolean
    ) {
        viewModelScope.launch {
            if (_loadMorePostsState.value.status == LoadingMorePostsStatus.LOADING
                || _loadMorePostsState.value.status == LoadingMorePostsStatus.NO_MORE_POSTS) {
                return@launch
            }

            // The duplicates ("Other Discussions") listing is loaded up front by PostPagingSource and
            // there is no duplicates endpoint on this swipe-detail "load more" path. Mark the list as
            // complete instead of falling through to the home Best feed (which would append unrelated
            // posts). The guard above then stops further fetches.
            if (postType == PostType.DUPLICATES) {
                _loadMorePostsState.value = LoadMorePostsState(LoadingMorePostsStatus.NO_MORE_POSTS)
                return@launch
            }

            _loadMorePostsState.value = LoadMorePostsState(LoadingMorePostsStatus.LOADING)

            if (postType != PostType.READ_POSTS) {
                // Read posts are the one listing with no sort of its own — HistoryPostFragment sends
                // none, and the branch below never asks for one, so a non-null parameter here threw
                // on entry for a call that would not have used it. Every other listing puts the sort
                // in the request path, so without one there is no request to make.
                if (sortType == null) {
                    _loadMorePostsState.value = LoadMorePostsState(LoadingMorePostsStatus.NO_MORE_POSTS)
                    return@launch
                }

                try {
                    val api: RedditAPIKt =
                        (if (accountName == Account.ANONYMOUS_ACCOUNT) retrofit else oauthRetrofit).create(
                            RedditAPIKt::class.java
                        )
                    // A page every post of which is filtered out adds nothing, and stopping there
                    // would end the swipe list for good on the first run of link posts even though
                    // the listing goes on. Keep asking, bounded, exactly as PostPagingSource does
                    // for the feed itself (issue #377).
                    var barrenPages = 0
                    while (true) {
                        val response: Response<String>?
                        val afterKey = lastListingCursor ?: posts?.let {
                            it.lastOrNull()?.fullName
                        }
                        when (postType) {
                            PostType.SUBREDDIT -> response = subredditName?.let {
                                if (accountName == Account.ANONYMOUS_ACCOUNT) {
                                    api.getSubredditBestPosts(
                                        subredditName, sortType, sortTime, afterKey,
                                        APIUtils.subredditAPICallLimit(subredditName)
                                    )
                                } else {
                                    api.getSubredditBestPostsOauth(
                                        subredditName, sortType,
                                        sortTime, afterKey, APIUtils.subredditAPICallLimit(subredditName),
                                        APIUtils.getOAuthHeader(accessToken)
                                    )
                                }
                            }

                            PostType.USER -> response = username?.let {
                                if (accountName == Account.ANONYMOUS_ACCOUNT) {
                                    api.getUserPosts(username, afterKey, sortType, sortTime)
                                } else {
                                    userWhere?.let {
                                        api.getUserPostsOauth(
                                            username, userWhere, afterKey, sortType,
                                            sortTime, APIUtils.getOAuthHeader(accessToken)
                                        )
                                    }
                                }
                            }

                            PostType.SEARCH -> response = if (subredditName == null) {
                                if (accountName == Account.ANONYMOUS_ACCOUNT) {
                                    api.searchPosts(
                                        query, afterKey, sortType, sortTime
                                    )
                                } else {
                                    api.searchPostsOauth(
                                        query, afterKey, sortType,
                                        sortTime, APIUtils.getOAuthHeader(accessToken)
                                    )
                                }
                            } else {
                                if (accountName == Account.ANONYMOUS_ACCOUNT) {
                                    api.searchPostsInSpecificSubreddit(
                                        subredditName, query,
                                        sortType, sortTime, afterKey
                                    )
                                } else {
                                    api.searchPostsInSpecificSubredditOauth(
                                        subredditName, query,
                                        sortType, sortTime, afterKey,
                                        APIUtils.getOAuthHeader(accessToken)
                                    )
                                }
                            }

                            PostType.MULTIREDDIT -> response = multiPath?.let {
                                if (accountName == Account.ANONYMOUS_ACCOUNT) {
                                    api.getMultiRedditPosts(multiPath, afterKey, sortTime)
                                } else {
                                    api.getMultiRedditPostsOauth(
                                        multiPath, afterKey,
                                        sortTime, APIUtils.getOAuthHeader(accessToken)
                                    )
                                }
                            }

                            PostType.ANONYMOUS_FRONT_PAGE, PostType.ANONYMOUS_MULTIREDDIT -> response = concatenatedSubredditNames?.let {
                                api.getAnonymousFrontPageOrMultiredditPosts(
                                    concatenatedSubredditNames, sortType,
                                    sortTime, afterKey, APIUtils.subredditAPICallLimit(subredditName),
                                    APIUtils.ANONYMOUS_USER_AGENT
                                )
                            }

                            else -> response = api.getBestPosts(
                                sortType, sortTime, afterKey,
                                APIUtils.getOAuthHeader(accessToken)
                            )
                        }

                        if (response?.isSuccessful != true) {
                            _loadMorePostsState.value =
                                LoadMorePostsState(LoadingMorePostsStatus.FAILED)
                            return@launch
                        }

                        val cursorBeforePage = lastListingCursor
                        val addedPosts =
                            finalizePosts(response, postFilter, mediaOnly, readPostsList, changePage)
                        barrenPages++
                        // Stop on anything gained, on the end of the listing, on a cursor that did
                        // not move (asking again would re-read the same page), and on the cap. The
                        // state finalizePosts already set is the right one in every case: LOADED
                        // when something was added, NO_MORE_POSTS when we gave up or ran out.
                        if (addedPosts
                            || lastListingCursor == null
                            || lastListingCursor == cursorBeforePage
                            || barrenPages >= MAX_BARREN_SWIPE_PAGES
                        ) {
                            break
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    _loadMorePostsState.value = LoadMorePostsState(LoadingMorePostsStatus.FAILED)
                }
            } else {
                val lastItem: Long = posts?.let {
                    if (!it.isEmpty()) {
                        redditDataRoomDatabase.readPostDaoKt()
                            .getReadPost(it.lastOrNull()?.id ?: "")?.time
                    } else {
                        0
                    }
                } ?: 0
                val readPosts: MutableList<ReadPost> = redditDataRoomDatabase.readPostDaoKt()
                    .getAllReadPosts(accountName, lastItem, readPostType)
                val ids = StringBuilder()
                for (readPost in readPosts) {
                    ids.append("t3_").append(readPost.id).append(",")
                }
                if (ids.isNotEmpty()) {
                    ids.deleteCharAt(ids.length - 1)
                }

                try {
                    val response = if (accountName == Account.ANONYMOUS_ACCOUNT) {
                        oauthRetrofit.create(RedditAPIKt::class.java)
                            .getInfoOauth(ids.toString(), APIUtils.getOAuthHeader(accessToken))
                    } else {
                        retrofit.create(RedditAPIKt::class.java).getInfo(ids.toString())
                    }

                    if (response.isSuccessful) {
                        finalizePosts(response, postFilter, mediaOnly, readPostsList, changePage)
                    } else {
                        _loadMorePostsState.value = LoadMorePostsState(LoadingMorePostsStatus.FAILED)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    _loadMorePostsState.value = LoadMorePostsState(LoadingMorePostsStatus.FAILED)
                }
            }
        }
    }

    private fun parsePostsSync(
        response: String?,
        postFilter: PostFilter?,
        mediaOnly: Boolean,
        readPostsList: ReadPostsListInterface?
    ): ArrayList<Post>? {
        val newPosts = ArrayList<Post>()
        try {
            val jsonResponse = JSONObject(response ?: "")
            val allPostsData =
                jsonResponse.getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.CHILDREN_KEY)

            val numberOfPosts = allPostsData.length()

            val newPostsIds = java.util.ArrayList<String>()
            for (i in 0..<numberOfPosts) {
                try {
                    if (allPostsData.getJSONObject(i).getString(JSONUtils.KIND_KEY) != "t3") {
                        continue
                    }
                    val data = allPostsData.getJSONObject(i).getJSONObject(JSONUtils.DATA_KEY)
                    val post = ParsePost.parseBasicData(data)
                    // mediaOnly is the gallery feed's "Media Posts Only" setting, carried over so
                    // swiping past the posts the feed handed us does not start turning up the text
                    // and link posts it was hiding (issue #377).
                    if (PostFilter.isPostAllowed(post, postFilter) && (!mediaOnly || post.isMediaPost)) {
                        newPosts.add(post)
                        newPostsIds.add(post.id)
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }

            if (readPostsList != null) {
                val readPostsIds = readPostsList.getReadPostsIdsByIds(newPostsIds)
                for (post in newPosts) {
                    if (readPostsIds.contains(post.id)) {
                        post.markAsRead()
                    }
                }
            }

            lastListingCursor = ParsePost.getLastItem(jsonResponse)

            return newPosts
        } catch (e: JSONException) {
            e.printStackTrace()
            return null
        }
    }

    /** Returns whether this page actually added anything to the swipe list. */
    private suspend fun finalizePosts(
        response: Response<String>,
        postFilter: PostFilter?,
        mediaOnly: Boolean,
        readPostsList: ReadPostsListInterface?,
        changePage: Boolean
    ): Boolean {
        val newPosts = withContext(Dispatchers.Default) {
            parsePostsSync(response.body(), postFilter, mediaOnly, readPostsList)
        }
        if (newPosts == null) {
            _loadMorePostsState.value = LoadMorePostsState(LoadingMorePostsStatus.NO_MORE_POSTS)
            return false
        } else {
            posts?.let { posts ->
                val currentPostsSize = posts.size
                val existingPostIds = mutableSetOf<String>()
                for (p in newPosts) {
                    if (existingPostIds.contains(p.id)) {
                        continue
                    }

                    existingPostIds.add(p.id)
                    posts.add(p)
                }
                if (currentPostsSize == posts.size) {
                    _loadMorePostsState.value = LoadMorePostsState(LoadingMorePostsStatus.NO_MORE_POSTS)
                    return false
                } else {
                    _loadMorePostsState.value = LoadMorePostsState(
                        LoadingMorePostsStatus.LOADED,
                        posts.size - currentPostsSize,
                        changePage
                    )
                    return true
                }
            } ?: run {
                posts = newPosts
                _loadMorePostsState.value = LoadMorePostsState(
                    LoadingMorePostsStatus.LOADED,
                    posts?.size ?: 0,
                    changePage
                )
                return newPosts.isNotEmpty()
            }
        }
    }

    companion object {
        /**
         * How many pages the swipe list may pull while every one of them is filtered away to
         * nothing, before it accepts that there is no more to show.
         */
        private const val MAX_BARREN_SWIPE_PAGES = 5

        fun provideFactory(
            retrofit: Retrofit,
            oauthRetrofit: Retrofit,
            redditDataRoomDatabase: RedditDataRoomDatabase,
            accessToken: String?,
            loader: UserProfileImagesBatchLoader
        ): ViewModelProvider.Factory {
            return object: ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    return ViewPostDetailActivityViewModel(retrofit, oauthRetrofit, redditDataRoomDatabase, accessToken, loader) as T
                }
            }
        }
    }
}