# Layout JSON format

Layouts live in `app/src/main/assets/layouts/`. One file per page, named after its id:
`en_qwerty.json`, `en_symbols_1.json`, `en_symbols_2.json`.

The loader (`LayoutLoader.kt`) reads one file into a list of rows of `KeyModel`.
Nothing about a layout is hard-coded in Kotlin: adding a language or a page is a new
JSON file, not new code.

## Top level

| field  | type              | required | meaning                                        |
|--------|-------------------|----------|------------------------------------------------|
| `id`   | string            | yes      | unique page id, e.g. `en_qwerty`               |
| `name` | string            | yes      | display name, e.g. `English`                   |
| `rows` | array of arrays   | yes      | row-major keys; each inner array is one row    |

## Key object

| field         | type     | required | meaning                                                |
|---------------|----------|----------|--------------------------------------------------------|
| `label`       | string   | yes      | the glyph drawn on the key                             |
| `code`        | string   | yes      | text committed to the editor; `""` for action keys      |
| `type`        | string   | no       | special behaviour, see the table below                  |
| `widthWeight` | number   | no       | relative width, default `1.0`                           |
| `long`        | string[] | no       | long-press alternates, in popup order                   |

Mapping to `KeyModel`: `label` -> `label`, `code` -> `code`, `type` -> `type`,
`widthWeight` -> `widthWeight` (default 1.0), `long` -> `longPressChars`.
A key with no `long` array has no popup.

### Width

Each key's pixel width is `widthWeight / sum(row widthWeights) * keyboard width`.
Every row is normalised on its own, so a row does **not** have to add up to the same
total as its neighbours. A row of ten `1.0` keys and a row of nine `1.0` keys both
fill the keyboard edge to edge.

### Special `type` values

| type        | behaviour                                                              |
|-------------|------------------------------------------------------------------------|
| `shift`     | single tap = next character capital, double tap = caps lock             |
| `backspace` | delete one char; hold deletes repeatedly (400 ms delay, then 50 ms)     |
| `enter`     | sends the editor's IME action (Send/Search/Go) if it has one, else `\n` |
| `space`     | inserts a space; double tap inserts `. `                                |
| `symbols`   | advances the symbol cycle: letters -> page 1 -> page 2 -> letters       |
| `globe`     | cycles enabled languages (P3, reserved)                                 |
| `emoji`     | opens the emoji page (reserved, not used yet)                           |
| `comma`     | inserts `,`                                                             |
| `period`    | inserts `.`                                                             |

The `label` is always author-controlled, never derived from `type`. That is what lets
the symbols key read `2/2` on page 1 and `ABC` on page 2 while both are `type: symbols`.

## Shift states

`off` -> `once` -> `locked` -> `off`. `once` returns to `off` after a single character
and is drawn with a filled arrow; `locked` is drawn with an underline bar.

## Example

```json
{
  "id": "demo",
  "name": "Demo",
  "rows": [
    [
      { "label": "a", "code": "a", "long": ["\u00e0", "\u00e1"] },
      { "label": "b", "code": "b" }
    ],
    [
      { "label": "\u21e7", "code": "", "type": "shift", "widthWeight": 1.5 },
      { "label": "space", "code": " ", "type": "space", "widthWeight": 5.0 }
    ]
  ]
}
```

## Validation rules

Enforced by `scripts/validate-layouts.py` (runs anywhere, no Android SDK needed):

- file parses as JSON and the top level is an object
- `id` and `name` are non-empty strings; `id` matches the file name without `.json`
- `rows` is a non-empty array of non-empty arrays
- every key has a non-empty `label` and a `code` string
- `type`, when present, is one of the nine values above
- `widthWeight`, when present, is a number greater than `0`
- `long`, when present, is a non-empty array of non-empty strings
- a row contains no more than one `shift`, one `backspace` and one `enter`
- across the `en_*` pages, every required special type appears:
  `shift`, `backspace`, `enter`, `space`, `symbols`, `comma`, `period`

`globe` and `emoji` are valid but intentionally unused in the English pages; they
belong to P3 (language switching) and the emoji page.

## Rows in the English page

`en_qwerty.json` has five rows:

1. number row (`1`..`0`, hidden later by a setting)
2. `q w e r t y u i o p`
3. `a s d f g h j k l`
4. `shift z x c v b n m backspace`
5. `symbols , space . enter`

Four rows cannot hold a full QWERTY plus shift, backspace, space, enter, comma,
period and the symbols key, so the number row counts as the optional fifth. See
`.ai/DECISIONS.md` (2026-10-28).

## Bangla pages

Bangla uses the same schema, split across three pages that form one cycle:

| file              | contents                                                        |
|-------------------|-----------------------------------------------------------------|
| `bn_provat.json`  | letters: Probhat mapping on the q / a / z rows                  |
| `bn_digits.json`  | Bangla digits `১`..`০` plus punctuation                         |
| `bn_kars.json`    | kar signs, hasanta, and the conjunct helpers `্র ্য ্ব ্ল ্ম র্` |

Cycle: `bn_provat` -> `bn_digits` -> `bn_kars` -> `bn_provat`. The bottom row keeps a
`type: symbols` key whose label names the destination (`কার`, `১২৩`, `অ`).

### Probhat mapping

The unshifted glyph of each letter key follows the Probhat chart at
<https://devsonket.com/provat-keymap/> (read 2026-10-28). The rows mirror the Latin
key rows of that chart:

- q-row: `দ ূ ী র ট এ ু ি ও প`
- a-row: `া স ড ত গ হ জ ক ল`
- z-row (after shift): `য় শ চ আ ব ন ম`

The Probhat uppercase glyph for the same key is the **first** entry of `long`, e.g.
`ক` has `long: ["খ"]`, `া` has `long: ["অ"]`, and the standalone `আ` key has
`long: ["ঋ"]`.

### Shift on the Bangla page

Shift does not uppercase Bangla. On a Bangla page, tapping shift swaps every key to its
`long[0]` alternate (the Probhat uppercase set: independent vowels and aspirated
consonants). That is why the uppercase glyph is always first in `long`. The composer
must also handle the two Unicode spellings of `ো`, `ৌ`, `য়`, `ড়`, `ঢ়`: precomposed
versus decomposed (letter + `়` / `ে` + `া`). The validator compares NFC-normalised text.

`globe` and `emoji` remain reserved; `bn_provat.json` has no `globe` key yet, that
arrives with the language switcher in P3's Kotlin.
