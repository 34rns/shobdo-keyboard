# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Banglish v2.** A Bangla frequency word list (83,433 words) now ships inside the app and
  ranks every reading of the Roman buffer, so everyday words come first. The suggestion strip
  expands into a full candidate panel, and the words you pick can be remembered on the device.
- **Thirteen designed themes**, replacing the old gallery: Studio Light, Studio Dark, Onyx,
  Frost, Aurora, Sakura, Tokyo Night, Pixel Arcade, Cassette, Obsidian Gold, Cloudline,
  Evergreen and Dynamic (Material You on Android 12+). The picker shows a live keyboard
  preview of every one.
- Bundled Inter, Noto Sans Bengali and Pixelify Sans, with their OFL licence files.
- Theme schema 2: key depth, key shape, a per-theme font role, a secondary accent and a
  pressed-key scale. Schema 1 files still load.
- Banglish learning controls: remember my choices, and clear the learned words.

### Changed

- The keyboard is rebuilt on one spacing grid, with a taller tool strip, a new icon set,
  press animation, and hit areas that match what is drawn.
- Retired 1.x theme ids are migrated to the closest new theme, so an existing selection
  survives the update instead of jumping to the first entry.

### Fixed

- Key labels, icons and tool-strip controls line up on the same grid at every font scale.

## [0.2.0] - 2026-10-29

### Added

- Twelve more built-in themes, bringing the collection to 24: **Aurora Borealis**, **Cherry
  Blossom**, **Desert Dune**, **Deep Space**, **Mint Fresh**, **Graphite Pro**, **Sky Day**,
  **Lava**, **Lavender Mist**, **Monsoon**, **Golden Hour** and **Ink and Paper**.
- Every theme is now checked against both stops of its own background gradient, so key text
  stays above the 4.5:1 floor at either end of the screen — the worst case across all 24 is
  5.06:1.

### Changed

- The theme validator and the unit tests now name all 24 built-in themes, so deleting one
  fails the build instead of quietly shrinking the gallery.

## [0.1.0] - 2026-10-29

First public release. Bangla, Banglish and English typing on Android, with no network
access at all.

### Added

- English QWERTY keyboard with a number row, shift (single tap and caps lock), backspace
  repeat, two symbol pages, long-press accents, and Enter mapped to the editor's IME action.
- Bangla fixed layout following Probhat positions, with a Bangla digits page and dedicated
  kar, hasanta and conjunct keys.
- Banglish phonetic input: type Roman, get Bangla, with a suggestion bar showing the Bangla
  candidate first and the Roman text second.
- Three languages with individual on/off switches, a globe key that cycles only the enabled
  ones, and a guard so at least one language always stays on.
- Twelve built-in themes covering dark, light and gradient looks, plus Material You on
  Android 12+, every one checked against a 4.5:1 contrast floor.
- Theme customisation: corner radius, key spacing, key height, font size and weight,
  borders, number row, haptic strength, one-handed mode, and a custom background image with
  brightness and blur.
- Custom themes as JSON: duplicate a built-in, edit its colours, export and import.
- Settings app with a three-step onboarding that detects when each step is done, a test
  text field, and a privacy statement.
- Bangla translation of the whole settings UI.
- No `INTERNET` permission, no analytics, no ads, no tracking.

### Known limitations

- No word list, so suggestions are limited to the phonetic candidate.
- Automatic reph (র্) is not implemented: `kormo` gives করমো rather than কর্ম.
- A few dictionary-dependent spellings differ from Avro; see
  [docs/BANGLISH.md](docs/BANGLISH.md).

[Unreleased]: https://github.com/34rns/shobdo-keyboard/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/34rns/shobdo-keyboard/releases/tag/v0.3.0
[0.2.0]: https://github.com/34rns/shobdo-keyboard/releases/tag/v0.2.0
[0.1.0]: https://github.com/34rns/shobdo-keyboard/releases/tag/v0.1.0