package ml.docilealligator.infinityforreddit.markdown.commentface

/**
 * What a subreddit's stylesheet turns one comment-face link into: a box of a fixed size in CSS
 * pixels, painted with part of a sprite and, when the link has text, a caption drawn over it.
 *
 * Sizes are in CSS pixels as the stylesheet wrote them; [CommentFacePlugin] draws one CSS pixel as
 * one dp, which is how a phone's browser sizes a page.
 */
data class CommentFace(
    val imageUrl: String,
    /** The padding box, which is what the background paints and positions against. */
    val boxWidth: Float,
    val boxHeight: Float,
    val paddingLeft: Float,
    val paddingTop: Float,
    val paddingRight: Float,
    val paddingBottom: Float,
    val background: CommentFaceBackground,
    val opacity: Float,
    /** Null when the stylesheet hides the link's text, which most face stylesheets do. */
    val caption: CommentFaceCaptionStyle?,
    val verticalAlign: VerticalAlign,
    /**
     * The stylesheet gives this link `display: none`: it, and any text it has, takes no space at
     * all. [HIDDEN] is the only instance with it set.
     */
    val hidden: Boolean = false,
) {
    enum class VerticalAlign { BASELINE, MIDDLE, BOTTOM }

    companion object {
        @JvmField
        val HIDDEN = CommentFace(
            "", 0f, 0f, 0f, 0f, 0f, 0f,
            CommentFaceBackground(CssLength.ZERO, CssLength.ZERO, CommentFaceBackground.SizeMode.EXPLICIT,
                null, null, repeatX = false, repeatY = false, color = null),
            0f, null, VerticalAlign.BASELINE, hidden = true
        )
    }
}

/** The `background-*` longhands, read but not yet resolved against the sprite's size. */
data class CommentFaceBackground(
    val positionX: CssLength,
    val positionY: CssLength,
    val sizeMode: SizeMode,
    /** Explicit `background-size` components; null is `auto`. */
    val sizeX: CssLength?,
    val sizeY: CssLength?,
    val repeatX: Boolean,
    val repeatY: Boolean,
    /** ARGB painted under the image, or null for none. */
    val color: Int?,
) {
    enum class SizeMode { EXPLICIT, COVER, CONTAIN }
}

/** How the link's own text is drawn over the face. */
data class CommentFaceCaptionStyle(
    val fontSize: Float,
    /** Null where the stylesheet leaves it to the link colour. */
    val color: Int?,
    /** An outline colour, from `text-shadow`; null for none, and the text colour for a shadow without one. */
    val outline: Outline?,
    val bold: Boolean,
    val align: Align,
    /** Where `**strong**` text in the link goes, which old Reddit face stylesheets pin to the bottom. */
    val strong: CommentFaceStrongStyle,
) {
    enum class Align { LEFT, CENTER, RIGHT }

    /** A null [color] means the text's own colour. */
    data class Outline(val color: Int?)
}

data class CommentFaceStrongStyle(
    val anchor: Anchor,
    /** Distance from the anchored edge of the padding box, in CSS pixels. */
    val offset: Float,
    val visible: Boolean,
    val bold: Boolean,
    val fontSize: Float,
    val color: Int?,
) {
    /** [FLOW] runs inline with the rest of the text; the others are `position: absolute`. */
    enum class Anchor { FLOW, TOP, BOTTOM }
}
