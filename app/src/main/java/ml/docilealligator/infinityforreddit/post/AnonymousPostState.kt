package ml.docilealligator.infinityforreddit.post

import androidx.annotation.WorkerThread
import ml.docilealligator.infinityforreddit.readpost.ReadPostDao
import ml.docilealligator.infinityforreddit.readpost.ReadPostType

/**
 * The anonymous account's votes, hides and saves, kept on the device as `read_posts` rows. Reddit
 * knows nothing of them and serves every post to an anonymous request unvoted, unhidden and
 * unsaved, so each post fetched has to be told -- the feeds always were, but a post opened by link,
 * pulled to refresh, or swiped to in post detail was not, and showed as unsaved.
 */
object AnonymousPostState {
    @JvmStatic
    @WorkerThread
    fun apply(readPostDao: ReadPostDao, username: String, posts: Collection<Post>) {
        if (posts.isEmpty()) {
            return
        }
        val postsById = posts.associateBy { it.id }
        for (readPost in readPostDao.getAllReadPostsForMetadata(username, postsById.keys.toList())) {
            val post = postsById[readPost.id] ?: continue
            when (readPost.readPostType) {
                ReadPostType.ANONYMOUS_UPVOTED_POSTS -> post.voteType = 1
                ReadPostType.ANONYMOUS_DOWNVOTED_POSTS -> post.voteType = -1
                ReadPostType.ANONYMOUS_HIDDEN_POSTS -> post.isHidden = true
                ReadPostType.ANONYMOUS_SAVED_POSTS -> post.isSaved = true
            }
        }
    }
}
