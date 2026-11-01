package ml.docilealligator.infinityforreddit.adapters

/**
 * The shape of a gallery grid ("Show gallery media as grid"), in the feed and in post detail: how
 * many columns and cells it has, and the size of a cell, worked out before layout the way
 * `GridLayoutManager` will measure it.
 *
 * Known ahead of layout, a cell's picture can be decoded at that exact box while the card is still
 * off screen -- by the feed's preloader, and by the cell itself the moment it is bound -- so the card
 * scrolls in with every cell already drawn. Sized from the laid-out view instead, a cell could only
 * start once it was on screen.
 */
object GalleryGridCell {
    /**
     * The most cells a grid shows: two by two. A longer gallery shows its first four, the fourth
     * counting the rest ("+2").
     */
    const val MAX_CELLS = 4

    /**
     * Columns of a grid of [gallerySize] images: two for any gallery of two or more, so it is a 2x2
     * at most. One rule for the feed and post detail, which drifted apart while each kept its own.
     */
    @JvmStatic
    fun spanCount(gallerySize: Int): Int = if (gallerySize >= 2) 2 else 3

    /** Cells a grid of [gallerySize] images shows: one per image, four at most. */
    @JvmStatic
    fun cellCount(gallerySize: Int): Int = minOf(gallerySize, MAX_CELLS)

    /**
     * Width in pixels of a cell in column [spanIndex] of a [spanCount]-column grid laid across
     * [totalSpacePx], the list's width less its padding, each cell inset [insetPx] by the grid's item
     * decoration. Cells are square, so it is the height too.
     *
     * `GridLayoutManager.calculateItemBorders` hands out what is left over from an uneven division a
     * pixel at a time, so neighbouring columns can differ by one; this follows it pixel for pixel.
     * Zero when the space is not known.
     */
    @JvmStatic
    fun widthPx(totalSpacePx: Int, spanCount: Int, spanIndex: Int, insetPx: Int): Int {
        if (totalSpacePx <= 0 || spanCount <= 0 || spanIndex !in 0 until spanCount) {
            return 0
        }
        val sizePerSpan = totalSpacePx / spanCount
        val remainder = totalSpacePx % spanCount
        var additional = 0
        var size = 0
        for (i in 0..spanIndex) {
            size = sizePerSpan
            additional += remainder
            if (additional > 0 && spanCount - additional < remainder) {
                size++
                additional -= spanCount
            }
        }
        return maxOf(0, size - insetPx)
    }
}
