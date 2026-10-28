#!/usr/bin/env python3
"""Validate keyboard theme JSON files and their contrast ratios.

    python scripts/validate-themes.py

Checks every theme in app/src/main/assets/themes/ against docs/THEME_FORMAT.md and
enforces the P5 quality bar: text on a key must reach at least 4.5:1 contrast
(WCAG AA) against the key's *composited* colour, i.e. the key fill blended over the
background, because theme fills are translucent (#AARRGGBB).

Exit code 0 = every theme valid and accessible, 1 = at least one problem.
No Android SDK needed; this mirrors what ThemeManager.kt must also guarantee.
"""

import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
THEME_DIR = ROOT / "app/src/main/assets/themes"

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="backslashreplace")

MIN_CONTRAST = 4.5

# The 12 themes P5 requires, by file name.
REQUIRED_THEMES = [
    "midnight_glass",
    "pure_light",
    "amoled_black",
    "ocean_breeze",
    "sunset_gradient",
    "forest_calm",
    "rose_gold",
    "neon_cyber",
    "material_you",
    "paper_minimal",
    "royal_purple",
    "bangla_flag",
]

KEY_FIELDS = ("fill", "pressed", "text")
BACKGROUND_TYPES = {"solid", "gradient", "image"}


def parse_color(value):
    """Return (r, g, b, a) from #RRGGBB or #AARRGGBB (alpha first, Android style)."""
    if not isinstance(value, str):
        raise ValueError(f"colour must be a string, got {type(value).__name__}")
    text = value.strip()
    if not text.startswith("#") or len(text) not in (7, 9):
        raise ValueError(f"{value!r} must be #RRGGBB or #AARRGGBB")
    digits = text[1:]
    try:
        parts = [int(digits[i : i + 2], 16) for i in range(0, len(digits), 2)]
    except ValueError:
        raise ValueError(f"{value!r} is not hexadecimal")
    if len(parts) == 4:
        # #AARRGGBB: alpha is the FIRST pair, matching docs/THEME_FORMAT.md and the
        # order Android's Color.parseColor expects. (This used to return the pairs in
        # file order, which read the transparent key fills as opaque yellow.)
        return (parts[1], parts[2], parts[3], parts[0])
    return (parts[0], parts[1], parts[2], 255)


def composite(fg, bg):
    """Alpha-blend fg over bg (both 0-255) in sRGB, the way Canvas draws them."""
    alpha = fg[3] / 255.0
    return tuple(round(fg[i] * alpha + bg[i] * (1 - alpha)) for i in range(3))


def relative_luminance(rgb):
    channels = []
    for value in rgb:
        channel = value / 255.0
        channels.append(channel / 12.92 if channel <= 0.03928 else ((channel + 0.055) / 1.055) ** 2.4)
    red, green, blue = channels
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue


def contrast_ratio(fg_rgb, bg_rgb):
    light = max(relative_luminance(fg_rgb), relative_luminance(bg_rgb))
    dark = min(relative_luminance(fg_rgb), relative_luminance(bg_rgb))
    return (light + 0.05) / (dark + 0.05)


def background_stops(background, where, errors):
    """Return the list of solid colours a background can show, for worst-case contrast."""
    kind = background.get("type")
    if kind not in BACKGROUND_TYPES:
        errors.append(f"{where}: background.type must be one of {sorted(BACKGROUND_TYPES)}")
        return []
    try:
        if kind == "solid":
            color = parse_color(background.get("color"))
            return [color[:3]]
        if kind == "gradient":
            colors = background.get("colors")
            if not isinstance(colors, list) or len(colors) < 2:
                errors.append(f"{where}: gradient needs a 'colors' array of at least 2")
                return []
            if not isinstance(background.get("angle"), (int, float)):
                errors.append(f"{where}: gradient needs a numeric 'angle'")
            return [parse_color(c)[:3] for c in colors]
        # image: brightness and blur change the visible colour, so a fallback is required
        if not isinstance(background.get("image"), str):
            errors.append(f"{where}: image background needs an 'image' path")
        for field in ("brightness", "blur"):
            value = background.get(field)
            if value is not None and not isinstance(value, (int, float)):
                errors.append(f"{where}: image '{field}' must be a number")
        fallback = background.get("fallback")
        if fallback is None:
            errors.append(f"{where}: image background needs 'fallback' so contrast can be checked")
            return []
        return [parse_color(fallback)[:3]]
    except ValueError as exc:
        errors.append(f"{where}: {exc}")
        return []


def require_color(value, label, errors):
    """Colour fields must always be well formed, even when contrast cannot be computed."""
    if not isinstance(value, str):
        errors.append(f"{label} must be a colour string")
        return
    try:
        parse_color(value)
    except ValueError as exc:
        errors.append(f"{label}: {exc}")


def check_contrast(label, text_color, fill_color, stops, errors):
    """Worst-case contrast of text over a translucent fill laid on each background stop."""
    try:
        text_rgb = parse_color(text_color)[:3]
        fill = parse_color(fill_color)
    except ValueError as exc:
        errors.append(f"{label}: {exc}")
        return None
    worst = None
    worst_ratio = None
    for stop in stops:
        composited = composite(fill, stop)
        ratio = contrast_ratio(text_rgb, composited)
        if worst_ratio is None or ratio < worst_ratio:
            worst_ratio = ratio
            worst = composited
    if worst_ratio < MIN_CONTRAST:
        hexed = "#{:02X}{:02X}{:02X}".format(*worst)
        errors.append(
            f"{label}: contrast {worst_ratio:.2f}:1 on {hexed} is below {MIN_CONTRAST}:1"
        )
    return worst_ratio


def check_theme(path):
    errors = []
    ratios = {}

    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        return [f"{path.name}: invalid JSON: {exc}"], ratios

    if not isinstance(data, dict):
        return [f"{path.name}: top level must be an object"], ratios

    theme_id = data.get("id")
    if not isinstance(theme_id, str) or not theme_id.strip():
        errors.append(f"{path.name}: 'id' must be a non-empty string")
    elif theme_id != path.stem:
        errors.append(f"{path.name}: 'id' is {theme_id!r}, expected {path.stem!r}")

    if not isinstance(data.get("name"), str) or not data["name"].strip():
        errors.append(f"{path.name}: 'name' must be a non-empty string")

    if not isinstance(data.get("dark"), bool):
        errors.append(f"{path.name}: 'dark' must be true or false")

    if "dynamic" in data and not isinstance(data["dynamic"], bool):
        errors.append(f"{path.name}: 'dynamic' must be true or false when present")

    background = data.get("background")
    if not isinstance(background, dict):
        errors.append(f"{path.name}: 'background' must be an object")
        stops = []
    else:
        stops = background_stops(background, path.name, errors)

    key = data.get("key")
    if not isinstance(key, dict):
        errors.append(f"{path.name}: 'key' must be an object")
        key = {}
    for field in KEY_FIELDS:
        require_color(key.get(field), f"{path.name}: key.{field}", errors)
    if not isinstance(key.get("radius"), (int, float)) or key.get("radius", -1) < 0:
        errors.append(f"{path.name}: key.radius must be a number >= 0")
    if not isinstance(key.get("shadow"), bool):
        errors.append(f"{path.name}: key.shadow must be true or false")
    require_color(key.get("stroke"), f"{path.name}: key.stroke", errors)

    special = data.get("specialKey")
    if not isinstance(special, dict):
        errors.append(f"{path.name}: 'specialKey' must be an object")
        special = {}
    for field in ("fill", "text"):
        require_color(special.get(field), f"{path.name}: specialKey.{field}", errors)

    require_color(data.get("accent"), f"{path.name}: accent", errors)

    bar = data.get("suggestionBar")
    if not isinstance(bar, dict):
        errors.append(f"{path.name}: 'suggestionBar' must be an object")
        bar = {}
    for field in ("background", "text"):
        require_color(bar.get(field), f"{path.name}: suggestionBar.{field}", errors)

    font = data.get("font")
    if not isinstance(font, dict):
        errors.append(f"{path.name}: 'font' must be an object")
        font = {}
    scale = font.get("scale")
    if not isinstance(scale, (int, float)) or scale <= 0:
        errors.append(f"{path.name}: font.scale must be a number greater than 0")
    if not isinstance(font.get("bold"), bool):
        errors.append(f"{path.name}: font.bold must be true or false")

    spacing = data.get("keySpacing")
    if not isinstance(spacing, (int, float)) or spacing < 0:
        errors.append(f"{path.name}: keySpacing must be a number >= 0")

    if stops:
        if key.get("text") and key.get("fill"):
            ratios["key"] = check_contrast(
                f"{path.name} key.text on key.fill", key["text"], key["fill"], stops, errors
            )
        if key.get("text") and key.get("pressed"):
            ratios["pressed"] = check_contrast(
                f"{path.name} key.text on key.pressed", key["text"], key["pressed"], stops, errors
            )
        if special.get("text") and special.get("fill"):
            ratios["special"] = check_contrast(
                f"{path.name} specialKey.text on specialKey.fill",
                special["text"],
                special["fill"],
                stops,
                errors,
            )
        if bar.get("text") and bar.get("background"):
            ratios["suggestionBar"] = check_contrast(
                f"{path.name} suggestionBar.text",
                bar["text"],
                bar["background"],
                stops,
                errors,
            )

    return errors, ratios


def main():
    files = sorted(THEME_DIR.glob("*.json"))
    if not files:
        print(f"FAIL no theme files found in {THEME_DIR}")
        return 1

    all_errors = []
    found = {path.stem for path in files}
    missing = [name for name in REQUIRED_THEMES if name not in found]
    if missing:
        all_errors.append(f"missing required themes: {missing}")

    print(f"{'theme':<18}{'key':>7}{'pressed':>9}{'special':>9}{'bar':>7}")
    for path in files:
        errors, ratios = check_theme(path)
        all_errors.extend(errors)
        cells = ""
        for name, width in (("key", 7), ("pressed", 9), ("special", 9), ("suggestionBar", 7)):
            value = ratios.get(name)
            # A ratio can be None when a colour failed to parse; the error is already
            # collected, so show a dash instead of crashing on the formatting.
            cells += f"{value:>{width}.2f}" if value is not None else f"{'-':>{width}}"
        status = "FAIL" if errors else "ok  "
        print(f"{status} {path.stem:<12}{cells}")

    if all_errors:
        print("\nProblems:")
        for error in all_errors:
            print(f"  - {error}")
        return 1

    print(
        f"\nPASS {len(files)} themes valid, all contrast ratios >= {MIN_CONTRAST}:1 "
        "(WCAG AA, worst background stop)"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
