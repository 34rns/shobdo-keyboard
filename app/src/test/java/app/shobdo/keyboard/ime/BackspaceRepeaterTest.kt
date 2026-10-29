package app.shobdo.keyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackspaceRepeaterTest {

    private val repeater = BackspaceRepeater()

    @Test
    fun `nothing repeats before the initial delay`() {
        repeater.press(now = 0)
        assertEquals(0, repeater.due(now = 399))
        assertTrue(repeater.isHolding)
    }

    @Test
    fun `first repeat lands exactly at 400 ms`() {
        repeater.press(now = 1_000)
        assertEquals(1, repeater.due(now = 1_400))
    }

    @Test
    fun `repeats continue every 50 ms`() {
        repeater.press(now = 0)
        assertEquals(1, repeater.due(now = 400))
        assertEquals(1, repeater.due(now = 450))
        assertEquals(1, repeater.due(now = 500))
        assertEquals("the same instant must not fire twice", 0, repeater.due(now = 500))
    }

    @Test
    fun `the same timestamp never double counts`() {
        repeater.press(now = 0)
        assertEquals(1, repeater.due(now = 400))
        assertEquals(0, repeater.due(now = 400))
        assertEquals(3, repeater.due(now = 550))
    }

    @Test
    fun `release stops the repeats`() {
        repeater.press(now = 0)
        repeater.due(now = 500)
        repeater.release()
        assertFalse(repeater.isHolding)
        assertEquals(0, repeater.due(now = 5_000))
    }

    @Test
    fun `releasing and pressing again restarts the delay`() {
        repeater.press(now = 0)
        repeater.due(now = 900)
        repeater.release()
        repeater.press(now = 1_000)
        assertEquals("a new press waits the full 400 ms again", 0, repeater.due(now = 1_300))
        assertEquals(1, repeater.due(now = 1_400))
        assertEquals(1, repeater.due(now = 1_450))
    }

    @Test
    fun `next delay counts down to the next repeat`() {
        repeater.press(now = 0)
        assertEquals(400, repeater.nextDelayMs(now = 0))
        assertEquals(100, repeater.nextDelayMs(now = 300))
        assertEquals(50, repeater.nextDelayMs(now = 400))
        assertEquals(25, repeater.nextDelayMs(now = 425))
    }
}
