package app.shobdo.keyboard.ime

/**
 * The three languages the keyboard can type. [displayName] is what the spacebar shows,
 * each in its own script, so the label never gives away which language is active only in
 * English.
 */
enum class Language(val id: String, val displayName: String) {
    ENGLISH("english", "English"),
    BANGLA("bangla", "বাংলা"),
    BANGLISH("banglish", "Banglish");

    /** Bangla and Banglish both show Bangla options in the suggestion bar. */
    val isBanglaScript: Boolean get() = this == BANGLA || this == BANGLISH

    companion object {
        fun fromId(id: String?): Language? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Which languages the globe key may visit. Pure: the settings repository decides the
 * booleans, this decides what they mean.
 */
object LanguageCycle {

    /** Fixed order the globe key follows: English -> Bangla -> Banglish. */
    val ORDER: List<Language> = listOf(Language.ENGLISH, Language.BANGLA, Language.BANGLISH)

    fun enabled(english: Boolean, bangla: Boolean, banglish: Boolean): List<Language> =
        ORDER.filter {
            when (it) {
                Language.ENGLISH -> english
                Language.BANGLA -> bangla
                Language.BANGLISH -> banglish
            }
        }

    /**
     * The language one tap of the globe key lands on, wrapping at the end.
     * Falls back to [Language.ENGLISH] if nothing is enabled, which the settings screen
     * prevents but a corrupted preferences file could produce.
     */
    fun next(current: Language, enabled: List<Language>): Language {
        if (enabled.isEmpty()) return Language.ENGLISH
        if (enabled.size == 1) return enabled.first()
        val position = enabled.indexOf(current)
        if (position < 0) {
            // Current is disabled; take the next enabled language after it in fixed order.
            val order = ORDER.indexOf(current)
            return enabled.firstOrNull { ORDER.indexOf(it) > order } ?: enabled.first()
        }
        return enabled[(position + 1) % enabled.size]
    }

    /**
     * Whether [language] may be switched off. Refused when it is the last one on, which is
     * the rule the settings screen shows a message for.
     */
    fun canDisable(enabled: List<Language>, language: Language): Boolean =
        !enabled.contains(language) || enabled.size > 1

    /** The language to actually type with when [preferred] may have been disabled. */
    fun resolve(preferred: Language?, enabled: List<Language>): Language {
        val fallback = enabled.firstOrNull() ?: Language.ENGLISH
        if (preferred != null && enabled.contains(preferred)) return preferred
        return fallback
    }
}
