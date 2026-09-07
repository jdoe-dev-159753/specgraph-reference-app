#!/usr/bin/env python3
"""Fail closed when repository-owned container references escape the immutable manifest."""

from __future__ import annotations

import argparse
import re
import shlex
from dataclasses import dataclass
from pathlib import Path


DIGEST = re.compile(r"sha256:[0-9a-f]{64}$")
REFERENCE = re.compile(r"^[a-z0-9][a-z0-9._:/-]*(?:@sha256:[0-9a-f]{64})?$")
FIRST_PARTY = frozenset({
    "ghcr.io/jdoe-dev-159753/specgraph-reference-app",
    "ghcr.io/jdoe-dev-159753/specgraph-reference-compose",
    "specgraph-reference-app",
})
BUILTIN_IMAGES = frozenset({"scratch"})
# Dockerfile FROM can also name a build context supplied by the repository-owned
# build command. Keep this allowance exact; arbitrary context-like names still fail.
DOCKERFILE_EXTERNAL_CONTEXTS = frozenset({"exact-head-image"})
# These selectors are production-owned local/GHCR images. Their names are part of the
# deployment contract; every other unresolved selector fails closed.
FIRST_PARTY_VARIABLES = frozenset({
    "APP_R0_IMAGE", "APP_R1_IMAGE", "APP_R2_IMAGE", "APP_R3_IMAGE",
    "APP_R4_IMAGE", "APP_R4_DEGRADATION_IMAGE", "APP_R5_IMAGE", "E2E_IMAGE",
    "R4_AUTH_IMAGE",
})
EXCLUDED_PARTS = frozenset({
    ".git", ".worktrees", "graphify-out", "node_modules", "target", "vendor",
})
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
    kind: str
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
        if len(parts) != 6:
            errors.append(f"{path}:{number}: expected six tab-separated columns")
            continue
        reference, kind, required_text, amd64, arm64, purpose = parts
        tag, separator, top_digest = reference.partition("@")
        if (
            not REFERENCE.fullmatch(reference)
            or ":" not in tag.rsplit("/", 1)[-1]
            or not separator
            or not DIGEST.fullmatch(top_digest)
        ):
            errors.append(f"{path}:{number}: reference must be name:tag@sha256")
        if kind not in {"index", "manifest"}:
            errors.append(f"{path}:{number}: top-level kind must be index or manifest")
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
        present = [digest for digest in (amd64, arm64) if digest != "-"]
        if kind == "manifest":
            if len(required) != 1 or len(present) != 1 or present[0] != top_digest:
                errors.append(
                    f"{path}:{number}: manifest top level must equal its one required platform digest"
                )
        elif kind == "index" and top_digest in present:
            errors.append(f"{path}:{number}: index digest must be distinct from platform manifests")
        if not purpose.strip():
            errors.append(f"{path}:{number}: purpose is blank")
        if tag in images:
            errors.append(f"{path}:{number}: duplicate image tag {tag}")
        images[tag] = Image(reference, kind, required, amd64, arm64, purpose)
    if not images:
        errors.append(f"{path}: manifest is empty")
    return images, errors


def source_files(root: Path) -> list[Path]:
    files = []
    for path in root.rglob("*"):
        relative = path.relative_to(root)
        if not path.is_file() or any(part in EXCLUDED_PARTS for part in relative.parts):
            continue
        dockerfile = (
            path.name == "Dockerfile"
            or path.name.startswith("Dockerfile.")
            or path.name.endswith(".Dockerfile")
        )
        if dockerfile or path.suffix in {".yml", ".yaml", ".sh", ".java", ".properties"}:
            files.append(path)
    return sorted(files)


def repository_name(reference: str) -> str:
    return reference.split("@", 1)[0].rsplit(":", 1)[0]


def is_first_party(reference: str) -> bool:
    return repository_name(reference) in FIRST_PARTY


@dataclass(frozen=True)
class Candidate:
    value: str
    line: int
    context: str


def _unquote(value: str) -> str:
    value = value.strip().rstrip("\\").strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
        return value[1:-1]
    return value


def _logical_lines(text: str) -> list[tuple[int, str]]:
    result: list[tuple[int, str]] = []
    start = 1
    pending = ""
    for number, line in enumerate(text.splitlines(), 1):
        if not pending:
            start = number
        pending += (" " if pending else "") + line.strip()
        if line.rstrip().endswith("\\"):
            pending = pending[:-1]
            continue
        result.append((start, pending))
        pending = ""
    if pending:
        result.append((start, pending))
    return result


def _shell_commands(path: Path, text: str) -> list[tuple[int, str]]:
    if path.suffix not in {".yml", ".yaml"}:
        lines = text.splitlines()
        normalized: list[str] = []
        index = 0
        while index < len(lines):
            if re.match(r"^\s*[A-Za-z_][A-Za-z0-9_]*=\(\s*$", lines[index]):
                block = [lines[index]]
                index += 1
                while index < len(lines):
                    block.append(lines[index])
                    if lines[index].strip() == ")":
                        index += 1
                        break
                    index += 1
                normalized.append(" ".join(item.strip() for item in block))
                normalized.extend("" for _ in block[1:])
            else:
                normalized.append(lines[index])
                index += 1
        return _logical_lines("\n".join(normalized))
    lines = text.splitlines()
    commands: list[tuple[int, str]] = []
    index = 0
    while index < len(lines):
        marker = re.match(r"^(\s*)(?:-\s+)?run:\s*([|>])-?\s*$", lines[index])
        if not marker:
            inline = re.match(r"^\s*(?:-\s+)?run:\s*(.+)$", lines[index])
            if inline:
                commands.append((index + 1, inline.group(1)))
            index += 1
            continue
        base_indent = len(marker.group(1))
        start = index + 2
        block: list[str] = []
        index += 1
        while index < len(lines):
            line = lines[index]
            if line.strip() and len(line) - len(line.lstrip()) <= base_indent:
                break
            block.append(line.strip())
            index += 1
        if marker.group(2) == ">":
            commands.append((start, " ".join(block)))
        else:
            commands.extend((start + offset - 1, command) for offset, command in _logical_lines("\n".join(block)))
    return commands


def _docker_run_value(command: str) -> str | None:
    match = re.search(r"\bdocker\s+run\b(.*)", command)
    if not match:
        return None
    try:
        tokens = shlex.split("docker run " + match.group(1), posix=True)[2:]
    except ValueError:
        tokens = match.group(1).split()
    options_with_value = {
        "--add-host", "--entrypoint", "--env", "-e", "--hostname", "--ipc", "--name",
        "--network", "--network-alias", "--platform", "--publish", "-p", "--security-opt", "--tmpfs", "--user", "-u",
        "--volume", "-v", "--workdir", "-w",
    }
    index = 0
    while index < len(tokens):
        token = tokens[index]
        if token == "--":
            index += 1
            break
        if token in options_with_value:
            index += 2
            continue
        if token.startswith("-"):
            index += 1
            continue
        return token
    return tokens[index] if index < len(tokens) else "<missing-docker-run-image>"


def _yaml_candidates(text: str) -> list[Candidate]:
    result: list[Candidate] = []
    stack: list[tuple[int, str]] = []
    for number, line in enumerate(text.splitlines(), 1):
        match = re.match(r"^(\s*)([A-Za-z0-9_.-]+):(?:\s*(.*))?$", line)
        if not match:
            continue
        indent, key, value = len(match.group(1)), match.group(2), (match.group(3) or "").strip()
        while stack and stack[-1][0] >= indent:
            stack.pop()
        ancestors = {item[1] for item in stack}
        if key == "image" and value and ({"services", "container"} & ancestors):
            result.append(Candidate(value, number, "YAML image"))
        if value:
            if re.fullmatch(r"[A-Z_][A-Z0-9_]*IMAGE[A-Z0-9_]*", key):
                result.append(Candidate(value, number, f"image selector {key}"))
        else:
            stack.append((indent, key))
    return result


def candidates(path: Path, text: str) -> tuple[list[Candidate], set[str]]:
    result: list[Candidate] = []
    stages: set[str] = set()
    dockerfile = (
        path.name == "Dockerfile"
        or path.name.startswith("Dockerfile.")
        or path.name.endswith(".Dockerfile")
    )
    if path.suffix in {".yml", ".yaml"}:
        result.extend(_yaml_candidates(text))
    if dockerfile:
        for number, line in enumerate(text.splitlines(), 1):
            syntax = re.match(r"^\s*#\s*syntax\s*=\s*(\S+)", line, re.IGNORECASE)
            if syntax:
                result.append(Candidate(syntax.group(1), number, "Dockerfile syntax image"))
            arg = re.match(r"^\s*ARG\s+([A-Za-z_][A-Za-z0-9_]*)=(\S+)", line, re.IGNORECASE)
            if arg and "IMAGE" in arg.group(1).upper():
                result.append(Candidate(arg.group(2), number, f"Dockerfile ARG {arg.group(1)}"))
            source = re.match(r"^\s*FROM\s+(?:--\S+\s+)*(\S+)(?:\s+AS\s+(\S+))?", line, re.IGNORECASE)
            if source:
                result.append(Candidate(source.group(1), number, "Dockerfile FROM"))
                if source.group(2):
                    stages.add(source.group(2))
    for number, line in _shell_commands(path, text):
        if re.search(r"(?:^|\s)[A-Za-z_][A-Za-z0-9_]*=\(\s*docker\s+run\b", line):
            continue
        run_value = _docker_run_value(line)
        if run_value:
            result.append(Candidate(run_value, number, "docker run"))
        for match in re.finditer(r"\bdocker\s+(?:pull|create)\s+(\S+)", line):
            result.append(Candidate(match.group(1), number, "docker image command"))
        for match in re.finditer(r"--driver-opt(?:=|\s+)[\"']?image=([^\s\"']+)", line):
            result.append(Candidate(match.group(1), number, "BuildKit worker image"))
    for number, line in enumerate(text.splitlines(), 1):
        assignment = re.match(
            r"^\s*(?:export\s+)?([A-Z_][A-Z0-9_]*IMAGE[A-Z0-9_]*)\s*[:=]\s*(.+?)\s*$",
            line,
        )
        if assignment:
            result.append(Candidate(assignment.group(2), number, f"image selector {assignment.group(1)}"))
        if path.suffix == ".properties" and ".image=" in line:
            result.append(Candidate(line.split("=", 1)[1], number, "container image property"))
        if path.suffix == ".java":
            for match in re.finditer(
                r"(?:DockerImageName\.parse|(?:PostgreSQL|Generic)Container(?:<[^>]+>)?)\(\s*\"([^\"]+)\"",
                line,
            ):
                result.append(Candidate(match.group(1), number, "Testcontainers image"))
    return result, stages


def bindings(text: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for line in text.splitlines():
        match = re.match(
            r"^\s*(?:export\s+)?([A-Z_][A-Z0-9_]*)\s*[:=]\s*(.+?)\s*$",
            line,
        )
        if match:
            result[match.group(1)] = _unquote(match.group(2))
    return result


def _reference_shape(reference: str) -> tuple[str, str | None, str | None]:
    without_digest, separator, digest = reference.partition("@")
    last = without_digest.rsplit("/", 1)[-1]
    tag = without_digest if ":" in last else None
    return without_digest, tag, digest if separator else None


def audit_candidate(
    candidate: Candidate,
    stages: set[str],
    images: dict[str, Image],
    defined: dict[str, str],
    resolving: frozenset[str] = frozenset(),
) -> tuple[int, list[str]]:
    value = _unquote(candidate.value)
    if value in stages or value in BUILTIN_IMAGES:
        return 0, []
    if candidate.context == "Dockerfile FROM" and value in DOCKERFILE_EXTERNAL_CONTEXTS:
        return 0, []
    variable = re.fullmatch(r"\$([A-Za-z_][A-Za-z0-9_]*)|\$\{([A-Za-z_][A-Za-z0-9_]*)(?:(:-|:\?)(.*))?\}", value)
    if variable:
        name = variable.group(1) or variable.group(2)
        operator, fallback = variable.group(3), variable.group(4)
        selector = candidate.context.removeprefix("image selector ")
        if selector == "APP_R5_IMAGE" and name == "LOCAL_R5_IMAGE":
            return 0, []
        bound = defined.get(name)
        if bound and name not in resolving and bound != value:
            return audit_candidate(candidate.__class__(bound, candidate.line, candidate.context), stages, images, defined, resolving | {name})
        if name not in FIRST_PARTY_VARIABLES:
            return 1, [f"{candidate.context}: dynamic image selector is not explicitly first-party: {value}"]
        if operator == ":-" and fallback:
            fallback_candidate = Candidate(fallback, candidate.line, candidate.context)
            _, findings = audit_candidate(fallback_candidate, stages, images, defined, resolving | {name})
            if findings or not is_first_party(_unquote(fallback)):
                return 1, [f"{candidate.context}: {name} default must be an exact first-party image"]
        return 0, []
    if "$" in value or value.startswith("<"):
        selector = candidate.context.removeprefix("image selector ")
        allowed_composition = (
            selector in FIRST_PARTY_VARIABLES
            and (
                re.fullmatch(r"\$APP_PACKAGE(?::\$\{?[A-Za-z0-9_]+\}?|@\$\{?[A-Za-z0-9_]+\}?)", value)
                or (selector == "APP_R5_IMAGE" and value == "$LOCAL_R5_IMAGE")
                or (selector == "APP_R4_DEGRADATION_IMAGE" and value.startswith('"$degradation_image"'))
            )
        )
        if allowed_composition:
            return 0, []
        return 1, [f"{candidate.context}: dynamic or unparseable image selector: {value}"]
    if not REFERENCE.fullmatch(value):
        return 1, [f"{candidate.context}: invalid image reference: {value}"]
    if is_first_party(value):
        return 0, []
    _, tag, digest = _reference_shape(value)
    if tag is None:
        return 1, [f"{candidate.context}: external image must include an explicit tag and digest: {value}"]
    governed = images.get(tag)
    if governed is None:
        return 1, [f"{candidate.context}: external image is absent from manifest: {value}"]
    if digest is None or value != governed.reference:
        return 1, [f"{candidate.context}: expected {governed.reference}, found {value}"]
    return 1, []


def audit_sources(root: Path, images: dict[str, Image]) -> tuple[int, list[str]]:
    findings: list[str] = []
    checked = 0
    for path in source_files(root):
        text = path.read_text(encoding="utf-8")
        found, stages = candidates(path, text)
        defined = bindings(text)
        for candidate in found:
            candidate_checked, candidate_findings = audit_candidate(candidate, stages, images, defined)
            checked += candidate_checked
            relative = path.relative_to(root).as_posix()
            findings.extend(f"{relative}:{candidate.line}: {finding}" for finding in candidate_findings)
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
