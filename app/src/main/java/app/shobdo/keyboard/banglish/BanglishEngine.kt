package app.shobdo.keyboard.banglish

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class RuleDataException(message: String) : Exception(message)

/**
 * Roman-to-Bangla transliteration.
 *
 * The rules are data (`assets/banglish/phonetic_rules.json`), not code: this class only
 * implements the matching order. Longest match wins; ties are broken by table priority
 * (signs > digits > j-phala > consonants/vowels), which is what makes `kh` beat `k` and
 * `kSh` beat `k`.
 *
 * It is a pure Kotlin class with no Android dependency so every case can be unit tested on
 * the JVM against the same fixture (app/src/test/resources/banglish/cases.json) that
 * scripts/banglish_check.py uses.
 */
class BanglishEngine private constructor(private val rules: RuleSet) {

    /** Converts the whole Roman buffer. Unknown characters (punctuation, letters we have no rule for) pass through. */
    fun transliterate(roman: String): String {
        val out = StringBuilder(roman.length * 2)
        var index = 0
        var afterConsonant = false

        while (index < roman.length) {
            val ch = roman[index]

            // A bare `o` after a consonant is the inherent vowel; at the end of the buffer
            // it becomes the ো kar instead (bhalo -> ভালো, por -> পর).
            if (ch == 'o' && afterConsonant) {
                out.append(if (index == roman.length - 1) rules.trailingO else rules.medialO)
                afterConsonant = false
                index++
                continue
            }

            var best = match(roman, index, rules.signs, PRIORITY_SIGNS, Kind.SIGN)
            best = better(best, match(roman, index, rules.digits, PRIORITY_DIGITS, Kind.DIGIT))
            best = better(best, match(roman, index, rules.consonants, PRIORITY_CONSONANT, Kind.CONSONANT))
            best = if (afterConsonant) {
                better(
                    better(best, match(roman, index, rules.vowelKar, PRIORITY_VOWEL, Kind.KAR)),
                    match(roman, index, rules.jPhala, PRIORITY_J_PHALA, Kind.J_PHALA)
                )
            } else {
                better(best, match(roman, index, rules.vowelStandalone, PRIORITY_VOWEL, Kind.STANDALONE))
            }

            if (best == null) {
                out.append(ch)
                afterConsonant = false
                index++
                continue
            }

            when (best.kind) {
                Kind.CONSONANT -> {
                    if (afterConsonant) out.append(rules.hasanta)
                    out.append(best.bangla)
                    afterConsonant = true
                }
                Kind.J_PHALA -> {
                    // ্য ends in য, so a following vowel is a kar, not a standalone letter.
                    out.append(best.bangla)
                    afterConsonant = true
                }
                else -> {
                    out.append(best.bangla)
                    afterConsonant = false
                }
            }
            index += best.length
        }
        return out.toString()
    }

    /**
     * What the suggestion bar shows for a Roman buffer: the Bangla candidate and the Roman
     * text as typed, in the order the user chose in settings.
     */
    fun suggestions(roman: String, banglaFirst: Boolean): List<String> {
        if (roman.isEmpty()) return emptyList()
        val bangla = transliterate(roman)
        return if (banglaFirst) listOf(bangla, roman) else listOf(roman, bangla)
    }

    private fun match(text: String, index: Int, table: List<Rule>, priority: Int, kind: Kind): Match? {
        var best: Match? = null
        for (rule in table) {
            if (text.startsWith(rule.roman, index)) {
                if (best == null || rule.roman.length > best.length) {
                    best = Match(rule.roman.length, priority, kind, rule.bangla)
                }
            }
        }
        return best
    }

    private fun better(current: Match?, candidate: Match?): Match? {
        if (candidate == null) return current
        if (current == null) return candidate
        val longer = candidate.length > current.length
        val tieButStronger = candidate.length == current.length && candidate.priority > current.priority
        return if (longer || tieButStronger) candidate else current
    }

    private class Match(val length: Int, val priority: Int, val kind: Kind, val bangla: String)

    private enum class Kind { SIGN, DIGIT, CONSONANT, KAR, J_PHALA, STANDALONE }

    private class Rule(val roman: String, val bangla: String)

    private class RuleSet(
        val consonants: List<Rule>,
        val vowelStandalone: List<Rule>,
        val vowelKar: List<Rule>,
        val jPhala: List<Rule>,
        val signs: List<Rule>,
        val digits: List<Rule>,
        val hasanta: String,
        val trailingO: String,
        val medialO: String
    )

    companion object {
        /** Path inside the APK's assets. */
        const val RULE_ASSET_PATH = "banglish/phonetic_rules.json"

        private const val PRIORITY_SIGNS = 4
        private const val PRIORITY_DIGITS = 3
        private const val PRIORITY_J_PHALA = 2
        private const val PRIORITY_CONSONANT = 1
        private const val PRIORITY_VOWEL = 1

        private val json = Json { ignoreUnknownKeys = true }

        @Serializable
        private data class RulesDto(
            val behavior: BehaviourDto,
            val rules: TablesDto
        )

        @Serializable
        private data class BehaviourDto(
            val hasanta: String,
            val oAfterConsonant: OAfterConsonantDto
        )

        @Serializable
        private data class OAfterConsonantDto(
            val trailing: String,
            val medial: String = ""
        )

        @Serializable
        private data class TablesDto(
            val consonants: List<RuleDto> = emptyList(),
            val vowelStandalone: List<RuleDto> = emptyList(),
            val vowelKar: List<RuleDto> = emptyList(),
            val jPhala: List<RuleDto> = emptyList(),
            val signs: List<RuleDto> = emptyList(),
            val digits: List<RuleDto> = emptyList()
        )

        @Serializable
        private data class RuleDto(val roman: String, val bangla: String)

        fun fromJson(source: String): BanglishEngine {
            val dto = try {
                json.decodeFromString(RulesDto.serializer(), source)
            } catch (e: Exception) {
                throw RuleDataException("phonetic rules are not readable: ${e.message}")
            }
            fun check(name: String, rules: List<RuleDto>): List<Rule> {
                val seen = HashSet<String>()
                return rules.map { rule ->
                    if (rule.roman.isEmpty()) throw RuleDataException("$name has a rule with no roman text")
                    if (rule.bangla.isEmpty()) throw RuleDataException("$name rule '${rule.roman}' has no bangla text")
                    if (!seen.add(rule.roman)) throw RuleDataException("$name has a duplicate roman '${rule.roman}'")
                    Rule(rule.roman, rule.bangla)
                }
            }
            val tables = dto.rules
            val set = RuleSet(
                consonants = check("consonants", tables.consonants),
                vowelStandalone = check("vowelStandalone", tables.vowelStandalone),
                vowelKar = check("vowelKar", tables.vowelKar),
                jPhala = check("jPhala", tables.jPhala),
                signs = check("signs", tables.signs),
                digits = check("digits", tables.digits),
                hasanta = dto.behavior.hasanta,
                trailingO = dto.behavior.oAfterConsonant.trailing,
                medialO = dto.behavior.oAfterConsonant.medial
            )
            if (set.hasanta.isEmpty()) throw RuleDataException("behavior.hasanta is empty")
            if (set.consonants.isEmpty()) throw RuleDataException("no consonant rules")
            return BanglishEngine(set)
        }
    }
}
