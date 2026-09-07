#!/usr/bin/env python3
"""Validate executable V&V markers against the controlled obligation catalogue.

Only JUnit ``@Tag`` values and Playwright ``test`` titles are evidence markers.
Workflow step names and controlled documentation are deliberately not treated as
test evidence: they may describe orchestration or historical gaps without being
executed by a test harness.
"""

from dataclasses import dataclass, replace
import os
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
CATALOGUE = Path("docs/assignment/VV/verification.yaml")
PLAYWRIGHT_CONFIG = Path("e2e/playwright.config.ts")
MARKER = re.compile(r"VFY-[A-Z0-9]+(?:-[A-Z0-9]+)*")
JAVA_TAG_ANNOTATION = re.compile(
    r"@(?P<qualified>org\.junit\.jupiter\.api\.)?Tag\b"
)
JUNIT_TEST_ANNOTATIONS = {
    "Test": "org.junit.jupiter.api.Test",
    "ParameterizedTest": "org.junit.jupiter.params.ParameterizedTest",
    "RepeatedTest": "org.junit.jupiter.api.RepeatedTest",
    "TestFactory": "org.junit.jupiter.api.TestFactory",
    "TestTemplate": "org.junit.jupiter.api.TestTemplate",
}
JAVA_TEST_ANNOTATION = re.compile(
    r"@(?P<qualified>org\.junit\.jupiter\.(?:api|params)\.)?"
    r"(?P<simple>Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b"
)
JUNIT_NESTED_ANNOTATIONS = {"Nested": "org.junit.jupiter.api.Nested"}
JAVA_NESTED_ANNOTATION = re.compile(
    r"@(?P<qualified>org\.junit\.jupiter\.api\.)?(?P<simple>Nested)\b"
)
JUNIT_EXECUTION_GUARD_NAMES = (
    "Disabled",
    "DisabledForJreRange",
    "DisabledIf",
    "DisabledIfEnvironmentVariable",
    "DisabledIfEnvironmentVariables",
    "DisabledIfSystemProperty",
    "DisabledIfSystemProperties",
    "DisabledInNativeImage",
    "DisabledOnJre",
    "DisabledOnOs",
    "EnabledForJreRange",
    "EnabledIf",
    "EnabledIfEnvironmentVariable",
    "EnabledIfEnvironmentVariables",
    "EnabledIfSystemProperty",
    "EnabledIfSystemProperties",
    "EnabledInNativeImage",
    "EnabledOnJre",
    "EnabledOnOs",
)
# Runtime conditions are not reproducible static evidence. Once their JUnit
# identity is resolved, only the annotated type or method is treated as guarded.
JUNIT_EXECUTION_GUARD_ANNOTATIONS = {
    name: "org.junit.jupiter.api.Disabled"
    if name == "Disabled"
    else f"org.junit.jupiter.api.condition.{name}"
    for name in JUNIT_EXECUTION_GUARD_NAMES
}
JAVA_EXECUTION_GUARD_ANNOTATION = re.compile(
    r"@(?P<qualified>org\.junit\.jupiter\.api(?:\.condition)?\.)?"
    + r"(?P<simple>" + "|".join(JUNIT_EXECUTION_GUARD_NAMES) + r")\b"
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
JAVA_LOCAL_TAG_DECLARATION = re.compile(
    r"(?:\b(?:class|interface|record|enum)|@interface)\s+Tag\b"
)
JAVA_ANNOTATION_DECLARATION = re.compile(
    r"(?<![A-Za-z0-9_$])@interface\s+(?P<name>[A-Za-z_$][A-Za-z0-9_$]*)\s*\{"
)
JAVA_ANY_ANNOTATION = re.compile(
    r"@[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*"
)
PLAYWRIGHT_NON_PASSING = re.compile(r"\.\s*(?:skip|fixme|fail)\b")
PLAYWRIGHT_FOCUSED = re.compile(r"\.\s*only\b")
PLAYWRIGHT_COMPUTED_NON_PASSING = re.compile(
    r"\[\s*(?P<quote>['\"`])(?:skip|fixme|fail)(?P=quote)\s*\]"
)
PLAYWRIGHT_COMPUTED_FOCUSED = re.compile(
    r"\[\s*(?P<quote>['\"`])only(?P=quote)\s*\]"
)
PLAYWRIGHT_DYNAMIC_COMPUTED_CALL = re.compile(
    r"\[\s*(?!['\"`])[^\]\r\n]+\]\s*\("
)
PLAYWRIGHT_DYNAMIC_COMPUTED_ALIAS = re.compile(
    r"\b(?:const|let|var)\s+(?P<alias>[A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*"
    r"[A-Za-z_$][A-Za-z0-9_$]*\s*(?:\?\.)?\s*"
    r"\[\s*(?!['\"`])[^\]\r\n]+\]"
)
PLAYWRIGHT_REFLECT_USAGE = re.compile(r"(?<![A-Za-z0-9_$])Reflect(?![A-Za-z0-9_$])")
PLAYWRIGHT_DESTRUCTURED_CONTROL = re.compile(
    r"\b(?:const|let|var)\s*\{(?P<bindings>[^{}]*)\}\s*=\s*"
    r"(?P<receiver>[A-Za-z_$][A-Za-z0-9_$]*)"
)
PLAYWRIGHT_TEST_MATCH = re.compile(r"\btestMatch\s*:\s*/((?:\\.|[^/\r\n])*)/([a-z]*)")
PLAYWRIGHT_TEST_DIR = re.compile(r"\btestDir\s*:")
PLAYWRIGHT_UNSUPPORTED_FILTER = re.compile(
    r"(?<![.$A-Za-z0-9_])(?:testIgnore|grep|grepInvert|projects)\s*(?=:|[,}])"
)
JAVASCRIPT_NAMED_IMPORT = re.compile(
    r"(?m)^[ \t]*import[ \t]*\{(?P<bindings>[^{};]+)\}[ \t]*from"
)
JAVASCRIPT_IMPORT_BINDING = re.compile(
    r"(?:type\s+)?(?P<imported>[A-Za-z_$][A-Za-z0-9_$]*)"
    r"(?:\s+as\s+(?P<local>[A-Za-z_$][A-Za-z0-9_$]*))?\Z"
)
JAVASCRIPT_EXPORT_DEFAULT = re.compile(
    r"(?m)^[ \t]*export\s+default\s+(?P<callee>[A-Za-z_$][A-Za-z0-9_$]*)"
)
SUREFIRE_DEFAULT_TEST_TYPE = re.compile(r"(?:Test.*|.*Test|.*Tests|.*TestCase)\Z")
JAVA_METHOD_MODIFIERS = frozenset({
    "public", "protected", "private", "static", "final", "abstract",
    "synchronized", "native", "strictfp", "default",
})
JUNIT_FACTORY_NODE_TYPES = frozenset({"DynamicNode", "DynamicTest", "DynamicContainer"})
JUNIT_FACTORY_CONTAINER_TYPES = frozenset({
    "Stream", "Collection", "Iterable", "Iterator", "List", "Set", "Queue", "Deque",
})

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
    enclosing_qualified_name: str | None
    declaration_start: int
    body_start: int
    body_end: int
    depth: int
    is_member_type: bool
    is_static: bool
    is_private: bool
    is_junit_nested: bool
    is_execution_guarded: bool
    is_interface: bool
    is_abstract: bool
    has_direct_tests: bool


@dataclass(frozen=True)
class JavaSource:
    package_name: str
    explicit_imports: tuple[str, ...]
    wildcard_imports: tuple[str, ...]
    types: tuple[JavaType, ...]
    test_annotations: tuple[re.Match[str], ...]
    execution_guards: tuple[re.Match[str], ...]


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


def declares_top_level_tag(structure: str) -> bool:
    """Return whether this compilation unit declares the package-level type Tag."""
    depths = brace_depths(structure)
    return any(
        depths[match.start()] == 0
        for match in JAVA_LOCAL_TAG_DECLARATION.finditer(structure)
    )


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


def java_parent_names(header: str) -> tuple[str, ...]:
    """Read extends/implements entries, ignoring commas inside generic arguments."""
    keywords: list[tuple[str, int, int]] = []
    angle_depth = 0
    index = 0
    while index < len(header):
        character = header[index]
        if character == "<":
            angle_depth += 1
            index += 1
            continue
        if character == ">":
            angle_depth = max(0, angle_depth - 1)
            index += 1
            continue
        if angle_depth == 0 and (character.isalpha() or character in "_$"):
            end = index + 1
            while end < len(header) and (header[end].isalnum() or header[end] in "_$"):
                end += 1
            word = header[index:end]
            if word in {"extends", "implements", "permits"}:
                keywords.append((word, index, end))
            index = end
            continue
        index += 1

    parents: list[str] = []
    for keyword_index, (keyword, _, start) in enumerate(keywords):
        if keyword not in {"extends", "implements"}:
            continue
        end = keywords[keyword_index + 1][1] if keyword_index + 1 < len(keywords) else len(header)
        segment = header[start:end]
        entry_start = 0
        angle_depth = 0
        entries: list[str] = []
        for index, character in enumerate(segment):
            if character == "<":
                angle_depth += 1
            elif character == ">":
                angle_depth = max(0, angle_depth - 1)
            elif character == "," and angle_depth == 0:
                entries.append(segment[entry_start:index])
                entry_start = index + 1
        entries.append(segment[entry_start:])
        for entry in entries:
            cursor = 0
            while True:
                while cursor < len(entry) and entry[cursor].isspace():
                    cursor += 1
                if cursor >= len(entry) or entry[cursor] != "@":
                    break
                annotation = re.match(
                    r"@[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*",
                    entry[cursor:],
                )
                if annotation is None:
                    break
                cursor += annotation.end()
                while cursor < len(entry) and entry[cursor].isspace():
                    cursor += 1
                if cursor < len(entry) and entry[cursor] == "(":
                    depth = 1
                    cursor += 1
                    while cursor < len(entry) and depth:
                        if entry[cursor] == "(":
                            depth += 1
                        elif entry[cursor] == ")":
                            depth -= 1
                        cursor += 1
            parent = re.match(r"([A-Za-z_$][A-Za-z0-9_$.]*)", entry[cursor:])
            if parent is not None:
                parents.append(parent.group(1))
    return tuple(dict.fromkeys(parents))


def java_member_type_scopes(structure: str) -> dict[str, tuple[tuple[int, int], ...]]:
    """Return lexical owner bodies in which a direct member type shadows imports."""
    depths = brace_depths(structure)
    declarations: list[tuple[re.Match[str], int, int, int]] = []
    scopes: dict[str, list[tuple[int, int]]] = {}
    for declaration in JAVA_TYPE_DECLARATION.finditer(structure):
        body_start = declaration.end() - 1
        body_end = closing_brace(structure, body_start)
        depth = depths[body_start]
        owner = next(
            (
                candidate
                for candidate in reversed(declarations)
                if candidate[1] < declaration.start() < candidate[2]
                and depth == candidate[3] + 1
            ),
            None,
        )
        if owner is not None:
            scopes.setdefault(declaration.group("name"), []).append(
                (owner[1], owner[2])
            )
        declarations.append((declaration, body_start, body_end, depth))
    return {name: tuple(ranges) for name, ranges in scopes.items()}


def resolved_junit_annotations(
    structure: str,
    pattern: re.Pattern[str],
    identities: dict[str, str],
    package_type_names: frozenset[str],
    explicit_imports: tuple[str, ...],
    wildcard_imports: tuple[str, ...],
) -> tuple[re.Match[str], ...]:
    """Resolve short JUnit annotations conservatively from Java imports."""
    resolved: list[re.Match[str]] = []
    member_type_scopes = java_member_type_scopes(structure)
    for annotation in pattern.finditer(structure):
        simple = annotation.group("simple")
        expected = identities[simple]
        if annotation.group("qualified") is not None:
            if annotation.group("qualified") + simple == expected:
                resolved.append(annotation)
            continue
        if simple in package_type_names or any(
            start < annotation.start() < end
            for start, end in member_type_scopes.get(simple, ())
        ):
            continue
        explicit_matches = {
            imported
            for imported in explicit_imports
            if imported.rsplit(".", 1)[-1] == simple
        }
        if explicit_matches:
            if explicit_matches == {expected}:
                resolved.append(annotation)
            continue
        expected_package = expected.rsplit(".", 1)[0]
        if set(wildcard_imports) == {expected_package}:
            resolved.append(annotation)
    return tuple(resolved)


def resolved_junit_test_annotations(
    structure: str,
    package_type_names: frozenset[str],
    explicit_imports: tuple[str, ...],
    wildcard_imports: tuple[str, ...],
) -> tuple[re.Match[str], ...]:
    return resolved_junit_annotations(
        structure,
        JAVA_TEST_ANNOTATION,
        JUNIT_TEST_ANNOTATIONS,
        package_type_names,
        explicit_imports,
        wildcard_imports,
    )


def resolved_junit_nested_annotations(
    structure: str,
    package_type_names: frozenset[str],
    explicit_imports: tuple[str, ...],
    wildcard_imports: tuple[str, ...],
) -> tuple[re.Match[str], ...]:
    return resolved_junit_annotations(
        structure,
        JAVA_NESTED_ANNOTATION,
        JUNIT_NESTED_ANNOTATIONS,
        package_type_names,
        explicit_imports,
        wildcard_imports,
    )


def resolved_junit_execution_guards(
    structure: str,
    package_type_names: frozenset[str],
    explicit_imports: tuple[str, ...],
    wildcard_imports: tuple[str, ...],
) -> tuple[re.Match[str], ...]:
    return resolved_junit_annotations(
        structure,
        JAVA_EXECUTION_GUARD_ANNOTATION,
        JUNIT_EXECUTION_GUARD_ANNOTATIONS,
        package_type_names,
        explicit_imports,
        wildcard_imports,
    )


def java_top_level_type_names(structure: str) -> frozenset[str]:
    depths = brace_depths(structure)
    ordinary = {
        declaration.group("name")
        for declaration in JAVA_TYPE_DECLARATION.finditer(structure)
        if depths[declaration.end() - 1] == 0
    }
    annotations = {
        declaration.group("name")
        for declaration in JAVA_ANNOTATION_DECLARATION.finditer(structure)
        if depths[declaration.end() - 1] == 0
    }
    return frozenset(ordinary | annotations)


def resolve_local_annotation_reference(
    raw: str,
    package_name: str,
    explicit: tuple[str, ...],
    wildcard: tuple[str, ...],
    known: frozenset[str],
    strict_external: bool = False,
) -> tuple[str | None, bool]:
    """Resolve a local annotation, reporting identities that remain ambiguous."""
    source_names = {name.replace("$", "."): name for name in known}

    def local_identity(reference: str) -> str | None:
        return reference if reference in known else source_names.get(reference)

    if "." in raw:
        references = {raw, f"{package_name}.{raw}" if package_name else raw}
        first, remainder = raw.split(".", 1)
        references.update(
            f"{name}.{remainder}"
            for name in explicit
            if name.rsplit(".", 1)[-1] == first
        )
        references.update(f"{name}.{raw}" for name in wildcard)
        candidates = {
            resolved for reference in references
            if (resolved := local_identity(reference)) is not None
        }
        if len(candidates) == 1:
            return next(iter(candidates)), False
        if len(candidates) > 1:
            return None, True
        external = next(
            (reference for reference in references if reference.startswith(("java.", "org.junit."))),
            None,
        )
        return None, strict_external and external is None
    explicit_matches = {name for name in explicit if name.rsplit(".", 1)[-1] == raw}
    if len(explicit_matches) > 1:
        return None, True
    candidates = {
        resolved for name in explicit_matches
        if (resolved := local_identity(name)) is not None
    }
    same_package = f"{package_name}.{raw}" if package_name else raw
    if (same := local_identity(same_package)) is not None:
        candidates.add(same)
    candidates.update(
        resolved
        for imported_package in wildcard
        if (resolved := local_identity(f"{imported_package}.{raw}")) is not None
    )
    if len(candidates) == 1:
        if explicit_matches and next(iter(explicit_matches)) not in source_names:
            return None, True
        return next(iter(candidates)), False
    known_simple = {name.rsplit(".", 1)[-1] for name in known}
    if len(candidates) > 1 or raw in known_simple:
        return None, True
    if raw in {
        "Deprecated", "Documented", "FunctionalInterface", "Inherited",
        "Override", "Repeatable", "Retention", "SafeVarargs",
        "SuppressWarnings", "Target",
    }:
        return None, False
    if explicit_matches:
        imported = next(iter(explicit_matches))
        return None, strict_external and not imported.startswith(("java.", "org.junit."))
    if len(wildcard) == 1:
        return None, strict_external and not wildcard[0].startswith(("java.", "org.junit."))
    return None, True


def classify_local_junit_annotations(
    structures: list[str],
    package_types: dict[str, frozenset[str]],
) -> tuple[dict[str, str], frozenset[str]]:
    """Classify local annotations as safe, guarded, or unresolved."""
    declarations: dict[str, list[tuple[str, re.Match[str], str, tuple[str, ...], tuple[str, ...]]]] = {}
    for structure in structures:
        package = JAVA_PACKAGE.search(structure)
        package_name = package.group(1) if package is not None else ""
        imports = tuple(JAVA_IMPORT.findall(structure))
        explicit = tuple(name for name in imports if not name.endswith(".*"))
        wildcard = tuple(name[:-2] for name in imports if name.endswith(".*"))
        parsed_types = java_types(structure, package_name)
        for declaration in JAVA_ANNOTATION_DECLARATION.finditer(structure):
            declared_type = next(
                (java_type for java_type in parsed_types
                 if java_type.body_start == declaration.end() - 1),
                None,
            )
            if declared_type is None:
                continue
            declarations.setdefault(declared_type.qualified_name, []).append(
                (structure, declaration, package_name, explicit, wildcard)
            )

    known = frozenset(declarations)
    dependencies: dict[str, set[str]] = {name: set() for name in known}
    directly_guarded: set[str] = set()
    unresolved: set[str] = {
        name for name, entries in declarations.items() if len(entries) != 1
    }
    for qualified, entries in declarations.items():
        if qualified in unresolved:
            continue
        structure, declaration, package_name, explicit, wildcard = entries[0]
        direct_guards = {
            match.start()
            for match in resolved_junit_execution_guards(
                structure,
                package_types.get(package_name, frozenset()),
                explicit,
                wildcard,
            )
        }
        for annotation in JAVA_ANY_ANNOTATION.finditer(structure):
            if not java_annotation_attaches_to_type(structure, annotation, declaration):
                continue
            if annotation.start() in direct_guards:
                directly_guarded.add(qualified)
                continue
            dependency, uncertain = resolve_local_annotation_reference(
                annotation.group()[1:], package_name, explicit, wildcard, known, True
            )
            if dependency is not None:
                dependencies[qualified].add(dependency)
            elif uncertain:
                unresolved.add(qualified)

    statuses: dict[str, str] = {}
    visiting: set[str] = set()

    def status(name: str) -> str:
        if name in statuses:
            return statuses[name]
        if name in visiting or name in unresolved:
            return "unresolved"
        if name in directly_guarded:
            statuses[name] = "guarded"
            return "guarded"
        visiting.add(name)
        child_statuses = {status(child) for child in dependencies[name]}
        visiting.remove(name)
        resolved = (
            "guarded" if "guarded" in child_statuses
            else "unresolved" if "unresolved" in child_statuses
            else "safe"
        )
        statuses[name] = resolved
        return resolved

    for name in known:
        statuses[name] = status(name)
    return statuses, known


def resolved_local_execution_guards(
    structure: str,
    statuses: dict[str, str],
    known: frozenset[str],
) -> tuple[re.Match[str], ...]:
    """Return uses of guarded or unresolved local composed annotations."""
    package = JAVA_PACKAGE.search(structure)
    package_name = package.group(1) if package is not None else ""
    imports = tuple(JAVA_IMPORT.findall(structure))
    explicit = tuple(name for name in imports if not name.endswith(".*"))
    wildcard = tuple(name[:-2] for name in imports if name.endswith(".*"))
    guarded: list[re.Match[str]] = []
    for annotation in JAVA_ANY_ANNOTATION.finditer(structure):
        raw = annotation.group()[1:]
        if raw == "interface":
            continue
        resolved, uncertain = resolve_local_annotation_reference(
            raw, package_name, explicit, wildcard, known
        )
        if uncertain or (resolved is not None and statuses.get(resolved) != "safe"):
            guarded.append(annotation)
    return tuple(guarded)


def java_annotation_end(structure: str, annotation: re.Match[str]) -> int:
    cursor = annotation.end()
    while cursor < len(structure) and structure[cursor].isspace():
        cursor += 1
    if cursor >= len(structure) or structure[cursor] != "(":
        return annotation.end()
    depth = 1
    cursor += 1
    while cursor < len(structure) and depth:
        if structure[cursor] == "(":
            depth += 1
        elif structure[cursor] == ")":
            depth -= 1
        cursor += 1
    return cursor


def java_annotations_share_declaration(
    structure: str,
    left: re.Match[str],
    right: re.Match[str],
) -> bool:
    first, second = sorted((left, right), key=lambda annotation: annotation.start())
    return not any(
        separator in structure[java_annotation_end(structure, first):second.start()]
        for separator in ";{}"
    )


def java_annotation_attaches_to_type(
    structure: str,
    annotation: re.Match[str],
    declaration: re.Match[str],
) -> bool:
    return annotation.start() < declaration.start() and not any(
        separator
        in structure[java_annotation_end(structure, annotation):declaration.start()]
        for separator in ";{}"
    )


def active_junit_test_annotations(
    structure: str,
    test_annotations: tuple[re.Match[str], ...],
    execution_guards: tuple[re.Match[str], ...],
) -> tuple[re.Match[str], ...]:
    depths = brace_depths(structure)
    return tuple(
        annotation
        for annotation in test_annotations
        if not any(
            depths[guard.start()] == depths[annotation.start()]
            and java_annotations_share_declaration(structure, guard, annotation)
            for guard in execution_guards
        )
    )


def java_declaration_after_annotation(
    structure: str,
    annotation: re.Match[str],
) -> tuple[str, str] | None:
    """Read the declaration header and terminator following an annotation cluster."""
    cursor = java_annotation_end(structure, annotation)
    while True:
        while cursor < len(structure) and structure[cursor].isspace():
            cursor += 1
        following = JAVA_ANY_ANNOTATION.match(structure, cursor)
        if following is None:
            break
        cursor = java_annotation_end(structure, following)

    start = cursor
    parenthesis_depth = 0
    while cursor < len(structure):
        character = structure[cursor]
        if character == "(":
            parenthesis_depth += 1
        elif character == ")":
            parenthesis_depth = max(0, parenthesis_depth - 1)
        elif character in "{};" and parenthesis_depth == 0:
            if character == "}":
                return None
            return structure[start:cursor].strip(), character
        cursor += 1
    return None


def java_leading_type_parameters_end(prefix: str) -> int | None:
    if not prefix.startswith("<"):
        return 0
    depth = 0
    for index, character in enumerate(prefix):
        if character == "<":
            depth += 1
        elif character == ">":
            depth -= 1
            if depth == 0:
                return index + 1
    return None


def junit_factory_return_type_is_supported(return_type: str) -> bool:
    """Accept statically recognisable TestFactory node/container return shapes."""
    compact = re.sub(r"\s+", "", return_type)
    if not compact or "@" in compact:
        return False
    if compact.endswith("[]"):
        component = compact[:-2].rsplit(".", 1)[-1]
        return component in JUNIT_FACTORY_NODE_TYPES
    outer = compact.split("<", 1)[0].rsplit(".", 1)[-1]
    if outer in JUNIT_FACTORY_NODE_TYPES:
        return "<" not in compact
    if outer not in JUNIT_FACTORY_CONTAINER_TYPES or "<" not in compact:
        return False
    arguments = compact[compact.find("<") + 1:compact.rfind(">")]
    return re.fullmatch(
        r"(?:\?extends)?(?:[A-Za-z_$][A-Za-z0-9_$]*\.)*"
        r"(?:DynamicNode|DynamicTest|DynamicContainer)",
        arguments,
    ) is not None


def junit_method_is_executable(
    structure: str,
    annotation: re.Match[str],
    owner: JavaType,
) -> bool:
    declaration = java_declaration_after_annotation(structure, annotation)
    if declaration is None:
        return False
    header, terminator = declaration
    if terminator != "{" or "=" in header:
        return False
    method = re.fullmatch(
        r"(?s)(?P<prefix>.*?)\b(?P<name>[A-Za-z_$][A-Za-z0-9_$]*)\s*"
        r"\((?P<parameters>.*)\)\s*(?:throws\s+[A-Za-z0-9_$.,<>?\s]+)?",
        header,
    )
    if method is None:
        return False

    prefix = method.group("prefix").strip()
    modifiers: set[str] = set()
    while True:
        modifier = re.match(r"([A-Za-z_$][A-Za-z0-9_$]*)\b", prefix)
        if modifier is None or modifier.group(1) not in JAVA_METHOD_MODIFIERS:
            break
        modifiers.add(modifier.group(1))
        prefix = prefix[modifier.end():].lstrip()
    type_parameters_end = java_leading_type_parameters_end(prefix)
    if type_parameters_end is None:
        return False
    prefix = prefix[type_parameters_end:].strip()
    if not prefix or "@" in prefix:
        return False
    if modifiers & {"private", "static", "abstract", "native"}:
        return False
    if owner.is_interface and "default" not in modifiers:
        return False

    annotation_name = annotation.group("simple")
    if annotation_name == "TestFactory":
        return junit_factory_return_type_is_supported(prefix)
    return re.sub(r"\s+", "", prefix) == "void"


def executable_junit_test_annotations(
    structure: str,
    types: tuple[JavaType, ...],
    test_annotations: tuple[re.Match[str], ...],
    execution_guards: tuple[re.Match[str], ...],
) -> tuple[re.Match[str], ...]:
    depths = brace_depths(structure)
    active = active_junit_test_annotations(structure, test_annotations, execution_guards)
    executable: list[re.Match[str]] = []
    for annotation in active:
        owner = next(
            (
                java_type
                for java_type in reversed(types)
                if java_type.body_start < annotation.start() < java_type.body_end
                and depths[annotation.start()] == java_type.depth + 1
            ),
            None,
        )
        if owner is not None and junit_method_is_executable(structure, annotation, owner):
            executable.append(annotation)
    return tuple(executable)


def java_types(
    structure: str,
    package_name: str = "",
    test_annotations: tuple[re.Match[str], ...] = (),
    nested_annotations: tuple[re.Match[str], ...] = (),
    execution_guards: tuple[re.Match[str], ...] = (),
) -> tuple[JavaType, ...]:
    depths = brace_depths(structure)
    types: list[JavaType] = []
    for declaration in JAVA_TYPE_DECLARATION.finditer(structure):
        body_start = declaration.end() - 1
        body_end = closing_brace(structure, body_start)
        depth = depths[body_start]
        header = declaration.group("header")
        parents = java_parent_names(header)
        enclosing = next(
            (
                java_type
                for java_type in reversed(types)
                if java_type.body_start < declaration.start() < java_type.body_end
            ),
            None,
        )
        is_member_type = enclosing is not None and depth == enclosing.depth + 1
        enclosing_name = enclosing.qualified_name if enclosing is not None else None
        name = declaration.group("name")
        qualified_name = (
            f"{enclosing_name}${name}"
            if enclosing_name is not None
            else (f"{package_name}.{name}" if package_name else name)
        )
        type_guards = tuple(
            guard
            for guard in execution_guards
            if depths[guard.start()] == depth
            and java_annotation_attaches_to_type(structure, guard, declaration)
        )
        modifiers = declaration.group("modifiers").split()
        types.append(JavaType(
            name=name,
            qualified_name=qualified_name,
            parents=parents,
            enclosing_qualified_name=enclosing_name,
            declaration_start=declaration.start(),
            body_start=body_start,
            body_end=body_end,
            depth=depth,
            is_member_type=is_member_type,
            is_static="static" in modifiers,
            is_private="private" in modifiers,
            is_junit_nested=any(
                depths[annotation.start()] == depth
                and java_annotation_attaches_to_type(structure, annotation, declaration)
                for annotation in nested_annotations
            ),
            is_execution_guarded=bool(type_guards),
            is_interface=declaration.group("kind") == "interface",
            is_abstract=declaration.group("kind") == "interface" or "abstract" in modifiers,
            has_direct_tests=False,
        ))
    parsed_types = tuple(types)
    executable_annotations = executable_junit_test_annotations(
        structure,
        parsed_types,
        test_annotations,
        execution_guards,
    )
    return tuple(
        replace(
            java_type,
            has_direct_tests=any(
                java_type.body_start < annotation.start() < java_type.body_end
                and depths[annotation.start()] == java_type.depth + 1
                for annotation in executable_annotations
            ),
        )
        for java_type in parsed_types
    )


def java_source(
    structure: str,
    package_type_names: frozenset[str] | None = None,
    local_annotation_statuses: dict[str, str] | None = None,
    local_annotation_names: frozenset[str] = frozenset(),
) -> JavaSource:
    package = JAVA_PACKAGE.search(structure)
    package_name = package.group(1) if package is not None else ""
    imports = tuple(JAVA_IMPORT.findall(structure))
    explicit_imports = tuple(
        import_name for import_name in imports if not import_name.endswith(".*")
    )
    wildcard_imports = tuple(
        import_name[:-2] for import_name in imports if import_name.endswith(".*")
    )
    if package_type_names is None:
        package_type_names = java_top_level_type_names(structure)
    test_annotations = resolved_junit_test_annotations(
        structure,
        package_type_names,
        explicit_imports,
        wildcard_imports,
    )
    nested_annotations = resolved_junit_nested_annotations(
        structure,
        package_type_names,
        explicit_imports,
        wildcard_imports,
    )
    execution_guards = resolved_junit_execution_guards(
        structure,
        package_type_names,
        explicit_imports,
        wildcard_imports,
    )
    if local_annotation_statuses is not None:
        execution_guards += resolved_local_execution_guards(
            structure,
            local_annotation_statuses,
            local_annotation_names,
        )
    return JavaSource(
        package_name,
        explicit_imports,
        wildcard_imports,
        java_types(
            structure,
            package_name,
            test_annotations,
            nested_annotations,
            execution_guards,
        ),
        test_annotations,
        execution_guards,
    )


def java_package_types(structures: list[str]) -> dict[str, frozenset[str]]:
    packages: dict[str, set[str]] = {}
    for structure in structures:
        package = JAVA_PACKAGE.search(structure)
        package_name = package.group(1) if package is not None else ""
        packages.setdefault(package_name, set()).update(java_top_level_type_names(structure))
    return {package: frozenset(names) for package, names in packages.items()}


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


def discoverable_java_types(
    structures: list[str],
    package_types: dict[str, frozenset[str]] | None = None,
    local_annotation_statuses: dict[str, str] | None = None,
    local_annotation_names: frozenset[str] = frozenset(),
) -> frozenset[str]:
    if package_types is None:
        package_types = java_package_types(structures)
    sources = tuple(
        java_source(
            structure,
            package_types.get(
                (package.group(1) if (package := JAVA_PACKAGE.search(structure)) else ""),
                frozenset(),
            ),
            local_annotation_statuses,
            local_annotation_names,
        )
        for structure in structures
    )
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
    effectively_guarded = {
        name
        for name, (_, java_type) in by_name.items()
        if java_type.is_execution_guarded
    }
    changed = True
    while changed:
        changed = False
        for name, (_, java_type) in by_name.items():
            if name in effectively_guarded:
                continue
            if (
                any(parent in effectively_guarded for parent in parents[name])
                or java_type.enclosing_qualified_name in effectively_guarded
            ):
                effectively_guarded.add(name)
                changed = True
    inherited_tests = {
        name
        for name, (_, java_type) in by_name.items()
        if java_type.has_direct_tests and name not in effectively_guarded
    }
    changed = True
    while changed:
        changed = False
        for name, (_, java_type) in by_name.items():
            if (
                name not in effectively_guarded
                and name not in inherited_tests
                and any(
                    parent in inherited_tests for parent in parents[name]
                )
            ):
                inherited_tests.add(name)
                changed = True

    eligible_nested = {
        name
        for name, (_, java_type) in by_name.items()
        if java_type.is_member_type
        and java_type.enclosing_qualified_name is not None
        and java_type.is_junit_nested
        and not java_type.is_static
        and not java_type.is_private
        and not java_type.is_abstract
        and name not in effectively_guarded
    }
    runnable_content = set(inherited_tests)
    changed = True
    while changed:
        changed = False
        for name in eligible_nested:
            enclosing = by_name[name][1].enclosing_qualified_name
            if name in runnable_content and enclosing not in runnable_content:
                runnable_content.add(enclosing)
                changed = True

    executable = {
        name
        for name, (_, java_type) in by_name.items()
        if java_type.depth == 0
        and java_type.enclosing_qualified_name is None
        and not java_type.is_abstract
        and name not in effectively_guarded
        and name in runnable_content
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
            if (
                parent in by_name
                and parent not in effectively_guarded
                and parent not in discoverable
            ):
                discoverable.add(parent)
                frontier.append(parent)
        for child in eligible_nested:
            child_type = by_name[child][1]
            if (
                child_type.enclosing_qualified_name == java_type.qualified_name
                and child in runnable_content
                and child not in discoverable
            ):
                discoverable.add(child)
                frontier.append(child)
    return frozenset(discoverable)


def java_tag_occurrences(
    text: str,
    structure: str,
    shadowed_tag_packages: frozenset[str],
) -> tuple[tuple[str, int, int], ...]:
    occurrences: list[tuple[str, int, int]] = []
    source = java_source(structure)
    member_type_scopes = java_member_type_scopes(structure)
    for annotation in JAVA_TAG_ANNOTATION.finditer(structure):
        if annotation.group("qualified") is None:
            if source.package_name in shadowed_tag_packages or any(
                start < annotation.start() < end
                for start, end in member_type_scopes.get("Tag", ())
            ):
                continue
            explicit_tags = {
                imported
                for imported in source.explicit_imports
                if imported.rsplit(".", 1)[-1] == "Tag"
            }
            if explicit_tags:
                if explicit_tags != {"org.junit.jupiter.api.Tag"}:
                    continue
            elif set(source.wildcard_imports) != {"org.junit.jupiter.api"}:
                continue
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
    shadowed_tag_packages: frozenset[str],
    package_type_names: frozenset[str],
    local_annotation_statuses: dict[str, str] | None = None,
    local_annotation_names: frozenset[str] = frozenset(),
) -> frozenset[str]:
    """Keep tags attached to executable test methods or discoverable test types."""
    depths = brace_depths(structure)
    source = java_source(
        structure,
        package_type_names,
        local_annotation_statuses,
        local_annotation_names,
    )
    types = source.types
    executable_test_annotations = executable_junit_test_annotations(
        structure,
        types,
        source.test_annotations,
        source.execution_guards,
    )
    markers: set[str] = set()
    for marker, start, end in java_tag_occurrences(text, structure, shadowed_tag_packages):
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
            for annotation in executable_test_annotations
        ):
            markers.add(marker)
    return frozenset(markers)


def javascript_named_imports(text: str) -> dict[str, frozenset[tuple[str, str]]]:
    """Resolve local named-import bindings without trusting quoted lookalikes."""
    structure = source_without_quoted_text(text)
    depths = brace_depths(structure)
    resolved: dict[str, set[tuple[str, str]]] = {}
    for statement in JAVASCRIPT_NAMED_IMPORT.finditer(structure):
        if depths[statement.start()] != 0:
            continue
        cursor = statement.end()
        while cursor < len(text) and text[cursor].isspace():
            cursor += 1
        if cursor >= len(text) or text[cursor] not in ('"', "'"):
            continue
        module, _ = quoted_literal(text, cursor)
        if module is None:
            continue
        for raw_binding in statement.group("bindings").split(","):
            binding = JAVASCRIPT_IMPORT_BINDING.fullmatch(raw_binding.strip())
            if binding is None or raw_binding.strip().startswith("type "):
                continue
            imported = binding.group("imported")
            local = binding.group("local") or imported
            resolved.setdefault(local, set()).add((module, imported))
    return {local: frozenset(origins) for local, origins in resolved.items()}


def playwright_imported_identifiers(text: str, imported: str) -> frozenset[str]:
    """Return unambiguous local names imported from the Playwright test package."""
    return frozenset(
        local
        for local, origins in javascript_named_imports(text).items()
        if origins == frozenset({("@playwright/test", imported)})
    )


def playwright_has_computed_control(
    text: str,
    structure: str,
    pattern: re.Pattern[str],
) -> bool:
    """Recognise quoted computed members only when their bracket is executable code."""
    return any(structure[match.start()] == "[" for match in pattern.finditer(text))


def playwright_has_ambiguous_test_member(text: str, structure: str) -> bool:
    """Reject any non-literal computed member on an imported Playwright test binding."""
    identifiers = playwright_imported_identifiers(text, "test")
    if not identifiers:
        return False
    pattern = re.compile(
        r"(?<![A-Za-z0-9_$])(?:"
        + "|".join(re.escape(identifier) for identifier in sorted(identifiers))
        + r")(?![A-Za-z0-9_$])\s*(?:\?\.)?\s*\["
    )
    return any(structure[match.end() - 1] == "[" for match in pattern.finditer(structure))


def playwright_has_destructured_control(
    structure: str,
    controls: frozenset[str],
    receivers: frozenset[str] | None = None,
) -> bool:
    """Reject aliased execution controls whose binding cannot be followed soundly."""
    for declaration in PLAYWRIGHT_DESTRUCTURED_CONTROL.finditer(structure):
        if receivers is not None and declaration.group("receiver") not in receivers:
            continue
        names = {
            name
            for binding in declaration.group("bindings").split(",")
            if (name := binding.split(":", 1)[0].strip())
        }
        if names & controls:
            return True
    return False


def playwright_has_dynamic_destructuring(
    structure: str,
    receivers: frozenset[str] | None = None,
) -> bool:
    """Detect computed property destructuring whose selected member is unknowable."""
    return any(
        "[" in declaration.group("bindings")
        and (
            receivers is None
            or declaration.group("receiver") in receivers
        )
        for declaration in PLAYWRIGHT_DESTRUCTURED_CONTROL.finditer(structure)
    )


def playwright_describe_callback_brace(
    structure: str,
    brace: int,
    test_identifiers: frozenset[str],
) -> bool:
    boundary = max(
        structure.rfind("{", 0, brace),
        structure.rfind("}", 0, brace),
        structure.rfind(";", 0, brace),
    )
    prefix = structure[boundary + 1:brace]
    identifiers = "|".join(re.escape(identifier) for identifier in test_identifiers)
    if not identifiers:
        return False
    return re.search(
        rf"\b(?:{identifiers})\s*\.\s*describe\s*\([^{{}};]*,\s*"
        r"(?:async\s*)?\([^{}]*\)\s*=>\s*\Z",
        prefix,
        re.DOTALL,
    ) is not None


def playwright_registration_scopes(
    structure: str,
    test_identifiers: frozenset[str],
) -> tuple[bool, ...]:
    scopes: list[bool] = []
    allowed_stack: list[bool] = []
    for index, character in enumerate(structure):
        scopes.append(all(allowed_stack))
        if character == "{":
            allowed_stack.append(
                playwright_describe_callback_brace(structure, index, test_identifiers)
            )
        elif character == "}" and allowed_stack:
            allowed_stack.pop()
    return tuple(scopes)


def matching_open_parenthesis(structure: str, closing: int) -> int | None:
    depth = 1
    for index in range(closing - 1, -1, -1):
        if structure[index] == ")":
            depth += 1
        elif structure[index] == "(":
            depth -= 1
            if depth == 0:
                return index
    return None


def playwright_has_control_prefix(structure: str, start: int) -> bool:
    previous = start - 1
    while previous >= 0 and structure[previous].isspace():
        previous -= 1
    if previous < 0:
        return False
    if structure[max(0, previous - 1):previous + 1] in {"=>", "&&", "||"}:
        return True
    if structure[previous] in "?:":
        return True
    if structure[previous] == ")":
        opening = matching_open_parenthesis(structure, previous)
        if opening is not None:
            word_end = opening
            word_start = word_end
            while word_start > 0 and structure[word_start - 1].isspace():
                word_start -= 1
                word_end = word_start
            while word_start > 0 and (
                structure[word_start - 1].isalnum() or structure[word_start - 1] in "_$"
            ):
                word_start -= 1
            if structure[word_start:word_end] in {"if", "for", "while", "with", "switch", "catch"}:
                return True
    word_end = previous + 1
    word_start = word_end
    while word_start > 0 and (
        structure[word_start - 1].isalnum() or structure[word_start - 1] in "_$"
    ):
        word_start -= 1
    return structure[word_start:word_end] in {"else", "do"}


def playwright_test_titles(text: str) -> tuple[str, ...]:
    """Extract titles from imported Playwright ``test`` bindings only."""
    titles: list[str] = []
    structure = source_without_quoted_text(text)
    test_identifiers = playwright_imported_identifiers(text, "test")
    if not test_identifiers:
        return tuple()
    registration_scopes = playwright_registration_scopes(structure, test_identifiers)
    calls = re.compile(
        r"(?<![A-Za-z0-9_$])(?:"
        + "|".join(re.escape(identifier) for identifier in sorted(test_identifiers))
        + r")(?![A-Za-z0-9_$])"
    )
    for call in calls.finditer(structure):
        index = call.start()
        after_index = call.end()

        previous = index - 1
        while previous >= 0 and structure[previous].isspace():
            previous -= 1
        if previous >= 0 and structure[previous] == ".":
            continue

        # Direct top-level and test.describe callback registrations are trustworthy.
        # Calls under any other brace/control-flow context are rejected conservatively.
        line_start = max(structure.rfind("\n", 0, index), structure.rfind("\r", 0, index)) + 1
        if (
            not registration_scopes[index]
            or structure[line_start:index].strip()
            or playwright_has_control_prefix(structure, index)
        ):
            continue

        cursor = after_index
        while cursor < len(text) and text[cursor].isspace():
            cursor += 1
        if cursor >= len(text) or text[cursor] != "(":
            continue
        cursor += 1
        while cursor < len(text) and text[cursor].isspace():
            cursor += 1
        if cursor >= len(text) or text[cursor] not in ('"', "'"):
            continue

        title, _ = quoted_literal(text, cursor)
        if title is not None:
            titles.append(title)

    return tuple(titles)


def exported_playwright_config(config: str) -> tuple[str, str]:
    """Return the sole object directly exported through imported ``defineConfig``."""
    syntax = source_without_javascript_regex_literals(config)
    structure = source_without_quoted_text(syntax)
    depths = brace_depths(structure)
    define_config_names = playwright_imported_identifiers(config, "defineConfig")
    exports = [
        export
        for export in JAVASCRIPT_EXPORT_DEFAULT.finditer(structure)
        if depths[export.start()] == 0
    ]
    if len(exports) != 1:
        raise ValueError(
            "playwright.config.ts must have one unambiguous default defineConfig export"
        )
    export = exports[0]
    if export.group("callee") not in define_config_names:
        raise ValueError(
            "playwright.config.ts default export must call defineConfig imported from @playwright/test"
        )
    cursor = export.end()
    while cursor < len(structure) and structure[cursor].isspace():
        cursor += 1
    if cursor >= len(structure) or structure[cursor] != "(":
        raise ValueError("playwright.config.ts default export must call defineConfig")
    cursor += 1
    while cursor < len(structure) and structure[cursor].isspace():
        cursor += 1
    if cursor >= len(structure) or structure[cursor] != "{":
        raise ValueError("playwright.config.ts defineConfig argument must be an object literal")
    end = closing_brace(structure, cursor)
    if end >= len(structure):
        raise ValueError("playwright.config.ts defineConfig object must be closed")
    after = end + 1
    while after < len(structure) and structure[after].isspace():
        after += 1
    if after >= len(structure) or structure[after] != ")":
        raise ValueError("playwright.config.ts defineConfig must have one object argument")
    exported_object = config[cursor:end + 1]
    return exported_object, source_without_quoted_text(exported_object)


def object_level_matches(pattern: re.Pattern[str], structure: str) -> tuple[re.Match[str], ...]:
    """Find properties that belong directly to the selected object literal."""
    depths = brace_depths(structure)
    return tuple(match for match in pattern.finditer(structure) if depths[match.start()] == 1)


def configured_playwright_tests(root: Path) -> tuple[Path, ...]:
    config = source_without_comments((root / PLAYWRIGHT_CONFIG).read_text(encoding="utf-8"))
    config, structure = exported_playwright_config(config)
    unsupported_filters = object_level_matches(PLAYWRIGHT_UNSUPPORTED_FILTER, structure)
    if unsupported_filters:
        unsupported_filter = unsupported_filters[0]
        option = re.match(r"[A-Za-z]+", unsupported_filter.group(0)).group(0)
        raise ValueError(
            f"unsupported Playwright evidence filter {option}; refusing partial discovery"
        )
    configured_matches = object_level_matches(PLAYWRIGHT_TEST_MATCH, structure)
    if len(configured_matches) != 1:
        raise ValueError("playwright.config.ts must declare one regex testMatch")
    configured = configured_matches[0]
    configured_dirs = object_level_matches(PLAYWRIGHT_TEST_DIR, structure)
    if len(configured_dirs) != 1:
        raise ValueError("playwright.config.ts must declare one string testDir")
    configured_dir = configured_dirs[0]
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
    selected = tuple(
        path
        for path in sorted(test_root.rglob("*.ts"))
        if test_match.search(str(path.resolve()))
        or (
            os.sep == "\\"
            and test_match.search(str(path.resolve()).replace("\\", "/"))
        )
    )
    for path in selected:
        text = source_without_comments(path.read_text(encoding="utf-8"))
        text = source_without_javascript_regex_literals(text)
        structure = source_without_quoted_text(text)
        test_identifiers = playwright_imported_identifiers(text, "test")
        if (
            PLAYWRIGHT_FOCUSED.search(structure)
            or playwright_has_computed_control(
                text,
                structure,
                PLAYWRIGHT_COMPUTED_FOCUSED,
            )
            or playwright_has_destructured_control(
                structure,
                frozenset({"only"}),
                test_identifiers,
            )
            or playwright_has_ambiguous_test_member(text, structure)
            or playwright_has_dynamic_destructuring(structure, test_identifiers)
            or PLAYWRIGHT_REFLECT_USAGE.search(structure)
        ):
            raise ValueError(
                "focused Playwright .only call prevents complete evidence discovery"
            )
    return selected


def evidence_markers(
    path: Path,
    java_discoverable_types: frozenset[str] | None = None,
    java_shadowed_tag_packages: frozenset[str] | None = None,
    java_package_type_names: frozenset[str] | None = None,
    java_local_annotation_statuses: dict[str, str] | None = None,
    java_local_annotation_names: frozenset[str] | None = None,
) -> frozenset[str]:
    text = source_without_comments(path.read_text(encoding="utf-8"))
    if path.suffix == ".java":
        structure = source_without_quoted_text(text)
        package = JAVA_PACKAGE.search(structure)
        package_name = package.group(1) if package is not None else ""
        package_type_names = java_package_type_names
        if package_type_names is None:
            package_type_names = java_top_level_type_names(structure)
        local_annotation_statuses = java_local_annotation_statuses
        local_annotation_names = java_local_annotation_names
        if local_annotation_statuses is None or local_annotation_names is None:
            local_annotation_statuses, local_annotation_names = (
                classify_local_junit_annotations(
                    [structure], {package_name: package_type_names}
                )
            )
        discoverable_types = java_discoverable_types
        if discoverable_types is None:
            discoverable_types = discoverable_java_types(
                [structure],
                {package_name: package_type_names},
                local_annotation_statuses,
                local_annotation_names,
            )
        shadowed_tag_packages = java_shadowed_tag_packages
        if shadowed_tag_packages is None:
            source = java_source(structure)
            shadowed_tag_packages = (
                frozenset({source.package_name})
                if declares_top_level_tag(structure)
                else frozenset()
            )
        return java_tag_markers(
            text,
            structure,
            discoverable_types,
            shadowed_tag_packages,
            package_type_names,
            local_annotation_statuses,
            local_annotation_names,
        )
    if path.suffix == ".ts":
        text = source_without_javascript_regex_literals(text)
        structure = source_without_quoted_text(text)
        # Any member skip/fixme/fail call can make a test non-passing dynamically or
        # disable an enclosing suite. A mixed file supplies no evidence until split.
        if (
            PLAYWRIGHT_NON_PASSING.search(structure)
            or playwright_has_computed_control(
                text,
                structure,
                PLAYWRIGHT_COMPUTED_NON_PASSING,
            )
            or PLAYWRIGHT_DYNAMIC_COMPUTED_CALL.search(structure)
            or PLAYWRIGHT_DYNAMIC_COMPUTED_ALIAS.search(structure)
            or playwright_has_destructured_control(
                structure,
                frozenset({"skip", "fixme", "fail"}),
            )
            or playwright_has_dynamic_destructuring(structure)
            or PLAYWRIGHT_REFLECT_USAGE.search(structure)
            or playwright_has_ambiguous_test_member(text, structure)
        ):
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
    package_types = java_package_types(java_structures)
    local_annotation_statuses, local_annotation_names = (
        classify_local_junit_annotations(java_structures, package_types)
    )
    discoverable_types = discoverable_java_types(
        java_structures,
        package_types,
        local_annotation_statuses,
        local_annotation_names,
    )
    shadowed_tag_packages = frozenset(
        java_source(structure).package_name
        for structure in java_structures
        if declares_top_level_tag(structure)
    )
    for path, structure in zip(java_paths, java_structures, strict=True):
        package = JAVA_PACKAGE.search(structure)
        package_name = package.group(1) if package is not None else ""
        for marker in evidence_markers(
            path,
            discoverable_types,
            shadowed_tag_packages,
            package_types.get(package_name, frozenset()),
            local_annotation_statuses,
            local_annotation_names,
        ):
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
