#!/usr/bin/env python3
"""Shrink the generated PNGs without a visible quality loss.

Chrome writes 24-bit PNGs with no palette; these screens are flat UI over a soft
gradient, so a 256-colour palette is visually indistinguishable at 1x and roughly a
third of the bytes. Measured on 01-english.png: 374.5 KiB -> 109.0 KiB.

Used by render_screenshots.py and compose_screenshots.py, and safe to run again:

    python scripts/png_optimise.py docs/screenshots/*.png
"""

import pathlib
import sys

from PIL import Image


def optimise(path, colours=256):
    """Quantise and re-save in place. Returns (bytes_before, bytes_after)."""
    path = pathlib.Path(path)
    before = path.stat().st_size
    with Image.open(path) as image:
        image = image.convert("RGB")
        if colours:
            image = image.quantize(colors=colours, method=Image.Quantize.MEDIANCUT,
                                   dither=Image.Dither.NONE)
        image.save(path, optimize=True)
    return before, path.stat().st_size


def main(argv):
    if not argv[1:]:
        print("usage: python scripts/png_optimise.py FILE [FILE ...]")
        return 1
    total_before = total_after = 0
    for name in argv[1:]:
        before, after = optimise(name)
        total_before += before
        total_after += after
        saved = 100 * (1 - after / before) if before else 0
        print(f"{name:52s} {before / 1024:7.1f} -> {after / 1024:7.1f} KiB  (-{saved:.0f}%)")
    if total_before:
        print(f"\ntotal {total_before / 1024:.1f} -> {total_after / 1024:.1f} KiB "
              f"(-{100 * (1 - total_after / total_before):.0f}%)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
