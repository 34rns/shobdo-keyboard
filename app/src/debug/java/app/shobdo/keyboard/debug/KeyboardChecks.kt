package app.shobdo.keyboard.debug

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import app.shobdo.keyboard.ime.*
import app.shobdo.keyboard.banglish.*
import app.shobdo.keyboard.settings.OneHanded
import app.shobdo.keyboard.theme.ThemeManager
import java.io.File

/** Debug-only device checks using the real Android Canvas/touch code, no test dependency. */
class KeyboardChecks : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            val start = System.nanoTime()
            val rules = SkeletonRules.fromJson(targetContext.assets.open(SkeletonRules.ASSET_PATH).bufferedReader().use { it.readText() })
            val parser = BanglishEngine.fromJson(targetContext.assets.open(BanglishEngine.RULE_ASSET_PATH).bufferedReader().use { it.readText() })
            val index = ReverseRomanIndex.parse(targetContext.assets.open(ReverseRomanIndex.ASSET_PATH).use { it.readBytes() })
            val engine = CandidateEngine(parser, rules, BanglishLexicon.empty(), reverse = index)
            result.putDouble("asset_load_ms", (System.nanoTime() - start) / 1e6)
            val anchors = mapOf("ami" to "আমি", "tumi" to "তুমি", "bhalo" to "ভালো", "valo" to "ভালো",
                "kemon" to "কেমন", "kormo" to "কর্ম", "bangla" to "বাংলা", "desh" to "দেশ", "tomar" to "তোমার", "achho" to "আছো")
            for ((roman, expected) in anchors) check(engine.candidates(roman).first().text == expected) { "$roman did not suggest $expected" }
            repeat(50) { anchors.keys.forEach { engine.candidates(it) } }
            val times = (0 until 200).map {
                val input = anchors.keys.elementAt(it % anchors.size)
                val before = System.nanoTime(); engine.candidates(input); (System.nanoTime() - before) / 1e6
            }.sorted()
            result.putDouble("lookup_p95_ms", times[190]); result.putDouble("lookup_max_ms", times.last())
            var toggles = 0; var switches = 0; var typed = 0; var picks = 0
            runOnMainSync {
                val themes = ThemeManager(targetContext).builtIn()
                val view = KeyboardView(targetContext)
                var language = Language.ENGLISH
                val panel = CandidatePanelState()
                val candidates = listOf("ভালো", "ভালোবাসা", "ভালোই", "bhal") + List(36) { "শব্দ$it" }
                fun page() = LayoutLoader.load(targetContext.assets, if (language == Language.BANGLA) "bn_provat" else "en_qwerty")
                fun strip() = view.setStrip(StripModel(candidates = candidates, expanded = panel.expanded))
                view.listener = object : KeyboardView.Listener {
                    override fun onText(text: String) { typed++ }
                    override fun onSpace() { typed++ }
                    override fun onBackspace() { typed++ }
                    override fun onEnter() { typed++ }
                    override fun onSymbols() { typed++ }
                    override fun onCycleLanguage() {
                        language = LanguageCycle.next(language, LanguageCycle.ORDER)
                        view.setLanguage(language); view.setLayout(page()); switches++
                    }
                    override fun onLanguagePicker() {}
                    override fun onSuggestion(index: Int, text: String) { picks++; panel.reset(); strip() }
                    override fun onShiftChanged(state: ShiftState) {}
                    override fun onStripAction(action: StripAction) { typed++ }
                    override fun onToggleCandidates() { panel.toggle(true); strip(); toggles++ }
                }
                fun event(action: Int, x: Float, y: Float) {
                    val now = SystemClock.uptimeMillis()
                    val event = MotionEvent.obtain(now, now, action, x, y, 0)
                    view.onTouchEvent(event); event.recycle()
                }
                fun tap(x: Float, y: Float) { event(MotionEvent.ACTION_DOWN, x, y); event(MotionEvent.ACTION_UP, x, y) }
                val field = KeyboardView::class.java.getDeclaredField("geometry").apply { isAccessible = true }
                val stripField = KeyboardView::class.java.getDeclaredField("stripPainter").apply { isAccessible = true }
                fun geometry() = field.get(view) as KeyboardGeometry
                for (theme in themes) for (lang in Language.entries) for (hand in OneHanded.entries) {
                    language = lang; panel.reset()
                    view.setStyle(KeyboardStyle(oneHanded = hand, haptic = false, popup = false, preview = false))
                    view.setLayout(page()); view.setLanguage(lang); view.setTheme(theme)
                    for (width in intArrayOf(1080, 1920)) {
                        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                        view.layout(0, 0, width, view.measuredHeight)
                        strip()
                        val arrowX = width - 24f * targetContext.resources.displayMetrics.density
                        val arrowY = geometry().barRect.centerY()
                        repeat(8) { tap(arrowX, arrowY); check(panel.expanded == (it % 2 == 0)) }
                        tap(arrowX, arrowY)
                        check(panel.expanded)
                        val stripPainter = stripField.get(view) as StripPainter
                        val beforePick = picks
                        val dragY = stripPainter.panelRect.centerY()
                        event(MotionEvent.ACTION_DOWN, width / 2f, dragY)
                        event(MotionEvent.ACTION_MOVE, width / 2f, dragY - 1000f)
                        event(MotionEvent.ACTION_UP, width / 2f, dragY - 1000f)
                        check(picks == beforePick && panel.expanded) { "scroll committed a word or closed the panel" }
                        val last = stripPainter.panelRects.last()
                        check(stripPainter.panelRect.contains(last.centerX(), last.centerY())) { "last candidate cannot be reached" }
                        tap(last.centerX(), last.centerY())
                        check(picks == beforePick + 1 && !panel.expanded)
                        tap(arrowX, arrowY)
                        // The lower, empty part of the drawn sheet must capture touches.
                        val countBefore = typed
                        tap(width / 2f, view.height - 30f)
                        check(typed == countBefore)
                        if (panel.expanded) tap(arrowX, arrowY)
                        view.setStrip(StripModel())
                        val bitmap = Bitmap.createBitmap(width, view.height, Bitmap.Config.ARGB_8888)
                        view.draw(Canvas(bitmap)); bitmap.recycle()
                        val space = geometry().keys.first { it.key.type == KeyType.SPACE }.rect
                        val x = space.centerX(); val y = space.centerY()
                        val before = switches
                        event(MotionEvent.ACTION_DOWN, x, y)
                        event(MotionEvent.ACTION_MOVE, x + 40f * targetContext.resources.displayMetrics.density, y)
                        check(switches == before + 1) { "language switch waited for ACTION_UP" }
                        event(MotionEvent.ACTION_UP, x, y)
                        check(typed == countBefore) { "space swipe also typed a key" }
                    }
                }
                result.putInt("themes", themes.size); result.putInt("arrow_toggles", toggles); result.putInt("immediate_swipes", switches); result.putInt("candidate_picks", picks)
                check(typed == 0)
            }
            result.putString("stream", "PASS: ${result.getInt("themes")} themes, ${result.getInt("arrow_toggles")} arrow toggles, ${result.getInt("immediate_swipes")} immediate swipes; all ten typing anchors. Asset load ${result.getDouble("asset_load_ms")} ms; lookup p95 ${result.getDouble("lookup_p95_ms")} ms, max ${result.getDouble("lookup_max_ms")} ms.\n")
            finish(0, result)
        } catch (e: Throwable) {
            result.putString("stream", "FAIL: ${e.stackTraceToString()}\n")
            finish(1, result)
        }
    }
}
