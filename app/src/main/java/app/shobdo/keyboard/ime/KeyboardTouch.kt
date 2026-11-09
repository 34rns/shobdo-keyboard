package app.shobdo.keyboard.ime

import android.os.Handler
import android.os.SystemClock
import android.util.SparseIntArray
import android.view.MotionEvent

/** Touch ownership stays separate from Canvas rendering; overlays capture whole gestures. */
internal class KeyboardTouch(
    private val view: KeyboardView,
    private val geometry: KeyboardGeometry,
    private val stripPainter: StripPainter,
    private val shift: ShiftStateMachine,
    private val repeater: BackspaceRepeater,
    private val feedback: KeyFeedback,
    private val press: PressAnimation,
    private val handler: Handler
) {
    private val pointers = SparseIntArray()
    private val density get() = view.resources.displayMetrics.density
    private val listener get() = view.listener
    private val stripModel get() = view.stripModel
    private val style get() = view.style
    private val letterRowPx get() = view.letterRowPx
    private fun invalidate() = view.invalidate()
    private fun rebuild() = view.rebuild()
    private fun postOnAnimation(runnable: Runnable) = view.postOnAnimation(runnable)
    var pressedIndex = -1
    private var pressedPointer = -1
    var popupShowing = false
    var popupSelected = 0
    var popupChars: List<String> = emptyList()
    private var stripChip = -1
    private var stripExpand = false
    private var stripAction: StripAction? = null
    private var panelPressed = -1
    private var panelTouch = false
    private var panelDownY = 0f
    private var panelLastY = 0f
    private var panelScrolled = false
    private var spacePointer = -1
    private val spaceGesture = SpaceGesture()

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                val y = event.y
                if (y <= geometry.barRect.bottom) {
                    stripChip = stripPainter.chipAt(x, y)
                    stripExpand = stripPainter.expandHit(x, y)
                    stripAction = stripPainter.actionAt(x, y)
                    return true
                }
                if (stripModel.expanded) {
                    panelTouch = true
                    panelDownY = y
                    panelLastY = y
                    panelScrolled = false
                    panelPressed = stripPainter.panelAt(x, y)
                    return true
                }
                down(event.getPointerId(0), x, y)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (panelTouch || stripExpand || stripChip >= 0 || stripModel.expanded) return true
                val index = event.actionIndex
                down(event.getPointerId(index), event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_MOVE -> {
                if (panelTouch) {
                    if (kotlin.math.abs(event.y - panelDownY) > 8f * density) panelScrolled = true
                    if (panelScrolled) {
                        stripPainter.scrollPanel(panelLastY - event.y)
                        panelPressed = -1
                        invalidate()
                    }
                    panelLastY = event.y
                } else if (!stripModel.expanded) move(event)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (panelTouch || stripModel.expanded) return true
                val index = event.actionIndex
                up(event.getPointerId(index))
            }
            MotionEvent.ACTION_UP -> {
                if (panelTouch) {
                    val chosen = stripPainter.panelAt(event.x, event.y)
                    if (!panelScrolled && chosen >= 0 && chosen == panelPressed) {
                        stripModel.candidates.getOrNull(chosen)?.let { listener?.onSuggestion(chosen, it) }
                    } else if (!panelScrolled && !stripPainter.panelRect.contains(event.x, event.y)) {
                        listener?.onToggleCandidates()
                    }
                    panelTouch = false
                    panelPressed = -1
                    return true
                }
                if (stripChip >= 0 || stripExpand || stripAction != null) {
                    handleStripRelease(event.x, event.y)
                    return true
                }
                up(event.getPointerId(0))
            }
            MotionEvent.ACTION_CANCEL -> cancelTouch()
        }
        return true
    }

    private fun handleStripRelease(x: Float, y: Float) {
        if (stripExpand && stripPainter.expandHit(x, y)) {
            listener?.onToggleCandidates()
        } else if (stripAction != null && stripPainter.actionAt(x, y) == stripAction) {
            stripAction?.let { listener?.onStripAction(it) }
        } else if (stripChip >= 0) {
            val chosen = stripPainter.chipAt(x, y)
            if (chosen == stripChip) {
                stripModel.candidates.getOrNull(chosen)?.let { listener?.onSuggestion(chosen, it) }
            }
        }
        stripChip = -1
        stripExpand = false
        stripAction = null
    }

    private fun down(pointerId: Int, x: Float, y: Float) {
        val index = geometry.findKey(x, y)
        pointers.put(pointerId, index)
        if (index < 0) return
        if (pressedIndex >= 0) return
        pressedIndex = index
        pressedPointer = pointerId
        press.press(SystemClock.uptimeMillis())
        val key = geometry.keys[index].key
        if (key.type == KeyType.SPACE) { spaceGesture.start(x); spacePointer = pointerId }
        if (key.type == KeyType.BACKSPACE) {
            listener?.onBackspace()
            feedback.key(isDelete = true)
            repeater.press(SystemClock.uptimeMillis())
            handler.postDelayed(repeatRunnable, BackspaceRepeater.INTERVAL_MS)
        } else {
            feedback.key()
        }
        if (key.hasLongPress || key.type == KeyType.GLOBE) {
            handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
        }
        scheduleAnimation()
    }

    private fun move(event: MotionEvent) {
        for (i in 0 until event.pointerCount) {
            val id = event.getPointerId(i)
            if (id == spacePointer && spaceGesture.switched) continue
            val index = pointers.get(id, -1)
            if (index < 0 || index !in geometry.keys.indices) continue
            if (popupShowing && id == pressedPointer) {
                val rect = geometry.keys[index].rect
                val count = popupChars.size
                if (count > 0) {
                    val cell = letterRowPx * 0.96f
                    val left = (rect.centerX() - (cell * count + 8f) / 2f)
                        .coerceAtLeast(4f * density)
                    popupSelected = (((event.getX(i) - left) / cell).toInt()).coerceIn(0, count - 1)
                    invalidate()
                }
                continue
            }
            val rect = geometry.keys[index].rect
            if (geometry.keys[index].key.type == KeyType.SPACE && id == pressedPointer) {
                if (spaceGesture.move(event.getX(i), 36f * density)) {
                    cancelTimers()
                    press.reset()
                    view.removeCallbacks(animationTick)
                    pressedIndex = -1
                    listener?.onCycleLanguage()
                }
                if (spaceGesture.switched) continue
            }
            val slop = 12f * density
            if (event.getX(i) < rect.left - slop || event.getX(i) > rect.right + slop ||
                event.getY(i) < geometry.barRect.bottom - slop || event.getY(i) > rect.bottom + slop * 3
            ) {
                pointers.put(id, -1)
                if (id == pressedPointer) cancelTouch()
            }
        }
    }

    private fun up(pointerId: Int) {
        if (pointerId == spacePointer && spaceGesture.switched) {
            spacePointer = -1
            pointers.delete(pointerId)
            cancelTimers()
            pressedPointer = -1
            return
        }
        if (pointerId == spacePointer) spacePointer = -1
        val index = pointers.get(pointerId, -1)
        pointers.delete(pointerId)
        if (popupShowing && pointerId == pressedPointer) {
            val alternate = popupChars.getOrNull(popupSelected)
            popupShowing = false
            popupChars = emptyList()
            cancelTimers()
            releasePress()
            if (alternate != null) {
                listener?.onText(alternate)
                consumeShiftOnce()
            }
            return
        }
        if (index in geometry.keys.indices) commit(geometry.keys[index])
        cancelTimers()
        releasePress()
    }

    private fun releasePress() {
        press.release(SystemClock.uptimeMillis())
        pressedPointer = -1
        scheduleAnimation()
    }

    private fun commit(placed: PlacedKey) {
        val now = SystemClock.uptimeMillis()
        when (placed.key.type) {
            KeyType.SHIFT -> {
                val state = shift.shiftPressed(now)
                listener?.onShiftChanged(state)
                rebuild()
            }
            KeyType.BACKSPACE -> Unit
            KeyType.ENTER -> listener?.onEnter()
            KeyType.SPACE -> {
                if (!spaceGesture.switched) listener?.onSpace()
                consumeShiftOnce()
            }
            KeyType.SYMBOLS -> listener?.onSymbols()
            KeyType.GLOBE -> listener?.onCycleLanguage()
            KeyType.EMOJI -> Unit
            KeyType.COMMA, KeyType.PERIOD, KeyType.NORMAL -> {
                listener?.onText(placed.code)
                consumeShiftOnce()
            }
        }
    }

    private fun consumeShiftOnce() {
        if (shift.state == ShiftState.ONCE) {
            shift.characterTyped()
            listener?.onShiftChanged(shift.state)
            rebuild()
        }
    }

    private fun cancelTouch() {
        panelTouch = false
        panelPressed = -1
        stripChip = -1
        stripExpand = false
        stripAction = null
        spacePointer = -1
        cancelTimers()
        pointers.clear()
        press.reset()
        pressedIndex = -1
        pressedPointer = -1
        popupShowing = false
        popupChars = emptyList()
        invalidate()
    }

    private fun cancelTimers() {
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(repeatRunnable)
        repeater.release()
    }

    private val animationTick = object : Runnable {
        override fun run() {
            if (press.isAnimating(SystemClock.uptimeMillis())) {
                invalidate()
                handler.postDelayed(this, FRAME_MS)
            } else {
                pressedIndex = -1
                invalidate()
            }
        }
    }

    private fun scheduleAnimation() {
        handler.removeCallbacks(animationTick)
        postOnAnimation(animationTick)
    }

    private val longPressRunnable = Runnable {
        val index = pressedIndex
        if (index !in geometry.keys.indices) return@Runnable
        val key = geometry.keys[index].key
        if (key.type == KeyType.GLOBE) {
            cancelTimers()
            press.reset()
            pressedIndex = -1
            invalidate()
            listener?.onLanguagePicker()
            return@Runnable
        }
        if (key.hasLongPress) {
            popupShowing = true
            popupChars = key.longPressChars
            popupSelected = 0
            feedback.longPress()
            invalidate()
        }
    }

    private val repeatRunnable = object : Runnable {
        override fun run() {
            val due = repeater.due(SystemClock.uptimeMillis())
            var count = 0
            while (count < due) {
                listener?.onBackspace()
                count++
            }
            if (repeater.isHolding) handler.postDelayed(this, BackspaceRepeater.INTERVAL_MS)
        }
    }

    fun detach() { cancelTouch(); handler.removeCallbacks(animationTick) }
    companion object {
        private const val LONG_PRESS_MS = 350L
        private const val FRAME_MS = 16L
    }
}
