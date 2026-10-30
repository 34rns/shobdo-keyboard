package app.shobdo.keyboard.ime

/**
 * Every size in the keyboard, in dp.
 *
 * This is the only place a number is allowed to live. A gap here is the same gap on every
 * page, in every language, under every theme, which is what makes the grid look drawn rather
 * than assembled. Pure Kotlin so the layout rules can be unit-tested on the JVM.
 */
object KeyboardMetrics {

    // --- vertical rhythm -----------------------------------------------------------

    /** Breathing room above the suggestion strip. */
    const val topPadding = 4f

    /** The strip is always present, even with no candidate in it. */
    const val suggestionStripHeight = 44f

    /** Space between the strip and the first key row. */
    const val gapBelowStrip = 6f

    const val rowVerticalGap = 8f
    const val letterRowHeight = 50f

    /** About 88% of a letter row, so the number row reads as secondary. */
    const val numberRowHeight = 44f

    /** Space under the last row, before the navigation bar inset. */
    const val bottomPadding = 6f

    // --- horizontal rhythm ---------------------------------------------------------

    const val horizontalPadding = 4f
    const val keyHorizontalGap = 5f

    // --- keys ----------------------------------------------------------------------

    const val keyCornerRadius = 10f
    const val minKeyCornerRadius = 4f
    const val maxKeyCornerRadius = 14f

    /** A key may never be smaller than this, in dp. */
    const val minTouchWidth = 48f
    const val minTouchHeight = 44f

    // --- press feel ----------------------------------------------------------------

    const val pressDownMs = 70L
    const val pressUpMs = 110L
    const val pressScale = 0.97f

    /** How long the spacebar keeps showing the new language name after a switch. */
    const val labelSwapMs = 900L

    fun rowHeight(isNumberRow: Boolean): Float =
        if (isNumberRow) numberRowHeight else letterRowHeight

    /** y of the first key row, measured from the top of the keyboard view. */
    fun firstRowTop(): Float = topPadding + suggestionStripHeight + gapBelowStrip

    /**
     * Height of the whole keyboard for [rowCount] rows.
     *
     * @param navInsetDp bottom navigation-bar inset in dp
     * @param firstRowIsNumberRow whether the first visible row is the shorter number row
     */
    fun totalHeight(rowCount: Int, navInsetDp: Float, firstRowIsNumberRow: Boolean): Float {
        if (rowCount <= 0) return 0f
        var height = firstRowTop()
        for (index in 0 until rowCount) {
            height += rowHeight(firstRowIsNumberRow && index == 0)
        }
        height += rowVerticalGap * (rowCount - 1)
        return height + bottomPadding + navInsetDp
    }

    /**
     * Height for [rowCount] rows with explicit row heights, used when the user changes the key
     * height setting: the number row always stays at 88% of a letter row.
     */
    fun totalHeight(rowCount: Int, navInsetDp: Float, numberRowDp: Float, letterRowDp: Float): Float {
        if (rowCount <= 0) return 0f
        var height = firstRowTop()
        for (index in 0 until rowCount) {
            height += if (index == 0) numberRowDp else letterRowDp
        }
        height += rowVerticalGap * (rowCount - 1)
        return height + bottomPadding + navInsetDp
    }

    /** True when the gap above the first key row is visible in the design. */
    fun hasTopGap(): Boolean = firstRowTop() >= 8f

    /**
     * Hit areas are grown by half a gap on every side, so two neighbouring keys meet without
     * either losing pixels. Taps in the gap belong to a key, never to nothing.
     */
    fun hitSlopX(): Float = keyHorizontalGap / 2f

    fun hitSlopY(): Float = rowVerticalGap / 2f

    /** Clamps a theme's own corner radius into the range the design allows. */
    fun clampRadius(radius: Float): Float =
        radius.coerceIn(minKeyCornerRadius, maxKeyCornerRadius)

    // --- pure layout math, shared with the view and asserted by the tests -------------

    /**
     * Widths for one row. Rows are normalised independently, which is what lets a ten-key
     * number row sit above a nine-key letter row and still end at the same edge.
     */
    fun keyWidths(
        availableWidth: Float,
        horizontalPadding: Float,
        gap: Float,
        weights: FloatArray
    ): FloatArray {
        if (weights.isEmpty()) return FloatArray(0)
        val total = weights.sum().coerceAtLeast(0.0001f)
        val usable = availableWidth - 2 * horizontalPadding - gap * (weights.size - 1)
        return FloatArray(weights.size) { index -> usable * (weights[index] / total) }
    }

    /** y of each row's top edge: every gap is [rowVerticalGap]. */
    fun rowTops(top: Float, heights: FloatArray, gap: Float = rowVerticalGap): FloatArray {
        val tops = FloatArray(heights.size)
        var y = top
        for (index in heights.indices) {
            tops[index] = y
            y += heights[index] + gap
        }
        return tops
    }
}
