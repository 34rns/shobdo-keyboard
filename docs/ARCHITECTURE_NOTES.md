# Release preparation architecture (2026-11-09)

Baseline: clean `main` at a87d7c4 (0.3.0). Read README, CHANGELOG, CONTRIBUTING,
THIRD_PARTY_NOTICES, BANGLISH, THEME_FORMAT and the private project checkpoints.

| Responsibility | Existing files | Planned change |
|---|---|---|
| Canvas and touch | `ime/KeyboardView.kt`, `KeyPainter.kt`, `KeyBackgroundCache.kt`, `KeyboardGeometry.kt`, `KeyboardMetrics.kt` | Immediate space swipe, 130 ms easing, prepared drawing resources |
| Candidates | `ime/StripPainter.kt`, `ShobdoIME.kt` | Arrow routing, complete panel touch consumption and resets |
| Rules / ranking | `banglish/BanglishEngine.kt`, `SkeletonRules.kt`, `CandidateEngine.kt`, `BanglishLexicon.kt` | Preserve rules; add compact reverse Roman index, ranked prefix search and context |
| Session / privacy | `ime/InputHandler.kt`, `ShobdoIME.kt` | Async suggestions, bounded buffers, sensitive-field guards |
| User choices | `banglish/LearnedWords.kt`, `settings/ui/BanglishScreen.kt` | Decay, contextual counts, asynchronous persistence, editable dictionary |
| Word assets | `assets/banglish/lexicon.bin`, `phonetic_rules.json`, `skeleton_rules.json` | Existing lexicon is UTF-8 skeleton/word/count TSV, despite its extension |
| Builder | `tools/build_banglish_lexicon.py` | Separate deterministic reverse-index builder and original supplemental data |
| Tests | `app/src/test/java/app/shobdo/keyboard/{ime,banglish}`, `app/src/test/resources/banglish_cases.tsv`, `banglish/cases.json` | UI state, lookup, learning, privacy and before/after accuracy |

Java paths above are relative to `app/src/main/java/app/shobdo/keyboard/`;
asset paths are relative to `app/src/main/`.

Baseline checks: `gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
BUILD SUCCESSFUL; 80 JVM tests, zero failures; lint zero errors (13 existing warnings).
All four Python validators pass: 6 layouts, 13 themes, 103 matched strings (52 awaiting
native review), 84 rule cases. Existing accuracy: 267/300 top-1 (89.0%), 300/300
top-5. A new benchmark will report top-3 on the same fixed fixture for both engines.
Debug APK: 10,697,809 bytes. Released 0.3.0 APK: 1,996,481 bytes.
ADB is installed, but `adb devices` returned no attached devices.

Confirmed defects: space label duration is 900 ms and touch code has no space swipe;
expanded ACTION_DOWN handling precedes strip handling and uses the grid height rather
than the full panel rectangle. KeyPainter also constructs gradient/cache keys and key
surfaces inside drawing. Fixes will preserve the current appearance and public suggestion API.

Design: retain sorted-array prefix lookup instead of an object-heavy trie; a sorted
compact binary reverse table gives equivalent prefix traversal with less heap. Perform
asset parsing, ranking and learned-file I/O on a single worker; reject stale results.
Retain separately licensed CC BY-SA word data with correct notices (upstream's code is
MIT, its content is not). Add only independently authored local-word/context supplements.
No new app dependencies, networking, analytics or permissions.
