#!/usr/bin/env python3
"""Reject competing prose work-state and stale PR review requests."""

from __future__ import annotations

from datetime import datetime
import json
import os
import re
import sys
from urllib import error, request

API = "https://api.github.com"
REPO = os.environ.get("GITHUB_REPOSITORY", "jdoe-dev-159753/specgraph-reference-app")
TOKEN = os.environ.get("GITHUB_TOKEN", "")
EVENT_PATH = os.environ.get("GITHUB_EVENT_PATH", "")

PREFIX = re.compile(
    r"^\s*(?:Classification|Parent|Children|Depends on|Blocked by|Blocking|"
    r"Production PR|Implementation PR|Lifecycle|Disposition)\s*:",
    re.IGNORECASE,
)
LEGACY_TOKEN = re.compile(r"\b(?:IN_SCOPE|FOLLOW_UP|ALREADY_TRACKED|NON_ACTIONABLE)\b")
CODEX_REVIEW_REQUEST = re.compile(r"(?im)^\s*@codex\s+review\b")


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


def parse_time(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def event_pr_number() -> int | None:
    if not EVENT_PATH or not os.path.exists(EVENT_PATH):
        return None
    with open(EVENT_PATH, encoding="utf-8") as handle:
        event = json.load(handle)
    pull_request = event.get("pull_request")
    if pull_request:
        return pull_request.get("number") or event.get("number")
    issue = event.get("issue") or {}
    if issue.get("pull_request"):
        return issue.get("number")
    return None


def require_current_head_codex_request(pr_number: int, failures: list[str]) -> None:
    pr = api(f"/repos/{REPO}/pulls/{pr_number}")
    if pr.get("state") != "open" or pr.get("draft"):
        return

    head_sha = pr["head"]["sha"]
    commit = api(f"/repos/{REPO}/commits/{head_sha}")
    head_time = parse_time(commit["commit"]["committer"]["date"])

    for comment in pages(f"/repos/{REPO}/issues/{pr_number}/comments"):
        if not CODEX_REVIEW_REQUEST.search(comment.get("body") or ""):
            continue
        if parse_time(comment["created_at"]) >= head_time:
            print(f"pull request #{pr_number}: current-head Codex review explicitly requested")
            return

    failures.append(
        f"pull request #{pr_number}: no explicit @codex review request exists after current head {head_sha[:12]}"
    )


def main() -> int:
    failures: list[str] = []
    for item in pages(f"/repos/{REPO}/issues?state=open"):
        number = item["number"]
        kind = "pull request" if "pull_request" in item else "issue"
        scan_surface(kind, f"#{number} title", item.get("title") or "", failures)
        scan_surface(kind, f"#{number} body", item.get("body") or "", failures)

    # Conversation and review comments are discussion, not controlled work-state
    # descriptions. They may legitimately quote typed values while reviewing the
    # mechanics. Treating third-party review prose as authoritative would make the
    # ratchet both noisy and impossible for the repository owner to remediate.

    pr_number = event_pr_number()
    if pr_number is not None:
        require_current_head_codex_request(pr_number, failures)

    if failures:
        print("Work-graph/review guard failed:", file=sys.stderr)
        for finding in failures:
            print(f"- {finding}", file=sys.stderr)
        return 1

    print("controlled GitHub work descriptions and current-head review request are clean")
    return 0


if __name__ == "__main__":
    sys.exit(main())
