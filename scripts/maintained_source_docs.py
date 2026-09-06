#!/usr/bin/env python3
"""Audit maintained scripting/configuration boundaries and render their intent inventory."""

from __future__ import annotations

import argparse
import ast
import html
import re
from dataclasses import dataclass
from pathlib import Path


WORD = re.compile(r"[A-Za-z][A-Za-z'-]+")
COMMENTABLE_KINDS = frozenset(
    {
        "api-contract",
        "dockerfile",
        "model-config",
        "python",
        "shell",
        "sql",
        "text-config",
        "workflow",
        "compose",
        "runtime-yaml",
    }
)
PROTECTED_MANIFESTED = frozenset(
    {
        ".github/workflows/work-graph-guard-tests.yml",
        ".github/workflows/work-graph-guard.yml",
        "scripts/test_work_graph_guard.py",
    }
)


@dataclass(frozen=True)
class SourceEntry:
    path: Path
    kind: str


@dataclass(frozen=True)
class Finding:
    path: Path
    reason: str

    def render(self, root: Path) -> str:
        return f"{self.path.relative_to(root).as_posix()}: {self.reason}"


def inventory(root: Path) -> list[SourceEntry]:
    """Enumerate maintained non-Java/non-TypeScript executable source by language."""
    entries: set[SourceEntry] = set()

    def add(paths, kind: str) -> None:
        for path in paths:
            if path.is_file():
                entries.add(SourceEntry(path, kind))

    add((root / "scripts").rglob("*.py"), "python")
    add((root / "scripts").rglob("*.sh"), "shell")
    add((root / "backend/src/main/resources/db").rglob("*.sql"), "sql")
    add((root / ".github/workflows").glob("*.yml"), "workflow")
    add((root / ".github/workflows").glob("*.yaml"), "workflow")
    add(root.glob("compose*.yaml"), "compose")
    add((root / "backend/src/main/resources").glob("application*.yml"), "runtime-yaml")
    add([root / "backend/src/main/resources/static/openapi.yaml"], "api-contract")
    add(
        (root / "backend/src/main/resources/dev/specgraph/reference/analysis/randomforest").glob("*.properties"),
        "model-config",
    )
    add((root / "docker").glob("*Dockerfile"), "dockerfile")
    add((root / "scripts/ci").glob("*.txt"), "text-config")
    add((root / "scripts/ci").glob("*.tsv"), "text-config")
    add([root / "backend/pom.xml"], "manifested-config")
    add((root / "frontend").glob("*.json"), "manifested-config")
    add([root / "e2e/package.json"], "manifested-config")
    add([root / "docs/reviewer/frontend-source-inventory.json"], "manifested-config")
    add(
        [
            root / "docs/tooling/frontend-reference/package.json",
            root / "docs/tooling/frontend-reference/tsconfig.json",
            root / "docs/tooling/frontend-reference/typedoc.json",
        ],
        "manifested-config",
    )
    return sorted(entries, key=lambda entry: entry.path.as_posix())


def prose_quality(prose: str) -> str | None:
    """Reject placeholder-sized headers without pretending to judge intent semantically."""
    if len(WORD.findall(prose)) < 8:
        return "intent documentation must contain at least eight words"
    return None


def python_intent(path: Path) -> str | None:
    """Read a Python module docstring without executing the maintained script."""
    try:
        module = ast.parse(path.read_text(encoding="utf-8"))
    except SyntaxError:
        return None
    return ast.get_docstring(module, clean=True)


def comment_intent(path: Path, kind: str) -> str | None:
    """Extract only the leading file-purpose comment for commentable text formats."""
    lines = path.read_text(encoding="utf-8").splitlines()
    comments: list[str] = []
    for index, line in enumerate(lines[:24]):
        stripped = line.strip()
        if not stripped:
            if comments:
                break
            continue
        if kind == "shell" and index == 0 and stripped.startswith("#!"):
            continue
        if kind == "dockerfile" and stripped.startswith("# syntax="):
            continue
        marker = "--" if kind == "sql" else "#"
        if stripped.startswith(marker):
            comments.append(stripped[len(marker) :].strip())
            continue
        if kind == "workflow" and index == 0 and stripped.startswith("name:"):
            continue
        break
    return " ".join(comments) or None


def read_manifest(root: Path) -> tuple[dict[Path, str], list[str]]:
    """Read intent for XML/JSON files whose grammars cannot carry portable comments."""
    path = root / "scripts/ci/source-intent-inventory.tsv"
    mappings: dict[Path, str] = {}
    errors: list[str] = []
    if not path.is_file():
        return mappings, ["scripts/ci/source-intent-inventory.tsv is missing"]
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not raw or raw.startswith("#"):
            continue
        columns = raw.split("\t", 1)
        if len(columns) != 2:
            errors.append(f"source-intent-inventory.tsv:{number}: expected path<TAB>intent")
            continue
        relative, intent = columns
        resolved = root / relative
        if resolved in mappings:
            errors.append(f"source-intent-inventory.tsv:{number}: duplicate {relative}")
        mappings[resolved] = intent.strip()
    return mappings, errors


def audit(root: Path) -> tuple[list[Finding], list[tuple[SourceEntry, str]]]:
    """Validate exact inventory coverage and return the prose used by the HTML view."""
    entries = inventory(root)
    manifested, manifest_errors = read_manifest(root)
    findings = [Finding(root / error.split(":", 1)[0], error) for error in manifest_errors]
    documented: list[tuple[SourceEntry, str]] = []
    expected_manifest = {
        entry.path
        for entry in entries
        if entry.kind == "manifested-config"
        or entry.path.relative_to(root).as_posix() in PROTECTED_MANIFESTED
    }
    for extra in sorted(set(manifested) - expected_manifest):
        findings.append(Finding(extra, "stale or unsupported manifested configuration"))

    for entry in entries:
        if entry.path in manifested:
            prose = manifested[entry.path]
        elif entry.kind == "python":
            prose = python_intent(entry.path)
        elif entry.kind in COMMENTABLE_KINDS:
            prose = comment_intent(entry.path, entry.kind)
        else:
            prose = manifested.get(entry.path)
        if not prose:
            findings.append(Finding(entry.path, "missing file-level intent documentation"))
            continue
        defect = prose_quality(prose)
        if defect:
            findings.append(Finding(entry.path, defect))
            continue
        documented.append((entry, prose))
    return findings, documented


def render_html(root: Path, documented: list[tuple[SourceEntry, str]], output: Path) -> None:
    """Render a deterministic navigable index without copying generated HTML into Git."""
    rows = []
    for entry, prose in documented:
        relative = entry.path.relative_to(root).as_posix()
        rows.append(
            "<tr><td>" + html.escape(entry.kind) + "</td><td><code>"
            + html.escape(relative) + "</code></td><td>" + html.escape(prose) + "</td></tr>"
        )
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
        "<title>Maintained source intent</title></head><body><main>"
        "<h1>Maintained source intent</h1>"
        "<p>File-level purpose for scripts, migrations and executable configuration. "
        "Implementation details remain authoritative in source.</p>"
        "<table><thead><tr><th>Language</th><th>Source</th><th>Intent</th></tr></thead><tbody>"
        + "".join(rows) + "</tbody></table></main></body></html>\n",
        encoding="utf-8",
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--html", type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    findings, documented = audit(root)
    if findings:
        print("Maintained source-intent ratchet failed:")
        for finding in findings:
            print(f"- {finding.render(root)}")
        return 1
    if args.html:
        output = args.html if args.html.is_absolute() else root / args.html
        render_html(root, documented, output)
    counts: dict[str, int] = {}
    for entry, _ in documented:
        counts[entry.kind] = counts.get(entry.kind, 0) + 1
    summary = ", ".join(f"{kind}={counts[kind]}" for kind in sorted(counts))
    print(f"Maintained source-intent coverage: {len(documented)}/{len(documented)} ({summary}).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
