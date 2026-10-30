package app.shobdo.keyboard.theme

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File

/**
 * Loads the built-in themes from assets plus the user's own themes from app storage, and
 * validates anything that comes in from outside.
 *
 * There is no global instance: the keyboard service and the settings screen each own one, so
 * no Context is held statically.
 */
class ThemeManager(private val context: Context) {

    private val userDir: File
        get() = File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    /** Themes shipped in the APK, in gallery order so the grid does not shuffle. */
    fun builtIn(): List<KeyboardTheme> {
        val names = context.assets.list(DIR).orEmpty()
            .filter { it.endsWith(".json") }
            .sorted()
        val parsed = names.mapNotNull { name ->
            val source = context.assets.open("$DIR/$name").bufferedReader().use { it.readText() }
            runCatching { ThemeJson.parse(source, builtIn = true) }.getOrNull()
        }
        return parsed.sortedBy { theme ->
            val position = ORDER.indexOf(theme.id)
            if (position < 0) ORDER.size else position
        }
    }

    /** Themes the user created, duplicated or imported. */
    fun user(): List<KeyboardTheme> =
        userDir.listFiles { file -> file.extension == "json" }.orEmpty()
            .sortedBy { it.name }
            .mapNotNull { file ->
                runCatching { ThemeJson.parse(file.readText(), builtIn = false) }.getOrNull()
            }

    fun all(): List<KeyboardTheme> = builtIn() + user()

    fun byId(id: String): KeyboardTheme? = all().firstOrNull { it.id == id }

    /**
     * The theme to draw with.
     *
     * A stored id from an older version is migrated first, so nobody loses their look after an
     * update, and Dynamic falls back to Studio Light or Dark below Android 12 rather than
     * drawing a palette it cannot read.
     */
    fun active(themeId: String): KeyboardTheme {
        val migrated = ThemeMigration.migrate(themeId)
        val theme = byId(migrated) ?: byId(themeId) ?: default()
        if (!theme.dynamic) return theme
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamic(theme) else default()
    }

    /** Studio Light in light mode, Studio Dark in dark mode: the keyboard's own default. */
    fun default(): KeyboardTheme {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val id = if (night) STUDIO_DARK else STUDIO_LIGHT
        return byId(id) ?: builtIn().firstOrNull() ?: fallbackTheme()
    }

    /**
     * Material You: take the system palette's accent and tint the keys with it. Only the
     * accent family is replaced, and always at a low alpha, so the theme's own text colour
     * keeps the contrast it was validated with.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun dynamic(theme: KeyboardTheme): KeyboardTheme {
        val accent = context.getColor(android.R.color.system_accent1_500)
        return theme.copy(
            accent = accent,
            keyFill = withAlpha(accent, 0x26),
            keyPressed = withAlpha(accent, 0x40),
            specialFill = withAlpha(accent, 0x33),
            barBackground = withAlpha(accent, 0x22),
            dynamic = true
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    /**
     * Puts the user's own picture behind the keys. The image path and its sliders are a
     * setting rather than part of a theme file, so the same photo works with every theme and a
     * theme stays small and shareable. The theme's own first background colour is kept as the
     * fallback, which is also what the contrast checker reads.
     */
    fun withImageOverride(theme: KeyboardTheme, path: String?, brightness: Float, blur: Float): KeyboardTheme {
        if (path.isNullOrBlank() || !File(path).exists()) return theme
        val fallback = ThemeContrast.backgroundStops(theme.background).firstOrNull() ?: 0xFF101418.toInt()
        return theme.copy(
            background = ThemeBackground.Image(
                path = path,
                brightness = brightness.coerceIn(0f, 1f),
                blur = blur.coerceAtLeast(0f),
                fallback = fallback
            )
        )
    }

    /**
     * Saves a theme after validating it. A theme that fails the schema or the contrast floor
     * is refused with the reason, so the user can fix the colour instead of getting an
     * unreadable keyboard.
     */
    fun save(theme: KeyboardTheme): Result<KeyboardTheme> {
        if (theme.id.isBlank()) return Result.failure(ThemeFormatException("A theme needs an id"))
        val failures = ThemeContrast.failures(theme) + listOfNotNull(ThemeContrast.accentFailure(theme))
        if (failures.isNotEmpty()) {
            return Result.failure(ThemeFormatException(failures.joinToString("; ")))
        }
        return runCatching {
            val file = File(userDir, "${theme.id}.json")
            file.writeText(ThemeWriter.toJson(theme.copy(builtIn = false)))
            ThemeJson.parse(file.readText(), builtIn = false)
        }
    }

    fun delete(themeId: String): Boolean {
        val file = File(userDir, "$themeId.json")
        return file.exists() && file.delete()
    }

    /** A copy of [theme] with a unique id, ready to be edited and saved. */
    fun duplicate(theme: KeyboardTheme): KeyboardTheme {
        val existing = all().map { it.id }.toSet()
        var index = 1
        var id: String
        do {
            id = "${theme.id}_copy${if (index == 1) "" else "_$index"}"
            index++
        } while (id in existing)
        return theme.withId(id, "${theme.name} copy", builtIn = false)
    }

    fun exportJson(theme: KeyboardTheme): String = ThemeWriter.toJson(theme.copy(builtIn = false))

    fun exportTo(uri: Uri, theme: KeyboardTheme): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(exportJson(theme).toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("could not open the chosen file")
    }

    /** Reads a `.json` file and validates it exactly like a save. */
    fun importFrom(uri: Uri): Result<KeyboardTheme> = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        } ?: throw ThemeFormatException("could not read that file")
        importJson(text)
    }

    fun importJson(text: String): KeyboardTheme {
        val parsed = ThemeJson.parse(text, builtIn = false)
        val unique = if (all().any { it.id == parsed.id }) duplicate(parsed) else parsed
        save(unique).getOrThrow()
        return unique
    }

    /** Used only if every asset failed, so the keyboard can still draw something. */
    private fun fallbackTheme(): KeyboardTheme = ThemeJson.parse(
        """{"id":"fallback","name":"Fallback","dark":true,
           "background":{"type":"solid","color":"#101418"},
           "key":{"fill":"#22FFFFFF","pressed":"#33FFFFFF","text":"#F8FAFC","radius":10,"shadow":false,"stroke":"#22FFFFFF"},
           "specialKey":{"fill":"#2AFFFFFF","text":"#F8FAFC"},
           "accent":"#6366F1",
           "suggestionBar":{"background":"#33000000","text":"#E2E8F0"},
           "font":{"scale":1.0,"bold":false},"keySpacing":6}"""
    )

    companion object {
        const val DIR = "themes"

        const val STUDIO_LIGHT = "studio_light"
        const val STUDIO_DARK = "studio_dark"

        /** Gallery order, chosen so the two studio themes open the list. */
        val ORDER: List<String> = listOf(
            STUDIO_LIGHT, STUDIO_DARK, "onyx", "frost", "aurora", "sakura", "tokyo_night",
            "pixel_arcade", "cassette", "obsidian_gold", "cloudline", "evergreen", "dynamic"
        )

        /** Suggested file name when exporting the theme with [KeyboardTheme.id]. */
        fun exportFileName(theme: KeyboardTheme): String = "shobdo-${theme.id}.json"
    }
}
