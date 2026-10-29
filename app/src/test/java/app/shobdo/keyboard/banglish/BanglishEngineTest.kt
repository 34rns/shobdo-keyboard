package app.shobdo.keyboard.banglish

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runs the same fixture as scripts/banglish_check.py, so the Kotlin engine and the reference
 * implementation cannot drift apart.
 */
class BanglishEngineTest {

    @Serializable
    private data class CasesFile(val cases: List<CaseDto>)

    @Serializable
    private data class CaseDto(
        val roman: String,
        val bangla: String,
        val group: String = "",
        val tag: String? = null,
        val note: String? = null
    )

    private val assetsDir = File(System.getProperty("shobdo.assets") ?: "src/main/assets")
    private val resourcesDir = File(System.getProperty("shobdo.testResources") ?: "src/test/resources")
    private val json = Json { ignoreUnknownKeys = true }

    private val engine: BanglishEngine by lazy {
        BanglishEngine.fromJson(File(assetsDir, BanglishEngine.RULE_ASSET_PATH).readText(Charsets.UTF_8))
    }

    private fun cases(): List<CaseDto> =
        json.decodeFromString(CasesFile.serializer(), File(resourcesDir, "banglish/cases.json").readText(Charsets.UTF_8)).cases

    @Test
    fun `the four required words`() {
        assertEquals("আমি", engine.transliterate("ami"))
        assertEquals("তুমি", engine.transliterate("tumi"))
        assertEquals("ভালো", engine.transliterate("bhalo"))
        assertEquals("বাংলা", engine.transliterate("bangla"))
    }

    @Test
    fun `every fixture case matches the reference implementation`() {
        val failures = cases().mapNotNull { case ->
            val actual = engine.transliterate(case.roman)
            if (actual == case.bangla) null else "'${case.roman}' expected '${case.bangla}' but got '$actual' (${case.group})"
        }
        assertTrue("${failures.size} case(s) disagree with the fixture:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the fixture is big enough to cover every consonant every vowel and kar forms`() {
        val all = cases()
        assertTrue("expected 30+ cases, found ${all.size}", all.size >= 34)
        val groups = all.groupBy { it.group }.mapValues { it.value.size }
        for (group in listOf("consonants", "vowels", "kar", "conjuncts", "words")) {
            assertTrue("fixture is missing the '$group' group", (groups[group] ?: 0) >= 4)
        }
    }

    @Test
    fun `longest match wins over a shorter prefix`() {
        assertEquals("খ", engine.transliterate("kh"))
        assertEquals("ক", engine.transliterate("k"))
        assertEquals("ষ", engine.transliterate("Sh"))
        assertEquals("শ", engine.transliterate("sh"))
    }

    @Test
    fun `an empty buffer produces nothing`() {
        assertEquals("", engine.transliterate(""))
        assertEquals(emptyList<String>(), engine.suggestions("", banglaFirst = true))
    }

    @Test
    fun `suggestions follow the configured order`() {
        assertEquals(listOf("আমি", "ami"), engine.suggestions("ami", banglaFirst = true))
        assertEquals(listOf("ami", "আমি"), engine.suggestions("ami", banglaFirst = false))
    }

    @Test
    fun `unknown characters pass through untouched`() {
        assertEquals("@", engine.transliterate("@"))
        assertEquals("!", engine.transliterate("!"))
    }

    @Test
    fun `broken rule data is rejected with a readable message`() {
        assertThrows(RuleDataException::class.java) { BanglishEngine.fromJson("{ not json") }
        assertThrows(RuleDataException::class.java) {
            BanglishEngine.fromJson(
                """{"behavior":{"hasanta":"্","oAfterConsonant":{"trailing":"ো"}},"rules":{"consonants":[{"roman":"k","bangla":""}]}}"""
            )
        }
    }
}
