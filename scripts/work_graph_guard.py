#!/usr/bin/env python3
"""Enforce canonical work state, trusted workflows, and exact-head review evidence."""

from __future__ import annotations

import ast
import base64
import hashlib
import json
import os
import re
import sys
from pathlib import Path
from urllib import error, parse, request

API = "https://api.github.com"
REPO = os.environ.get("GITHUB_REPOSITORY", "jdoe-dev-159753/specgraph-reference-app")
TOKEN = os.environ.get("GITHUB_TOKEN", "")
EVENT_PATH = os.environ.get("GITHUB_EVENT_PATH", "")
EVENT_NAME = os.environ.get("GITHUB_EVENT_NAME", "")
CODEX_USER_ID = 199175422
CODEX_APP_ID = 1144995
MIN_REVIEWED_SHA_PREFIX = 10
MIN_SUMMARY_SHA_PREFIX = 7
WORKFLOW_DIR = ".github/workflows"
DURABLE_WORKFLOW_MANIFEST = "scripts/ci/durable-workflows.txt"
GUARD_SOURCE = "scripts/work_graph_guard.py"
UNRESOLVED_WORKFLOW_NAME = "<unresolved-yaml-workflow-name>"
PROTECTED_ASSET_SHA256 = {
    ".github/workflows/work-graph-guard.yml": frozenset(
        {
            "dce4bdafcc8183eccf80c43c51cad5004d626472252e0b1e1f1eec30aa5b9751",
        }
    ),
    ".github/workflows/work-graph-guard-tests.yml": frozenset(
        {
            "22fe48af6a8ee4418643ea1f68dad53c8d5c589af0e1dedbe7573ae88e91f30c",
        }
    ),
    "scripts/test_work_graph_guard.py": frozenset(
        {
            "18b210b94a8597c65e84ba45a9fe045f46dd406db3e659c6fae1c14e5c8bec8d",
            "960e23ff7e0c66ba41729e5ed2b17cb0608984eab07e7f624ac93e661695396e",
        }
    ),
    ".github/workflows/project-v2-reconcile.yml": frozenset(
        {"0aaf3d9fe97690d8a9bbaf0789077883ae484ac850d2bf6511ccd10627f3c863"}
    ),
}
APPROVED_GUARD_SUCCESSOR_SHA256 = frozenset()

PREFIX = re.compile(
    r"^\s*(?:Classification|Parent|Children|Depends on|Blocked by|Blocking|"
    r"Production PR|Implementation PR|Lifecycle|Disposition)\s*:",
    re.IGNORECASE,
)
LEGACY_TOKEN = re.compile(r"\b(?:IN_SCOPE|FOLLOW_UP|ALREADY_TRACKED|NON_ACTIONABLE)\b")
CLEAN_CODEX_REVIEW = re.compile(r"Codex Review:\s*Didn't find any major issues\.", re.IGNORECASE)
REVIEWED_COMMIT = re.compile(r"\*\*Reviewed commit:\*\*\s*`([0-9a-fA-F]{10,40})`")
COMPLETED_CODEX_SUMMARY = re.compile(
    r"<!--\s*codex-pull-request-review-summary\s*-->.*?"
    r"\|\s*[^|\n]*\*\*Code Review\*\*\s*\|"
    r"\s*[^|\n]*\*\*Completed\*\*[^|\n]*\|"
    r"\s*`([0-9a-fA-F]{7,40})`\s*\|",
    re.DOTALL,
)
CANONICAL_WORKFLOW_NAME = re.compile(r"^name: ([a-z0-9]+(?:-[a-z0-9]+)*)$")
CANONICAL_ROOT_KEY = re.compile(
    r"^(run-name|on|permissions|env|defaults|concurrency|jobs):(?:\s|$)"
)
ONE_SHOT_WORKFLOW = re.compile(
    r"(?<![A-Za-z0-9])(?:pr|pull(?:[^A-Za-z0-9]+request)?|issue|discovery|story|fix)"
    r"[^A-Za-z0-9]+(?:(?:no|number|id)(?=[^A-Za-z0-9])[^A-Za-z0-9]*)?\d+(?![A-Za-z0-9])",
    re.IGNORECASE,
)
CANONICAL_JOB_KEY = re.compile(r"^  ([_A-Za-z][_A-Za-z0-9-]*):(?:\s+#.*)?$")
CANONICAL_JOB_PROPERTY = re.compile(r"^    ([A-Za-z][A-Za-z0-9_-]*):(.*)$")
CANONICAL_TRIGGER = re.compile(r"^  ([a-z_]+):(.*)$")
CANONICAL_PERMISSION = re.compile(r"^(  |      )([a-z][a-z-]*): (read|write|none)$")
PRIVATE_RUNNER = "    runs-on: [self-hosted, linux, x64, specgraph-reference-app, ci, docker]"
ALLOWED_TRIGGERS = frozenset({"push", "pull_request_target", "schedule", "workflow_dispatch"})
PINNED_ACTION = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+@[0-9a-f]{40}(?:\s+#.*)?$")
REFERENCE_NUMBER = re.compile(
    r"\b(?:pull[_ -]?request|pr|issue)[^A-Za-z0-9]+"
    r"(?:(?:no|number|id)(?=[^A-Za-z0-9])[^A-Za-z0-9]*)?\d+\b",
    re.IGNORECASE,
)
REQUIRED_JOB_CLAUSES = (
    "!(github.event_name == 'pull_request' && github.event.pull_request.head.repo.full_name != github.repository)",
    "!(github.event_name == 'pull_request_target' && github.event.pull_request.head.repo.full_name != github.repository)",
    "!(github.event_name == 'pull_request_review' && github.event.pull_request.head.repo.full_name != github.repository)",
    "(github.event_name != 'pull_request_review' || github.event.review.user.id == 199175422)",
    "github.event_name != 'issues'",
)
CANONICAL_QUEUE_GROUP = "  group: ${{ (!(github.event_name == 'pull_request' && github.event.pull_request.head.repo.full_name != github.repository) && !(github.event_name == 'pull_request_target' && github.event.pull_request.head.repo.full_name != github.repository) && !(github.event_name == 'pull_request_review' && github.event.pull_request.head.repo.full_name != github.repository) && (github.event_name != 'pull_request_review' || github.event.review.user.id == 199175422) && github.event_name != 'issues' && (github.event_name != 'issue_comment' || github.event.comment.user.id == 199175422)) && 'specgraph-repository-queue' || format('specgraph-bypassed-{0}', github.run_id) }}"
DIGEST_PERMISSION_NAMES = frozenset(
    {"PROTECTED_ASSET_SHA256", "APPROVED_GUARD_SUCCESSOR_SHA256"}
)
SHA256 = re.compile(r"^[0-9a-f]{64}$")


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


def clean_codex_summary_prefix(comment: dict) -> str | None:
    if (comment.get("user") or {}).get("id") != CODEX_USER_ID:
        return None
    if (comment.get("performed_via_github_app") or {}).get("id") != CODEX_APP_ID:
        return None
    match = COMPLETED_CODEX_SUMMARY.search(comment.get("body") or "")
    if not match:
        return None
    prefix = match.group(1).lower()
    return prefix if len(prefix) >= MIN_SUMMARY_SHA_PREFIX else None


def is_codex_approval_reaction(reaction: dict) -> bool:
    return (
        reaction.get("content") == "+1"
        and (reaction.get("user") or {}).get("id") == CODEX_USER_ID
    )


def has_current_head_clean_codex_summary(comments, reactions, head_sha: str) -> bool:
    normalized = head_sha.lower()
    has_current_summary = any(
        (prefix := clean_codex_summary_prefix(comment)) is not None
        and normalized.startswith(prefix)
        for comment in comments
    )
    return has_current_summary and any(is_codex_approval_reaction(reaction) for reaction in reactions)


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

    comments = list(pages(f"/repos/{REPO}/issues/{pr_number}/comments"))
    if has_current_head_clean_codex_result(comments, head_sha):
        print(f"pull request #{pr_number}: clean Codex result covers current head {head_sha[:12]}")
        return

    reactions = pages(f"/repos/{REPO}/issues/{pr_number}/reactions")
    if has_current_head_clean_codex_summary(comments, reactions, head_sha):
        print(f"pull request #{pr_number}: clean Codex summary covers current head {head_sha[:12]}")
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


def _indented_block(lines: list[str], index: int, indent: int) -> list[str]:
    block: list[str] = []
    for line in lines[index + 1 :]:
        if line.strip() and not line.lstrip().startswith("#"):
            current = len(line) - len(line.lstrip(" "))
            if current <= indent:
                break
        block.append(line)
    return block


def _top_level_conjunctions(expression: str) -> list[str]:
    clauses: list[str] = []
    start = depth = index = 0
    quote = None
    while index < len(expression):
        char = expression[index]
        if quote:
            if char == quote:
                if index + 1 < len(expression) and expression[index + 1] == quote:
                    index += 2
                    continue
                quote = None
        elif char in {"'", '"'}:
            quote = char
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth < 0:
                raise ValueError("unbalanced parentheses")
        elif depth == 0 and expression.startswith("||", index):
            raise ValueError("top-level || is forbidden")
        elif depth == 0 and expression.startswith("&&", index):
            clauses.append(expression[start:index].strip())
            start = index + 2
            index += 1
        index += 1
    if quote or depth:
        raise ValueError("unbalanced quotes or parentheses")
    clauses.append(expression[start:].strip())
    if any(not clause for clause in clauses):
        raise ValueError("empty top-level conjunction")
    return clauses


def job_condition_violations(filename: str, job: str, line: str) -> list[str]:
    prefix, suffix = "    if: ${{ ", " }}"
    if not line.startswith(prefix) or not line.endswith(suffix):
        return [f"{filename}: job {job!r} has non-canonical job-level if syntax"]
    expression = line[len(prefix) : -len(suffix)]
    try:
        clauses = _top_level_conjunctions(expression)
    except ValueError as exc:
        return [f"{filename}: job {job!r} has unsafe job-level if: {exc}"]
    missing = [clause for clause in REQUIRED_JOB_CLAUSES if clause not in clauses]
    failures = []
    if missing:
        failures.append(
            f"{filename}: job {job!r} is missing exact trust conjunctions: "
            + ", ".join(missing)
        )
    if REFERENCE_NUMBER.search(expression):
        failures.append(f"{filename}: job {job!r} embeds a one-shot issue/PR reference")
    return failures


def trigger_violations(filename: str, lines: list[str]) -> list[str]:
    indexes = [i for i, line in enumerate(lines) if line == "on:"]
    if len(indexes) != 1:
        return [f"{filename}: expected exactly one canonical root on block"]
    failures, seen = [], set()
    for line in _indented_block(lines, indexes[0], 0):
        if not line.strip() or line.lstrip().startswith("#") or line.startswith("    "):
            continue
        match = CANONICAL_TRIGGER.fullmatch(line)
        if not match:
            failures.append(f"{filename}: non-canonical trigger entry is forbidden: {line!r}")
            continue
        event = match.group(1)
        if event in seen:
            failures.append(f"{filename}: duplicate trigger is forbidden: {event}")
        seen.add(event)
        if event not in ALLOWED_TRIGGERS:
            failures.append(f"{filename}: untrusted or unsupported trigger is forbidden: {event}")
        if REFERENCE_NUMBER.search(line):
            failures.append(f"{filename}: trigger embeds a one-shot issue/PR reference")
    if not seen:
        failures.append(f"{filename}: workflow must declare at least one trusted trigger")
    return failures


def permission_violations(filename: str, lines: list[str]) -> list[str]:
    failures = []
    for line in lines:
        if re.fullmatch(r"(?:    )?permissions\s*:.*", line) and line not in {
            "permissions:", "    permissions:"
        }:
            failures.append(f"{filename}: permissions must use a canonical block")
    for index, line in enumerate(lines):
        if line not in {"permissions:", "    permissions:"}:
            continue
        indent = 0 if line == "permissions:" else 4
        seen = set()
        for entry in _indented_block(lines, index, indent):
            if not entry.strip() or entry.lstrip().startswith("#"):
                continue
            match = CANONICAL_PERMISSION.fullmatch(entry)
            if not match or len(match.group(1)) != indent + 2:
                failures.append(f"{filename}: non-canonical permission entry is forbidden: {entry!r}")
                continue
            name, value = match.group(2), match.group(3)
            if name in seen:
                failures.append(f"{filename}: duplicate permission is forbidden: {name}")
            seen.add(name)
            if name in {"statuses", "checks"} and value == "write":
                if filename != "work-graph-guard.yml" or name != "statuses":
                    failures.append(f"{filename}: {name}: write is reserved for work-graph-guard.yml")
    return failures


def durable_workflow_policy_violations(filename: str, text: str) -> list[str]:
    lines = text.splitlines()
    failures = trigger_violations(filename, lines)
    if re.search(r"\\u[0-9a-fA-F]{4}", text):
        failures.append(f"{filename}: Unicode YAML escapes are forbidden")
    if REFERENCE_NUMBER.search("\n".join(_indented_block(lines, lines.index("on:"), 0)) if "on:" in lines else ""):
        failures.append(f"{filename}: trigger block embeds a one-shot issue/PR reference")

    concurrency = [i for i, line in enumerate(lines) if line == "concurrency:"]
    expected_queue = [CANONICAL_QUEUE_GROUP, "  cancel-in-progress: false", "  queue: max"]
    if len(concurrency) != 1 or [
        line for line in _indented_block(lines, concurrency[0], 0)
        if line.strip() and not line.lstrip().startswith("#")
    ] != expected_queue:
        failures.append(f"{filename}: concurrency must use the canonical trusted/bypassed queue")

    jobs_indexes = [i for i, line in enumerate(lines) if line == "jobs:"]
    if len(jobs_indexes) != 1:
        return failures + [f"{filename}: expected exactly one canonical jobs block"]
    job_starts = []
    for index, line in enumerate(lines[jobs_indexes[0] + 1 :], jobs_indexes[0] + 1):
        if line and not line[0].isspace():
            break
        if not line.strip() or line.lstrip().startswith("#") or line.startswith("    "):
            continue
        match = CANONICAL_JOB_KEY.fullmatch(line)
        if not match:
            failures.append(f"{filename}: non-canonical indentation-two job entry: {line!r}")
        else:
            job_starts.append((index, match.group(1)))
    names = [name for _, name in job_starts]
    if not names:
        failures.append(f"{filename}: jobs block contains no canonical job")
        return failures
    if len(names) != len(set(names)):
        failures.append(f"{filename}: duplicate job key is forbidden")

    has_root_permissions = lines.count("permissions:") == 1
    for position, (start, job) in enumerate(job_starts):
        end = job_starts[position + 1][0] if position + 1 < len(job_starts) else len(lines)
        body = lines[start + 1 : end]
        properties = []
        for line in body:
            if not line.strip() or line.lstrip().startswith("#") or line.startswith("      "):
                continue
            match = CANONICAL_JOB_PROPERTY.fullmatch(line)
            if not match:
                failures.append(f"{filename}: job {job!r} has non-canonical property: {line!r}")
            else:
                properties.append((match.group(1), line))
        keys = [key for key, _ in properties]
        if len(keys) != len(set(keys)):
            failures.append(f"{filename}: job {job!r} has duplicate job-level keys")
        if not has_root_permissions and keys.count("permissions") != 1:
            failures.append(f"{filename}: job {job!r} has no explicit token permission boundary")
        runners = [line for key, line in properties if key == "runs-on"]
        if runners != [PRIVATE_RUNNER]:
            failures.append(f"{filename}: job {job!r} must use exactly the private runner pool")
        conditions = [line for key, line in properties if key == "if"]
        if len(conditions) != 1:
            failures.append(f"{filename}: job {job!r} must have exactly one canonical job-level if")
        else:
            failures.extend(job_condition_violations(filename, job, conditions[0]))
        needs = [line for key, line in properties if key == "needs"]
        expected_needs = [] if position == 0 else [f"    needs: {names[position - 1]}"]
        if needs != expected_needs:
            failures.append(f"{filename}: jobs must form one document-order needs chain")
        matrix_entries = [index for index, line in enumerate(body) if line == "      matrix:"]
        if matrix_entries:
            strategy_entries = [index for index, line in enumerate(body) if line == "    strategy:"]
            if len(strategy_entries) != 1:
                failures.append(f"{filename}: matrix job must have one canonical strategy block")
            else:
                strategy = _indented_block(body, strategy_entries[0], 4)
                direct = [line for line in strategy if line.startswith("      ") and not line.startswith("        ")]
                if direct.count("      matrix:") != 1 or direct.count("      max-parallel: 1") != 1:
                    failures.append(f"{filename}: matrix strategy must declare exactly max-parallel: 1")

    for line in lines:
        if "runs-on:" in line and line != PRIVATE_RUNNER:
            failures.append(f"{filename}: non-canonical runner placement is forbidden: {line!r}")
        uses = re.fullmatch(r"\s+(?:-\s+)?uses:\s+(.+)", line)
        if re.search(r"[\"']?uses[\"']?\s*:", line) and not uses:
            failures.append(f"{filename}: non-canonical uses key is forbidden: {line!r}")
        elif uses and not uses.group(1).startswith(("./", "docker://")) and not PINNED_ACTION.fullmatch(uses.group(1)):
            failures.append(f"{filename}: external action must use an exact 40-hex commit: {uses.group(1)!r}")
    failures.extend(permission_violations(filename, lines))
    if filename != "project-v2-reconcile.yml" and "PROJECTS_TOKEN" in text:
        failures.append(f"{filename}: PROJECTS_TOKEN is reserved for project-v2-reconcile.yml")
    if filename == "project-v2-reconcile.yml" and (
        text.count("PROJECTS_TOKEN") != 1
        or text.count("          github-token: ${{ secrets.PROJECTS_TOKEN }}") != 1
    ):
        failures.append(f"{filename}: expected one canonical PROJECTS_TOKEN binding")
    return failures




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


def _frozenset_literals(node: ast.AST) -> frozenset[str]:
    if (
        isinstance(node, ast.Call)
        and isinstance(node.func, ast.Name)
        and node.func.id == "frozenset"
        and not node.args
        and not node.keywords
    ):
        return frozenset()
    if not (
        isinstance(node, ast.Call)
        and isinstance(node.func, ast.Name)
        and node.func.id == "frozenset"
        and len(node.args) == 1
        and not node.keywords
        and isinstance(node.args[0], ast.Set)
    ):
        raise ValueError("digest permission must be a literal frozenset")
    values = [element.value for element in node.args[0].elts if isinstance(element, ast.Constant)]
    if len(values) != len(node.args[0].elts) or not all(
        isinstance(value, str) and SHA256.fullmatch(value) for value in values
    ):
        raise ValueError("digest permissions must contain only lowercase SHA-256 values")
    return frozenset(values)


def _guard_policy_and_skeleton(text: str) -> tuple[dict[str, frozenset[str]], str]:
    tree = ast.parse(text)
    assignments: dict[str, ast.Assign] = {}
    for node in tree.body:
        if not isinstance(node, ast.Assign) or len(node.targets) != 1:
            continue
        target = node.targets[0]
        if isinstance(target, ast.Name) and target.id in DIGEST_PERMISSION_NAMES:
            if target.id in assignments:
                raise ValueError(f"duplicate digest permission assignment: {target.id}")
            assignments[target.id] = node
    if set(assignments) != DIGEST_PERMISSION_NAMES:
        raise ValueError("guard source must define both reviewed digest permission assignments")

    protected_node = assignments["PROTECTED_ASSET_SHA256"].value
    if not isinstance(protected_node, ast.Dict):
        raise ValueError("protected asset permissions must be a literal dictionary")
    protected: dict[str, frozenset[str]] = {}
    for key_node, value_node in zip(protected_node.keys, protected_node.values, strict=True):
        if not isinstance(key_node, ast.Constant) or not isinstance(key_node.value, str):
            raise ValueError("protected asset paths must be literal strings")
        if key_node.value in protected:
            raise ValueError("duplicate protected asset path")
        protected[key_node.value] = _frozenset_literals(value_node)
    if set(protected) != set(PROTECTED_ASSET_SHA256):
        raise ValueError("protected asset path set cannot change through a digest-only rotation")
    if any(not 1 <= len(values) <= 2 for values in protected.values()):
        raise ValueError("each protected asset must retain one or two reviewed digests")

    successors = _frozenset_literals(assignments["APPROVED_GUARD_SUCCESSOR_SHA256"].value)
    if len(successors) > 1:
        raise ValueError("guard source may preauthorize at most one exact successor")
    spans = sorted(
        (node.lineno - 1, node.end_lineno or node.lineno, name)
        for name, node in assignments.items()
    )
    lines = text.splitlines(keepends=True)
    skeleton, cursor = [], 0
    for start, end, name in spans:
        skeleton.extend(lines[cursor:start])
        skeleton.append(f"<{name}>\n")
        cursor = end
    skeleton.extend(lines[cursor:])
    return {**protected, GUARD_SOURCE: successors}, "".join(skeleton)


def protected_guard_source_violations(text: str) -> list[str]:
    """Accept this guard, one exact successor, or a digest-permission-only rotation."""
    normalized = text.replace("\r\n", "\n").replace("\r", "\n")
    actual = hashlib.sha256(normalized.encode("utf-8")).hexdigest()
    current = Path(__file__).read_text(encoding="utf-8").replace("\r\n", "\n").replace("\r", "\n")
    current_hash = hashlib.sha256(current.encode("utf-8")).hexdigest()
    if actual == current_hash or actual in APPROVED_GUARD_SUCCESSOR_SHA256:
        return []
    try:
        _, current_skeleton = _guard_policy_and_skeleton(current)
        _, candidate_skeleton = _guard_policy_and_skeleton(normalized)
    except (SyntaxError, ValueError) as exc:
        return [f"{GUARD_SOURCE}: invalid digest permission policy: {exc}"]
    if candidate_skeleton == current_skeleton:
        return []
    return [
        f"{GUARD_SOURCE}: protected guard source changed without an exact reviewed "
        f"successor permission (got {actual})"
    ]


def workflow_inventory_violations(
    workflow_texts: dict[str, str],
    manifest_text: str,
    require_protected_workflows: bool = False,
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
            if require_protected_workflows:
                failures.append(f"{protected_path}: protected asset is missing")
            continue
        failures.extend(
            protected_asset_violations(protected_path, workflow_texts[filename])
        )

    for filename, text in sorted(workflow_texts.items()):
        failures.extend(canonical_workflow_name_violations(filename, text))
        failures.extend(durable_workflow_policy_violations(filename, text))
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
        or path == GUARD_SOURCE
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
    if pr.get("state") != "open" or pr.get("draft") or pr.get("base", {}).get("ref") != "main":
        return

    head_sha = pr["head"]["sha"]
    ref = parse.quote(head_sha, safe="")
    manifest_payload = api(
        f"/repos/{REPO}/contents/{DURABLE_WORKFLOW_MANIFEST}?ref={ref}"
    )
    manifest_text = decode_contents_payload(manifest_payload, DURABLE_WORKFLOW_MANIFEST)

    tree = api(f"/repos/{REPO}/git/trees/{ref}?recursive=1")
    if (not isinstance(tree, dict) or tree.get("truncated") is not False
            or not isinstance(tree.get("tree"), list)):
        failures.append(f"pull request #{pr_number}: recursive Git tree is missing or truncated")
        return
    workflow_paths = sorted(
        entry.get("path", "")
        for entry in tree.get("tree", [])
        if entry.get("type") == "blob"
        and entry.get("path", "").startswith(f"{WORKFLOW_DIR}/")
        and "/" not in entry.get("path", "").removeprefix(f"{WORKFLOW_DIR}/")
        and entry.get("path", "").endswith((".yml", ".yaml"))
    )
    workflow_texts: dict[str, str] = {}
    for path in workflow_paths:
        payload = api(f"/repos/{REPO}/contents/{path}?ref={ref}")
        workflow_texts[path.removeprefix(f"{WORKFLOW_DIR}/")] = decode_contents_payload(payload, path)

    inventory_failures = workflow_inventory_violations(
        workflow_texts, manifest_text, require_protected_workflows=True
    )
    for protected_path in sorted(PROTECTED_ASSET_SHA256):
        if protected_path.startswith(f"{WORKFLOW_DIR}/"):
            continue
        payload = api(f"/repos/{REPO}/contents/{protected_path}?ref={ref}")
        protected_text = decode_contents_payload(payload, protected_path)
        inventory_failures.extend(
            protected_asset_violations(protected_path, protected_text)
        )
    guard_payload = api(f"/repos/{REPO}/contents/{GUARD_SOURCE}?ref={ref}")
    guard_text = decode_contents_payload(guard_payload, GUARD_SOURCE)
    inventory_failures.extend(protected_guard_source_violations(guard_text))
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
                require_durable_workflow_surface(item["number"], failures)
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
