# QA checklist — v0.1.0

P7 asks for a device pass. No device, emulator or build exists yet (no JDK, no Android
SDK, no Gradle project), so this is the written checklist a human runs instead. Every
item is written so a failure is visible: steps, then the expected result, then a box.

Nothing here has been executed. Do not tick a box you did not run.

## Preconditions

```bash
cd shobdo-keyboard
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell ime enable app.shobdo.keyboard/.ShobdoIME
adb shell ime set    app.shobdo.keyboard/.ShobdoIME
adb shell ime list -s        # expect app.shobdo.keyboard/.ShobdoIME
```

Then open Settings > System > Languages & input > On-screen keyboard and confirm
**Shobdo Keyboard** is listed and selected.

Log check while testing (keep this running in a second terminal):

```bash
adb logcat -c && adb logcat | grep -iE "shobdo|AndroidRuntime|FATAL"
```

Expect: no `FATAL EXCEPTION`, no `StrictMode` violation from the keyboard, and **no
typed text** in any log line (a password must never appear in logcat).

---

## 1. Keyboard basics

| # | Steps | Expected | Pass |
|---|-------|----------|------|
| 1.1 | Open any app's text field | Keyboard appears with QWERTY, number row visible, no delay over ~250 ms | [ ] |
| 1.2 | Type `Hello world 123` | Exact text appears, cursor stays after the last character | [ ] |
| 1.3 | Press shift once, type `a`, type `b` | `Ab` — shift released after one character | [ ] |
| 1.4 | Double-tap shift, type `abc`, tap shift | `ABC`, shift off afterwards | [ ] |
| 1.5 | Hold backspace on a long word | Deletes once, pauses ~400 ms, then repeats about every 50 ms | [ ] |
| 1.6 | Tap `123`, then `2/2`, then `ABC` | Symbols page 1, page 2, back to letters | [ ] |
| 1.7 | Long-press `e` | Popup with accents and digits; drag to pick inserts that character | [ ] |

## 2. Rotation

| # | Steps | Expected | Pass |
|---|-------|----------|------|
| 2.1 | In a text field, rotate to landscape | Keyboard redraws at landscape height, no clipped bottom row, no crash | [ ] |
| 2.2 | Type half a word, rotate, finish it | Composing text survives the rotation | [ ] |
| 2.3 | Rotate back to portrait | Height returns to portrait; no leftover landscape spacing | [ ] |

```bash
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1   # landscape; 0 = portrait
```

## 3. Split-screen and multi-window

| # | Steps | Expected | Pass |
|---|-------|----------|------|
| 3.1 | Put an app in split-screen, focus its text field | Keyboard fits the lower half, keys not under the system bar | [ ] |
| 3.2 | Resize the split while typing | Keyboard re-lays out, no stretched or squashed keys | [ ] |
| 3.3 | Use the keyboard in a floating/freeform window | Keys stay inside the window | [ ] |

## 4. Dark and light system mode

| # | Steps | Expected | Pass |
|---|-------|----------|------|
| 4.1 | Switch system to dark | Keyboard uses the dark variant, no white flash | [ ] |
| 4.2 | Switch to light | Keyboard uses the light variant immediately, without restart | [ ] |
| 4.3 | Change theme in settings while a keyboard is open | New colours apply live (P5 requirement) | [ ] |

```bash
adb shell cmd uimode night yes
adb shell cmd uimode night no
```

## 5. Field types

| # | Field | Expected | Pass |
|---|-------|----------|------|
| 5.1 | Password (`inputType=textPassword`) | No suggestions, no Banglish candidates, no popup learning, nothing in logcat; text masked | [ ] |
| 5.2 | Number-only (`number`) | Digits available, letters either hidden or ignored; nothing but digits committed | [ ] |
| 5.3 | Email (`textEmailAddress`) | `@` and `.` reachable (symbol page or long-press), no space inserted by autocomplete | [ ] |
| 5.4 | URL (`textUri`) | `/`, `.`, `:` reachable; no auto-capitalise, no auto-space | [ ] |
| 5.5 | Search field (`imeOptions=actionSearch`) | Enter key shows the Search icon/action and triggers search, not a newline | [ ] |
| 5.6 | Multiline editor | Enter inserts a newline (IME action is `none`) | [ ] |

## 6. Fast typing and long text

| # | Steps | Expected | Pass |
|---|-------|----------|------|
| 6.1 | Two thumbs type alternate letters as fast as possible for 10 s | No dropped keys, no stuck shift, no phantom characters | [ ] |
| 6.2 | Type 5,000+ characters in a notes app | No input lag growth, no ANR, memory stable | [ ] |
| 6.3 | Hold a key while pressing a second key | First key releases correctly, no double commit | [ ] |
| 6.4 | Type with the keyboard over a heavy app (maps/video) | No jank on key press | [ ] |

## 7. Languages

| # | Steps | Expected | Pass |
|---|-------|----------|------|
| 7.1 | All three languages enabled, tap globe repeatedly | Cycles English -> Bangla -> Banglish -> English, skipping nothing | [ ] |
| 7.2 | Disable Bangla, tap globe | Cycles English <-> Banglish only | [ ] |
| 7.3 | Try to disable the last enabled language | Switch refuses and shows the friendly message; at least one stays on | [ ] |
| 7.4 | Long-press globe | Picker lists only enabled languages | [ ] |
| 7.5 | Note the spacebar label | Shows the current language name | [ ] |
| 7.6 | Switch app, come back | Last language is restored | [ ] |

## 8. Banglish composing

| # | Steps | Expected | Pass |
|---|-------|----------|------|
| 8.1 | Type `ami` in Banglish | Suggestion bar shows `আমি` first, then `ami` | [ ] |
| 8.2 | Press space | Commits `আমি` | [ ] |
| 8.3 | Type `ami`, tap the Roman chip | Commits `ami` instead | [ ] |
| 8.4 | Type `ami`, press backspace twice | Deletes from the Roman buffer, not committed Bangla | [ ] |
| 8.5 | Type `ami`, then switch to another app mid-composition, return | Buffer is either committed cleanly or dropped; no half-Bangla/half-Roman text | [ ] |
| 8.6 | Type `bhalo` | `ভালো` (see HANDOFF for the two known Avro deviations) | [ ] |

## 9. Release hardening

```bash
./gradlew :app:assembleRelease
```

| # | Check | Command | Expected | Pass |
|---|-------|---------|----------|------|
| 9.1 | R8 shrinking on | `grep -n "isMinifyEnabled\|isShrinkResources" app/build.gradle.kts` | both `true` for release | [ ] |
| 9.2 | Assets survive shrinking | `unzip -l app/build/outputs/apk/release/app-release*.apk \| grep assets/` | `layouts/*.json`, `themes/*.json`, `banglish/phonetic_rules.json` all listed | [ ] |
| 9.3 | No INTERNET permission | `"$ANDROID_HOME"/build-tools/35.0.0/aapt dump permissions app/build/outputs/apk/release/app-release*.apk` | output has **no** `android.permission.INTERNET` line | [ ] |
| 9.4 | Same, via apkanalyzer | `apkanalyzer manifest permissions app/build/outputs/apk/release/app-release*.apk` | lists only `BIND_INPUT_METHOD` and any non-network permission | [ ] |
| 9.5 | Release APK actually types | install the release APK, repeat 1.2 | typing works, no `ClassNotFoundException`/`NoSuchFieldException` | [ ] |
| 9.6 | No reflection-only classes | `./gradlew :app:assembleRelease` + `grep -rn "Class.forName\|::class.java.getDeclared" app/src/main` | empty, or a matching keep rule exists | [ ] |

## 10. Memory and leaks

| # | Check | Command / steps | Expected | Pass |
|---|-------|-----------------|----------|------|
| 10.1 | No static Context/View | `grep -rn -A6 "companion object" --include=*.kt app/src/main \| grep -E "Context\|View\|Activity"` | empty output | [ ] |
| 10.2 | Keyboard view released | Open/close the keyboard 50x, then `adb shell dumpsys meminfo app.shobdo.keyboard` | PSS stable, no growth trend | [ ] |
| 10.3 | No allocation per frame | `adb shell dumpsys gfxinfo app.shobdo.keyboard` while typing | no `Alloc` growth in sustained typing | [ ] |
| 10.4 | Leak check | Android Studio profiler: open/close keyboard, force GC | no retained `KeyboardView`/`InputMethodService` instances | [ ] |

## 11. Accessibility and localisation

| # | Check | Expected | Pass |
|---|-------|----------|------|
| 11.1 | TalkBack on: focus each settings control | Every control announces a meaningful label from `cd_*` strings | [ ] |
| 11.2 | Measure touch targets in settings | Every tappable control is at least 48dp | [ ] |
| 11.3 | TalkBack on: type on the keyboard | Keys announce their label; space announces the current language | [ ] |
| 11.4 | Set system language to Bangla | All settings text is Bangla; the 15 `<!-- review -->` strings read correctly to a native speaker | [ ] |
| 11.5 | Large text / display size 130% | No clipped labels in settings or on keys | [ ] |

```bash
adb shell settings put system font_scale 1.3
adb shell am broadcast -a android.intent.action.CONFIGURATION_CHANGED
```

## 12. Lint and unit tests

```bash
./gradlew :app:lintDebug :app:testDebugUnitTest
```

| # | Check | Expected | Pass |
|---|-------|----------|------|
| 12.1 | Lint errors | `app/build/reports/lint-results-debug.html` shows 0 errors | [ ] |
| 12.2 | Lint warnings that matter | No `MissingTranslation`, `ContentDescription`, `ClickableViewAccessibility` warnings | [ ] |
| 12.3 | Unit tests | `testDebugUnitTest` green: layout parsing, shift machine, backspace repeat, language toggles, kar placement, 84 Banglish cases, theme contrast | [ ] |

## Sign-off

| Field | Value |
|-------|-------|
| Build tested (APK sha256) | |
| Device / API level | |
| Tested by | |
| Date | |
| Result | pass / fail |
| Blocking issues | |
