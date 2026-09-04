#!/usr/bin/env python3
"""Request one Codex review after every applicable exact-head workflow succeeds."""

from __future__ import annotations

import json
import os
import sys
from urllib import parse, request

API = "https://api.github.com"
REPO = os.environ.get("GITHUB_REPOSITORY", "jdoe-dev-159753/specgraph-reference-app")
TOKEN = os.environ.get("GITHUB_TOKEN", "")
EVENT_PATH = os.environ.get("GITHUB_EVENT_PATH", "")
CODEX_USER_ID = 199175422
MARKER = "<!-- codex-review-request:{sha} -->"
RECOVERY_JOBS = {
    "application-ci": "fast-verify",
    "plantuml-diagrams": "verify",
    "r4-acceptance-ci": "verify-r4-acceptance",
}
WORKFLOW_PATHS = {
    "application-ci": ".github/workflows/application-ci.yml",
    "plantuml-diagrams": ".github/workflows/plantuml-diagrams.yml",
    "r4-acceptance-ci": ".github/workflows/r4-acceptance-ci.yml",
    "codex-review-fan-in-tests": ".github/workflows/codex-review-fan-in-tests.yml",
    "work-graph-guard-tests": ".github/workflows/work-graph-guard-tests.yml",
}


def api(path: str, method: str = "GET", payload: dict | None = None):
    data = json.dumps(payload).encode() if payload is not None else None
    call = request.Request(f"{API}{path}", data=data, method=method)
    call.add_header("Accept", "application/vnd.github+json")
    call.add_header("X-GitHub-Api-Version", "2026-03-10")
    call.add_header("Authorization", f"Bearer {TOKEN}")
    if data is not None:
        call.add_header("Content-Type", "application/json")
    with request.urlopen(call, timeout=30) as response:
        return json.loads(response.read())


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


def expected_workflows(paths: list[str]) -> set[str]:
    def prefixed(*prefixes):
        return any(path.startswith(prefix) for path in paths for prefix in prefixes)

    expected: set[str] = set()
    application_exact = {
        "compose.yaml", "compose.oci.yaml", "compose.r4.yaml",
        "scripts/test-r4-gallery.sh", "scripts/analyze_dataset_ceiling.py",
        "scripts/test_analyze_dataset_ceiling.py", "scripts/validate_design_map.py",
        "docs/analysis/dataset-ceiling.md",
        ".github/workflows/application-ci.yml",
    }
    if prefixed("backend/", "frontend/") or any(
        path in application_exact or (path.startswith("scripts/r4-") and path.endswith(".sh"))
        for path in paths
    ):
        expected.add("application-ci")

    r4_exact = {
        "compose.r4.yaml", "compose.r4.ci-cache.yaml", ".dockerignore",
        ".github/workflows/r4-acceptance-ci.yml",
    }
    if prefixed("backend/", "frontend/", "e2e/", "docker/", "scripts/ci/") or any(
        path in r4_exact for path in paths
    ):
        expected.add("r4-acceptance-ci")

    if any(
        (path.startswith("docs/assignment/") and path.endswith((".puml", ".dot", ".svg")))
        or path in {"scripts/render_plantuml.sh", ".github/workflows/plantuml-diagrams.yml"}
        for path in paths
    ):
        expected.add("plantuml-diagrams")

    if any(path in {
        "scripts/codex_review_fan_in.py",
        "scripts/test_codex_review_fan_in.py",
        ".github/workflows/codex-review-fan-in.yml",
        ".github/workflows/codex-review-fan-in-tests.yml",
    } for path in paths):
        expected.add("codex-review-fan-in-tests")

    if any(path in {
        "scripts/work_graph_guard.py",
        "scripts/test_work_graph_guard.py",
        "scripts/ci/durable-workflows.txt",
        ".github/workflows/work-graph-guard.yml",
        ".github/workflows/work-graph-guard-tests.yml",
    } for path in paths):
        expected.add("work-graph-guard-tests")
    return expected


def requires_manual_review(paths: list[str]) -> bool:
    return any(path.startswith(".github/workflows/") for path in paths)


def gate_state(expected: set[str], runs: list[dict]) -> tuple[str, list[str]]:
    latest: dict[str, dict] = {}
    for run in runs:
        name = run.get("name")
        if (
            name in expected
            and run.get("path") == WORKFLOW_PATHS.get(name)
            and run.get("event") in {"pull_request", "workflow_dispatch"}
        ):
            if run.get("event") == "workflow_dispatch":
                required_job = RECOVERY_JOBS.get(name)
                if not required_job or not any(
                    job.get("name") == required_job and job.get("conclusion") == "success"
                    for job in run.get("jobs", [])
                ):
                    continue
            if name not in latest or run.get("id", 0) > latest[name].get("id", 0):
                latest[name] = run
    missing = sorted(expected - latest.keys())
    waiting = sorted(name for name, run in latest.items() if run.get("status") != "completed")
    blocked = sorted(
        name for name, run in latest.items()
        if run.get("status") == "completed" and run.get("conclusion") != "success"
    )
    if blocked:
        return "blocked", blocked
    if missing or waiting:
        return "waiting", missing + waiting
    return "ready", []


def event_pr_number(event: dict) -> int | None:
    if event.get("pull_request"):
        return event["pull_request"].get("number") or event.get("number")
    if (event.get("inputs") or {}).get("pr_number"):
        return int(event["inputs"]["pr_number"])
    pull_requests = (event.get("workflow_run") or {}).get("pull_requests") or []
    return pull_requests[0].get("number") if pull_requests else None


def stale_workflow_event(event: dict, head_sha: str) -> bool:
    event_sha = (event.get("workflow_run") or {}).get("head_sha")
    return bool(event_sha and event_sha != head_sha)


def review_already_requested(
    comments: list[dict], reviews: list[dict], head_sha: str, requester_id: int
) -> bool:
    marker = MARKER.format(sha=head_sha)
    if any(
        marker in (comment.get("body") or "")
        and (comment.get("user") or {}).get("id") == requester_id
        for comment in comments
    ):
        return True
    if any(
        (review.get("user") or {}).get("id") == CODEX_USER_ID
        and review.get("commit_id") == head_sha
        for review in reviews
    ):
        return True
    prefix = head_sha[:7].lower()
    return any(
        (comment.get("user") or {}).get("id") == CODEX_USER_ID
        and prefix in (comment.get("body") or "").lower()
        for comment in comments
    )


def workflow_runs_path(head_sha: str) -> str:
    query = parse.urlencode({"head_sha": head_sha, "per_page": 100})
    return f"/repos/{REPO}/actions/runs?{query}"


def main() -> int:
    if not TOKEN or not EVENT_PATH:
        print("GITHUB_TOKEN and GITHUB_EVENT_PATH are required", file=sys.stderr)
        return 2
    with open(EVENT_PATH, encoding="utf-8") as event_file:
        event = json.load(event_file)
    number = event_pr_number(event)
    if number is None and (event.get("workflow_run") or {}).get("head_sha"):
        event_sha = event["workflow_run"]["head_sha"]
        candidates = list(pages(f"/repos/{REPO}/commits/{event_sha}/pulls"))
        candidate = next(
            (
                item for item in candidates
                if item.get("state") == "open" and (item.get("base") or {}).get("ref") == "main"
            ),
            None,
        )
        number = candidate.get("number") if candidate else None
    if number is None:
        print("no pull request is associated with this event")
        return 0
    pr = api(f"/repos/{REPO}/pulls/{number}")
    head_sha = pr["head"]["sha"]
    if pr.get("state") != "open" or pr.get("draft") or pr["base"]["ref"] != "main":
        print(f"pull request #{number} is not an open main-targeting review candidate")
        return 0
    if stale_workflow_event(event, head_sha):
        print(f"ignoring stale workflow event for pull request #{number}")
        return 0

    changed = list(pages(f"/repos/{REPO}/pulls/{number}/files"))
    paths = [
        path for item in changed for path in (item.get("filename"), item.get("previous_filename"))
        if path
    ]
    if requires_manual_review(paths):
        print(f"pull request #{number}: workflow source changed; manual exact-head review required")
        return 0
    expected = expected_workflows(paths)
    runs = api(workflow_runs_path(head_sha)).get("workflow_runs", [])
    for run in runs:
        if run.get("name") in RECOVERY_JOBS and run.get("event") == "workflow_dispatch":
            run["jobs"] = api(f"/repos/{REPO}/actions/runs/{run['id']}/jobs").get("jobs", [])
    state, names = gate_state(expected, runs)
    if state != "ready":
        print(f"pull request #{number}: deterministic workflows {state}: {', '.join(names)}")
        return 0

    comments = list(pages(f"/repos/{REPO}/issues/{number}/comments"))
    reviews = list(pages(f"/repos/{REPO}/pulls/{number}/reviews"))
    if review_already_requested(comments, reviews, head_sha, api("/user")["id"]):
        print(f"pull request #{number}: Codex review already requested for {head_sha[:12]}")
        return 0
    body = f"{MARKER.format(sha=head_sha)}\n@codex review"
    api(f"/repos/{REPO}/issues/{number}/comments", method="POST", payload={"body": body})
    print(f"pull request #{number}: requested Codex review for {head_sha[:12]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
