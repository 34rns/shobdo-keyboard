# Theme JSON format

Themes live in `app/src/main/assets/themes/`, one file per theme, and the file name
must equal the theme `id` (`midnight_glass.json` -> `"id": "midnight_glass"`).
`ThemeManager.kt` loads every built-in theme from that directory, plus user themes from
app storage, so adding a theme is adding a file.

## Colour format

Every colour is `#RRGGBB` or `#AARRGGBB` — **alpha first**, the Android order, not CSS.
`#FFFFFF1A` is white at 10% alpha (0x1A = 26/255), so a key fill like that shows the
background gradient through it. Getting the order wrong is the most common mistake here:
`#1AFFFFFF` would be a fully opaque, slightly-off-white instead of 10% white.

## Top level

| field           | type    | required | meaning                                        |
|-----------------|---------|----------|------------------------------------------------|
| `id`            | string  | yes      | unique id, equal to the file name               |
| `name`          | string  | yes      | display name in the theme picker                |
| `dark`          | boolean | yes      | true if the keyboard is a dark theme            |
| `dynamic`       | boolean | no       | true = read system colours at runtime (see below) |
| `background`    | object  | yes      | see Background                               |
| `key`           | object  | yes      | normal key styling                              |
| `specialKey`    | object  | yes      | shift, backspace, enter, symbols                |
| `accent`        | colour  | yes      | cursor, selection and highlight colour          |
| `suggestionBar` | object  | yes      | `background` and `text`                         |
| `font`          | object  | yes      | `scale` (number > 0) and `bold` (boolean)       |
| `keySpacing`    | number  | yes      | gap between keys in dp, `>= 0`                  |

### `key`

| field     | type    | meaning                                      |
|-----------|---------|----------------------------------------------|
| `fill`    | colour  | key background, usually translucent          |
| `pressed` | colour  | background while held                        |
| `text`    | colour  | key label                                    |
| `radius`  | number  | corner radius in dp, `>= 0`                  |
| `shadow`  | boolean | draw a soft drop shadow under each key       |
| `stroke`  | colour  | 1dp key border (use a low-alpha colour)      |

### `specialKey`

Only `fill` and `text`. It intentionally does not carry radius or shadow, so a theme
cannot make the spacebar look like a different keyboard.

## Background

```json
{ "type": "solid",    "color": "#0F172A" }
{ "type": "gradient", "colors": ["#0F172A", "#1E293B"], "angle": 90 }
{ "type": "image",    "image": "user_bg.jpg", "brightness": 0.6, "blur": 12, "fallback": "#101418" }
```

- `solid` needs `color`.
- `gradient` needs `colors` (2 or more, in draw order) and `angle` in degrees,
  where 90 = top to bottom.
- `image` is the user's own picture. `brightness` (0..1) and `blur` (dp) are the sliders
  from the settings screen. `fallback` is required: it is the colour assumed for contrast
  checking, and what is drawn while the image decodes.

A custom image is stored in app storage, never in the theme file itself; the JSON keeps
only the path so a theme stays small and exportable.

## Contrast rule (enforced)

Text on a key must reach **at least 4.5:1** (WCAG AA for normal text) against the key's
composited colour — the translucent `fill` blended over the background, not against the
fill's raw value. For gradients every colour stop is evaluated and the **worst** result
must still pass, because the keyboard may sit on either end.

`scripts/validate-themes.py` enforces this and the schema:

```
python scripts/validate-themes.py
```

It prints the four ratios per theme (`key`, `pressed`, `special`, `suggestionBar`) so a
failure tells you exactly which colour to change and what composited hex it produced.

## Material You

`material_you.json` sets `"dynamic": true`. On Android 12+ `ThemeManager` reads the
system palette and replaces the background, key, specialKey, accent and suggestionBar
colours at runtime. The colours in the file are the **fallback** for Android 8-11 or when
the system palette is unavailable, and they are what the contrast checker validates — so
they must stand on their own.

## Built-in themes

| id                | name            | dark | notes                              |
|-------------------|-----------------|------|------------------------------------|
| `midnight_glass`  | Midnight Glass  | yes  | navy gradient, translucent keys    |
| `pure_light`      | Pure Light      | no   | near-white, dark ink text          |
| `amoled_black`    | AMOLED Black    | yes  | true #000000 for OLED power saving |
| `ocean_breeze`    | Ocean Breeze    | yes  | teal gradient                      |
| `sunset_gradient` | Sunset Gradient | no   | warm orange gradient               |
| `forest_calm`     | Forest Calm     | yes  | deep green                         |
| `rose_gold`       | Rose Gold       | no   | blush background, dark text        |
| `neon_cyber`      | Neon Cyber      | yes  | cyan glow on near-black            |
| `material_you`    | Material You    | yes  | `dynamic: true`, dark fallback     |
| `paper_minimal`   | Paper Minimal   | no   | cream, flat, tiny radius           |
| `royal_purple`    | Royal Purple    | yes  | violet gradient                    |
| `bangla_flag`     | Bangla Flag     | yes  | deep green with red accent         |

## Customisation vs theme fields

The theme file owns appearance: colours, radius, spacing, font scale and bold.

These belong to app settings, not to a theme, because they change layout or hardware
behaviour rather than colour: key height (compact/normal/tall), number row on/off,
haptic strength, one-handed mode (left/right), key borders (on/off), and the background
image brightness/blur sliders. A theme must not be able to change them.

## User themes

- Duplicating a built-in theme copies its JSON with a new `id` (`midnight_glass_copy`)
  and marks it user-owned, so app updates never overwrite it.
- Export writes the same JSON to a `.json` file; import validates it with the same rules
  as `validate-themes.py` (schema + contrast) before accepting it, so a hand-edited or
  malicious file cannot produce unreadable keys.
- Rejecting an import must show a readable reason ("text contrast 2.1:1, needs 4.5:1"),
  not a generic failure.
