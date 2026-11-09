# Banglish typing guide

Banglish mode lets you type Bangla with Roman letters. The keyboard keeps what you type as
a buffer and ranks every possible reading against a shipped Bangla word list; the best
matches sit above the keys, and space or punctuation commits the first one. Tap the Roman
chip to keep the English instead, or open the full panel to see every candidate.

The shipped packed reverse index contains 82,816 unique Bengali words and 333,039 Roman
references, including 126 original local, name and loanword entries. Lookup first resolves
common spellings such as `bhalo`/`valo` → ভালো, `kemon` → কেমন and `kormo` → কর্ম.
Typing a prefix such as `bhal` already offers ভালো, ভালোবাসা and ভালোই. There is no
network access. The literal phonetic rules below remain unchanged.

Roman composition appears immediately; ranking runs on a worker. The strip displays a
bounded set of candidates and the arrow opens a scrolling panel for more. Tapping its up
arrow or pressing Back closes it. A candidate commit, language change, empty buffer or
keyboard hide also closes the panel. Space waits for the current word's ranking before
committing, preserving rapid typing order.

Small seed bigrams favor follow-ups to the previous committed word. "Learn my choices"
adds bounded local word/context counts with a 30-day recency half-life; it can be disabled
or cleared in Settings → Banglish. User dictionary entries can be added and removed there
and remain available with automatic learning off. Password, URI/email, numeric and fields
requesting no suggestions or no personalized learning suppress suggestions and recording.
See [Privacy](../PRIVACY.md) for exact storage limits and defaults.

The rules follow the Avro Phonetic conventions, so muscle memory from other Bangla
keyboards mostly carries over. The rules live in a data file, not in code, which is why
they can be read, diffed and corrected by anyone:
[`app/src/main/assets/banglish/phonetic_rules.json`](../app/src/main/assets/banglish/phonetic_rules.json).

## Consonants

| Roman | Bangla | | Roman | Bangla | | Roman | Bangla |
|---|---|---|---|---|---|---|---|
| `k` | ক | | `T` | ট | | `p` | প |
| `kh` | খ | | `Th` | ঠ | | `ph` | ফ |
| `g` | গ | | `D` | ড | | `b` | ব |
| `gh` | ঘ | | `Dh` | ঢ | | `bh` | ভ |
| `Ng` | ঙ | | `N` | ণ | | `m` | ম |
| `c` | চ | | `t` | ত | | `z` | য |
| `ch` | ছ | | `th` | থ | | `r` | র |
| `j` | জ | | `d` | দ | | `l` | ল |
| `jh` | ঝ | | `dh` | ধ | | `sh` `S` | শ |
| `NG` | ঞ | | `n` | ন | | `Sh` | ষ |
| | | | | | | `s` | স |
| | | | | | | `h` | হ |
| | | | | | | `y` | য় |
| | | | | | | `R` | ড় |
| | | | | | | `Rh` | ঢ় |

## Vowels and kar signs

After a consonant a vowel becomes a kar sign; on its own it is a full vowel letter.

| Roman | Alone | After a consonant | Example |
|---|---|---|---|
| `a` | আ | া | `ka` → কা |
| `i` | ই | ি | `ki` → কি |
| `I` | ঈ | ী | `kI` → কী |
| `u` | উ | ু | `ku` → কু |
| `U` | ঊ | ূ | `kU` → কূ |
| `rri` | ঋ | ৃ | `krri` → কৃ |
| `e` | এ | ে | `ke` → কে |
| `OI` | ঐ | ৈ | `kOI` → কৈ |
| `O` | ও | ো | `kO` → কো |
| `OU` | ঔ | ৌ | `kOU` → কৌ |
| `o` | অ | see below | `o` → অ |

A plain `o` after a consonant is the inherent vowel: it adds nothing but it *closes* the
consonant, which is how you stop two consonants from joining — `por` → পর, while `pr` → প্র.
At the end of a word it becomes ো, which is why `bhalo` → ভালো.

## Signs and conjuncts

| Roman | Result | Notes |
|---|---|---|
| `ng` | ং | anusvara; `bangla` → বাংলা |
| `:` | ঃ | visarga |
| `^` | ঁ | candrabindu |
| `` t`` `` | ৎ | khanda ta |
| `,,` | ্ | hasanta, joins two consonants manually |
| `y` after a consonant | ্য | `kya` → ক্যা |
| two consonants in a row | joined | `pr` → প্র, `kk` → ক্ক, `kSh` → ক্ষ |
| `0`–`9` | ০–৯ | Bangla digits |

## Known differences from Avro Phonetic

Literal conversion has two tested deviations below. Ranked dictionary suggestions can
correct a literal reading; these are different layers of the engine. For example `kormo`
has literal করমো but the first ranked suggestion is কর্ম, and dictionary spellings handle
`bhalobasha` → ভালোবাসা. Automatic reph is dictionary based; an unknown name may still
need an explicit conjunct or a user dictionary entry.

| You type | Shobdo | Avro | Why |
|---|---|---|---|
| `ko` | কো | ক | A trailing `o` becomes ো, which `bhalo` → ভালো depends on. Type just `k` for ক. |
| `supriyo` | সুপ্রিয়ো | সুপ্রিয় | Nothing marks the final `য়` as closing the word. |

If a word comes out wrong, type the conjunct explicitly with `,,` or split the syllable
with an `o`, and please open an issue with the word. A word that is missing from the
ranking is the most useful report we can get.
