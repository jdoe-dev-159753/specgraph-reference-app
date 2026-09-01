#!/usr/bin/env python3
"""Reject competing prose work-state and stale Codex review evidence."""

from __future__ import annotations

import json
import os
import re
import sys
from urllib import error, request

API = "https://api.github.com"
REPO = os.environ.get("GITHUB_REPOSITORY", "jdoe-dev-159753/specgraph-reference-app")
TOKEN = os.environ.get("GITHUB_TOKEN", "")
EVENT_PATH = os.environ.get("GITHUB_EVENT_PATH", "")
EVENT_NAME = os.environ.get("GITHUB_EVENT_NAME", "")
CODEX_USER_ID = 199175422

PREFIX = re.compile(
    r"^\s*(?:Classification|Parent|Children|Depends on|Blocked by|Blocking|"
    r"Production PR|Implementation PR|Lifecycle|Disposition)\s*:",
    re.IGNORECASE,
)
LEGACY_TOKEN = re.compile(r"\b(?:IN_SCOPE|FOLLOW_UP|ALREADY_TRACKED|NON_ACTIONABLE)\b")


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
    if not pull_request:
        return None
    return pull_request.get("number") or event.get("number")


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


def require_current_head_codex_review(pr_number: int, failures: list[str]) -> None:
    pr = api(f"/repos/{REPO}/pulls/{pr_number}")
    if pr.get("state") != "open" or pr.get("draft"):
        return
    if (pr.get("base") or {}).get("ref") != "main":
        return

    head_sha = pr["head"]["sha"]
    reviews = pages(f"/repos/{REPO}/pulls/{pr_number}/reviews")
    if has_current_head_codex_review(reviews, head_sha):
        print(f"pull request #{pr_number}: Codex reviewed current head {head_sha[:12]}")
        return

    failures.append(
        f"pull request #{pr_number}: no Codex review is anchored to current head {head_sha[:12]}"
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
    # descriptions. Review freshness is anchored to GitHub's native review.commit_id,
    # immutable Codex bot user id, and pull_request.head.sha identities rather than
    # prose, mutable account names, or commit timestamps.
    pr_number = event_pr_number()
    if pr_number is not None:
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

    print("controlled GitHub work descriptions and exact-head Codex review evidence are clean")
    return 0


if __name__ == "__main__":
    sys.exit(main())
