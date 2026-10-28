# Security Policy

## Supported versions

Shobdo Keyboard is pre-1.0, so only the latest release is supported. Fixes land in a new
release rather than as patches to older ones.

| Version | Supported |
|---|---|
| 0.1.x | yes |
| older | no |

## Reporting a vulnerability

**Please do not open a public issue for a security problem.**

Use GitHub's private advisory form:
<https://github.com/34rns/shobdo-keyboard/security/advisories/new>

If you cannot use that, contact the maintainer at <https://github.com/34rns> and ask for a
private channel. Please include what you did, what happened, what you expected, and the
Android version and device if relevant.

You can expect an acknowledgement as soon as possible, a plain assessment of whether we
agree it is a vulnerability, and credit in the release notes if you would like it. We are a
volunteer project and cannot offer a bug bounty.

## What is in scope

Anything that breaks one of these guarantees:

- **No network.** The app requests no `INTERNET` permission and ships no networking code or
  libraries. A path that causes network access, directly or through a dependency, is a
  serious bug.
- **Typed text stays on the device.** Text goes only to the focused app via the
  `InputConnection`. It must never be written to logs, cached, or included in a crash
  report.
- **Password fields are treated specially.** No suggestions, no learning, no logging while
  a password field is focused.
- **Clipboard and content are not read** beyond what a paste gesture passes in.
- **A malicious theme or layout file cannot execute code or exhaust resources.** Themes and
  layouts are data; parsing them must not crash, loop forever, or allocate without bound.

Useful reports look like: a theme JSON that crashes the keyboard, a layout that causes an
infinite loop, typed text appearing in logcat, or anything touching the `INTERNET`
permission.

## What is out of scope

- Bugs that require a rooted device or a malicious app with accessibility-level access.
- Denial of service by the user themselves (for example typing 100 MB into a field).
- Issues in Android itself or in the system keyboard chooser.
- Crash reports from unsupported versions.
- Anything requiring an attacker to already have physical access to an unlocked device.

## Supply chain

Dependencies are kept to a minimum on purpose and pinned through a Gradle version catalog.
If a dependency gains a network permission or a telemetry component, treat that as a
vulnerability and report it.
