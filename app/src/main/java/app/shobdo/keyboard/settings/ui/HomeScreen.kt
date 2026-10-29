package app.shobdo.keyboard.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.shobdo.keyboard.R
import app.shobdo.keyboard.ime.Language
import app.shobdo.keyboard.settings.SettingsRepository
import app.shobdo.keyboard.theme.ThemeManager
import kotlinx.coroutines.launch

/**
 * Home: the language switches, a test field, and the way in to everything else.
 *
 * The switches enforce the one rule the keyboard has: at least one language stays on. A
 * refused toggle explains itself in a snackbar instead of silently doing nothing.
 */
@Composable
fun HomeScreen(
    repository: SettingsRepository,
    themeManager: ThemeManager,
    snackbar: SnackbarHostState,
    onOpenThemes: () -> Unit,
    onOpenTyping: () -> Unit,
    onOpenBanglish: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val lastWarning = stringResource(R.string.language_last_warning)

    var english by remember { mutableStateOf(repository.enableEnglish) }
    var bangla by remember { mutableStateOf(repository.enableBangla) }
    var banglish by remember { mutableStateOf(repository.enableBanglish) }
    var testText by remember { mutableStateOf("") }
    val themeName = remember(repository.themeId) {
        themeManager.byId(repository.themeId)?.name ?: repository.themeId
    }

    fun toggle(language: Language, enabled: Boolean, apply: (Boolean) -> Unit) {
        if (repository.setLanguageEnabled(language, enabled)) {
            apply(enabled)
        } else {
            scope.launch { snackbar.showSnackbar(lastWarning) }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        SectionCard(title = stringResource(R.string.home_languages)) {
            Text(
                stringResource(R.string.home_languages_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
            SwitchRow(
                label = stringResource(R.string.language_english),
                checked = english,
                description = stringResource(R.string.cd_language_switch, stringResource(R.string.language_english)),
                onCheckedChange = { toggle(Language.ENGLISH, it) { value -> english = value } }
            )
            RowDivider()
            SwitchRow(
                label = stringResource(R.string.language_bangla),
                checked = bangla,
                description = stringResource(R.string.cd_language_switch, stringResource(R.string.language_bangla)),
                onCheckedChange = { toggle(Language.BANGLA, it) { value -> bangla = value } }
            )
            RowDivider()
            SwitchRow(
                label = stringResource(R.string.language_banglish),
                checked = banglish,
                description = stringResource(R.string.cd_language_switch, stringResource(R.string.language_banglish)),
                onCheckedChange = { toggle(Language.BANGLISH, it) { value -> banglish = value } }
            )
        }

        SectionCard(title = stringResource(R.string.home_theme)) {
            NavRow(label = themeName, onClick = onOpenThemes)
        }

        SectionCard(title = stringResource(R.string.home_try_keyboard)) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                OutlinedTextField(
                    value = testText,
                    onValueChange = { testText = it },
                    label = { Text(stringResource(R.string.home_test_field_hint)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(8.dp)
                )
            }
        }

        SectionCard(title = stringResource(R.string.home_more)) {
            NavRow(label = stringResource(R.string.typing_title), onClick = onOpenTyping)
            RowDivider()
            NavRow(label = stringResource(R.string.banglish_title), onClick = onOpenBanglish)
            RowDivider()
            NavRow(label = stringResource(R.string.about_title), onClick = onOpenAbout)
        }

        Text(
            stringResource(R.string.about_privacy_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp)
        )
    }
}
