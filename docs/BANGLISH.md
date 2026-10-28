# Banglish typing guide

Banglish mode lets you type Bangla with Roman letters. The keyboard keeps what you type as
a buffer, suggests the Bangla word above the keys, and commits it when you press space or
punctuation. Tap the Roman chip to keep the English instead.

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

Avro resolves a few cases with a word dictionary. Shobdo has no dictionary, so these are
the honest differences. They are pinned by tests so they cannot change silently.

| You type | Shobdo | Avro | Why |
|---|---|---|---|
| `ko` | কো | ক | A trailing `o` becomes ো, which `bhalo` → ভালো depends on. Type just `k` for ক. |
| `supriyo` | সুপ্রিয়ো | সুপ্রিয় | Nothing marks the final `য়` as closing the word. |
| `kormo` | করমো | কর্ম | Automatic reph (র্) is not implemented yet. |
| `bhalobasha` | ভালবাশা | ভালোবাসা | `sh` is শ by rule; Avro's dictionary knows the word. Use `bhalobasa` for ভালবাসা. |

If a word comes out wrong, type the conjunct explicitly with `,,` or split the syllable
with an `o`, and please open an issue with the word — a small word list is planned.
