#!/usr/bin/env python3
"""Validate the shipped packed index without Android or network access."""
import hashlib
import json
import pathlib
import struct

ROOT = pathlib.Path(__file__).resolve().parents[1]

def validate():
    folder = ROOT / 'app/src/main/assets/banglish'
    data = (folder/'reverse.bin').read_bytes()
    meta = json.loads((folder/'index_metadata.json').read_text(encoding='utf-8'))
    assert data[:4] == b'SRI1', 'wrong reverse index signature'
    offset = 4
    def number(fmt):
        nonlocal offset
        value = struct.unpack_from(fmt, data, offset)[0]
        offset += struct.calcsize(fmt)
        return value
    count = number('>I')
    assert count == meta['words'] and count < 200000
    words = []
    for _ in range(count):
        size = number('>H')
        word = data[offset:offset+size].decode('utf-8'); offset += size
        weight = number('>I')
        assert word and weight > 0
        words.append(word)
    rows = number('>I')
    assert rows == meta['roman_rows']
    previous = ''
    for _ in range(rows):
        size = number('>H')
        key = data[offset:offset+size].decode('ascii'); offset += size
        reference = number('>I')
        assert 1 <= size <= 40 and key.isalpha() and key.islower()
        assert previous <= key and reference < count
        previous = key
    assert offset == len(data) == meta['reverse_bytes']
    assert hashlib.sha256((folder/'lexicon.bin').read_bytes()).hexdigest() == meta['lexicon_sha256']
    assert (folder/'licenses/CC-BY-SA-4.0.txt').stat().st_size > 10000
    assert 'ShareAlike' in (folder/'licenses/ATTRIBUTION.txt').read_text(encoding='utf-8')
    fixtures = list((ROOT/'app/src/test/resources').glob('banglish*cases.tsv'))
    cases = sum(sum(bool(line.strip()) and not line.startswith('#') for line in p.read_text(encoding='utf-8').splitlines()) for p in fixtures)
    assert cases >= 300
    print(f'PASS packed reverse index: {count} words, {rows} Roman references, {len(data):,} bytes; {cases} fixed accuracy cases; data license included')
    return 0

if __name__ == '__main__': raise SystemExit(validate())
