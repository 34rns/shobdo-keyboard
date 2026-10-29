package app.shobdo.keyboard.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.shobdo.keyboard.R
import app.shobdo.keyboard.settings.SettingsRepository

/**
 * First launch. Three steps, each with a big button and a check mark that turns on by itself
 * when the step is actually done — the keyboard being enabled and selected are read from the
 * system, not from a flag we set.
 */
@Composable
fun OnboardingScreen(
    repository: SettingsRepository,
    imeEnabled: Boolean,
    imeSelected: Boolean,
    onOpenKeyboardSettings: () -> Unit,
    onChooseKeyboard: () -> Unit,
    onOpenSetup: () -> Unit,
    onFinish: () -> Unit
) {
    val setupDone = repository.setupChosen

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            stringResource(R.string.home_languages_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        StepCard(
            number = 1,
            title = stringResource(R.string.onboarding_enable_title),
            description = stringResource(R.string.onboarding_enable_desc),
            done = imeEnabled,
            buttonText = stringResource(R.string.onboarding_enable_button),
            onClick = onOpenKeyboardSettings
        )
        StepCard(
            number = 2,
            title = stringResource(R.string.onboarding_choose_title),
            description = stringResource(R.string.onboarding_choose_desc),
            done = imeSelected,
            buttonText = stringResource(R.string.onboarding_choose_button),
            onClick = onChooseKeyboard
        )
        StepCard(
            number = 3,
            title = stringResource(R.string.onboarding_setup_title),
            description = stringResource(R.string.onboarding_setup_desc),
            done = setupDone,
            buttonText = stringResource(R.string.onboarding_setup_button),
            onClick = onOpenSetup
        )

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(stringResource(R.string.onboarding_finish), style = MaterialTheme.typography.titleMedium)
        }
        val skipLabel = stringResource(R.string.onboarding_skip)
        TextButton(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics { contentDescription = skipLabel }
        ) {
            Text(skipLabel)
        }
    }
}

private const val TOTAL_STEPS = 3

@Composable
private fun StepCard(
    number: Int,
    title: String,
    description: String,
    done: Boolean,
    buttonText: String,
    onClick: () -> Unit
) {
    val mark = if (done) "✓" else number.toString()
    val markDescription = stringResource(if (done) R.string.cd_step_done else R.string.cd_step_pending)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (done) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    mark,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = markDescription }
                )
                Spacer(Modifier.height(0.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.onboarding_step_of, number, TOTAL_STEPS),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(52.dp)
            ) {
                Text(buttonText, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
