package app.shobdo.keyboard.ime

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import app.shobdo.keyboard.theme.KeyboardTheme
import app.shobdo.keyboard.theme.ThemeBackground
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * All of the drawing.
 *
 * Every Paint is created once and only has its colour set, so a frame allocates nothing:
 * no Paint, no Rect, no String. Font metrics go into a reused object, and the gradient
 * shader is rebuilt only when the theme or the size changes.
 */
class KeyPainter {

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadePaint = Paint()
    private val metrics = Paint.FontMetrics()

    private var shader: Shader? = null
    private var shaderKey: String = ""
    private var bitmap: Bitmap? = null
    private var bitmapKey: String = ""
    private val sourceRect = Rect()
    private val destRect = RectF()
    private val scratch = RectF()

    /** Stored so hit testing and drawing agree on where the chips are. */
    val suggestionRects: ArrayList<RectF> = ArrayList(4)

    var keyTextSize: Float = 48f
        private set

    fun setTextSize(keyHeight: Float, fontScale: Float) {
        keyTextSize = (keyHeight * 0.42f * fontScale).coerceIn(18f, keyHeight * 0.62f)
    }

    // --- background ------------------------------------------------------------------

    fun drawBackground(canvas: Canvas, theme: KeyboardTheme, width: Float, height: Float) {
        when (val background = theme.background) {
            is ThemeBackground.Solid -> {
                canvas.drawColor(background.color)
            }
            is ThemeBackground.Gradient -> {
                val key = "${theme.id}:${width.toInt()}x${height.toInt()}"
                if (shader == null || shaderKey != key) {
                    shader = buildGradient(background, width, height)
                    shaderKey = key
                }
                keyPaint.shader = shader
                keyPaint.color = Color.WHITE
                canvas.drawRect(0f, 0f, width, height, keyPaint)
                keyPaint.shader = null
            }
            is ThemeBackground.Image -> {
                canvas.drawColor(background.fallback)
                val image = imageFor(background.path, background.blur)
                if (image != null) {
                    sourceRect.set(0, 0, image.width, image.height)
                    destRect.set(0f, 0f, width, height)
                    keyPaint.shader = null
                    keyPaint.alpha = 255
                    keyPaint.isFilterBitmap = true
                    canvas.drawBitmap(image, sourceRect, destRect, keyPaint)
                    val darken = ((1f - background.brightness) * 200f).toInt().coerceIn(0, 200)
                    if (darken > 0) {
                        shadePaint.color = Color.argb(darken, 0, 0, 0)
                        canvas.drawRect(0f, 0f, width, height, shadePaint)
                    }
                }
            }
        }
    }

    private fun buildGradient(background: ThemeBackground.Gradient, width: Float, height: Float): Shader {
        val radians = Math.toRadians(background.angle.toDouble())
        val dx = cos(radians).toFloat()
        val dy = sin(radians).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val colors = background.colors.toIntArray()
        val positions = FloatArray(colors.size) { index ->
            if (colors.size == 1) 0f else index.toFloat() / (colors.size - 1)
        }
        return LinearGradient(
            cx - width / 2f * dx, cy - height / 2f * dy,
            cx + width / 2f * dx, cy + height / 2f * dy,
            colors, positions, Shader.TileMode.CLAMP
        )
    }

    /**
     * The background image, decoded at reduced resolution when blur is on: drawing a small
     * bitmap stretched over the keyboard with filtering is a cheap blur that works on every
     * supported API and allocates nothing per frame.
     */
    private fun imageFor(path: String?, blur: Float): Bitmap? {
        if (path.isNullOrBlank()) return null
        val sample = (1 + blur / 3f).toInt().coerceIn(1, 32)
        val key = "$path:$sample"
        if (bitmap != null && bitmapKey == key) return bitmap
        val file = File(path)
        if (!file.exists()) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull() ?: return null
        bitmap?.recycle()
        bitmap = decoded
        bitmapKey = key
        return decoded
    }

    // --- keys ------------------------------------------------------------------------

    fun drawKeys(
        canvas: Canvas,
        theme: KeyboardTheme,
        keys: List<PlacedKey>,
        pressedIndex: Int,
        borders: Boolean
    ) {
        val radius = theme.keyRadius * density
        strokePaint.strokeWidth = 1f * density

        textPaint.isFakeBoldText = theme.bold
        textPaint.textSize = keyTextSize

        for (index in keys.indices) {
            val placed = keys[index]
            val rect = placed.rect
            val pressed = index == pressedIndex
            if (placed.isSpecial) {
                keyPaint.color = theme.specialFill
                textPaint.color = theme.specialText
            } else {
                keyPaint.color = if (pressed) theme.keyPressed else theme.keyFill
                textPaint.color = theme.keyText
            }
            canvas.drawRoundRect(rect, radius, radius, keyPaint)
            if (borders && theme.keyStroke != 0) {
                strokePaint.color = theme.keyStroke
                canvas.drawRoundRect(rect, radius, radius, strokePaint)
            }
            drawCentred(canvas, placed.label, rect.centerX(), rect.centerY(), if (placed.isSpecial) 0.86f else 1f)
        }
    }

    private fun drawCentred(canvas: Canvas, text: String, centerX: Float, centerY: Float, scale: Float) {
        if (text.isEmpty()) return
        val previous = textPaint.textSize
        textPaint.textSize = previous * scale
        textPaint.getFontMetrics(metrics)
        val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, centerX, baseline, textPaint)
        textPaint.textSize = previous
    }

    /** Measured width of a label, used to shrink long spacebar language names. */
    fun measure(text: String): Float {
        textPaint.textSize = keyTextSize
        return textPaint.measureText(text)
    }

    // --- press preview and long-press popup ------------------------------------------

    fun drawPreview(canvas: Canvas, theme: KeyboardTheme, placed: PlacedKey, keyHeight: Float) {
        val rect = placed.rect
        scratch.set(rect.left - 4f * density, rect.top - keyHeight * 0.9f, rect.right + 4f * density, rect.top - 6f * density)
        bubblePaint.color = theme.accent
        canvas.drawRoundRect(scratch, theme.keyRadius * density, theme.keyRadius * density, bubblePaint)
        textPaint.color = if (isLight(theme.accent)) Color.BLACK else Color.WHITE
        textPaint.isFakeBoldText = false
        textPaint.textSize = keyTextSize * 1.05f
        drawCentred(canvas, placed.label, scratch.centerX(), scratch.centerY(), 1f)
    }

    fun drawPopup(
        canvas: Canvas,
        theme: KeyboardTheme,
        placed: PlacedKey,
        characters: List<String>,
        selected: Int,
        keyHeight: Float
    ) {
        if (characters.isEmpty()) return
        val cell = keyHeight * 0.96f
        val width = cell * characters.size + 8f * density
        val height = cell + 8f * density
        var left = placed.rect.centerX() - width / 2f
        left = left.coerceAtLeast(4f * density)
        val top = (placed.rect.top - height - 6f * density).coerceAtLeast(4f * density)
        scratch.set(left, top, left + width, top + height)

        bubblePaint.color = theme.dark.let { if (it) 0xFF1E2430.toInt() else 0xFFFFFFFF.toInt() }
        canvas.drawRoundRect(scratch, theme.keyRadius * density, theme.keyRadius * density, bubblePaint)
        strokePaint.color = theme.accent
        strokePaint.strokeWidth = 1.5f * density
        canvas.drawRoundRect(scratch, theme.keyRadius * density, theme.keyRadius * density, strokePaint)

        for (index in characters.indices) {
            val cellLeft = scratch.left + 4f * density + cell * index
            if (index == selected) {
                keyPaint.color = theme.accent
                val highlight = RectF(cellLeft + 2f * density, scratch.top + 4f * density, cellLeft + cell - 2f * density, scratch.bottom - 4f * density)
                canvas.drawRoundRect(highlight, theme.keyRadius * density * 0.7f, theme.keyRadius * density * 0.7f, keyPaint)
            }
            textPaint.color = if (index == selected) {
                if (isLight(theme.accent)) Color.BLACK else Color.WHITE
            } else {
                if (theme.dark) 0xFFF8FAFC.toInt() else 0xFF111827.toInt()
            }
            textPaint.textSize = keyTextSize * 0.92f
            textPaint.isFakeBoldText = false
            drawCentred(canvas, characters[index], cellLeft + cell / 2f, scratch.centerY(), 1f)
        }
        textPaint.isFakeBoldText = theme.bold
    }

    // --- suggestion bar --------------------------------------------------------------

    /** Lays out the chips and returns their rects; call when the candidates change. */
    fun layoutSuggestions(bar: RectF, candidates: List<String>, primaryIndex: Int) {
        suggestionRects.clear()
        if (bar.isEmpty || candidates.isEmpty()) return
        textPaint.textSize = keyTextSize * 0.86f
        var x = bar.left + 6f * density
        for (candidate in candidates) {
            val textWidth = textPaint.measureText(candidate)
            val width = (textWidth + 26f * density).coerceAtMost(bar.width() * 0.62f)
            val rect = RectF(x, bar.top + 5f * density, x + width, bar.bottom - 5f * density)
            suggestionRects.add(rect)
            x += width + 4f * density
            if (x > bar.right) break
        }
    }

    fun drawSuggestionBar(canvas: Canvas, theme: KeyboardTheme, bar: RectF, candidates: List<String>, primaryIndex: Int) {
        if (bar.isEmpty) return
        barPaint.color = theme.barBackground
        canvas.drawRect(bar, barPaint)
        textPaint.textSize = keyTextSize * 0.86f
        textPaint.isFakeBoldText = false
        for (index in suggestionRects.indices) {
            if (index >= candidates.size) break
            val rect = suggestionRects[index]
            if (index == primaryIndex) {
                chipPaint.color = theme.accent
                canvas.drawRoundRect(rect, 8f * density, 8f * density, chipPaint)
                textPaint.color = if (isLight(theme.accent)) Color.BLACK else Color.WHITE
            } else {
                textPaint.color = theme.barText
            }
            drawCentred(canvas, candidates[index], rect.centerX(), rect.centerY(), 1f)
        }
        textPaint.isFakeBoldText = theme.bold
    }

    /** Index of the chip at ([x], [y]) or -1. */
    fun chipAt(x: Float, y: Float): Int {
        for (index in suggestionRects.indices) {
            val rect = suggestionRects[index]
            if (rect.contains(x, y)) return index
        }
        return -1
    }

    var density: Float = 2.5f

    private fun isLight(color: Int): Boolean {
        val luminance = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
        return luminance > 160
    }
}
