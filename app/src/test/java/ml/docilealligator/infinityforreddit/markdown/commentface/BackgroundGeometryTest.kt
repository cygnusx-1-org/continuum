package ml.docilealligator.infinityforreddit.markdown.commentface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where a face's sprite lands in its box, by the CSS background rules — the arithmetic
 * [CommentFaceBackgroundTransformation] paints with. Positions are in CSS pixels from the box's
 * top-left corner.
 */
class BackgroundGeometryTest {

    private fun background(
        x: CssLength = CssLength.ZERO,
        y: CssLength = CssLength.ZERO,
        sizeMode: CommentFaceBackground.SizeMode = CommentFaceBackground.SizeMode.EXPLICIT,
        sizeX: CssLength? = null,
        sizeY: CssLength? = null,
        repeat: Boolean = true,
    ) = CommentFaceBackground(x, y, sizeMode, sizeX, sizeY, repeat, repeat, null)

    @Test
    fun `an auto-sized sprite is drawn at its own size, offset by the position`() {
        // r/anime's schemingsaten: 140px into an 8839 x 154 sprite, which wraps round.
        val placement = BackgroundGeometry.place(
            background(CssLength(140f, 0f)), 129f, 121f, 8839f, 154f
        )
        assertEquals(BackgroundGeometry.Placement(8839f, 154f, 140f, 0f), placement)
    }

    @Test
    fun `a percentage position lines up the same point of image and box`() {
        // hikariactually's implied `center`: half the 38px the sprite overhangs the box.
        val placement = BackgroundGeometry.place(
            background(y = CssLength(0f, 0.5f)), 144f, 116f, 8839f, 154f
        )
        assertEquals(-19f, placement.y)
    }

    @Test
    fun `an explicit size with one auto keeps the aspect ratio`() {
        val placement = BackgroundGeometry.place(
            background(sizeX = CssLength(420f, 0f)), 20f, 20f, 840f, 560f
        )
        assertEquals(420f, placement.width)
        assertEquals(280f, placement.height)
    }

    @Test
    fun `cover fills the box and contain fits inside it`() {
        val cover = BackgroundGeometry.place(
            background(sizeMode = CommentFaceBackground.SizeMode.COVER), 100f, 50f, 200f, 200f
        )
        assertEquals(100f, cover.width)
        assertEquals(100f, cover.height)
        val contain = BackgroundGeometry.place(
            background(sizeMode = CommentFaceBackground.SizeMode.CONTAIN), 100f, 50f, 200f, 200f
        )
        assertEquals(50f, contain.width)
        assertEquals(50f, contain.height)
    }

    @Test
    fun `a repeating background covers the whole box`() {
        val bg = background(CssLength(140f, 0f))
        val placement = BackgroundGeometry.place(bg, 129f, 121f, 8839f, 154f)
        assertEquals(BackgroundGeometry.Area(0f, 0f, 129f, 121f),
            BackgroundGeometry.paintedArea(placement, bg, 129f, 121f))
    }

    @Test
    fun `a background that does not repeat covers only its one tile`() {
        val bg = background(CssLength(10f, 0f), CssLength(-5f, 0f), repeat = false)
        val placement = BackgroundGeometry.place(bg, 100f, 100f, 50f, 50f)
        assertEquals(BackgroundGeometry.Area(10f, 0f, 60f, 45f),
            BackgroundGeometry.paintedArea(placement, bg, 100f, 100f))
    }

    @Test
    fun `a background that does not repeat and misses the box paints nothing`() {
        val bg = background(CssLength(-9999f, 0f), CssLength(-9999f, 0f), repeat = false)
        val placement = BackgroundGeometry.place(bg, 16f, 11f, 400f, 300f)
        assertNull(BackgroundGeometry.paintedArea(placement, bg, 16f, 11f))
    }

    @Test
    fun `a sprite shrunk by background-size is rendered at its own resolution`() {
        val placement = BackgroundGeometry.place(
            background(sizeX = CssLength(420f, 0f), sizeY = CssLength(280f, 0f)), 20f, 20f, 840f, 560f
        )
        assertEquals(2f, BackgroundGeometry.outputScale(placement, 840f))
        val natural = BackgroundGeometry.place(background(), 20f, 20f, 840f, 560f)
        assertEquals(1f, BackgroundGeometry.outputScale(natural, 840f))
    }
}
