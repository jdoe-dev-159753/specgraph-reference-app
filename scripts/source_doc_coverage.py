#!/usr/bin/env python3
"""Ratchet semantic source documentation across every maintained Java source."""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path


TYPE = re.compile(
    r"(?m)^\s*(?:(?:public|protected|private|abstract|final|static|non-sealed|sealed|strictfp)\s+)*"
    r"(?:@interface|class|enum|interface|record)\s+([A-Za-z_$][\w$]*)\b"
)
PACKAGE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.MULTILINE)
PACKAGE_DOC = re.compile(r"(?s)^\s*/\*\*.*?\*/\s*package\s+[\w.]+\s*;")
PRECEDING_JAVADOC = re.compile(r"(?s)/\*\*.*?\*/\s*(?:@[^\r\n]+\s*)*$")
JAVADOC = re.compile(r"/\*\*.*?\*/", re.DOTALL)
TOKEN = re.compile(r"[A-Za-z_$][\w$]*")
CONTROL_NAMES = {"catch", "do", "else", "for", "if", "new", "switch", "synchronized", "try", "while"}
ROOTS = (
    Path("backend/src/main/java"),
    Path("backend/src/test/java"),
    Path("e2e/fixtures/backend"),
)


@dataclass(frozen=True)
class TypeDeclaration:
    source: Path
    line: int
    name: str
    documented: bool


@dataclass(frozen=True)
class ExecutableDeclaration:
    source: Path
    line: int
    name: str
    body_lines: int
    decisions: int
    documented: bool
    descriptive_test: bool

    @property
    def nontrivial(self) -> bool:
        """Keep the threshold mechanical; semantic comments remain a human-owned judgment."""
        return self.body_lines >= 12 or self.decisions >= 2

    @property
    def justified(self) -> bool:
        return self.documented or self.descriptive_test or not self.nontrivial


@dataclass(frozen=True)
class Coverage:
    sources: tuple[Path, ...]
    types: tuple[TypeDeclaration, ...]
    executables: tuple[ExecutableDeclaration, ...]
    packages: tuple[str, ...]
    missing_package_docs: tuple[str, ...]


def inspect_types(source: Path) -> list[TypeDeclaration]:
    """Inventory top-level and nested named types, regardless of visibility."""
    text = source.read_text(encoding="utf-8")
    return [
        TypeDeclaration(
            source,
            text.count("\n", 0, match.start()) + 1,
            match.group(1),
            bool(PRECEDING_JAVADOC.search(text[: match.start()])),
        )
        for match in TYPE.finditer(text)
    ]


def _mask_literals_and_comments(text: str) -> str:
    """Blank lexical noise while preserving offsets and line endings for the brace inventory."""
    masked = list(text)
    index = 0
    while index < len(text):
        if text.startswith('"""', index):
            end = text.find('"""', index + 3)
            end = len(text) - 3 if end < 0 else end
            stop = min(len(text), end + 3)
        elif text.startswith("//", index):
            end = text.find("\n", index + 2)
            stop = len(text) if end < 0 else end
        elif text.startswith("/*", index):
            end = text.find("*/", index + 2)
            stop = len(text) if end < 0 else end + 2
        elif text[index] in {'"', "'"}:
            quote = text[index]
            stop = index + 1
            while stop < len(text):
                if text[stop] == "\\":
                    stop += 2
                    continue
                stop += 1
                if text[stop - 1] == quote:
                    break
        else:
            index += 1
            continue
        for position in range(index, stop):
            if masked[position] not in "\r\n":
                masked[position] = " "
        index = stop
    return "".join(masked)


def _matching_braces(masked: str) -> dict[int, int]:
    stack: list[int] = []
    matches: dict[int, int] = {}
    for position, character in enumerate(masked):
        if character == "{":
            stack.append(position)
        elif character == "}" and stack:
            matches[stack.pop()] = position
    return matches


def _callable_name(header: str, type_names: set[str]) -> str | None:
    trimmed = re.sub(r"\bthrows\b[\s\S]*$", "", header).strip()
    if re.search(r"\b(?:class|interface|enum|record)\s+", trimmed):
        return None
    if trimmed.endswith(")"):
        depth = 0
        opening = None
        for position in range(len(trimmed) - 1, -1, -1):
            if trimmed[position] == ")":
                depth += 1
            elif trimmed[position] == "(":
                depth -= 1
                if depth == 0:
                    opening = position
                    break
        if opening is None:
            return None
        names = TOKEN.findall(trimmed[:opening])
        if not names:
            return None
        name = names[-1]
        if name in CONTROL_NAMES or re.search(r"\bnew\s+" + re.escape(name) + r"\s*$", trimmed[:opening]):
            return None
        return name
    names = TOKEN.findall(trimmed)
    if names and names[-1] in type_names:
        return names[-1]
    if trimmed == "static":
        return "<static-initializer>"
    return None


def inspect_executables(source: Path, type_names: set[str]) -> list[ExecutableDeclaration]:
    """Inventory executable bodies and classify only mechanically obvious documentation exemptions."""
    text = source.read_text(encoding="utf-8")
    masked = _mask_literals_and_comments(text)
    braces = _matching_braces(masked)
    delimiters = [-1]
    delimiters.extend(position for position, character in enumerate(masked) if character in "{};")
    delimiters.sort()
    declarations: list[ExecutableDeclaration] = []
    for opening, closing in sorted(braces.items()):
        prior = max(position for position in delimiters if position < opening)
        header = masked[prior + 1 : opening]
        name = _callable_name(header, type_names)
        if name is None:
            continue
        original_header = text[prior + 1 : opening]
        body = masked[opening : closing + 1]
        decisions = len(re.findall(r"\b(?:if|for|while|catch|case)\b|\?", body))
        body_lines = body.count("\n") + 1
        documented = bool(JAVADOC.search(original_header))
        descriptive_test = "@Test" in original_header and len(name) >= 32 and len(re.findall(r"[A-Z]|_", name)) >= 4
        declarations.append(ExecutableDeclaration(
            source,
            text.count("\n", 0, prior + 1) + 1,
            name,
            body_lines,
            decisions,
            documented,
            descriptive_test,
        ))
    return declarations


def inspect(roots: tuple[Path, ...] = ROOTS) -> Coverage:
    """Return the complete maintained Java source/type/executable/package inventory."""
    sources = tuple(sorted(source for root in roots if root.exists() for source in root.rglob("*.java")))
    types = tuple(declaration for source in sources for declaration in inspect_types(source))
    type_names = {declaration.name for declaration in types}
    executables = tuple(declaration for source in sources for declaration in inspect_executables(source, type_names))
    production = roots[0]
    production_sources = tuple(source for source in sources if source.is_relative_to(production))
    packages = tuple(sorted({package for source in production_sources if (package := package_name(source))}))
    missing = tuple(
        package
        for package in packages
        if not has_package_doc(production / Path(*package.split(".")) / "package-info.java")
    )
    return Coverage(sources, types, executables, packages, missing)


def has_package_doc(package_info: Path) -> bool:
    return package_info.is_file() and bool(PACKAGE_DOC.match(package_info.read_text(encoding="utf-8")))


def package_name(source: Path) -> str | None:
    match = PACKAGE.search(source.read_text(encoding="utf-8"))
    return match.group(1) if match else None


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", action="append", type=Path, help="Override Java roots; repeat for multiple roots")
    args = parser.parse_args(argv)
    roots = tuple(args.root) if args.root else ROOTS
    coverage = inspect(roots)
    undocumented_types = [item for item in coverage.types if not item.documented]
    unjustified = [item for item in coverage.executables if not item.justified]
    explicitly_documented = sum(item.documented for item in coverage.executables)
    scenario_specified = sum(item.descriptive_test and not item.documented for item in coverage.executables)
    structurally_trivial = sum(
        not item.nontrivial and not item.documented and not item.descriptive_test
        for item in coverage.executables
    )

    print(f"Java files inventoried: {len(coverage.sources)}")
    print(f"Named types documented: {len(coverage.types) - len(undocumented_types)}/{len(coverage.types)}")
    print(f"Production packages documented: {len(coverage.packages) - len(coverage.missing_package_docs)}/{len(coverage.packages)}")
    print(f"Executable bodies inventoried: {len(coverage.executables)}")
    print(f"Executable bodies with semantic Javadoc: {explicitly_documented}")
    print(f"Self-describing test scenarios: {scenario_specified}")
    print(f"Structurally trivial bodies: {structurally_trivial}")
    for declaration in undocumented_types:
        print(f"UNDOCUMENTED_TYPE {declaration.source}:{declaration.line} {declaration.name}")
    for package in coverage.missing_package_docs:
        print(f"UNDOCUMENTED_PACKAGE {package}")
    for declaration in unjustified:
        print(
            f"UNDOCUMENTED_NONTRIVIAL_EXECUTABLE {declaration.source}:{declaration.line} "
            f"{declaration.name} ({declaration.body_lines} lines, {declaration.decisions} decisions)"
        )
    return 1 if undocumented_types or coverage.missing_package_docs or unjustified else 0


if __name__ == "__main__":
    sys.exit(main())
