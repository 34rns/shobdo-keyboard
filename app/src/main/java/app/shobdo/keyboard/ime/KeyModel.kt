package app.shobdo.keyboard.ime

/**
 * What a key does when it is tapped.
 *
 * [NORMAL] commits [KeyModel.code] to the editor. Every other type is handled by the view
 * or the input handler and ignores `code`, except [SPACE] and [ENTER] whose `code` is a
 * sensible fallback.
 */
enum class KeyType(val json: String) {
    NORMAL(""),
    SHIFT("shift"),
    BACKSPACE("backspace"),
    ENTER("enter"),
    SPACE("space"),
    SYMBOLS("symbols"),
    GLOBE("globe"),
    EMOJI("emoji"),
    COMMA("comma"),
    PERIOD("period");

    companion object {
        private val byJson: Map<String, KeyType> =
            entries.filter { it != NORMAL }.associateBy { it.json }

        /** @throws IllegalArgumentException when the layout names a type we do not know. */
        fun fromJson(value: String?): KeyType =
            if (value.isNullOrBlank()) NORMAL
            else byJson[value] ?: throw IllegalArgumentException("unknown key type '$value'")
    }
}

/**
 * One key, exactly as it appears in a layout JSON file. Immutable: shift and the Bangla
 * alternate glyph are derived with [asShifted] instead of mutating the loaded layout.
 */
data class KeyModel(
    val label: String,
    val code: String = "",
    val type: KeyType = KeyType.NORMAL,
    val widthWeight: Float = 1f,
    val longPressChars: List<String> = emptyList()
) {
    val isSpecial: Boolean get() = type != KeyType.NORMAL

    val hasLongPress: Boolean get() = longPressChars.isNotEmpty()

    /**
     * The glyph this key types when shift is active.
     *
     * Latin pages uppercase the letter. A Bangla page has no uppercase, so shift reaches the
     * Probhat alternate instead, which is always the first long-press entry
     * (see docs/LAYOUT_FORMAT.md) — that is what [shiftUsesAlternates] selects.
     */
    fun asShifted(shiftUsesAlternates: Boolean): KeyModel {
        if (isSpecial) return this
        if (shiftUsesAlternates && longPressChars.isNotEmpty()) {
            val alternate = longPressChars.first()
            return copy(label = alternate, code = alternate)
        }
        return if (code.length == 1) copy(label = code.uppercase(), code = code.uppercase()) else this
    }

    /** Glyph drawn for this key, given the shift state. */
    fun displayLabel(shifted: Boolean, shiftUsesAlternates: Boolean = false): String =
        if (shifted) asShifted(shiftUsesAlternates).label else label

    /** Text committed for this key, given the shift state. */
    fun displayCode(shifted: Boolean, shiftUsesAlternates: Boolean = false): String =
        if (shifted) asShifted(shiftUsesAlternates).code else code
}

/**
 * A whole page of keys. Rows are normalised independently when measured.
 *
 * [numberRowIndex] marks the optional row the "number row" setting hides; null means the page
 * has no optional row, which is every page except `en_qwerty`.
 */
data class KeyboardLayout(
    val id: String,
    val name: String,
    val rows: List<List<KeyModel>>,
    val numberRowIndex: Int? = null
) {
    val columnCount: Int get() = rows.maxOfOrNull { it.size } ?: 0

    companion object {
        fun rowWeight(row: List<KeyModel>): Float =
            row.sumOf { it.widthWeight.toDouble() }.toFloat().coerceAtLeast(0.0001f)
    }
}
