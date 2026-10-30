package app.shobdo.keyboard.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.shobdo.keyboard.ime.Language
import app.shobdo.keyboard.ime.LanguageCycle

enum class OneHanded(val stored: Int) {
    OFF(0), LEFT(1), RIGHT(2);

    companion object {
        fun from(stored: Int): OneHanded = entries.firstOrNull { it.stored == stored } ?: OFF
    }
}

enum class KeyHeight(val stored: Int, val dpPortrait: Float, val dpLandscape: Float) {
    COMPACT(0, 46f, 36f),
    NORMAL(1, 54f, 42f),
    TALL(2, 62f, 48f);

    companion object {
        fun from(stored: Int): KeyHeight = entries.firstOrNull { it.stored == stored } ?: NORMAL
    }
}

/**
 * Every user-facing setting, stored in one SharedPreferences file.
 *
 * The keyboard service and the settings activity each hold their own instance; the service
 * also listens to [preferences] so a change made in the settings screen takes effect on the
 * next keystroke without restarting the keyboard. Nothing here is static, so no Context is
 * ever leaked.
 */
class SettingsRepository(context: Context) {

    val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // --- languages -------------------------------------------------------------------

    var enableEnglish: Boolean
        get() = preferences.getBoolean(KEY_ENGLISH, true)
        set(value) = preferences.edit { putBoolean(KEY_ENGLISH, value) }

    var enableBangla: Boolean
        get() = preferences.getBoolean(KEY_BANGLA, true)
        set(value) = preferences.edit { putBoolean(KEY_BANGLA, value) }

    var enableBanglish: Boolean
        get() = preferences.getBoolean(KEY_BANGLISH, true)
        set(value) = preferences.edit { putBoolean(KEY_BANGLISH, value) }

    fun enabledLanguages(): List<Language> =
        LanguageCycle.enabled(enableEnglish, enableBangla, enableBanglish)

    /**
     * Turns [language] on or off. Returns false when the change is refused because it would
     * leave no language enabled, which is the only rule the user cannot break.
     */
    fun setLanguageEnabled(language: Language, enabled: Boolean): Boolean {
        val current = enabledLanguages()
        if (!enabled && !LanguageCycle.canDisable(current, language)) return false
        when (language) {
            Language.ENGLISH -> enableEnglish = enabled
            Language.BANGLA -> enableBangla = enabled
            Language.BANGLISH -> enableBanglish = enabled
        }
        return true
    }

    /** Last language used; the keyboard restores it when it opens again. */
    var lastLanguage: Language?
        get() = Language.fromId(preferences.getString(KEY_LAST_LANGUAGE, null))
        set(value) = preferences.edit { putString(KEY_LAST_LANGUAGE, value?.id) }

    // --- appearance ------------------------------------------------------------------

    var themeId: String
        get() = preferences.getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
        set(value) = preferences.edit { putString(KEY_THEME, value) }

    var keyHeight: KeyHeight
        get() = KeyHeight.from(preferences.getInt(KEY_KEY_HEIGHT, KeyHeight.NORMAL.stored))
        set(value) = preferences.edit { putInt(KEY_KEY_HEIGHT, value.stored) }

    var oneHanded: OneHanded
        get() = OneHanded.from(preferences.getInt(KEY_ONE_HANDED, OneHanded.OFF.stored))
        set(value) = preferences.edit { putInt(KEY_ONE_HANDED, value.stored) }

    var numberRow: Boolean
        get() = preferences.getBoolean(KEY_NUMBER_ROW, true)
        set(value) = preferences.edit { putBoolean(KEY_NUMBER_ROW, value) }

    var keyBorders: Boolean
        get() = preferences.getBoolean(KEY_KEY_BORDERS, true)
        set(value) = preferences.edit { putBoolean(KEY_KEY_BORDERS, value) }

    /** Absolute path of the user's own background picture, or null for the theme's own. */
    var backgroundImage: String?
        get() = preferences.getString(KEY_BACKGROUND, null)
        set(value) = preferences.edit { putString(KEY_BACKGROUND, value) }

    var backgroundBrightness: Float
        get() = preferences.getFloat(KEY_BACKGROUND_BRIGHTNESS, 1f)
        set(value) = preferences.edit().putFloat(KEY_BACKGROUND_BRIGHTNESS, value.coerceIn(0f, 1f)).apply()

    var backgroundBlur: Float
        get() = preferences.getFloat(KEY_BACKGROUND_BLUR, 0f)
        set(value) = preferences.edit().putFloat(KEY_BACKGROUND_BLUR, value.coerceIn(0f, 40f)).apply()

    var fontScale: Float
        get() = preferences.getFloat(KEY_FONT_SCALE, 1f)
        set(value) = preferences.edit().putFloat(KEY_FONT_SCALE, value.coerceIn(0.7f, 1.6f)).apply()

    var boldText: Boolean
        get() = preferences.getBoolean(KEY_BOLD, false)
        set(value) = preferences.edit { putBoolean(KEY_BOLD, value) }

    // --- typing ----------------------------------------------------------------------

    var hapticEnabled: Boolean
        get() = preferences.getBoolean(KEY_HAPTIC, true)
        set(value) = preferences.edit { putBoolean(KEY_HAPTIC, value) }

    /** 0 = light, 1 = normal, 2 = strong. */
    var hapticStrength: Int
        get() = preferences.getInt(KEY_HAPTIC_STRENGTH, 1)
        set(value) = preferences.edit().putInt(KEY_HAPTIC_STRENGTH, value.coerceIn(0, 2)).apply()

    var soundEnabled: Boolean
        get() = preferences.getBoolean(KEY_SOUND, false)
        set(value) = preferences.edit { putBoolean(KEY_SOUND, value) }

    var autoCapitalise: Boolean
        get() = preferences.getBoolean(KEY_AUTO_CAPITALISE, true)
        set(value) = preferences.edit { putBoolean(KEY_AUTO_CAPITALISE, value) }

    var keyPreview: Boolean
        get() = preferences.getBoolean(KEY_PREVIEW, true)
        set(value) = preferences.edit { putBoolean(KEY_PREVIEW, value) }

    /** Bangla candidate before the Roman text in the Banglish suggestion bar. */
    var banglaCandidateFirst: Boolean
        get() = preferences.getBoolean(KEY_BANGLA_FIRST, true)
        set(value) = preferences.edit { putBoolean(KEY_BANGLA_FIRST, value) }

    // --- onboarding ------------------------------------------------------------------

    var onboardingComplete: Boolean
        get() = preferences.getBoolean(KEY_ONBOARDED, false)
        set(value) = preferences.edit { putBoolean(KEY_ONBOARDED, value) }

    /** True once the user has opened the languages and theme step, which is what completes it. */
    var setupChosen: Boolean
        get() = preferences.getBoolean(KEY_SETUP_CHOSEN, false)
        set(value) = preferences.edit { putBoolean(KEY_SETUP_CHOSEN, value) }

    companion object {
        const val FILE = "shobdo_settings"
        /**
         * Not a theme id: the keyboard resolves this to Studio Light or Studio Dark from the
         * system night mode, which is the documented default.
         */
        const val DEFAULT_THEME = "system"

        const val KEY_ENGLISH = "enable_english"
        const val KEY_BANGLA = "enable_bangla"
        const val KEY_BANGLISH = "enable_banglish"
        const val KEY_LAST_LANGUAGE = "last_language"
        const val KEY_THEME = "theme_id"
        const val KEY_KEY_HEIGHT = "key_height"
        const val KEY_ONE_HANDED = "one_handed"
        const val KEY_NUMBER_ROW = "number_row"
        const val KEY_KEY_BORDERS = "key_borders"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_BOLD = "bold_text"
        const val KEY_BACKGROUND = "background_image"
        const val KEY_BACKGROUND_BRIGHTNESS = "background_brightness"
        const val KEY_BACKGROUND_BLUR = "background_blur"
        const val KEY_HAPTIC = "haptic_enabled"
        const val KEY_HAPTIC_STRENGTH = "haptic_strength"
        const val KEY_SOUND = "sound_enabled"
        const val KEY_AUTO_CAPITALISE = "auto_capitalise"
        const val KEY_PREVIEW = "key_preview"
        const val KEY_BANGLA_FIRST = "bangla_candidate_first"
        const val KEY_ONBOARDED = "onboarding_complete"
        const val KEY_SETUP_CHOSEN = "setup_chosen"
    }
}
