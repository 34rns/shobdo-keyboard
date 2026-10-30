package app.shobdo.keyboard.ime

import android.graphics.Canvas
import android.graphics.Paint

/**
 * The Paints the whole keyboard shares.
 *
 * One instance is created with the view. Every draw call only sets a colour or a size, so a
 * frame allocates no Paint, no RectF and no String. Text is centred with font metrics, not
 * with an eyeballed offset.
 */
class PaintBox(var density: Float = 2.5f) {

    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    val shade = Paint()
    val metrics = Paint.FontMetrics()

    /** Baseline that puts [value]'s optical centre on [centerY]. */
    fun baselineFor(centerY: Float): Float {
        text.getFontMetrics(metrics)
        return centerY - (metrics.ascent + metrics.descent) / 2f
    }

    fun drawCentred(canvas: Canvas, value: String, centerX: Float, centerY: Float) {
        if (value.isEmpty()) return
        canvas.drawText(value, centerX, baselineFor(centerY), text)
    }

    /** Draws [value] inside [left]..[right], shrinking only when it would overflow. */
    fun drawFitted(canvas: Canvas, value: String, left: Float, right: Float, centerY: Float, size: Float) {
        if (value.isEmpty()) return
        text.textSize = size
        val width = text.measureText(value)
        val room = right - left
        if (width > room && width > 0f) text.textSize = size * (room / width)
        drawCentred(canvas, value, (left + right) / 2f, centerY)
        text.textSize = size
        Unit
    }
}
