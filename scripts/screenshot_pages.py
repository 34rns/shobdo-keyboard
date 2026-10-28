#!/usr/bin/env python3
"""The two non-keyboard screens: Settings and the theme picker.

Split out of screenshot_html.py to keep that file readable (RULES.md: no file over 400
lines). Colours, surfaces and the phone shell come from screenshot_html, so these pages
stay consistent with the keyboard screens.
"""

import screenshot_html as ui

PAGES_CSS = """
.page { flex: 1; overflow: hidden; display: flex; flex-direction: column; }
.h1 { font-size: 25px; font-weight: 650; letter-spacing: -.2px; }
.section { padding: 14px 20px 2px; font-size: 12px; font-weight: 650; letter-spacing: .8px;
           text-transform: uppercase; opacity: .55; }
.card { margin: 8px 14px 0; border-radius: 18px; overflow: hidden; }
.srow { display: flex; align-items: center; gap: 12px; padding: 13px 16px; font-size: 14.5px;
        border-bottom: 1px solid rgba(128,128,128,.16); }
.srow:last-child { border-bottom: 0; }
.srow .val { margin-left: auto; font-size: 13px; opacity: .6; }
.sw { width: 40px; height: 23px; border-radius: 12px; position: relative; flex: none; }
.sw::after { content: ""; position: absolute; top: 3px; width: 17px; height: 17px;
             border-radius: 9px; background: #fff; }
.sw.on::after { right: 3px; }
.sw.off::after { left: 3px; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; padding: 10px 14px; }
.tcard { border-radius: 15px; padding: 9px; display: flex; flex-direction: column;
         justify-content: space-between; height: 116px; }
.tcard .tname { font-size: 12.5px; font-weight: 600; margin-top: 6px; }
.tcard .mini { display: flex; gap: 4px; }
.tcard .mini i { flex: 1; height: 12px; border-radius: 3.5px; }
.tcard .mini-col { display: flex; flex-direction: column; gap: 4px; }
.tcard .tick { font-size: 11px; font-weight: 700; }
"""


def settings_screen(theme, rows):
    """The Compose SettingsActivity home: sections of switches and value rows."""
    p = ui.ui_palette(theme)
    accent = theme["accent"]
    blocks = []
    for section, entries in rows:
        items = []
        for entry in entries:
            if entry["kind"] == "switch":
                state = "on" if entry["on"] else "off"
                background = ui.css(accent) if entry["on"] else "rgba(128,128,128,.35)"
                right = f'<div class="sw {state}" style="background:{background}"></div>'
            elif entry["kind"] == "field":
                right = ""
            else:
                right = f'<span class="val">{entry["value"]}</span>'
            muted = "opacity:.55" if entry["kind"] == "field" else ""
            items.append(f'<div class="srow"><span style="{muted}">{entry["label"]}</span>'
                         f'<span style="margin-left:auto"></span>{right}</div>')
        blocks.append(f'<div class="section">{section}</div>'
                      f'<div class="card" style="background:{p["surface"]}">{"".join(items)}</div>')
    body = f"""<div class="phone" style="background:{p['app']};color:{p['text']}">
{ui.status_bar(theme)}
<div style="padding:10px 20px 4px"><div class="h1">Shobdo Keyboard</div>
<div style="font-size:13px;opacity:.6;margin-top:4px">Version 0.1.0</div></div>
<div class="page">{''.join(blocks)}</div>
{ui.navbar(theme)}
</div>"""
    return ui.page(body, theme, PAGES_CSS)


def theme_picker_screen(theme, themes, active_id):
    p = ui.ui_palette(theme)
    accent = theme["accent"]
    cards = []
    for item in themes:
        style = item["key"]
        selected = item["id"] == active_id
        ring = f"box-shadow: 0 0 0 2px {ui.css(accent)}" if selected else ""
        bar = f'<i style="background:{ui.css(style["fill"])}"></i>'
        mini = "".join(f'<div class="mini">{bar * 5}</div>' for _ in range(3))
        tick = (f'<span class="tick" style="color:{ui.css(accent)}">&#10003;</span>'
                if selected else "<span></span>")
        cards.append(
            f'<div class="tcard" style="background:{ui.background_css(item)};{ring}">'
            f'<div class="mini-col">{mini}</div>'
            f'<div class="tname" style="color:{ui.css(style["text"])}">{item["name"]}</div>'
            f'{tick}</div>'
        )
    body = f"""<div class="phone" style="background:{p['app']};color:{p['text']}">
{ui.status_bar(theme)}
<div style="padding:10px 20px 0"><div class="h1">Themes</div>
<div style="font-size:13px;opacity:.6;margin-top:4px">12 built-in, plus your own</div></div>
<div class="grid">{''.join(cards)}</div>
{ui.navbar(theme)}
</div>"""
    return ui.page(body, theme, PAGES_CSS)
