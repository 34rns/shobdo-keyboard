# Offline Banglish design research — 2026-11-09

Research is development-time only. No network API, downloader or INTERNET permission
is added to the app. No typing-site data was copied or scraped.

## Phonetic ambiguity

[AvroPad](https://github.com/omicronlab/avro-pad) combines a phonetic parser and
dictionary suggestions. [pyAvroPhonetic](https://github.com/omicronlab/pyAvroPhonetic)
documents the independent rule conversion. These are references, not bundled code.
Avro uses longest patterns with contextual exceptions: independent vowels versus kar
signs, vowel/consonant surroundings, and dictionary spellings that literal rules cannot
recover. Shobdo already has longest matching and kar/conjunct state, described in
[BANGLISH.md](BANGLISH.md). Keep this tested literal conversion stable.

| Ambiguity | Current Shobdo rules | Dictionary strategy |
|---|---|---|
| o / অ / ো | Inherent medial o closes a consonant, trailing o is ো | Reverse spellings retain pronounced inherent vowels; lookup resolves kemon, kormo |
| reph / conjuncts | Adjacent consonants insert ্; medial o interrupts joining | Generate Roman pronunciations from র্ and all written conjuncts |
| ড় / ঢ় | R / Rh; case-sensitive literal parser | Normalize dictionary lookup across r/R, retaining literal fallback |
| ণ / ন | N / n | Both map to n for candidate retrieval; frequency selects orthography |
| শ / ষ / স | sh or S / Sh / s | Shared skeleton s; distinct words retain separate weights |
| ঁ / ৎ | ^ / t followed by backtick | Ignore nasalization in lookup; খণ্ড ত contributes t |
| ং / ঙ / ঞ | ng / Ng / NG | ng and conjunct-specific readings, plus learned disambiguation |
| w / ও | Literal w is not a complete vowel convention | Dictionary lookup normalizes w to o; explicit literal input remains available |
| y / য় / য | y and consonant ya-phala, z for য | Reverse mapping retains written য় and conjunct y; contextual aliases for common endings |
| i/ee, u/oo, bh/v | Skeleton already folds long vowels; v maps to b | Add bh/v alternatives to reverse index, without changing literal phonetics |

## Data audit

Sizes refer to the named version or selection, not a promise about future downloads.
An unknown size is explicitly unknown, and rejected sources are not downloaded.

| Source / URL | License and size evidence | Decision for an MIT application |
|---|---|---|
| [FrequencyWords 2018 Bengali](https://github.com/hermitdave/FrequencyWords/blob/master/content/2018/bn/bn_full.txt) | [Upstream README](https://github.com/hermitdave/FrequencyWords#license) says **content CC BY-SA 4.0**, code MIT. Current filtered asset: 83,433 words, about 2.7 MB | Retain as a separately licensed data component; publish adaptations under CC BY-SA 4.0 with attribution, source, transformations and license. It must never be described as MIT data. No code copied |
| [Bangla Wikipedia dumps](https://dumps.wikimedia.org/bnwiki/latest/) | [Text reuse terms](https://en.wikipedia.org/wiki/Wikipedia:Reusing_Wikipedia_content): CC BY-SA 4.0 (some imported text has additional conditions). Nov 1, 2026 main-title gzip: 3,251,413 bytes; article dump substantially larger | Not bundled this release. Potential future formal-word corpus only after per-source attribution and imported-material audit; not MIT-licensed text |
| [OSCAR 2201](https://huggingface.co/datasets/oscar-corpus/OSCAR-2201/blob/main/README.md) | Packaging/annotations CC0; underlying Common Crawl pages keep their rights. 152 language entries; Bengali payload size not verified in this audit | Reject: packaging license does not authorize redistribution of page content |
| [XL-Sum news](https://huggingface.co/datasets/csebuetnlp/xlsum) | Dataset card: CC BY-NC-SA 4.0, >1 million examples across languages; Bengali split not downloaded | Reject: noncommercial restriction conflicts with unrestricted redistribution |
| [GNU Aspell Bengali dictionary](https://ftp.gnu.org/gnu/aspell/dict/bn/) | GPL dictionary distribution; exact selected-file size not verified | Reject for this release: no GPL-covered dictionary/code integration or license uncertainty |
| [Wikidata names/places](https://www.wikidata.org/wiki/Wikidata:Licensing) | Structured main/lexeme namespace data CC0. Entire multilingual database is enormous; selected Bengali label count depends on query | License-safe candidate for future curated labels; not downloaded/bundled. Exclude descriptive website text and images |
| Project-authored supplements (`tools/data/local_words.tsv`, `chat_phrases.txt`) | Original spellings and short example phrases written for this project, MIT. Actual row/byte counts emitted by builder | Bundle. Seed weights are editorial priorities, **not measured corpus frequencies**. Dialect spelling still needs native review |

No unlicensed news scraping, third-party typing-site dictionaries, GPL code, or unclear
web text is included. CC BY-SA assets are distributed separately from MIT source code;
their derivatives retain ShareAlike. The source builder and published assets provide a
reproducible transformation, and the license/attribution are also packaged in the APK.

## Engines and performance

[OpenBangla upodesh](https://github.com/OpenBangla/upodesh) uses an FST for dictionary
search and compares it with earlier regex search. Its README attributes a trie approach
to libavrophonetic. Those desktop measurements do not establish phone performance.
[Android LatinIME](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/)
contains binary dictionaries, contextual bigram suggestions and forgetting-curve utilities.
They motivate the design; no implementation or database is copied.

Recommended pipeline: exact reverse Roman index, frequency-ranked prefix completions,
context/learning boosts, normalized edit-distance-one fallback, then existing literal
rules and the Roman chip. Sorted packed keys provide trie-equivalent prefix traversal
without a node and map for every character. Preserve strict-case literal fallback for
explicit Avro conjunct notation. Limit fuzzy input length, completion scan and result count.
Avoid regex over 83k words on every keystroke.

Build bounded variants per word rather than their Cartesian product. Store word IDs,
counts and packed Roman bytes in a binary table, loaded once on a worker. Prefix search
uses binary lower-bound and a bounded scan of descendants. Fuzzy search probes one-edit
neighbors by binary lookup; never scans the whole dictionary. A small original phrase
list produces at most four follow-ups per previous word. User choices decay with a
30-day half-life, with bounded global/context counts and an explicit manual dictionary.

Budget estimates before measurement: reverse asset roughly 4–12 MB uncompressed for
100k–400k key/word references, approximately 1–4 MB APK growth; packed runtime arrays
and bytes roughly 8–20 MB, with existing lexicon heap additional until replaced. Query
target <20 ms; worker ranking keeps input/drawing responsive even on cold start.
Keep initial Roman composing immediate, publish only the current session/buffer's result,
and keep top-candidate commit safe if space arrives before ranking completes. Measure
actual desktop/JVM times separately from unverified phone latency.
