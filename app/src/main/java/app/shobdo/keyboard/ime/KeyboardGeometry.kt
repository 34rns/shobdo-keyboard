package app.shobdo.keyboard.ime

import android.graphics.RectF
import app.shobdo.keyboard.settings.OneHanded

/** One key with its on-screen rectangle and the glyph/text for the current state. */
class PlacedKey(
    val key: KeyModel,
    val row: Int,
    val column: Int,
    val label: String,
    val code: String
) {
    val rect: RectF = RectF()

    val isSpecial: Boolean get() = key.isSpecial
}

/**
 * Turns a layout into rectangles.
 *
 * This runs only when something changes size (rotation, split screen, settings) — never from
 * onDraw — so drawing stays allocation-free. Rows are normalised independently, which is what
 * lets a ten-key number row sit above a nine-key letter row.
 */
class KeyboardGeometry {

    val keys: ArrayList<PlacedKey> = ArrayList(56)

    /** Suggestion bar rect; empty when the bar is hidden. */
    val barRect: RectF = RectF()

    /** Left edge of the key area, which is not zero in one-handed mode. */
    var keyboardLeft: Float = 0f
        private set

    var keyboardRight: Float = 0f
        private set

    /**
     * Lays the page out and returns the total height used.
     *
     * @param top y of the first row (below the suggestion bar)
     * @param hiddenRowIndexes rows to skip (the number row when the setting is off)
     */
    fun layout(
        layout: KeyboardLayout,
        width: Float,
        keyHeight: Float,
        horizontalPadding: Float,
        spacing: Float,
        top: Float,
        barHeight: Float,
        shift: ShiftState,
        shiftUsesAlternates: Boolean,
        spaceLabel: String?,
        oneHanded: OneHanded,
        hiddenRowIndexes: Set<Int> = emptySet()
    ): Float {
        keys.clear()

        val visibleRows = layout.rows.withIndex().filter { it.index !in hiddenRowIndexes }
        val availableWidth = if (oneHanded == OneHanded.OFF) width else width * ONE_HANDED_FRACTION
        keyboardLeft = when (oneHanded) {
            OneHanded.OFF -> 0f
            OneHanded.LEFT -> 0f
            OneHanded.RIGHT -> width - availableWidth
        }
        keyboardRight = keyboardLeft + availableWidth

        if (barHeight > 0f) {
            barRect.set(keyboardLeft, 0f, keyboardRight, barHeight)
        } else {
            barRect.setEmpty()
        }

        val shifted = shift != ShiftState.OFF
        var y = top
        for ((rowIndex, row) in visibleRows) {
            val rowWeight = KeyboardLayout.rowWeight(row)
            val keysWidth = availableWidth - 2 * horizontalPadding - spacing * (row.size - 1)
            var x = keyboardLeft + horizontalPadding
            row.forEachIndexed { column, key ->
                val keyWidth = keysWidth * (key.widthWeight / rowWeight)
                val label = if (key.type == KeyType.SPACE && spaceLabel != null) {
                    spaceLabel
                } else {
                    key.displayLabel(shifted, shiftUsesAlternates)
                }
                val placed = PlacedKey(
                    key = key,
                    row = rowIndex,
                    column = column,
                    label = label,
                    code = key.displayCode(shifted, shiftUsesAlternates)
                )
                placed.rect.set(x, y, x + keyWidth, y + keyHeight)
                keys.add(placed)
                x += keyWidth + spacing
            }
            y += keyHeight + spacing
        }
        return y - spacing
    }

    /** Index of the key under ([x], [y]), or -1. A small slop keeps fast taps forgiving. */
    fun findKey(x: Float, y: Float, slop: Float = 0f): Int {
        for (index in keys.indices) {
            val rect = keys[index].rect
            if (x >= rect.left - slop && x <= rect.right + slop && y >= rect.top - slop && y <= rect.bottom + slop) {
                return index
            }
        }
        return -1
    }

    companion object {
        /** One-handed mode shrinks the keyboard to this fraction of the screen width. */
        const val ONE_HANDED_FRACTION = 0.78f
    }
}
