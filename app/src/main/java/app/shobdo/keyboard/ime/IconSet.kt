package app.shobdo.keyboard.ime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.SparseArray
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

    private val cache = SparseArray<Bitmap>()
    private val filters = SparseArray<PorterDuffColorFilter>()
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
        cache.put(key, bitmap)
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
        val bitmap = cache[id * 1000 + pixels] ?: return
        paint.colorFilter = filters[color]
        target.drawBitmap(bitmap, centerX - pixels / 2f, centerY - pixels / 2f, paint)
    }

    fun clear() {
        for (index in 0 until cache.size()) cache.valueAt(index).recycle()
        cache.clear()
        filters.clear()
    }

    private fun prepare(id: Int, size: Float, color: Int) {
        bitmap(id, size.toInt().coerceAtLeast(1))
        if (filters[color] == null) filters.put(color, PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN))
    }

    fun prepareStrip(density: Float, color: Int) {
        for (id in intArrayOf(THEME, ONE_HANDED, SETTINGS, COLLAPSE)) prepare(id, 20f * density, color)
    }

    fun prepareKey(key: PlacedKey, theme: app.shobdo.keyboard.theme.KeyboardTheme) {
        val rect = key.rect
        val size = (rect.height() * 0.46f).coerceAtMost(rect.width() * 0.66f)
        // Pressed icons use the same raster size; only their centres move with the key.
        for (state in ShiftState.entries) {
            val id = forKey(key.key.type, state)
            if (id != 0) prepare(id, size, theme.specialText)
        }
        if (key.key.type == KeyType.ENTER) {
            val color = if (android.graphics.Color.red(theme.accent) * 0.299 +
                android.graphics.Color.green(theme.accent) * 0.587 + android.graphics.Color.blue(theme.accent) * 0.114 > 160
            ) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            prepare(RETURN, (rect.height() * 0.44f).coerceAtMost(rect.width() * 0.60f), color)
        }
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
