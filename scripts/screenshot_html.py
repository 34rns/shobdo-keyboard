#!/usr/bin/env python3
"""Build the HTML that render_screenshots.py photographs.

Every key, gap, corner radius and colour in here is read from the same JSON the
app ships: app/src/main/assets/layouts/*.json and app/src/main/assets/themes/*.json.
Nothing about the keyboard is drawn from memory - if a theme or layout changes, the
screenshots change with it.

Colours are the app's #AARRGGBB (alpha first), NOT CSS order.

This module only builds strings. scripts/render_screenshots.py runs a browser over them.
"""

import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
LAYOUTS = ROOT / "app/src/main/assets/layouts"
THEMES = ROOT / "app/src/main/assets/themes"

DEVICE_W = 540
DEVICE_H = 960


# --- assets ----------------------------------------------------------------


def load_theme(theme_id):
    path = THEMES / f"{theme_id}.json"
    return json.loads(path.read_text(encoding="utf-8"))


def load_layout(layout_id):
    path = LAYOUTS / f"{layout_id}.json"
    return json.loads(path.read_text(encoding="utf-8"))


def theme_ids():
    return sorted(p.stem for p in THEMES.glob("*.json"))


# --- colour ----------------------------------------------------------------


def parse_color(value):
    """(r, g, b, a) from #RRGGBB or #AARRGGBB (alpha first, Android order)."""
    hex_digits = value.lstrip("#")
    if len(hex_digits) == 6:
        r, g, b = (int(hex_digits[i:i + 2], 16) for i in (0, 2, 4))
        return r, g, b, 255
    if len(hex_digits) == 8:
        a, r, g, b = (int(hex_digits[i:i + 2], 16) for i in (0, 2, 4, 6))
        return r, g, b, a
    raise ValueError(f"bad colour {value!r}")


def css(value, alpha_mul=1.0):
    r, g, b, a = parse_color(value)
    return f"rgba({r},{g},{b},{round(a / 255 * alpha_mul, 4)})"


def luminance(value):
    r, g, b, _ = parse_color(value)
    channels = []
    for channel in (r, g, b):
        c = channel / 255
        channels.append(c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4)
    return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]


def text_on(background):
    """Black or white, whichever reads better on this fill."""
    return "#0B1020" if luminance(background) > 0.42 else "#FFFFFF"


def background_css(theme):
    bg = theme["background"]
    if bg["type"] == "gradient":
        angle = bg.get("angle", 90)
        stops = ", ".join(css(c) for c in bg["colors"])
        return f"linear-gradient({angle}deg, {stops})"
    # "solid", or "image" - the image is a user file we cannot load here, so a
    # screenshot of an image theme shows the flat colour behind it.
    colour = bg.get("color") or bg.get("colors", ["#000000"])[0]
    return css(colour)


def is_dark(theme):
    return bool(theme["dark"])


# --- page shell ------------------------------------------------------------

CSS = """
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body { width: __W__px; height: __H__px; overflow: hidden; }
body {
  font-family: "Segoe UI", "Nirmala UI", "Noto Sans", sans-serif;
  -webkit-font-smoothing: antialiased;
  text-rendering: optimizeLegibility;
}
.phone { width: __W__px; height: __H__px; display: flex; flex-direction: column; overflow: hidden; }

.status {
  height: 30px; display: flex; align-items: center; justify-content: space-between;
  padding: 0 18px; font-size: 13px; font-weight: 600; letter-spacing: .2px; flex: none;
}
.status-right { display: flex; align-items: center; gap: 7px; }
.sig { display: flex; align-items: flex-end; gap: 2px; }
.sig i { width: 3px; background: currentColor; border-radius: 1px; opacity: .9; }
.batt { width: 22px; height: 11px; border: 1.4px solid currentColor; border-radius: 3px;
        position: relative; opacity: .9; }
.batt::after { content: ""; position: absolute; left: 1.5px; top: 1.5px; bottom: 1.5px;
               width: 68%; background: currentColor; border-radius: 1px; }

.appbar { height: 58px; display: flex; align-items: center; gap: 12px; padding: 0 16px; flex: none; }
.avatar { width: 36px; height: 36px; border-radius: 18px; display: flex; align-items: center;
          justify-content: center; font-size: 15px; font-weight: 600; flex: none; }
.who { line-height: 1.25; }
.who .name { font-size: 15.5px; font-weight: 600; }
.who .sub { font-size: 11.5px; opacity: .62; }
.spacer { flex: 1; }
.iconbtn { width: 34px; height: 34px; border-radius: 17px; display: flex; align-items: center;
           justify-content: center; font-size: 15px; }

.chat { flex: 1; display: flex; flex-direction: column; justify-content: flex-end;
        gap: 8px; padding: 8px 16px 14px; overflow: hidden; }
.bubble { max-width: 74%; padding: 9px 13px; border-radius: 16px; font-size: 14.5px;
          line-height: 1.38; }
.bubble.in { align-self: flex-start; border-bottom-left-radius: 5px; }
.bubble.out { align-self: flex-end; border-bottom-right-radius: 5px; }
.stamp { font-size: 10.5px; opacity: .55; align-self: center; margin: 2px 0 6px; }

.compose { display: flex; align-items: center; gap: 9px; padding: 8px 14px 10px; flex: none; }
.field { flex: 1; display: flex; align-items: center; padding: 0 15px; height: 42px;
         border-radius: 21px; font-size: 14.5px; white-space: nowrap; overflow: hidden; }
.caret { display: inline-block; width: 1.8px; height: 19px; margin-left: 1px;
         background: currentColor; opacity: .85; }
.send { width: 40px; height: 40px; border-radius: 20px; display: flex; align-items: center;
        justify-content: center; font-size: 17px; flex: none; }

.ime { flex: none; }
.sugg { display: flex; align-items: center; gap: 4px; padding: 6px 8px 4px; font-size: 14px; }
.chip { padding: 5px 11px; border-radius: 8px; white-space: nowrap; }
.chip.primary { font-weight: 600; }
.kb { padding: 5px 5px 3px; }
.row { display: flex; gap: var(--gap); margin-bottom: var(--gap); }
.row:last-child { margin-bottom: 0; }
.key { flex-grow: 1; flex-shrink: 1; flex-basis: 0; height: var(--key-h); border-radius: var(--radius);
       display: flex; align-items: center; justify-content: center;
       font-size: var(--font); letter-spacing: .2px; position: relative; }
.key.special { font-size: calc(var(--font) * .82); }
.key.space { font-size: calc(var(--font) * .78); letter-spacing: .6px; }
.key.locked::after { content: ""; position: absolute; bottom: 5px; left: 50%;
                     transform: translateX(-50%); width: 13px; height: 2.5px;
                     border-radius: 2px; background: currentColor; opacity: .8; }
.navbar { height: 22px; display: flex; align-items: center; justify-content: center; flex: none; }
.navbar div { width: 128px; height: 4px; border-radius: 2px; }
"""


def page(body, theme, extra_css=""):
    css_text = CSS.replace("__W__", str(DEVICE_W)).replace("__H__", str(DEVICE_H))
    return f"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><style>
{css_text}{extra_css}
</style></head><body>{body}</body></html>"""


# --- surfaces --------------------------------------------------------------

DARK_UI = {
    "app": "#0A0E1A",
    "surface": "rgba(255,255,255,.055)",
    "bubble_in": "#1A2233",
    "text": "#E9EEF8",
    "field": "rgba(255,255,255,.07)",
    "divider": "rgba(255,255,255,.08)",
    "nav": "rgba(255,255,255,.30)",
    "bubble_edge": "",
}
LIGHT_UI = {
    "app": "#F4F6FB",
    "surface": "rgba(255,255,255,.78)",
    "bubble_in": "#FFFFFF",
    "text": "#12172A",
    "field": "rgba(18,23,42,.06)",
    "divider": "rgba(18,23,42,.08)",
    "nav": "rgba(18,23,42,.22)",
    # White bubbles on a near-white app background need a hairline to stay visible.
    "bubble_edge": "0 0 0 1px rgba(18,23,42,.055), 0 1px 2px rgba(18,23,42,.05)",
}


def ui_palette(theme):
    return DARK_UI if is_dark(theme) else LIGHT_UI


# --- parts -----------------------------------------------------------------


def status_bar(theme):
    p = ui_palette(theme)
    return f"""<div class="status" style="color:{p['text']}">
  <span>9:41</span>
  <span class="status-right">
    <span class="sig"><i style="height:4px"></i><i style="height:6px"></i><i style="height:8px"></i><i style="height:10px"></i></span>
    <span style="font-size:11px;opacity:.85">5G</span>
    <span class="batt"></span>
  </span>
</div>"""


def app_bar(theme, title, subtitle, initial):
    p = ui_palette(theme)
    accent = theme["accent"]
    return f"""<div class="appbar" style="color:{p['text']}">
  <div class="avatar" style="background:{css(accent)};color:{text_on(accent)}">{initial}</div>
  <div class="who"><div class="name">{title}</div><div class="sub">{subtitle}</div></div>
  <div class="spacer"></div>
  <div class="iconbtn" style="background:{p['surface']}">&#8942;</div>
</div>"""


def chat_block(theme, messages):
    p = ui_palette(theme)
    accent = theme["accent"]
    out_fg = text_on(accent)
    parts = []
    for item in messages:
        if item.startswith("·"):
            label = item[1:].strip() or "Today, 9:41 AM"
            parts.append(f'<div class="stamp" style="color:{p["text"]}">{label}</div>')
        elif item.startswith(">"):
            parts.append(
                f'<div class="bubble out" style="background:{css(accent)};color:{out_fg}">'
                f"{item[1:].strip()}</div>"
            )
        else:
            edge = f"box-shadow:{p['bubble_edge']}" if p["bubble_edge"] else ""
            parts.append(
                f'<div class="bubble in" style="background:{p["bubble_in"]};'
                f'color:{p["text"]};{edge}">{item}</div>'
            )
    return f'<div class="chat">{"".join(parts)}</div>'


def compose_bar(theme, field_text):
    p = ui_palette(theme)
    accent = theme["accent"]
    return f"""<div class="compose">
  <div class="field" style="background:{p['field']};color:{p['text']}">{field_text}<span class="caret"></span></div>
  <div class="send" style="background:{css(accent)};color:{text_on(accent)}">&#8593;</div>
</div>"""


def suggestion_bar(theme, candidates):
    bar = theme["suggestionBar"]
    accent = theme["accent"]
    chips = []
    for index, text in enumerate(candidates):
        if index == 0:
            chips.append(
                f'<span class="chip primary" style="background:{css(accent, .16)};'
                f'color:{css(accent)}">{text}</span>'
            )
        else:
            chips.append(f'<span class="chip">{text}</span>')
    return (
        f'<div class="sugg" style="background:{css(bar["background"])};color:{css(bar["text"])}">'
        f'{"".join(chips)}</div>'
    )


# Vector icons for the function keys. The layout JSON carries a glyph label (⇧ ⌫ ⏎),
# which a font draws as a boxed symbol at key size; these paths draw the shape
# KeyboardView will draw on its Canvas.
ICON_PATHS = {
    "shift": '<path d="M12 4.5 4.8 12.4h4.1v6.9h6.2v-6.9h4.1z"/>',
    "backspace": '<path d="M9.4 5h10.2A2.4 2.4 0 0 1 22 7.4v9.2a2.4 2.4 0 0 1-2.4 2.4H9.4L2 12z"/>'
                 '<path d="m12.6 9.3 5.4 5.4m0-5.4-5.4 5.4"/>',
    "enter": '<path d="M20 5.5v6.6a3 3 0 0 1-3 3H5.2"/><path d="m9 11-4 4 4 4"/>',
}


def icon_svg(kind, size=21):
    return (f'<svg viewBox="0 0 24 24" width="{size}" height="{size}" fill="none" '
            f'stroke="currentColor" stroke-width="1.7" stroke-linecap="round" '
            f'stroke-linejoin="round">{ICON_PATHS[kind]}</svg>')


def key_html(key, theme, opts):
    accent = theme["accent"]
    style = theme["key"]
    special = theme.get("specialKey", {})
    kind = key.get("type")
    label = key.get("label", "")
    weight = key.get("widthWeight", 1)

    fill = special.get("fill", style["fill"]) if kind else style["fill"]
    text = special.get("text", style["text"]) if kind else style["text"]

    classes = ["key"]
    extra = [f"flex-grow:{weight}", f"background:{css(fill)}", f"color:{css(text)}"]

    if kind:
        classes.append("special")
    if kind == "space":
        classes.append("space")
        label = opts["language_label"]
    if kind == "shift" and opts.get("shift"):
        extra += [f"background:{css(accent)}", f"color:{text_on(accent)}"]
        if opts["shift"] == "locked":
            classes.append("locked")
    if opts.get("pressed_code") is not None and key.get("code") == opts["pressed_code"]:
        extra.append(f"background:{css(style['pressed'])}")

    if style.get("stroke"):
        extra.append(f"box-shadow: inset 0 0 0 1px {css(style['stroke'])}")
    if style.get("shadow"):
        extra.append(f"box-shadow: inset 0 0 0 1px {css(style.get('stroke', '#00000000'))}, "
                     f"0 1px 0 rgba(0,0,0,.22), 0 2px 5px rgba(0,0,0,.14)")

    content = icon_svg(kind) if kind in ICON_PATHS else f'<span>{label}</span>'
    return (f'<div class="{" ".join(classes)}" style="{";".join(extra)}" '
            f'aria-label="{label}">{content}</div>')


def keyboard_html(theme, layout, opts):
    gap = theme.get("keySpacing", 6)
    style = theme["key"]
    font = theme.get("font", {"scale": 1.0, "bold": False})
    rows = []
    for row in layout["rows"]:
        keys = "".join(key_html(k, theme, opts) for k in row)
        rows.append(f'<div class="row">{keys}</div>')

    kb_style = (f"--gap:{gap}px; --radius:{style.get('radius', 10)}px; "
                f"--key-h:{opts.get('key_height', 52)}px; "
                f"--font:{round(18 * font.get('scale', 1.0), 1)}px; "
                f"font-weight:{650 if font.get('bold') else 400}")

    return f'<div class="kb" style="{kb_style}">{"".join(rows)}</div>'


def navbar(theme):
    p = ui_palette(theme)
    return f'<div class="navbar"><div style="background:{p["nav"]}"></div></div>'


# --- whole screens ---------------------------------------------------------


def keyboard_screen(theme, layout, opts):
    """Chat app above, Shobdo below: the arrangement a user actually sees."""
    p = ui_palette(theme)
    suggest = suggestion_bar(theme, opts["candidates"]) if opts.get("candidates") else ""
    ime_style = f"background:{background_css(theme)};overflow:hidden"
    if opts.get("one_handed") == "right":
        ime_style += ";width:86%;margin-left:auto;border-top-left-radius:20px"
    body = f"""<div class="phone" style="background:{p['app']};color:{p['text']}">
{status_bar(theme)}
{app_bar(theme, opts['title'], opts['subtitle'], opts['initial'])}
{chat_block(theme, opts['messages'])}
{compose_bar(theme, opts['field'])}
<div class="ime" style="{ime_style}">{suggest}{keyboard_html(theme, layout, opts)}</div>
{navbar(theme)}
</div>"""
    return page(body, theme)
