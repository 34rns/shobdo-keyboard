package app.shobdo.keyboard.ime

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import app.shobdo.keyboard.theme.KeyboardTheme

/** The quick actions on the right of the strip. */
enum class StripAction { THEME, ONE_HANDED, SETTINGS, COLLAPSE, EXPAND }

/** What the suggestion strip shows. Immutable so the view can compare and skip a relayout. */
data class StripModel(
    val word: String = "",
    val candidates: List<String> = emptyList(),
    val primary: Int = 0,
    val expanded: Boolean = false
) {
    val hasCandidates: Boolean get() = candidates.isNotEmpty()
}

/**
 * The strip above the keys: the word being typed plus quick actions, or Banglish candidates
 * plus an expand arrow.
 *
 * The strip is always present, in every language, which is what gives the keyboard its
 * breathing room at the top. Rects are computed once per content change (see [layout]) and
 * only read while drawing.
 */
class StripPainter(private val box: PaintBox, private val icons: IconSet) {

    val chipRects = ArrayList<RectF>(8)
    val panelRects = ArrayList<RectF>(40)
    val panelRect = RectF()
    private val actionRects = LinkedHashMap<StripAction, RectF>()

    private val expandRect = RectF()
    private val wordRect = RectF()

    private var lastDensity = 0f

    /** Lays out the strip for [model]. Cheap; called on content change, not per frame. */
    fun layout(strip: RectF, model: StripModel, density: Float) {
        lastDensity = density
        chipRects.clear()
        actionRects.clear()
        expandRect.setEmpty()
        wordRect.set(strip)
        if (strip.isEmpty) return

        val inset = 6f * density
        val top = strip.top + 4f * density
        val bottom = strip.bottom - 4f * density
        val primarySize = 15f * density
        box.text.textSize = primarySize

        if (model.hasCandidates) {
            val arrowWidth = 26f * density
            expandRect.set(strip.right - inset - arrowWidth, top, strip.right - inset, bottom)
            var x = strip.left + inset
            val limit = expandRect.left - inset
            val maxChip = (strip.width() - arrowWidth - inset * 3) * 0.55f
            for (candidate in model.candidates) {
                val width = (box.text.measureText(candidate) + 20f * density).coerceAtMost(maxChip)
                if (x + width > limit) break
                chipRects.add(RectF(x, top, x + width, bottom))
                x += width + 2f * density
            }
            return
        }

        // No candidates: the word on the left, quick actions on the right.
        val actionSize = 34f * density
        var right = strip.right - inset
        for (action in ACTIONS) {
            actionRects[action] = RectF(right - actionSize, strip.top, right, strip.bottom)
            right -= actionSize
        }
        wordRect.set(strip.left + inset * 1.5f, top, right - inset, bottom)
    }

    fun draw(canvas: Canvas, theme: KeyboardTheme, strip: RectF, model: StripModel) {
        if (strip.isEmpty) return
        box.fill.color = theme.barBackground
        canvas.drawRect(strip, box.fill)

        if (model.hasCandidates) {
            drawChips(canvas, theme, model)
            drawExpandArrow(canvas, theme, model.expanded)
            return
        }
        drawWord(canvas, theme, model.word)
        for ((action, rect) in actionRects) {
            icons.draw(canvas, iconFor(action), rect.centerX(), rect.centerY(), 20f * lastDensity, theme.barText)
        }
    }

    private fun drawWord(canvas: Canvas, theme: KeyboardTheme, word: String) {
        if (word.isEmpty()) return
        box.text.typeface = Typeface.DEFAULT
        box.text.isFakeBoldText = false
        box.text.color = theme.barText
        box.text.textSize = 16f * lastDensity
        val baseline = box.baselineFor(wordRect.centerY())
        canvas.drawText(word, wordRect.left, baseline, box.text)
    }

    private fun drawChips(canvas: Canvas, theme: KeyboardTheme, model: StripModel) {
        box.text.typeface = Typeface.DEFAULT
        box.text.isFakeBoldText = false
        for (index in chipRects.indices) {
            val rect = chipRects[index]
            val primary = index == model.primary
            if (primary) {
                box.fill.color = theme.accent
                canvas.drawRoundRect(rect, 7f * lastDensity, 7f * lastDensity, box.fill)
                box.text.color = if (isLight(theme.accent)) Color.BLACK else Color.WHITE
            } else {
                box.text.color = theme.barText
            }
            box.text.textSize = 15f * lastDensity
            box.drawFitted(canvas, model.candidates[index], rect.left + 4f * lastDensity, rect.right - 4f * lastDensity, rect.centerY(), 15f * lastDensity)
        }
    }

    private fun drawExpandArrow(canvas: Canvas, theme: KeyboardTheme, expanded: Boolean) {
        val rect = expandRect
        if (rect.isEmpty) return
        box.stroke.color = theme.barText
        box.stroke.strokeWidth = 1.5f * lastDensity
        box.stroke.strokeCap = Paint.Cap.ROUND
        val cx = rect.centerX()
        val cy = rect.centerY()
        val half = 4f * lastDensity
        val lift = 2.4f * lastDensity
        if (expanded) {
            canvas.drawLine(cx - half, cy + lift, cx, cy - lift, box.stroke)
            canvas.drawLine(cx, cy - lift, cx + half, cy + lift, box.stroke)
        } else {
            canvas.drawLine(cx - half, cy - lift, cx, cy + lift, box.stroke)
            canvas.drawLine(cx, cy + lift, cx + half, cy - lift, box.stroke)
        }
        box.stroke.strokeCap = Paint.Cap.BUTT
    }

    /** Lays out the expanded candidate grid. Returns the height it needs. */
    fun layoutPanel(
        strip: RectF,
        top: Float,
        width: Float,
        count: Int,
        density: Float,
        coverTo: Float = 0f
    ): Float {
        panelRects.clear()
        if (count <= 0) {
            panelRect.setEmpty()
            return 0f
        }
        val cellHeight = 34f * density
        val rows = ((count + PANEL_COLUMNS - 1) / PANEL_COLUMNS).coerceAtMost(PANEL_MAX_ROWS)
        val height = rows * cellHeight + 8f * density
        // The grid only needs a few rows, but a sheet that stops in the middle of a key row
        // looks broken. It is drawn down to [coverTo] so the keys never peek out below it.
        panelRect.set(0f, top, width, top + maxOf(height, coverTo - top))
        val cellWidth = width / PANEL_COLUMNS
        for (index in 0 until minOf(count, PANEL_COLUMNS * PANEL_MAX_ROWS)) {
            val column = index % PANEL_COLUMNS
            val row = index / PANEL_COLUMNS
            val left = column * cellWidth
            val cellTop = top + 4f * density + row * cellHeight
            panelRects.add(RectF(left + 2f * density, cellTop, left + cellWidth - 2f * density, cellTop + cellHeight))
        }
        return height
    }

    fun drawPanel(canvas: Canvas, theme: KeyboardTheme, model: StripModel) {
        if (panelRect.isEmpty) return
        box.fill.color = theme.barBackground
        canvas.drawRect(panelRect, box.fill)
        box.text.typeface = Typeface.DEFAULT
        box.text.isFakeBoldText = false
        for (index in panelRects.indices) {
            if (index >= model.candidates.size) break
            val rect = panelRects[index]
            if (index == model.primary) {
                box.fill.color = theme.accent
                canvas.drawRoundRect(rect, 6f * lastDensity, 6f * lastDensity, box.fill)
                box.text.color = if (isLight(theme.accent)) Color.BLACK else Color.WHITE
            } else {
                box.text.color = theme.barText
            }
            box.text.textSize = 15f * lastDensity
            box.drawFitted(canvas, model.candidates[index], rect.left + 3f * lastDensity, rect.right - 3f * lastDensity, rect.centerY(), 15f * lastDensity)
        }
    }

    fun chipAt(x: Float, y: Float): Int {
        for (index in chipRects.indices) if (chipRects[index].contains(x, y)) return index
        return -1
    }

    fun panelAt(x: Float, y: Float): Int {
        for (index in panelRects.indices) if (panelRects[index].contains(x, y)) return index
        return -1
    }

    fun expandHit(x: Float, y: Float): Boolean = expandRect.contains(x, y)

    fun actionAt(x: Float, y: Float): StripAction? {
        for ((action, rect) in actionRects) if (rect.contains(x, y)) return action
        return null
    }

    private fun iconFor(action: StripAction): Int = when (action) {
        StripAction.THEME -> IconSet.THEME
        StripAction.ONE_HANDED -> IconSet.ONE_HANDED
        StripAction.SETTINGS -> IconSet.SETTINGS
        StripAction.COLLAPSE -> IconSet.COLLAPSE
        StripAction.EXPAND -> IconSet.COLLAPSE
    }

    private fun isLight(color: Int): Boolean =
        0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color) > 160

    companion object {
        val ACTIONS = listOf(StripAction.THEME, StripAction.ONE_HANDED, StripAction.SETTINGS, StripAction.COLLAPSE)
        const val PANEL_COLUMNS = 4
        const val PANEL_MAX_ROWS = 10
    }
}
