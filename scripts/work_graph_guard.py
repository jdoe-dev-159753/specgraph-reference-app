#!/usr/bin/env python3
"""Reject competing prose work-state, stale Codex review evidence, and one-shot workflows."""

from __future__ import annotations

import base64
import hashlib
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
UNRESOLVED_WORKFLOW_NAME = "<unresolved-yaml-workflow-name>"
PROTECTED_ASSET_SHA256 = {
    ".github/workflows/work-graph-guard.yml": frozenset(
        {"f15bad4b691b95abb8b0cf7f2e3b6976dc3ca3d9ad5323097aacb53f394a5fae"}
    ),
    ".github/workflows/work-graph-guard-tests.yml": frozenset(
        {"0e6865bb537e7b837ff47ec501a09b8e2a06d674fc391740b89a7ccd6c5b767c"}
    ),
    "scripts/test_work_graph_guard.py": frozenset(
        {"9c41fe1b4ffa3bb0aa59dffbd923b1baaecb6c731b49f31522b0ac6ba383f05b"}
    ),
}

PREFIX = re.compile(
    r"^\s*(?:Classification|Parent|Children|Depends on|Blocked by|Blocking|"
    r"Production PR|Implementation PR|Lifecycle|Disposition)\s*:",
    re.IGNORECASE,
)
LEGACY_TOKEN = re.compile(r"\b(?:IN_SCOPE|FOLLOW_UP|ALREADY_TRACKED|NON_ACTIONABLE)\b")
CLEAN_CODEX_REVIEW = re.compile(r"Codex Review:\s*Didn't find any major issues\.", re.IGNORECASE)
REVIEWED_COMMIT = re.compile(r"\*\*Reviewed commit:\*\*\s*`([0-9a-fA-F]{10,40})`")
CANONICAL_WORKFLOW_NAME = re.compile(r"^name: ([a-z0-9]+(?:-[a-z0-9]+)*)$")
CANONICAL_ROOT_KEY = re.compile(
    r"^(run-name|on|permissions|env|defaults|concurrency|jobs):(?:\s|$)"
)
ONE_SHOT_WORKFLOW = re.compile(
    r"(?<![A-Za-z0-9])(?:pr|pull(?:[^A-Za-z0-9]+request)?|issue|discovery|story|fix)"
    r"[^A-Za-z0-9]+(?:(?:no|number|id)(?=[^A-Za-z0-9])[^A-Za-z0-9]*)?\d+(?![A-Za-z0-9])",
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


def extract_workflow_name(text: str) -> str:
    """Return only the repository's canonical first-line workflow name."""
    lines = text.splitlines()
    if not lines:
        return UNRESOLVED_WORKFLOW_NAME
    match = CANONICAL_WORKFLOW_NAME.fullmatch(lines[0])
    if not match:
        return UNRESOLVED_WORKFLOW_NAME
    return match.group(1)


def canonical_workflow_name_violations(filename: str, text: str) -> list[str]:
    expected = filename.rsplit(".", 1)[0]
    workflow_name = extract_workflow_name(text)
    if workflow_name == UNRESOLVED_WORKFLOW_NAME:
        return [
            f"{filename}: workflow must start with exactly "
            f"'name: {expected}' using an unquoted, unindented plain scalar"
        ]
    if workflow_name != expected:
        return [
            f"{filename}: canonical workflow name {workflow_name!r} "
            f"must equal filename stem {expected!r}"
        ]

    seen_root_keys = {"name"}
    for line in text.splitlines()[1:]:
        if not line or line.lstrip().startswith("#") or line[0].isspace():
            continue
        root_key = CANONICAL_ROOT_KEY.match(line)
        if not root_key:
            return [
                f"{filename}: non-canonical or unknown top-level YAML key is forbidden: "
                f"{line!r}"
            ]
        key = root_key.group(1)
        if key in seen_root_keys:
            return [f"{filename}: duplicate top-level YAML key is forbidden: {key}"]
        seen_root_keys.add(key)
    return []




def protected_asset_violations(path: str, text: str) -> list[str]:
    """Pin complete guard-chain assets so overrides and no-op changes fail closed."""
    allowed = PROTECTED_ASSET_SHA256.get(path)
    if allowed is None:
        return [f"{path}: protected asset has no digest policy"]
    if not 1 <= len(allowed) <= 2:
        return [f"{path}: protected digest allowlist must contain one or two entries"]
    normalized = text.replace("\r\n", "\n").replace("\r", "\n")
    actual = hashlib.sha256(normalized.encode("utf-8")).hexdigest()
    if actual in allowed:
        return []
    return [
        f"{path}: protected asset changed (allowed sha256 values {sorted(allowed)}, "
        f"got {actual}); rotate safely in two PRs: first preauthorize the reviewed "
        "future digest while retaining the current digest, then change the asset in "
        "a second PR; remove the retired digest only after that change merges"
    ]


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

    for protected_path in sorted(PROTECTED_ASSET_SHA256):
        prefix = f"{WORKFLOW_DIR}/"
        if not protected_path.startswith(prefix):
            continue
        filename = protected_path.removeprefix(prefix)
        if filename not in workflow_texts:
            failures.append(f"{protected_path}: protected asset is missing")
        else:
            failures.extend(
                protected_asset_violations(protected_path, workflow_texts[filename])
            )

    for filename, text in sorted(workflow_texts.items()):
        failures.extend(canonical_workflow_name_violations(filename, text))
        workflow_name = extract_workflow_name(text)
        if ONE_SHOT_WORKFLOW.search(filename) or (
            workflow_name != UNRESOLVED_WORKFLOW_NAME
            and ONE_SHOT_WORKFLOW.search(workflow_name)
        ):
            failures.append(
                f"one-shot workflow identity is forbidden: {filename} (name={workflow_name!r})"
            )
    return failures


def pr_changes_workflow_contract(changed_paths) -> bool:
    return any(
        path == DURABLE_WORKFLOW_MANIFEST
        or path.startswith(f"{WORKFLOW_DIR}/")
        or path in PROTECTED_ASSET_SHA256
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
    for protected_path in sorted(PROTECTED_ASSET_SHA256):
        if protected_path.startswith(f"{WORKFLOW_DIR}/"):
            continue
        payload = api(f"/repos/{REPO}/contents/{protected_path}?ref={ref}")
        protected_text = decode_contents_payload(payload, protected_path)
        inventory_failures.extend(
            protected_asset_violations(protected_path, protected_text)
        )
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
