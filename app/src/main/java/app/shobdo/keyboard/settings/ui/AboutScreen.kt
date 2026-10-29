package app.shobdo.keyboard.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.shobdo.keyboard.BuildConfig
import app.shobdo.keyboard.R

@Composable
fun AboutScreen(onOpenGitHub: () -> Unit) {
    var showLicences by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        SectionCard(title = stringResource(R.string.about_title)) {
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
            )
            RowDivider()
            NavRow(label = stringResource(R.string.about_licenses), onClick = { showLicences = true })
            RowDivider()
            NavRow(label = stringResource(R.string.about_github), onClick = onOpenGitHub)
        }

        SectionCard(title = stringResource(R.string.about_privacy)) {
            Text(
                stringResource(R.string.about_privacy_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
            Text(
                stringResource(R.string.home_languages_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            )
        }

        val githubLabel = stringResource(R.string.cd_open_github)
        Button(
            onClick = onOpenGitHub,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .semantics { contentDescription = githubLabel }
        ) {
            Text(stringResource(R.string.about_github))
        }
    }

    if (showLicences) {
        AlertDialog(
            onDismissRequest = { showLicences = false },
            title = { Text(stringResource(R.string.about_licenses)) },
            text = { Text(stringResource(R.string.about_licenses_body)) },
            confirmButton = {
                TextButton(onClick = { showLicences = false }) { Text(stringResource(R.string.common_ok)) }
            }
        )
    }
}
