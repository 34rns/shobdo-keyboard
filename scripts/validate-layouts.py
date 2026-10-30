#!/usr/bin/env python3
"""Validate the keyboard layout JSON files.

Runs on any machine with Python 3.8+, no Android SDK needed:

    python scripts/validate-layouts.py

Exit code 0 means every layout is well formed, 1 means at least one problem.
The rules enforced here are the ones documented in docs/LAYOUT_FORMAT.md.
"""

import json
import pathlib
import sys
import unicodedata

ALLOWED_TYPES = {
    "shift",
    "backspace",
    "enter",
    "space",
    "symbols",
    "globe",
    "emoji",
    "comma",
    "period",
}

REQUIRED_TYPES = {"shift", "backspace", "enter", "space", "symbols", "comma", "period"}

ONE_PER_ROW = {"shift", "backspace", "enter"}

LAYOUT_DIR = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/layouts"

# Error messages contain Bangla glyphs. Windows consoles default to cp1252 and would
# raise UnicodeEncodeError while printing a failure report, hiding the real problem.
for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="backslashreplace")

# --- Bangla / Probhat -------------------------------------------------------
#
# Source for the letter mapping: the Probhat key chart at
# https://devsonket.com/provat-keymap/ (read 2026-10-28). Each entry is the
# unshifted Probhat glyph for that physical key position, in row order.
# Row 2 (z x c v b n m) starts after the shift key, hence the offset.
PROBHAT_LETTERS = {
    0: ["দ", "ূ", "ী", "র", "ট", "এ", "ু", "ি", "ও", "প"],
    1: ["া", "স", "ড", "ত", "গ", "হ", "জ", "ক", "ল"],
    2: ["য়", "শ", "চ", "আ", "ব", "ন", "ম"],
}
PROBHAT_ROW2_OFFSET = 1

# Kar signs, hasanta and conjunct helpers that must exist somewhere in bn_*.
REQUIRED_BANGLA = set("ািীুূৃেৈোৌ্ংঃঁ্র্য")

BANGLA_DIGITS = "০১২৩৪৫৬৭৮৯"


def nfc(text):
    return unicodedata.normalize("NFC", text)


def is_bangla_char(text):
    """True when every code point of text is in the Bengali block."""
    return bool(text) and all(0x0980 <= ord(ch) <= 0x09FF for ch in nfc(text))


def check_bangla(datasets):
    """Cross-page Bangla checks. datasets maps file stem -> parsed JSON."""
    errors = []

    provat = datasets.get("bn_provat")
    if provat is None:
        errors.append("bn_provat.json is missing")
    else:
        rows = provat.get("rows") or []
        for row_index, expected in PROBHAT_LETTERS.items():
            if row_index >= len(rows):
                errors.append(f"bn_provat.json: missing row {row_index}")
                continue
            offset = PROBHAT_ROW2_OFFSET if row_index == 2 else 0
            row = rows[row_index]
            for column, want in enumerate(expected):
                index = column + offset
                if index >= len(row):
                    errors.append(
                        f"bn_provat.json row {row_index}: missing key for {want!r} "
                        f"(index {index})"
                    )
                    continue
                key = row[index]
                code = key.get("code", "")
                if not is_bangla_char(code):
                    errors.append(
                        f"bn_provat.json row {row_index} key {index}: "
                        f"code {code!r} is not a Bengali character"
                    )
                elif nfc(code) != nfc(want):
                    errors.append(
                        f"bn_provat.json row {row_index} key {index}: "
                        f"code is {code!r} (U+{ord(nfc(code)[0]):04X}), "
                        f"Probhat expects {want!r} (U+{ord(nfc(want)[0]):04X})"
                    )
                for alternate in key.get("long") or []:
                    if not is_bangla_char(alternate):
                        errors.append(
                            f"bn_provat.json row {row_index} key {index}: "
                            f"long-press {alternate!r} is not a Bengali character"
                        )
        for row_index, row in enumerate(rows):
            for key in row:
                code = key.get("code", "")
                if code and code.isascii() and code.isalpha():
                    errors.append(
                        f"bn_provat.json row {row_index}: Latin letter {code!r} "
                        "on the Bangla letters page"
                    )

    digits = datasets.get("bn_digits")
    if digits is None:
        errors.append("bn_digits.json is missing (Bangla digits page)")
    else:
        codes = {
            nfc(key.get("code", ""))
            for row in digits.get("rows") or []
            for key in row
        }
        missing_digits = [d for d in BANGLA_DIGITS if nfc(d) not in codes]
        if missing_digits:
            errors.append(f"bn_digits.json: missing Bangla digits {missing_digits}")

    seen = set()
    for name, data in datasets.items():
        if not name.startswith("bn_"):
            continue
        for row in data.get("rows") or []:
            for key in row:
                code = key.get("code", "")
                if code:
                    seen.update(nfc(code))
    missing = {ch for ch in REQUIRED_BANGLA if nfc(ch) not in seen}
    if missing:
        errors.append(
            "Bangla pages are missing required kar/conjunct keys: "
            + ", ".join(sorted(f"{ch} (U+{ord(nfc(ch)):04X})" for ch in missing))
        )

    return errors


# --- generic layout checks --------------------------------------------------


def check_layout(path):
    """Return (errors, types_seen, parsed_data) for one layout file."""
    errors = []
    seen = set()

    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        return [f"{path.name}: invalid JSON: {exc}"], seen, None

    if not isinstance(data, dict):
        return [f"{path.name}: top level must be an object"], seen, None

    layout_id = data.get("id")
    if not isinstance(layout_id, str) or not layout_id.strip():
        errors.append(f"{path.name}: 'id' must be a non-empty string")
    elif layout_id != path.stem:
        errors.append(f"{path.name}: 'id' is {layout_id!r}, expected {path.stem!r}")

    name = data.get("name")
    if not isinstance(name, str) or not name.strip():
        errors.append(f"{path.name}: 'name' must be a non-empty string")

    rows = data.get("rows")
    if not isinstance(rows, list) or not rows:
        errors.append(f"{path.name}: 'rows' must be a non-empty array")
        return errors, seen, data

    for row_index, row in enumerate(rows):
        where = f"{path.name} row {row_index}"
        if not isinstance(row, list) or not row:
            errors.append(f"{where}: must be a non-empty array of keys")
            continue

        row_types = []
        for key_index, key in enumerate(row):
            at = f"{where} key {key_index}"
            if not isinstance(key, dict):
                errors.append(f"{at}: must be an object")
                continue

            label = key.get("label")
            # The language key draws its own mark on the canvas, so it may carry no label.
            draws_itself = key.get("type") == "globe"
            if not isinstance(label, str) or (not label.strip() and not draws_itself):
                errors.append(f"{at}: 'label' must be a non-empty string")

            if not isinstance(key.get("code"), str):
                errors.append(f"{at}: 'code' must be a string (use \"\" for action keys)")

            key_type = key.get("type")
            if key_type is not None:
                if key_type not in ALLOWED_TYPES:
                    errors.append(f"{at}: unknown type {key_type!r}")
                else:
                    row_types.append(key_type)
                    seen.add(key_type)

            weight = key.get("widthWeight")
            if weight is not None and (not isinstance(weight, (int, float)) or weight <= 0):
                errors.append(f"{at}: 'widthWeight' must be a number greater than 0")

            long_press = key.get("long")
            if long_press is not None:
                if not isinstance(long_press, list) or not long_press:
                    errors.append(f"{at}: 'long' must be a non-empty array")
                else:
                    for alt in long_press:
                        if not isinstance(alt, str) or not alt:
                            errors.append(f"{at}: 'long' entries must be non-empty strings")

        for single in ONE_PER_ROW:
            count = row_types.count(single)
            if count > 1:
                errors.append(f"{where}: {count} keys of type {single!r}, expected at most 1")

    return errors, seen, data


def main():
    files = sorted(LAYOUT_DIR.glob("*.json"))
    if not files:
        print(f"FAIL no layout files found in {LAYOUT_DIR}")
        return 1

    all_errors = []
    all_types = set()
    datasets = {}

    for path in files:
        errors, seen, data = check_layout(path)
        all_errors.extend(errors)
        all_types |= seen
        if data is not None:
            datasets[path.stem] = data
        rows = []
        if not errors and data is not None:
            rows = data["rows"]
        status = "ok  " if not errors else "FAIL"
        detail = f"{len(rows)} rows, {sum(len(r) for r in rows)} keys" if rows else "-"
        print(f"{status} {path.name:<22} {detail}")

    missing = REQUIRED_TYPES - all_types
    if missing:
        all_errors.append(f"missing required special types across all layouts: {sorted(missing)}")

    all_errors.extend(check_bangla(datasets))

    if all_errors:
        print("\nProblems:")
        for error in all_errors:
            print(f"  - {error}")
        return 1

    print(
        f"\nPASS {len(files)} layouts valid\n"
        f"     special types: {sorted(all_types)}\n"
        f"     Bangla: Probhat mapping, 10 digits, {len(REQUIRED_BANGLA)} required "
        "kar/conjunct glyphs"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
