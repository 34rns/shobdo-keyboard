# Privacy

Shobdo Keyboard works offline. It has no network permission or remote data collection.

## What we collect

We receive nothing from the keyboard. There is no account, analytics, remote crash
reporting, advertising or tracking. Local learning and dictionary storage are described below.

## Why we cannot collect anything

The app does not request the `android.permission.INTERNET` permission. Without it the
operating system blocks network access at the kernel level, so no code path inside the app
can send data anywhere, even if it tried. There are also no third-party network libraries
in the app, and no ads.

## What happens to what you type

Typed text is sent to the app you are typing into through Android's input connection.
That app controls what happens to the text afterward. Shobdo keeps the active composing
word in memory and clears its session when the field or keyboard closes. It does not save
full messages or typing transcripts, or read surrounding field text.

When **Learn my choices** is enabled (the existing default), Bangla words selected or
committed in Banglish can be saved as counts and timestamps in private on-device storage.
The keyboard stores up to 400 words and 800 previous-word/next-word counts. Their ranking
influence decays with a 30-day half-life. Turning learning off stops recording and using
these automatic counts; **Clear learned words** deletes them.

The **User dictionary** stores up to 200 Bangla words and their Roman spellings that you
add explicitly. These entries work even with automatic learning off. Remove an entry to
delete it; clearing learned words preserves the explicit dictionary. Removing a dictionary
word also removes its automatic counts. Uninstalling the app deletes its private storage.

Password fields never show Banglish suggestions or record learning. URI/email, numeric,
no-suggestion and no-personalized-learning fields also suppress suggestions and learning.
The previous-word context resets between input sessions; it is built only from words
committed through this keyboard, not surrounding text read from the receiving app.

- Settings (languages, theme, haptics) are stored locally on your device.
- Custom themes are stored in the app's private storage.
- No keyboard data is uploaded or synced. Android backup/data transfer is disabled in
  the manifest and extraction rules.

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
