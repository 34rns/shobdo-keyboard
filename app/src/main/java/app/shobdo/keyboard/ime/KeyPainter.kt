package app.shobdo.keyboard.ime

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import app.shobdo.keyboard.theme.FontRole
import app.shobdo.keyboard.theme.KeyShape
import app.shobdo.keyboard.theme.KeyboardTheme
import app.shobdo.keyboard.theme.ThemeBackground
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * All of the key drawing.
 *
 * Every Paint is created once with the view and only has its colour or size set, so a frame
 * allocates nothing: no Paint, no RectF, no String. Key backgrounds come from
 * [KeyBackgroundCache] and icons from [IconSet], both pre-rendered.
 */
class KeyPainter(private val icons: IconSet) {

    val box = PaintBox()
    private val surfaces = KeyBackgroundCache()
    private val path = Path()
    private val scratch = RectF()
    private val bubbleRect = RectF()

    private var shader: Shader? = null
    private var shaderKey: String = ""
    private var bitmap: Bitmap? = null
    private var bitmapKey: String = ""
    private val sourceRect = Rect()
    private val destRect = RectF()

    var keyTextSize: Float = 48f
        private set
    var labelTextSize: Float = 30f
        private set

    /** 0..1 slide of the spacebar language label after a language switch. */
    var spaceSlide: Float = 1f

    /** Bundled families, set once by the view. */
    var latinTypeface: Typeface = Typeface.DEFAULT
    var banglaTypeface: Typeface = Typeface.DEFAULT
    var pixelTypeface: Typeface = Typeface.DEFAULT

    /** Row index of the optional number row, so it can use its own smaller size. */
    var numberRowIndex: Int = -1

    private var numberRowTextSize: Float = 0f

    fun setSizes(density: Float, keyHeight: Float, fontScale: Float) {
        box.density = density
        surfaces.density = density
        // 22sp letters and 15sp labels at the 50dp row height, both scaled by the setting.
        keyTextSize = (22f * density * fontScale).coerceIn(18f, keyHeight * 0.62f)
        labelTextSize = (15f * density * fontScale).coerceIn(11f, keyHeight * 0.40f)
        numberRowTextSize = (18f * density * fontScale).coerceIn(10f, keyHeight * 0.50f)
    }

    fun clearCaches() {
        surfaces.clear()
        icons.clear()
        shader = null
        shaderKey = ""
        bitmap = null
        bitmapKey = ""
    }

    // --- background ------------------------------------------------------------------

    fun drawBackground(canvas: Canvas, theme: KeyboardTheme, width: Float, height: Float) {
        when (val background = theme.background) {
            is ThemeBackground.Solid -> canvas.drawColor(background.color)
            is ThemeBackground.Gradient -> {
                val key = "${theme.id}:${width.toInt()}x${height.toInt()}"
                if (shader == null || shaderKey != key) {
                    shader = buildGradient(background, width, height)
                    shaderKey = key
                }
                box.fill.shader = shader
                box.fill.color = Color.WHITE
                canvas.drawRect(0f, 0f, width, height, box.fill)
                box.fill.shader = null
            }
            is ThemeBackground.Image -> {
                canvas.drawColor(background.fallback)
                val image = imageFor(background.path, background.blur)
                if (image != null) {
                    sourceRect.set(0, 0, image.width, image.height)
                    destRect.set(0f, 0f, width, height)
                    box.fill.shader = null
                    box.fill.alpha = 255
                    box.fill.isFilterBitmap = true
                    canvas.drawBitmap(image, sourceRect, destRect, box.fill)
                    val darken = ((1f - background.brightness) * 200f).toInt().coerceIn(0, 200)
                    if (darken > 0) {
                        box.shade.color = Color.argb(darken, 0, 0, 0)
                        canvas.drawRect(0f, 0f, width, height, box.shade)
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

    /**
     * Draws every key.
     *
     * @param activeIndex the key whose press animation is running, or -1
     * @param progress 0 at rest, 1 fully pressed
     */
    fun drawKeys(
        canvas: Canvas,
        theme: KeyboardTheme,
        keys: List<PlacedKey>,
        activeIndex: Int,
        progress: Float,
        borders: Boolean,
        shift: ShiftState
    ) {
        val radius = KeyboardMetrics.clampRadius(theme.keyRadius) * box.density
        val strokeWidth = 1f * box.density
        for (index in keys.indices) {
            val placed = keys[index]
            val rect = placed.rect
            val active = index == activeIndex && progress > 0.01f
            val isPixel = theme.keyShape == KeyShape.PIXEL
            val scale = 1f - (1f - theme.pressedScale) * if (active) progress else 0f
            if (isPixel) {
                // The pixel theme presses by moving the key down a block, never by scaling it.
                scratch.set(rect)
                if (active) scratch.offset(0f, 2f * box.density * progress)
            } else if (scale > 0.99f) {
                scratch.set(rect)
            } else {
                val insetX = rect.width() * (1f - scale) / 2f
                val insetY = rect.height() * (1f - scale) / 2f
                scratch.set(rect.left + insetX, rect.top + insetY, rect.right - insetX, rect.bottom - insetY)
            }

            val isAction = placed.key.type == KeyType.ENTER
            val fill = when {
                isAction -> theme.accent
                placed.isSpecial -> theme.specialFill
                active -> lerp(theme.keyFill, theme.keyPressed, progress)
                else -> theme.keyFill
            }
            val surface = KeySurface(
                fill = fill,
                radius = radius,
                shape = theme.keyShape,
                depth = theme.keyDepth
            )
            val bitmap = surfaces.bitmap(scratch.width().toInt(), scratch.height().toInt(), surface)
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, scratch.left, scratch.top, null)
            }

            if (borders && theme.keyStroke != 0 && !isAction) {
                box.stroke.color = theme.keyStroke
                box.stroke.strokeWidth = strokeWidth
                drawOutline(canvas, theme.keyShape, scratch, radius)
            }

            if (isAction) {
                box.fill.color = theme.accent
                drawActionLabel(canvas, theme, placed, scratch)
            } else {
                drawKeyContent(canvas, theme, placed, scratch, shift)
            }
        }
    }

    private fun drawOutline(canvas: Canvas, shape: KeyShape, rect: RectF, radius: Float) {
        when (shape) {
            KeyShape.PIXEL -> {
                path.reset()
                path.addRect(rect, Path.Direction.CW)
                canvas.drawPath(path, box.stroke)
            }
            else -> canvas.drawRoundRect(rect, radius, radius, box.stroke)
        }
    }

    private fun drawKeyContent(
        canvas: Canvas,
        theme: KeyboardTheme,
        placed: PlacedKey,
        rect: RectF,
        shift: ShiftState
    ) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val iconId = IconSet.forKey(placed.key.type, shift)
        if (iconId != 0) {
            val size = (rect.height() * 0.46f).coerceAtMost(rect.width() * 0.66f)
            icons.draw(canvas, iconId, cx, cy, size, theme.specialText)
            return
        }
        if (placed.key.type == KeyType.GLOBE) {
            drawLanguageMark(canvas, theme, rect)
            return
        }
        if (placed.key.type == KeyType.SPACE) {
            drawSpaceLabel(canvas, theme, placed.label, rect)
            return
        }
        val glyph = renderable(placed.label)
        val isBangla = glyph.any { it.code in 0x0980..0x09FF }
        box.text.typeface = when {
            isBangla -> banglaTypeface
            !placed.isSpecial && theme.fontRole == FontRole.PIXEL -> pixelTypeface
            else -> latinTypeface
        }
        box.text.isFakeBoldText = theme.bold
        box.text.color = if (placed.isSpecial) theme.specialText else theme.keyText
        if (placed.isSpecial) {
            box.text.textSize = labelTextSize
            box.drawFitted(canvas, glyph, rect.left + 2f, rect.right - 2f, cy, labelTextSize)
        } else {
            val base = if (placed.row == numberRowIndex) numberRowTextSize else keyTextSize
            // Bangla x-height is smaller than Latin at the same point size; nudge it up so a
            // Bangla key and a Latin key read as the same weight.
            box.text.textSize = if (isBangla) base * BANGLA_OPTICAL_SCALE else base
            box.drawCentred(canvas, glyph, cx, cy)
        }
        box.text.isFakeBoldText = false
        box.text.typeface = Typeface.DEFAULT
    }

    /** The language name on the spacebar, sliding in when the language changes. */
    private fun drawSpaceLabel(canvas: Canvas, theme: KeyboardTheme, label: String, rect: RectF) {
        box.text.typeface = latinTypeface
        box.text.isFakeBoldText = false
        box.text.color = theme.specialText
        box.text.alpha = (255 * (0.25f + 0.75f * spaceSlide)).toInt().coerceIn(0, 255)
        val offset = (1f - spaceSlide) * 12f * box.density
        box.text.textSize = labelTextSize
        box.drawFitted(
            canvas, label,
            rect.left + 6f * box.density + offset, rect.right - 6f * box.density + offset,
            rect.centerY(), labelTextSize
        )
        box.text.alpha = 255
        box.text.typeface = Typeface.DEFAULT
        if (theme.spaceAccentLine) {
            val lineWidth = rect.width() * 0.16f
            val y = rect.bottom - 9f * box.density
            box.stroke.color = (theme.secondary ?: theme.accent)
            box.stroke.strokeWidth = 1f * box.density
            canvas.drawLine(rect.centerX() - lineWidth / 2f, y, rect.centerX() + lineWidth / 2f, y, box.stroke)
        }
    }

    /**
     * A lone kar or hasanta cannot be drawn on its own: the shaper places it on a dotted
     * circle (U+25CC), which reads as a missing glyph. Prefixing the base letter ক gives a
     * correctly shaped, meaningful key — কি, কু, ক্ — while the key still types only the mark.
     */
    private fun renderable(label: String): String {
        if (label.isEmpty()) return label
        return if (isBengaliMark(label[0])) BENGALI_BASE + label else label
    }

    private fun isBengaliMark(character: Char): Boolean {
        val code = character.code
        return code in 0x0981..0x0983 ||
            code == 0x09BC ||
            code in 0x09BE..0x09CD ||
            code == 0x09D7 ||
            code in 0x09E2..0x09E3
    }

    /**
     * The language key: Latin "A" beside Bangla "অ" with a thin double arrow underneath.
     * A globe says "the world"; two scripts and a swap arrow say "change language".
     */
    private fun drawLanguageMark(canvas: Canvas, theme: KeyboardTheme, rect: RectF) {
        val cx = rect.centerX()
        val height = rect.height()
        // The two letters have to share the key with the swap arrows, so they are sized from
        // the width first and only then from the height.
        val glyphSize = (height * 0.32f).coerceAtMost(rect.width() * 0.26f)
        val colour = theme.specialText
        val markColour = theme.secondary ?: theme.specialText

        box.text.typeface = latinTypeface
        box.text.isFakeBoldText = false
        box.text.color = colour
        box.text.textSize = glyphSize
        val latinWidth = box.text.measureText(OFF_LATIN) / 2f
        box.text.typeface = banglaTypeface
        val banglaWidth = box.text.measureText(OFF_BANGLA) / 2f
        val gap = (glyphSize * 1.15f).coerceAtMost(rect.width() - (latinWidth + banglaWidth) * 2f - 4f)
        val total = latinWidth * 2 + banglaWidth * 2 + gap
        val startX = cx - total / 2f + latinWidth
        val glyphY = rect.centerY()

        box.text.typeface = latinTypeface
        box.text.textSize = glyphSize
        box.drawCentred(canvas, OFF_LATIN, startX, glyphY)
        box.text.typeface = banglaTypeface
        box.drawCentred(canvas, OFF_BANGLA, startX + latinWidth * 2 + gap + banglaWidth, glyphY)

        // A small double arrow in the gap: it says "swap these two" without a globe.
        val arrowLeft = cx - gap / 2f + 1f * box.density
        val arrowRight = cx + gap / 2f - 1f * box.density
        val head = (gap * 0.22f).coerceAtMost(2.6f * box.density)
        val stroke = 1.1f * box.density
        val rise = glyphSize * 0.17f
        box.stroke.color = markColour
        box.stroke.strokeWidth = stroke
        val topY = glyphY - rise
        val bottomY = glyphY + rise
        canvas.drawLine(arrowLeft + head, topY, arrowRight, topY, box.stroke)
        canvas.drawLine(arrowRight, topY, arrowRight - head, topY - head * 0.7f, box.stroke)
        canvas.drawLine(arrowRight, topY, arrowRight - head, topY + head * 0.7f, box.stroke)
        canvas.drawLine(arrowRight - head, bottomY, arrowLeft, bottomY, box.stroke)
        canvas.drawLine(arrowLeft, bottomY, arrowLeft + head, bottomY - head * 0.7f, box.stroke)
        canvas.drawLine(arrowLeft, bottomY, arrowLeft + head, bottomY + head * 0.7f, box.stroke)
        box.text.typeface = Typeface.DEFAULT
    }

    /** The action key is the one filled key: accent behind, readable label on top. */
    private fun drawActionLabel(canvas: Canvas, theme: KeyboardTheme, placed: PlacedKey, rect: RectF) {
        val onAccent = if (isLight(theme.accent)) Color.BLACK else Color.WHITE
        if (placed.label == RETURN_GLYPH || placed.label.isEmpty()) {
            val size = (rect.height() * 0.44f).coerceAtMost(rect.width() * 0.60f)
            icons.draw(canvas, IconSet.RETURN, rect.centerX(), rect.centerY(), size, onAccent)
            return
        }
        box.text.typeface = latinTypeface
        box.text.isFakeBoldText = true
        box.text.color = onAccent
        box.text.textSize = labelTextSize
        box.drawFitted(canvas, placed.label, rect.left + 2f, rect.right - 2f, rect.centerY(), labelTextSize)
        box.text.isFakeBoldText = false
        box.text.typeface = Typeface.DEFAULT
    }

    // --- press preview and long-press popup ------------------------------------------

    fun drawPreview(canvas: Canvas, theme: KeyboardTheme, placed: PlacedKey, keyHeight: Float) {
        val rect = placed.rect
        bubbleRect.set(
            rect.left - 4f * box.density, rect.top - keyHeight * 0.92f,
            rect.right + 4f * box.density, rect.top - 6f * box.density
        )
        box.fill.color = theme.keyFill
        box.fill.alpha = 255
        box.fill.color = if (Color.alpha(theme.keyFill) < 255) blendOpaque(theme.keyFill) else theme.keyFill
        canvas.drawRoundRect(bubbleRect, theme.keyRadius * box.density, theme.keyRadius * box.density, box.fill)
        box.stroke.color = theme.accent
        box.stroke.strokeWidth = 1.2f * box.density
        canvas.drawRoundRect(bubbleRect, theme.keyRadius * box.density, theme.keyRadius * box.density, box.stroke)
        box.text.typeface = latinTypeface
        box.text.isFakeBoldText = false
        box.text.color = theme.keyText
        box.text.textSize = keyTextSize
        box.drawFitted(
            canvas, renderable(placed.label),
            bubbleRect.left + 4f * box.density, bubbleRect.right - 4f * box.density,
            bubbleRect.centerY(), keyTextSize
        )
        box.text.typeface = Typeface.DEFAULT
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
        val width = cell * characters.size + 8f * box.density
        val height = cell + 8f * box.density
        var left = placed.rect.centerX() - width / 2f
        left = left.coerceAtLeast(4f * box.density)
        val top = (placed.rect.top - height - 6f * box.density).coerceAtLeast(4f * box.density)
        bubbleRect.set(left, top, left + width, top + height)

        box.fill.color = if (theme.dark) 0xFF1E2430.toInt() else 0xFFFFFFFF.toInt()
        val radius = theme.keyRadius * box.density
        canvas.drawRoundRect(bubbleRect, radius, radius, box.fill)
        box.stroke.color = theme.accent
        box.stroke.strokeWidth = 1.5f * box.density
        canvas.drawRoundRect(bubbleRect, radius, radius, box.stroke)

        for (index in characters.indices) {
            val cellLeft = bubbleRect.left + 4f * box.density + cell * index
            if (index == selected) {
                box.fill.color = theme.accent
                scratch.set(
                    cellLeft + 2f * box.density, bubbleRect.top + 4f * box.density,
                    cellLeft + cell - 2f * box.density, bubbleRect.bottom - 4f * box.density
                )
                canvas.drawRoundRect(scratch, radius * 0.7f, radius * 0.7f, box.fill)
            }
            box.text.color = if (index == selected) {
                if (isLight(theme.accent)) Color.BLACK else Color.WHITE
            } else {
                if (theme.dark) 0xFFF8FAFC.toInt() else 0xFF111827.toInt()
            }
            box.text.typeface = if (characters[index].any { it.code in 0x0980..0x09FF }) {
                banglaTypeface
            } else {
                latinTypeface
            }
            box.text.textSize = keyTextSize * 0.92f
            box.drawCentred(canvas, renderable(characters[index]), cellLeft + cell / 2f, bubbleRect.centerY())
        }
        box.text.typeface = Typeface.DEFAULT
    }

    private fun blendOpaque(color: Int): Int = Color.argb(255, Color.red(color), Color.green(color), Color.blue(color))

    private fun lerp(from: Int, to: Int, t: Float): Int = Color.argb(
        (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t).toInt(),
        (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt(),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt(),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt()
    )

    private fun isLight(color: Int): Boolean =
        0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color) > 160

    companion object {
        const val RETURN_GLYPH = "⏎"

        /** Optical size correction for Bangla, whose glyphs sit smaller than Latin. */
        private const val BANGLA_OPTICAL_SCALE = 1.08f

        /** Base letter a bare kar is drawn on, so no key shows a dotted circle. */
        private const val BENGALI_BASE = "ক"
        private const val OFF_LATIN = "A"
        private const val OFF_BANGLA = "অ"
    }
}
