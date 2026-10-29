package app.shobdo.keyboard.ime

/**
 * Bangla composing rules: where a kar sign belongs, how hasanta joins a conjunct, and what
 * "one logical unit" means when backspace deletes.
 *
 * The rules are deliberately positional rather than linguistic. The keyboard only ever sees
 * the text it has produced, so it can answer both questions by looking at the tail of the
 * buffer.
 */
object BanglaComposer {

    const val HASANTA = '\u09CD' // ্  virama

    /** Dependent vowel signs. A letter takes at most one. */
    val KAR_SIGNS: Set<Char> = setOf('\u09BE', '\u09BF', '\u09C0', '\u09C1', '\u09C2', '\u09C3', '\u09C7', '\u09C8', '\u09CB', '\u09CC')

    /** Signs that hang off the end of a syllable: ঁ ং ঃ */
    val SIGNS: Set<Char> = setOf('\u0981', '\u0982', '\u0983')

    private const val E_KAR = '\u09C7' // ে
    private const val AA_KAR = '\u09BE' // া
    private const val O_KAR = '\u09CB' // ো, the precomposed ে + া

    fun isKar(ch: Char): Boolean = ch in KAR_SIGNS

    fun isSign(ch: Char): Boolean = ch in SIGNS

    /**
     * Appends [ch] at the right place.
     *
     * - a kar replaces any kar the letter already has, and goes before a trailing ং/ঃ/ঁ
     * - ে followed by া becomes the precomposed ো, because that is the single code point
     *   Android renders as one glyph
     * - hasanta is only added after a letter, never after a kar or another hasanta
     */
    fun append(text: CharSequence, ch: Char): String {
        val base = text.toString()
        return when {
            isKar(ch) -> appendKar(base, ch)
            ch == HASANTA -> appendHasanta(base)
            else -> base + ch
        }
    }

    private fun appendKar(base: String, kar: Char): String {
        var end = base.length
        while (end > 0 && isSign(base[end - 1])) end--
        if (end == 0) return base + kar
        val existing = base[end - 1]
        val head = base.substring(0, end - 1)
        val tail = base.substring(end)
        return when {
            existing == E_KAR && kar == AA_KAR -> head + O_KAR + tail
            isKar(existing) -> head + kar + tail
            existing == HASANTA -> base + kar
            else -> base.substring(0, end) + kar + tail
        }
    }

    private fun appendHasanta(base: String): String {
        if (base.isEmpty()) return base
        val last = base.last()
        return if (isKar(last) || isSign(last) || last == HASANTA) base else base + HASANTA
    }

    /** Appends a whole string, one code point at a time, through [append]. */
    fun appendAll(text: CharSequence, addition: String): String {
        var result = text.toString()
        for (ch in addition) result = append(result, ch)
        return result
    }

    /**
     * Length in chars of the last logical unit.
     *
     * A unit is one base letter with its kar and signs, including any hasanta-joined
     * consonants: ক্ষ is one unit, কো is one unit, া alone (no letter) is one unit.
     */
    fun lastUnitLength(text: CharSequence): Int {
        val n = text.length
        if (n == 0) return 0
        var i = n
        while (i > 0 && isSign(text[i - 1])) i--
        if (i > 0 && isKar(text[i - 1])) i--
        if (i == 0) return n
        i-- // the base letter itself
        while (i >= 2 && text[i - 1] == HASANTA) i -= 2 // ...ক্ before it, so the cluster goes too
        return n - i
    }

    /** Removes one logical unit from the end. */
    fun deleteLastUnit(text: CharSequence): String {
        val length = lastUnitLength(text)
        if (length == 0) return text.toString()
        return text.substring(0, text.length - length)
    }

    /** Splits text into logical units; used by tests and by the composer's own checks. */
    fun splitUnits(text: CharSequence): List<String> {
        val units = ArrayDeque<String>()
        var rest = text.toString()
        while (rest.isNotEmpty()) {
            val length = lastUnitLength(rest)
            units.addFirst(rest.substring(rest.length - length))
            rest = rest.substring(0, rest.length - length)
        }
        return units
    }
}
