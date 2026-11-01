package ml.docilealligator.infinityforreddit.adapters

import androidx.recyclerview.widget.GridLayoutManagerBorders
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A grid cell's box is worked out before layout, and a cell decoded at any other size than the one
 * it is measured to would be a second decode and the pop-in left in place. So the width has to be
 * GridLayoutManager's to the pixel, remainder included.
 */
class GalleryGridCellTest {
    @Test
    fun `every column is as wide as GridLayoutManager makes it`() {
        for (spanCount in 1..4) {
            for (space in 1..1600) {
                val borders = GridLayoutManagerBorders.of(spanCount, space)
                for (span in 0 until spanCount) {
                    assertEquals("space=$space spans=$spanCount span=$span",
                        maxOf(0, borders[span + 1] - borders[span] - 24),
                        GalleryGridCell.widthPx(space, spanCount, span, 24))
                }
            }
        }
    }

    @Test
    fun `a 2x2 cell on a 1280px phone card`() {
        // Pixel 9 Pro, density 3: the card is 1280px, less the grid's 8dp end padding, two columns,
        // each cell inset by half of the 16dp decoration offset. Measured on the emulator: 604x604.
        assertEquals(604, GalleryGridCell.widthPx(1280 - 24, 2, 0, 24))
        assertEquals(604, GalleryGridCell.widthPx(1280 - 24, 2, 1, 24))
    }

    @Test
    fun `every gallery of two or more is laid out in two columns`() {
        for (size in listOf(2, 3, 4, 5, 6, 20)) {
            assertEquals("size=$size", 2, GalleryGridCell.spanCount(size))
        }
    }

    @Test
    fun `the grid shows four cells at most`() {
        assertEquals(2, GalleryGridCell.cellCount(2))
        assertEquals(3, GalleryGridCell.cellCount(3))
        assertEquals(4, GalleryGridCell.cellCount(4))
        assertEquals(4, GalleryGridCell.cellCount(6))
        assertEquals(4, GalleryGridCell.cellCount(20))
    }

    @Test
    fun `an unknown space has no box`() {
        assertEquals(0, GalleryGridCell.widthPx(0, 2, 0, 24))
        assertEquals(0, GalleryGridCell.widthPx(1256, 0, 0, 24))
        assertEquals(0, GalleryGridCell.widthPx(1256, 2, 2, 24))
    }
}
