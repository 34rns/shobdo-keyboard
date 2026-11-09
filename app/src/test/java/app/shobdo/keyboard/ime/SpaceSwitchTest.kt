package app.shobdo.keyboard.ime

import org.junit.Assert.*
import org.junit.Test

class SpaceSwitchTest {
    @Test fun `swipe switches during move only once and never queues`() {
        val gesture = SpaceGesture()
        gesture.start(100f)
        assertFalse(gesture.move(120f, 36f))
        assertTrue(gesture.move(136f, 36f))
        assertTrue(gesture.switched)
        assertFalse(gesture.move(190f, 36f))
        gesture.start(200f)
        assertFalse(gesture.switched)
        assertTrue(gesture.move(150f, 36f))
    }
    @Test fun `language feedback finishes within 150 milliseconds`() {
        assertTrue(KeyboardMetrics.labelSwapMs in 100L..150L)
    }

    @Test fun `rapid cycles update state synchronously`() {
        var language = Language.ENGLISH
        repeat(7) { language = LanguageCycle.next(language, LanguageCycle.ORDER) }
        assertEquals(Language.BANGLA, language)
    }
}
