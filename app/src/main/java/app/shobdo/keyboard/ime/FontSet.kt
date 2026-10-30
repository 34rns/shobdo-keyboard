package app.shobdo.keyboard.ime

import android.content.Context
import android.graphics.Typeface
import app.shobdo.keyboard.theme.FontRole

/**
 * The families shipped inside the APK.
 *
 * Bundling them means a Bangla conjunct is shaped by the same font on every phone instead of
 * depending on the system's Bengali support. Each is subset to the characters the keyboard can
 * print, which is why all five files together are about 410 KB.
 *
 * A file that cannot be read falls back to the system typeface so the keyboard still draws.
 */
class FontSet(context: Context) {

    val latinRegular: Typeface = load(context, "fonts/Inter-Regular.ttf", Typeface.NORMAL, 400)
    val latinMedium: Typeface = load(context, "fonts/Inter-Medium.ttf", Typeface.NORMAL, 500)
    val banglaRegular: Typeface = load(context, "fonts/NotoSansBengali-Regular.ttf", Typeface.NORMAL, 400)
    val banglaMedium: Typeface = load(context, "fonts/NotoSansBengali-Medium.ttf", Typeface.NORMAL, 500)
    val pixel: Typeface = load(context, "fonts/PixelifySans-Regular.ttf", Typeface.NORMAL, 400)

    /** Latin family for a theme that asks for a specific role. */
    fun latin(role: FontRole): Typeface = when (role) {
        FontRole.PIXEL -> pixel
        FontRole.DEFAULT -> latinRegular
    }

    fun bangla(bold: Boolean): Typeface = if (bold) banglaMedium else banglaRegular

    fun latin(bold: Boolean): Typeface = if (bold) latinMedium else latinRegular

    private fun load(context: Context, path: String, style: Int, weight: Int): Typeface {
        val loaded = runCatching { Typeface.createFromAsset(context.assets, path) }.getOrNull()
            ?: return Typeface.create(Typeface.DEFAULT, style)
        return Typeface.create(loaded, weight, false)
    }
}
