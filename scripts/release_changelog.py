#!/usr/bin/env python
"""Move the CHANGELOG.md "Unreleased" notes under a new version heading.

Usage: release_changelog.py VERSION NOTES_FILE

Behaviour:

- Writes the notes for VERSION into NOTES_FILE (consumed by `gh release create`).
  For a first release of a version whose section already exists, the notes come
  from that existing section; otherwise from whatever is being moved.
- If Unreleased only holds the "- No unreleased changes yet." placeholders,
  nothing is moved: an empty release must not carry placeholder text into a
  version heading.
- If Unreleased has real notes they are cut into `## [VERSION] - YYYY-MM-DD`
  placed immediately after Unreleased, placeholder bullets are dropped, sections
  left empty by that are removed, and Unreleased is reset to the placeholders.
- Footnote links are kept in step: the Unreleased comparison starts at the new
  tag and a `releases/tag/vVERSION` link is added.
- Refuses when the VERSION section already exists while Unreleased has content,
  because merging by concatenation would duplicate the heading.

Exit code 0 means CHANGELOG.md is in the required state for this release.
"""
import datetime
import pathlib
import re
import sys

PLACEHOLDER_BULLET = "- No unreleased changes yet."

UNRELEASED_RESET = [
    "",
    "### Added",
    "",
    PLACEHOLDER_BULLET,
    "",
    "### Changed",
    "",
    PLACEHOLDER_BULLET,
    "",
    "### Fixed",
    "",
    PLACEHOLDER_BULLET,
    "",
]


def fail(message):
    print("release_changelog: " + message, file=sys.stderr)
    sys.exit(1)


def is_header(line, level=2):
    return line.startswith("#" * level + " ")


def find_header(lines, pattern):
    for index, line in enumerate(lines):
        if re.match(pattern, line):
            return index
    return None


def block_end(lines, start):
    """Index of the next heading of the same or higher level."""
    for index in range(start + 1, len(lines)):
        if is_header(lines[index], 2):
            return index
    return len(lines)


def is_link(line):
    return bool(re.match(r"^\[[^\]]+\]:\s+https?://", line))


def is_placeholder(line):
    return line.strip() == PLACEHOLDER_BULLET


def has_content(body):
    """Any line that is not blank, a heading or a placeholder bullet."""
    for line in body:
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if is_placeholder(line):
            continue
        return True
    return False


def drop_empty_sections(body):
    """Remove `###` sections that carry no real content, blanks and headings."""
    out, index = [], 0
    while index < len(body):
        line = body[index]
        if re.match(r"^###\s", line):
            end = index + 1
            while end < len(body) and not re.match(r"^###\s", body[end]):
                end += 1
            segment = body[index + 1 : end]
            if has_content([line] + segment):
                out.append(line)
                out.extend(segment)
            index = end
        else:
            out.append(line)
            index += 1
    while out and not out[0].strip():
        out.pop(0)
    while out and not out[-1].strip():
        out.pop()
    return out


def trim(body):
    out = list(body)
    while out and not out[0].strip():
        out.pop(0)
    while out and not out[-1].strip():
        out.pop()
    return out


def section_body(lines, pattern):
    start = find_header(lines, pattern)
    if start is None:
        return None
    return lines[start + 1 : block_end(lines, start)]


def write_notes(body, version, notes_path):
    text = "\n".join(trim([l for l in body if not is_link(l)])).strip()
    if not text:
        text = "Release v%s of Shobdo Keyboard." % version
    pathlib.Path(notes_path).write_text(text + "\n", encoding="utf-8")


def update_footnotes(lines, version):
    """Point the Unreleased comparison at the new tag, add its release link."""
    repo = None
    out = []
    for line in lines:
        match = re.match(
            r"^(\[Unreleased\]:\s+)(\S+?)/compare/v[\d.]+(\.\.\.HEAD)\s*$", line
        )
        if match:
            repo = match.group(2)
            out.append("%s%s/compare/v%s%s" % (match.group(1), repo, version, match.group(3)))
        else:
            out.append(line)

    tag_name = "[%s]" % version
    if not any(line.startswith(tag_name + ":") for line in out):
        if repo:
            link = "%s: %s/releases/tag/v%s" % (tag_name, repo, version)
            for index, line in enumerate(out):
                if line.startswith("[") and is_link(line):
                    out.insert(index, link)
                    break
            else:
                out.append(link)
        elif repo is None:
            print(
                "release_changelog: no [Unreleased] comparison link to update",
                file=sys.stderr,
            )
    return out


def main():
    if len(sys.argv) != 3:
        fail("usage: release_changelog.py VERSION NOTES_FILE")
    version, notes_path = sys.argv[1], sys.argv[2]
    if not re.match(r"^\d+\.\d+\.\d+$", version):
        fail("'%s' is not a MAJOR.MINOR.PATCH version" % version)

    path = pathlib.Path("CHANGELOG.md")
    if not path.exists():
        fail("CHANGELOG.md not found")
    lines = path.read_text(encoding="utf-8").splitlines()

    unreleased = find_header(lines, r"^## \[Unreleased\]\s*$")
    if unreleased is None:
        fail("no '## [Unreleased]' section in CHANGELOG.md")
    end = block_end(lines, unreleased)
    body = lines[unreleased + 1 : end]

    existing = find_header(lines, r"^## \[%s\]" % re.escape(version))
    today = datetime.date.today().isoformat()

    if not has_content(body):
        # Nothing pending: the Unreleased block stays exactly as it is.
        source = (
            lines[existing + 1 : block_end(lines, existing)]
            if existing is not None
            else body
        )
        write_notes(source, version, notes_path)
        print("release_changelog: Unreleased has no pending notes, nothing moved")
        print("release_changelog: notes for v%s -> %s" % (version, notes_path))
        return

    if existing is not None:
        fail(
            "Unreleased has notes but '## [%s]' already exists - merge them by hand"
            % version
        )

    moved = drop_empty_sections(body)

    text = "\n".join(
        lines[: unreleased + 1]
        + UNRELEASED_RESET
        + ["## [%s] - %s" % (version, today), ""]
        + moved
        + [""]
        + lines[end:]
    ) + "\n"

    text = "\n".join(update_footnotes(text.splitlines(), version)) + "\n"
    path.write_text(text, encoding="utf-8")

    write_notes(
        section_body(text.splitlines(), r"^## \[%s\]" % re.escape(version)),
        version,
        notes_path,
    )
    print("release_changelog: moved Unreleased -> '## [%s] - %s'" % (version, today))
    print("release_changelog: notes for v%s -> %s" % (version, notes_path))


if __name__ == "__main__":
    main()