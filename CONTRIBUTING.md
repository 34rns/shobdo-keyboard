# Contributing to Shobdo Keyboard

Thanks for helping. This project exists so Bangla typing on Android is fast, private and
pleasant, and every contribution moves that forward — bug reports, translations, themes and
code alike.

## Before you start

- Read the [Code of Conduct](CODE_OF_CONDUCT.md).
- For anything large (a new layout, a rewrite of the drawing code, a new dependency), open
  an issue first so we can agree on the approach before you spend time.
- Security issues go to [SECURITY.md](SECURITY.md), never the public tracker.

## Ground rules

These keep the project honest and small:

1. **No network.** The app has no `INTERNET` permission and must never gain one. No
   analytics, no ads, no crash reporting service, no third-party network library.
2. **Data over code.** Layouts, themes and phonetic rules are JSON in
   `app/src/main/assets/`. A new language or theme should be a new file, not new Kotlin.
3. **One custom `View`.** The keyboard draws every key on a `Canvas`. Compose is only for
   the settings screens.
4. **Small files.** If a file approaches 400 lines, split it.
5. **Everything verified.** A change is done when it has been built and tested, not when it
   looks right. Please paste the commands you ran in the pull request.

## Set up

You need JDK 17 or newer and Android SDK Platform 35.

```bash
git clone https://github.com/34rns/shobdo-keyboard.git
cd shobdo-keyboard
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

## Build and test

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Data checks need only Python 3.8+ and run in a second:

```bash
python scripts/validate-layouts.py
python scripts/validate-themes.py
python scripts/validate-strings.py
python scripts/banglish_check.py
```

All four must pass before you open a pull request. They are also what CI will run.

## Adding or fixing a theme

1. Copy an existing file in `app/src/main/assets/themes/` and give it a new `id` that
   matches its file name.
2. Keep every contrast ratio at or above 4.5:1. `python scripts/validate-themes.py` prints
   the ratio for each surface and tells you which colour to change.
3. Add a row to the table in [docs/THEME_FORMAT.md](docs/THEME_FORMAT.md).
4. Remember that colours are `#AARRGGBB` — alpha first — not CSS order.

## Adding or fixing a layout

Layouts are documented in [docs/LAYOUT_FORMAT.md](docs/LAYOUT_FORMAT.md). Keys carry a
`label`, a `code`, an optional `type` and optional long-press alternates. A validator
checks structure, per-row width weights and, for Bangla, the exact Probhat mapping by
codepoint — so a typo in a Bangla letter fails with the codepoint it expected.

## Translating

- UI strings live in `app/src/main/res/values/strings.xml` (English) and
  `app/src/main/res/values-bn/strings.xml` (Bangla).
- Every key must exist in both files with the same format placeholders;
  `python scripts/validate-strings.py` enforces it.
- Entries marked `<!-- review -->` are translations that need a native speaker's opinion.
  If you are a native speaker, improving them is one of the most useful contributions you
  can make — please remove the comment when you are confident.
- When you add a string, add it to both locales in the same pull request.

## Banglish rules

The phonetic table is [documented](docs/BANGLISH.md) and tested against a fixture of 84
cases. If you fix a transliteration, add the case to
`app/src/test/resources/banglish/cases.json` first, then make it pass. Do not weaken an
existing expectation to make a new one fit; if a change conflicts with an existing case,
explain in the pull request which behaviour is right and why.

## Style

- Kotlin: 4-space indent, `camelCase` for functions and properties, `PascalCase` for types.
- Prefer clear names over comments; comment the *why*, not the *what*.
- No new dependency without a discussion — the dependency list is deliberately tiny.
- Use `stringResource` / `getString` for all user-visible text. No hard-coded strings.

## Commits and pull requests

- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/):
  `feat:`, `fix:`, `docs:`, `test:`, `chore:`, `refactor:`.
- One logical change per pull request. Small pull requests get reviewed quickly.
- Use the pull request template: say what changed, how you tested it, and paste the command
  output that proves it.
- Screenshots or a short screen recording help a lot for UI changes.

## Reporting bugs

Use the bug report template and include:

- your Android version and device,
- the exact steps,
- what you expected and what happened,
- the language and theme you had active,
- a logcat excerpt if there is a crash (`adb logcat | grep -i shobdo`).

Please do not paste passwords or personal text from your clipboard into an issue.

## Licensing

By contributing you agree that your contribution is licensed under the project's
[MIT license](LICENSE).
