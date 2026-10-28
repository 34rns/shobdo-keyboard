#!/usr/bin/env python3
"""Render the Shobdo Keyboard screenshots with a headless browser.

    python scripts/render_screenshots.py            # all screens
    python scripts/render_screenshots.py 01-english # one or more by name

Why a browser: no Android device and no JVM are available here, so METHOD A (adb) and
METHOD B (Paparazzi/Roborazzi) cannot run. A browser gives us two things a hand-drawn
mock cannot: real Bengali text shaping (Chrome uses HarfBuzz, so conjuncts and kar
signs join correctly) and CSS rounded-rect drawing that matches the app's Canvas maths.

These are renders, not device captures. The README and HANDOFF say so.

Layouts, themes, key gaps, radii and the Banglish candidate are read from the files the
app ships, so the screens follow the data instead of a designer's memory.
"""

import json
import os
import pathlib
import shutil
import subprocess
import sys

import png_optimise
import screenshot_html as ui
import screenshot_pages as pages

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "docs/screenshots"
WORK = ROOT / "build/screenshots"

sys.path.insert(0, str(ROOT / "scripts"))
from banglish_check import Rules, transliterate  # noqa: E402

RULES = Rules(json.loads(
    (ROOT / "app/src/main/assets/banglish/phonetic_rules.json").read_text(encoding="utf-8")))


def find_chrome():
    """Locate a Chromium binary: env override, then the usual install locations."""
    candidates = [os.environ.get("CHROME")]
    local = os.environ.get("LOCALAPPDATA", "")
    pf = os.environ.get("PROGRAMFILES", "C:/Program Files")
    pf86 = os.environ.get("PROGRAMFILES(X86)", "C:/Program Files (x86)")
    candidates += [
        f"{pf}/Google/Chrome/Application/chrome.exe",
        f"{pf86}/Google/Chrome/Application/chrome.exe",
        f"{pf86}/Microsoft/Edge/Application/msedge.exe",
        f"{pf}/Microsoft/Edge/Application/msedge.exe",
    ]
    if local:
        base = pathlib.Path(local) / "ms-playwright"
        for pattern in ("chromium-*/chrome-win64/chrome.exe",
                        "chromium_headless_shell-*/chrome-headless-shell-win64/chrome-headless-shell.exe"):
            candidates += [str(p) for p in sorted(base.glob(pattern), reverse=True)]
    for binary in ("chromium", "google-chrome", "chrome", "chromium-browser"):
        found = shutil.which(binary)
        if found:
            candidates.append(found)
    for candidate in candidates:
        if candidate and pathlib.Path(candidate).exists():
            return candidate
    return None


CHROME = find_chrome()
SCALE = 2  # 540x960 CSS px -> 1080x1920 PNG


def shoot(name, html):
    """Write the HTML, screenshot it at 540x960 CSS px, return the PNG path."""
    WORK.mkdir(parents=True, exist_ok=True)
    html_path = WORK / f"{name}.html"
    png_path = OUT / f"{name}.png"
    html_path.write_text(html, encoding="utf-8")

    for headless in ("--headless=new", "--headless"):
        cmd = [
            CHROME, headless,
            "--disable-gpu", "--hide-scrollbars", "--no-first-run",
            "--no-default-browser-check", "--disable-extensions",
            f"--user-data-dir={WORK / 'profile'}",
            f"--force-device-scale-factor={SCALE}",
            f"--window-size={ui.DEVICE_W},{ui.DEVICE_H}",
            "--virtual-time-budget=2000",
            f"--screenshot={png_path}",
            html_path.resolve().as_uri(),
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
        if png_path.exists() and png_path.stat().st_size > 2000:
            return png_path, result
        if "headless=new" in headless:
            continue
        raise SystemExit(f"chrome failed for {name}\n{result.stdout}\n{result.stderr}")
    raise SystemExit(f"chrome produced no screenshot for {name}")


def chat_opts(theme_id, layout_id, language, title, subtitle, initial, messages, field,
              candidates=None, shift=None, one_handed=None, key_height=52):
    return {
        "theme": ui.load_theme(theme_id),
        "layout": ui.load_layout(layout_id),
        "language_label": language,
        "title": title,
        "subtitle": subtitle,
        "initial": initial,
        "messages": messages,
        "field": field,
        "candidates": candidates,
        "shift": shift,
        "one_handed": one_handed,
        "key_height": key_height,
    }


EN_CHAT = [
    "· Today, 9:41 AM",
    "Hey! Are you still coming tonight?",
    ">Yes - leaving work now.",
    "Perfect, see you at eight.",
    "Should I book the corner table?",
    ">Please do. I'll be there on time.",
]
EN_NAME = ("Ayesha Rahman", "online", "A")
BN_CHAT = [
    "· আজ, ৯:৪১",
    "তুমি কি আজ আসছ?",
    ">হ্যাঁ, সন্ধ্যা ৮টায় আসব।",
    "দারুণ! অপেক্ষা করছি।",
    "কোন দিকে যাবে বলো?",
    ">শ্যামলী, নতুন জায়গাটায়।",
]
BN_NAME = ("রুমানা আক্তার", "সক্রিয়", "রু")
BL_CHAT = [
    "· Today, 9:41 AM",
    "Bhai, kal er meeting ta ki final?",
    ">Haan, 10 baje.",
    "Thik ache, dekha hobe.",
    "Ami ektu late hote pari.",
    ">No problem, ami table dhore rakhi.",
]
BL_NAME = ("Rafiq Hasan", "online", "R")

THEME_SHOWCASE = [
    ("07-theme-midnight-glass", "midnight_glass"),
    ("08-theme-pure-light", "pure_light"),
    ("09-theme-amoled-black", "amoled_black"),
    ("10-theme-ocean-breeze", "ocean_breeze"),
    ("11-theme-sunset-gradient", "sunset_gradient"),
    ("12-theme-bangla-flag", "bangla_flag"),
]

SETTINGS_ROWS = [
    ("Languages", [
        {"kind": "switch", "label": "English", "on": True},
        {"kind": "switch", "label": "বাংলা", "on": True},
        {"kind": "switch", "label": "Banglish", "on": True},
    ]),
    ("Theme", [{"kind": "value", "label": "Midnight Glass", "value": "Change >"}]),
    ("Typing", [
        {"kind": "switch", "label": "Haptic feedback", "on": True},
        {"kind": "switch", "label": "Key sound", "on": False},
        {"kind": "switch", "label": "Number row", "on": True},
        {"kind": "switch", "label": "Auto-capitalise", "on": True},
    ]),
    ("One-handed", [{"kind": "value", "label": "Off", "value": "Right >"}]),
    ("Try your keyboard", [{"kind": "field", "label": "Type here to test Shobdo"}]),
    ("About", [
        {"kind": "value", "label": "Privacy", "value": "No internet"},
        {"kind": "value", "label": "Version", "value": "0.1.0"},
    ]),
]


def shot(opts):
    return ui.keyboard_screen(opts["theme"], opts["layout"], opts)


def build_screens():
    shots = {}

    shots["01-english"] = shot(
        chat_opts("midnight_glass", "en_qwerty", "English", *EN_NAME, EN_CHAT, "Hello world 123"))

    shots["02-bangla"] = shot(
        chat_opts("bangla_flag", "bn_provat", "বাংলা", *BN_NAME, BN_CHAT, "আমি বাংলায় লিখি"))

    roman = "ami tumi bhalo"
    shots["03-banglish"] = shot(
        chat_opts("paper_minimal", "en_qwerty", "Banglish", *BL_NAME, BL_CHAT, roman,
                  candidates=[transliterate(roman, RULES), roman]))

    base = ui.load_theme("midnight_glass")
    shots["04-settings"] = pages.settings_screen(base, SETTINGS_ROWS)
    shots["05-themes"] = pages.theme_picker_screen(
        base, [ui.load_theme(t) for t in ui.theme_ids()], "midnight_glass")

    shots["06-one-handed"] = shot(
        chat_opts("ocean_breeze", "en_qwerty", "English", *EN_NAME, EN_CHAT,
                  "One-handed typing", one_handed="right"))

    for name, theme_id in THEME_SHOWCASE:
        shots[name] = shot(
            chat_opts(theme_id, "en_qwerty", "English", *EN_NAME, EN_CHAT, "Shobdo Keyboard"))

    return shots


def main(argv):
    if not CHROME:
        raise SystemExit("no Chromium binary found; set CHROME=/path/to/chrome")
    OUT.mkdir(parents=True, exist_ok=True)
    screens = build_screens()
    wanted = argv[1:] or list(screens)

    print(f"chrome: {CHROME}")
    print(f"output: {OUT.relative_to(ROOT)}\n")
    for name in wanted:
        if name not in screens:
            raise SystemExit(f"unknown screen {name!r}")
        png, _ = shoot(name, screens[name])
        before, after = png_optimise.optimise(png)
        print(f"ok   {name:28s} {before / 1024:7.1f} -> {after / 1024:7.1f} KiB")
    print(f"\n{len(wanted)} screenshots rendered")


if __name__ == "__main__":
    main(sys.argv)
