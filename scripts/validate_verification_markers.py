#!/usr/bin/env python3
"""Validate executable V&V markers against the controlled obligation catalogue.

Only JUnit ``@Tag`` values and Playwright ``test`` titles are evidence markers.
Workflow step names and controlled documentation are deliberately not treated as
test evidence: they may describe orchestration or historical gaps without being
executed by a test harness.
"""

from dataclasses import dataclass
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
CATALOGUE = Path("docs/assignment/VV/verification.yaml")
MARKER = re.compile(r"VFY-[A-Z0-9]+(?:-[A-Z0-9]+)*")
JAVA_TAG = re.compile(r'@Tag\(\s*"(VFY-[A-Z0-9]+(?:-[A-Z0-9]+)*)"\s*\)')
JAVA_DISABLED = re.compile(r"@(?:org\.junit\.jupiter\.api\.)?Disabled\b")
PLAYWRIGHT_DISABLED = re.compile(r"\.\s*(?:skip|fixme)\s*\(")

EVIDENCE_GLOBS = (
    "backend/src/test/**/*.java",
    "e2e/**/*.spec.ts",
)

# These qualifications are part of the ratchet output so marker presence cannot
# be misread as proof that every heterogeneous method on an obligation passed.
BOUNDED_METHOD_NOTES = {
    "VFY-CUSTOMER-READ-001": (
        "query_count remains independently owned by issue #421"
    ),
    "VFY-REPRODUCIBILITY-001": (
        "the marker covers executable smoke evidence; clean_checkout_compose "
        "still requires evidence from the delivery workflow run"
    ),
    "VFY-DELIVERY-001": (
        "the marker covers the executable demo path; human_validation remains manual"
    ),
}


@dataclass(frozen=True)
class MarkerInventory:
    catalogue_ids: frozenset[str]
    sources: dict[str, tuple[Path, ...]]
    unknown: frozenset[str]
    missing: frozenset[str]


def catalogue_ids(text: str) -> frozenset[str]:
    obligations = re.search(r"(?ms)^obligations:\n(.*)\Z", text)
    if obligations is None:
        raise ValueError("missing top-level obligations section")
    ids = frozenset(re.findall(r"(?m)^  (VFY-[A-Z0-9]+(?:-[A-Z0-9]+)*):", obligations.group(1)))
    if not ids:
        raise ValueError("verification catalogue contains no obligations")
    return ids


def source_without_comments(text: str) -> str:
    """Mask line and block comments while preserving quoted source text."""
    masked: list[str] = []
    index = 0
    state = "code"
    quote = ""
    while index < len(text):
        character = text[index]
        following = text[index + 1] if index + 1 < len(text) else ""

        if state == "code":
            if character == "/" and following == "/":
                masked.extend((" ", " "))
                index += 2
                state = "line-comment"
                continue
            if character == "/" and following == "*":
                masked.extend((" ", " "))
                index += 2
                state = "block-comment"
                continue
            masked.append(character)
            if character in ('"', "'", "`"):
                quote = character
                state = "quoted"
            index += 1
            continue

        if state == "line-comment":
            masked.append(character if character in "\r\n" else " ")
            index += 1
            if character in "\r\n":
                state = "code"
            continue

        if state == "block-comment":
            if character == "*" and following == "/":
                masked.extend((" ", " "))
                index += 2
                state = "code"
                continue
            masked.append(character if character in "\r\n" else " ")
            index += 1
            continue

        masked.append(character)
        index += 1
        if character == "\\" and index < len(text):
            masked.append(text[index])
            index += 1
        elif character == quote:
            state = "code"

    return "".join(masked)


def source_without_quoted_text(text: str) -> str:
    """Mask quoted text so disabled-looking content is not treated as syntax."""
    masked: list[str] = []
    index = 0
    quote = ""
    while index < len(text):
        character = text[index]
        if not quote:
            if character in ('"', "'", "`"):
                quote = character
                masked.append(" ")
            else:
                masked.append(character)
            index += 1
            continue

        masked.append(character if character in "\r\n" else " ")
        index += 1
        if character == "\\" and index < len(text):
            masked.append(" ")
            index += 1
        elif character == quote:
            quote = ""

    return "".join(masked)


def quoted_literal(text: str, start: int) -> tuple[str | None, int]:
    """Read one single- or double-quoted literal without evaluating escapes."""
    quote = text[start]
    content: list[str] = []
    index = start + 1
    while index < len(text):
        character = text[index]
        if character == "\\" and index + 1 < len(text):
            content.extend((character, text[index + 1]))
            index += 2
            continue
        if character == quote:
            return "".join(content), index + 1
        content.append(character)
        index += 1
    return None, len(text)


def playwright_test_titles(text: str) -> tuple[str, ...]:
    """Extract static titles from global ``test(...)`` calls in executable source."""
    titles: list[str] = []
    index = 0
    while index < len(text):
        character = text[index]
        if character in ('"', "'", "`"):
            _, index = quoted_literal(text, index)
            continue

        if not text.startswith("test", index):
            index += 1
            continue

        before = text[index - 1] if index else ""
        after_index = index + len("test")
        after = text[after_index] if after_index < len(text) else ""
        if (before and (before.isalnum() or before in "_$")) or (
            after and (after.isalnum() or after in "_$")
        ):
            index = after_index
            continue

        previous = index - 1
        while previous >= 0 and text[previous].isspace():
            previous -= 1
        if previous >= 0 and text[previous] == ".":
            index = after_index
            continue

        cursor = after_index
        while cursor < len(text) and text[cursor].isspace():
            cursor += 1
        if cursor >= len(text) or text[cursor] != "(":
            index = after_index
            continue
        cursor += 1
        while cursor < len(text) and text[cursor].isspace():
            cursor += 1
        if cursor >= len(text) or text[cursor] not in ('"', "'"):
            index = after_index
            continue

        title, index = quoted_literal(text, cursor)
        if title is not None:
            titles.append(title)

    return tuple(titles)


def evidence_markers(path: Path) -> frozenset[str]:
    text = source_without_comments(path.read_text(encoding="utf-8"))
    structure = source_without_quoted_text(text)
    if path.suffix == ".java":
        # Fail closed for the whole source file: class- and method-level @Disabled
        # are ambiguous without a Java parser, so no colocated tag certifies evidence.
        if JAVA_DISABLED.search(structure):
            return frozenset()
        return frozenset(JAVA_TAG.findall(text))
    if path.suffix == ".ts":
        # Any member skip/fixme call can disable a test dynamically or an enclosing
        # suite. A file that mixes one with V&V titles supplies no evidence until split.
        if PLAYWRIGHT_DISABLED.search(structure):
            return frozenset()
        return frozenset(
            marker for title in playwright_test_titles(text) for marker in MARKER.findall(title)
        )
    return frozenset()


def inventory(root: Path = ROOT) -> MarkerInventory:
    controlled = catalogue_ids((root / CATALOGUE).read_text(encoding="utf-8"))
    found: dict[str, set[Path]] = {}
    for pattern in EVIDENCE_GLOBS:
        for path in sorted(root.glob(pattern)):
            for marker in evidence_markers(path):
                found.setdefault(marker, set()).add(path.relative_to(root))

    discovered = frozenset(found)
    sources = {marker: tuple(sorted(paths)) for marker, paths in sorted(found.items())}
    return MarkerInventory(
        controlled,
        sources,
        discovered - controlled,
        controlled - discovered,
    )


def validate(root: Path = ROOT) -> MarkerInventory:
    result = inventory(root)
    failures: list[str] = []
    if result.unknown:
        failures.append("unknown executable V&V markers: " + ", ".join(sorted(result.unknown)))
    if result.missing:
        failures.append(
            "obligations without discoverable executable evidence: "
            + ", ".join(sorted(result.missing))
        )
    if failures:
        raise ValueError("; ".join(failures))
    return result


def main() -> int:
    try:
        result = validate()
    except (OSError, ValueError) as error:
        print(f"Verification marker ratchet failed: {error}", file=sys.stderr)
        return 1

    print(f"Verification marker ratchet OK: {len(result.catalogue_ids)}/10 obligations discoverable.")
    for marker in sorted(result.catalogue_ids):
        locations = ", ".join(str(path).replace("\\", "/") for path in result.sources[marker])
        print(f"  - {marker}: {locations}")
        if marker in BOUNDED_METHOD_NOTES:
            print(f"    NOTE: {BOUNDED_METHOD_NOTES[marker]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
