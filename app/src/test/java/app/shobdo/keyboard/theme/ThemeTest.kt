package app.shobdo.keyboard.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ThemeTest {

    private val assetsDir = File(System.getProperty("shobdo.assets") ?: "src/main/assets")
    private val themeDir = File(assetsDir, "themes")

    private fun files(): List<File> = themeDir.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }

    private fun themes(): List<KeyboardTheme> =
        files().map { ThemeJson.parse(it.readText(Charsets.UTF_8), builtIn = true) }

    @Test
    fun `all thirteen built in themes are present and parse`() {
        val expected = listOf(
            "studio_light", "studio_dark", "onyx", "frost", "aurora", "sakura",
            "tokyo_night", "pixel_arcade", "cassette", "obsidian_gold", "cloudline",
            "evergreen", "dynamic"
        )
        val ids = files().map { it.nameWithoutExtension }
        assertEquals(expected.sorted(), ids.sorted())
        assertEquals(13, themes().size)
    }

    @Test
    fun `every theme carries the schema 2 tokens`() {
        for (theme in themes()) {
            assertEquals("${theme.id} schemaVersion", 2, theme.schemaVersion)
            assertTrue("${theme.id} pressedScale", theme.pressedScale in 0.85f..1f)
            assertTrue("${theme.id} key radius", theme.keyRadius >= 0f)
        }
    }

    @Test
    fun `the pixel theme is the only stair-stepped one and it uses the pixel font`() {
        val pixel = themes().filter { it.keyShape == KeyShape.PIXEL }
        assertEquals(1, pixel.size)
        assertEquals("pixel_arcade", pixel.first().id)
        assertEquals(FontRole.PIXEL, pixel.first().fontRole)
        assertEquals(KeyDepth.BLOCK, pixel.first().keyDepth)
    }

    @Test
    fun `tokyo night uses its second accent only for the space bar line`() {
        val tokyo = themes().first { it.id == "tokyo_night" }
        assertTrue(tokyo.spaceAccentLine)
        assertEquals(Argb.parse("#36E3FF"), tokyo.secondary)
    }

    @Test
    fun `accent stays readable on every background`() {
        val problems = themes().mapNotNull { theme ->
            val stops = ThemeContrast.backgroundStops(theme.background)
            val worst = stops.minOf { ThemeContrast.ratio(theme.accent, it) }
            if (worst < ThemeContrast.MIN_UI_CONTRAST) {
                "${theme.id} accent %.2f:1".format(worst)
            } else {
                null
            }
        }
        assertTrue("accent contrast failures: $problems", problems.isEmpty())
    }

    @Test
    fun `every retired theme id migrates to a theme that exists`() {
        val ids = files().map { it.nameWithoutExtension }.toSet()
        for (old in ThemeMigration.oldIds) {
            val target = ThemeMigration.migrate(old)
            assertTrue("$old -> $target is not a shipped theme", target in ids)
        }
    }

    @Test
    fun `every theme id matches its file name`() {
        for (file in files()) {
            val theme = ThemeJson.parse(file.readText(Charsets.UTF_8))
            assertEquals(file.nameWithoutExtension, theme.id)
            assertTrue(theme.name.isNotBlank())
            assertTrue(theme.keySpacing >= 0f)
            assertTrue(theme.fontScale > 0f)
        }
    }

    @Test
    fun `every theme meets the 45 to 1 contrast floor on the composited key colour`() {
        val problems = themes().flatMap { theme ->
            ThemeContrast.failures(theme).map { "${theme.id}: $it" }
        }
        assertTrue("contrast failures:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `contrast is measured worst stop first across a gradient`() {
        val worst = ThemeContrast.worst(
            text = 0xFFFFFFFF.toInt(),
            fill = 0x00000000,
            stops = listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        )
        assertEquals(1.0, worst.first, 0.01)
    }

    @Test
    fun `colours are read alpha first`() {
        // Alpha first: FF alpha, FF red, FF green, 1F blue - not a 10% white.
        assertEquals(0xFFFFFF1F.toInt(), Argb.parse("#FFFFFF1F"))
        assertEquals(0x1F, Argb.alpha(Argb.parse("#1FFFFFFF")))
        assertEquals(0xFF, Argb.alpha(Argb.parse("#F8FAFC")))
        assertEquals(0xFFF8FAFC.toInt(), Argb.parse("#F8FAFC"))
    }

    @Test
    fun `bad colour and schema values are rejected with a readable message`() {
        val missingName = """{"id":"x","background":{"type":"solid","color":"#000000"},"key":{},"specialKey":{},"accent":"#FFFFFF","suggestionBar":{},"font":{},"keySpacing":6}"""
        assertThrows(ThemeFormatException::class.java) { ThemeJson.parse(missingName) }

        val badType = """{"id":"x","name":"X","background":{"type":"hologram"},"key":{},"specialKey":{},"accent":"#FFFFFF","suggestionBar":{},"font":{},"keySpacing":6}"""
        assertThrows(ThemeFormatException::class.java) { ThemeJson.parse(badType) }

        val badColour = """{"id":"x","name":"X","background":{"type":"solid","color":"blue"},"key":{},"specialKey":{},"accent":"#FFFFFF","suggestionBar":{},"font":{},"keySpacing":6}"""
        assertThrows(ThemeFormatException::class.java) { ThemeJson.parse(badColour) }
    }

    @Test
    fun `a duplicate of a built in theme keeps its colours but is no longer built in`() {
        val original = themes().first()
        val copy = original.withId("${original.id}_copy", "Copy")
        assertTrue(copy.builtIn.not())
        assertEquals(original.keyFill, copy.keyFill)
        assertEquals("${original.id}_copy", copy.id)
    }
}
