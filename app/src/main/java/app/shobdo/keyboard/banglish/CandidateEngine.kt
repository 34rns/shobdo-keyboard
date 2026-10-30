package app.shobdo.keyboard.banglish

import kotlin.math.log10

/**
 * Turns a Roman buffer into every reasonable Bangla reading, best first.
 *
 * Six sources feed one ranked list:
 *  1. the exact Avro phonetic reading,
 *  2. lexicon words with the same skeleton,
 *  3. lexicon words whose skeleton continues the buffer (completions),
 *  4. words one edit away, only while there are few real matches,
 *  5. single-substitution variants of the skeleton (o/a, i/e, t/th, s/…),
 *  6. the Roman text itself, always present.
 *
 * Ranking is frequency-led, so the ordinary word wins over an obscure one with the same
 * letters, and a learned bonus moves words the user has chosen before to the front. Pure
 * Kotlin: every rule here is unit tested.
 */
class CandidateEngine(
    private val phonetic: BanglishEngine,
    private val skeleton: SkeletonRules,
    private val lexicon: BanglishLexicon,
    private val learned: LearnedWords? = null
) {

    data class Candidate(
        val text: String,
        val bangla: Boolean,
        val score: Double,
        val source: Source
    )

    enum class Source { EXACT, PHONETIC, PREFIX, NEAR, VARIANT, ROMAN }

    /** Ranked candidates for [roman]; the Roman text is always within the first three. */
    fun candidates(roman: String, limit: Int = 40): List<Candidate> {
        if (roman.isBlank()) return emptyList()
        val key = skeleton.ofRoman(roman)
        val phoneticText = phonetic.transliterate(roman)
        val ranked = LinkedHashMap<String, Candidate>(16)

        fun offer(text: String, bangla: Boolean, score: Double, source: Source) {
            if (text.isEmpty()) return
            val bonus = if (bangla) learnedBonus(text) else 0.0
            val adjusted = score + bonus
            val existing = ranked[text]
            if (existing == null || adjusted > existing.score) {
                ranked[text] = Candidate(text, bangla, adjusted, source)
            }
        }

        val exact = if (key.isEmpty()) emptyList() else lexicon.exact(key, EXACT_LIMIT)
        for (word in exact) {
            offer(word.text, true, EXACT_SCORE + frequency(word.count), Source.EXACT)
        }
        offer(
            phoneticText,
            true,
            if (exact.any { it.text == phoneticText }) EXACT_SCORE + PHONETIC_AGREEMENT_BONUS else PHONETIC_SCORE,
            Source.PHONETIC
        )

        if (key.length >= MIN_PREFIX_LENGTH) {
            for (word in lexicon.prefix(key, PREFIX_LIMIT)) {
                val extra = (key.length - word.text.length).coerceAtMost(0)
                offer(word.text, true, PREFIX_SCORE + frequency(word.count) + extra, Source.PREFIX)
            }
        }

        if (exact.size < NEAR_THRESHOLD && key.length >= MIN_PREFIX_LENGTH) {
            for (word in lexicon.near(key, NEAR_LIMIT)) {
                offer(word.text, true, NEAR_SCORE + frequency(word.count), Source.NEAR)
            }
        }

        if (exact.size < VARIANT_THRESHOLD) {
            for (variant in variantKeys(key)) {
                for (word in lexicon.exact(variant, VARIANT_WORDS_PER_KEY)) {
                    offer(word.text, true, VARIANT_SCORE + frequency(word.count), Source.VARIANT)
                }
            }
        }

        offer(roman, false, ROMAN_SCORE, Source.ROMAN)

        val sorted = ranked.values.sortedWith(
            compareByDescending<Candidate> { it.score }.thenBy { it.text }
        )
        return keepRomanNearTop(sorted).take(limit)
    }

    /** The Bangla reading of [roman] alone, for a caller that wants one answer. */
    fun best(roman: String): String = phonetic.transliterate(roman)

    private fun learnedBonus(word: String): Double = learned?.bonus(word) ?: 0.0

    private fun frequency(count: Int): Double = log10(count.coerceAtLeast(1) + 1.0) * FREQUENCY_WEIGHT

    /** The Roman text is a candidate too, and the spec keeps it in the top three. */
    private fun keepRomanNearTop(candidates: List<Candidate>): List<Candidate> {
        val romanIndex = candidates.indexOfFirst { !it.bangla }
        if (romanIndex <= ROMAN_MAX_INDEX) return candidates
        val mutable = candidates.toMutableList()
        val roman = mutable.removeAt(romanIndex)
        mutable.add(ROMAN_MAX_INDEX, roman)
        return mutable
    }

    /** Every skeleton reachable by swapping one token for its common alternative. */
    private fun variantKeys(key: String): List<String> {
        if (key.isEmpty()) return emptyList()
        val tokens = tokenize(key)
        if (tokens.isEmpty()) return emptyList()
        val variants = ArrayList<String>(MAX_VARIANTS)
        // A medial "o" is the inherent vowel, which the scheme leaves unwritten: কেমন is
        // "kemn" in the lexicon, so someone typing "kemon" must still find it. Typing
        // "dhonnobad" needs every medial o gone at once, which is why the wholesale form
        // comes first and a doubled consonant is then collapsed on top of it.
        if (tokens.size > 2) {
            val withoutO = StringBuilder(key.length)
            for (position in tokens.indices) {
                if (position != 0 && position != tokens.lastIndex && tokens[position] == "o") continue
                withoutO.append(tokens[position])
            }
            for (text in listOf(withoutO.toString(), collapseDoubles(withoutO.toString()))) {
                if (text.isNotEmpty() && text != key) variants.add(text)
            }
            for (index in 0 until tokens.size - 1) {
                if (tokens[index] != "o") continue
                val builder = StringBuilder(key.length)
                for (position in tokens.indices) {
                    if (position != index) builder.append(tokens[position])
                }
                variants.add(builder.toString())
            }
        }
        for (index in tokens.indices) {
            val replacements = ALTERNATIVES[tokens[index]] ?: continue
            for (replacement in replacements) {
                if (variants.size >= MAX_VARIANTS) return variants
                val builder = StringBuilder(key.length + 2)
                for (position in tokens.indices) {
                    builder.append(if (position == index) replacement else tokens[position])
                }
                variants.add(builder.toString())
            }
        }
        return variants
    }

    /** "dhnnbad" and ধন্যবাদ's "dhnbad" are the same word typed with one letter too many. */
    private fun collapseDoubles(key: String): String {
        val tokens = tokenize(key)
        val out = StringBuilder(key.length)
        var previous = ""
        for (token in tokens) {
            if (token == previous && token !in VOWEL_TOKENS) continue
            out.append(token)
            previous = token
        }
        return out.toString()
    }

    /** Splits a skeleton into units using the same longest-match rule as the table. */
    private fun tokenize(key: String): List<String> {
        val tokens = ArrayList<String>(key.length)
        var index = 0
        while (index < key.length) {
            var matched: String? = null
            for (unit in MULTI_UNITS) {
                if (key.startsWith(unit, index)) {
                    matched = unit
                    break
                }
            }
            if (matched != null) {
                tokens.add(matched)
                index += matched.length
            } else {
                tokens.add(key[index].toString())
                index++
            }
        }
        return tokens
    }

    companion object {
        private const val EXACT_SCORE = 1000.0
        private const val PHONETIC_AGREEMENT_BONUS = 60.0
        private const val PHONETIC_SCORE = 700.0
        private const val PREFIX_SCORE = 500.0
        private const val NEAR_SCORE = 400.0
        private const val VARIANT_SCORE = 300.0
        private const val ROMAN_SCORE = 100.0
        private const val FREQUENCY_WEIGHT = 14.0
        private const val EXACT_LIMIT = 10
        private const val PREFIX_LIMIT = 10
        private const val NEAR_LIMIT = 6
        private const val NEAR_THRESHOLD = 8
        private const val VARIANT_THRESHOLD = 4
        private const val VARIANT_WORDS_PER_KEY = 2
        private const val MIN_PREFIX_LENGTH = 3
        private const val MAX_VARIANTS = 50
        private const val ROMAN_MAX_INDEX = 2

        private val VOWEL_TOKENS = setOf("a", "i", "u", "e", "o")

        private val MULTI_UNITS = listOf("kh", "gh", "ch", "jh", "th", "dh", "ph", "bh", "ng", "ri", "oi", "ou")

        /** Tokens a typist swaps for one another, from the scheme's own ambiguities. */
        private val ALTERNATIVES: Map<String, List<String>> = mapOf(
            "o" to listOf("a", "u"), "a" to listOf("o", "e"), "i" to listOf("e", "u"),
            "u" to listOf("o", "i"), "e" to listOf("i", "o"),
            "t" to listOf("th"), "th" to listOf("t"),
            "d" to listOf("dh"), "dh" to listOf("d"),
            "n" to listOf("ng"), "ng" to listOf("n"),
            "j" to listOf("y"), "y" to listOf("j"),
            "r" to listOf("ri"), "ri" to listOf("r"),
            "b" to listOf("bh"), "bh" to listOf("b"),
            "p" to listOf("ph"), "ph" to listOf("p"),
            "k" to listOf("kh"), "kh" to listOf("k"),
            "g" to listOf("gh"), "gh" to listOf("g"),
            "c" to listOf("ch"), "ch" to listOf("c")
        )
    }
}
