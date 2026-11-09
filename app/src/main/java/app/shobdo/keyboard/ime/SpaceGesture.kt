package app.shobdo.keyboard.ime

/** One switch at the threshold, never a space on release after a swipe. */
class SpaceGesture {
    private var startX = 0f
    var switched = false
        private set

    fun start(x: Float) { startX = x; switched = false }
    fun move(x: Float, threshold: Float): Boolean {
        if (switched || kotlin.math.abs(x - startX) < threshold) return false
        switched = true
        return true
    }
}
