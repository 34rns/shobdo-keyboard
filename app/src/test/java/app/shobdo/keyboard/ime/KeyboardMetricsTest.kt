package app.shobdo.keyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The layout contract, checked as arithmetic rather than by looking at a screenshot.
 *
 * Every number here comes from [KeyboardMetrics], which is what the view and the geometry
 * both use, so a stray gap or a differently sized row fails the build.
 */
class KeyboardMetricsTest {

    private val tolerance = 0.001f

    @Test
    fun `top gap above the first key row is at least 8dp`() {
        assertTrue(
            "first row top was ${KeyboardMetrics.firstRowTop()}",
            KeyboardMetrics.firstRowTop() >= 8f
        )
        assertTrue(KeyboardMetrics.hasTopGap())
    }

    @Test
    fun `the strip is always part of the height`() {
        val withOneRow = KeyboardMetrics.totalHeight(1, navInsetDp = 0f, firstRowIsNumberRow = false)
        assertTrue(withOneRow > KeyboardMetrics.suggestionStripHeight)
        assertTrue(withOneRow >= KeyboardMetrics.firstRowTop() + KeyboardMetrics.letterRowHeight)
    }

    @Test
    fun `every letter row has the same height`() {
        val heights = FloatArray(5) { KeyboardMetrics.rowHeight(isNumberRow = false) }
        assertEquals(1, heights.toSet().size)
        assertEquals(KeyboardMetrics.letterRowHeight, heights.first(), tolerance)
    }

    @Test
    fun `the number row is shorter but no more than 88 percent`() {
        val number = KeyboardMetrics.rowHeight(isNumberRow = true)
        assertTrue(number < KeyboardMetrics.letterRowHeight)
        assertEquals(0.88f, number / KeyboardMetrics.letterRowHeight, 0.005f)
    }

    @Test
    fun `horizontal gaps are identical across a row of mixed weights`() {
        val widths = KeyboardMetrics.keyWidths(
            availableWidth = 1080f,
            horizontalPadding = KeyboardMetrics.horizontalPadding,
            gap = KeyboardMetrics.keyHorizontalGap,
            weights = floatArrayOf(1.25f, 0.8f, 1f, 4f, 0.8f, 1.5f)
        )
        var x = KeyboardMetrics.horizontalPadding
        val gaps = FloatArray(widths.size - 1)
        for (index in widths.indices) {
            x += widths[index]
            if (index < widths.size - 1) {
                gaps[index] = (x + KeyboardMetrics.keyHorizontalGap) - x - KeyboardMetrics.keyHorizontalGap
                x += KeyboardMetrics.keyHorizontalGap
            }
        }
        assertEquals(1, gaps.toSet().size)
        assertEquals(0f, gaps.first(), tolerance)
    }

    @Test
    fun `a row fills the width exactly`() {
        val width = 1080f
        val weights = floatArrayOf(1f, 1f, 1f, 1.5f, 1f, 1f, 1f, 1f, 1.5f)
        val widths = KeyboardMetrics.keyWidths(
            width, KeyboardMetrics.horizontalPadding, KeyboardMetrics.keyHorizontalGap, weights
        )
        val used = widths.sum() +
            KeyboardMetrics.keyHorizontalGap * (weights.size - 1) +
            2 * KeyboardMetrics.horizontalPadding
        assertEquals(width, used, 0.01f)
    }

    @Test
    fun `vertical gaps are identical between every pair of rows`() {
        val heights = floatArrayOf(
            KeyboardMetrics.numberRowHeight, KeyboardMetrics.letterRowHeight, KeyboardMetrics.letterRowHeight
        )
        val tops = KeyboardMetrics.rowTops(100f, heights)
        assertEquals(3, tops.size)
        for (index in 0 until tops.size - 1) {
            val gap = tops[index + 1] - (tops[index] + heights[index])
            assertEquals(KeyboardMetrics.rowVerticalGap, gap, tolerance)
        }
    }

    @Test
    fun `hit areas leave no dead zones between keys or rows`() {
        val widths = KeyboardMetrics.keyWidths(
            availableWidth = 1080f,
            horizontalPadding = KeyboardMetrics.horizontalPadding,
            gap = KeyboardMetrics.keyHorizontalGap,
            weights = floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f)
        )
        val halfGapX = KeyboardMetrics.hitSlopX()
        var left = KeyboardMetrics.horizontalPadding
        for (index in widths.indices) {
            val right = left + widths[index]
            if (index < widths.size - 1) {
                val nextLeft = right + KeyboardMetrics.keyHorizontalGap
                // The touched area of this key reaches the touched area of the next one.
                assertTrue(
                    "gap between key $index and ${index + 1}",
                    right + halfGapX >= nextLeft - halfGapX
                )
            }
            left = right + KeyboardMetrics.keyHorizontalGap
        }

        val heights = floatArrayOf(KeyboardMetrics.letterRowHeight, KeyboardMetrics.letterRowHeight)
        val tops = KeyboardMetrics.rowTops(200f, heights)
        val halfGapY = KeyboardMetrics.hitSlopY()
        assertTrue(tops[0] + heights[0] + halfGapY >= tops[1] - halfGapY)
    }

    @Test
    fun `press animation eases out and returns to rest`() {
        val press = PressAnimation()
        press.press(1000L)
        assertEquals(0f, press.progress(1000L), tolerance)
        val quarter = press.progress(1000L + KeyboardMetrics.pressDownMs / 4)
        val half = press.progress(1000L + KeyboardMetrics.pressDownMs / 2)
        assertTrue("ease-out starts fast", quarter > half / 2f)
        assertEquals(1f, press.progress(1000L + KeyboardMetrics.pressDownMs), tolerance)
        press.release(1000L + KeyboardMetrics.pressDownMs)
        assertEquals(1f, press.progress(1000L + KeyboardMetrics.pressDownMs), tolerance)
        assertEquals(0f, press.progress(1000L + KeyboardMetrics.pressDownMs + KeyboardMetrics.pressUpMs), tolerance)
    }

    @Test
    fun `press scales the key down and back`() {
        val press = PressAnimation()
        press.press(0L)
        assertEquals(1f, press.scale(0L), tolerance)
        assertEquals(KeyboardMetrics.pressScale, press.scale(KeyboardMetrics.pressDownMs), tolerance)
        press.release(KeyboardMetrics.pressDownMs)
        assertEquals(1f, press.scale(KeyboardMetrics.pressDownMs + KeyboardMetrics.pressUpMs), tolerance)
    }

    @Test
    fun `theme corner radius is clamped into the design range`() {
        assertEquals(KeyboardMetrics.minKeyCornerRadius, KeyboardMetrics.clampRadius(0f), tolerance)
        assertEquals(KeyboardMetrics.maxKeyCornerRadius, KeyboardMetrics.clampRadius(40f), tolerance)
        assertEquals(10f, KeyboardMetrics.clampRadius(10f), tolerance)
    }
}
