#!/usr/bin/env python3
"""Fail when browser source inventory or required intent commentary drifts."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "docs/reviewer/frontend-source-inventory.json"
SOURCE_SUFFIXES = {".ts", ".tsx", ".js", ".jsx", ".css", ".scss", ".html", ".json"}
IGNORED_PARTS = {"node_modules", "dist", "playwright-report", "test-results"}

DOCUMENTED_SURFACES = (
    re.compile(r"^\s*(?:export\s+)?(?:default\s+)?(?:async\s+)?function\s+\w+"),
    re.compile(r"^\s*(?:export\s+)?type\s+\w+"),
    re.compile(r"^(?:export\s+)?const\s+\w+"),
    re.compile(r"^\s*const\s+(?:\[[^]]+]|\w+)\s*=\s*use(?:State|Ref|Query|Mutation|QueryClient)\b"),
    re.compile(r"^\s*const\s+\w+\s*=\s*\([^;]*\)\s*=>"),
    re.compile(r"^\s*test\("),
)


def inventory() -> set[str]:
    candidates: set[str] = set()
    for scope in (ROOT / "frontend", ROOT / "e2e"):
        for path in scope.rglob("*"):
            if not path.is_file() or path.suffix.lower() not in SOURCE_SUFFIXES:
                continue
            relative = path.relative_to(ROOT)
            if any(part in IGNORED_PARTS for part in relative.parts):
                continue
            candidates.add(relative.as_posix())
    return candidates


def has_preceding_doc(lines: list[str], index: int) -> bool:
    cursor = index - 1
    while cursor >= 0 and not lines[cursor].strip():
        cursor -= 1
    if cursor < 0 or not lines[cursor].rstrip().endswith("*/"):
        return False
    while cursor >= 0:
        stripped = lines[cursor].lstrip()
        if stripped.startswith("/**"):
            return True
        if stripped.startswith("/*"):
            return False
        cursor -= 1
    return False


def validate_typescript(path: Path) -> list[str]:
    lines = path.read_text(encoding="utf-8").splitlines()
    problems: list[str] = []
    first_content = next((line.strip() for line in lines if line.strip()), "")
    if not first_content.startswith("/**"):
        problems.append(f"{path.relative_to(ROOT).as_posix()}: missing module-level intent documentation")

    for index, line in enumerate(lines):
        if any(pattern.match(line) for pattern in DOCUMENTED_SURFACES) and not has_preceding_doc(lines, index):
            problems.append(
                f"{path.relative_to(ROOT).as_posix()}:{index + 1}: intent-bearing surface lacks a preceding /** ... */ comment"
            )
    return problems


def main() -> int:
    payload = json.loads(MANIFEST.read_text(encoding="utf-8"))
    entries = payload.get("files", [])
    declared = [entry["path"] for entry in entries]
    problems: list[str] = []

    if declared != sorted(declared) or len(declared) != len(set(declared)):
        problems.append("frontend source inventory must be sorted and contain unique paths")

    actual = inventory()
    expected = set(declared)
    for path in sorted(actual - expected):
        problems.append(f"unclassified frontend/browser source: {path}")
    for path in sorted(expected - actual):
        problems.append(f"stale frontend/browser inventory entry: {path}")

    for relative in sorted(actual & expected):
        path = ROOT / relative
        if path.suffix.lower() in {".ts", ".tsx", ".js", ".jsx"}:
            problems.extend(validate_typescript(path))

    if problems:
        print("Frontend source-reference ratchet failed:", file=sys.stderr)
        for problem in problems:
            print(f"- {problem}", file=sys.stderr)
        return 1

    print(f"Frontend source-reference ratchet passed: {len(actual)} files inventoried.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
