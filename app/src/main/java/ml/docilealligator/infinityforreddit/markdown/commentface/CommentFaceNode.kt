package ml.docilealligator.infinityforreddit.markdown.commentface

import org.commonmark.node.CustomNode

/**
 * A link that the subreddit's stylesheet draws as a comment face. Its children are the link's
 * text, which [CommentFacePlugin] draws over the face as its caption rather than as text.
 */
class CommentFaceNode(
    val face: CommentFace,
    val destination: String,
    /** The link title, `[](#face "like this")`, which old Reddit showed on hover. */
    val title: String?,
) : CustomNode() {
    override fun toStringAttributes(): String = "destination=$destination, title=$title"
}

/**
 * Turns a link into a comment face while the markdown is parsed. Bound per subreddit and per
 * place in the app by [CommentFaces.lookup]; null wherever faces are off.
 */
fun interface CommentFaceLookup {
    /**
     * The face for a link to [destination], [CommentFace.HIDDEN] if the stylesheet hides it, or
     * null to leave it an ordinary link. [hasText] is whether the link has any text of its own.
     */
    fun find(destination: String, title: String?, hasText: Boolean): CommentFace?
}
