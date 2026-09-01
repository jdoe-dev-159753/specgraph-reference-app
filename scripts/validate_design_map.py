#!/usr/bin/env python3
"""Fail when normative SRS requirements silently disappear from design-map.yaml.

This deliberately avoids adding a YAML dependency to the reviewer toolchain. Both controlled
files use stable two-space top-level mapping conventions, so the ratchet extracts only the
IDs whose presence is mechanically decidable and leaves semantic adequacy to review.
"""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
REQUIREMENTS = ROOT / "docs/assignment/SRS/requirements.yaml"
DESIGN_MAP = ROOT / "docs/assignment/SDD/design-map.yaml"


def section(text: str, name: str) -> str:
    match = re.search(rf"(?ms)^{re.escape(name)}:\n(.*?)(?=^[A-Za-z_][A-Za-z0-9_-]*:\n|\Z)", text)
    if not match:
        raise SystemExit(f"missing top-level section {name!r}")
    return match.group(1)


def mapping_ids(block: str) -> set[str]:
    return set(re.findall(r"(?m)^  ([A-Z][A-Z0-9-]+):", block))


srs = REQUIREMENTS.read_text(encoding="utf-8")
design = DESIGN_MAP.read_text(encoding="utf-8")

normative_requirements = mapping_ids(section(srs, "requirements"))
design_mappings = mapping_ids(section(design, "requirement_design"))

missing = sorted(normative_requirements - design_mappings)
if missing:
    print("Normative SRS requirements missing from design-map requirement_design:", file=sys.stderr)
    for requirement_id in missing:
        print(f"  - {requirement_id}", file=sys.stderr)
    raise SystemExit(1)

# CON-AI-002 is a normative confidentiality constraint whose design consequence must stay
# explicit even though constraints are kept in a separate SRS section.
if "CON-AI-002" not in design_mappings:
    raise SystemExit("CON-AI-002 must remain explicitly mapped in design-map.yaml")

# Invariants are normally attached to project-owned contracts rather than duplicated as
# requirement_design entries. They must nevertheless remain referenced somewhere by ID.
invariants = mapping_ids(section(srs, "invariants"))
unreferenced_invariants = sorted(invariant for invariant in invariants if invariant not in design)
if unreferenced_invariants:
    print("SRS invariants absent from the machine design map:", file=sys.stderr)
    for invariant_id in unreferenced_invariants:
        print(f"  - {invariant_id}", file=sys.stderr)
    raise SystemExit(1)

print(
    f"Design coverage ratchet OK: {len(normative_requirements)} requirements, "
    f"{len(invariants)} invariants, CON-AI-002 explicit."
)
