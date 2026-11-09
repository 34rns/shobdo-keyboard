package app.shobdo.keyboard.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.shobdo.keyboard.R
import app.shobdo.keyboard.banglish.LearnedWords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

/** Explicit names/dialect words; separate from automatic learned counts. */
@Composable
fun UserDictionaryScreen(learned: LearnedWords?, onBack: () -> Unit) {
    var roman by remember { mutableStateOf("") }
    var word by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(emptyList<LearnedWords.DictionaryWord>()) }
    var invalid by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(learned) { entries = withContext(Dispatchers.IO) { learned?.dictionary().orEmpty() } }
    SectionCard(title = stringResource(R.string.dictionary_title)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.dictionary_body), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(roman, { roman = it.take(40); invalid = false },
                label = { Text(stringResource(R.string.dictionary_roman)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(), isError = invalid)
            OutlinedTextField(word, { word = it.take(40); invalid = false },
                label = { Text(stringResource(R.string.dictionary_word)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), isError = invalid)
            if (invalid) Text(stringResource(R.string.dictionary_error), color = MaterialTheme.colorScheme.error)
            Button(enabled = learned != null && !busy && roman.isNotBlank() && word.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp), onClick = {
                    busy = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            learned?.add(roman.trim(), word.trim()) == true && learned.flush()
                        }
                        invalid = !ok
                        if (ok) { roman = ""; word = "" }
                        entries = withContext(Dispatchers.IO) { learned?.dictionary().orEmpty() }
                        busy = false
                    }
                }) { Text(stringResource(R.string.dictionary_add)) }
            if (learned == null) Text(stringResource(R.string.dictionary_loading))
            else if (entries.isEmpty()) Text(stringResource(R.string.dictionary_empty))
            entries.forEach { entry ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(entry.word); Text(entry.roman, style = MaterialTheme.typography.bodySmall) }
                    TextButton(enabled = !busy, modifier = Modifier.heightIn(min = 48.dp), onClick = {
                        busy = true
                        scope.launch {
                            val saved = withContext(Dispatchers.IO) { learned?.remove(entry.word); learned?.flush() == true }
                            invalid = !saved
                            entries = withContext(Dispatchers.IO) { learned?.dictionary().orEmpty() }
                            busy = false
                        }
                    }) { Text(stringResource(R.string.dictionary_remove, entry.word)) }
                }
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.dictionary_back))
            }
        }
    }
}
