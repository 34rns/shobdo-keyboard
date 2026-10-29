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
    fun `all twelve built in themes are present and parse`() {
        val expected = listOf(
            "amoled_black", "bangla_flag", "forest_calm", "material_you", "midnight_glass",
            "neon_cyber", "ocean_breeze", "paper_minimal", "pure_light", "rose_gold",
            "royal_purple", "sunset_gradient"
        )
        val ids = files().map { it.nameWithoutExtension }
        assertEquals(expected.sorted(), ids.sorted())
        assertEquals(12, themes().size)
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
