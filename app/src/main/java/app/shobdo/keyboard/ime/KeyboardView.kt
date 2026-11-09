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
import android.view.animation.DecelerateInterpolator
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
    private val touch = KeyboardTouch(this, geometry, stripPainter, shift, repeater, feedback, press, handler)
    private val density = resources.displayMetrics.density

    private var layout: KeyboardLayout? = null
    private var theme: KeyboardTheme? = null
    private var language: Language = Language.ENGLISH
    internal var style = KeyboardStyle()
    internal var stripModel = StripModel()

    internal var letterRowPx = 0f
    private var navInsetPx = 0
    private var panelBottom = 0f


    private var labelSwapAt = 0L
    private val labelEasing = DecelerateInterpolator()

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
            removeCallbacks(labelTick)
            postOnAnimation(labelTick)
        }
        rebuild()
    }

    fun setStrip(model: StripModel) {
        if (stripModel == model) return
        stripModel = model
        layoutStrip()
        invalidate()
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

    internal fun rebuild() {
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
        theme?.let { painter.prepare(it, geometry.keys, width.toFloat(), height.toFloat()) }
        layoutStrip()
        invalidate()
    }

    private fun layoutStrip() {
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
            activeIndex = touch.pressedIndex,
            progress = if (touch.popupShowing) 0f else press.progress(now),
            borders = style.borders,
            shift = shift.state
        )
        if (touch.popupShowing && touch.pressedIndex in geometry.keys.indices) {
            painter.drawPopup(
                canvas, currentTheme, geometry.keys[touch.pressedIndex], touch.popupChars, touch.popupSelected, letterRowPx
            )
        } else if (touch.pressedIndex in geometry.keys.indices && style.preview &&
            press.progress(now) > 0.5f
        ) {
            painter.drawPreview(canvas, currentTheme, geometry.keys[touch.pressedIndex], letterRowPx)
        }
        if (stripModel.expanded) stripPainter.drawPanel(canvas, currentTheme, stripModel)
        // Nothing else would ask for the next frame, and a frozen label at half opacity
        // looks like a disabled key.
    }

    private fun labelSlide(now: Long): Float {
        if (labelSwapAt == 0L) return 1f
        val elapsed = now - labelSwapAt
        if (elapsed >= KeyboardMetrics.labelSwapMs) {
            labelSwapAt = 0L
            return 1f
        }
        return labelEasing.getInterpolation((elapsed.toFloat() / KeyboardMetrics.labelSwapMs).coerceIn(0f, 1f))
    }

    // --- touch -----------------------------------------------------------------------

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = touch.onTouchEvent(event)

    // --- animation -------------------------------------------------------------------

    private val labelTick = object : Runnable {
        override fun run() {
            val progress = labelSlide(SystemClock.uptimeMillis())
            for (index in geometry.keys.indices) {
                val key = geometry.keys[index]
                if (key.key.type == KeyType.SPACE) {
                    invalidate(key.rect.left.toInt(), key.rect.top.toInt(), key.rect.right.toInt() + 1, key.rect.bottom.toInt() + 1)
                    break
                }
            }
            if (progress < 1f) postOnAnimation(this)
        }
    }

    private fun applyFeedback() {
        feedback.haptic = style.haptic
        feedback.strength = style.hapticStrength
        feedback.sound = style.sound
    }

    override fun onDetachedFromWindow() {
        touch.detach()
        removeCallbacks(labelTick)
        painter.clearCaches()
        super.onDetachedFromWindow()
    }

    companion object {
        const val LONG_PRESS_MS = 350L
        private const val FRAME_MS = 16L
        private const val NUMBER_ROW_RATIO = 0.88f
    }
}
