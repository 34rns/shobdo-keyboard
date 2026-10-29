package app.shobdo.keyboard.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Writes a theme back to the documented JSON shape, so a duplicated or edited theme can be
 * saved, exported and imported without ever producing a file the loader cannot read.
 */
object ThemeWriter {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun toJson(theme: KeyboardTheme): String {
        val root = buildJsonObject {
            put("id", theme.id)
            put("name", theme.name)
            put("dark", theme.dark)
            if (theme.dynamic) put("dynamic", true)
            putJsonObject("background") {
                when (val background = theme.background) {
                    is ThemeBackground.Solid -> {
                        put("type", "solid")
                        put("color", Argb.toHex(background.color))
                    }
                    is ThemeBackground.Gradient -> {
                        put("type", "gradient")
                        put(
                            "colors",
                            buildJsonArray { background.colors.forEach { add(kotlinx.serialization.json.JsonPrimitive(Argb.toHex(it))) } }
                        )
                        put("angle", background.angle)
                    }
                    is ThemeBackground.Image -> {
                        put("type", "image")
                        background.path?.let { put("image", it) }
                        put("brightness", background.brightness)
                        put("blur", background.blur)
                        put("fallback", Argb.toHex(background.fallback))
                    }
                }
            }
            putJsonObject("key") {
                put("fill", Argb.toHex(theme.keyFill))
                put("pressed", Argb.toHex(theme.keyPressed))
                put("text", Argb.toHex(theme.keyText))
                put("radius", theme.keyRadius)
                put("shadow", theme.keyShadow)
                put("stroke", Argb.toHex(theme.keyStroke))
            }
            putJsonObject("specialKey") {
                put("fill", Argb.toHex(theme.specialFill))
                put("text", Argb.toHex(theme.specialText))
            }
            put("accent", Argb.toHex(theme.accent))
            putJsonObject("suggestionBar") {
                put("background", Argb.toHex(theme.barBackground))
                put("text", Argb.toHex(theme.barText))
            }
            putJsonObject("font") {
                put("scale", theme.fontScale)
                put("bold", theme.bold)
            }
            put("keySpacing", theme.keySpacing)
        }
        return json.encodeToString(JsonObject.serializer(), root) + "\n"
    }
}
