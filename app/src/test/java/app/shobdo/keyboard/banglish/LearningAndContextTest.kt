package app.shobdo.keyboard.banglish

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class LearningAndContextTest {
    @Test fun `recording again does not revive expired counts`() {
        var clock = 1000000000L
        val learned = LearnedWords(File.createTempFile("shobdo-expired", ".tsv")) { clock }
        repeat(500) { learned.record("আছি", "আমি") }
        clock += (LearnedWords.HALF_LIFE_MS * 12).toLong()
        learned.record("আছি", "আমি")
        val fresh = LearnedWords(File.createTempFile("shobdo-fresh", ".tsv")) { clock }
        fresh.record("আছি", "আমি")
        assertEquals(fresh.bonus("আছি", "আমি"), learned.bonus("আছি", "আমি"), .001)
    }
    @Test fun `dictionary rejects isolated marks and punctuation`() {
        val learned = LearnedWords(File.createTempFile("shobdo-valid", ".tsv"))
        assertFalse(learned.add("mark", "াম"))
        assertFalse(learned.add("punctuation", "অ\u09FD"))
        assertTrue(learned.add("word", "নাম"))
    }
    @Test fun `learning decays over thirty days and remembers previous word`() {
        var clock = 1000000000L
        val learned = LearnedWords(File.createTempFile("shobdo-decay", ".tsv")) { clock }
        learned.record("আছি", "আমি")
        assertTrue(learned.bonus("আছি", "আমি") > learned.bonus("আছি", "তুমি"))
        val original = learned.bonus("আছি")
        clock += LearnedWords.HALF_LIFE_MS.toLong()
        assertEquals(original / 2, learned.bonus("আছি"), .001)
    }
    @Test fun `storage caps migration and dictionary round trip`() {
        val file = File.createTempFile("shobdo-store", ".tsv")
        file.writeText("নাম\t3\n")
        val learned = LearnedWords(file)
        assertTrue(learned.bonus("নাম") > 0)
        repeat(900) { learned.record("শব্দ$it", "আগে$it") }
        assertEquals(LearnedWords.MAX_WORDS, learned.size)
        assertEquals(LearnedWords.MAX_CONTEXTS, learned.contextSize)
        assertTrue(learned.add("dialect", "আম্মু"))
        assertFalse(learned.add("bad\tentry", "নাম"))
        assertTrue(learned.flush())
        val restored = LearnedWords(file)
        assertEquals(listOf("আম্মু"), restored.matches("dialect"))
        restored.clear()
        assertEquals(0, restored.size)
        assertEquals(1, restored.dictionary().size)
        restored.remove("আম্মু")
        assertTrue(restored.dictionary().isEmpty())
    }
    @Test fun `bigram boosts fit only the previous committed word`() {
        val table = BigramTable.parse("আমি\tআছি\t4\nতুমি\tআছো\t3\n")
        assertTrue(table.bonus("আমি", "আছি") > 0)
        assertEquals(0.0, table.bonus("তুমি", "আছি"), .001)
    }
}
