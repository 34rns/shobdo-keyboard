package app.shobdo.keyboard.ime

import app.shobdo.keyboard.banglish.CandidateEngine
import app.shobdo.keyboard.banglish.LearnedWords
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/** Immediate Roman composing, worker ranking, and a commit fence for rapid typing. */
class BanglishSession(
    private val handler: InputHandler,
    private var engine: CandidateEngine,
    private var banglaFirst: Boolean,
    private var learnEnabled: Boolean,
    private var learned: LearnedWords?,
    private val worker: ExecutorService? = null,
    private val deliver: (() -> Unit) -> Unit = { it() },
    private val changed: () -> Unit = {}
) {
    private val roman = StringBuilder()
    private var ranked: List<CandidateEngine.Candidate> = emptyList()
    private var request = 0
    private var pending: Future<*>? = null
    private var waiting = false
    private var previousWord = ""
    var isCommitting = false
        private set
    val isActive get() = roman.isNotEmpty()
    val romanText get() = roman.toString()
    val banglaCandidate get() = ranked.firstOrNull { it.bangla }?.text ?: engine.best(romanText)

    fun configure(newEngine: CandidateEngine, first: Boolean, learning: Boolean, store: LearnedWords?) {
        engine = newEngine; banglaFirst = first; learnEnabled = learning; learned = store
        if (isActive) refresh()
    }
    fun candidates(limit: Int = 8): List<String> {
        if (!isActive) return emptyList()
        val texts = if (ranked.isEmpty()) listOf(engine.best(romanText), romanText) else ranked.map { it.text }
        if (banglaFirst) return texts.take(limit)
        return (listOf(romanText) + texts.filter { it != romanText }).take(limit)
    }
    fun primaryIndex() = candidates().indexOfFirst { it != romanText }.coerceAtLeast(0)
    fun type(text: String) {
        if (text.isEmpty()) return
        if (roman.length + text.length > 40) {
            commit(romanText)
            handler.commit(text)
            return
        }
        roman.append(text)
        handler.setComposing(romanText)
        refresh()
    }
    fun delete(): Boolean {
        if (!isActive) { previousWord = ""; return false }
        roman.setLength(roman.length - 1)
        if (!isActive) {
            // Remove the final composing character before ending composition.
            handler.clearComposing()
        } else handler.setComposing(romanText)
        refresh()
        return true
    }
    fun commit(text: String): Boolean {
        if (!isActive || text.isEmpty() || handler.suggestionsSuppressed) return false
        val typed = romanText
        handler.commit(text)
        val previous = previousWord
        if (learnEnabled && text != typed) {
            val store = learned
            if (worker != null) worker.execute { store?.record(text, previous) } else store?.record(text, previous)
        }
        previousWord = if (text != typed) text else ""
        request++; pending?.cancel(false); waiting = false; isCommitting = false
        roman.setLength(0); ranked = emptyList()
        return true
    }
    fun commitBangla(): Boolean {
        if (!isActive) return false
        if (waiting && worker != null) { isCommitting = true; return true }
        return commit(banglaCandidate)
    }
    fun commitRoman() = commit(romanText)
    fun reset() {
        request++; pending?.cancel(false); waiting = false; isCommitting = false
        roman.setLength(0); ranked = emptyList(); previousWord = ""
    }
    private fun refresh() {
        val token = ++request
        pending?.cancel(false)
        ranked = emptyList()
        if (!isActive || handler.suggestionsSuppressed) { waiting = false; return }
        val buffer = romanText
        val context = previousWord
        val currentEngine = engine
        if (worker == null) { ranked = currentEngine.candidates(buffer, previousWord = context); return }
        waiting = true
        pending = worker.submit {
            val result = runCatching { currentEngine.candidates(buffer, previousWord = context) }.getOrElse { emptyList() }
            deliver {
                if (token == request && buffer == romanText && !handler.suggestionsSuppressed) {
                    ranked = result; waiting = false
                    if (isCommitting) commit(banglaCandidate)
                    changed()
                }
            }
        }
    }
}
