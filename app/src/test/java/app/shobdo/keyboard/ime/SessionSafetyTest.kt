package app.shobdo.keyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import app.shobdo.keyboard.banglish.*
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class SessionSafetyTest {
    private val assets = File(System.getProperty("shobdo.assets")!!)
    private fun engine() = CandidateEngine(
        BanglishEngine.fromJson(File(assets, BanglishEngine.RULE_ASSET_PATH).readText()),
        SkeletonRules.fromJson(File(assets, SkeletonRules.ASSET_PATH).readText()),
        BanglishLexicon.empty(), reverse = ReverseRomanIndex.parse(File(assets, ReverseRomanIndex.ASSET_PATH).readBytes()))
    private class Editor {
        var composing = ""
        var committed = ""
        val connection = Proxy.newProxyInstance(InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java)) { _, method, args ->
            when (method.name) {
                "setComposingText" -> { composing = args[0].toString(); true }
                "commitText" -> { committed += args[0].toString(); composing = ""; true }
                "finishComposingText" -> true
                else -> if (method.returnType == Boolean::class.javaPrimitiveType) true else null
            }
        } as InputConnection
    }
    @Test fun `password URL numeric and incognito fields suppress suggestions`() {
        for (type in listOf(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)) {
            assertTrue(InputHandler.shouldSuppressSuggestions(type, 0))
        }
        assertTrue(InputHandler.shouldSuppressSuggestions(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))
        assertFalse(InputHandler.shouldSuppressSuggestions(InputType.TYPE_CLASS_TEXT, 0))
    }
    @Test fun `empty and long buffers are safe and final backspace removes composing text`() {
        val editor = Editor(); val handler = InputHandler(); handler.bind(editor.connection, null)
        val session = BanglishSession(handler, engine(), true, false, null)
        assertFalse(session.delete()); assertFalse(session.commitBangla())
        session.type("a"); assertTrue(session.delete()); assertEquals("", editor.composing)
        repeat(200) { session.type("x") }
        assertTrue(session.romanText.length <= 40)
        assertTrue(editor.committed.length >= 160)
        assertEquals("x".repeat(200), editor.committed + editor.composing)
    }
    @Test fun `late results preserve current buffer and commit waits for the current ranking`() {
        val worker = Executors.newSingleThreadExecutor()
        val gate = CountDownLatch(1)
        val delivery = ConcurrentLinkedQueue<() -> Unit>()
        worker.execute { gate.await() }
        val editor = Editor(); val handler = InputHandler(); handler.bind(editor.connection, null)
        val indexEngine = engine()
        val session = BanglishSession(handler, indexEngine, true, false, null, worker, { delivery.add(it) })
        try {
            session.type("ami"); session.reset(); session.type("valo")
            assertTrue(session.commitBangla()); assertTrue(session.isCommitting)
            // Asset publication while a commit waits must replace the pending fallback query.
            session.configure(indexEngine, true, false, null)
            gate.countDown()
            worker.submit {}.get(5, TimeUnit.SECONDS)
            while (delivery.isNotEmpty()) delivery.poll().invoke()
            assertEquals("ভালো", editor.committed)
            assertFalse(session.isCommitting)
            assertFalse(session.isActive)
        } finally { gate.countDown(); worker.shutdownNow() }
    }
}
