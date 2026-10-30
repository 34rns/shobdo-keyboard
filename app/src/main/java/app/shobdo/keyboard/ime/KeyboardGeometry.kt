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
 * Runs only when something changes size (rotation, split screen, settings) — never from
 * onDraw — so drawing stays allocation-free. Rows are normalised independently, which is what
 * lets a ten-key number row sit above a nine-key letter row, and each row takes its height
 * from the metrics table so the number row can be shorter.
 */
class KeyboardGeometry {

    val keys: ArrayList<PlacedKey> = ArrayList(56)

    /** Suggestion strip rect; set by [setStrip] before [layout]. */
    val barRect: RectF = RectF()

    /** Left edge of the key area, which is not zero in one-handed mode. */
    var keyboardLeft: Float = 0f
        private set

    var keyboardRight: Float = 0f
        private set

    /** Bottom of the last row, so the view can check its measured height. */
    var bottom: Float = 0f
        private set

    private var hitSlopX: Float = 0f
    private var hitSlopY: Float = 0f


    fun setStrip(left: Float, top: Float, right: Float, bottom: Float) {
        if (bottom > top) barRect.set(left, top, right, bottom) else barRect.setEmpty()
    }

    /**
     * Lays the page out and returns the total height used.
     *
     * @param top y of the first visible row
     * @param rowHeights px height of each visible row, in order; a missing entry falls back
     *   to the previous one so a caller cannot leave a zero-height row
     * @param spacing horizontal gap between keys, which is also the horizontal hit slop
     * @param rowGap vertical gap between rows, which is also the vertical hit slop
     */
    fun layout(
        layout: KeyboardLayout,
        width: Float,
        top: Float,
        rowHeights: FloatArray,
        horizontalPadding: Float,
        spacing: Float,
        rowGap: Float,
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

        hitSlopX = spacing / 2f
        hitSlopY = rowGap / 2f

        val shifted = shift != ShiftState.OFF
        val heights = FloatArray(visibleRows.size) { position ->
            rowHeights.getOrNull(position)
                ?: rowHeights.getOrNull(position - 1)
                ?: rowHeights.firstOrNull()
                ?: 0f
        }
        val tops = KeyboardMetrics.rowTops(top, heights, rowGap)
        visibleRows.forEachIndexed { position, (rowIndex, row) ->
            val rowHeight = heights[position]
            val y = tops[position]
            val widths = KeyboardMetrics.keyWidths(
                availableWidth = availableWidth,
                horizontalPadding = horizontalPadding,
                gap = spacing,
                weights = FloatArray(row.size) { row[it].widthWeight }
            )
            var x = keyboardLeft + horizontalPadding
            row.forEachIndexed { column, key ->
                val keyWidth = widths[column]
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
                placed.rect.set(x, y, x + keyWidth, y + rowHeight)
                keys.add(placed)
                x += keyWidth + spacing
            }
        }
        bottom = when {
            keys.isEmpty() -> top
            else -> tops.last() + heights.last()
        }
        return bottom
    }

    /**
     * Index of the key under ([x], [y]), or -1.
     *
     * Exact containment is checked first so a finger that is clearly on a key always gets
     * that key. Only then is the gap between keys assigned, which is what removes dead zones
     * between rows and between neighbours.
     */
    fun findKey(x: Float, y: Float): Int {
        for (index in keys.indices) {
            if (keys[index].rect.contains(x, y)) return index
        }
        for (index in keys.indices) {
            val rect = keys[index].rect
            if (x >= rect.left - hitSlopX && x <= rect.right + hitSlopX &&
                y >= rect.top - hitSlopY && y <= rect.bottom + hitSlopY
            ) {
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
