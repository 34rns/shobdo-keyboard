package app.shobdo.keyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftStateMachineTest {

    @Test
    fun `one tap arms a single capital`() {
        val shift = ShiftStateMachine()
        assertEquals(ShiftState.ONCE, shift.shiftPressed(now = 0))
        assertTrue(shift.isActive)
    }

    @Test
    fun `double tap locks`() {
        val shift = ShiftStateMachine()
        shift.shiftPressed(now = 1_000)
        assertEquals(ShiftState.LOCKED, shift.shiftPressed(now = 1_100))
    }

    @Test
    fun `a slow second tap turns shift off instead of locking`() {
        val shift = ShiftStateMachine()
        shift.shiftPressed(now = 1_000)
        assertEquals(ShiftState.OFF, shift.shiftPressed(now = 1_000 + ShiftStateMachine.DOUBLE_TAP_WINDOW_MS + 1))
    }

    @Test
    fun `typing clears a single capital but not caps lock`() {
        val shift = ShiftStateMachine()
        shift.shiftPressed(now = 0)
        assertEquals(ShiftState.OFF, shift.characterTyped())

        shift.shiftPressed(now = 100)
        shift.shiftPressed(now = 150) // locked
        assertEquals(ShiftState.LOCKED, shift.characterTyped())
        assertEquals(ShiftState.LOCKED, shift.characterTyped())
    }

    @Test
    fun `tapping a locked shift turns it off`() {
        val shift = ShiftStateMachine()
        shift.shiftPressed(now = 0)
        shift.shiftPressed(now = 10)
        assertEquals(ShiftState.LOCKED, shift.state)
        assertEquals(ShiftState.OFF, shift.shiftPressed(now = 5_000))
        assertFalse(shift.isActive)
    }

    @Test
    fun `the double tap window is measured from the previous tap`() {
        val shift = ShiftStateMachine()
        shift.shiftPressed(now = 0)
        shift.shiftPressed(now = 50) // locks
        shift.shiftPressed(now = 10_000) // off, last tap moves
        assertEquals(ShiftState.ONCE, shift.shiftPressed(now = 10_100))
        assertEquals(ShiftState.LOCKED, shift.shiftPressed(now = 10_200))
    }

    @Test
    fun `reset forgets everything`() {
        val shift = ShiftStateMachine()
        shift.shiftPressed(now = 0)
        shift.shiftPressed(now = 10)
        shift.reset()
        assertEquals(ShiftState.OFF, shift.state)
        assertEquals(ShiftState.ONCE, shift.shiftPressed(now = 20))
    }
}
