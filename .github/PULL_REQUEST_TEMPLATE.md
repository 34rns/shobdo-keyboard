## What changed

A short description of the change and why it is needed.

Closes #

## Type of change

- [ ] Bug fix (`fix:`)
- [ ] New feature (`feat:`)
- [ ] Documentation (`docs:`)
- [ ] Tests or QA (`test:`)
- [ ] Chore, build or refactor (`chore:` / `refactor:`)
- [ ] Translation
- [ ] Theme or layout data

## How this was verified

Paste the commands you ran and their result. A change is done when it builds and passes
tests, not when it looks right.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
python scripts/validate-layouts.py
python scripts/validate-themes.py
python scripts/validate-strings.py
python scripts/banglish_check.py
```

Result:

## Screenshots

For any visual change, before and after.

## Checklist

- [ ] The change is one logical thing, not several
- [ ] No new dependency without prior discussion
- [ ] All user-visible text goes through string resources, in both locales
- [ ] No `INTERNET` permission, analytics or network library was added
- [ ] Files stay well under 400 lines
- [ ] Commit messages follow Conventional Commits
- [ ] Documentation updated if behaviour, themes, layouts or Banglish rules changed
