#!/usr/bin/env bash
# scripts/release.sh VERSION
#
# Publishes a release of Shobdo Keyboard:
#
#   1. refuses to run on a dirty tree or if guard.sh complains
#   2. bumps version.properties (VERSION_NAME = VERSION, VERSION_CODE += 1)
#   3. moves the CHANGELOG "Unreleased" notes under the new version and today's date
#   4. runs the unit tests and builds the signed release APK
#   5. copies it to build/releases/Shobdo-Keyboard-vVERSION.apk + a .sha256 file
#   6. commits "chore(release): vVERSION", tags vVERSION, pushes both
#   7. creates the GitHub release with the CHANGELOG section as notes
#
# Example:  scripts/release.sh 0.1.0
#
# Every preflight check runs BEFORE version.properties or CHANGELOG.md are
# touched, so a machine that is not ready stops here and changes nothing.
set -euo pipefail

fail() { echo "release: $*" >&2; exit 1; }

[ $# -eq 1 ] || { echo "usage: scripts/release.sh <major.minor.patch>" >&2; exit 64; }

VERSION=$1
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] \
  || fail "'$VERSION' is not a MAJOR.MINOR.PATCH version"

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$REPO_ROOT"

# ---------------------------------------------------------------- preflight --
[ -d .git ] || fail "not a git repository"
command -v git  >/dev/null || fail "git not found on PATH"
command -v gh   >/dev/null || fail "gh (GitHub CLI) not found - install with: winget install GitHub.cli"
command -v sha256sum >/dev/null || fail "sha256sum not found"

echo "release: checking guard.sh"
bash scripts/guard.sh || fail "guard.sh refused this tree (private files present)"

gh auth status >/dev/null 2>&1 || fail "gh is not logged in - run: gh auth login"

[ -f app/build.gradle.kts ] || fail "app/build.gradle.kts not found - there is no Gradle project yet"
[ -f ./gradlew ]            || fail "./gradlew not found - generate it with: gradle wrapper --gradle-version 8.11.1"
[ -f version.properties ]   || fail "version.properties not found"
[ -f CHANGELOG.md ]         || fail "CHANGELOG.md not found"

git remote get-url origin >/dev/null 2>&1 \
  || fail "no 'origin' remote - create the repo first: gh repo create shobdo-keyboard --public --source=. --remote=origin"

if [ -n "$(git status --porcelain)" ]; then
  echo "release: the git tree is not clean:" >&2
  git status --short >&2
  fail "commit or stash these first (release.sh publishes what is on disk)"
fi

# Signing: app/build.gradle.kts reads these; without a keystore the APK is
# still built, but it is unsigned and will not install as an update.
KEYSTORE=${SHOBDO_KEYSTORE:-"$HOME/keystores/shobdo-release.jks"}
if [ -f "$KEYSTORE" ]; then
  echo "release: keystore found at $KEYSTORE"
else
  echo "release: WARNING - no keystore at $KEYSTORE" >&2
  echo "release: WARNING - the APK will be UNSIGNED. Generate one first:" >&2
  echo "release: WARNING -   keytool -genkeypair -v -keystore \\$HOME/keystores/shobdo-release.jks" >&2
  echo "release: WARNING -             -alias shobdo -keyalg RSA -keysize 4096 -validity 10000" >&2
fi

# ------------------------------------------------------- bump version file --
OLD_NAME=$(sed -n 's/^VERSION_NAME=//p' version.properties | tr -d '\r')
OLD_CODE=$(sed -n 's/^VERSION_CODE=//p' version.properties | tr -d '\r')
[[ "$OLD_CODE" =~ ^[0-9]+$ ]] || fail "VERSION_CODE in version.properties is '$OLD_CODE', expected a number"
NEW_CODE=$((OLD_CODE + 1))

printf 'VERSION_NAME=%s\nVERSION_CODE=%s\n' "$VERSION" "$NEW_CODE" > version.properties
echo "release: version.properties  $OLD_NAME ($OLD_CODE) -> $VERSION ($NEW_CODE)"

# ------------------------------------------------------- move CHANGELOG notes --
NOTES="build/releases/NOTES-v${VERSION}.md"
mkdir -p build/releases

python "$REPO_ROOT/scripts/release_changelog.py" "$VERSION" "$NOTES" \
  || fail "could not update CHANGELOG.md"

# ------------------------------------------------------- test, then build --
echo "release: running unit tests"
./gradlew :app:testDebugUnitTest --stacktrace

echo "release: building release APK"
./gradlew :app:assembleRelease --stacktrace

APK=""
for candidate in app/build/outputs/apk/release/app-release.apk \
                 app/build/outputs/apk/release/*-release*.apk; do
  if [ -f "$candidate" ]; then APK=$candidate; break; fi
done
[ -n "$APK" ] || fail "no APK found under app/build/outputs/apk/release/"

DEST="build/releases/Shobdo-Keyboard-v${VERSION}.apk"
cp "$APK" "$DEST"
( cd build/releases && sha256sum "Shobdo-Keyboard-v${VERSION}.apk" > "Shobdo-Keyboard-v${VERSION}.apk.sha256" )
echo "release: $(du -h "$DEST" | cut -f1)  $DEST"
cat "build/releases/Shobdo-Keyboard-v${VERSION}.apk.sha256"

# ------------------------------------------------------------- publish --
if [ ! -s "$NOTES" ]; then
  printf 'Release v%s of Shobdo Keyboard.\n' "$VERSION" > "$NOTES"
fi

echo "release: committing"
git add version.properties CHANGELOG.md
git commit -m "chore(release): v${VERSION}"

echo "release: tagging v${VERSION}"
git tag -a "v${VERSION}" -m "Release v${VERSION}"

echo "release: pushing"
git push origin HEAD
git push origin "v${VERSION}"

echo "release: creating the GitHub release"
gh release create "v${VERSION}" \
  --title "v${VERSION}" \
  --notes-file "$NOTES" \
  "$DEST" "$DEST.sha256"

echo
echo "release: done - v${VERSION} published"
gh release view "v${VERSION}" --json name,tagName,url --jq '"\(.name)  \(.url)"'