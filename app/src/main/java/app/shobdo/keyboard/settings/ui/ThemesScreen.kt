package app.shobdo.keyboard.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.shobdo.keyboard.R
import app.shobdo.keyboard.settings.SettingsRepository
import app.shobdo.keyboard.theme.Argb
import app.shobdo.keyboard.theme.KeyboardTheme
import app.shobdo.keyboard.theme.ThemeBackground
import app.shobdo.keyboard.theme.ThemeManager
import kotlinx.coroutines.launch

/** Grid of every theme with a live swatch, plus create, duplicate, export and import. */
@Composable
fun ThemesScreen(
    themeManager: ThemeManager,
    repository: SettingsRepository,
    snackbar: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var themes by remember { mutableStateOf(themeManager.all()) }
    var activeId by remember { mutableStateOf(repository.themeId) }
    var selected by remember { mutableStateOf<KeyboardTheme?>(null) }
    var editing by remember { mutableStateOf<KeyboardTheme?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            themeManager.importFrom(uri).fold(
                onSuccess = { imported ->
                    themes = themeManager.all()
                    repository.themeId = imported.id
                    activeId = imported.id
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.theme_import_done)) }
                },
                onFailure = { error ->
                    scope.launch {
                        snackbar.showSnackbar(
                            context.getString(R.string.theme_import_failed) + ": " + (error.message ?: "")
                        )
                    }
                }
            )
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val theme = selected
        if (uri != null && theme != null) {
            themeManager.exportTo(uri, theme).onFailure { error ->
                scope.launch { snackbar.showSnackbar(error.message ?: "export failed") }
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.weight(1f).height(48.dp)
            ) { Text(stringResource(R.string.theme_import)) }
            val createLabel = stringResource(R.string.cd_create_theme)
            OutlinedButton(
                onClick = {
                    val current = themeManager.byId(activeId) ?: themes.first()
                    editing = themeManager.duplicate(current)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .semantics { contentDescription = createLabel }
            ) { Text(stringResource(R.string.theme_create)) }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(themes, key = { it.id }) { theme ->
                ThemeCard(
                    theme = theme,
                    active = theme.id == activeId,
                    onClick = { selected = theme }
                )
            }
        }
    }

    selected?.let { theme ->
        ThemeDialog(
            theme = theme,
            active = theme.id == activeId,
            onApply = {
                repository.themeId = theme.id
                activeId = theme.id
                selected = null
            },
            onDuplicate = {
                editing = themeManager.duplicate(theme)
                selected = null
            },
            onExport = {
                exportLauncher.launch(ThemeManager.exportFileName(theme))
            },
            onDelete = {
                if (themeManager.delete(theme.id)) {
                    themes = themeManager.all()
                    if (activeId == theme.id) {
                        val fallback = themes.first()
                        repository.themeId = fallback.id
                        activeId = fallback.id
                    }
                }
                selected = null
            },
            onDismiss = { selected = null }
        )
    }

    editing?.let { theme ->
        ThemeEditorDialog(
            theme = theme,
            onDismiss = { editing = null },
            onSave = { edited ->
                themeManager.save(edited).fold(
                    onSuccess = {
                        themes = themeManager.all()
                        repository.themeId = it.id
                        activeId = it.id
                        editing = null
                        scope.launch { snackbar.showSnackbar(context.getString(R.string.theme_saved, it.name)) }
                    },
                    onFailure = { error ->
                        scope.launch { snackbar.showSnackbar(error.message ?: "could not save") }
                    }
                )
            }
        )
    }
}

@Composable
private fun ThemeCard(theme: KeyboardTheme, active: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val cardLabel = context.getString(R.string.cd_theme_card, theme.name)
    val background = when (val bg = theme.background) {
        is ThemeBackground.Solid -> Color(bg.color)
        is ThemeBackground.Gradient -> Color(bg.colors.first())
        is ThemeBackground.Image -> Color(bg.fallback)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = cardLabel },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            // The card body is the theme's own canvas, so the label has to be coloured
            // explicitly: inheriting the card's content colour painted dark names on dark
            // themes and left them unreadable.
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeKeyboardPreview(theme = theme, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    theme.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (active) {
                    Text(
                        stringResource(R.string.theme_current),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeDialog(
    theme: KeyboardTheme,
    active: Boolean,
    onApply: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(theme.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(if (theme.builtIn) R.string.theme_builtin else R.string.theme_user),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = onDuplicate) { Text(stringResource(R.string.theme_duplicate)) }
                TextButton(onClick = onExport) { Text(stringResource(R.string.theme_export)) }
                if (!theme.builtIn) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.theme_delete)) }
                }
            }
        },
        confirmButton = {
            Button(onClick = onApply, enabled = !active) { Text(stringResource(R.string.theme_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

/** Rename and recolour a theme, with the contrast rule enforced on save. */
@Composable
private fun ThemeEditorDialog(
    theme: KeyboardTheme,
    onDismiss: () -> Unit,
    onSave: (KeyboardTheme) -> Unit
) {
    var name by remember { mutableStateOf(theme.name) }
    var fill by remember { mutableStateOf(Argb.toHex(theme.keyFill)) }
    var text by remember { mutableStateOf(Argb.toHex(theme.keyText)) }
    var accent by remember { mutableStateOf(Argb.toHex(theme.accent)) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.theme_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fill,
                    onValueChange = { fill = it },
                    label = { Text(stringResource(R.string.theme_key_fill)) },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.theme_key_text)) },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accent,
                    onValueChange = { accent = it },
                    label = { Text(stringResource(R.string.theme_accent)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = runCatching {
                    theme.copy(
                        name = name,
                        keyFill = Argb.parse(fill),
                        keyText = Argb.parse(text),
                        accent = Argb.parse(accent)
                    )
                }
                parsed.fold(
                    onSuccess = { onSave(it) },
                    onFailure = { error = it.message ?: "invalid colour" }
                )
            }) { Text(stringResource(R.string.theme_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}
