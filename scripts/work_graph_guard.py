#!/usr/bin/env python3
"""Reject competing prose work-state, stale Codex review evidence, and one-shot workflows."""

from __future__ import annotations

import base64
import json
import os
import re
import sys
from urllib import error, parse, request

API = "https://api.github.com"
REPO = os.environ.get("GITHUB_REPOSITORY", "jdoe-dev-159753/specgraph-reference-app")
TOKEN = os.environ.get("GITHUB_TOKEN", "")
EVENT_PATH = os.environ.get("GITHUB_EVENT_PATH", "")
EVENT_NAME = os.environ.get("GITHUB_EVENT_NAME", "")
CODEX_USER_ID = 199175422
CODEX_APP_ID = 1144995
MIN_REVIEWED_SHA_PREFIX = 10
WORKFLOW_DIR = ".github/workflows"
DURABLE_WORKFLOW_MANIFEST = "scripts/ci/durable-workflows.txt"

PREFIX = re.compile(
    r"^\s*(?:Classification|Parent|Children|Depends on|Blocked by|Blocking|"
    r"Production PR|Implementation PR|Lifecycle|Disposition)\s*:",
    re.IGNORECASE,
)
LEGACY_TOKEN = re.compile(r"\b(?:IN_SCOPE|FOLLOW_UP|ALREADY_TRACKED|NON_ACTIONABLE)\b")
CLEAN_CODEX_REVIEW = re.compile(r"Codex Review:\s*Didn't find any major issues\.", re.IGNORECASE)
REVIEWED_COMMIT = re.compile(r"\*\*Reviewed commit:\*\*\s*`([0-9a-fA-F]{10,40})`")
YAML_MAPPING_KEY = re.compile(
    r"^(\s*)(?:\"((?:\\.|[^\"])*)\"|'((?:''|[^'])*)'|([A-Za-z0-9_.-]+))\s*:\s*(.*)$"
)
YAML_SEQUENCE_ENTRY = re.compile(r"^(\s*)-\s+(.*)$")
ONE_SHOT_WORKFLOW = re.compile(
    r"(?<![A-Za-z0-9])(?:pr|pull(?:[^A-Za-z0-9]+request)?|issue|discovery|story|fix)"
    r"[^A-Za-z0-9]*\d+(?![A-Za-z0-9])",
    re.IGNORECASE,
)


def api(path: str):
    req = request.Request(f"{API}{path}")
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("X-GitHub-Api-Version", "2026-03-10")
    if TOKEN:
        req.add_header("Authorization", f"Bearer {TOKEN}")
    try:
        with request.urlopen(req, timeout=30) as response:
            return json.loads(response.read())
    except error.HTTPError as exc:
        detail = exc.read().decode(errors="replace")
        raise RuntimeError(f"GitHub API GET {path} failed: {exc.code} {detail}") from exc


def pages(path: str):
    page = 1
    while True:
        separator = "&" if "?" in path else "?"
        items = api(f"{path}{separator}per_page=100&page={page}")
        if not items:
            return
        yield from items
        if len(items) < 100:
            return
        page += 1


def violations(text: str) -> list[str]:
    found: list[str] = []
    fenced = False
    fence = ""
    for lineno, line in enumerate(text.splitlines(), 1):
        stripped = line.lstrip()
        if stripped.startswith(("```", "~~~")):
            marker = stripped[:3]
            if not fenced:
                fenced, fence = True, marker
            elif marker == fence:
                fenced, fence = False, ""
            continue
        if fenced:
            continue
        if PREFIX.search(line):
            found.append(f"line {lineno}: native work relation/state encoded as prose")
        if LEGACY_TOKEN.search(line):
            found.append(f"line {lineno}: legacy work-state token encoded as prose")
    return found


def scan_surface(kind: str, identifier: str, text: str, failures: list[str]) -> None:
    for finding in violations(text or ""):
        failures.append(f"{kind} {identifier}: {finding}")


def event_pr_number_from_payload(event: dict) -> int | None:
    pull_request = event.get("pull_request")
    if pull_request:
        return pull_request.get("number") or event.get("number")
    issue = event.get("issue") or {}
    if issue.get("pull_request"):
        return issue.get("number")
    return None


def event_pr_number() -> int | None:
    if not EVENT_PATH or not os.path.exists(EVENT_PATH):
        return None
    with open(EVENT_PATH, encoding="utf-8") as handle:
        return event_pr_number_from_payload(json.load(handle))


def is_codex_review(review: dict) -> bool:
    return (review.get("user") or {}).get("id") == CODEX_USER_ID


def has_current_head_codex_review(reviews, head_sha: str) -> bool:
    return any(
        is_codex_review(review) and review.get("commit_id") == head_sha
        for review in reviews
    )


def clean_codex_reviewed_prefix(comment: dict) -> str | None:
    if (comment.get("user") or {}).get("id") != CODEX_USER_ID:
        return None
    if (comment.get("performed_via_github_app") or {}).get("id") != CODEX_APP_ID:
        return None
    body = comment.get("body") or ""
    if not CLEAN_CODEX_REVIEW.search(body):
        return None
    match = REVIEWED_COMMIT.search(body)
    if not match:
        return None
    prefix = match.group(1).lower()
    return prefix if len(prefix) >= MIN_REVIEWED_SHA_PREFIX else None


def has_current_head_clean_codex_result(comments, head_sha: str) -> bool:
    normalized = head_sha.lower()
    return any(
        (prefix := clean_codex_reviewed_prefix(comment)) is not None
        and normalized.startswith(prefix)
        for comment in comments
    )


def require_current_head_codex_review(pr_number: int, failures: list[str]) -> None:
    pr = api(f"/repos/{REPO}/pulls/{pr_number}")
    if pr.get("state") != "open" or pr.get("draft"):
        return
    if (pr.get("base") or {}).get("ref") != "main":
        return

    head_sha = pr["head"]["sha"]
    reviews = pages(f"/repos/{REPO}/pulls/{pr_number}/reviews")
    if has_current_head_codex_review(reviews, head_sha):
        print(f"pull request #{pr_number}: Codex review object covers current head {head_sha[:12]}")
        return

    comments = pages(f"/repos/{REPO}/issues/{pr_number}/comments")
    if has_current_head_clean_codex_result(comments, head_sha):
        print(f"pull request #{pr_number}: clean Codex result covers current head {head_sha[:12]}")
        return

    failures.append(
        f"pull request #{pr_number}: no Codex review evidence is anchored to current head {head_sha[:12]}"
    )


def decode_contents_payload(payload: dict, path: str) -> str:
    if payload.get("type") != "file" or payload.get("encoding") != "base64":
        raise RuntimeError(f"GitHub contents response for {path} is not a base64 file")
    compact = "".join((payload.get("content") or "").splitlines())
    return base64.b64decode(compact).decode("utf-8")


def parse_durable_workflow_manifest(text: str) -> set[str]:
    return {
        line.strip()
        for line in text.splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    }


def _strip_yaml_inline_comment(value: str) -> str:
    """Strip a YAML comment marker only when it is outside quoted scalar text."""
    single = False
    double = False
    escaped = False
    index = 0
    while index < len(value):
        char = value[index]
        if double:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                double = False
        elif single:
            if char == "'":
                if index + 1 < len(value) and value[index + 1] == "'":
                    index += 1
                else:
                    single = False
        elif char == '"':
            double = True
        elif char == "'":
            single = True
        elif char == "#" and (index == 0 or value[index - 1].isspace()):
            return value[:index].rstrip()
        index += 1
    return value.rstrip()


def _double_quoted_yaml_scalar(value: str) -> str:
    """Decode YAML 1.2 double-quoted escapes without a runtime dependency."""
    simple = {
        "0": "\0", "a": "\a", "b": "\b", "t": "\t", "n": "\n",
        "v": "\v", "f": "\f", "r": "\r", "e": "\x1b", " ": " ",
        '"': '"', "/": "/", "\\": "\\", "N": "\x85", "_": "\xa0",
        "L": "\u2028", "P": "\u2029",
    }
    content = value[1:-1]
    decoded: list[str] = []
    index = 0
    while index < len(content):
        if content[index] != "\\":
            decoded.append(content[index])
            index += 1
            continue
        index += 1
        if index >= len(content):
            decoded.append("\\")
            break
        escape = content[index]
        if escape in "xuU":
            width = {"x": 2, "u": 4, "U": 8}[escape]
            digits = content[index + 1 : index + 1 + width]
            if len(digits) != width or not re.fullmatch(r"[0-9A-Fa-f]+", digits):
                return content
            try:
                decoded.append(chr(int(digits, 16)))
            except (ValueError, OverflowError):
                return content
            index += width + 1
            continue
        decoded.append(simple.get(escape, escape))
        index += 1
    return "".join(decoded)


def _plain_yaml_scalar(value: str) -> str:
    value = _strip_yaml_inline_comment(value.strip())
    if not value:
        return ""
    if value[0] == '"' and value.endswith('"'):
        return _double_quoted_yaml_scalar(value)
    if value[0] == "'" and value.endswith("'"):
        return value[1:-1].replace("''", "'")
    return value

def _double_quoted_scalar_is_closed(value: str) -> bool:
    escaped = False
    for char in value[1:]:
        if escaped:
            escaped = False
        elif char == "\\":
            escaped = True
        elif char == '"':
            return True
    return False


def _single_quoted_scalar_is_closed(value: str) -> bool:
    index = 1
    while index < len(value):
        if value[index] != "'":
            index += 1
            continue
        if index + 1 < len(value) and value[index + 1] == "'":
            index += 2
            continue
        return True
    return False


def _complete_flow_scalar(
    lines: list[str], index: int, root_indent: int, value: str
) -> str:
    block_header = _strip_yaml_inline_comment(value)
    if re.fullmatch(r"([>|])(?:[+-]?\d?|\d?[+-]?)?", block_header):
        return value

    is_double = value.startswith('"')
    is_single = value.startswith("'")
    if is_double and _double_quoted_scalar_is_closed(value):
        return value
    if is_single and _single_quoted_scalar_is_closed(value):
        return value

    combined = value
    for continuation in lines[index + 1 :]:
        if not continuation.strip():
            continue
        continuation_indent = len(continuation) - len(continuation.lstrip(" "))
        if continuation_indent <= root_indent:
            break
        piece = continuation.strip()
        if is_double and combined.endswith("\\"):
            combined = combined[:-1] + piece
        else:
            combined += " " + piece
        if is_double and _double_quoted_scalar_is_closed(combined):
            break
        if is_single and _single_quoted_scalar_is_closed(combined):
            break
    return combined


def _extract_yaml_scalar(
    lines: list[str], index: int, root_indent: int, raw_value: str
) -> str:
    value = _complete_flow_scalar(lines, index, root_indent, raw_value.strip())
    block_header = _strip_yaml_inline_comment(value)
    block = re.fullmatch(r"([>|])(?:[+-]?\d?|\d?[+-]?)?", block_header)
    if not block:
        return _plain_yaml_scalar(value)

    parts: list[str] = []
    for continuation in lines[index + 1 :]:
        if not continuation.strip():
            parts.append("")
            continue
        continuation_indent = len(continuation) - len(continuation.lstrip(" "))
        if continuation_indent <= root_indent:
            break
        parts.append(continuation.strip())
    separator = "\n" if block.group(1) == "|" else " "
    return separator.join(parts).strip()


def extract_workflow_name(text: str) -> str:
    """Read the resolved top-level YAML name scalar without a YAML dependency."""
    lines = text.splitlines()
    mapping_lines: list[tuple[int, int, str, str]] = []
    sequence_lines: list[tuple[int, int, str]] = []
    for index, line in enumerate(lines):
        if not line.strip() or line.lstrip().startswith(("#", "---", "%")):
            continue
        match = YAML_MAPPING_KEY.match(line)
        if match:
            indent = len(match.group(1).replace("\t", "    "))
            if match.group(2) is not None:
                key = _plain_yaml_scalar(f'"{match.group(2)}"')
            elif match.group(3) is not None:
                key = _plain_yaml_scalar(f"'{match.group(3)}'")
            else:
                key = match.group(4)
            mapping_lines.append((index, indent, key, match.group(5)))
            continue
        sequence = YAML_SEQUENCE_ENTRY.match(line)
        if sequence:
            indent = len(sequence.group(1).replace("\t", "    "))
            sequence_lines.append((index, indent, sequence.group(2)))

    if not mapping_lines:
        return ""
    root_indent = min(item[1] for item in mapping_lines)
    root_entries = [item for item in mapping_lines if item[1] == root_indent]

    anchors: dict[str, str] = {}
    scalar_entries = [
        (index, indent, raw_value)
        for index, indent, _, raw_value in mapping_lines
    ] + sequence_lines
    for index, indent, raw_value in scalar_entries:
        value = _extract_yaml_scalar(lines, index, indent, raw_value)
        anchor = re.match(r"&([A-Za-z0-9_-]+)(?:\s+)(.+)$", value, re.DOTALL)
        if anchor:
            anchors[anchor.group(1)] = _plain_yaml_scalar(anchor.group(2))

    for index, _, key, raw_value in root_entries:
        if key != "name":
            continue
        value = _extract_yaml_scalar(lines, index, root_indent, raw_value)
        alias = re.fullmatch(r"\*([A-Za-z0-9_-]+)", value)
        if alias:
            return anchors.get(alias.group(1), "")
        return value
    return ""

def workflow_inventory_violations(
    workflow_texts: dict[str, str], manifest_text: str
) -> list[str]:
    failures: list[str] = []
    actual = set(workflow_texts)
    allowed = parse_durable_workflow_manifest(manifest_text)

    missing = sorted(allowed - actual)
    unexpected = sorted(actual - allowed)
    if missing:
        failures.append(
            "durable workflow manifest entries missing from repository: " + ", ".join(missing)
        )
    if unexpected:
        failures.append(
            "workflow files not declared durable: "
            + ", ".join(unexpected)
            + "; parameterize/reuse an existing durable workflow or deliberately update the manifest"
        )

    for filename, text in sorted(workflow_texts.items()):
        workflow_name = extract_workflow_name(text)
        if ONE_SHOT_WORKFLOW.search(filename) or (
            workflow_name and ONE_SHOT_WORKFLOW.search(workflow_name)
        ):
            failures.append(
                f"one-shot workflow identity is forbidden: {filename} (name={workflow_name!r})"
            )
    return failures


def pr_changes_workflow_contract(changed_paths) -> bool:
    return any(
        path == DURABLE_WORKFLOW_MANIFEST or path.startswith(f"{WORKFLOW_DIR}/")
        for path in changed_paths
    )


def changed_file_paths(changed_items: list[dict]) -> list[str]:
    paths: list[str] = []
    for item in changed_items:
        filename = item.get("filename")
        previous_filename = item.get("previous_filename")
        if filename:
            paths.append(filename)
        if previous_filename:
            paths.append(previous_filename)
    return paths


def require_durable_workflow_surface(pr_number: int, failures: list[str]) -> None:
    pr = api(f"/repos/{REPO}/pulls/{pr_number}")
    if pr.get("state") != "open" or pr.get("draft"):
        return

    changed_items = list(pages(f"/repos/{REPO}/pulls/{pr_number}/files"))
    if not pr_changes_workflow_contract(changed_file_paths(changed_items)):
        return

    head_sha = pr["head"]["sha"]
    ref = parse.quote(head_sha, safe="")
    manifest_payload = api(
        f"/repos/{REPO}/contents/{DURABLE_WORKFLOW_MANIFEST}?ref={ref}"
    )
    manifest_text = decode_contents_payload(manifest_payload, DURABLE_WORKFLOW_MANIFEST)

    entries = api(f"/repos/{REPO}/contents/{WORKFLOW_DIR}?ref={ref}")
    workflow_texts: dict[str, str] = {}
    for entry in entries:
        name = entry.get("name") or ""
        if entry.get("type") != "file" or not name.endswith((".yml", ".yaml")):
            continue
        payload = api(f"/repos/{REPO}/contents/{WORKFLOW_DIR}/{name}?ref={ref}")
        workflow_texts[name] = decode_contents_payload(payload, f"{WORKFLOW_DIR}/{name}")

    inventory_failures = workflow_inventory_violations(workflow_texts, manifest_text)
    for finding in inventory_failures:
        failures.append(f"pull request #{pr_number}: {finding}")

    if not inventory_failures:
        print(
            f"pull request #{pr_number}: durable workflow surface is clean "
            f"({len(workflow_texts)} workflows)"
        )


def main() -> int:
    failures: list[str] = []
    open_items = list(pages(f"/repos/{REPO}/issues?state=open"))
    for item in open_items:
        number = item["number"]
        kind = "pull request" if "pull_request" in item else "issue"
        scan_surface(kind, f"#{number} title", item.get("title") or "", failures)
        scan_surface(kind, f"#{number} body", item.get("body") or "", failures)

    # Conversation and review comments are discussion, not controlled work-state
    # descriptions. Review freshness uses immutable Codex bot/App identity and the
    # SHA GitHub/Codex records for the reviewed head. Finding-bearing reviews expose
    # PullRequestReview.commit_id; clean Codex reviews are emitted as bot comments
    # that explicitly name the reviewed commit prefix.
    pr_number = event_pr_number()
    if pr_number is not None:
        require_durable_workflow_surface(pr_number, failures)
        require_current_head_codex_review(pr_number, failures)
    elif EVENT_NAME in {"schedule", "workflow_dispatch"}:
        for item in open_items:
            if "pull_request" in item:
                require_current_head_codex_review(item["number"], failures)

    if failures:
        print("Work-graph/review guard failed:", file=sys.stderr)
        for finding in failures:
            print(f"- {finding}", file=sys.stderr)
        return 1

    print("controlled GitHub work descriptions, durable workflows, and current-head Codex review evidence are clean")
    return 0


if __name__ == "__main__":
    sys.exit(main())
