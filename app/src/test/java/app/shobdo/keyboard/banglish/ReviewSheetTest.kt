package app.shobdo.keyboard.banglish

import org.junit.Test
import java.io.File

/**
 * Builds `docs/banglish_review.md`: the cases the engine gets wrong or nearly wrong, with the
 * five candidates it offers, so a Bangla speaker can mark the bad ones.
 * Run with `--tests "*ReviewSheetTest*"` after rebuilding the lexicon.
 */
class ReviewSheetTest {

    @Test
    fun writeReviewSheet() {
        val assets = File("src/main/assets")
        val skeleton = SkeletonRules.fromJson(
            File(assets, SkeletonRules.ASSET_PATH).readText(Charsets.UTF_8)
        )
        val lexicon = BanglishLexicon.parse(
            File(assets, BanglishLexicon.ASSET_PATH).readLines(Charsets.UTF_8)
        )
        val phonetic = BanglishEngine.fromJson(
            File(assets, BanglishEngine.RULE_ASSET_PATH).readText(Charsets.UTF_8)
        )
        val engine = CandidateEngine(phonetic, skeleton, lexicon)

        val rows = File("src/test/resources/banglish_cases.tsv").readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split('\t') }
            .filter { it.size >= 2 }

        // A word is "hard" when the recorded answer is not the engine's first choice. The
        // ones it cannot reach at all come first, then the ones it pushes down the list.
        val hard = rows.mapNotNull { row ->
            val roman = row[0]
            val expected = row[1]
            val ranked = engine.candidates(roman, 5).map { it.text }
            val position = ranked.indexOf(expected)
            if (position == 0) null else Triple(roman, expected, ranked) to position
        }.sortedByDescending { it.second }.map { it.first }.take(100)

        val out = StringBuilder()
        out.appendLine("# Banglish review sheet")
        out.appendLine()
        out.appendLine("Every line is a case the engine ranks low. Tick the candidate that is")
        out.appendLine("actually the word a Bangla speaker would type, or write the right one.")
        out.appendLine("Until someone does, nothing in this list is verified.")
        out.appendLine()
        out.appendLine("| Roman typed | expected | engine candidates (best first) | ok? |")
        out.appendLine("|---|---|---|---|")
        for ((roman, expected, ranked) in hard) {
            val cells = ranked.joinToString(" · ") { it }
            out.appendLine("| `$roman` | $expected | $cells | |")
        }
        out.appendLine()
        out.appendLine("${hard.size} of ${rows.size} cases are listed.")
        val target = File("../docs/banglish_review.md")
        target.parentFile.mkdirs()
        target.writeText(out.toString(), Charsets.UTF_8)
    }
}
