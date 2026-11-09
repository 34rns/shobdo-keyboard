package app.shobdo.keyboard.banglish

/** Tiny editorial seed contexts, separate from on-device learned contexts. */
class BigramTable private constructor(private val counts: Map<String, Map<String, Int>>) {
    fun bonus(previous: String, word: String): Double =
        ((counts[previous]?.get(word) ?: 0) * 18.0).coerceAtMost(90.0)
    companion object {
        const val ASSET_PATH = "banglish/bigrams.tsv"
        fun empty() = BigramTable(emptyMap())
        fun parse(text: String): BigramTable {
            val rows = HashMap<String, MutableMap<String, Int>>()
            for (line in text.lineSequence()) {
                val parts = line.split('\t')
                if (parts.size != 3) continue
                val count = parts[2].toIntOrNull()?.takeIf { it > 0 } ?: continue
                rows.getOrPut(parts[0]) { HashMap() }[parts[1]] = count
            }
            return BigramTable(rows)
        }
    }
}
