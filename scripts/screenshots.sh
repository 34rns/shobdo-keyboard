#!/usr/bin/env bash
# Capture real screenshots from a connected device or emulator (METHOD A).
#
# NOT RUN for the 0.1.0 assets: this machine has no adb, no emulator and no built APK,
# so docs/screenshots/*.png were rendered by scripts/render_screenshots.py instead.
# This script is the upgrade path once a device is available - run it, look at the
# output, and replace the rendered files if the real captures look better.
#
#   ./scripts/screenshots.sh                 # capture everything
#   APK=path/to/app.apk ./scripts/screenshots.sh
#
# It expects a debug-only DemoActivity in the app module:
#   -n app.shobdo.keyboard/.DemoActivity
#     --es screen  chat | settings | themes      which screen to show
#     --es theme   midnight_glass                theme id from assets/themes
#     --es lang    en | bn | banglish            keyboard language
#     --ez one_handed true                       one-handed layout
# That activity does not exist yet (the project has no toolchain, see .ai/HANDOFF.md).

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="${APK:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
OUT="${OUT:-$ROOT/docs/screenshots/device}"
PKG=app.shobdo.keyboard
IME="$PKG/.ShobdoIME"

for tool in adb; do
  command -v "$tool" >/dev/null 2>&1 || { echo "error: $tool not found in PATH" >&2; exit 1; }
done
[ -f "$APK" ] || { echo "error: no APK at $APK (run ./gradlew assembleDebug)" >&2; exit 1; }

mkdir -p "$OUT"

echo "== device =="
adb wait-for-device
adb devices

echo "== install =="
adb install -r "$APK"

echo "== enable keyboard =="
adb shell ime enable "$IME"
adb shell ime set "$IME"
# Give the IME a moment to become the default before a field asks for it.
sleep 2

capture() {
  local name="$1"; shift
  echo "-- $name"
  adb shell am start -W -n "$PKG/.DemoActivity" "$@" >/dev/null
  sleep 2                      # let the window and keyboard settle
  adb exec-out screencap -p > "$OUT/$name.png"
  # A 0-byte or tiny file means the screencap failed; fail loudly.
  local size
  size=$(wc -c < "$OUT/$name.png")
  [ "$size" -gt 20000 ] || { echo "error: $name.png is only $size bytes" >&2; exit 1; }
  echo "   $OUT/$name.png ($size bytes)"
}

capture 01-english  --es screen chat --es theme midnight_glass --es lang en
capture 02-bangla   --es screen chat --es theme bangla_flag   --es lang bn
capture 03-banglish --es screen chat --es theme paper_minimal --es lang banglish
capture 04-settings --es screen settings
capture 05-themes   --es screen themes
capture 06-one-handed --es screen chat --es theme ocean_breeze --es lang en --ez one_handed true

for theme in midnight_glass pure_light amoled_black ocean_breeze sunset_gradient bangla_flag; do
  capture "theme-$theme" --es screen chat --es theme "$theme" --es lang en
done

# Optional: a short Banglish typing clip, if the device supports screenrecord.
if command -v ffmpeg >/dev/null 2>&1; then
  echo "== recording banglish gif =="
  adb shell screenrecord --time-limit 8 --size 540x960 /sdcard/banglish.mp4 &
  record_pid=$!
  adb shell am start -W -n "$PKG/.DemoActivity" --es screen chat --es theme paper_minimal \
    --es lang banglish --ez auto_type true >/dev/null
  wait "$record_pid" || true
  adb pull /sdcard/banglish.mp4 "$OUT/banglish.mp4"
  adb shell rm /sdcard/banglish.mp4
  ffmpeg -y -i "$OUT/banglish.mp4" -vf "fps=10,scale=360:-1:flags=lanczos" \
    "$ROOT/docs/assets/banglish-typing.gif"
  rm -f "$OUT/banglish.mp4"
fi

echo
echo "captured into $OUT"
echo "now re-run: python scripts/compose_screenshots.py"
