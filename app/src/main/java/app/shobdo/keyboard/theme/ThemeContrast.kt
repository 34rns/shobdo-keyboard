package app.shobdo.keyboard.theme

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG contrast, computed the way the keyboard actually draws: the (usually translucent) key
 * fill is composited over the background first, and the text is measured against that.
 *
 * This mirrors scripts/validate-themes.py, which is what the shipped theme files were checked
 * with, so the unit test and the script agree on every theme.
 */
object ThemeContrast {

    const val MIN_CONTRAST = 4.5

    /** The solid colours a background can show. Every gradient stop counts, worst wins. */
    fun backgroundStops(background: ThemeBackground): List<Int> = when (background) {
        is ThemeBackground.Solid -> listOf(background.color)
        is ThemeBackground.Gradient -> background.colors
        is ThemeBackground.Image -> listOf(background.fallback)
    }

    /** Alpha-blends [foreground] over an opaque [background] and returns an opaque colour. */
    fun composite(foreground: Int, background: Int): Int {
        val a = Argb.alpha(foreground) / 255.0
        val r = Math.round(Argb.red(foreground) * a + Argb.red(background) * (1 - a)).toInt()
        val g = Math.round(Argb.green(foreground) * a + Argb.green(background) * (1 - a)).toInt()
        val b = Math.round(Argb.blue(foreground) * a + Argb.blue(background) * (1 - a)).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun relativeLuminance(color: Int): Double {
        fun channel(value: Int): Double {
            val v = value / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(Argb.red(color)) +
            0.7152 * channel(Argb.green(color)) +
            0.0722 * channel(Argb.blue(color))
    }

    fun ratio(first: Int, second: Int): Double {
        val light = max(relativeLuminance(first), relativeLuminance(second))
        val dark = min(relativeLuminance(first), relativeLuminance(second))
        return (light + 0.05) / (dark + 0.05)
    }

    /**
     * Worst-case ratio of [text] over [fill] laid on any background stop.
     * Returns [worstStop] alongside so a failure can name the colour to fix.
     */
    fun worst(text: Int, fill: Int, stops: List<Int>): Pair<Double, Int> {
        var worstRatio = Double.MAX_VALUE
        var worstStop = stops.firstOrNull() ?: 0xFF000000.toInt()
        for (stop in stops) {
            val composited = composite(fill, stop)
            val value = ratio(text, composited)
            if (value < worstRatio) {
                worstRatio = value
                worstStop = composited
            }
        }
        return worstRatio to worstStop
    }

    /** The four pairs the format documents, keyed for error messages. */
    fun ratios(theme: KeyboardTheme): Map<String, Double> {
        val stops = backgroundStops(theme.background)
        if (stops.isEmpty()) return emptyMap()
        return linkedMapOf(
            "key" to worst(theme.keyText, theme.keyFill, stops).first,
            "pressed" to worst(theme.keyText, theme.keyPressed, stops).first,
            "special" to worst(theme.specialText, theme.specialFill, stops).first,
            "suggestionBar" to worst(theme.barText, theme.barBackground, stops).first
        )
    }

    /** Readable failures, e.g. `key.text on key.fill: contrast 2.10:1 needs 4.5:1`. */
    fun failures(theme: KeyboardTheme): List<String> {
        val stops = backgroundStops(theme.background)
        if (stops.isEmpty()) return listOf("${theme.id}: background has no colour stops")
        val checks = listOf(
            Triple("key.text on key.fill", theme.keyText, theme.keyFill),
            Triple("key.text on key.pressed", theme.keyText, theme.keyPressed),
            Triple("specialKey.text on specialKey.fill", theme.specialText, theme.specialFill),
            Triple("suggestionBar.text on suggestionBar.background", theme.barText, theme.barBackground)
        )
        return checks.mapNotNull { (label, text, fill) ->
            val (value, stop) = worst(text, fill, stops)
            if (value < MIN_CONTRAST) {
                "$label: contrast %.2f:1 on %s needs %s:1".format(value, Argb.toHex(stop), MIN_CONTRAST)
            } else {
                null
            }
        }
    }
}
