#!/usr/bin/env python3
"""Enforce documentation of public Java types and production packages."""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path


PUBLIC_TYPE = re.compile(
    r"^public\s+(?:(?:abstract|final|non-sealed|sealed|strictfp)\s+)*"
    r"(?:@interface|class|enum|interface|record)\s+([A-Za-z_$][\w$]*)\b",
    re.MULTILINE,
)
PACKAGE = re.compile(r"^\s*package\s+([\w.]+)\s*;")
PACKAGE_DOC = re.compile(r"(?s)^\s*/\*\*.*?\*/\s*package\s+[\w.]+\s*;")
PRECEDING_JAVADOC = re.compile(r"(?s)/\*\*.*?\*/\s*(?:@[^\r\n]+\s*)*$")


@dataclass(frozen=True)
class PublicType:
    source: Path
    line: int
    name: str
    documented: bool


def inspect_public_types(source: Path) -> list[PublicType]:
    """Return public top-level declarations and whether a Javadoc precedes each one."""
    text = source.read_text(encoding="utf-8")
    return [
        PublicType(
            source,
            text.count("\n", 0, match.start()) + 1,
            match.group(1),
            bool(PRECEDING_JAVADOC.search(text[: match.start()])),
        )
        for match in PUBLIC_TYPE.finditer(text)
    ]


def inspect(source_root: Path) -> tuple[list[PublicType], list[Path], list[str]]:
    java_sources = sorted(path for path in source_root.rglob("*.java") if path.name != "package-info.java")
    public_types = [declaration for source in java_sources for declaration in inspect_public_types(source)]
    packages = sorted({package for source in java_sources if (package := package_name(source))})
    missing_package_docs = [
        package
        for package in packages
        if not has_package_doc(source_root / Path(*package.split(".")) / "package-info.java")
    ]
    return public_types, java_sources, missing_package_docs


def has_package_doc(package_info: Path) -> bool:
    return package_info.is_file() and bool(PACKAGE_DOC.match(package_info.read_text(encoding="utf-8")))


def package_name(source: Path) -> str | None:
    match = PACKAGE.search(source.read_text(encoding="utf-8"))
    return match.group(1) if match else None


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--root",
        type=Path,
        default=Path("backend/src/main/java"),
        help="Java production source root (default: backend/src/main/java)",
    )
    args = parser.parse_args(argv)

    public_types, java_sources, missing_package_docs = inspect(args.root)
    undocumented = [declaration for declaration in public_types if not declaration.documented]
    documented_count = len(public_types) - len(undocumented)
    package_count = len({package for source in java_sources if (package := package_name(source))})

    print(f"Public top-level types documented: {documented_count}/{len(public_types)}")
    print(f"Production packages documented: {package_count - len(missing_package_docs)}/{package_count}")
    for declaration in undocumented:
        print(f"UNDOCUMENTED_TYPE {declaration.source}:{declaration.line} {declaration.name}")
    for package in missing_package_docs:
        print(f"UNDOCUMENTED_PACKAGE {package}")

    return 1 if undocumented or missing_package_docs else 0


if __name__ == "__main__":
    sys.exit(main())
