# Shobdo Keyboard 0.4.0 release readiness

Completed locally on 2026-11-09 in `D:\FreeCodeChats\shobdo-keyboard`.
Version 0.4.0, Android version code 4. Phases 0–6 were committed separately.
No push, tag, GitHub release or publication was performed.

## 1. Bug causes and fixes

**Space language feedback:** the original touch handler had no swipe detector and the
label transition lasted 900 ms. Drawing also prepared cache keys, surfaces and labels.
`SpaceGesture` now switches once when ACTION_MOVE crosses 36dp, before release;
release after a swipe never inserts a space. Language state changes immediately.
The 130 ms DecelerateInterpolator transition cancels on repeated switches, requests
invalidation of the space rectangle only, and cancels callbacks when detached. Rendering
resources and labels are prepared before drawing. Redundant preference refreshes on
language changes were removed. No allocation profiler was run; zero total allocations
or a particular frame-rate improvement is not claimed.

**Candidate close arrow:** expanded ACTION_DOWN routing intercepted the strip before
the close arrow, and the overlay hit boundary covered less than its drawn background.
`CandidatePanelState` is now the state owner; `StripModel` carries its snapshot.
`StripPainter.arrowRect()` supplies the same 48dp rectangle for drawing and hit testing.
The strip receives touch priority; the panel owns whole gestures and extra pointers.
Commit, language switch, empty buffer and keyboard hide reset it; Back closes it.
An additional overflow found during live inspection was fixed by clipping the panel
to available height and scrolling the same rectangles used for drawing and selection.
A scroll cannot select a candidate accidentally.

## 2. Accuracy and implementation

| Same fixed 422 cases, NFC comparison | v0.3.0 ranker | Release ranker |
|---|---:|---:|
| Top-1 | 313/422 — 74.17% | 384/422 — 91.00% |
| Visible top-3, including Roman chip | 351/422 — 83.18% | 412/422 — 97.63% |

The benchmark uses default Bangla-first order, no learned/context boosts, and the actual
release configuration: packed index loaded, legacy lexicon empty. The original ranker is
preserved only in test sources. The 300 existing and 122 original new cases include chat,
conjuncts, reph, colloquial forms, names, loanwords and spelling errors. This is a regression
fixture, not independent generalization evidence; vocabulary and some seed examples overlap
the shipped data. The builder never reads fixtures. There are still 38 top-1 and 10 top-3 misses.

Implemented behind `CandidateEngine`: packed reverse lookup first, sorted binary prefix
lookup instead of an object-heavy trie, original supplements, 51 pruned seed bigrams,
bounded learning with 30-day decay, editable dictionary and normalized edit-distance-one
fallback when there is no exact reverse hit. Literal phonetic rules remain unchanged.
`kormo` → কর্ম is dictionary-based automatic reph, not a universal new phonetic rule.
Ranking/loading are worker tasks; Roman composition is immediate, stale results are
discarded and Space waits for the current ranking. See [BENCHMARK.md](BENCHMARK.md).

## 3. Bundled data and licenses

| Component | Provenance and license |
|---|---|
| Legacy frequency lexicon and derived reverse index | [Hermit Dave FrequencyWords Bengali 2018](https://github.com/hermitdave/FrequencyWords/blob/master/content/2018/bn/bn_full.txt), content **CC BY-SA 4.0**; upstream code is MIT |
| Original 126-entry local/name/loanword source | Project-authored `tools/data/local_words.tsv`, MIT; incorporated index is distributed under CC BY-SA 4.0 |
| Original phrases and 51 seed bigrams | Project-authored MIT data; editorial weights, not measured corpus frequencies |
| Inter, Noto Sans Bengali, Pixelify Sans | Existing bundled fonts, SIL OFL 1.1 |
| Project code | MIT; no Avro/OpenBangla/LatinIME code copied |

Corrected the old inaccurate MIT claim for FrequencyWords content. Attribution and full
CC BY-SA text are packaged in `assets/banglish/licenses/`; font licenses remain packaged.
No typing-site dictionary, unclear web corpus or noncommercial-only dataset was added.
Research, rejected sources and source metadata are in [RESEARCH_BANGLISH.md](RESEARCH_BANGLISH.md),
[DATA_SOURCES.md](DATA_SOURCES.md) and [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).

## 4. Verification results

Final command:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

**BUILD SUCCESSFUL**. 98 JVM tests in 16 suites, zero failures. Lint: zero errors,
16 warnings and one informational item (style/resource suggestions and a debug-only
literal). The compiler also reports Android's deprecated rectangle-invalidate API.
Tests cover reverse exact/prefix lookup, fuzzy fallback, bigrams, decay, expired-count
updates, dictionary validation/persistence/caps, buffer safety, stale results, sensitive
field flags, immediate switches and panel state resets. The new learning edge-case tests
were observed failing before their fixes, then passed afterward.

All required Python validators passed:

- `validate-layouts.py`: 6 layouts.
- `validate-themes.py`: 13 themes, worst-stop contrast ≥4.5:1.
- `validate-strings.py`: 113 matching English/Bangla keys and placeholders; 52 flagged for native review.
- `banglish_check.py`: 84 literal cases, both documented deviations preserved; packed
  index structure/order/references, source hash, license and 422-case fixture checks pass.

Android `shobdo` emulator: 13 themes × 3 languages × 3 one-handed modes × 2 widths,
2,574 arrow toggles, 234 switches checked immediately after ACTION_MOVE, 40-candidate
scrolling and last-candidate selection; zero accidental characters. All ten requested
words passed engine assertions and subsequent live Canvas typing/visual inspection:
ami, tumi, bhalo, valo, kemon, kormo, bangla, desh, tomar, achho.

Native settings dictionary add/remove, ASCII Roman field and Banglish restoration passed.
Live repeated arrow toggles, Back close, Space commit, hide/reopen and actual display
rotation passed. Password, URI and numeric fields showed no suggestions; their typing
left the learning-file hash unchanged. Numeric digits were accepted and letters rejected.
The signed release installed successfully and passed swipe/panel/Space smoke checks.
The emulator crash-log buffer contained no crash entries after these checks. This is
smoke coverage, not proof that every app/editor combination is crash-free.

## 5. Offline and release integrity

`aapt` verified package `app.shobdo.keyboard`, version 0.4.0/code 4, minSdk 26,
targetSdk 35. Source and built manifest have **no INTERNET permission**. Requested
permissions are VIBRATE and AndroidX's app-specific signature-protected dynamic-receiver
permission; the IME service requires BIND_INPUT_METHOD. Backup is disabled. Dependency
declarations and resolved release classpath contain AndroidX/Kotlin libraries, with no
networking, analytics, advertising or tracking libraries added.

`apksigner verify` passes with v2 signing and the same certificate SHA-256 as the
existing signed 0.3.0 APK:
`6919f068e93b0c126c4856cc12b21b71c87b2ce00bb4406b85bd7834239abd1f`.
Release manifest/dex inspection confirms no `DemoActivity`, `KeyboardChecks` or
instrumentation entry. All main/debug/test Kotlin files are ≤400 lines. Repository
guard and whitespace checks pass; no private checkpoints, instruction files, keystores,
local configuration or secrets are committed.

Privacy documentation now accurately describes local counts/context and manual dictionary
storage. Learning retains its existing enabled-by-default setting and can be disabled or
cleared; the explicit dictionary is separate. No transcripts or surrounding editor text
are saved. See [PRIVACY.md](../PRIVACY.md).

## 6. Size and performance

| Artifact | Before | After | Growth |
|---|---:|---:|---:|
| Signed release APK | 1,996,481 bytes | 3,988,785 bytes | +1,992,304 bytes (99.79%) |
| Debug APK | 10,697,809 bytes | 13,290,690 bytes | +2,592,881 bytes (24.24%) |

The signed APK remains about 3.99 MB (3.80 MiB). The 6,952,901-byte reverse index
compresses to 1,965,647 bytes in the APK, accounting for almost all release growth.
It contains 82,816 unique words and 333,039 Roman references; the old 2,722,307-byte
lexicon remains as a fallback asset and is normally not parsed into memory.

Final desktop JVM warm lookup: mean 0.025 ms, p95 0.083 ms, max 0.190 ms.
Emulator repeated warm lookup: p95 1.179–2.157 ms, max 1.912–3.684 ms.
Cold asset load: 747–1,444 ms on the instrumentation worker. Warm lookup met the
~20 ms target in these runs; end-to-end keystroke/frame latency was not profiled.
Signed-release process PSS after loading/typing was 47,074 KiB (about 46 MiB),
Java heap PSS 17,068 KiB. This includes the whole process, not just the dictionary.

Local signed deliverable: `dist/Shobdo-Keyboard-v0.4.0.apk`.
SHA-256: `c98674eea81490ac5358181fdfe906012d2562bfadaf558b16d221c44cd658cb`.
This checksum records the prepared local snapshot. Publishing can rebuild Git provenance
metadata; use the checksum attached to the GitHub release for the published APK.
The APK and checksum are local ignored deliverables, not committed binaries.

## 7. Limitations and recommended next checks

- Run on a midrange physical phone: profile cold first-result latency, total frame time,
  heap and allocation rate. Background loading avoids UI stalls but first suggestions
  can wait for the cold index load. No measured zero-allocation claim is made.
- Obtain native Bangla review of Probhat alternates, the 52 flagged strings, new dictionary
  wording and colloquial/name spellings. Current fixtures are not a native quality audit.
- Broaden independent accuracy evaluation and seed contexts. The 51 editorial bigrams
  are a small starting table; broad corpus coverage and universal automatic reph remain limited.
- Prefix search scans a bounded 4,096-reference window; very broad prefixes can omit a
  high-frequency completion outside that window. Fuzzy search is bounded and only one edit.
- Test more editor apps, Android versions and accessibility services on hardware.
  Emulator remeasurement and rotation were verified; a broad device matrix was not.
- Local release is prepared; publication, remote CI and store acceptance are outside this
  run. No source was pushed and no public release was created.

## Changed-file inventory

Paths below are relative to the repository; this includes additions and modifications
since baseline `a87d7c4`, grouped by purpose.

**Release/docs:** `CHANGELOG.md`, `PRIVACY.md`, `README.md`, `THIRD_PARTY_NOTICES.md`,
`version.properties`, `docs/ARCHITECTURE_NOTES.md`, `docs/BANGLISH.md`,
`docs/BENCHMARK.md`, `docs/DATA_SOURCES.md`, `docs/RESEARCH_BANGLISH.md`,
`docs/RELEASE_READINESS.md`.

**Banglish runtime**, under `app/src/main/java/app/shobdo/keyboard/banglish/`:
`BigramTable.kt`, `CandidateEngine.kt`, `LearnedWords.kt`, `ReverseRomanIndex.kt`.

**IME**, under `app/src/main/java/app/shobdo/keyboard/ime/`:
`BanglishSession.kt`, `CandidatePanelState.kt`, `FallbackBanglish.kt`, `IconSet.kt`,
`InputHandler.kt`, `KeyBackgroundCache.kt`, `KeyPainter.kt`, `KeyboardGeometry.kt`,
`KeyboardMetrics.kt`, `KeyboardPreferences.kt`, `KeyboardTouch.kt`, `KeyboardView.kt`,
`ShobdoIME.kt`, `SpaceGesture.kt`, `StripPainter.kt`.

**Settings/resources:** `app/src/main/java/app/shobdo/keyboard/settings/ui/BanglishScreen.kt`,
`ShobdoApp.kt`, `UserDictionaryScreen.kt` in that same folder;
`app/src/main/res/values/strings.xml`, `app/src/main/res/values-bn/strings.xml`.

**Data**, under `app/src/main/assets/banglish/`: `bigrams.tsv`, `index_metadata.json`,
`reverse.bin`, `licenses/ATTRIBUTION.txt`, `licenses/CC-BY-SA-4.0.txt`.

**Tests**, under `app/src/test/java/app/shobdo/keyboard/`:
`banglish/BaselineBanglishLexicon.kt`, `banglish/BaselineCandidateEngine.kt`,
`banglish/LearningAndContextTest.kt`, `banglish/ReleaseAccuracyTest.kt`,
`banglish/ReverseIndexTest.kt`, `ime/CandidatePanelStateTest.kt`,
`ime/SessionSafetyTest.kt`, `ime/SpaceSwitchTest.kt`;
`app/src/test/resources/banglish_release_cases.tsv`.

**Debug only:** `app/src/debug/AndroidManifest.xml`,
`app/src/debug/java/app/shobdo/keyboard/debug/DemoActivity.kt`,
`app/src/debug/java/app/shobdo/keyboard/debug/KeyboardChecks.kt`.

**Builders/validators:** `scripts/banglish_assets_check.py`, `scripts/banglish_benchmark.py`,
`scripts/banglish_check.py`, `tools/build_banglish_lexicon.py`, `tools/build_reverse_index.py`,
`tools/data/chat_phrases.txt`, `tools/data/local_words.tsv`.
