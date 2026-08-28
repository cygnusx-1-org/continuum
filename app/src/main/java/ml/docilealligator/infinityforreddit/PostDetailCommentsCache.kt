package ml.docilealligator.infinityforreddit

import ml.docilealligator.infinityforreddit.comment.Comment
import ml.docilealligator.infinityforreddit.commentfilter.CommentFilter
import ml.docilealligator.infinityforreddit.post.Post
import ml.docilealligator.infinityforreddit.thing.SortType

class PostDetailCommentsCache(
    val post: Post,
    val visibleComments: ArrayList<Comment>,
    val children: ArrayList<String>?,
    val sortType: SortType.Type,
    val scrollPosition: Int,
    /**
     * Comment ids the archive has already answered for, carried across with the comments so a
     * restored view does not offer to recover the ones it already knows are not there.
     */
    val recoveryAttemptedIds: Set<String>
    ) {
}