package app.shobdo.keyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class BanglaComposerTest {

    private fun type(vararg chars: Char): String =
        chars.fold("") { text, ch -> BanglaComposer.append(text, ch) }

    @Test
    fun `a kar attaches to the letter before it`() {
        assertEquals("কি", type('ক', 'ি'))
        assertEquals("কো", type('ক', 'ো'))
        assertEquals("সং", type('স', 'ং'))
    }

    @Test
    fun `a second kar replaces the first instead of stacking`() {
        assertEquals("কা", type('ক', 'ি', 'া'))
        assertEquals("কু", type('ক', 'ো', 'ু'))
        assertEquals("the letter and its kar are one unit", "", BanglaComposer.deleteLastUnit("কু"))
    }

    @Test
    fun `e kar followed by aa kar becomes the precomposed o kar`() {
        assertEquals("কো", type('ক', '\u09C7', '\u09BE'))
        assertEquals('\u09CB', type('ক', '\u09C7', '\u09BE').last())
    }

    @Test
    fun `a kar goes before a trailing sign`() {
        assertEquals("সিং", type('স', 'ং', 'ি'))
        assertEquals("কিঁ", type('ক', 'ঁ', 'ি'))
    }

    @Test
    fun `hasanta joins consonants and never doubles or follows a kar`() {
        assertEquals("ক্", type('ক', BanglaComposer.HASANTA))
        assertEquals("ক্", type('ক', BanglaComposer.HASANTA, BanglaComposer.HASANTA))
        assertEquals("a kar cannot take hasanta", "কি", type('ক', 'ি', BanglaComposer.HASANTA))
        assertEquals("ক্ষ", type('ক', BanglaComposer.HASANTA, 'ষ'))
    }

    @Test
    fun `one logical unit is a base letter with its kar`() {
        assertEquals(2, BanglaComposer.lastUnitLength("কি"))
        assertEquals(2, BanglaComposer.lastUnitLength("কো"))
        assertEquals(1, BanglaComposer.lastUnitLength("ক"))
        assertEquals("a kar with no letter is its own unit", 1, BanglaComposer.lastUnitLength("ি"))
    }

    @Test
    fun `a conjunct counts as one unit including hasanta`() {
        assertEquals(3, BanglaComposer.lastUnitLength("ক্ষ"))
        assertEquals("", BanglaComposer.deleteLastUnit("ক্ষ"))
    }

    @Test
    fun `deleting removes exactly the last unit`() {
        assertEquals("আ", BanglaComposer.deleteLastUnit("আমি"))
        assertEquals("বাং", BanglaComposer.deleteLastUnit("বাংলা"))
        assertEquals("", BanglaComposer.deleteLastUnit("কি"))
        assertEquals("", BanglaComposer.deleteLastUnit(""))
    }

    @Test
    fun `signs travel with their unit`() {
        assertEquals(2, BanglaComposer.lastUnitLength("সং"))
        assertEquals("a sign belongs to the letter it sits on", "", BanglaComposer.deleteLastUnit("সং"))
    }

    @Test
    fun `splitUnits round trips`() {
        assertEquals(listOf("আ", "মি"), BanglaComposer.splitUnits("আমি"))
        assertEquals(listOf("বাং", "লা"), BanglaComposer.splitUnits("বাংলা"))
        assertEquals(emptyList<String>(), BanglaComposer.splitUnits(""))
        val text = "ক্চ্ছি"
        assertEquals(text, BanglaComposer.splitUnits(text).joinToString(""))
    }

    @Test
    fun `appendAll walks a string through the same rules`() {
        assertEquals("কো", BanglaComposer.appendAll("ক", "ো"))
        assertEquals("ক্ষি", BanglaComposer.appendAll("ক্", "ষি"))
        assertEquals("", BanglaComposer.appendAll("", ""))
    }
}
