package app.shobdo.keyboard.banglish

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.expm1
import kotlin.math.roundToInt

/** Bounded local counts. I/O is coalesced on a worker; no transcript is retained. */
class LearnedWords(private val file: File, private val now: () -> Long = System::currentTimeMillis) {
    private data class Count(val value: Int, val time: Long)
    data class DictionaryWord(val roman: String, val word: String)
    private val counts = HashMap<String, Count>()
    private val contexts = HashMap<Pair<String, String>, Count>()
    private val manual = LinkedHashMap<String, String>()
    private var pending: ScheduledFuture<*>? = null
    var storageError = false
        private set

    init {
        runCatching {
            if (file.exists()) file.forEachLine { line ->
                val p = line.split('\t')
                when {
                    p.size == 2 -> p[1].toIntOrNull()?.takeIf { it > 0 }?.let { counts[p[0]] = Count(it.coerceAtMost(10000), now()) }
                    p.size == 4 && p[0] == "G" -> readCount(p[2], p[3])?.let { counts[p[1]] = it }
                    p.size == 5 && p[0] == "C" -> readCount(p[3], p[4])?.let { contexts[p[1] to p[2]] = it }
                    p.size == 3 && p[0] == "M" && valid(p[1], p[2]) -> manual[p[2]] = p[1]
                }
            }
            trim()
        }.onFailure { storageError = true }
    }
    private fun readCount(value: String, time: String): Count? {
        val n = value.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val t = time.toLongOrNull()?.coerceIn(0, now()) ?: return null
        return Count(n.coerceAtMost(10000), t)
    }
    @get:Synchronized val size: Int get() = counts.size
    @get:Synchronized val contextSize: Int get() = contexts.size
    @Synchronized fun dictionary() = manual.map { DictionaryWord(it.value, it.key) }.sortedBy { it.word }
    @Synchronized fun matches(roman: String) = manual.filterValues { it.equals(roman, true) }.keys.toList()
    private fun strength(c: Count, at: Long = now()) = ln(1.0 + c.value) * 0.5.pow((at - c.time).coerceAtLeast(0) / HALF_LIFE_MS)
    @Synchronized fun bonus(word: String, previous: String = ""): Double =
        (counts[word]?.let { strength(it) * 85 } ?: 0.0).coerceAtMost(250.0) +
        (contexts[previous to word]?.let { strength(it) * 100 } ?: 0.0).coerceAtMost(160.0)
    @Synchronized fun record(word: String, previous: String = "") {
        if (word.isBlank() || word.length > 40 || word.any { it == '\t' || it == '\n' || it == '\r' }) return
        val at = now()
        fun increment(old: Count?) = Count(
            ((old?.let { expm1(strength(it, at)).roundToInt() } ?: 0) + 1).coerceAtMost(10000), at)
        counts[word] = increment(counts[word])
        if (previous.isNotBlank() && previous.length <= 40 && previous.none { it == '\t' || it == '\n' || it == '\r' }) {
            val key = previous to word
            contexts[key] = increment(contexts[key])
        }
        trim()
        saveLater()
    }
    private fun trim() {
        val at = now()
        if (counts.size > MAX_WORDS) counts.keys.retainAll(counts.entries.sortedByDescending { strength(it.value, at) }.take(MAX_WORDS).map { it.key }.toSet())
        if (contexts.size > MAX_CONTEXTS) contexts.keys.retainAll(contexts.entries.sortedByDescending { strength(it.value, at) }.take(MAX_CONTEXTS).map { it.key }.toSet())
        while (manual.size > MAX_MANUAL) manual.remove(manual.keys.first())
    }
    @Synchronized fun add(roman: String, word: String): Boolean {
        if (!valid(roman, word) || (manual.size >= MAX_MANUAL && word !in manual)) return false
        manual[word] = roman.lowercase()
        saveLater()
        return true
    }
    @Synchronized fun remove(word: String) {
        manual.remove(word)
        counts.remove(word)
        contexts.keys.removeAll { it.first == word || it.second == word }
        saveLater()
    }
    /** Explicit dictionary words survive clearing learned choices. */
    @Synchronized fun clear() { counts.clear(); contexts.clear(); saveLater() }
    private fun saveLater() {
        pending?.cancel(false)
        pending = writer.schedule({ flush() }, 80, TimeUnit.MILLISECONDS)
    }
    /** Worker-only persistence; callable in tests for a deterministic disk round trip. */
    @Synchronized fun flush(): Boolean = runCatching {
        file.parentFile?.mkdirs()
        val text = buildString {
            for ((word, c) in counts) append("G\t$word\t${c.value}\t${c.time}\n")
            for ((key, c) in contexts) append("C\t${key.first}\t${key.second}\t${c.value}\t${c.time}\n")
            for ((word, roman) in manual) append("M\t$roman\t$word\n")
        }
        val temporary = File(file.path + ".tmp")
        temporary.writeText(text, Charsets.UTF_8)
        java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        storageError = false
        true
    }.getOrElse { storageError = true; false }

    companion object {
        const val FILE_NAME = "banglish_learned.tsv"
        const val MAX_WORDS = 400
        const val MAX_CONTEXTS = 800
        const val MAX_MANUAL = 200
        const val HALF_LIFE_MS = 30.0 * 24 * 60 * 60 * 1000
        private val writer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "shobdo-learning").apply { isDaemon = true } }
        private val shared = HashMap<String, LearnedWords>()
        /** Load on the asset worker; Settings and IME share one instance. */
        @Synchronized fun open(file: File) = shared.getOrPut(file.absolutePath) { LearnedWords(file) }
        private fun valid(roman: String, word: String) = roman.length in 1..40 &&
            roman.all { it in 'a'..'z' || it in 'A'..'Z' } && word.length in 1..40 &&
            Character.isLetter(word.first()) && word.all {
                it.code in 0x0980..0x09FF && (Character.isLetter(it) ||
                    Character.getType(it) == Character.NON_SPACING_MARK.toInt() ||
                    Character.getType(it) == Character.COMBINING_SPACING_MARK.toInt())
            }
    }
}
