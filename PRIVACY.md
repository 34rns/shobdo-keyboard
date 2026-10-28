# Privacy

Shobdo Keyboard is built so that it cannot collect anything, by construction rather than
by promise.

## What we collect

Nothing. There is no account, no analytics, no crash reporting service, no advertising and
no tracking of any kind.

## Why we cannot collect anything

The app does not request the `android.permission.INTERNET` permission. Without it the
operating system blocks network access at the kernel level, so no code path inside the app
can send data anywhere, even if it tried. There are also no third-party network libraries
in the app, and no ads.

## What happens to what you type

Typed text goes only to the app you are typing into, through the standard Android input
connection. It is never stored by the keyboard and never leaves the device.

- Settings (languages, theme, haptics) are stored locally on your device.
- Custom themes are stored in the app's private storage.
- Nothing is uploaded, synced or backed up to us.

There is no clipboard history feature, and the keyboard does not read your clipboard
beyond what Android passes for a paste you trigger yourself.

## Permissions

| Permission | Why |
|---|---|
| `BIND_INPUT_METHOD` | Required by Android for any keyboard; it is not a data permission. |
| `VIBRATE` | Only if you turn haptic feedback on. |

No internet. No location. No contacts. No microphone. No storage-wide access.

## Children

The app collects no data from anyone, including children.

## Changes

If this policy ever changes, the change will appear in this file with the release that
introduced it. A keyboard that gains network access would be a different product, so treat
any such change with suspicion and check the source.

## Contact

Open an issue at <https://github.com/34rns/shobdo-keyboard/issues>, or use the private
reporting route in [SECURITY.md](SECURITY.md) for anything sensitive.
