#!/usr/bin/env python3
"""One-shot reconciliation of bootstrap prose work-state into GitHub-native relations."""

from __future__ import annotations

import json
import os
import sys
from urllib import error, request

API = "https://api.github.com"
REPO = os.environ.get("GITHUB_REPOSITORY", "jdoe-dev-159753/specgraph-reference-app")
TOKEN = os.environ["GITHUB_TOKEN"]

PARENT_CHILDREN = {5: (6, 7, 8)}
BLOCKED_BY = {
    3: (8,),
    7: (6,),
    8: (6, 7),
}

DROP_PREFIXES = (
    "Classification:",
    "Parent:",
    "Children:",
    "Depends on:",
)


def api(method: str, path: str, payload: dict | None = None):
    data = None if payload is None else json.dumps(payload).encode()
    req = request.Request(f"{API}{path}", data=data, method=method)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("Authorization", f"Bearer {TOKEN}")
    req.add_header("X-GitHub-Api-Version", "2026-03-10")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with request.urlopen(req, timeout=30) as response:
            raw = response.read()
            return None if not raw else json.loads(raw)
    except error.HTTPError as exc:
        detail = exc.read().decode(errors="replace")
        raise RuntimeError(f"GitHub API {method} {path} failed: {exc.code} {detail}") from exc


def issue(number: int) -> dict:
    return api("GET", f"/repos/{REPO}/issues/{number}")


def ensure_sub_issue(parent: int, child: int, child_id: int) -> None:
    current = api("GET", f"/repos/{REPO}/issues/{parent}/sub_issues?per_page=100")
    if child in {item["number"] for item in current}:
        return
    api("POST", f"/repos/{REPO}/issues/{parent}/sub_issues", {"sub_issue_id": child_id})


def ensure_blocked_by(blocked: int, blocker: int, blocker_id: int) -> None:
    current = api("GET", f"/repos/{REPO}/issues/{blocked}/dependencies/blocked_by?per_page=100")
    if blocker in {item["number"] for item in current}:
        return
    api(
        "POST",
        f"/repos/{REPO}/issues/{blocked}/dependencies/blocked_by",
        {"issue_id": blocker_id},
    )


def clean_body(number: int, body: str) -> str:
    lines = [line for line in body.splitlines() if not line.startswith(DROP_PREFIXES)]
    cleaned = "\n".join(lines)

    if number == 3:
        cleaned = cleaned.replace("## Dependency", "## Integration sequencing")
        cleaned = cleaned.replace(
            "Blocked by the reusable consumer boundary defined in `specgraph-harness` issue #18 and by whichever harness capabilities the first end-to-end slice actually exercises.",
            "The reusable consumer boundary remains owned by `specgraph-harness` issue #18; this application consumes only harness capabilities that are already available and does not place them on its delivery critical path.",
        )
    elif number == 4:
        cleaned = cleaned.replace(
            "- require FOLLOW_UP capture instead of scope expansion;",
            "- create or reuse a GitHub issue for independently reviewable discoveries instead of expanding the current change;",
        )
    elif number == 5:
        cleaned = cleaned.replace(
            "The fourth layer is `ALREADY_TRACKED` by #3 plus the harness-side #18/#40/#41/#12/#81/#82 family. This repository should provide domain data/configuration, not reimplement generic traceability machinery.",
            "Generic traceability and generated-view integration remain owned by #3 plus the harness-side #18/#40/#41/#12/#81/#82 family. This repository should provide domain data/configuration, not reimplement generic traceability machinery.",
        )

    while "\n\n\n" in cleaned:
        cleaned = cleaned.replace("\n\n\n", "\n\n")
    return cleaned.strip() + "\n"


def verify(ids: dict[int, int]) -> None:
    for parent, children in PARENT_CHILDREN.items():
        current = api("GET", f"/repos/{REPO}/issues/{parent}/sub_issues?per_page=100")
        actual = {item["number"] for item in current}
        missing = set(children) - actual
        if missing:
            raise RuntimeError(f"missing native sub-issues for #{parent}: {sorted(missing)}")

    for blocked, blockers in BLOCKED_BY.items():
        current = api("GET", f"/repos/{REPO}/issues/{blocked}/dependencies/blocked_by?per_page=100")
        actual = {item["number"] for item in current}
        missing = set(blockers) - actual
        if missing:
            raise RuntimeError(f"missing native blockers for #{blocked}: {sorted(missing)}")

    for number in range(3, 9):
        body = issue(number).get("body") or ""
        if any(line.startswith(DROP_PREFIXES) for line in body.splitlines()):
            raise RuntimeError(f"prose work-state prefix remains in issue #{number}")
        for token in ("IN_SCOPE", "FOLLOW_UP", "ALREADY_TRACKED", "NON_ACTIONABLE"):
            if token in body:
                raise RuntimeError(f"legacy work-state token {token} remains in issue #{number}")


def main() -> int:
    snapshots = {number: issue(number) for number in range(3, 9)}
    ids = {number: item["id"] for number, item in snapshots.items()}

    for parent, children in PARENT_CHILDREN.items():
        for child in children:
            ensure_sub_issue(parent, child, ids[child])

    for blocked, blockers in BLOCKED_BY.items():
        for blocker in blockers:
            ensure_blocked_by(blocked, blocker, ids[blocker])

    # Native relations exist before competing prose is removed.
    for number, snapshot in snapshots.items():
        body = snapshot.get("body") or ""
        cleaned = clean_body(number, body)
        if cleaned != body:
            api("PATCH", f"/repos/{REPO}/issues/{number}", {"body": cleaned})

    verify(ids)
    print("native work-graph reconciliation complete")
    return 0


if __name__ == "__main__":
    sys.exit(main())
