#!/usr/bin/env python3
"""Deterministic offline reverse index; never reads accuracy fixtures or downloads text."""
from __future__ import annotations
import collections
import hashlib
import json
import pathlib
import re
import struct
import unicodedata
from build_banglish_lexicon import make_skeleton, load_rules

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets/banglish'
DATA = ROOT / 'tools/data'
KARS = dict(zip('ািীুূৃেৈোৌ', ['a','i','i','u','u','ri','e','oi','o','ou']))
VOWELS = dict(zip('অআইঈউঊঋএঐওঔ', ['o','a','i','i','u','u','ri','e','oi','o','ou']))
CONS = dict(zip('কখগঘঙচছজঝঞটঠডঢণতথদধনপফবভমযরলশষসহ',
                    ['k','kh','g','gh','ng','ch','chh','j','jh','ng','t','th','d','dh','n',
                     't','th','d','dh','n','p','f','b','bh','m','j','r','l','sh','sh','s','h']))

def pronounced(word):
    text = unicodedata.normalize('NFC', word)
    out = []
    i = 0
    while i < len(text):
        ch = text[i]
        nxt = text[i+1:i+2]
        if ch in CONS:
            token = CONS[ch]
            if nxt == '়':
                token = {'ড':'r','ঢ':'rh','য':'y'}.get(ch, token)
                i += 1
                nxt = text[i+1:i+2]
            out.append(token)
            if nxt and (nxt in CONS or nxt in VOWELS):
                out.append('o')
        elif ch in KARS: out.append(KARS[ch])
        elif ch in VOWELS: out.append(VOWELS[ch])
        elif ch == 'ং': out.append('ng')
        elif ch == 'ৎ': out.append('t')
        i += 1
    return ''.join(out)

def variants(key):
    # Bounded one-token alternatives; no exponential Cartesian product.
    result = {key}
    if key and key[-1] not in 'aeiou': result.add(key + 'o')
    if len(key) > 2:
        result.add(key[0] + key[1:-1].replace('o', '') + key[-1])
    result.add(re.sub(r'(.)\1+', r'\1', key))
    for a,b in [('bh','b'),('bh','v'),('i','ee'),('u','oo'),('sh','s'),
                ('ch','c'),('kh','k'),('o','u')]:
        if a in key: result.add(key.replace(a,b,1))
    return sorted(result)

def build():
    normalize, of_bangla = make_skeleton(load_rules())
    words = {}
    keys = {}
    lexicon = ASSETS / 'lexicon.bin'
    for line in lexicon.read_text(encoding='utf-8').splitlines():
        if not line or line.startswith('#'): continue
        key, word, count = line.split('\t')
        words[word] = int(count)
        keys[word] = {key, pronounced(word)}
    originals = []
    for line in (DATA/'local_words.tsv').read_text(encoding='utf-8').splitlines():
        if not line or line.startswith('#'): continue
        word, aliases, weight = line.split('\t')
        originals.append(word)
        words[word] = max(words.get(word,0), int(weight))
        keys.setdefault(word,set()).update([pronounced(word), of_bangla(word), *aliases.split(',')])
    ordered_words = sorted(words)
    ids = {word:i for i,word in enumerate(ordered_words)}
    rows = set()
    for word, spellings in keys.items():
        for spelling in spellings:
            for variant in variants(spelling):
                key = normalize(variant)
                if 1 <= len(key) <= 40: rows.add((key,ids[word]))
    ordered_rows = sorted(rows, key=lambda row: (row[0], -words[ordered_words[row[1]]], row[1]))
    out = bytearray(b'SRI1')
    out += struct.pack('>I',len(ordered_words))
    for word in ordered_words:
        encoded = word.encode('utf-8')
        out += struct.pack('>H',len(encoded)) + encoded + struct.pack('>I',words[word])
    out += struct.pack('>I',len(ordered_rows))
    for key, index in ordered_rows:
        encoded = key.encode('ascii')
        out += struct.pack('>H',len(encoded)) + encoded + struct.pack('>I',index)
    (ASSETS/'reverse.bin').write_bytes(out)
    counts = collections.Counter()
    for line in (DATA/'chat_phrases.txt').read_text(encoding='utf-8').splitlines():
        if not line or line.startswith('#'): continue
        tokens = line.split()
        counts.update(zip(tokens,tokens[1:]))
    contexts = collections.defaultdict(list)
    for (previous,word), count in counts.items(): contexts[previous].append((word,count))
    lines = []
    for previous in sorted(contexts):
        for word,count in sorted(contexts[previous], key=lambda r:(-r[1],r[0]))[:4]:
            lines.append(f'{previous}\t{word}\t{count}')
    (ASSETS/'bigrams.tsv').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    metadata = dict(words=len(words), roman_rows=len(rows), reverse_bytes=len(out),
        original_words=len(originals), bigrams=len(lines), lexicon_sha256=hashlib.sha256(lexicon.read_bytes()).hexdigest(),
        license='CC BY-SA 4.0 for derived index; original supplements MIT')
    (ASSETS/'index_metadata.json').write_text(json.dumps(metadata,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(metadata))
    assert pronounced('কর্ম') == 'korm'
    assert normalize('bhalo') in {k for k,i in rows if ordered_words[i]=='ভালো'}
    return metadata

if __name__ == '__main__': build()
