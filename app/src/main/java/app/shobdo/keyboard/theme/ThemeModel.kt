package app.shobdo.keyboard.theme

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ThemeFormatException(message: String) : Exception(message)

/** `#RRGGBB` or `#AARRGGBB`. Alpha comes first, the way Android reads colours. */
object Argb {
    fun parse(value: String): Int {
        val text = value.trim()
        if (!text.startsWith("#")) throw IllegalArgumentException("'$value' must start with #")
        val hex = text.substring(1)
        if (hex.length != 6 && hex.length != 8) {
            throw IllegalArgumentException("'$value' must be #RRGGBB or #AARRGGBB")
        }
        val number = try {
            hex.toLong(16)
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("'$value' is not hexadecimal")
        }
        return if (hex.length == 6) (0xFF000000L or number).toInt() else number.toInt()
    }

    fun alpha(color: Int): Int = (color ushr 24) and 0xFF

    fun red(color: Int): Int = (color shr 16) and 0xFF

    fun green(color: Int): Int = (color shr 8) and 0xFF

    fun blue(color: Int): Int = color and 0xFF

    fun toHex(color: Int): String =
        "#%02X%02X%02X%02X".format(alpha(color), red(color), green(color), blue(color))
}

/** How a key sits on the background. No blurred shadows anywhere: depth is an edge. */
enum class KeyDepth(val json: String) {
    FLAT("flat"),

    /** A 1dp darker edge under the key. */
    LIP("lip"),

    /** A hard 2px block edge, for the pixel theme. */
    BLOCK("block");

    companion object {
        fun fromJson(value: String?): KeyDepth =
            entries.firstOrNull { it.json == value } ?: FLAT
    }
}

enum class KeyShape(val json: String) {
    ROUND("round"),
    SQUIRCLE("squircle"),

    /** Stair-stepped corners drawn without anti-aliasing. */
    PIXEL("pixel");

    companion object {
        fun fromJson(value: String?): KeyShape =
            entries.firstOrNull { it.json == value } ?: ROUND
    }
}

/** Which bundled font family a theme asks for. */
enum class FontRole(val json: String) {
    DEFAULT("default"),
    PIXEL("pixel");

    companion object {
        fun fromJson(value: String?): FontRole =
            entries.firstOrNull { it.json == value } ?: DEFAULT
    }
}

sealed interface ThemeBackground {
    data class Solid(val color: Int) : ThemeBackground

    data class Gradient(val colors: List<Int>, val angle: Float) : ThemeBackground

    data class Image(val path: String?, val brightness: Float, val blur: Float, val fallback: Int) : ThemeBackground
}

/**
 * A theme, already parsed. Colours are ARGB ints so the keyboard never parses strings while
 * drawing.
 */
data class KeyboardTheme(
    val id: String,
    val name: String,
    val dark: Boolean,
    val dynamic: Boolean,
    val background: ThemeBackground,
    val keyFill: Int,
    val keyPressed: Int,
    val keyText: Int,
    val keyRadius: Float,
    val keyShadow: Boolean,
    val keyStroke: Int,
    val specialFill: Int,
    val specialText: Int,
    val accent: Int,
    val barBackground: Int,
    val barText: Int,
    val fontScale: Float,
    val bold: Boolean,
    val keySpacing: Float,
    /** Schema v2 tokens. Defaults keep a v1 theme file loadable. */
    val keyDepth: KeyDepth = KeyDepth.FLAT,
    val keyShape: KeyShape = KeyShape.ROUND,
    val fontRole: FontRole = FontRole.DEFAULT,
    val spaceAccentLine: Boolean = false,
    val pressedScale: Float = 0.97f,
    /** Second accent: only the language key mark and the spacebar line may use it. */
    val secondary: Int? = null,
    val schemaVersion: Int = 2,
    /** False for a theme the user created, which survives app updates. */
    val builtIn: Boolean = true,
    /** The JSON it was read from, kept so duplicate and export write the same file. */
    val rawJson: String = ""
) {
    fun withId(newId: String, newName: String, builtIn: Boolean = false): KeyboardTheme =
        copy(id = newId, name = newName, builtIn = builtIn)
}

/** Parsed from theme JSON. Mirrors docs/THEME_FORMAT.md. */
object ThemeJson {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ThemeDto(
        val id: String = "",
        val name: String = "",
        val dark: Boolean = false,
        val dynamic: Boolean = false,
        val background: BackgroundDto = BackgroundDto(),
        val key: KeyDto = KeyDto(),
        val specialKey: SpecialKeyDto = SpecialKeyDto(),
        val accent: String = "#FF888888",
        val suggestionBar: SuggestionBarDto = SuggestionBarDto(),
        val font: FontDto = FontDto(),
        val keySpacing: Float = 6f,
        val schemaVersion: Int = 2,
        val keyDepth: String? = null,
        val keyShape: String? = null,
        val fontRole: String? = null,
        val spaceAccentLine: Boolean = false,
        val pressedScale: Float = 0.97f,
        val secondary: String? = null
    )

    @Serializable
    private data class BackgroundDto(
        val type: String = "solid",
        val color: String? = null,
        val colors: List<String>? = null,
        val angle: Float = 90f,
        val image: String? = null,
        val brightness: Float = 1f,
        val blur: Float = 0f,
        val fallback: String? = null
    )

    @Serializable
    private data class KeyDto(
        val fill: String = "#00000000",
        val pressed: String = "#00000000",
        val text: String = "#FFFFFFFF",
        val radius: Float = 8f,
        val shadow: Boolean = false,
        val stroke: String = "#00000000"
    )

    @Serializable
    private data class SpecialKeyDto(
        val fill: String = "#00000000",
        val text: String = "#FFFFFFFF"
    )

    @Serializable
    private data class SuggestionBarDto(
        val background: String = "#00000000",
        val text: String = "#FFFFFFFF"
    )

    @Serializable
    private data class FontDto(
        val scale: Float = 1f,
        val bold: Boolean = false
    )

    fun parse(source: String, builtIn: Boolean = true): KeyboardTheme {
        val dto = try {
            json.decodeFromString(ThemeDto.serializer(), source)
        } catch (e: Exception) {
            throw ThemeFormatException("theme JSON is not readable: ${e.message}")
        }
        if (dto.id.isBlank()) throw ThemeFormatException("theme has no 'id'")
        if (dto.name.isBlank()) throw ThemeFormatException("theme '${dto.id}' has no 'name'")
        if (dto.keySpacing < 0f) throw ThemeFormatException("theme '${dto.id}' has negative keySpacing")
        if (dto.font.scale <= 0f) throw ThemeFormatException("theme '${dto.id}' has font.scale ${dto.font.scale}")

        fun colour(value: String?, field: String): Int = try {
            Argb.parse(value ?: throw IllegalArgumentException("missing"))
        } catch (e: IllegalArgumentException) {
            throw ThemeFormatException("theme '${dto.id}' $field: ${e.message}")
        }

        val background = parseBackground(dto.id, dto.background)
        return KeyboardTheme(
            id = dto.id,
            name = dto.name,
            dark = dto.dark,
            dynamic = dto.dynamic,
            background = background,
            keyFill = colour(dto.key.fill, "key.fill"),
            keyPressed = colour(dto.key.pressed, "key.pressed"),
            keyText = colour(dto.key.text, "key.text"),
            keyRadius = dto.key.radius.coerceAtLeast(0f),
            keyShadow = dto.key.shadow,
            keyStroke = colour(dto.key.stroke, "key.stroke"),
            specialFill = colour(dto.specialKey.fill, "specialKey.fill"),
            specialText = colour(dto.specialKey.text, "specialKey.text"),
            accent = colour(dto.accent, "accent"),
            barBackground = colour(dto.suggestionBar.background, "suggestionBar.background"),
            barText = colour(dto.suggestionBar.text, "suggestionBar.text"),
            fontScale = dto.font.scale,
            bold = dto.font.bold,
            keySpacing = dto.keySpacing,
            keyDepth = KeyDepth.fromJson(dto.keyDepth),
            keyShape = KeyShape.fromJson(dto.keyShape),
            fontRole = FontRole.fromJson(dto.fontRole),
            spaceAccentLine = dto.spaceAccentLine,
            pressedScale = dto.pressedScale.coerceIn(0.85f, 1f),
            secondary = dto.secondary?.let { colour(it, "secondary") },
            schemaVersion = dto.schemaVersion,
            builtIn = builtIn,
            rawJson = source
        )
    }

    private fun parseBackground(themeId: String, dto: BackgroundDto): ThemeBackground {
        fun colour(value: String?, field: String): Int = try {
            Argb.parse(value ?: throw IllegalArgumentException("missing"))
        } catch (e: IllegalArgumentException) {
            throw ThemeFormatException("theme '$themeId' background.$field: ${e.message}")
        }
        return when (dto.type) {
            "solid" -> ThemeBackground.Solid(colour(dto.color, "color"))
            "gradient" -> {
                val colors = dto.colors.orEmpty()
                if (colors.size < 2) {
                    throw ThemeFormatException("theme '$themeId' gradient needs at least two colours")
                }
                ThemeBackground.Gradient(colors.map { colour(it, "colors") }, dto.angle)
            }
            "image" -> ThemeBackground.Image(
                path = dto.image,
                brightness = dto.brightness.coerceIn(0f, 1f),
                blur = dto.blur.coerceAtLeast(0f),
                fallback = colour(dto.fallback, "fallback")
            )
            else -> throw ThemeFormatException("theme '$themeId' background.type '${dto.type}' is not solid, gradient or image")
        }
    }
}
