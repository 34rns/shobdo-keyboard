#!/usr/bin/env bash
# guard.sh - keeps private memory files and key material out of Git.
#
# Checks the tracked files and the staged files, and exits non-zero if any path
# is private. Installed as .git/hooks/pre-commit and .git/hooks/pre-push.
#
# This script is itself public, so it must never contain secrets.

set -uo pipefail

status=0

is_private() {
  case "$1" in
    .ai | .ai/*) return 0 ;;
    AGENTS.md | CLAUDE.md | GEMINI.md | .cursorrules) return 0 ;;
    local.properties) return 0 ;;
    *.jks | *.keystore) return 0 ;;
    *) return 1 ;;
  esac
}

report() {
  printf 'guard: BLOCKED (%s): %s\n' "$1" "$2" >&2
  status=1
}

while IFS= read -r path; do
  [ -n "$path" ] || continue
  is_private "$path" && report "tracked" "$path"
done <<EOF
$(git ls-files)
EOF

while IFS= read -r path; do
  [ -n "$path" ] || continue
  is_private "$path" && report "staged" "$path"
done <<EOF
$(git diff --cached --name-only --diff-filter=ACMR)
EOF

if [ "$status" -ne 0 ]; then
  cat >&2 <<'MSG'
guard: commit/push refused. These paths are private and must stay local.
guard: unstage them with: git reset HEAD -- <path>
MSG
  exit 1
fi

exit 0
