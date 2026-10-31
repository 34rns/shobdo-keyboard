package app.shobdo.keyboard.ime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.SparseIntArray
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import app.shobdo.keyboard.settings.OneHanded
import app.shobdo.keyboard.theme.KeyboardTheme

/** Look-and-feel values the service copies out of settings so the view never touches storage. */
data class KeyboardStyle(
    val keyHeightDpPortrait: Float = KeyboardMetrics.letterRowHeight,
    val keyHeightDpLandscape: Float = 44f,
    val borders: Boolean = true,
    val popup: Boolean = true,
    val preview: Boolean = true,
    val haptic: Boolean = true,
    val hapticStrength: Int = 1,
    val sound: Boolean = false,
    val fontScale: Float = 1f,
    val oneHanded: OneHanded = OneHanded.OFF,
    val numberRow: Boolean = true,
    val shiftUsesAlternates: Boolean = false
)

/**
 * The whole keyboard: one custom View that draws every key on a Canvas and handles touch.
 *
 * There is no child view anywhere. A frame is one background draw, one strip, N cached key
 * bitmaps and N labels — no allocation, because the Paints, the key bitmaps and the icon
 * bitmaps are all created before the frame starts.
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onText(text: String)
        fun onSpace()
        fun onBackspace()
        fun onEnter()
        fun onSymbols()
        fun onCycleLanguage()
        fun onLanguagePicker()
        fun onSuggestion(index: Int, text: String)
        fun onShiftChanged(state: ShiftState)
        fun onStripAction(action: StripAction)
        fun onToggleCandidates()
    }

    var listener: Listener? = null

    private val geometry = KeyboardGeometry()
    private val icons = IconSet(context)
    private val painter = KeyPainter(icons)
    private val stripPainter = StripPainter(painter.box, icons)
    private val shift = ShiftStateMachine()
    private val repeater = BackspaceRepeater()
    private val feedback = KeyFeedback(context)
    private val press = PressAnimation()
    private val handler = Handler(Looper.getMainLooper())
    private val pointers = SparseIntArray()
    private val density = resources.displayMetrics.density

    private var layout: KeyboardLayout? = null
    private var theme: KeyboardTheme? = null
    private var language: Language = Language.ENGLISH
    private var style = KeyboardStyle()
    private var stripModel = StripModel()

    private var letterRowPx = 0f
    private var navInsetPx = 0
    private var panelBottom = 0f

    private var pressedIndex = -1
    private var pressedPointer = -1
    private var popupShowing = false
    private var popupSelected = 0
    private var popupChars: List<String> = emptyList()

    private var stripChip = -1
    private var stripExpand = false
    private var stripAction: StripAction? = null
    private var panelPressed = -1
    private var labelSwapAt = 0L

    init {
        isClickable = true
        isFocusable = false
        isHapticFeedbackEnabled = false
        val fonts = FontSet(context)
        painter.latinTypeface = fonts.latinRegular
        painter.banglaTypeface = fonts.banglaRegular
        painter.pixelTypeface = fonts.pixel
        applyFeedback()
    }

    // --- public API used by the service ----------------------------------------------

    fun setLayout(page: KeyboardLayout) {
        layout = page
        shift.reset()
        rebuild()
    }

    fun setTheme(keyboardTheme: KeyboardTheme) {
        if (theme?.id != keyboardTheme.id || theme != keyboardTheme) painter.clearCaches()
        theme = keyboardTheme
        rebuild()
    }

    fun setStyle(newStyle: KeyboardStyle) {
        style = newStyle
        applyFeedback()
        requestLayout()
        rebuild()
    }

    fun setLanguage(newLanguage: Language) {
        if (language != newLanguage) {
            language = newLanguage
            labelSwapAt = SystemClock.uptimeMillis()
            scheduleAnimation()
        }
        rebuild()
    }

    fun setStrip(model: StripModel) {
        stripModel = model
        requestLayout()
        rebuild()
    }

    fun armShiftOnce() {
        if (shift.state == ShiftState.OFF) {
            shift.shiftPressed(SystemClock.uptimeMillis())
            rebuild()
        }
    }

    val currentShift: ShiftState get() = shift.state

    // --- measure and layout ----------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val letterRowDp = if (landscape) style.keyHeightDpLandscape else style.keyHeightDpPortrait
        letterRowPx = letterRowDp * density
        painter.setSizes(density, letterRowPx, style.fontScale)

        val page = layout
        val visible = page?.let { visibleRows(it) }.orEmpty()
        val navInsetDp = navInsetPx / density
        val height = KeyboardMetrics.totalHeight(
            rowCount = visible.size,
            navInsetDp = navInsetDp,
            numberRowDp = letterRowPx * NUMBER_ROW_RATIO / density,
            letterRowDp = letterRowDp
        ) * density
        setMeasuredDimension(width, height.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    private fun visibleRows(page: KeyboardLayout): List<IndexedValue<List<KeyModel>>> =
        page.rows.withIndex().filter { style.numberRow || it.index != page.numberRowIndex }

    private fun rebuild() {
        val page = layout ?: return
        if (width == 0) return
        val visible = visibleRows(page)
        val heights = FloatArray(visible.size) { index ->
            if (visible[index].index == page.numberRowIndex) letterRowPx * NUMBER_ROW_RATIO else letterRowPx
        }
        val stripTop = KeyboardMetrics.topPadding * density
        val stripBottom = (KeyboardMetrics.topPadding + KeyboardMetrics.suggestionStripHeight) * density
        geometry.setStrip(0f, stripTop, width.toFloat(), stripBottom)
        geometry.layout(
            layout = page,
            width = width.toFloat(),
            top = KeyboardMetrics.firstRowTop() * density,
            rowHeights = heights,
            horizontalPadding = KeyboardMetrics.horizontalPadding * density,
            spacing = KeyboardMetrics.keyHorizontalGap * density,
            rowGap = KeyboardMetrics.rowVerticalGap * density,
            shift = shift.state,
            shiftUsesAlternates = style.shiftUsesAlternates,
            spaceLabel = language.displayName,
            oneHanded = style.oneHanded,
            hiddenRowIndexes = if (style.numberRow) emptySet() else setOfNotNull(page.numberRowIndex)
        )
        painter.numberRowIndex = page.numberRowIndex ?: -1
        stripPainter.layout(geometry.barRect, stripModel, density)
        panelBottom = 0f
        if (stripModel.expanded && stripModel.hasCandidates) {
            val top = geometry.barRect.bottom + KeyboardMetrics.gapBelowStrip * density / 2f
            val height = stripPainter.layoutPanel(
                geometry.barRect, top, width.toFloat(), stripModel.candidates.size, density,
                coverTo = this.height.toFloat() - navInsetPx - KeyboardMetrics.bottomPadding * density
            )
            panelBottom = top + height
        }
        invalidate()
    }

    // --- insets ----------------------------------------------------------------------

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val bottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }
        if (bottom != navInsetPx) {
            navInsetPx = bottom
            requestLayout()
        }
        return insets
    }

    // --- draw ------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val currentTheme = theme ?: return
        val now = SystemClock.uptimeMillis()
        painter.drawBackground(canvas, currentTheme, width.toFloat(), height.toFloat())
        stripPainter.draw(canvas, currentTheme, geometry.barRect, stripModel)
        painter.spaceSlide = labelSlide(now)
        painter.drawKeys(
            canvas = canvas,
            theme = currentTheme,
            keys = geometry.keys,
            activeIndex = pressedIndex,
            progress = if (popupShowing) 0f else press.progress(now),
            borders = style.borders,
            shift = shift.state
        )
        if (popupShowing && pressedIndex in geometry.keys.indices) {
            painter.drawPopup(
                canvas, currentTheme, geometry.keys[pressedIndex], popupChars, popupSelected, letterRowPx
            )
        } else if (pressedIndex in geometry.keys.indices && style.preview &&
            press.progress(now) > 0.5f
        ) {
            painter.drawPreview(canvas, currentTheme, geometry.keys[pressedIndex], letterRowPx)
        }
        if (stripModel.expanded) stripPainter.drawPanel(canvas, currentTheme, stripModel)
        // Nothing else would ask for the next frame, and a frozen label at half opacity
        // looks like a disabled key.
        if (labelSwapAt != 0L) postInvalidateOnAnimation()
    }

    private fun labelSlide(now: Long): Float {
        if (labelSwapAt == 0L) return 1f
        val elapsed = now - labelSwapAt
        if (elapsed >= KeyboardMetrics.labelSwapMs) {
            labelSwapAt = 0L
            return 1f
        }
        return (elapsed.toFloat() / KeyboardMetrics.labelSwapMs).coerceIn(0f, 1f)
    }

    // --- touch -----------------------------------------------------------------------

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                val y = event.y
                if (stripModel.expanded && y < panelBottom) {
                    panelPressed = stripPainter.panelAt(x, y)
                    return true
                }
                if (y <= geometry.barRect.bottom) {
                    stripChip = stripPainter.chipAt(x, y)
                    stripExpand = stripPainter.expandHit(x, y)
                    stripAction = stripPainter.actionAt(x, y)
                    return true
                }
                down(event.getPointerId(0), x, y)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                down(event.getPointerId(index), event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_MOVE -> move(event)
            MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                up(event.getPointerId(index))
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                if (panelPressed >= 0) {
                    val chosen = stripPainter.panelAt(event.x, event.y)
                    if (chosen == panelPressed) {
                        stripModel.candidates.getOrNull(chosen)?.let { listener?.onSuggestion(chosen, it) }
                    }
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
                listener?.onSpace()
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

    // --- animation -------------------------------------------------------------------

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

    private fun applyFeedback() {
        feedback.haptic = style.haptic
        feedback.strength = style.hapticStrength
        feedback.sound = style.sound
    }

    override fun onDetachedFromWindow() {
        cancelTimers()
        handler.removeCallbacks(animationTick)
        painter.clearCaches()
        super.onDetachedFromWindow()
    }

    companion object {
        const val LONG_PRESS_MS = 350L
        private const val FRAME_MS = 16L
        private const val NUMBER_ROW_RATIO = 0.88f
    }
}
