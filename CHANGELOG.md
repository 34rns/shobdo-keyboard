# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- No unreleased changes yet.

### Changed

- No unreleased changes yet.

### Fixed

- No unreleased changes yet.

## [0.1.0] - 2026-10-28

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

[Unreleased]: https://github.com/34rns/shobdo-keyboard/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/34rns/shobdo-keyboard/releases/tag/v0.1.0
