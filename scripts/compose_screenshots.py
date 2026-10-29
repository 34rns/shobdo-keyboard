#!/usr/bin/env python3
"""Compose the presentation images from the raw screenshots.

    python scripts/compose_screenshots.py

Inputs  : docs/screenshots/01-english.png ... (produced by scripts/screenshots.ps1)
Outputs : docs/screenshots/composite-languages.png
          docs/screenshots/composite-themes.png
          docs/screenshots/composite-settings.png
          docs/assets/social-preview.png        (1280x640, GitHub social image)

Captions are English only on purpose: Pillow has no HarfBuzz shaping here (`raqm` is
missing), so Bengali drawn into a caption would break its conjuncts. The Bengali in the
screenshots themselves is real, shaped by the browser.
"""

import pathlib
import sys

from PIL import Image, ImageDraw, ImageFilter

import png_optimise

ROOT = pathlib.Path(__file__).resolve().parent.parent
SHOTS = ROOT / "docs/screenshots"
ASSETS = ROOT / "docs/assets"

INK = (232, 238, 249)
MUTED = (154, 168, 190)
ACCENT = (99, 102, 241)

FONT_CANDIDATES = {
    "bold": ["C:/Windows/Fonts/segoeuib.ttf", "C:/Windows/Fonts/arialbd.ttf",
             "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
             "/System/Library/Fonts/Supplemental/Arial Bold.ttf"],
    "regular": ["C:/Windows/Fonts/segoeui.ttf", "C:/Windows/Fonts/arial.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/System/Library/Fonts/Supplemental/Arial.ttf"],
}


def font(style, size):
    from PIL import ImageFont

    for path in FONT_CANDIDATES[style]:
        if pathlib.Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default(size)


def load(name):
    path = SHOTS / f"{name}.png"
    if not path.exists():
        raise SystemExit(f"missing {path}; run scripts/screenshots.ps1 first")
    return Image.open(path).convert("RGB")


# --- background ------------------------------------------------------------


def vertical_gradient(size, top, bottom):
    width, height = size
    strip = Image.new("RGB", (1, height))
    for y in range(height):
        t = y / max(height - 1, 1)
        strip.putpixel((0, y), tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return strip.resize(size, Image.Resampling.BILINEAR)


def glow(canvas, centre, radius, colour, alpha):
    """A soft blurred circle: the 'premium' light source behind the phones."""
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    cx, cy = centre
    draw.ellipse([cx - radius, cy - radius, cx + radius, cy + radius],
                 fill=(*colour, alpha))
    canvas.alpha_composite(layer.filter(ImageFilter.GaussianBlur(radius // 2)))


def backdrop(size, top=(11, 17, 32), bottom=(26, 37, 61)):
    canvas = vertical_gradient(size, top, bottom).convert("RGBA")
    glow(canvas, (int(size[0] * 0.18), int(size[1] * 0.12)), 520, ACCENT, 46)
    glow(canvas, (int(size[0] * 0.88), int(size[1] * 0.92)), 480, (56, 189, 248), 34)
    return canvas


# --- device frame ----------------------------------------------------------


def place_phone(canvas, shot, x, y, width, radius=34, bezel=9):
    """Draw a phone with a bezel and a soft drop shadow, top-left at (x, y)."""
    height = round(width * shot.height / shot.width)
    shot = shot.resize((width, height), Image.Resampling.LANCZOS)

    shadow = Image.new("RGBA", (width + 160, height + 160), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        [80, 88, 80 + width, 88 + height], radius=radius + bezel, fill=(0, 0, 0, 165))
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(26)), (x - 80, y - 80))

    body = Image.new("RGBA", (width + bezel * 2, height + bezel * 2), (0, 0, 0, 0))
    ImageDraw.Draw(body).rounded_rectangle(
        [0, 0, body.width - 1, body.height - 1], radius=radius + bezel,
        fill=(24, 30, 45, 255), outline=(70, 84, 112, 255), width=1)
    canvas.alpha_composite(body, (x - bezel, y - bezel))

    mask = Image.new("L", (width, height), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, width - 1, height - 1], radius=radius, fill=255)
    canvas.paste(shot, (x, y), mask)
    return width, height


def centred_text(draw, text, centre_x, y, text_font, fill):
    left, top, right, bottom = draw.textbbox((0, 0), text, font=text_font)
    draw.text((centre_x - (right - left) / 2 - left, y), text, font=text_font, fill=fill)
    return bottom - top


# --- composites ------------------------------------------------------------


def composite(paths, captions, out_name, cols, phone_width, title=None):
    gap = 74 if cols == 3 else 60
    margin = 96
    caption_size = 27
    caption_gap = 16

    sample = load(paths[0])
    phone_h = round(phone_width * sample.height / sample.width)
    rows = (len(paths) + cols - 1) // cols

    header = 118 if title else 74
    width = margin * 2 + cols * phone_width + (cols - 1) * gap
    height = header + rows * (phone_h + caption_gap + caption_size + 46) + 60

    canvas = backdrop((width, height))
    draw = ImageDraw.Draw(canvas)
    if title:
        centred_text(draw, title, width / 2, 46, font("bold", 44), INK)

    for index, (name, caption) in enumerate(zip(paths, captions)):
        row, col = divmod(index, cols)
        row_width = min(cols, len(paths) - row * cols)
        row_width_px = row_width * phone_width + (row_width - 1) * gap
        x = (width - row_width_px) / 2 + col * (phone_width + gap)
        y = header + row * (phone_h + caption_gap + caption_size + 46)
        place_phone(canvas, load(name), round(x), round(y), phone_width)
        centred_text(draw, caption, x + phone_width / 2, y + phone_h + caption_gap,
                     font("regular", caption_size), MUTED)

    out = SHOTS / out_name
    canvas.convert("RGB").save(out, optimize=True)
    png_optimise.optimise(out)
    return out


def social_preview(out_name="social-preview.png"):
    width, height = 1280, 640
    canvas = backdrop((width, height), top=(9, 14, 28), bottom=(30, 32, 78))
    draw = ImageDraw.Draw(canvas)

    draw.rectangle([76, 118, 80, 300], fill=(*ACCENT, 255))

    draw.text((116, 96), "Shobdo Keyboard", font=font("bold", 66), fill=INK)
    draw.text((118, 186), "A private, beautiful keyboard",
              font=font("regular", 27), fill=(198, 208, 226))
    draw.text((118, 222), "for Bangla, Banglish and English.",
              font=font("regular", 27), fill=(198, 208, 226))
    draw.text((118, 276), "English QWERTY  ·  Bangla (Provhat)  ·  Banglish input",
              font=font("regular", 21), fill=MUTED)

    for index, line in enumerate([
        "No internet permission — nothing leaves your device",
        "24 themes, each checked for 4.5:1 contrast",
        "Open source, MIT licensed",
    ]):
        y = 336 + index * 38
        draw.ellipse([118, y + 8, 126, y + 16], fill=(*ACCENT, 255))
        draw.text((142, y), line, font=font("regular", 21), fill=(214, 222, 236))

    # Two phones, the lighter one behind so it reads as a pair.
    back = load("07-pure-light")
    front = load("03-banglish")
    place_phone(canvas, back, 792, 150, 212, radius=24, bezel=6)
    place_phone(canvas, front, 986, 118, 212, radius=24, bezel=6)

    out = ASSETS / out_name
    canvas.convert("RGB").save(out, optimize=True)
    png_optimise.optimise(out)
    return out


def main():
    ASSETS.mkdir(parents=True, exist_ok=True)
    written = []

    written.append(composite(
        ["01-english", "02-bangla", "03-banglish"],
        ["English QWERTY, with number row",
         "Bangla, Provhat fixed layout",
         "Banglish: type Roman, get Bangla"],
        "composite-languages.png", cols=3, phone_width=520))

    written.append(composite(
        ["01-english", "09-aurora", "07-pure-light", "08-lava"],
        ["Midnight Glass (default)", "Aurora Borealis", "Pure Light", "Lava"],
        "composite-themes.png", cols=4, phone_width=400,
        title="Four of the twenty-four built-in themes"))

    written.append(composite(
        ["04-settings", "05-themes", "06-theme-dialog"],
        ["Settings", "Twenty-four themes, live preview", "Duplicate, export or apply"],
        "composite-settings.png", cols=3, phone_width=520))

    written.append(social_preview())

    for path in written:
        print(f"wrote {path.relative_to(ROOT)}  {path.stat().st_size / 1024:7.1f} KiB")
    print(f"\n{len(written)} images composed")


if __name__ == "__main__":
    main()
