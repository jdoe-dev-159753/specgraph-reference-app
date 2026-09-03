#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[2]
workflow_dir = root / ".github" / "workflows"
manifest = root / "scripts" / "ci" / "durable-workflows.txt"

allowed = {
    line.strip()
    for line in manifest.read_text(encoding="utf-8").splitlines()
    if line.strip() and not line.lstrip().startswith("#")
}
actual = {
    path.name
    for path in workflow_dir.iterdir()
    if path.is_file() and path.suffix in {".yml", ".yaml"}
}

missing = sorted(allowed - actual)
unexpected = sorted(actual - allowed)
errors: list[str] = []
if missing:
    errors.append("durable workflow manifest entries missing from the repository: " + ", ".join(missing))
if unexpected:
    errors.append(
        "workflow files are not declared durable: "
        + ", ".join(unexpected)
        + "; reuse/parameterize an existing durable workflow or deliberately update scripts/ci/durable-workflows.txt"
    )

one_shot = re.compile(r"(?:^|[-_.])(pr|pull|issue|discovery|story|fix)[-_#.]?\d+(?:[-_.]|$)", re.IGNORECASE)
for filename in sorted(actual):
    path = workflow_dir / filename
    text = path.read_text(encoding="utf-8")
    match = re.search(r"(?m)^name:\s*['\"]?([^'\"\n]+)", text)
    workflow_name = match.group(1).strip() if match else ""
    if one_shot.search(filename) or (workflow_name and one_shot.search(workflow_name)):
        errors.append(f"one-shot workflow identity is forbidden: {filename} (name={workflow_name!r})")

if errors:
    for error in errors:
        print(f"workflow-hygiene: {error}", file=sys.stderr)
    raise SystemExit(1)

print(f"Workflow hygiene OK: {len(actual)} durable workflow files, no one-shot identities.")
