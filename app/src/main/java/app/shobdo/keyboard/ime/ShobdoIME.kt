package app.shobdo.keyboard.ime
import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.os.Handler
import android.os.Looper
import app.shobdo.keyboard.banglish.BanglishEngine
import app.shobdo.keyboard.banglish.BanglishLexicon
import app.shobdo.keyboard.banglish.CandidateEngine
import app.shobdo.keyboard.banglish.LearnedWords
import app.shobdo.keyboard.banglish.SkeletonRules
import app.shobdo.keyboard.settings.OneHanded
import app.shobdo.keyboard.settings.SettingsActivity
import app.shobdo.keyboard.settings.SettingsRepository
import app.shobdo.keyboard.theme.KeyboardTheme
import app.shobdo.keyboard.theme.ThemeManager
import java.io.File
import app.shobdo.keyboard.banglish.ReverseRomanIndex
import app.shobdo.keyboard.banglish.BigramTable
import java.util.concurrent.Executors
class ShobdoIME : InputMethodService(), KeyboardView.Listener {
    private lateinit var settings: SettingsRepository
    private lateinit var themeManager: ThemeManager
    private val handler = InputHandler()
    private val bangla = BanglaSession(handler)
    private val layouts = HashMap<String, KeyboardLayout>()
    private lateinit var banglish: BanglishSession
    private var theme: KeyboardTheme? = null
    private var keyboardView: KeyboardView? = null
    private var language: Language = Language.ENGLISH
    private var pageIndex: Int = 0
    private var lastTypedWasSentenceEnd: Boolean = false
    private var stripWord: String = ""
    private val candidatePanel = CandidatePanelState()
    private val uiHandler = Handler(Looper.getMainLooper())
    private var skeletonRules: SkeletonRules = SkeletonRules.empty()
    private var phonetic: BanglishEngine? = null
    private var learned: LearnedWords? = null
    private var reverse = ReverseRomanIndex.empty()
    private var bigrams = BigramTable.empty()
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "shobdo-banglish").apply { isDaemon = true } }
    private var dataLoading = false
    private var destroyed = false
    private val deferredInput = ArrayDeque<() -> Unit>()
    private fun deferInput(action: () -> Unit): Boolean {
        if (!banglish.isCommitting) return false
        deferredInput.addLast(action)
        return true
    }
    private fun suggestionsChanged() {
        updateSuggestions()
        while (!banglish.isCommitting && deferredInput.isNotEmpty()) deferredInput.removeFirst().invoke()
    }
    @Volatile
    private var lexicon: BanglishLexicon = BanglishLexicon.empty()
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != SettingsRepository.KEY_LAST_LANGUAGE) applyPreferences()
    }
    override fun onCreate() {
        super.onCreate()
        markRunning(true)
        settings = SettingsRepository(this)
        themeManager = ThemeManager(this)
        banglish = newSession()
        settings.preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }
    override fun onDestroy() {
        destroyed = true
        deferredInput.clear()
        banglish.reset()
        worker.shutdownNow()
        markRunning(false)
        settings.preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onDestroy()
    }
    override fun onCreateInputView(): View {
        val view = KeyboardView(this)
        view.listener = this
        keyboardView = view
        applyPreferences()
        return view
    }
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        deferredInput.clear()
        handler.bind(currentInputConnection, info)
        bangla.reset()
        banglish.reset()
        restoreLanguage()
        if (info != null && info.imeOptions and EditorInfo.IME_FLAG_FORCE_ASCII != 0) language = Language.ENGLISH
        pageIndex = 0
        showCurrentPage()
        updateSuggestions()
        if (settings.autoCapitalise && !handler.suggestionsSuppressed) keyboardView?.armShiftOnce()
        lastTypedWasSentenceEnd = false
    }
    override fun onFinishInputView(finishingInput: Boolean) {
        deferredInput.clear()
        candidatePanel.reset()
        banglish.reset()
        updateSuggestions()
        super.onFinishInputView(finishingInput)
    }
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK && candidatePanel.expanded) {
            candidatePanel.reset()
            updateSuggestions()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    override fun onFinishInput() {
        candidatePanel.reset()
        bangla.finish()
        bangla.reset()
        banglish.reset()
        super.onFinishInput()
    }
        override fun onEvaluateFullscreenMode(): Boolean = false
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (candidatesStart == -1 && (bangla.isActive || banglish.isActive)) {
            bangla.reset()
            banglish.reset()
            updateSuggestions()
        }
    }
        private fun loadBanglishData() {
        if (dataLoading) return
        dataLoading = true
        worker.execute {
            val rules = runCatching { SkeletonRules.fromJson(assets.open(SkeletonRules.ASSET_PATH).bufferedReader().use { it.readText() }) }.getOrElse { SkeletonRules.empty() }
            val parser = runCatching { BanglishEngine.fromJson(assets.open(BanglishEngine.RULE_ASSET_PATH).bufferedReader().use { it.readText() }) }.getOrNull()
            val index = runCatching { ReverseRomanIndex.parse(assets.open(ReverseRomanIndex.ASSET_PATH).use { it.readBytes() }) }.getOrElse { ReverseRomanIndex.empty() }
            val words = if (index.wordCount > 0) BanglishLexicon.empty() else runCatching {
                BanglishLexicon.parse(assets.open(BanglishLexicon.ASSET_PATH).bufferedReader().use { it.readLines() })
            }.getOrElse { BanglishLexicon.empty() }
            val context = runCatching { BigramTable.parse(assets.open(BigramTable.ASSET_PATH).bufferedReader().use { it.readText() }) }.getOrElse { BigramTable.empty() }
            val store = LearnedWords.open(File(filesDir, LearnedWords.FILE_NAME))
            uiHandler.post {
                if (!destroyed) {
                    skeletonRules = rules; phonetic = parser; lexicon = words; reverse = index; bigrams = context; learned = store
                    banglish.configure(candidateEngine(), settings.banglaCandidateFirst, settings.learnBanglish, learned)
                    updateSuggestions()
                }
            }
        }
    }
    private fun candidateEngine() = CandidateEngine(
        phonetic ?: FallbackBanglish.engine, skeletonRules, lexicon,
        learned = learned, reverse = reverse, bigrams = bigrams, learningEnabled = settings.learnBanglish
    )
    private fun newSession() = BanglishSession(handler, candidateEngine(), settings.banglaCandidateFirst,
        settings.learnBanglish, learned, worker, { task -> uiHandler.post { if (!destroyed) task() } }, ::suggestionsChanged)
    private fun restoreLanguage() {
        language = LanguageCycle.resolve(settings.lastLanguage, settings.enabledLanguages())
    }
    private fun selectLanguage(next: Language) {
        candidatePanel.reset()
        stripWord = ""
        bangla.finish()
        bangla.reset()
        if (!banglish.isCommitting) banglish.reset()
        language = next
        settings.lastLanguage = next
        pageIndex = 0
        showCurrentPage()
        updateSuggestions()
    }
    private fun pageIds(forLanguage: Language): List<String> = when (forLanguage) {
        Language.BANGLA -> listOf("bn_provat", "bn_digits", "bn_kars")
        Language.ENGLISH, Language.BANGLISH -> listOf("en_qwerty", "en_symbols_1", "en_symbols_2")
    }
    private fun showCurrentPage() {
        val view = keyboardView ?: return
        if (language == Language.BANGLISH) loadBanglishData()
        val ids = pageIds(language)
        val id = ids[pageIndex.coerceIn(0, ids.size - 1)]
        val page = layouts.getOrPut(id) { LayoutLoader.load(assets, id) }
        view.setLanguage(language)
        view.setStyle(keyboardStyle(settings, theme, language))
        view.setLayout(withEnterLabel(page, handler.enterLabel()))
        view.setTheme(
            theme ?: themeManager.withImageOverride(
                themeManager.active(settings.themeId),
                settings.backgroundImage,
                settings.backgroundBrightness,
                settings.backgroundBlur
            ).also { theme = it }
        )
    }
    private fun applyPreferences() {
        theme = themeManager.withImageOverride(
            themeManager.active(settings.themeId),
            settings.backgroundImage,
            settings.backgroundBrightness,
            settings.backgroundBlur
        )
        banglish.configure(candidateEngine(), settings.banglaCandidateFirst, settings.learnBanglish, learned)
        val enabled = settings.enabledLanguages()
        if (language !in enabled) {
            language = LanguageCycle.resolve(settings.lastLanguage, enabled)
            pageIndex = 0
        }
        showCurrentPage()
        updateSuggestions()
    }
        private fun updateSuggestions() {
        val view = keyboardView ?: return
        val composing = language == Language.BANGLISH && !handler.suggestionsSuppressed && banglish.isActive
        if (!composing) candidatePanel.reset()
        val model = if (composing) {
            StripModel(
                candidates = banglish.candidates(limit = PANEL_CANDIDATES),
                primary = banglish.primaryIndex(),
                expanded = candidatePanel.expanded
            )
        } else {
            StripModel(word = if (handler.suggestionsSuppressed) "" else stripWord)
        }
        view.setStrip(model)
    }
    override fun onStripAction(action: StripAction) {
        when (action) {
            StripAction.THEME -> cycleTheme()
            StripAction.ONE_HANDED -> cycleOneHanded()
            StripAction.SETTINGS -> openSettings()
            StripAction.COLLAPSE -> requestHideSelf(0)
            StripAction.EXPAND -> onToggleCandidates()
        }
    }
    override fun onToggleCandidates() {
        if (!banglish.isActive) return
        candidatePanel.toggle(banglish.isActive)
        updateSuggestions()
    }
    private fun cycleTheme() {
        val themes = themeManager.all()
        if (themes.isEmpty()) return
        val current = themes.indexOfFirst { it.id == settings.themeId }
        settings.themeId = themes[(current + 1) % themes.size].id
        applyPreferences()
    }
    private fun cycleOneHanded() {
        settings.oneHanded = when (settings.oneHanded) {
            OneHanded.OFF -> OneHanded.LEFT
            OneHanded.LEFT -> OneHanded.RIGHT
            OneHanded.RIGHT -> OneHanded.OFF
        }
        applyPreferences()
    }
    private fun openSettings() {
        startActivity(
            Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
    override fun onText(text: String) {
        if (deferInput { onText(text) }) return
        if (text.isEmpty()) return
        when (language) {
            Language.BANGLA -> bangla.type(text)
            Language.BANGLISH -> {
                if (handler.suggestionsSuppressed) {
                    handler.commit(text)
                } else if (text.all { it.isLetterOrDigit() }) {
                    banglish.type(text)
                    updateSuggestions()
                } else {
                    banglish.commitBangla()
                    if (deferInput { handler.commit(text); updateSuggestions() }) return
                    handler.commit(text)
                    updateSuggestions()
                }
            }
            Language.ENGLISH -> handler.commit(text)
        }
        if (handler.suggestionsSuppressed) { stripWord = ""; updateSuggestions(); return }
        lastTypedWasSentenceEnd = text.endsWith(".") || text.endsWith("!") || text.endsWith("?") || text.endsWith("।")
        if (text.length == 1 && (text[0].isLetterOrDigit() || language == Language.BANGLA)) {
            stripWord = if (language == Language.BANGLA) "" else stripWord.takeLast(23) + text
        } else {
            stripWord = ""
        }
        updateSuggestions()
    }
    override fun onSpace() {
        if (deferInput { onSpace() }) return
        when (language) {
            Language.BANGLA -> {
                bangla.finish()
                handler.commit(" ")
            }
            Language.BANGLISH -> {
                if (!handler.suggestionsSuppressed) banglish.commitBangla()
                if (deferInput { handler.commit(" "); stripWord = ""; updateSuggestions() }) return
                handler.commit(" ")
                updateSuggestions()
            }
            Language.ENGLISH -> handler.commit(" ")
        }
        if (lastTypedWasSentenceEnd && settings.autoCapitalise && language != Language.BANGLA) {
            keyboardView?.armShiftOnce()
        }
        lastTypedWasSentenceEnd = false
        stripWord = ""
        updateSuggestions()
    }
    override fun onBackspace() {
        if (deferInput { onBackspace() }) return
        when (language) {
            Language.BANGLA -> if (!bangla.deleteLastUnit()) handler.deleteSurrounding(1)
            Language.BANGLISH -> {
                if (!handler.suggestionsSuppressed && banglish.delete()) {
                    updateSuggestions()
                } else {
                    handler.deleteSurrounding(1)
                }
            }
            Language.ENGLISH -> handler.deleteSurrounding(1)
        }
        stripWord = stripWord.dropLast(1)
        updateSuggestions()
    }
    override fun onEnter() {
        if (deferInput { onEnter() }) return
        bangla.finish()
        if (language == Language.BANGLISH && !handler.suggestionsSuppressed) banglish.commitBangla()
        if (deferInput { handler.performEnter(); stripWord = ""; candidatePanel.reset(); updateSuggestions() }) return
        handler.performEnter()
        stripWord = ""
        candidatePanel.reset()
        updateSuggestions()
        lastTypedWasSentenceEnd = false
    }
    override fun onSymbols() {
        if (deferInput { onSymbols() }) return
        bangla.finish()
        banglish.reset()
        val count = pageIds(language).size
        pageIndex = (pageIndex + 1) % count
        showCurrentPage()
        updateSuggestions()
    }
    override fun onCycleLanguage() {
        val enabled = settings.enabledLanguages()
        selectLanguage(LanguageCycle.next(language, enabled))
    }
    override fun onLanguagePicker() {
        if (deferInput { onLanguagePicker() }) return
        val view = keyboardView ?: return
        val enabled = settings.enabledLanguages()
        if (enabled.isEmpty()) return
        val popup = ListPopupWindow(this)
        popup.anchorView = view
        popup.width = (150 * resources.displayMetrics.density).toInt()
        popup.isModal = true
        popup.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, enabled.map { it.displayName })
        )
        popup.setOnItemClickListener { _, _, position, _ ->
            popup.dismiss()
            selectLanguage(enabled[position])
        }
        popup.show()
    }
    override fun onSuggestion(index: Int, text: String) {
        if (deferInput { onSuggestion(index, text) }) return
        if (language != Language.BANGLISH || handler.suggestionsSuppressed) return
        banglish.commit(text)
        candidatePanel.reset()
        updateSuggestions()
    }
    override fun onShiftChanged(state: ShiftState) = Unit
        fun previewTheme(id: String): KeyboardTheme? = themeManager.byId(id)
        fun userThemeDirectory(): File = File(filesDir, ThemeManager.DIR)
    companion object {
                private const val PANEL_CANDIDATES = 40
                        @Volatile
        private var running: Boolean = false
        val isRunning: Boolean get() = running
        private fun markRunning(value: Boolean) {
            running = value
        }
    }
}
