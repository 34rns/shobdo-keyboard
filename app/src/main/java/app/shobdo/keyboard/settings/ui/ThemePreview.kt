package app.shobdo.keyboard.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.shobdo.keyboard.settings.OneHanded
import app.shobdo.keyboard.ime.KeyModel
import app.shobdo.keyboard.ime.KeyType
import app.shobdo.keyboard.ime.KeyboardLayout
import app.shobdo.keyboard.ime.KeyboardStyle
import app.shobdo.keyboard.ime.KeyboardView
import app.shobdo.keyboard.theme.KeyboardTheme

/**
 * A theme preview rendered by the real [KeyboardView], not by a row of coloured boxes.
 *
 * It is a real, four-row keyboard at a small key height: identical drawing code, identical
 * cached key bitmaps, identical icons, so what the grid shows is what the keyboard will look
 * like — including the key depth, the corner shape and the accent action key.
 */
@Composable
fun ThemeKeyboardPreview(theme: KeyboardTheme, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            KeyboardView(context).apply {
                setStyle(PREVIEW_STYLE)
                setLayout(PREVIEW_LAYOUT)
                setTheme(theme)
            }
        },
        update = { view ->
            view.setTheme(theme)
            view.setLayout(PREVIEW_LAYOUT)
        }
    )
}

private val PREVIEW_STYLE = KeyboardStyle(
    keyHeightDpPortrait = 24f,
    keyHeightDpLandscape = 24f,
    borders = true,
    popup = false,
    preview = false,
    haptic = false,
    sound = false,
    fontScale = 1f,
    oneHanded = OneHanded.OFF,
    numberRow = false,
    shiftUsesAlternates = false
)

private fun row(vararg keys: KeyModel): List<KeyModel> = keys.toList()

private fun letter(label: String) = KeyModel(label = label, code = label)

private val PREVIEW_LAYOUT = KeyboardLayout(
    id = "preview",
    name = "Preview",
    rows = listOf(
        row(
            letter("q"), letter("w"), letter("e"), letter("r"), letter("t"),
            letter("y"), letter("u"), letter("i"), letter("o"), letter("p")
        ),
        row(
            letter("a"), letter("s"), letter("d"), letter("f"), letter("g"),
            letter("h"), letter("j"), letter("k"), letter("l")
        ),
        row(
            KeyModel("", type = KeyType.SHIFT, widthWeight = 1.5f),
            letter("z"), letter("x"), letter("c"), letter("v"),
            letter("b"), letter("n"), letter("m"),
            KeyModel("", type = KeyType.BACKSPACE, widthWeight = 1.5f)
        ),
        row(
            KeyModel("123", type = KeyType.SYMBOLS, widthWeight = 1.25f),
            KeyModel(",", ",", KeyType.COMMA, 0.8f),
            KeyModel("", type = KeyType.GLOBE),
            KeyModel("space", " ", KeyType.SPACE, 4f),
            KeyModel(".", ".", KeyType.PERIOD, 0.8f),
            KeyModel("⏎", "\n", KeyType.ENTER, 1.5f)
        )
    )
)
