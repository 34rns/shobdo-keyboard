package app.shobdo.keyboard.ime

enum class ShiftState {
    OFF,
    /** Next character only. */
    ONCE,
    /** Caps lock until shift is tapped again. */
    LOCKED
}

/**
 * Shift state machine: off -> once -> locked -> off.
 *
 * A second tap within [doubleTapWindowMs] promotes `once` to `locked`; a slower second tap
 * turns shift off. Typing a character while in `once` clears it, while `locked` survives.
 *
 * Time is passed in rather than read from the clock so the behaviour is testable.
 */
class ShiftStateMachine(private val doubleTapWindowMs: Long = DOUBLE_TAP_WINDOW_MS) {

    var state: ShiftState = ShiftState.OFF
        private set

    private var lastTapAt: Long = Long.MIN_VALUE

    val isActive: Boolean get() = state != ShiftState.OFF

    /** @return the state after the tap. */
    fun shiftPressed(now: Long): ShiftState {
        state = when (state) {
            ShiftState.OFF -> ShiftState.ONCE
            ShiftState.ONCE -> if (now - lastTapAt <= doubleTapWindowMs) ShiftState.LOCKED else ShiftState.OFF
            ShiftState.LOCKED -> ShiftState.OFF
        }
        lastTapAt = now
        return state
    }

    /** @return the state after typing a character. */
    fun characterTyped(): ShiftState {
        if (state == ShiftState.ONCE) state = ShiftState.OFF
        return state
    }

    /** Called when the page or language changes: shift never survives that. */
    fun reset() {
        state = ShiftState.OFF
        lastTapAt = Long.MIN_VALUE
    }

    companion object {
        const val DOUBLE_TAP_WINDOW_MS = 350L
    }
}
