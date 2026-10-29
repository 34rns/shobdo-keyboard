package app.shobdo.keyboard.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.shobdo.keyboard.R
import app.shobdo.keyboard.settings.KeyHeight
import app.shobdo.keyboard.settings.OneHanded
import app.shobdo.keyboard.settings.SettingsRepository
import java.io.File

/**
 * Typing options. Everything here is applied by the keyboard the next time it draws: the
 * service listens for preference changes, so no restart is needed.
 */
@Composable
fun TypingScreen(repository: SettingsRepository, snackbarMessage: (String) -> Unit) {
    val context = LocalContext.current

    var haptic by remember { mutableStateOf(repository.hapticEnabled) }
    var strength by remember { mutableStateOf(repository.hapticStrength) }
    var sound by remember { mutableStateOf(repository.soundEnabled) }
    var numberRow by remember { mutableStateOf(repository.numberRow) }
    var autoCaps by remember { mutableStateOf(repository.autoCapitalise) }
    var popup by remember { mutableStateOf(repository.keyPreview) }
    var borders by remember { mutableStateOf(repository.keyBorders) }
    var bold by remember { mutableStateOf(repository.boldText) }
    var keyHeight by remember { mutableStateOf(repository.keyHeight) }
    var oneHanded by remember { mutableStateOf(repository.oneHanded) }
    var fontScale by remember { mutableFloatStateOf(repository.fontScale) }
    var background by remember { mutableStateOf(repository.backgroundImage) }
    var brightness by remember { mutableFloatStateOf(repository.backgroundBrightness) }
    var blur by remember { mutableFloatStateOf(repository.backgroundBlur) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val target = File(context.filesDir, "background_custom")
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }.isSuccess
            if (copied) {
                repository.backgroundImage = target.absolutePath
                background = target.absolutePath
                snackbarMessage(context.getString(R.string.background_updated))
            } else {
                snackbarMessage(context.getString(R.string.background_unreadable))
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        SectionCard(title = stringResource(R.string.typing_title)) {
            SwitchRow(
                label = stringResource(R.string.typing_haptic),
                checked = haptic,
                description = stringResource(R.string.typing_haptic),
                onCheckedChange = { haptic = it; repository.hapticEnabled = it }
            )
            RowDivider()
            ChoiceRow(
                label = stringResource(R.string.typing_haptic_strength),
                options = listOf(
                    0 to stringResource(R.string.haptic_light),
                    1 to stringResource(R.string.haptic_normal),
                    2 to stringResource(R.string.haptic_strong)
                ),
                selected = strength,
                onSelect = { strength = it; repository.hapticStrength = it }
            )
            RowDivider()
            SwitchRow(
                label = stringResource(R.string.typing_sound),
                checked = sound,
                description = stringResource(R.string.typing_sound),
                onCheckedChange = { sound = it; repository.soundEnabled = it }
            )
            RowDivider()
            SwitchRow(
                label = stringResource(R.string.typing_key_popup),
                checked = popup,
                description = stringResource(R.string.typing_key_popup),
                onCheckedChange = { popup = it; repository.keyPreview = it }
            )
            RowDivider()
            SwitchRow(
                label = stringResource(R.string.typing_auto_capitalise),
                checked = autoCaps,
                description = stringResource(R.string.typing_auto_capitalise),
                onCheckedChange = { autoCaps = it; repository.autoCapitalise = it }
            )
        }

        SectionCard(title = stringResource(R.string.typing_key_height)) {
            SwitchRow(
                label = stringResource(R.string.typing_number_row),
                checked = numberRow,
                description = stringResource(R.string.typing_number_row),
                onCheckedChange = { numberRow = it; repository.numberRow = it }
            )
            RowDivider()
            ChoiceRow(
                label = stringResource(R.string.typing_key_height),
                options = listOf(
                    KeyHeight.COMPACT to stringResource(R.string.key_height_compact),
                    KeyHeight.NORMAL to stringResource(R.string.key_height_normal),
                    KeyHeight.TALL to stringResource(R.string.key_height_tall)
                ),
                selected = keyHeight,
                onSelect = { keyHeight = it; repository.keyHeight = it }
            )
            RowDivider()
            ChoiceRow(
                label = stringResource(R.string.typing_one_handed),
                options = listOf(
                    OneHanded.OFF to stringResource(R.string.one_handed_off),
                    OneHanded.LEFT to stringResource(R.string.one_handed_left),
                    OneHanded.RIGHT to stringResource(R.string.one_handed_right)
                ),
                selected = oneHanded,
                onSelect = { oneHanded = it; repository.oneHanded = it }
            )
        }

        SectionCard(title = stringResource(R.string.typing_font_size)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "${(fontScale * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = fontScale,
                    onValueChange = { fontScale = it; repository.fontScale = it },
                    valueRange = 0.7f..1.6f,
                    modifier = Modifier.semantics {
                        contentDescription = context.getString(
                            R.string.cd_slider,
                            context.getString(R.string.typing_font_size)
                        )
                    }
                )
            }
            RowDivider()
            SwitchRow(
                label = stringResource(R.string.typing_bold),
                checked = bold,
                description = stringResource(R.string.typing_bold),
                onCheckedChange = { bold = it; repository.boldText = it }
            )
            RowDivider()
            SwitchRow(
                label = stringResource(R.string.typing_key_borders),
                checked = borders,
                description = stringResource(R.string.typing_key_borders),
                onCheckedChange = { borders = it; repository.keyBorders = it }
            )
        }

        SectionCard(title = stringResource(R.string.typing_background)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { pickImage.launch("image/*") },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text(stringResource(R.string.typing_background_choose)) }
                if (background != null) {
                    TextButton(
                        onClick = {
                            repository.backgroundImage = null
                            background = null
                        },
                        modifier = Modifier.height(48.dp)
                    ) { Text(stringResource(R.string.typing_background_remove)) }
                }
            }
            if (background != null) {
                SliderRow(
                    label = stringResource(R.string.typing_background_brightness),
                    value = brightness,
                    range = 0.2f..1f
                ) { brightness = it; repository.backgroundBrightness = it }
                SliderRow(
                    label = stringResource(R.string.typing_background_blur),
                    value = blur,
                    range = 0f..24f
                ) { blur = it; repository.backgroundBlur = it }
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val sliderLabel = androidx.compose.ui.platform.LocalContext.current.getString(R.string.cd_slider, label)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.semantics { contentDescription = sliderLabel }
        )
    }
}
