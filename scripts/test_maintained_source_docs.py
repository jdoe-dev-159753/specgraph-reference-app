"""Verifies complete inventory and intent boundaries for maintained source formats."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from scripts import maintained_source_docs as docs


class MaintainedSourceDocsTests(unittest.TestCase):
    def write(self, root: Path, relative: str, text: str) -> Path:
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        return path

    def test_inventory_groups_commentable_and_manifested_sources(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write(root, "scripts/tool.py", '"""Preserves a deterministic workflow boundary for reproducible review evidence."""\n')
            self.write(root, "scripts/tool.sh", "#!/bin/sh\n# Preserves a deterministic workflow boundary for reproducible review evidence.\n")
            self.write(root, ".github/workflows/verify.yml", "name: verify\n# Preserves a deterministic workflow boundary for reproducible review evidence.\n")
            self.write(root, "compose.yaml", "# Preserves a deterministic runtime boundary for reproducible reviewer execution.\nservices: {}\n")
            self.write(root, "backend/pom.xml", "<project/>\n")
            self.write(
                root,
                "scripts/ci/source-intent-inventory.tsv",
                "# Maps non-commentable configuration intent into the generated source reference.\n",
            )
            self.assertEqual(
                ["workflow", "manifested-config", "compose", "text-config", "python", "shell"],
                [entry.kind for entry in docs.inventory(root)],
            )

    def test_missing_file_intent_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write(root, "scripts/tool.sh", "#!/bin/sh\nset -eu\n")
            self.write(
                root,
                "scripts/ci/source-intent-inventory.tsv",
                "# Maps non-commentable configuration intent into the generated source reference.\n",
            )
            findings, _ = docs.audit(root)
            self.assertTrue(any("missing file-level intent" in finding.reason for finding in findings))

    def test_placeholder_sized_comment_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write(root, "scripts/tool.sh", "#!/bin/sh\n# Runs the tool.\n")
            self.write(
                root,
                "scripts/ci/source-intent-inventory.tsv",
                "# Maps non-commentable configuration intent into the generated source reference.\n",
            )
            findings, _ = docs.audit(root)
            self.assertTrue(any("at least eight words" in finding.reason for finding in findings))

    def test_manifest_must_cover_non_commentable_configuration_exactly(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write(root, "backend/pom.xml", "<project/>\n")
            self.write(root, "scripts/ci/source-intent-inventory.tsv", "")
            findings, _ = docs.audit(root)
            self.assertTrue(any(finding.path.name == "pom.xml" for finding in findings))

    def test_protected_source_can_use_manifested_intent_without_byte_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write(root, ".github/workflows/work-graph-guard.yml", "name: work-graph-guard\non: {}\n")
            self.write(
                root,
                "scripts/ci/source-intent-inventory.tsv",
                "# Maps non-commentable configuration intent into the generated source reference.\n"
                ".github/workflows/work-graph-guard.yml\t"
                "Enforces the protected workflow boundary and exact-head review evidence.\n",
            )
            findings, documented = docs.audit(root)
            self.assertEqual([], findings)
            self.assertEqual(
                {
                    ".github/workflows/work-graph-guard.yml",
                    "scripts/ci/source-intent-inventory.tsv",
                },
                {entry.path.relative_to(root).as_posix() for entry, _ in documented},
            )

    def test_complete_intention_inventory_can_render_html(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write(root, "scripts/tool.py", '"""Preserves a deterministic workflow boundary for reproducible review evidence."""\n')
            self.write(
                root,
                "scripts/ci/source-intent-inventory.tsv",
                "# Maps non-commentable configuration intent into the generated source reference.\n",
            )
            findings, documented = docs.audit(root)
            self.assertEqual([], findings)
            output = root / "target/reference/index.html"
            docs.render_html(root, documented, output)
            self.assertIn("scripts/tool.py", output.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
