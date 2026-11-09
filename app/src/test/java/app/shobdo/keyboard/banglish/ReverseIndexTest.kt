package app.shobdo.keyboard.banglish

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ReverseIndexTest {
    private val assets = File(System.getProperty("shobdo.assets"))
    private val index by lazy { ReverseRomanIndex.parse(File(assets, ReverseRomanIndex.ASSET_PATH).readBytes()) }
    private val rules = SkeletonRules.fromJson(File(assets, SkeletonRules.ASSET_PATH).readText())

    @Test fun `reverse lookup resolves colloquial vowels and reph`() {
        for ((roman, word) in mapOf("valo" to "ভালো", "bhalo" to "ভালো", "kemon" to "কেমন", "kormo" to "কর্ম")) {
            assertTrue("$roman -> $word", index.exact(rules.ofRoman(roman)).any { it.text == word })
        }
    }
    @Test fun `prefix includes all useful completions ordered by weight`() {
        val words = index.prefix("bhal", 40)
        assertTrue(words.map { it.text }.containsAll(listOf("ভালো", "ভালোবাসা", "ভালোই")))
        assertTrue(words.zipWithNext().all { it.first.count >= it.second.count })
    }
    @Test fun `fuzzy lookup tolerates insertion deletion and substitution`() {
        for (key in listOf("bhalx", "bhlo", "bhallo")) {
            assertTrue(key, index.near(key).any { it.text == "ভালো" })
        }
    }
    @Test fun `malformed asset is rejected`() {
        assertThrows(BanglishDataException::class.java) { ReverseRomanIndex.parse(byteArrayOf(1,2,3)) }
    }
}
