package app.shobdo.keyboard.ime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.annotation.DrawableRes
import app.shobdo.keyboard.R

/**
 * The keyboard's icon set.
 *
 * Every icon is a vector drawable from `res/drawable/ic_kb_*` on the same 24dp grid with the
 * same 1.75dp stroke, so nothing mixes weights. Vectors are rasterised once into a Bitmap at
 * the size they are drawn at and tinted with a colour filter, which keeps onDraw free of
 * allocations and of drawable re-parsing.
 */
class IconSet(private val context: Context) {

    private val cache = HashMap<Int, Bitmap>(16)
    private val canvas = Canvas()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var filter: PorterDuffColorFilter? = null
    private var filterColor: Int = 0

    /** Rasterises [id] at [sizePx], cached per resource and size. */
    fun bitmap(@DrawableRes id: Int, sizePx: Int): Bitmap? {
        if (sizePx <= 0) return null
        val key = id * 1000 + sizePx
        cache[key]?.let { return it }
        val drawable = runCatching { context.getDrawable(id) }.getOrNull() ?: return null
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        canvas.setBitmap(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        canvas.setBitmap(null)
        cache[key] = bitmap
        return bitmap
    }

    /**
     * Draws [id] centred on ([centerX], [centerY]) at [size] dp-scaled pixels, tinted to
     * [color]. Tinting with a filter means one rasterised bitmap serves every theme.
     */
    fun draw(
        target: Canvas,
        @DrawableRes id: Int,
        centerX: Float,
        centerY: Float,
        size: Float,
        color: Int
    ) {
        val pixels = size.toInt().coerceAtLeast(1)
        val bitmap = bitmap(id, pixels) ?: return
        if (filter == null || filterColor != color) {
            filter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            filterColor = color
        }
        paint.colorFilter = filter
        target.drawBitmap(bitmap, centerX - pixels / 2f, centerY - pixels / 2f, paint)
    }

    fun clear() {
        for (bitmap in cache.values) bitmap.recycle()
        cache.clear()
    }

    companion object {
        @DrawableRes
        val SHIFT_OUTLINE = R.drawable.ic_kb_shift

        @DrawableRes
        val SHIFT_FILLED = R.drawable.ic_kb_shift_filled

        @DrawableRes
        val CAPS_LOCK = R.drawable.ic_kb_caps_lock

        @DrawableRes
        val BACKSPACE = R.drawable.ic_kb_backspace

        @DrawableRes
        val RETURN = R.drawable.ic_kb_return

        @DrawableRes
        val SEND = R.drawable.ic_kb_send

        @DrawableRes
        val SEARCH = R.drawable.ic_kb_search

        @DrawableRes
        val SYMBOLS = R.drawable.ic_kb_symbols

        @DrawableRes
        val THEME = R.drawable.ic_kb_theme

        @DrawableRes
        val ONE_HANDED = R.drawable.ic_kb_one_handed

        @DrawableRes
        val SETTINGS = R.drawable.ic_kb_settings

        @DrawableRes
        val COLLAPSE = R.drawable.ic_kb_collapse

        /** The icon for a key type, or 0 when the key draws text instead. */
        fun forKey(type: KeyType, shift: ShiftState): Int = when (type) {
            KeyType.SHIFT -> when (shift) {
                ShiftState.OFF -> SHIFT_OUTLINE
                ShiftState.ONCE -> SHIFT_FILLED
                ShiftState.LOCKED -> CAPS_LOCK
            }
            KeyType.BACKSPACE -> BACKSPACE
            KeyType.SYMBOLS -> SYMBOLS
            else -> 0
        }
    }
}
