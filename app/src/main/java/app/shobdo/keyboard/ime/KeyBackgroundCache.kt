package app.shobdo.keyboard.ime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import app.shobdo.keyboard.theme.KeyDepth
import app.shobdo.keyboard.theme.KeyShape

/** The look of one key surface, without its label. */
class KeySurface(
    val fill: Int,
    val radius: Float,
    val shape: KeyShape,
    val depth: KeyDepth
)

/**
 * Pre-rendered key backgrounds.
 *
 * A rounded (or stepped) rectangle with a lip is the same picture on every frame, so it is
 * drawn once into a small Bitmap and blitted afterwards. The cache is keyed by size and
 * surface, bounded, and cleared when the theme or the density changes — nothing is created
 * in onDraw.
 */
class KeyBackgroundCache {

    private val cache = LinkedHashMap<String, Bitmap>(CACHE_SIZE, 0.75f, true)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val canvas = Canvas()
    private val path = Path()
    private val rect = RectF()
    private val key = StringBuilder(48)

    var density: Float = 2.5f

    fun clear() {
        for (bitmap in cache.values) bitmap.recycle()
        cache.clear()
    }

    fun bitmap(width: Int, height: Int, surface: KeySurface): Bitmap? {
        if (width <= 0 || height <= 0) return null
        key.setLength(0)
        key.append(width).append('x').append(height)
            .append(':').append(surface.fill)
            .append(':').append(surface.radius.toInt())
            .append(':').append(surface.shape.json)
            .append(':').append(surface.depth.json)
        val id = key.toString()
        cache[id]?.let { return it }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        canvas.setBitmap(bitmap)
        drawSurface(canvas, width.toFloat(), height.toFloat(), surface)
        canvas.setBitmap(null)

        cache[id] = bitmap
        if (cache.size > CACHE_SIZE) {
            val eldest = cache.entries.iterator()
            if (eldest.hasNext()) {
                val entry = eldest.next()
                eldest.remove()
                entry.value.recycle()
            }
        }
        return bitmap
    }

    private fun drawSurface(target: Canvas, width: Float, height: Float, surface: KeySurface) {
        val edge = when (surface.depth) {
            KeyDepth.FLAT -> 0
            KeyDepth.LIP -> darken(surface.fill, 0.86f)
            KeyDepth.BLOCK -> darken(surface.fill, 0.70f)
        }
        val edgeHeight = when (surface.depth) {
            KeyDepth.FLAT -> 0f
            KeyDepth.LIP -> 1f * density
            KeyDepth.BLOCK -> 2f * density
        }
        if (surface.depth != KeyDepth.FLAT) {
            paint.color = edge
            paint.isAntiAlias = surface.shape != KeyShape.PIXEL
            drawShape(target, width, height, surface, path)
        }
        paint.color = surface.fill
        paint.isAntiAlias = surface.shape != KeyShape.PIXEL
        val bodyHeight = (height - edgeHeight).coerceAtLeast(1f)
        drawShape(target, width, bodyHeight, surface, path)
    }

    private fun drawShape(target: Canvas, width: Float, height: Float, surface: KeySurface, scratch: Path) {
        when (surface.shape) {
            KeyShape.PIXEL -> {
                buildStepped(scratch, width, height, 2f * density)
                target.drawPath(scratch, paint)
            }
            KeyShape.ROUND -> {
                rect.set(0f, 0f, width, height)
                val radius = surface.radius.coerceAtMost(height / 2f)
                target.drawRoundRect(rect, radius, radius, paint)
            }
            KeyShape.SQUIRCLE -> {
                rect.set(0f, 0f, width, height)
                // A squircle keeps its curvature past the corner: a larger radius than a
                // round key of the same size, capped so it never becomes a pill.
                val radius = (surface.radius * 1.55f).coerceIn(0f, height * 0.42f)
                target.drawRoundRect(rect, radius, radius, paint)
            }
        }
    }

    /** Stair-stepped corners: two 2dp steps per corner, drawn without anti-aliasing. */
    private fun buildStepped(scratch: Path, width: Float, height: Float, step: Float) {
        val s = step.coerceAtMost(width / 4f).coerceAtMost(height / 4f)
        scratch.reset()
        scratch.moveTo(2 * s, 0f)
        scratch.lineTo(width - 2 * s, 0f)
        scratch.lineTo(width - 2 * s, s)
        scratch.lineTo(width - s, s)
        scratch.lineTo(width - s, 2 * s)
        scratch.lineTo(width, 2 * s)
        scratch.lineTo(width, height - 2 * s)
        scratch.lineTo(width - s, height - 2 * s)
        scratch.lineTo(width - s, height - s)
        scratch.lineTo(width - 2 * s, height - s)
        scratch.lineTo(width - 2 * s, height)
        scratch.lineTo(2 * s, height)
        scratch.lineTo(2 * s, height - s)
        scratch.lineTo(s, height - s)
        scratch.lineTo(s, height - 2 * s)
        scratch.lineTo(0f, height - 2 * s)
        scratch.lineTo(0f, 2 * s)
        scratch.lineTo(s, 2 * s)
        scratch.lineTo(s, s)
        scratch.lineTo(2 * s, s)
        scratch.close()
    }

    private fun darken(color: Int, factor: Float): Int {
        val red = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val green = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val blue = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(Color.alpha(color), red, green, blue)
    }

    companion object {
        /**
         * Every distinct key width on the widest page is cached; the cap keeps a pathological
         * layout (or a rotating screen) from pinning megabytes.
         */
        private const val CACHE_SIZE = 48
    }
}
