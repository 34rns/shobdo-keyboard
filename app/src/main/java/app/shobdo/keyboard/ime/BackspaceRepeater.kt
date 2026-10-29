package app.shobdo.keyboard.ime

/**
 * Timing for press-and-hold delete: one delete on press, then after [initialDelayMs], then
 * every [intervalMs] while the finger stays down.
 *
 * The view calls [due] from a repeating runnable; this class keeps the count of deletes it
 * has already handed out so a tick that arrives late catches up instead of being lost, and
 * so the same timestamp can never fire twice.
 */
class BackspaceRepeater(
    private val initialDelayMs: Long = INITIAL_DELAY_MS,
    private val intervalMs: Long = INTERVAL_MS
) {

    private var downAt: Long = 0L
    private var holding: Boolean = false
    private var repeatsFired: Int = 0

    fun press(now: Long) {
        holding = true
        downAt = now
        repeatsFired = 0
    }

    fun release() {
        holding = false
        repeatsFired = 0
    }

    val isHolding: Boolean get() = holding

    /**
     * Number of repeats due at [now], not counting the delete that already happened on
     * press. Returns 0 before the initial delay or after release.
     */
    fun due(now: Long): Int {
        if (!holding) return 0
        val elapsed = now - downAt
        if (elapsed < initialDelayMs) return 0
        val slots = ((elapsed - initialDelayMs) / intervalMs + 1).toInt()
        val fresh = slots - repeatsFired
        if (fresh <= 0) return 0
        repeatsFired = slots
        return fresh
    }

    /** Delay until [due] can next return something greater than 0. */
    fun nextDelayMs(now: Long): Long {
        if (!holding) return initialDelayMs
        val elapsed = now - downAt
        if (elapsed < initialDelayMs) return initialDelayMs - elapsed
        return intervalMs - ((elapsed - initialDelayMs) % intervalMs)
    }

    companion object {
        const val INITIAL_DELAY_MS = 400L
        const val INTERVAL_MS = 50L
    }
}
