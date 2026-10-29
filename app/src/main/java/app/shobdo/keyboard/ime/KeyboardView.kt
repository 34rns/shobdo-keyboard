package app.shobdo.keyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.SparseIntArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import app.shobdo.keyboard.settings.OneHanded
import app.shobdo.keyboard.theme.KeyboardTheme

/** Look-and-feel values the service copies out of settings so the view never touches storage. */
data class KeyboardStyle(
    val keyHeightDpPortrait: Float = 54f,
    val keyHeightDpLandscape: Float = 42f,
    val keySpacingDp: Float = 6f,
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
 * There is no child view anywhere, which is what keeps fast typing cheap — a frame is one
 * background draw, N rounded rectangles, N labels and a suggestion bar, all with reused
 * Paint objects.
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
    }

    var listener: Listener? = null

    private val geometry = KeyboardGeometry()
    private val painter = KeyPainter()
    private val shift = ShiftStateMachine()
    private val repeater = BackspaceRepeater()
    private val handler = Handler(Looper.getMainLooper())
    private val pointers = SparseIntArray()

    private var layout: KeyboardLayout? = null
    private var theme: KeyboardTheme? = null
    private var language: Language = Language.ENGLISH
    private var style = KeyboardStyle()
    private var suggestions: List<String> = emptyList()
    private var suggestionPrimary: Int = 0

    private var keyHeightPx = 0f
    private var keySpacingPx = 0f
    private var barHeightPx = 0f
    private var navInsetPx = 0

    private var pressedIndex = -1
    private var pressedPointer = -1
    private var popupShowing = false
    private var popupSelected = 0
    private var popupChars: List<String> = emptyList()
    private var chipPressed = -1

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(VibratorManager::class.java))?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    }

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    init {
        painter.density = resources.displayMetrics.density
        isClickable = true
        isFocusable = false
        isHapticFeedbackEnabled = false
        keySpacingPx = style.keySpacingDp * resources.displayMetrics.density
    }

    // --- public API used by the service ----------------------------------------------

    fun setLayout(page: KeyboardLayout) {
        layout = page
        shift.reset()
        rebuild()
    }

    fun setTheme(keyboardTheme: KeyboardTheme) {
        theme = keyboardTheme
        rebuild()
    }

    fun setStyle(newStyle: KeyboardStyle) {
        val needsRelayout = newStyle.oneHanded != style.oneHanded ||
            newStyle.borders != style.borders ||
            newStyle.shiftUsesAlternates != style.shiftUsesAlternates ||
            newStyle.numberRow != style.numberRow
        style = newStyle
        keySpacingPx = newStyle.keySpacingDp * resources.displayMetrics.density
        if (needsRelayout) {
            requestLayout()
        }
        rebuild()
    }

    fun setLanguage(newLanguage: Language) {
        language = newLanguage
        rebuild()
    }

    fun setSuggestions(candidates: List<String>, primaryIndex: Int) {
        suggestions = candidates
        suggestionPrimary = primaryIndex
        requestLayout()
        rebuild()
    }

    fun armShiftOnce() {
        if (shift.state == ShiftState.OFF) {
            shift.shiftPressed(SystemClock.uptimeMillis())
            rebuild()
        }
    }

    fun setShiftLocked(locked: Boolean) {
        shift.reset()
        if (locked) shift.shiftPressed(SystemClock.uptimeMillis())
        rebuild()
    }

    fun clearShift() {
        if (shift.state != ShiftState.OFF) {
            shift.reset()
            rebuild()
        }
    }

    val currentShift: ShiftState get() = shift.state

    /** Bangla and Banglish pages draw Bangla; used to pick the number row and shift behaviour. */
    fun usesNumberRow(page: KeyboardLayout): Boolean = style.numberRow || page.numberRowIndex == null

    // --- measure and layout ----------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val landscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val desiredDp = if (landscape) style.keyHeightDpLandscape else style.keyHeightDpPortrait
        keyHeightPx = desiredDp * resources.displayMetrics.density
        keySpacingPx = style.keySpacingDp * resources.displayMetrics.density

        val page = layout
        val rows = page?.let { visibleRowCount(it) } ?: 0
        barHeightPx = if (suggestions.isEmpty()) 0f else BAR_HEIGHT_DP * resources.displayMetrics.density
        val height = (barHeightPx + rows * keyHeightPx + (rows - 1).coerceAtLeast(0) * keySpacingPx + navInsetPx).toInt()
        painter.setTextSize(keyHeightPx, style.fontScale)
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    private fun visibleRowCount(page: KeyboardLayout): Int =
        page.rows.size - if (!style.numberRow && page.numberRowIndex != null) 1 else 0

    private fun hiddenRows(page: KeyboardLayout): Set<Int> =
        if (!style.numberRow && page.numberRowIndex != null) setOf(page.numberRowIndex) else emptySet()

    private fun rebuild() {
        val page = layout ?: return
        if (width == 0) return
        val density = resources.displayMetrics.density
        val top = barHeightPx
        geometry.layout(
            layout = page,
            width = width.toFloat(),
            keyHeight = keyHeightPx,
            horizontalPadding = 3f * density,
            spacing = keySpacingPx,
            top = top,
            barHeight = barHeightPx,
            shift = shift.state,
            shiftUsesAlternates = style.shiftUsesAlternates,
            spaceLabel = language.displayName,
            oneHanded = style.oneHanded,
            hiddenRowIndexes = hiddenRows(page)
        )
        painter.layoutSuggestions(geometry.barRect, suggestions, suggestionPrimary)
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
        painter.drawBackground(canvas, currentTheme, width.toFloat(), height.toFloat())
        painter.drawSuggestionBar(canvas, currentTheme, geometry.barRect, suggestions, suggestionPrimary)
        painter.drawKeys(canvas, currentTheme, geometry.keys, if (popupShowing) -1 else pressedIndex, style.borders)
        if (popupShowing && pressedIndex in geometry.keys.indices) {
            painter.drawPopup(canvas, currentTheme, geometry.keys[pressedIndex], popupChars, popupSelected, keyHeightPx)
        } else if (pressedIndex in geometry.keys.indices && style.preview) {
            painter.drawPreview(canvas, currentTheme, geometry.keys[pressedIndex], keyHeightPx)
        }
    }

    // --- touch -----------------------------------------------------------------------

    /** Announced to accessibility services when a key is released. */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                val y = event.y
                if (!geometry.barRect.isEmpty && y <= geometry.barRect.bottom) {
                    chipPressed = painter.chipAt(x, y)
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
                up(event.getPointerId(index), event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                if (chipPressed >= 0) {
                    val chosen = painter.chipAt(event.x, event.y)
                    if (chosen >= 0 && chosen == chipPressed) {
                        suggestions.getOrNull(chosen)?.let { listener?.onSuggestion(chosen, it) }
                    }
                    chipPressed = -1
                    return true
                }
                up(event.getPointerId(0), event.x, event.y)
            }
            MotionEvent.ACTION_CANCEL -> cancelTouch()
        }
        return true
    }

    private fun down(pointerId: Int, x: Float, y: Float) {
        val index = geometry.findKey(x, y)
        pointers.put(pointerId, index)
        if (index < 0) return
        if (pressedIndex < 0) {
            pressedIndex = index
            pressedPointer = pointerId
            val key = geometry.keys[index].key
            if (key.type == KeyType.BACKSPACE) {
                listener?.onBackspace()
                feedback(isDelete = true)
                repeater.press(SystemClock.uptimeMillis())
                handler.postDelayed(repeatRunnable, BackspaceRepeater.INTERVAL_MS)
            } else {
                feedback(isDelete = false)
            }
            if (key.hasLongPress || key.type == KeyType.GLOBE) {
                handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
            }
            invalidate()
        }
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
                    val cell = keyHeightPx * 0.96f
                    val left = (rect.centerX() - (cell * count + 8f) / 2f)
                        .coerceAtLeast(4f * resources.displayMetrics.density)
                    val offset = ((event.getX(i) - left) / cell).toInt()
                    popupSelected = offset.coerceIn(0, count - 1)
                    invalidate()
                }
                continue
            }
            val rect = geometry.keys[index].rect
            val slop = 12f * resources.displayMetrics.density
            if (event.getX(i) < rect.left - slop || event.getX(i) > rect.right + slop ||
                event.getY(i) < geometry.barRect.bottom - slop || event.getY(i) > rect.bottom + slop * 3
            ) {
                pointers.put(id, -1)
                if (id == pressedPointer) cancelTouch()
            }
        }
    }

    private fun up(pointerId: Int, x: Float, y: Float) {
        val index = pointers.get(pointerId, -1)
        pointers.delete(pointerId)
        if (popupShowing && pointerId == pressedPointer) {
            val alternate = popupChars.getOrNull(popupSelected)
            popupShowing = false
            popupChars = emptyList()
            cancelTimers()
            pressedIndex = -1
            pressedPointer = -1
            if (alternate != null) {
                listener?.onText(alternate)
                if (shift.state == ShiftState.ONCE) shift.characterTyped()
            }
            invalidate()
            return
        }
        if (index in geometry.keys.indices) commit(geometry.keys[index])
        cancelTimers()
        pressedIndex = -1
        pressedPointer = -1
        invalidate()
    }

    private fun commit(placed: PlacedKey) {
        val now = SystemClock.uptimeMillis()
        when (placed.key.type) {
            KeyType.SHIFT -> {
                val state = shift.shiftPressed(now)
                listener?.onShiftChanged(state)
                rebuild()
            }
            KeyType.BACKSPACE -> Unit // handled on press and by the repeat runnable
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
        repeater.release()
        pointers.clear()
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

    private val longPressRunnable = Runnable {
        val index = pressedIndex
        if (index !in geometry.keys.indices) return@Runnable
        val key = geometry.keys[index].key
        if (key.type == KeyType.GLOBE) {
            cancelTimers()
            pressedIndex = -1
            invalidate()
            listener?.onLanguagePicker()
            return@Runnable
        }
        if (key.hasLongPress) {
            popupShowing = true
            popupChars = key.longPressChars
            popupSelected = 0
            vv()
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

    private fun feedback(isDelete: Boolean) {
        if (style.haptic && (isDelete || pressedIndex >= 0)) vv()
        if (style.sound) {
            audioManager?.playSoundEffect(
                if (isDelete) AudioManager.FX_KEYPRESS_DELETE else AudioManager.FX_KEYPRESS_STANDARD,
                0.6f
            )
        }
    }

    private fun vv() {
        if (!style.haptic) return
        val vibratorService = vibrator ?: return
        if (!vibratorService.hasVibrator()) return
        val duration = when (style.hapticStrength) {
            0 -> 8L
            2 -> 22L
            else -> 14L
        }
        val amplitude = when (style.hapticStrength) {
            0 -> 70
            2 -> 200
            else -> 130
        }
        runCatching {
            vibratorService.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        }
    }

    /** Exposed for the service, which uses the platform's light tap on onboarding. */
    fun tapFeedback() {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    override fun onDetachedFromWindow() {
        cancelTimers()
        super.onDetachedFromWindow()
    }

    companion object {
        const val BAR_HEIGHT_DP = 40f
        const val LONG_PRESS_MS = 350L
    }
}
