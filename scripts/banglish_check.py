#!/usr/bin/env python3
"""Reference implementation + checker for the Banglish phonetic rules.

This is NOT the app. It is a small, dependency-free mirror of the algorithm that
BanglishEngine.kt must implement, so the rule data can be proven correct on a
machine without a JDK or Android SDK:

    python scripts/banglish_check.py

It loads app/src/main/assets/banglish/phonetic_rules.json and checks every case in
app/src/test/resources/banglish/cases.json. Exit code 0 means all cases pass.

The Kotlin port must behave exactly like transliterate() below; the JVM unit test
reads the same cases.json, so both implementations are held to one fixture.
"""

import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
RULES_PATH = ROOT / "app/src/main/assets/banglish/phonetic_rules.json"
CASES_PATH = ROOT / "app/src/test/resources/banglish/cases.json"

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="backslashreplace")

# Tie-break when two rules match at the same position with the same length.
# Higher wins. Signs and digits beat consonants, j-phala beats the য় consonant.
PRIORITY = {"signs": 4, "digits": 3, "jPhala": 2, "consonants": 1, "vowel": 1}

# Vowels with no kar entry because behaviour.oAfterConsonant decides their output.
KAR_EXCEPTIONS = {"o"}


class Rules:
    def __init__(self, data):
        rules = data["rules"]
        self.consonants = rules["consonants"]
        self.vowel_standalone = rules["vowelStandalone"]
        self.vowel_kar = rules["vowelKar"]
        self.j_phala = rules["jPhala"]
        self.signs = rules["signs"]
        self.digits = rules["digits"]
        behaviour = data["behavior"]
        self.hasanta = behaviour["hasanta"]
        self.trailing_o = behaviour["oAfterConsonant"]["trailing"]
        self.medial_o = behaviour["oAfterConsonant"]["medial"]


def best_match(text, index, kind, table, priority):
    """Longest rule at index; ties broken by priority.

    Returns (length, priority, kind, roman, bangla) or None.
    """
    best = None
    for rule in table:
        roman = rule["roman"]
        if text.startswith(roman, index):
            candidate = (len(roman), priority, kind, roman, rule["bangla"])
            if best is None or candidate[:2] > best[:2]:
                best = candidate
    return best


def transliterate(text, rules):
    """Convert a Roman buffer to Bangla. Pure function, no state kept between calls."""
    out = []
    index = 0
    after_consonant = False  # a bare consonant is pending, so it can take a kar or ্

    while index < len(text):
        char = text[index]

        # "o" after a consonant is the inherent vowel (and breaks a conjunct), except
        # at the end of the buffer where it becomes the ো kar. Avro resolves this with
        # a dictionary; we have none, so the rule lives in the behaviour block.
        if char == "o" and after_consonant:
            out.append(rules.trailing_o if index == len(text) - 1 else rules.medial_o)
            after_consonant = False
            index += 1
            continue

        tables = [
            ("signs", rules.signs, PRIORITY["signs"]),
            ("digits", rules.digits, PRIORITY["digits"]),
            ("consonants", rules.consonants, PRIORITY["consonants"]),
        ]
        if after_consonant:
            tables.append(("vowelKar", rules.vowel_kar, PRIORITY["vowel"]))
            tables.append(("jPhala", rules.j_phala, PRIORITY["jPhala"]))
        else:
            tables.append(("vowelStandalone", rules.vowel_standalone, PRIORITY["vowel"]))

        best = None
        for kind, table, priority in tables:
            candidate = best_match(text, index, kind, table, priority)
            if candidate is not None and (best is None or candidate[:2] > best[:2]):
                best = candidate

        if best is None:
            out.append(char)  # punctuation and anything unknown passes through
            after_consonant = False
            index += 1
            continue

        _, _, kind, roman, bangla = best

        if kind == "consonants":
            if after_consonant:
                out.append(rules.hasanta)
            out.append(bangla)
            after_consonant = True
        elif kind == "jPhala":
            out.append(bangla)
            after_consonant = True  # ্য ends in য, so a following vowel is a kar
        else:
            out.append(bangla)
            after_consonant = False

        index += len(roman)

    return "".join(out)


def validate_rule_data(data):
    errors = []
    rules = data["rules"]
    for name, table in rules.items():
        seen = set()
        for rule in table:
            roman = rule.get("roman")
            bangla = rule.get("bangla")
            if not isinstance(roman, str) or not roman:
                errors.append(f"{name}: a rule has an empty or missing 'roman'")
            elif roman in seen:
                errors.append(f"{name}: duplicate roman {roman!r}")
            else:
                seen.add(roman)
            if not isinstance(bangla, str) or not bangla:
                errors.append(f"{name}: rule {roman!r} has an empty or missing 'bangla'")
    # Every vowel must have both spellings: a standalone form and a kar form. The one
    # documented exception is "o", whose after-consonant behaviour is in the behaviour
    # block because it depends on position in the word.
    kar_romans = {rule["roman"] for rule in rules["vowelKar"]}
    standalone_romans = {rule["roman"] for rule in rules["vowelStandalone"]}
    for roman in sorted(standalone_romans - kar_romans - KAR_EXCEPTIONS):
        errors.append(f"vowels: {roman!r} has a standalone form but no kar form")
    for roman in sorted(kar_romans - standalone_romans):
        errors.append(f"vowels: {roman!r} has a kar form but no standalone form")
    for roman in sorted(KAR_EXCEPTIONS - standalone_romans):
        errors.append(f"vowels: {roman!r} is exempted but has no standalone form")
    return errors


def main():
    rules_data = json.loads(RULES_PATH.read_text(encoding="utf-8"))
    cases_data = json.loads(CASES_PATH.read_text(encoding="utf-8"))

    errors = validate_rule_data(rules_data)
    if errors:
        print("Rule data problems:")
        for error in errors:
            print(f"  - {error}")
        return 1

    rules = Rules(rules_data)
    cases = cases_data["cases"]

    failures = []
    groups = {}
    for case in cases:
        roman = case["roman"]
        expected = case["bangla"]
        actual = transliterate(roman, rules)
        group = case.get("group", "other")
        groups.setdefault(group, [0, 0])
        if actual == expected:
            groups[group][0] += 1
        else:
            groups[group][1] += 1
            failures.append((roman, expected, actual, group))

    for group in sorted(groups):
        passed, failed = groups[group]
        print(f"{'ok  ' if failed == 0 else 'FAIL'} {group:<12} {passed} passed, {failed} failed")

    if failures:
        print("\nMismatches:")
        for roman, expected, actual, group in failures:
            print(f"  - {roman!r} ({group}): expected {expected!r}, engine gave {actual!r}")
        return 1

    deviations = [c for c in cases if c.get("tag") == "avro-deviation"]
    print(
        f"\nPASS {len(cases)} cases across {len(groups)} groups, "
        f"{len(RULES_PATH.read_text(encoding='utf-8'))} bytes of rules\n"
        f"     engine matches the reference for every fixture; "
        f"{len(deviations)} documented Avro deviation(s) asserted explicitly"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
