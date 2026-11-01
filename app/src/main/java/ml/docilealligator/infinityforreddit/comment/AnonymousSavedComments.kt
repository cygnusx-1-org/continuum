package ml.docilealligator.infinityforreddit.comment

import android.content.Context
import androidx.annotation.WorkerThread
import ml.docilealligator.infinityforreddit.Infinity
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Saving and unsaving comments while logged out, entirely on the device: see [AnonymousSavedComment].
 * Posts have had this all along, as `ANONYMOUS_SAVED_POSTS` rows in `read_posts`; comments went to
 * `/api/save` with no token and failed.
 */
object AnonymousSavedComments {
    /** One thread, so a save and the unsave tapped right after it land in that order. */
    private val EXECUTOR: Executor = Executors.newSingleThreadExecutor()

    /**
     * Saves [comment] if it is not saved and unsaves it if it is, and sets [Comment.isSaved] to
     * match. The write happens in the background; nothing here can fail the way a request can, so
     * the caller reports success straight away.
     *
     * @return whether the comment is saved now
     */
    @JvmStatic
    fun toggle(database: RedditDataRoomDatabase, username: String, comment: Comment): Boolean {
        val id = comment.id ?: return comment.isSaved
        val saved = !comment.isSaved
        if (saved) {
            val row = AnonymousSavedComment(username, id, comment.linkId.orEmpty(), System.currentTimeMillis())
            EXECUTOR.execute { database.anonymousSavedCommentDao().insert(row) }
        } else {
            EXECUTOR.execute { database.anonymousSavedCommentDao().delete(username, id) }
        }
        comment.isSaved = saved
        return saved
    }

    /** [toggle] for the comment adapters, which are not given the database. */
    @JvmStatic
    fun toggle(context: Context, username: String, comment: Comment): Boolean =
        toggle((context.applicationContext as Infinity).mRedditDataRoomDatabase, username, comment)

    /**
     * Marks the comments in [comments], and every reply below them, that are saved on this device.
     * Reddit reports every comment as unsaved to an anonymous request, so a fetched thread has to
     * be told.
     */
    @JvmStatic
    @WorkerThread
    fun markSaved(database: RedditDataRoomDatabase, username: String, comments: List<Comment>) {
        if (comments.isEmpty()) {
            return
        }
        val saved = database.anonymousSavedCommentDao().getAllIds(username).toHashSet()
        if (saved.isNotEmpty()) {
            markSaved(comments, saved)
        }
    }

    private fun markSaved(comments: List<Comment>, saved: Set<String>) {
        for (comment in comments) {
            val id = comment.id
            if (comment.placeholderType == Comment.NOT_PLACEHOLDER && id != null && id in saved) {
                comment.isSaved = true
            }
            // Comment guards its own list against null throughout, so this does too.
            val children: List<Comment>? = comment.children
            if (children != null) {
                markSaved(children, saved)
            }
        }
    }
}
