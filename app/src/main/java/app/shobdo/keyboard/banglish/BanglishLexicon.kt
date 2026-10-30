package app.shobdo.keyboard.banglish

import java.io.File

/**
 * The shipped Bangla word list, indexed by skeleton.
 *
 * `lexicon.bin` is a UTF-8 file of `skeleton<TAB>word<TAB>count` lines sorted by skeleton,
 * built offline by `tools/build_banglish_lexicon.py`. At runtime it is read once on a
 * background thread into two structures: a sorted array of skeletons for prefix search, and a
 * map from skeleton to its words, most frequent first.
 *
 * 44k words is about 1.3 MB on disk and a few MB of heap, which is why loading it never
 * happens on the thread that draws the keyboard: [isReady] is false until it is done and the
 * engine simply offers the phonetic reading in the meantime.
 */
class BanglishLexicon private constructor(
    private val skeletons: Array<String>,
    private val bySkeleton: Map<String, List<Word>>
) {

    data class Word(val text: String, val count: Int)

    val size: Int get() = skeletons.size

    val wordCount: Int get() = bySkeleton.values.sumOf { it.size }

    /** Words whose skeleton is exactly [skeleton], most frequent first. */
    fun exact(skeleton: String, limit: Int = 12): List<Word> =
        bySkeleton[skeleton]?.take(limit).orEmpty()

    /** Words whose skeleton continues [skeleton], used for completions. */
    fun prefix(skeleton: String, limit: Int = 12): List<Word> {
        if (skeleton.isEmpty()) return emptyList()
        val start = lowerBound(skeleton)
        val result = ArrayList<Word>(limit)
        var index = start
        while (index < skeletons.size && skeletons[index].startsWith(skeleton)) {
            val words = bySkeleton[skeletons[index]].orEmpty()
            for (word in words) {
                if (result.size >= limit) return result
                result.add(word)
            }
            index++
        }
        return result.sortedByDescending { it.count }.take(limit)
    }

    /**
     * Words one edit away from [skeleton], to catch a typo. Deletions only: insertion and
     * transposition on a Roman buffer produce far more noise than they remove.
     */
    fun near(skeleton: String, limit: Int = 8): List<Word> {
        if (skeleton.length < 3) return emptyList()
        val seen = HashSet<String>()
        val result = ArrayList<Word>()
        for (index in skeleton.indices) {
            val candidate = skeleton.removeRange(index, index + 1)
            if (!seen.add(candidate)) continue
            for (word in exact(candidate, limit)) {
                if (result.size >= limit) return result
                result.add(word)
            }
        }
        return result
    }

    private fun lowerBound(key: String): Int {
        var low = 0
        var high = skeletons.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (skeletons[middle] < key) low = middle + 1 else high = middle
        }
        return low
    }

    companion object {
        const val ASSET_PATH = "banglish/lexicon.bin"

        fun parse(lines: List<String>): BanglishLexicon {
            val grouped = HashMap<String, MutableList<Word>>(lines.size)
            for (line in lines) {
                if (line.isEmpty() || line[0] == '#') continue
                val first = line.indexOf('\t')
                if (first <= 0) continue
                val second = line.indexOf('\t', first + 1)
                if (second <= first) continue
                val skeleton = line.substring(0, first)
                val word = line.substring(first + 1, second)
                val count = line.substring(second + 1).toIntOrNull() ?: continue
                grouped.getOrPut(skeleton) { ArrayList(2) }.add(Word(word, count))
            }
            val ordered = grouped.mapValues { (_, words) -> words.sortedByDescending { it.count } }
            val skeletons = ordered.keys.sorted().toTypedArray()
            return BanglishLexicon(skeletons, ordered)
        }

        fun load(file: File): BanglishLexicon =
            parse(file.readLines(Charsets.UTF_8))

        /** Empty lexicon: the engine still works, using the phonetic rules only. */
        fun empty(): BanglishLexicon = BanglishLexicon(emptyArray(), emptyMap())
    }
}
