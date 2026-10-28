#!/usr/bin/env python3
"""Check the README's images: every reference resolves, and no file is blank.

    python scripts/verify_readme_images.py

Reads README.md (and any doc that opts in) for Markdown ![alt](path) and HTML <img src>,
then for each local file reports size and pixel statistics. A file "passes" if it is
large enough, has real colour variation and is not a flat wash of one colour - the two
failure modes a renderer produces when it silently draws nothing.
"""

import pathlib
import re
import sys

from PIL import Image, ImageStat

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOCS = ["README.md"]
MIN_BYTES = 8000
MIN_STDDEV = 6.0      # a flat colour has stddev ~0
MIN_COLOURS = 400     # text and keys always add hundreds of greys

MARKDOWN = re.compile(r"!\[[^\]]*\]\(([^)\s]+)")
HTML = re.compile(r"<img[^>]+src=\"([^\"]+)\"")


def references(path):
    text = (ROOT / path).read_text(encoding="utf-8")
    found = MARKDOWN.findall(text) + HTML.findall(text)
    return [ref for ref in found if not ref.startswith(("http://", "https://", "data:"))]


def inspect(relative):
    path = ROOT / relative
    if not path.exists():
        return False, "MISSING"
    size = path.stat().st_size
    if path.suffix.lower() == ".svg":
        # Vector: existence and a sane size is all this check can say.
        good = size > 200 and "<svg" in path.read_text(encoding="utf-8")[:400]
        return good, f"svg            {size / 1024:7.1f} KiB"
    with Image.open(path) as image:
        image = image.convert("RGB")
        grey = ImageStat.Stat(image.convert("L"))
        stddev = grey.stddev[0]
        colours = len(image.resize((200, 200)).getcolors(maxcolors=200 * 200) or [])
        width, height = image.size
    problems = []
    if size < MIN_BYTES:
        problems.append(f"only {size} bytes")
    if stddev < MIN_STDDEV:
        problems.append(f"flat image (stddev {stddev:.2f})")
    if colours < MIN_COLOURS:
        problems.append(f"only {colours} distinct colours")
    detail = f"{width}x{height}  {size / 1024:7.1f} KiB  stddev {stddev:5.1f}  colours {colours:4d}"
    return not problems, detail + ("" if not problems else "  <-- " + ", ".join(problems))


def main():
    seen, failures = [], []
    for doc in DOCS:
        for ref in references(doc):
            if ref in seen:
                continue
            seen.append(ref)
            ok, detail = inspect(ref)
            print(f"{'ok  ' if ok else 'FAIL'} {ref:46s} {detail}")
            if not ok:
                failures.append(ref)

    print(f"\n{len(seen)} images referenced by README.md, {len(failures)} problem(s)")
    if failures:
        for ref in failures:
            print(f"  - {ref}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
