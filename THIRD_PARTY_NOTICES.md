# Third-party notices

Shobdo Keyboard bundles the fonts below inside the APK so that Bangla conjuncts and kar
signs are shaped identically on every phone. Each is subset to the characters the keyboard
can print; the original, unmodified licence text for each family is kept next to the font in
`app/src/main/assets/fonts/licenses/`.

## Fonts

| File | Family | Copyright | Licence |
|---|---|---|---|
| `fonts/Inter-Regular.ttf`, `fonts/Inter-Medium.ttf` | Inter | Copyright (c) 2016 The Inter Project Authors | SIL Open Font License 1.1 |
| `fonts/NotoSansBengali-Regular.ttf`, `fonts/NotoSansBengali-Medium.ttf` | Noto Sans Bengali | Copyright 2017 The Noto Project Authors | SIL Open Font License 1.1 |
| `fonts/PixelifySans-Regular.ttf` | Pixelify Sans | Copyright 2021 The Pixelify Sans Project Authors | SIL Open Font License 1.1 |

Sources: the Google Fonts repository, `ofl/inter`, `ofl/notosansbengali` and
`ofl/pixelifysans`.

The SIL Open Font License 1.1 permits bundling and redistribution, including in a
commercial product, provided the font is not sold on its own and the licence travels with
it. The reserved font names are unchanged, and the subset files are not renamed in a way
that implies endorsement.

## Libraries

The app uses the AndroidX and Kotlin standard libraries plus `kotlinx-serialization-json`,
all under the Apache License 2.0. No analytics, advertising, networking or crash-reporting
library is included, and the app declares no `INTERNET` permission.

## Data

The Bangla word list shipped for Banglish suggestions is described, with its source and
licence, in `docs/DATA_SOURCES.md`.
