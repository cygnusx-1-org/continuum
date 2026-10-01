package ml.docilealligator.infinityforreddit.markdown.commentface

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Where a background image lands inside a face's box, by the CSS rules for `background-size`,
 * `background-position` and `background-repeat`. Pure arithmetic, in CSS pixels.
 */
internal object BackgroundGeometry {

    /** The image drawn at [width] by [height] with its top-left corner at ([x], [y]) in the box. */
    data class Placement(val width: Float, val height: Float, val x: Float, val y: Float)

    data class Area(val left: Float, val top: Float, val right: Float, val bottom: Float)

    /** The largest number of sprite pixels drawn per CSS pixel; more detail than a phone shows. */
    private const val MAX_SCALE = 4f

    fun place(background: CommentFaceBackground, boxWidth: Float, boxHeight: Float,
              imageWidth: Float, imageHeight: Float): Placement {
        val width: Float
        val height: Float
        when (background.sizeMode) {
            CommentFaceBackground.SizeMode.COVER -> {
                val scale = max(boxWidth / imageWidth, boxHeight / imageHeight)
                width = imageWidth * scale
                height = imageHeight * scale
            }
            CommentFaceBackground.SizeMode.CONTAIN -> {
                val scale = min(boxWidth / imageWidth, boxHeight / imageHeight)
                width = imageWidth * scale
                height = imageHeight * scale
            }
            CommentFaceBackground.SizeMode.EXPLICIT -> {
                val sizeX = background.sizeX?.resolve(boxWidth)
                val sizeY = background.sizeY?.resolve(boxHeight)
                when {
                    sizeX != null && sizeY != null -> {
                        width = sizeX
                        height = sizeY
                    }
                    sizeX != null -> {
                        width = sizeX
                        height = imageHeight * sizeX / imageWidth
                    }
                    sizeY != null -> {
                        height = sizeY
                        width = imageWidth * sizeY / imageHeight
                    }
                    else -> {
                        width = imageWidth
                        height = imageHeight
                    }
                }
            }
        }
        // A percentage position lines the same point of the image up with the same point of the box.
        return Placement(
            width, height,
            background.positionX.resolve(boxWidth - width),
            background.positionY.resolve(boxHeight - height)
        )
    }

    /** The part of the box the image covers, or null if it misses the box entirely. */
    fun paintedArea(placement: Placement, background: CommentFaceBackground,
                    boxWidth: Float, boxHeight: Float): Area? {
        val left = if (background.repeatX) 0f else max(0f, placement.x)
        val right = if (background.repeatX) boxWidth else min(boxWidth, placement.x + placement.width)
        val top = if (background.repeatY) 0f else max(0f, placement.y)
        val bottom = if (background.repeatY) boxHeight else min(boxHeight, placement.y + placement.height)
        return if (right > left && bottom > top) Area(left, top, right, bottom) else null
    }

    /**
     * Sprite pixels per CSS pixel to render at: one for an image drawn at its own size, more where
     * `background-size` shrinks a high-resolution sprite, so that detail is kept for a dense screen.
     */
    fun outputScale(placement: Placement, imageWidth: Float): Float =
        if (placement.width <= 0f) 1f else (imageWidth / placement.width).coerceIn(1f, MAX_SCALE)
}

/**
 * Paints a face's box from its sprite: the stylesheet's sprite comes in whole, and this cuts out
 * and lays down exactly what the box shows, repeat and all, as a bitmap the size of the box.
 *
 * Glide caches the result under this transformation's key, so each face is cut once and the
 * multi-megabyte sprite is not kept around afterwards.
 */
internal class CommentFaceBackgroundTransformation(private val face: CommentFace) : BitmapTransformation() {

    private val key = "$ID:${face.boxWidth}x${face.boxHeight}:${face.background}"

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val imageWidth = toTransform.width.toFloat()
        val imageHeight = toTransform.height.toFloat()
        val background = face.background
        val placement = BackgroundGeometry.place(background, face.boxWidth, face.boxHeight, imageWidth, imageHeight)
        val scale = BackgroundGeometry.outputScale(placement, imageWidth)
        val width = max(1, ceil(face.boxWidth * scale).toInt())
        val height = max(1, ceil(face.boxHeight * scale).toInt())

        val result = pool.get(width, height, Bitmap.Config.ARGB_8888)
        result.eraseColor(0)
        val canvas = Canvas(result)
        canvas.scale(scale, scale)
        background.color?.let { canvas.drawColor(it) }
        val area = BackgroundGeometry.paintedArea(placement, background, face.boxWidth, face.boxHeight)
        if (area != null && placement.width > 0f && placement.height > 0f) {
            // REPEAT on both axes reproduces background-repeat; an axis that does not repeat is
            // limited to one tile by the painted area instead.
            val shader = BitmapShader(toTransform, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            val matrix = Matrix()
            matrix.setScale(placement.width / imageWidth, placement.height / imageHeight)
            matrix.postTranslate(placement.x, placement.y)
            shader.setLocalMatrix(matrix)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            paint.shader = shader
            canvas.drawRect(area.left, area.top, area.right, area.bottom, paint)
        }
        canvas.setBitmap(null)
        return result
    }

    override fun equals(other: Any?): Boolean =
        other is CommentFaceBackgroundTransformation && other.key == key

    override fun hashCode(): Int = key.hashCode()

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(key.toByteArray(Charsets.UTF_8))
    }

    private companion object {
        const val ID = "ml.docilealligator.infinityforreddit.markdown.commentface.CommentFaceBackgroundTransformation.1"
    }
}
