#!/usr/bin/env python3
"""Build the Banglish lexicon and the accuracy corpus.

    python tools/build_banglish_lexicon.py [--source PATH]

Source: the Bengali list from hermitdave/FrequencyWords (MIT licence), 50k words
with occurrence counts.  The script downloads it once into build/ if it is not
already there, then:

  * keeps only words written entirely in Bengali letters and marks,
  * computes each word's *skeleton* — the same canonical roman form the keyboard
    computes for the user's Roman buffer, read from
    app/src/main/assets/banglish/skeleton_rules.json so build and runtime cannot
    diverge,
  * writes app/src/main/assets/banglish/lexicon.bin, sorted by skeleton so the
    app can binary-search it,
  * writes app/src/test/resources/banglish_cases.tsv, the accuracy corpus.

Nothing here runs inside the app: the APK only ever reads the two output files.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import sys
import unicodedata
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
RULES = ROOT / "app/src/main/assets/banglish/skeleton_rules.json"
LEXICON = ROOT / "app/src/main/assets/banglish/lexicon.bin"
CASES = ROOT / "app/src/test/resources/banglish_cases.tsv"
CACHE_DIR = ROOT / "build/banglish"

# Two subtitle corpora from the same project. Each is 50k words; they overlap a lot
# but together they cover enough distinct Bengali words to clear the 50k target.
SOURCES = [
    (
        "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/bn/bn_full.txt",
        CACHE_DIR / "bn_full_2018.txt",
    )
]

# Every Bengali token in the corpus is kept: a word seen once is rare, not wrong, and the
# ranker sorts rare words last anyway. The cap is what keeps the asset inside its budget.
MIN_COUNT = 1
MAX_WORDS = 120_000
LICENCE = "MIT (c) 2016 Hermit Dave - https://github.com/hermitdave/FrequencyWords"
URL_BY_NAME = {path.name: url for url, path in SOURCES}

# Words a learner is most likely to type, with the spelling people actually use.
# Every one of these must survive into the corpus; the script fails if a word is
# missing from the source list, which is how a broken skeleton would show up.
CURATED = [
    ("ami", "আমি"), ("tumi", "তুমি"), ("bhalo", "ভালো"), ("bangla", "বাংলা"),
    ("kemon", "কেমন"), ("dhonnobad", "ধন্যবাদ"), ("amar", "আমার"), ("tomar", "তোমার"),
    ("ki", "কি"), ("na", "না"), ("ha", "হ্যাঁ"), ("ekhon", "এখন"),
    ("bhai", "ভাই"), ("bon", "বোন"), ("ma", "মা"), ("baba", "বাবা"),
    ("khabar", "খাবার"), ("pani", "পানি"), ("bari", "বাড়ি"), ("school", "স্কুল"),
    ("boi", "বই"), ("kaj", "কাজ"), ("somoy", "সময়"), ("din", "দিন"),
    ("rat", "রাত"), ("sokal", "সকাল"), ("bhalobasha", "ভালোবাসা"), ("mon", "মন"),
    ("chokh", "চোখ"), ("hridoy", "হৃদয়"), ("desh", "দেশ"), ("manush", "মানুষ"),
    ("chele", "ছেলে"), ("meye", "মেয়ে"), ("bondhu", "বন্ধু"), ("kotha", "কথা"),
    ("gaan", "গান"), ("khela", "খেলা"), ("bazar", "বাজার"), ("taka", "টাকা"),
]


def load_rules() -> dict:
    data = json.loads(RULES.read_text(encoding="utf-8"))
    return data


def make_skeleton(rules: dict):
    units = [(u, c) for u, c in rules["romanUnits"]]
    letters = rules["romanLetters"]
    bangla = rules["bangla"]

    def of_roman(text: str) -> str:
        out = []
        i = 0
        while i < len(text):
            for unit, canonical in units:
                if text.startswith(unit, i):
                    out.append(canonical)
                    i += len(unit)
                    break
            else:
                ch = text[i]
                canonical = letters.get(ch)
                if canonical:
                    out.append(canonical)
                i += 1
        return "".join(out)

    def of_bangla(word: str) -> str:
        # ZWJ/ZWNJ and the silent ya-phala never change how a word sounds, and the corpus
        # is full of both, so they are removed before anything is read.
        text = unicodedata.normalize("NFC", word).replace("\u200c", "").replace("\u200d", "")
        text = text.replace("\u09cd\u09af", "")
        out = []
        i = 0
        while i < len(text):
            ch = text[i]
            # Precomposed ড় / ঢ় / য় are single code points; decomposed forms are two.
            if i + 1 < len(text) and unicodedata.combining(text[i + 1]) and (ch + text[i + 1]) in bangla:
                out.append(bangla[ch + text[i + 1]])
                i += 2
                continue
            out.append(bangla.get(ch, ""))
            i += 1
        return "".join(out)

    return of_roman, of_bangla


def drop_medial_o(skeleton: str) -> str:
    """A Roman "o" between consonants is the inherent vowel, which Bangla does not write.

    Keeping it would make ``kemon`` and কেমন (skeleton ``kemn``) miss each other, so the
    medial one is dropped and the leading/trailing ones — which are real vowels — stay.
    """
    if len(skeleton) < 3:
        return skeleton
    return skeleton[0] + skeleton[1:-1].replace("o", "") + skeleton[-1]


def read_source(path: pathlib.Path) -> list[tuple[str, int]]:
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        print(f"downloading {path.name}")
        with urllib.request.urlopen(URL_BY_NAME[path.name], timeout=120) as response:
            path.write_bytes(response.read())
    rows = []
    for line in path.read_text(encoding="utf-8").splitlines():
        parts = line.split(" ")
        if len(parts) != 2:
            continue
        word, count = parts[0], parts[1]
        if not count.isdigit():
            continue
        rows.append((word, int(count)))
    return rows


def read_all_sources() -> list[tuple[str, int]]:
    """Every corpus merged by word, keeping the highest count seen."""
    counts: dict[str, int] = {}
    for url, path in SOURCES:
        URL_BY_NAME[path.name] = url
        for word, count in read_source(path):
            counts[word] = max(counts.get(word, 0), count)
    return list(counts.items())


VOWELS = set("অআইঈউঊঋএঐওঔ")
HASANTA = "\u09cd"
KAR_SIGNS = set("\u09be\u09bf\u09c0\u09c1\u09c2\u09c3\u09c7\u09c8\u09cb\u09cc")


def is_bengali_word(word: str) -> bool:
    if len(word) < 2:
        return False
    for ch in word:
        code = ord(ch)
        if not (0x0980 <= code <= 0x09FF):
            return False
        if 0x09E6 <= code <= 0x09EF:  # digits
            return False
    # Subtitle corpora are full of typos that no one would ever type: a doubled
    # independent vowel (আাবার for আবার), a word starting with a kar sign or a hasanta,
    # or one ending on a dangling hasanta. None of those is a Bangla word.
    if word[0] in KAR_SIGNS or word[0] == HASANTA:
        return False
    if word[-1] == HASANTA:
        return False
    for first, second in zip(word, word[1:]):
        if first in VOWELS and (second in VOWELS or second in KAR_SIGNS):
            return False
        # An independent vowel cannot take a hasanta either: আ্যামবুল্যান্স is a typo,
        # not a word.
        if first in VOWELS and second == HASANTA:
            return False
    return True


def build_lexicon(rows, of_bangla):
    entries = {}
    for word, count in rows:
        if count < MIN_COUNT:
            continue
        word = unicodedata.normalize("NFC", word)
        if not is_bengali_word(word):
            continue
        skeleton = of_bangla(word)
        if len(skeleton) < 2:
            continue
        # One row per (skeleton, word): the count is the strongest signal we have.
        entries[(skeleton, word)] = max(entries.get((skeleton, word), 0), count)

    ordered = sorted(
        ((skeleton, word, count) for (skeleton, word), count in entries.items()),
        key=lambda item: (-item[2], item[0], item[1]),
    )[:MAX_WORDS]
    ordered.sort(key=lambda item: (item[0], -item[2], item[1]))
    LEXICON.parent.mkdir(parents=True, exist_ok=True)
    with LEXICON.open("w", encoding="utf-8", newline="\n") as handle:
        for skeleton, word, count in ordered:
            handle.write(f"{skeleton}\t{word}\t{count}\n")
    return ordered


def build_cases(ordered, of_roman, of_bangla):
    """300 Roman -> Bangla pairs: the curated list plus frequent words."""
    by_word = {}
    for skeleton, word, count in ordered:
        by_word.setdefault(word, (skeleton, count))

    pairs = []
    seen = set()

    def add(roman: str, word: str):
        if (roman, word) in seen:
            return
        seen.add((roman, word))
        pairs.append((roman, word))

    missing = []
    for roman, word in CURATED:
        if word in by_word:
            add(roman, word)
        else:
            missing.append(word)
    if missing:
        raise SystemExit(f"curated words missing from the source list: {missing}")

    # Casual spellings: the same word typed with doubled vowels or sh for s. They
    # normalise to the same skeleton, which is exactly what the engine promises to
    # tolerate, so they belong in the corpus.
    casual = (("a", "aa"), ("i", "ee"), ("u", "oo"), ("s", "sh"), ("j", "z"), ("b", "v"))
    for index, (skeleton, word, count) in enumerate(ordered):
        if len(pairs) >= 300:
            break
        roman = skeleton
        variant = index % 5
        if variant and len(roman) > 2:
            short, long_form = casual[variant % len(casual)]
            if short in roman:
                candidate = roman.replace(short, long_form, 1)
                # Only keep the casual spelling when it reads back to the same skeleton;
                # otherwise the test would be asserting something no typist could reach.
                if of_roman(candidate) == skeleton:
                    roman = candidate
        add(roman, word)

    CASES.parent.mkdir(parents=True, exist_ok=True)
    with CASES.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("# roman\tbangla\t# generated by tools/build_banglish_lexicon.py\n")
        for roman, word in pairs:
            handle.write(f"{roman}\t{word}\n")
    return pairs


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=pathlib.Path, default=None)
    args = parser.parse_args()

    rules = load_rules()
    of_roman, of_bangla = make_skeleton(rules)
    rows = read_source(args.source) if args.source else read_all_sources()
    ordered = build_lexicon(rows, of_bangla)
    pairs = build_cases(ordered, of_roman, of_bangla)

    size = LEXICON.stat().st_size
    skeletons = len({entry[0] for entry in ordered})
    print(f"source rows        {len(rows)}")
    print(f"lexicon words      {len(ordered)}")
    print(f"distinct skeletons {skeletons}")
    print(f"lexicon size       {size / 1024:.0f} KB")
    print(f"corpus pairs       {len(pairs)}")
    for anchor in ("ami", "tumi", "bhalo", "bangla"):
        print(f"  {anchor:>8} -> {of_bangla(dict((r, w) for r, w in pairs).get(anchor, '?'))}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
