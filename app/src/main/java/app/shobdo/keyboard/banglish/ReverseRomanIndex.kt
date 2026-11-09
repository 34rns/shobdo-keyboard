package app.shobdo.keyboard.banglish

import java.nio.ByteBuffer
import java.util.PriorityQueue

/** Packed sorted Roman keys with word IDs: no map/trie node per generated spelling. */
class ReverseRomanIndex private constructor(
    private val data: ByteBuffer,
    private val offsets: IntArray,
    private val words: Array<BanglishLexicon.Word>
) {
    val wordCount: Int get() = words.size
    val rowCount: Int get() = offsets.size

    private fun length(row: Int): Int = data.getShort(offsets[row]).toInt() and 0xffff
    private fun word(row: Int): BanglishLexicon.Word = words[data.getInt(offsets[row] + 2 + length(row))]
    private fun compare(row: Int, key: String, prefix: Boolean = false): Int {
        val length = length(row)
        val offset = offsets[row] + 2
        for (i in 0 until minOf(length, key.length)) {
            val difference = (data.get(offset + i).toInt() and 0xff) - key[i].code
            if (difference != 0) return difference
        }
        return if (prefix && length >= key.length) 0 else length - key.length
    }
    private fun lowerBound(key: String): Int {
        var low = 0
        var high = offsets.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (compare(mid, key) < 0) low = mid + 1 else high = mid
        }
        return low
    }
    fun exact(key: String, limit: Int = 40): List<BanglishLexicon.Word> {
        if (key.isEmpty() || key.length > MAX_INPUT) return emptyList()
        val result = ArrayList<BanglishLexicon.Word>()
        var row = lowerBound(key)
        while (row < offsets.size && compare(row, key) == 0 && result.size < limit) {
            result.add(word(row++))
        }
        return result
    }
    fun prefix(key: String, limit: Int = 40): List<BanglishLexicon.Word> {
        if (key.length !in 3..MAX_INPUT || limit <= 0) return emptyList()
        val ranked = PriorityQueue<BanglishLexicon.Word>(compareBy { it.count })
        val seen = HashSet<String>()
        var row = lowerBound(key)
        val end = minOf(offsets.size, row + PREFIX_SCAN_LIMIT)
        while (row < end && compare(row, key, prefix = true) == 0) {
            val word = word(row++)
            if (!seen.add(word.text)) continue
            ranked.add(word)
            if (ranked.size > limit) ranked.poll()
        }
        return ranked.sortedWith(compareByDescending<BanglishLexicon.Word> { it.count }.thenBy { it.text })
    }
    /** Bounded Levenshtein distance one, including insertions and substitutions. */
    fun near(key: String, limit: Int = 12): List<BanglishLexicon.Word> {
        if (key.length !in 3..MAX_FUZZY_INPUT) return emptyList()
        val result = HashMap<String, BanglishLexicon.Word>()
        fun offer(variant: String) {
            for (word in exact(variant, limit)) result[word.text] = word
        }
        val collapsed = key.replace(Regex("(.)\\1+"), "$1")
        if (collapsed != key) offer(collapsed)
        for (i in key.indices) {
            offer(key.removeRange(i, i + 1))
            for (letter in ALPHABET) offer(key.substring(0, i) + letter + key.substring(i + 1))
        }
        for (i in 0..key.length) {
            for (letter in ALPHABET) offer(key.substring(0, i) + letter + key.substring(i))
        }
        return result.values.sortedByDescending { it.count }.take(limit)
    }
    companion object {
        const val ASSET_PATH = "banglish/reverse.bin"
        const val MAX_INPUT = 40
        private const val MAX_FUZZY_INPUT = 20
        private const val PREFIX_SCAN_LIMIT = 4096
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz"
        fun empty() = ReverseRomanIndex(ByteBuffer.allocate(0), IntArray(0), emptyArray())
        fun parse(bytes: ByteArray): ReverseRomanIndex {
            try {
                require(bytes.size in 12..40_000_000)
                val buffer = ByteBuffer.wrap(bytes).asReadOnlyBuffer()
                require(buffer.int == 0x53524931)
                val count = buffer.int
                require(count in 0..200_000)
                val words = Array(count) {
                    val size = buffer.short.toInt() and 0xffff
                    require(size in 1..200)
                    val text = ByteArray(size)
                    buffer.get(text)
                    val weight = buffer.int
                    require(weight > 0)
                    BanglishLexicon.Word(String(text, Charsets.UTF_8), weight)
                }
                val rows = buffer.int
                require(rows in 0..2_000_000)
                val offsets = IntArray(rows) {
                    val position = buffer.position()
                    val size = buffer.short.toInt() and 0xffff
                    require(size in 1..MAX_INPUT)
                    repeat(size) { require(buffer.get().toInt() in 97..122) }
                    require(buffer.int in words.indices)
                    position
                }
                require(!buffer.hasRemaining())
                return ReverseRomanIndex(buffer, offsets, words)
            } catch (e: Exception) {
                throw BanglishDataException("reverse index is not readable: ${e.message}")
            }
        }
    }
}
