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

/**
 * The service. It owns the wiring — which language, which page, which theme — and nothing
 * else: drawing lives in [KeyboardView], editor edits in [InputHandler], rules in
 * [BanglaComposer] and [BanglishEngine].
 */
class ShobdoIME : InputMethodService(), KeyboardView.Listener {

    private lateinit var settings: SettingsRepository
    private lateinit var themeManager: ThemeManager

    private val handler = InputHandler()
    private val bangla = BanglaSession(handler)
    private val layouts = HashMap<String, KeyboardLayout>()

    // Built in onCreate: a property initialiser runs before the service has a Context, so
    // reading assets there would crash.
    private lateinit var banglish: BanglishSession
    private var theme: KeyboardTheme? = null
    private var keyboardView: KeyboardView? = null
    private var language: Language = Language.ENGLISH
    private var pageIndex: Int = 0
    private var lastTypedWasSentenceEnd: Boolean = false

    /** The word shown on the left of the strip while typing English or Banglish. */
    private var stripWord: String = ""
    private var candidatesExpanded: Boolean = false

    private val uiHandler = Handler(Looper.getMainLooper())
    private var skeletonRules: SkeletonRules = SkeletonRules.empty()
    private var phonetic: BanglishEngine? = null
    private var learned: LearnedWords? = null

    /** Filled on a background thread; the phonetic engine works without it. */
    @Volatile
    private var lexicon: BanglishLexicon = BanglishLexicon.empty()

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        applyPreferences()
    }

    override fun onCreate() {
        super.onCreate()
        markRunning(true)
        settings = SettingsRepository(this)
        themeManager = ThemeManager(this)
        loadBanglishData()
        banglish = newSession()
        settings.preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    override fun onDestroy() {
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
        handler.bind(currentInputConnection, info)
        bangla.reset()
        banglish.reset()
        restoreLanguage()
        pageIndex = 0
        showCurrentPage()
        updateSuggestions()
        if (settings.autoCapitalise && !handler.isPasswordField) keyboardView?.armShiftOnce()
        lastTypedWasSentenceEnd = false
    }

    override fun onFinishInput() {
        bangla.finish()
        bangla.reset()
        banglish.reset()
        super.onFinishInput()
    }

    /** The keyboard is its own layout, so never hide it behind the fullscreen extract view. */
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
        // -1 means the framework dropped our composing region (the user moved the cursor or
        // another app changed the field). Forget the buffer so we never rewrite text we no
        // longer own.
        if (candidatesStart == -1 && (bangla.isActive || banglish.isActive)) {
            bangla.reset()
            banglish.reset()
            updateSuggestions()
        }
    }

    // --- state -----------------------------------------------------------------------

    /**
     * The phonetic table is small and read immediately; the word list is 1.3 MB, so it is
     * parsed on a background thread and the keyboard appears while it is still loading.
     */
    private fun loadBanglishData() {
        skeletonRules = runCatching {
            val text = assets.open(SkeletonRules.ASSET_PATH).bufferedReader().use { it.readText() }
            SkeletonRules.fromJson(text)
        }.getOrElse { SkeletonRules.empty() }
        phonetic = runCatching {
            val text = assets.open(BanglishEngine.RULE_ASSET_PATH).bufferedReader().use { it.readText() }
            BanglishEngine.fromJson(text)
        }.getOrNull()
        learned = LearnedWords(File(filesDir, LearnedWords.FILE_NAME))
        Thread({
            val loaded = runCatching {
                val text = assets.open(BanglishLexicon.ASSET_PATH).bufferedReader().use { it.readText() }
                BanglishLexicon.parse(text.lines())
            }.getOrElse { BanglishLexicon.empty() }
            lexicon = loaded
            uiHandler.post {
                if (loaded.wordCount > 0) {
                    banglish = newSession()
                    updateSuggestions()
                }
            }
        }, "shobdo-lexicon").apply { isDaemon = true }.start()
    }

    private fun newSession(): BanglishSession = BanglishSession(
        handler = handler,
        engine = CandidateEngine(
            phonetic = phonetic ?: BanglishEngine.fromJson(FALLBACK_RULES),
            skeleton = skeletonRules,
            lexicon = lexicon,
            learned = learned
        ),
        banglaFirst = settings.banglaCandidateFirst,
        learnEnabled = settings.learnBanglish,
        learned = learned
    )

    private fun restoreLanguage() {
        language = LanguageCycle.resolve(settings.lastLanguage, settings.enabledLanguages())
    }

    private fun selectLanguage(next: Language) {
        bangla.finish()
        bangla.reset()
        banglish.reset()
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
        val ids = pageIds(language)
        val id = ids[pageIndex.coerceIn(0, ids.size - 1)]
        val page = layouts.getOrPut(id) { LayoutLoader.load(assets, id) }
        view.setLanguage(language)
        view.setStyle(style())
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

    /** Repaints the enter key with the editor's action name (Send, Search, Go). */
    private fun withEnterLabel(page: KeyboardLayout, label: String): KeyboardLayout {
        if (label == "⏎") return page
        val rows = page.rows.map { row ->
            row.map { key -> if (key.type == KeyType.ENTER) key.copy(label = label) else key }
        }
        return page.copy(rows = rows)
    }

    private fun style(): KeyboardStyle {
        val height = settings.keyHeight
        return KeyboardStyle(
            keyHeightDpPortrait = height.dpPortrait,
            keyHeightDpLandscape = height.dpLandscape,
            borders = settings.keyBorders,
            popup = settings.keyPreview,
            preview = settings.keyPreview,
            haptic = settings.hapticEnabled,
            hapticStrength = settings.hapticStrength,
            sound = settings.soundEnabled,
            fontScale = settings.fontScale * (theme?.fontScale ?: 1f),
            oneHanded = settings.oneHanded,
            numberRow = settings.numberRow,
            shiftUsesAlternates = language == Language.BANGLA
        )
    }

    /** Re-reads every setting: used on first draw and whenever preferences change. */
    private fun applyPreferences() {
        theme = themeManager.withImageOverride(
            themeManager.active(settings.themeId),
            settings.backgroundImage,
            settings.backgroundBrightness,
            settings.backgroundBlur
        )
        banglish = newSession()
        val enabled = settings.enabledLanguages()
        if (language !in enabled) {
            language = LanguageCycle.resolve(settings.lastLanguage, enabled)
            pageIndex = 0
        }
        showCurrentPage()
        updateSuggestions()
    }

    /**
     * Rebuilds the strip. English shows the word being typed plus quick actions; Banglish
     * shows candidates. A password field gets neither: the word would be the password.
     */
    private fun updateSuggestions() {
        val view = keyboardView ?: return
        val composing = language == Language.BANGLISH && !handler.isPasswordField && banglish.isActive
        if (!composing) candidatesExpanded = false
        val model = if (composing) {
            StripModel(
                // The strip shows only what fits; the expanded panel lays out all of these.
                candidates = banglish.candidates(limit = PANEL_CANDIDATES),
                primary = banglish.primaryIndex(),
                expanded = candidatesExpanded
            )
        } else {
            StripModel(word = if (handler.isPasswordField) "" else stripWord)
        }
        view.setStrip(model)
    }

    // --- strip actions ---------------------------------------------------------------

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
        candidatesExpanded = !candidatesExpanded
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

    // --- listener --------------------------------------------------------------------

    override fun onText(text: String) {
        if (text.isEmpty()) return
        when (language) {
            Language.BANGLA -> bangla.type(text)
            Language.BANGLISH -> {
                if (handler.isPasswordField) {
                    handler.commit(text)
                } else if (text.all { it.isLetterOrDigit() }) {
                    banglish.type(text)
                    updateSuggestions()
                } else {
                    banglish.commitBangla()
                    handler.commit(text)
                    updateSuggestions()
                }
            }
            Language.ENGLISH -> handler.commit(text)
        }
        lastTypedWasSentenceEnd = text.endsWith(".") || text.endsWith("!") || text.endsWith("?") || text.endsWith("।")
        if (text.length == 1 && (text[0].isLetterOrDigit() || language == Language.BANGLA)) {
            stripWord = if (language == Language.BANGLA) "" else stripWord.takeLast(23) + text
        } else {
            stripWord = ""
        }
        updateSuggestions()
    }

    override fun onSpace() {
        when (language) {
            Language.BANGLA -> {
                bangla.finish()
                handler.commit(" ")
            }
            Language.BANGLISH -> {
                if (!handler.isPasswordField) banglish.commitBangla()
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
        when (language) {
            Language.BANGLA -> if (!bangla.deleteLastUnit()) handler.deleteSurrounding(1)
            Language.BANGLISH -> {
                if (!handler.isPasswordField && banglish.delete()) {
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
        bangla.finish()
        if (language == Language.BANGLISH && !handler.isPasswordField) banglish.commitBangla()
        handler.performEnter()
        stripWord = ""
        candidatesExpanded = false
        updateSuggestions()
        lastTypedWasSentenceEnd = false
    }

    override fun onSymbols() {
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
        if (language != Language.BANGLISH) return
        banglish.commit(text)
        candidatesExpanded = false
        updateSuggestions()
    }

    override fun onShiftChanged(state: ShiftState) = Unit

    /** Exposed for tests and for the settings screen's live preview of a theme file. */
    fun previewTheme(id: String): KeyboardTheme? = themeManager.byId(id)

    /** Where user-made themes are stored; used by the settings screen through ThemeManager. */
    fun userThemeDirectory(): File = File(filesDir, ThemeManager.DIR)

    companion object {

        /** How many readings the expanded panel may show. */
        private const val PANEL_CANDIDATES = 32

        /** Used only if the shipped phonetic table cannot be read, so typing never dies. */
        private const val FALLBACK_RULES =
            """{"behavior":{"hasanta":"্","oAfterConsonant":{"trailing":"ো","medial":""}},
                "rules":{"consonants":[{"roman":"k","bangla":"ক"}],
                "vowelStandalone":[{"roman":"a","bangla":"আ"}],
                "vowelKar":[],"jPhala":[],"signs":[],"digits":[]}}"""

        /**
         * Whether the system currently has this keyboard bound. Android 14 hides the
         * `default_input_method` setting from apps targeting SDK 34+, so the onboarding
         * screen asks the service instead. A Boolean, never a Context.
         */
        @Volatile
        private var running: Boolean = false

        val isRunning: Boolean get() = running

        private fun markRunning(value: Boolean) {
            running = value
        }
    }
}
