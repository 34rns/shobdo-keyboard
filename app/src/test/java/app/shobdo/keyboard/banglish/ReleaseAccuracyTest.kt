package app.shobdo.keyboard.banglish

import java.io.File
import java.text.Normalizer
import org.junit.Assert.*
import org.junit.Test

class ReleaseAccuracyTest {
    private val assets = File(System.getProperty("shobdo.assets")!!)
    private val resources = File(System.getProperty("shobdo.testResources")!!)
    private val skeleton = SkeletonRules.fromJson(File(assets, SkeletonRules.ASSET_PATH).readText())
    private val phonetic = BanglishEngine.fromJson(File(assets, BanglishEngine.RULE_ASSET_PATH).readText())
    private val baseline = BaselineCandidateEngine(phonetic, skeleton,
        BaselineBanglishLexicon.load(File(assets, BanglishLexicon.ASSET_PATH)))
    private val release = CandidateEngine(phonetic, skeleton,
        BanglishLexicon.empty(), reverse =
        ReverseRomanIndex.parse(File(assets, ReverseRomanIndex.ASSET_PATH).readBytes()))
    private fun normalize(word: String) = Normalizer.normalize(word, Normalizer.Form.NFC)

    @Test fun `same fixed corpus measures before and after top one and three`() {
        val cases = listOf("banglish_cases.tsv", "banglish_release_cases.tsv").flatMap { name ->
            File(resources, name).readLines().filter { it.isNotBlank() && !it.startsWith('#') }.map {
                val p = it.split('\t'); p[0] to normalize(p[1])
            }
        }
        assertTrue(cases.size >= 300)
        var before1 = 0; var before3 = 0; var after1 = 0; var after3 = 0
        val misses = ArrayList<String>()
        for ((roman, expected) in cases) {
            val before = baseline.candidates(roman).map { normalize(it.text) }
            val after = release.candidates(roman).map { normalize(it.text) }
            if (before.firstOrNull() == expected) before1++
            if (expected in before.take(3)) before3++
            if (after.firstOrNull() == expected) after1++ else misses.add("$roman -> ${after.take(3)} (expected $expected)")
            if (expected in after.take(3)) after3++
        }
        println("RELEASE ACCURACY n=${cases.size}: BEFORE top-1=$before1 top-3=$before3; AFTER top-1=$after1 top-3=$after3")
        println("AFTER MISSES: ${misses.joinToString("; ")}")
        assertTrue("top-1 regressed", after1 >= before1)
        assertTrue("top-3 regressed", after3 >= before3)
        val samples = cases.take(60).map { it.first }
        repeat(20) { samples.forEach { release.candidates(it) } }
        val times = samples.map { roman ->
            val start = System.nanoTime(); repeat(10) { release.candidates(roman) }
            (System.nanoTime() - start) / 10.0 / 1_000_000
        }.sorted()
        println("RELEASE LOOKUP: mean=%.3f ms, p95=%.3f ms, max=%.3f ms".format(times.average(), times[(times.size * .95).toInt()], times.last()))
        assertTrue(times.last() < 20.0)
    }
}
