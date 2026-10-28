#!/usr/bin/env python3
"""Validate the settings app string resources.

    python scripts/validate-strings.py

Checks that values/strings.xml (English) and values-bn/strings.xml (Bangla) agree:
same keys, same format placeholders, no empty or duplicate entries, valid Android
escaping (an unescaped apostrophe fails the build), and a content description for
every interactive control.

Exit code 0 = clean, 1 = at least one problem.
"""

import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
BASE = ROOT / "app/src/main/res/values/strings.xml"
BN = ROOT / "app/src/main/res/values-bn/strings.xml"

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="backslashreplace")

PLACEHOLDER = re.compile(r"%(?:\d+\$)?[a-zA-Z]")

# Controls that must expose a content description (accessibility requirement).
REQUIRED_CD = [
    "cd_navigate_back",
    "cd_language_switch",
    "cd_theme_card",
    "cd_create_theme",
    "cd_open_github",
    "cd_step_done",
    "cd_step_pending",
]

# Strings that are legitimately identical across locales (proper nouns, symbols).
SAME_VALUE_OK = {"language_bangla"}


def label(path):
    """Both files are named strings.xml, so always say which folder it came from."""
    return f"{path.parent.name}/strings.xml"


def load(path, errors):
    """Return {name: text} plus the review-marked names, or None on parse failure."""
    try:
        raw = path.read_text(encoding="utf-8")
    except FileNotFoundError:
        errors.append(f"{label(path)}: file not found at {path}")
        return None, []

    try:
        tree = ET.fromstring(raw)
    except ET.ParseError as exc:
        errors.append(f"{path}: invalid XML: {exc}")
        return None, []

    strings = {}
    for element in tree.findall("string"):
        name = element.get("name")
        if not name:
            errors.append(f"{label(path)}: a <string> has no name attribute")
            continue
        if name in strings:
            errors.append(f"{label(path)}: duplicate string name {name!r}")
            continue
        strings[name] = element.text or ""

    # Review markers apply to the next <string> that follows them.
    reviews = []
    pending = False
    for line in raw.splitlines():
        stripped = line.strip()
        if stripped.startswith("<!--") and "review" in stripped:
            pending = True
            continue
        match = re.search(r'<string name="([^"]+)"', stripped)
        if match and pending:
            reviews.append(match.group(1))
            pending = False

    return strings, reviews


def check_values(path, strings, errors):
    for name, value in strings.items():
        if not value.strip():
            errors.append(f"{label(path)}: '{name}' is empty")
        if value != value.strip():
            errors.append(f"{label(path)}: '{name}' has leading or trailing whitespace")
        if re.search(r"(?<!\\)'", value):
            errors.append(
                f"{label(path)}: '{name}' has an unescaped apostrophe "
                "(use \\' or aapt will fail)"
            )


def main():
    errors = []

    base, base_reviews = load(BASE, errors)
    bn, bn_reviews = load(BN, errors)
    if base is None or bn is None:
        print("FAIL could not read string resources")
        for error in errors:
            print(f"  - {error}")
        return 1

    check_values(BASE, base, errors)
    check_values(BN, bn, errors)

    missing = sorted(set(base) - set(bn))
    extra = sorted(set(bn) - set(base))
    for name in missing:
        errors.append(f"values-bn: missing translation for '{name}'")
    for name in extra:
        errors.append(f"values-bn: '{name}' does not exist in values/strings.xml")

    for name in sorted(set(base) & set(bn)):
        expected = sorted(PLACEHOLDER.findall(base[name]))
        actual = sorted(PLACEHOLDER.findall(bn[name]))
        if expected != actual:
            errors.append(
                f"'{name}': placeholders differ, English has {expected}, Bangla has {actual}"
            )

    for name in REQUIRED_CD:
        if name not in base:
            errors.append(f"accessibility: missing content description string '{name}'")
        if name in base and name in bn and not bn[name].strip():
            errors.append(f"accessibility: '{name}' has an empty Bangla description")

    untranslated = [
        name
        for name in sorted(set(base) & set(bn))
        if base[name] == bn[name] and name not in SAME_VALUE_OK
    ]
    for name in untranslated:
        errors.append(f"values-bn: '{name}' is identical to English (untranslated?)")

    print(f"{'file':<26}{'strings':>9}{'review':>8}")
    print(f"{'values/strings.xml':<26}{len(base):>9}{len(base_reviews):>8}")
    print(f"{'values-bn/strings.xml':<26}{len(bn):>9}{len(bn_reviews):>8}")

    if bn_reviews:
        print("\nMarked for review by a Bangla speaker:")
        for name in bn_reviews:
            print(f"  - {name}")

    if errors:
        print("\nProblems:")
        for error in errors:
            print(f"  - {error}")
        return 1

    print(
        f"\nPASS {len(base)} strings, English and Bangla keys match, "
        f"placeholders match, {len(bn_reviews)} marked for review"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
