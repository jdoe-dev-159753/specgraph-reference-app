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
PLAYWRIGHT_CONFIG = Path("e2e/playwright.config.ts")
MARKER = re.compile(r"VFY-[A-Z0-9]+(?:-[A-Z0-9]+)*")
JAVA_TAG_ANNOTATION = re.compile(r"@(?:org\.junit\.jupiter\.api\.)?Tag\b")
JAVA_DISABLED = re.compile(r"@(?:org\.junit\.jupiter\.api\.)?Disabled\b")
JAVA_TEST_ANNOTATION = re.compile(
    r"@(?:org\.junit\.jupiter\.(?:api|params)\.)?"
    r"(?:Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b"
)
JAVA_TYPE_DECLARATION = re.compile(
    r"\b(?P<modifiers>(?:(?:public|protected|private|abstract|static|final|sealed|non-sealed)\s+)*)"
    r"(?P<kind>class|interface|record|enum)\s+"
    r"(?P<name>[A-Za-z_$][A-Za-z0-9_$]*)(?P<header>[^;{}]*)\{"
)
JAVA_PACKAGE = re.compile(
    r"(?m)^\s*package\s+([A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*)\s*;"
)
JAVA_IMPORT = re.compile(
    r"(?m)^\s*import\s+(?!static\b)"
    r"([A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$*][A-Za-z0-9_$*]*)*)\s*;"
)
PLAYWRIGHT_NON_PASSING = re.compile(r"\.\s*(?:skip|fixme|fail)\s*\(")
PLAYWRIGHT_TEST_MATCH = re.compile(r"\btestMatch\s*:\s*/((?:\\.|[^/\r\n])*)/([a-z]*)")
PLAYWRIGHT_TEST_DIR = re.compile(r"\btestDir\s*:")
SUREFIRE_DEFAULT_TEST_TYPE = re.compile(r"(?:Test.*|.*Test|.*Tests|.*TestCase)\Z")

CONTROLLED_OBLIGATION_IDS = frozenset({
    "VFY-CUSTOMER-READ-001",
    "VFY-AUTH-001",
    "VFY-ANALYSIS-CONTRACT-001",
    "VFY-RAG-001",
    "VFY-HISTORY-001",
    "VFY-REPRODUCIBILITY-001",
    "VFY-DETERMINISM-001",
    "VFY-FAILURE-PATHS-001",
    "VFY-CONFIDENTIALITY-001",
    "VFY-DELIVERY-001",
})

JAVA_EVIDENCE_GLOB = "backend/src/test/**/*.java"
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


@dataclass(frozen=True)
class JavaType:
    name: str
    qualified_name: str
    parents: tuple[str, ...]
    declaration_start: int
    body_start: int
    body_end: int
    depth: int
    is_abstract: bool
    has_direct_tests: bool


@dataclass(frozen=True)
class JavaSource:
    package_name: str
    explicit_imports: tuple[str, ...]
    wildcard_imports: tuple[str, ...]
    types: tuple[JavaType, ...]


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
            if text.startswith('"""', index):
                masked.extend(('"', '"', '"'))
                index += 3
                quote = '"""'
                state = "quoted"
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

        if quote == '"""' and text.startswith(quote, index):
            masked.extend(('"', '"', '"'))
            index += 3
            state = "code"
            continue
        masked.append(character)
        index += 1
        if character == "\\" and index < len(text):
            masked.append(text[index])
            index += 1
        elif quote != '"""' and character == quote:
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
            if text.startswith('"""', index):
                quote = '"""'
                masked.extend((" ", " ", " "))
                index += 3
                continue
            if character in ('"', "'", "`"):
                quote = character
                masked.append(" ")
            else:
                masked.append(character)
            index += 1
            continue

        if quote == '"""' and text.startswith(quote, index):
            masked.extend((" ", " ", " "))
            index += 3
            quote = ""
            continue
        masked.append(character if character in "\r\n" else " ")
        index += 1
        if character == "\\" and index < len(text):
            masked.append(" ")
            index += 1
        elif quote != '"""' and character == quote:
            quote = ""

    return "".join(masked)


def javascript_regex_can_start(text: str, start: int) -> bool:
    previous = start - 1
    while previous >= 0 and text[previous].isspace():
        previous -= 1
    if previous < 0 or text[previous] in "([{,:;=!?&|+-*%^~<>":
        return True
    if not (text[previous].isalnum() or text[previous] in "_$"):
        return False
    word_end = previous + 1
    while previous >= 0 and (text[previous].isalnum() or text[previous] in "_$"):
        previous -= 1
    return text[previous + 1:word_end] in {
        "await", "case", "delete", "do", "else", "in", "instanceof",
        "of", "return", "throw", "typeof", "void", "yield",
    }


def javascript_regex_end(text: str, start: int) -> int | None:
    index = start + 1
    in_character_class = False
    while index < len(text):
        character = text[index]
        if character in "\r\n":
            return None
        if character == "\\":
            index += 2
            continue
        if character == "[":
            in_character_class = True
        elif character == "]":
            in_character_class = False
        elif character == "/" and not in_character_class:
            index += 1
            while index < len(text) and text[index].isalpha():
                index += 1
            return index
        index += 1
    return None


def source_without_javascript_regex_literals(text: str) -> str:
    """Mask JavaScript regex literals while preserving offsets and string titles."""
    masked = list(text)
    index = 0
    while index < len(text):
        if text[index] in ('"', "'", "`"):
            _, index = quoted_literal(text, index)
            continue
        if text[index] != "/" or not javascript_regex_can_start(text, index):
            index += 1
            continue
        end = javascript_regex_end(text, index)
        if end is None:
            index += 1
            continue
        for masked_index in range(index, end):
            if masked[masked_index] not in "\r\n":
                masked[masked_index] = " "
        index = end
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


def brace_depths(structure: str) -> list[int]:
    depths: list[int] = []
    depth = 0
    for character in structure:
        depths.append(depth)
        if character == "{":
            depth += 1
        elif character == "}":
            depth = max(0, depth - 1)
    return depths


def closing_brace(structure: str, opening: int) -> int:
    depth = 1
    for index in range(opening + 1, len(structure)):
        if structure[index] == "{":
            depth += 1
        elif structure[index] == "}":
            depth -= 1
            if depth == 0:
                return index
    return len(structure)


def java_types(structure: str, package_name: str = "") -> tuple[JavaType, ...]:
    depths = brace_depths(structure)
    test_annotations = tuple(JAVA_TEST_ANNOTATION.finditer(structure))
    types: list[JavaType] = []
    for declaration in JAVA_TYPE_DECLARATION.finditer(structure):
        body_start = declaration.end() - 1
        body_end = closing_brace(structure, body_start)
        depth = depths[body_start]
        header = declaration.group("header")
        parents = tuple(
            re.findall(
                r"(?:\bextends\b|\bimplements\b|,)\s*([A-Za-z_$][A-Za-z0-9_$.]*)",
                header,
            )
        )
        has_direct_tests = any(
            body_start < annotation.start() < body_end
            and depths[annotation.start()] == depth + 1
            for annotation in test_annotations
        )
        modifiers = declaration.group("modifiers").split()
        name = declaration.group("name")
        types.append(JavaType(
            name,
            f"{package_name}.{name}" if package_name else name,
            parents,
            declaration.start(),
            body_start,
            body_end,
            depth,
            declaration.group("kind") == "interface" or "abstract" in modifiers,
            has_direct_tests,
        ))
    return tuple(types)


def java_source(structure: str) -> JavaSource:
    package = JAVA_PACKAGE.search(structure)
    package_name = package.group(1) if package is not None else ""
    imports = tuple(JAVA_IMPORT.findall(structure))
    return JavaSource(
        package_name,
        tuple(import_name for import_name in imports if not import_name.endswith(".*")),
        tuple(import_name[:-2] for import_name in imports if import_name.endswith(".*")),
        java_types(structure, package_name),
    )


def resolved_java_parent(
    source: JavaSource,
    parent: str,
    known_types: frozenset[str],
) -> str | None:
    """Resolve only Java parent references whose identity is unambiguous."""
    if "." in parent:
        return parent if parent in known_types else None

    explicit_matches = {
        imported
        for imported in source.explicit_imports
        if imported.rsplit(".", 1)[-1] == parent
    }
    if len(explicit_matches) > 1:
        return None
    candidates = set(explicit_matches)
    same_package = f"{source.package_name}.{parent}" if source.package_name else parent
    if same_package in known_types:
        candidates.add(same_package)
    if len(candidates) == 1:
        candidate = next(iter(candidates))
        return candidate if candidate in known_types else None
    # Do not guess which package a wildcard import contributes. False negatives
    # are safer here than allowing an unrelated type with the same simple name.
    if source.wildcard_imports:
        return None
    return None


def discoverable_java_types(structures: list[str]) -> frozenset[str]:
    sources = tuple(java_source(structure) for structure in structures)
    occurrences: dict[str, list[tuple[JavaSource, JavaType]]] = {}
    for source in sources:
        for java_type in source.types:
            occurrences.setdefault(java_type.qualified_name, []).append((source, java_type))
    # Duplicate fully-qualified declarations would not compile; exclude them
    # rather than merging their test/tag state.
    by_name = {
        name: declarations[0]
        for name, declarations in occurrences.items()
        if len(declarations) == 1
    }
    known_types = frozenset(by_name)
    parents = {
        name: tuple(
            resolved
            for parent in java_type.parents
            if (resolved := resolved_java_parent(source, parent, known_types)) is not None
        )
        for name, (source, java_type) in by_name.items()
    }
    inherited_tests = {
        name for name, (_, java_type) in by_name.items() if java_type.has_direct_tests
    }
    changed = True
    while changed:
        changed = False
        for name in by_name:
            if name not in inherited_tests and any(
                parent in inherited_tests for parent in parents[name]
            ):
                inherited_tests.add(name)
                changed = True

    executable = {
        name
        for name, (_, java_type) in by_name.items()
        if not java_type.is_abstract
        and name in inherited_tests
        and SUREFIRE_DEFAULT_TEST_TYPE.fullmatch(java_type.name)
    }
    discoverable = set(executable)
    frontier = list(executable)
    while frontier:
        declaration = by_name.get(frontier.pop())
        if declaration is None:
            continue
        _, java_type = declaration
        for parent in parents[java_type.qualified_name]:
            if parent in by_name and parent not in discoverable:
                discoverable.add(parent)
                frontier.append(parent)
    return frozenset(discoverable)


def java_tag_occurrences(text: str, structure: str) -> tuple[tuple[str, int, int], ...]:
    occurrences: list[tuple[str, int, int]] = []
    for annotation in JAVA_TAG_ANNOTATION.finditer(structure):
        cursor = annotation.end()
        while cursor < len(structure) and structure[cursor].isspace():
            cursor += 1
        if cursor >= len(structure) or structure[cursor] != "(":
            continue
        cursor += 1
        while cursor < len(text) and text[cursor].isspace():
            cursor += 1
        if cursor >= len(text) or text[cursor] != '"' or text.startswith('"""', cursor):
            continue

        marker, cursor = quoted_literal(text, cursor)
        if marker is None:
            continue
        while cursor < len(text) and text[cursor].isspace():
            cursor += 1
        if cursor < len(structure) and structure[cursor] == ")" and MARKER.fullmatch(marker):
            occurrences.append((marker, annotation.start(), cursor + 1))
    return tuple(occurrences)


def java_tag_markers(
    text: str,
    structure: str,
    discoverable_types: frozenset[str],
) -> frozenset[str]:
    """Keep tags attached to executable test methods or discoverable test types."""
    depths = brace_depths(structure)
    types = java_source(structure).types
    test_annotations = tuple(JAVA_TEST_ANNOTATION.finditer(structure))
    markers: set[str] = set()
    for marker, start, end in java_tag_occurrences(text, structure):
        depth = depths[start]
        attached_type = next(
            (
                java_type
                for java_type in types
                if java_type.declaration_start > end
                and java_type.depth == depth
                and not any(
                    separator in structure[end:java_type.declaration_start]
                    for separator in ";{}"
                )
            ),
            None,
        )
        if attached_type is not None:
            if attached_type.qualified_name in discoverable_types:
                markers.add(marker)
            continue

        containing_type = next(
            (
                java_type
                for java_type in reversed(types)
                if java_type.body_start < start < java_type.body_end
            ),
            None,
        )
        if containing_type is None or containing_type.qualified_name not in discoverable_types:
            continue
        if any(
            depths[annotation.start()] == depth
            and not any(
                separator
                in structure[min(start, annotation.start()):max(end, annotation.end())]
                for separator in ";{}"
            )
            for annotation in test_annotations
        ):
            markers.add(marker)
    return frozenset(markers)


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


def configured_playwright_tests(root: Path) -> tuple[Path, ...]:
    config = source_without_comments((root / PLAYWRIGHT_CONFIG).read_text(encoding="utf-8"))
    structure = source_without_quoted_text(config)
    configured = PLAYWRIGHT_TEST_MATCH.search(structure)
    if configured is None:
        raise ValueError("playwright.config.ts must declare one regex testMatch")
    configured_dir = PLAYWRIGHT_TEST_DIR.search(structure)
    if configured_dir is None:
        raise ValueError("playwright.config.ts must declare one string testDir")
    cursor = configured_dir.end()
    while cursor < len(config) and config[cursor].isspace():
        cursor += 1
    if cursor >= len(config) or config[cursor] not in ('"', "'"):
        raise ValueError("playwright.config.ts testDir must be a string literal")
    test_dir, _ = quoted_literal(config, cursor)
    if test_dir is None:
        raise ValueError("playwright.config.ts testDir must be a closed string literal")
    flag_names = configured.group(2)
    unsupported = set(flag_names) - set("ims")
    if unsupported:
        raise ValueError("unsupported Playwright testMatch flags: " + "".join(sorted(unsupported)))
    flags = 0
    if "i" in flag_names:
        flags |= re.IGNORECASE
    if "m" in flag_names:
        flags |= re.MULTILINE
    if "s" in flag_names:
        flags |= re.DOTALL
    try:
        test_match = re.compile(configured.group(1), flags)
    except re.error as error:
        raise ValueError(f"unsupported Playwright testMatch regex: {error}") from error
    e2e_root = (root / "e2e").resolve()
    test_root = (e2e_root / Path(test_dir)).resolve()
    try:
        test_root.relative_to(e2e_root)
    except ValueError as error:
        raise ValueError("playwright.config.ts testDir must stay within e2e") from error
    return tuple(
        path
        for path in sorted(test_root.rglob("*.ts"))
        if test_match.search(path.relative_to(test_root).as_posix())
    )


def evidence_markers(
    path: Path,
    java_discoverable_types: frozenset[str] | None = None,
) -> frozenset[str]:
    text = source_without_comments(path.read_text(encoding="utf-8"))
    if path.suffix == ".java":
        structure = source_without_quoted_text(text)
        # Fail closed for the whole source file: class- and method-level @Disabled
        # are ambiguous without a Java parser, so no colocated tag certifies evidence.
        if JAVA_DISABLED.search(structure):
            return frozenset()
        discoverable_types = java_discoverable_types
        if discoverable_types is None:
            discoverable_types = discoverable_java_types([structure])
        return java_tag_markers(text, structure, discoverable_types)
    if path.suffix == ".ts":
        text = source_without_javascript_regex_literals(text)
        structure = source_without_quoted_text(text)
        # Any member skip/fixme/fail call can make a test non-passing dynamically or
        # disable an enclosing suite. A mixed file supplies no evidence until split.
        if PLAYWRIGHT_NON_PASSING.search(structure):
            return frozenset()
        return frozenset(
            marker for title in playwright_test_titles(text) for marker in MARKER.findall(title)
        )
    return frozenset()


def inventory(root: Path = ROOT) -> MarkerInventory:
    controlled = catalogue_ids((root / CATALOGUE).read_text(encoding="utf-8"))
    found: dict[str, set[Path]] = {}
    java_paths = tuple(sorted(root.glob(JAVA_EVIDENCE_GLOB)))
    java_structures = [
        source_without_quoted_text(source_without_comments(path.read_text(encoding="utf-8")))
        for path in java_paths
    ]
    discoverable_types = discoverable_java_types(java_structures)
    for path in java_paths:
        for marker in evidence_markers(path, discoverable_types):
            found.setdefault(marker, set()).add(path.relative_to(root))
    for path in configured_playwright_tests(root):
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
    removed_ids = CONTROLLED_OBLIGATION_IDS - result.catalogue_ids
    added_ids = result.catalogue_ids - CONTROLLED_OBLIGATION_IDS
    if removed_ids or added_ids:
        drift: list[str] = []
        if removed_ids:
            drift.append("missing stable IDs: " + ", ".join(sorted(removed_ids)))
        if added_ids:
            drift.append("unexpected IDs: " + ", ".join(sorted(added_ids)))
        failures.append("controlled verification catalogue drift: " + "; ".join(drift))
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
