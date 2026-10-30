package app.shobdo.keyboard.settings.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.shobdo.keyboard.BuildInfo
import app.shobdo.keyboard.R
import app.shobdo.keyboard.banglish.BanglishEngine
import app.shobdo.keyboard.banglish.BanglishLexicon
import app.shobdo.keyboard.banglish.CandidateEngine
import app.shobdo.keyboard.banglish.LearnedWords
import app.shobdo.keyboard.banglish.SkeletonRules
import java.io.File
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.shobdo.keyboard.settings.ImeStatus
import app.shobdo.keyboard.settings.SettingsRepository
import app.shobdo.keyboard.theme.ThemeManager
import kotlinx.coroutines.launch

private enum class Screen { ONBOARDING, HOME, THEMES, TYPING, BANGLISH, ABOUT }

/**
 * The settings app: one activity, a handful of screens and no navigation library, because a
 * six-destination app does not need one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShobdoApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { SettingsRepository(context) }
    val themeManager = remember { ThemeManager(context) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var screen by rememberSaveable {
        mutableStateOf(if (repository.onboardingComplete) Screen.HOME.name else Screen.ONBOARDING.name)
    }
    var imeEnabled by remember { mutableStateOf(ImeStatus.isEnabled(context)) }
    var imeSelected by remember { mutableStateOf(ImeStatus.isSelected(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                imeEnabled = ImeStatus.isEnabled(context)
                imeSelected = ImeStatus.isSelected(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The word list is 1.3 MB, so the settings screen loads it off the main thread too.
    var engine by remember { mutableStateOf<CandidateEngine?>(null) }
    LaunchedEffect(Unit) {
        engine = withContext(Dispatchers.IO) {
            runCatching {
                val rules = SkeletonRules.fromJson(
                    context.assets.open(SkeletonRules.ASSET_PATH).bufferedReader().use { it.readText() }
                )
                val phonetic = BanglishEngine.fromJson(
                    context.assets.open(BanglishEngine.RULE_ASSET_PATH).bufferedReader().use { it.readText() }
                )
                val lexiconText = context.assets.open(BanglishLexicon.ASSET_PATH)
                    .bufferedReader().use { it.readText() }
                CandidateEngine(phonetic, rules, BanglishLexicon.parse(lexiconText.lines()))
            }.getOrNull()
        }
    }
    val learned = remember { LearnedWords(File(context.filesDir, LearnedWords.FILE_NAME)) }

    val current = Screen.valueOf(screen)
    val rootScreen = if (repository.onboardingComplete) Screen.HOME else Screen.ONBOARDING

    fun go(target: Screen) {
        screen = target.name
    }

    fun back() {
        screen = rootScreen.name
    }

    BackHandler(enabled = current != rootScreen) { back() }

    val title = when (current) {
        Screen.ONBOARDING -> stringResource(R.string.app_name)
        Screen.HOME -> stringResource(R.string.home_title)
        Screen.THEMES -> stringResource(R.string.themes_title)
        Screen.TYPING -> stringResource(R.string.typing_title)
        Screen.BANGLISH -> stringResource(R.string.banglish_title)
        Screen.ABOUT -> stringResource(R.string.about_title)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    if (current != rootScreen) {
                        val backLabel = stringResource(R.string.cd_navigate_back)
                        IconButton(
                            onClick = { back() },
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { contentDescription = backLabel }
                        ) {
                            Text("‹", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (current) {
            Screen.ONBOARDING -> androidx.compose.foundation.layout.Box(contentModifier) {
                OnboardingScreen(
                    repository = repository,
                    imeEnabled = imeEnabled,
                    imeSelected = imeSelected,
                    onOpenKeyboardSettings = {
                        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    },
                    onChooseKeyboard = { ImeStatus.showPicker(context) },
                    onOpenSetup = {
                        repository.setupChosen = true
                        go(Screen.THEMES)
                    },
                    onFinish = {
                        repository.onboardingComplete = true
                        go(Screen.HOME)
                    }
                )
            }

            Screen.HOME -> androidx.compose.foundation.layout.Box(contentModifier) {
                HomeScreen(
                    repository = repository,
                    themeManager = themeManager,
                    snackbar = snackbar,
                    onOpenThemes = { go(Screen.THEMES) },
                    onOpenTyping = { go(Screen.TYPING) },
                    onOpenBanglish = { go(Screen.BANGLISH) },
                    onOpenAbout = { go(Screen.ABOUT) }
                )
            }

            Screen.THEMES -> androidx.compose.foundation.layout.Box(contentModifier) {
                ThemesScreen(themeManager = themeManager, repository = repository, snackbar = snackbar)
            }

            Screen.TYPING -> androidx.compose.foundation.layout.Box(contentModifier) {
                TypingScreen(repository = repository) { message ->
                    scope.launch { snackbar.showSnackbar(message) }
                }
            }

            Screen.BANGLISH -> androidx.compose.foundation.layout.Box(contentModifier) {
                BanglishScreen(repository = repository, engine = engine, learned = learned)
            }

            Screen.ABOUT -> androidx.compose.foundation.layout.Box(contentModifier) {
                AboutScreen(onOpenGitHub = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildInfo.GITHUB_URL)))
                    }
                })
            }
        }
    }
}
