package app.shobdo.keyboard.banglish

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer

/**
 * The skeleton key: a canonical spelling that ignores the differences people type.
 *
 * `ami`, `aami` and `amee` all reduce to the same skeleton, and so does the Bangla word
 * আমি, because both directions go through one table (`assets/banglish/skeleton_rules.json`).
 * That is what lets a Roman buffer find Bangla words the phonetic rules alone would miss,
 * and it is why the build tool and the app read the same file instead of keeping two copies.
 *
 * Pure Kotlin: no Android, so the parity with the Python builder can be unit tested.
 */
class SkeletonRules private constructor(
    private val units: List<Pair<String, String>>,
    private val letters: Map<Char, String>,
    private val bangla: Map<String, String>
) {

    /** Canonical form of a Roman buffer. Unknown characters are skipped. */
    fun ofRoman(text: String): String {
        val out = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            var matched = false
            for ((unit, canonical) in units) {
                if (text.startsWith(unit, index)) {
                    out.append(canonical)
                    index += unit.length
                    matched = true
                    break
                }
            }
            if (matched) continue
            val canonical = letters[text[index]]
            if (canonical != null) out.append(canonical)
            index++
        }
        return out.toString()
    }

    /** Canonical form of a Bangla word. */
    fun ofBangla(word: String): String {
        // Invisible joiners and the silent ya-phala never change how a word is typed.
        val text = Normalizer.normalize(word, Normalizer.Form.NFC)
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace("\u09CD\u09AF", "")
        val out = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            if (index + 1 < text.length) {
                val pair = bangla[text.substring(index, index + 2)]
                if (pair != null) {
                    out.append(pair)
                    index += 2
                    continue
                }
            }
            val single = bangla[text[index].toString()]
            if (single != null) out.append(single)
            index++
        }
        return out.toString()
    }

    companion object {
        /** Path inside the APK's assets. */
        const val ASSET_PATH = "banglish/skeleton_rules.json"

        @Serializable
        private data class RulesDto(
            val version: Int = 1,
            val romanUnits: List<List<String>> = emptyList(),
            val romanLetters: Map<String, String> = emptyMap(),
            val bangla: Map<String, String> = emptyMap()
        )

        private val json = Json { ignoreUnknownKeys = true }

        /** Used when the table cannot be read, so the keyboard still types phonetically. */
        fun empty(): SkeletonRules = SkeletonRules(emptyList(), emptyMap(), emptyMap())

        /** @throws BanglishDataException when the table is missing or malformed. */
        fun fromJson(source: String): SkeletonRules {
            val dto = try {
                json.decodeFromString(RulesDto.serializer(), source)
            } catch (e: Exception) {
                throw BanglishDataException("skeleton rules are not readable: ${e.message}")
            }
            if (dto.bangla.isEmpty()) throw BanglishDataException("skeleton rules have no Bangla table")
            if (dto.romanLetters.isEmpty()) throw BanglishDataException("skeleton rules have no letter table")
            val units = dto.romanUnits
                .mapNotNull { row -> row.takeIf { it.size == 2 }?.let { it[0] to it[1] } }
                // Longest first, so a two-letter unit always wins over its first letter.
                .sortedByDescending { it.first.length }
            val letters = dto.romanLetters
                .filterKeys { it.length == 1 }
                .mapKeys { it.key[0] }
            return SkeletonRules(units, letters, dto.bangla)
        }
    }
}

/** Thrown when the shipped Banglish data cannot be read. */
class BanglishDataException(message: String) : Exception(message)
