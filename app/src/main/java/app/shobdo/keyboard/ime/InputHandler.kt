package app.shobdo.keyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import app.shobdo.keyboard.banglish.CandidateEngine
import app.shobdo.keyboard.banglish.LearnedWords

/**
 * The only class that talks to the editor.
 *
 * Nothing here reads the surrounding text: every edit is expressed as "commit this" or
 * "delete N characters", so the keyboard cannot pull a password or any other field content
 * into its own memory, and no typed text is ever logged.
 */
class InputHandler {

    var connection: InputConnection? = null
        private set

    var editorInfo: EditorInfo? = null
        private set

    var isPasswordField: Boolean = false
        private set

    fun bind(ic: InputConnection?, info: EditorInfo?) {
        connection = ic
        editorInfo = info
        isPasswordField = info != null && isPasswordInput(info.inputType)
    }

    fun commit(text: String) {
        if (text.isEmpty()) return
        connection?.commitText(text, 1)
    }

    /** Replaces the composing region; an empty string ends composing. */
    fun setComposing(text: String) {
        val ic = connection ?: return
        if (text.isEmpty()) ic.finishComposingText() else ic.setComposingText(text, 1)
    }

    fun finishComposing() {
        connection?.finishComposingText()
    }

    fun deleteSurrounding(count: Int) {
        if (count > 0) connection?.deleteSurroundingText(count, 0)
    }

    /** Sends the editor's own action (Send, Search, Go, Done) when it has one. */
    fun performEnter() {
        val info = editorInfo ?: run { commit("\n"); return }
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        if (action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
            commit("\n")
        } else {
            connection?.performEditorAction(action)
        }
    }

    /** Short label for the enter key, e.g. "Send"; "⏎" when the editor has no action. */
    fun enterLabel(): String {
        val info = editorInfo ?: return "⏎"
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_DONE -> "Done"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            else -> "⏎"
        }
    }

    companion object {
        /** All the password variations Android offers, including the numeric PIN field. */
        fun isPasswordInput(inputType: Int): Boolean {
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            return when (inputType and InputType.TYPE_MASK_CLASS) {
                InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
                else -> false
            }
        }
    }
}

/**
 * Bangla typing, unit by unit.
 *
 * The word being assembled stays in the editor's composing region, so a kar that replaces a
 * previous kar re-writes the same region instead of deleting text around the cursor. That is
 * what makes `ক` + `ি` + `া` land as `কা` without touching anything the user did not type.
 */
class BanglaSession(private val handler: InputHandler) {

    private var buffer: String = ""

    val isActive: Boolean get() = buffer.isNotEmpty()

    fun type(text: String) {
        if (text.isEmpty()) return
        buffer = BanglaComposer.appendAll(buffer, text)
        handler.setComposing(buffer)
    }

    /** @return false when there was nothing of ours to delete, so the caller deletes normally. */
    fun deleteLastUnit(): Boolean {
        if (buffer.isEmpty()) return false
        buffer = BanglaComposer.deleteLastUnit(buffer)
        handler.setComposing(buffer)
        return true
    }

    /** Commits the word and closes the composing region. */
    fun finish() {
        if (buffer.isEmpty()) return
        handler.commit(buffer)
        buffer = ""
    }

    fun reset() {
        buffer = ""
    }

    val pending: String get() = buffer
}

/**
 * Banglish typing: the Roman buffer lives in the composing region, so backspace always edits
 * Roman letters and the editor shows exactly what was typed until a candidate is chosen.
 *
 * Candidate work happens in [CandidateEngine]; this class only owns the buffer, the editor
 * calls and which candidate is highlighted.
 */
class BanglishSession(
    private val handler: InputHandler,
    private val engine: CandidateEngine,
    private val banglaFirst: Boolean,
    private val learnEnabled: Boolean,
    private val learned: LearnedWords?
) {

    private val roman = StringBuilder()
    private var ranked: List<CandidateEngine.Candidate> = emptyList()

    val isActive: Boolean get() = roman.isNotEmpty()

    val romanText: String get() = roman.toString()

    /** The Bangla word Space will commit. */
    val banglaCandidate: String
        get() = ranked.firstOrNull { it.bangla }?.text ?: engine.best(roman.toString())

    /** Up to [limit] chip labels, with the Roman text kept where the settings put it. */
    fun candidates(limit: Int = 8): List<String> {
        if (roman.isEmpty()) return emptyList()
        if (ranked.isEmpty()) refresh()
        val texts = ranked.map { it.text }
        return if (banglaFirst) texts.take(limit) else romanFirst(texts).take(limit)
    }

    /** Index of the chip that Space will commit. */
    fun primaryIndex(): Int {
        val texts = candidates()
        val index = texts.indexOfFirst { it != romanText }
        return if (index < 0) 0 else index
    }

    fun type(text: String) {
        if (text.isEmpty()) return
        roman.append(text)
        handler.setComposing(roman.toString())
        refresh()
    }

    /** @return false when the buffer was already empty. */
    fun delete(): Boolean {
        if (roman.isEmpty()) return false
        roman.setLength(roman.length - 1)
        handler.setComposing(roman.toString())
        refresh()
        return true
    }

    /** Commits one candidate and closes the composing region. */
    fun commit(text: String): Boolean {
        if (roman.isEmpty() || text.isEmpty()) return false
        handler.commit(text)
        if (learnEnabled && text != romanText) learned?.record(text)
        roman.setLength(0)
        ranked = emptyList()
        return true
    }

    /** Space or punctuation: the highlighted Bangla word replaces the Roman composing text. */
    fun commitBangla(): Boolean = commit(banglaCandidate)

    /** The Roman chip keeps the English text instead. */
    fun commitRoman(): Boolean = commit(romanText)

    fun reset() {
        roman.setLength(0)
        ranked = emptyList()
    }

    private fun refresh() {
        ranked = if (roman.isEmpty()) emptyList() else engine.candidates(roman.toString())
    }

    private fun romanFirst(texts: List<String>): List<String> {
        val index = texts.indexOf(romanText)
        if (index <= 0) return texts
        val ordered = texts.toMutableList()
        ordered.removeAt(index)
        ordered.add(0, romanText)
        return ordered
    }
}
