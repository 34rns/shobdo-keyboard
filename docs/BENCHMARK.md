# Release regression benchmark

Measured on 2026-11-09 using the v0.3.0 ranker preserved as test-only classes and
the release ranker with its normal packed index (empty legacy lexicon), default
Bangla-first order, no learning and no previous-word context. Comparisons use NFC.
The Roman chip occupies one of the visible top three positions.

| Fixed corpus: 422 cases | Before | After |
|---|---:|---:|
| Top-1 | 313/422 (74.17%) | 384/422 (91.00%) |
| Top-3 | 351/422 (83.18%) | 412/422 (97.63%) |

The original 300 cases and 122 new cases cover everyday chat, conjuncts, reph,
names, dialect forms, loanwords and spelling errors. They are a regression fixture,
not an independent language-quality study: vocabulary overlaps the shipped data,
and some editorial examples also occur in the seed context file. The builder never
reads the fixtures. Inspect the misses printed by `ReleaseAccuracyTest` before
changing a fixture or claiming broader accuracy.

```powershell
python scripts/banglish_benchmark.py
# Read an existing report explicitly (does not execute tests):
python scripts/banglish_benchmark.py --from-report
```

The desktop JVM run measured warm lookup mean 0.018 ms, p95 0.060 ms, maximum
0.096 ms. These are engine lookup timings, not input-to-render latency.
Android emulator warm lookup p95 was 1.179 ms and maximum 1.912 ms; cold asset
load was 747 ms on the instrumentation worker. Asset loading and ranking in the
IME are on a background worker; the Roman composition appears immediately.
First-result latency still depends on the device and cold asset load.

## Android checks

`KeyboardChecks` is debug-only instrumentation using the real View, Canvas and
MotionEvent code. On the local `shobdo` emulator it passed 13 themes × 3 languages
× 3 one-handed modes × 2 measured widths: 2,574 arrow toggles, 234 immediate
ACTION_MOVE language switches, scrolling to/picking the last of 40 candidates,
and zero accidental key insertions. Both widths exercise remeasurement; this is
separate from real display rotation. All ten requested typing anchors passed.

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am instrument -w app.shobdo.keyboard.debug/app.shobdo.keyboard.debug.KeyboardChecks
```

Live screen inspection also verified `ami` → আমি, repeated panel open/close,
Back closing the panel, Space committing `আমি `, and native dictionary add/remove.
The Roman dictionary field temporarily displayed English; the Bangla field
restored Banglish. The expanded sheet stays within the keyboard and scrolls.

No physical-phone measurement, allocation profiling or native-language review
has been performed. Do not equate the warm lookup timing with total frame time.
