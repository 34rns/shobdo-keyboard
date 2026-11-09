package app.shobdo.keyboard.ime

import app.shobdo.keyboard.settings.SettingsRepository
import app.shobdo.keyboard.theme.KeyboardTheme

internal fun keyboardStyle(settings: SettingsRepository, theme: KeyboardTheme?, language: Language): KeyboardStyle {
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

internal fun withEnterLabel(page: KeyboardLayout, label: String): KeyboardLayout {
    if (label == "⏎") return page
    val rows = page.rows.map { row ->
        row.map { key -> if (key.type == KeyType.ENTER) key.copy(label = label) else key }
    }
    return page.copy(rows = rows)
}
