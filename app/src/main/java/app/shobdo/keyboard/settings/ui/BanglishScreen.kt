package app.shobdo.keyboard.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.shobdo.keyboard.R
import app.shobdo.keyboard.banglish.CandidateEngine
import app.shobdo.keyboard.banglish.LearnedWords
import app.shobdo.keyboard.settings.SettingsRepository

/**
 * Banglish options, a live sample of the conversion, and the two switches that govern what the
 * keyboard is allowed to remember.
 */
@Composable
fun BanglishScreen(
    repository: SettingsRepository,
    engine: CandidateEngine?,
    learned: LearnedWords?
) {
    var banglaFirst by remember { mutableStateOf(repository.banglaCandidateFirst) }
    var learn by remember { mutableStateOf(repository.learnBanglish) }
    var cleared by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        SectionCard(title = stringResource(R.string.banglish_title)) {
            ChoiceRow(
                label = stringResource(R.string.banglish_candidate_order),
                options = listOf(
                    true to stringResource(R.string.banglish_bangla_first),
                    false to stringResource(R.string.banglish_roman_first)
                ),
                selected = banglaFirst,
                onSelect = {
                    banglaFirst = it
                    repository.banglaCandidateFirst = it
                }
            )
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(
                    stringResource(R.string.banglish_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionCard(title = stringResource(R.string.banglish_learn_title)) {
            SwitchRow(
                label = stringResource(R.string.banglish_learn_switch),
                checked = learn,
                description = stringResource(R.string.cd_banglish_learn),
                supporting = stringResource(R.string.banglish_learn_body),
                onCheckedChange = {
                    learn = it
                    repository.learnBanglish = it
                }
            )
            RowDivider()
            NavRow(
                label = stringResource(R.string.banglish_clear),
                value = if (cleared) stringResource(R.string.banglish_cleared) else null,
                onClick = {
                    learned?.clear()
                    cleared = true
                }
            )
        }

        SectionCard(title = stringResource(R.string.banglish_examples)) {
            val samples = listOf("ami", "tumi", "bhalo", "bangla", "kemon", "dhonnobad")
            samples.forEachIndexed { index, roman ->
                if (index > 0) RowDivider()
                val bangla = engine?.candidates(roman)?.firstOrNull()?.text ?: roman
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(roman, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        bangla,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
