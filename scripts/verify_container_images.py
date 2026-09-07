#!/usr/bin/env python3
"""Fail closed when repository-owned container references escape the immutable manifest."""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from pathlib import Path


DIGEST = re.compile(r"sha256:[0-9a-f]{64}$")
REFERENCE = re.compile(
    r"(?<![A-Za-z0-9_.])((?:[a-z0-9][a-z0-9.-]*(?::[0-9]+)?/)?[a-z0-9][a-z0-9._-]*"
    r"(?:/[a-z0-9][a-z0-9._-]*)*:[A-Za-z0-9][A-Za-z0-9._-]*(?:@sha256:[0-9a-f]{64})?)"
)
BARE_EXTERNAL = frozenset({"alpine", "eclipse-temurin", "maven", "node", "postgres", "python"})
FIRST_PARTY = (
    "ghcr.io/jdoe-dev-159753/specgraph-reference-app",
    "ghcr.io/jdoe-dev-159753/specgraph-reference-compose",
    "specgraph-reference-app",
)
TESTCONTAINERS_HELPERS = {
    "ryuk.container.image": "testcontainers/ryuk:0.14.0",
    "tinyimage.container.image": "alpine:3.17",
    "socat.container.image": "alpine/socat:1.7.4.3-r0",
    "sshd.container.image": "testcontainers/sshd:1.3.0",
    "vncrecorder.container.image": "testcontainers/vnc-recorder:1.3.0",
}


@dataclass(frozen=True)
class Image:
    reference: str
    required: frozenset[str]
    amd64: str
    arm64: str
    purpose: str

    @property
    def tag(self) -> str:
        return self.reference.split("@", 1)[0]


def load_manifest(path: Path) -> tuple[dict[str, Image], list[str]]:
    images: dict[str, Image] = {}
    errors: list[str] = []
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) != 5:
            errors.append(f"{path}:{number}: expected five tab-separated columns")
            continue
        reference, required_text, amd64, arm64, purpose = parts
        tag, separator, index_digest = reference.partition("@")
        if not separator or not DIGEST.fullmatch(index_digest):
            errors.append(f"{path}:{number}: reference must be name:tag@sha256")
        required = frozenset(required_text.split(","))
        if not required or not required <= {"linux/amd64", "linux/arm64"}:
            errors.append(f"{path}:{number}: invalid required platform set")
        platform_digests = {"linux/amd64": amd64, "linux/arm64": arm64}
        for platform in required:
            if not DIGEST.fullmatch(platform_digests[platform]):
                errors.append(f"{path}:{number}: required {platform} manifest digest is missing")
        for digest in (amd64, arm64):
            if digest != "-" and not DIGEST.fullmatch(digest):
                errors.append(f"{path}:{number}: invalid platform manifest digest {digest}")
        if not purpose.strip():
            errors.append(f"{path}:{number}: purpose is blank")
        if tag in images:
            errors.append(f"{path}:{number}: duplicate image tag {tag}")
        images[tag] = Image(reference, required, amd64, arm64, purpose)
    if not images:
        errors.append(f"{path}: manifest is empty")
    return images, errors


def source_files(root: Path) -> list[Path]:
    files = [*root.glob("compose*.yaml"), *root.glob("docker/*Dockerfile")]
    files.extend(root.glob(".github/workflows/*.yml"))
    files.extend(root.glob(".github/workflows/*.yaml"))
    files.extend(root.glob("scripts/**/*.sh"))
    files.extend(root.glob("backend/src/test/**/*.java"))
    properties = root / "backend/src/test/resources/testcontainers.properties"
    if properties.is_file():
        files.append(properties)
    return sorted(set(files))


def repository_name(reference: str) -> str:
    return reference.split("@", 1)[0].rsplit(":", 1)[0]


def is_external_candidate(reference: str) -> bool:
    repository = repository_name(reference)
    return "/" in repository or repository in BARE_EXTERNAL


def is_first_party(reference: str) -> bool:
    repository = repository_name(reference)
    return any(repository == prefix or repository.startswith(prefix + "/") for prefix in FIRST_PARTY)


def audit_sources(root: Path, images: dict[str, Image]) -> tuple[int, list[str]]:
    findings: list[str] = []
    checked = 0
    for path in source_files(root):
        text = path.read_text(encoding="utf-8")
        for match in REFERENCE.finditer(text):
            reference = match.group(1)
            if text[max(0, match.start() - 3):match.start()] == "://":
                continue
            if not is_external_candidate(reference) or is_first_party(reference):
                continue
            checked += 1
            tag = reference.split("@", 1)[0]
            governed = images.get(tag)
            relative = path.relative_to(root).as_posix()
            line = text.count("\n", 0, match.start()) + 1
            if not governed:
                findings.append(f"{relative}:{line}: external image is absent from manifest: {reference}")
            elif reference != governed.reference:
                findings.append(f"{relative}:{line}: expected {governed.reference}, found {reference}")
    return checked, sorted(set(findings))


def audit_testcontainers_helpers(root: Path, images: dict[str, Image]) -> list[str]:
    path = root / "backend/src/test/resources/testcontainers.properties"
    if not path.is_file():
        return [f"{path.relative_to(root).as_posix()}: missing Testcontainers helper configuration"]
    values = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    findings = []
    for key, tag in TESTCONTAINERS_HELPERS.items():
        governed = images.get(tag)
        expected = governed.reference if governed else None
        actual = values.get(key)
        if expected is None:
            findings.append(f"manifest is missing Testcontainers helper {tag}")
        elif actual != expected:
            findings.append(f"{path.relative_to(root).as_posix()}: expected {key}={expected}, found {actual or '<missing>'}")
    return findings


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--manifest", type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    manifest = args.manifest or root / "scripts/ci/container-images.tsv"
    images, errors = load_manifest(manifest)
    checked, findings = audit_sources(root, images)
    failures = errors + findings + audit_testcontainers_helpers(root, images)
    if failures:
        print("Container image supply-chain audit failed:")
        for failure in failures:
            print(f"- {failure}")
        return 1
    required = sum(len(image.required) for image in images.values())
    print(f"Container image supply-chain audit: images={len(images)}; required platforms={required}; references={checked}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
