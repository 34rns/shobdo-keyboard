package app.shobdo.keyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LayoutLoaderTest {

    private val assetsDir: File = File(System.getProperty("shobdo.assets") ?: "src/main/assets")

    private fun asset(path: String): String = File(assetsDir, path).readText(charset = Charsets.UTF_8)

    @Test
    fun `parses the english page into five rows`() {
        val layout = LayoutLoader.parse(asset("layouts/en_qwerty.json"))
        assertEquals("en_qwerty", layout.id)
        assertEquals("English", layout.name)
        assertEquals(5, layout.rows.size)
        assertEquals(listOf(10, 10, 9, 9, 6), layout.rows.map { it.size })
        assertEquals("the number row is row 0 so the setting can hide it", 0, layout.numberRowIndex ?: -1)
    }

    @Test
    fun `maps labels codes and long press alternates`() {
        val layout = LayoutLoader.parse(asset("layouts/en_qwerty.json"))
        val e = layout.rows[1][2]
        assertEquals("e", e.label)
        assertEquals("e", e.code)
        assertEquals(listOf("é", "è", "ê", "ë"), e.longPressChars)
        assertEquals(KeyType.NORMAL, e.type)

        val space = layout.rows[4][2]
        assertEquals(KeyType.SPACE, space.type)
        assertEquals(" ", space.code)
        assertEquals(4f, space.widthWeight)
    }

    @Test
    fun `maps every special type that appears in the shipped pages`() {
        val qwerty = LayoutLoader.parse(asset("layouts/en_qwerty.json"))
        val types = qwerty.rows.flatten().map { it.type }.toSet()
        assertTrue(
            types.containsAll(
                listOf(
                    KeyType.SHIFT, KeyType.BACKSPACE, KeyType.ENTER, KeyType.SPACE,
                    KeyType.SYMBOLS, KeyType.COMMA, KeyType.PERIOD, KeyType.GLOBE
                )
            )
        )
        assertFalse("the emoji page is not built yet", types.contains(KeyType.EMOJI))
    }

    @Test
    fun `all shipped layouts parse`() {
        val files = File(assetsDir, "layouts").listFiles { f -> f.extension == "json" }.orEmpty()
        assertTrue("expected layout files", files.isNotEmpty())
        for (file in files) {
            val layout = LayoutLoader.parse(file.readText(Charsets.UTF_8))
            assertEquals(file.nameWithoutExtension, layout.id)
            assertTrue(layout.rows.all { row -> row.isNotEmpty() })
            assertTrue("${file.name} needs a space key", layout.rows.flatten().any { it.type == KeyType.SPACE })
        }
    }

    @Test
    fun `rejects malformed json`() {
        assertThrows(LayoutFormatException::class.java) { LayoutLoader.parse("{ not json") }
    }

    @Test
    fun `rejects an unknown key type`() {
        val source = """{"id":"x","name":"X","rows":[[{"label":"a","code":"a","type":"teleport"}]]}"""
        val error = assertThrows(LayoutFormatException::class.java) { LayoutLoader.parse(source) }
        assertTrue(error.message!!.contains("teleport"))
    }

    @Test
    fun `rejects a layout with no rows or no label`() {
        assertThrows(LayoutFormatException::class.java) { LayoutLoader.parse("""{"id":"x","name":"X","rows":[]}""") }
        assertThrows(LayoutFormatException::class.java) {
            LayoutLoader.parse("""{"id":"x","name":"X","rows":[[{"label":"","code":"a"}]]}""")
        }
    }

    @Test
    fun `rejects a non positive width weight`() {
        val source = """{"id":"x","name":"X","rows":[[{"label":"a","code":"a","widthWeight":0}]]}"""
        assertThrows(LayoutFormatException::class.java) { LayoutLoader.parse(source) }
    }

    @Test
    fun `shifted letters keep latin uppercase and bangla takes the first alternate`() {
        val qwerty = LayoutLoader.parse(asset("layouts/en_qwerty.json"))
        val a = qwerty.rows[2][0]
        assertEquals("A", a.displayLabel(shifted = true))
        assertEquals("A", a.displayCode(shifted = true))
        assertEquals("a", a.displayCode(shifted = false))

        // A Bangla page has no uppercase, so shift takes the Probhat alternate instead.
        val bangla = LayoutLoader.parse(asset("layouts/bn_provat.json"))
        val ka = bangla.rows[1].first { it.code == "ক" }
        assertEquals("খ", ka.displayCode(shifted = true, shiftUsesAlternates = true))
        assertEquals("ক", ka.displayCode(shifted = false, shiftUsesAlternates = true))
        assertEquals("on a Latin page shift is uppercase, not the accent popup", "A", a.displayCode(shifted = true))
    }
}
