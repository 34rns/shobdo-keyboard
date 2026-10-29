package app.shobdo.keyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import app.shobdo.keyboard.banglish.BanglishEngine

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
 */
class BanglishSession(
    private val handler: InputHandler,
    private val engine: BanglishEngine,
    private val banglaFirst: Boolean
) {

    private val roman = StringBuilder()

    val isActive: Boolean get() = roman.isNotEmpty()

    val banglaCandidate: String get() = engine.transliterate(roman.toString())

    val romanText: String get() = roman.toString()

    /** Candidate chip labels in the configured order; the Bangla one is the primary. */
    fun candidates(): List<String> {
        if (roman.isEmpty()) return emptyList()
        return engine.suggestions(roman.toString(), banglaFirst)
    }

    fun primaryIndex(): Int = if (banglaFirst) 0 else 1

    fun type(text: String) {
        if (text.isEmpty()) return
        roman.append(text)
        handler.setComposing(roman.toString())
    }

    /** @return false when the buffer was already empty. */
    fun delete(): Boolean {
        if (roman.isEmpty()) return false
        roman.setLength(roman.length - 1)
        handler.setComposing(roman.toString())
        return true
    }

    /** Space or punctuation: the Bangla word replaces the Roman composing text. */
    fun commitBangla(): Boolean {
        if (roman.isEmpty()) return false
        val bangla = engine.transliterate(roman.toString())
        handler.commit(bangla)
        roman.setLength(0)
        return true
    }

    /** Tapping the Roman chip keeps the English text instead. */
    fun commitRoman(): Boolean {
        if (roman.isEmpty()) return false
        handler.commit(roman.toString())
        roman.setLength(0)
        return true
    }

    fun reset() {
        roman.setLength(0)
    }
}
