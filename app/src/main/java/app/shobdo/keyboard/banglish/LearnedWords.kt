package app.shobdo.keyboard.banglish

import java.io.File

/**
 * The words this user actually picks.
 *
 * Stored in the app's private storage only, as `word<TAB>count` lines, and never written for
 * a password field. It exists to move a familiar word above a more frequent stranger with the
 * same letters, and it can be switched off or cleared in settings.
 *
 * Instances are cheap: the file is a few kilobytes, read once when the session is built.
 */
class LearnedWords(private val file: File) {

    private val counts = HashMap<String, Int>(64)
    private var highest = 1

    init {
        runCatching {
            if (file.exists()) {
                file.forEachLine { line ->
                    val tab = line.indexOf('\t')
                    if (tab > 0) {
                        val count = line.substring(tab + 1).toIntOrNull() ?: return@forEachLine
                        counts[line.substring(0, tab)] = count
                        if (count > highest) highest = count
                    }
                }
            }
        }
    }

    val size: Int get() = counts.size

    /** 0 for an unknown word, up to [MAX_BONUS] for the user's most used one. */
    fun bonus(word: String): Double {
        val count = counts[word] ?: return 0.0
        return MAX_BONUS * (count.toDouble() / highest)
    }

    fun record(word: String) {
        if (word.isBlank() || word.length > 40) return
        val next = (counts[word] ?: 0) + 1
        counts[word] = next
        if (next > highest) highest = next
        if (counts.size > MAX_WORDS) {
            // Keep the strongest entries; the list is a nudge, not a history.
            val keep = counts.entries.sortedByDescending { it.value }.take(MAX_WORDS / 2)
            counts.clear()
            keep.forEach { (key, value) -> counts[key] = value }
        }
        save()
    }

    fun clear() {
        counts.clear()
        highest = 1
        runCatching { file.delete() }
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            val text = counts.entries
                .sortedByDescending { it.value }
                .joinToString(separator = "\n") { "${it.key}\t${it.value}" }
            file.writeText(text, Charsets.UTF_8)
        }
    }

    companion object {
        const val FILE_NAME = "banglish_learned.tsv"
        private const val MAX_BONUS = 250.0
        private const val MAX_WORDS = 400
    }
}
