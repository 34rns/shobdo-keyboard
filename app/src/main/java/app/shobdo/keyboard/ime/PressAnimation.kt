package app.shobdo.keyboard.ime

import kotlin.math.min

/**
 * The press/release animation of a single key, in milliseconds of uptime.
 *
 * Kept out of the view so the curve can be unit-tested: a key takes
 * [KeyboardMetrics.pressDownMs] to reach the pressed state with an ease-out curve (fast at
 * first, settling), and [KeyboardMetrics.pressUpMs] to return. No ripple, no overshoot.
 *
 * [progress] is 0 at rest and 1 fully pressed. The view turns it into a scale and a colour
 * blend, so this object allocates nothing and holds no Canvas state.
 */
class PressAnimation {

    private var downAt: Long = -1L
    private var upAt: Long = -1L
    private var releaseFrom: Float = 0f

    val isDown: Boolean get() = downAt >= 0L

    /** True while either direction of the animation still has frames left. */
    fun isAnimating(now: Long): Boolean {
        if (downAt < 0L) return false
        if (upAt < 0L) return elapsed(now, downAt) < KeyboardMetrics.pressDownMs
        return elapsed(now, upAt) < KeyboardMetrics.pressUpMs
    }

    /** Starts (or restarts) the press at [now]. */
    fun press(now: Long) {
        downAt = now
        upAt = -1L
        releaseFrom = 1f
    }

    /** Begins the return. Called on release, on cancel and when the finger slides off. */
    fun release(now: Long) {
        if (downAt < 0L || upAt >= 0L) return
        releaseFrom = progress(now)
        upAt = now
    }

    /** Forgets the press entirely, e.g. when the keyboard is detached. */
    fun reset() {
        downAt = -1L
        upAt = -1L
        releaseFrom = 0f
    }

    /** 0 at rest, 1 fully pressed, interpolated with an ease-out curve. */
    fun progress(now: Long): Float {
        if (downAt < 0L) return 0f
        val sinceDown = elapsed(now, downAt)
        if (upAt < 0L) {
            return easeOut(min(1f, sinceDown.toFloat() / KeyboardMetrics.pressDownMs))
        }
        val sinceUp = elapsed(now, upAt)
        if (sinceUp >= KeyboardMetrics.pressUpMs) return 0f
        val t = sinceUp.toFloat() / KeyboardMetrics.pressUpMs
        return releaseFrom * (1f - easeOut(t))
    }

    /** Scale of the key rect: 1 at rest, [KeyboardMetrics.pressScale] when fully pressed. */
    fun scale(now: Long, pressedScale: Float = KeyboardMetrics.pressScale): Float =
        1f - (1f - pressedScale) * progress(now)

    private fun elapsed(now: Long, from: Long): Long = (now - from).coerceAtLeast(0L)

    private fun easeOut(t: Float): Float {
        val inverse = 1f - t
        return 1f - inverse * inverse * inverse
    }
}
