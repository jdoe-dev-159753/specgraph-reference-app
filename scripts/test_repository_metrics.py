#!/usr/bin/env python3
"""Verify deterministic repository metrics generation, badge output, and stale-state ratchets."""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


SCRIPT = Path(__file__).with_name("repository-metrics.sh")


class RepositoryMetricsTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.repo = Path(self.temporary.name)
        (self.repo / "scripts").mkdir()
        (self.repo / "docs" / "reviewer").mkdir(parents=True)
        shutil.copy2(SCRIPT, self.repo / "scripts" / SCRIPT.name)
        (self.repo / "README.md").write_text(
            "# Fixture\n\n"
            "<!-- repository-metrics-badge:start -->\n"
            "[![stale](https://example.invalid/stale)](stale)\n"
            "<!-- repository-metrics-badge:end -->\n",
            encoding="utf-8",
        )
        (self.repo / "docs" / "reviewer" / "repository-metrics.md").write_text(
            "stale report\n", encoding="utf-8"
        )
        (self.repo / "main.py").write_text("print('counted')\n", encoding="utf-8")
        for excluded in ("generated", "vendor", "private"):
            path = self.repo / excluded
            path.mkdir()
            (path / "excluded.py").write_text("raise SystemExit('must not be counted')\n", encoding="utf-8")
        presentation_output = self.repo / "docs" / "presentation" / "output"
        presentation_output.mkdir(parents=True)
        (presentation_output / "generated-deck.pptx").write_bytes(b"generated presentation")
        (self.repo / "authored-notes.docx").write_bytes(b"binary document")
        (self.repo / "package-lock.json").write_text("{}\n", encoding="utf-8")

        self.fake_cloc = self.repo / "scripts" / "fake-cloc.sh"
        self.fake_cloc.write_text(
            textwrap.dedent(
                """\
                #!/usr/bin/env bash
                set -euo pipefail
                files="$(cat)"
                grep -Fqx 'README.md' <<<"${files}"
                grep -Fqx 'main.py' <<<"${files}"
                if grep -Eq '(^|/)(generated|vendor|private)(/|$)|package-lock\\.json$|docs/presentation/output/|\\.(pptx|docx)$' <<<"${files}"; then
                  echo 'excluded input reached cloc' >&2
                  exit 21
                fi
                if awk '
                  $0 == "<!-- repository-metrics-badge:start -->" { inside = 1; next }
                  $0 == "<!-- repository-metrics-badge:end -->" { inside = 0; next }
                  inside && NF { found = 1 }
                  END { exit found ? 0 : 1 }
                ' README.md; then
                  echo 'managed README badges were not removed' >&2
                  exit 22
                fi
                case "${FAKE_CLOC_MODE:-valid}" in
                  duplicate)
                    extra_row='C++|1|0|0|1'
                    total=1235
                    ;;
                  malformed)
                    extra_row='Python|one|0|0|1'
                    total=1234
                    ;;
                  mismatched-sum)
                    extra_row=''
                    total=1235
                    ;;
                  valid)
                    extra_row=''
                    total=1234
                    ;;
                  *) exit 23 ;;
                esac
                cat <<EOF
                cloc|fixture
                --- | ---

                Language|files|blank|comment|code
                :-------|-------:|-------:|-------:|-------:
                C++|2|0|0|700
                Objective-C|1|0|0|300
                Bourne Shell|1|0|0|234
                ${extra_row}
                --------|--------|--------|--------|--------
                SUM:|4|0|0|${total}
                EOF
                """
            ),
            encoding="utf-8",
            newline="\n",
        )
        self.fake_cloc.chmod(0o755)

        subprocess.run(["git", "init", "--quiet"], cwd=self.repo, check=True)
        subprocess.run(["git", "config", "user.name", "Metrics Fixture"], cwd=self.repo, check=True)
        subprocess.run(["git", "config", "user.email", "metrics@example.invalid"], cwd=self.repo, check=True)
        subprocess.run(["git", "add", "."], cwd=self.repo, check=True)
        subprocess.run(["git", "commit", "--quiet", "-m", "fixture"], cwd=self.repo, check=True)

        self.bash = os.environ.get("SPECGRAPH_TEST_BASH") or shutil.which("bash")
        if not self.bash:
            self.skipTest("bash is required")

    def tearDown(self):
        self.temporary.cleanup()

    def run_metrics(
        self,
        mode,
        *,
        check=True,
        fake_cloc_mode="valid",
        test_mode=True,
        use_override=True,
    ):
        environment = os.environ.copy()
        environment.pop("REPOSITORY_METRICS_CLOC_BIN", None)
        environment.pop("REPOSITORY_METRICS_TEST_MODE", None)
        if use_override:
            environment["REPOSITORY_METRICS_CLOC_BIN"] = "scripts/fake-cloc.sh"
        if test_mode:
            environment["REPOSITORY_METRICS_TEST_MODE"] = "1"
        environment["FAKE_CLOC_MODE"] = fake_cloc_mode
        return subprocess.run(
            [self.bash, "scripts/repository-metrics.sh", mode],
            cwd=self.repo,
            env=environment,
            check=check,
            capture_output=True,
            text=True,
        )

    def test_generate_is_idempotent_and_escapes_badge_components(self):
        self.run_metrics("generate")
        first_readme = (self.repo / "README.md").read_bytes()
        first_report = (self.repo / "docs" / "reviewer" / "repository-metrics.md").read_bytes()

        readme = first_readme.decode("utf-8")
        expected = [
            "Authored_LOC-1%2C234-informational",
            "C%2B%2B_LOC-700-informational",
            "Objective--C_LOC-300-informational",
            "Bourne_Shell_LOC-234-informational",
        ]
        positions = [readme.index(value) for value in expected]
        self.assertEqual(positions, sorted(positions))
        self.assertEqual(1, readme.count("Authored_LOC-"))
        self.assertIn(
            "The README total and per-language badges are generated from this single cloc result",
            first_report.decode("utf-8"),
        )
        self.assertIn("TEST MODE OUTPUT", first_report.decode("utf-8"))

        self.run_metrics("generate")
        self.assertEqual(first_readme, (self.repo / "README.md").read_bytes())
        self.assertEqual(first_report, (self.repo / "docs" / "reviewer" / "repository-metrics.md").read_bytes())
        self.run_metrics("check")

    def test_check_rejects_stale_report_and_stale_badge_block(self):
        self.run_metrics("generate")
        report = self.repo / "docs" / "reviewer" / "repository-metrics.md"
        report.write_text(report.read_text(encoding="utf-8") + "stale\n", encoding="utf-8")
        self.assertNotEqual(0, self.run_metrics("check", check=False).returncode)

        self.run_metrics("generate")
        readme = self.repo / "README.md"
        readme.write_text(
            readme.read_text(encoding="utf-8").replace("C%2B%2B_LOC-700", "C%2B%2B_LOC-701"),
            encoding="utf-8",
        )
        self.assertNotEqual(0, self.run_metrics("check", check=False).returncode)

    def test_generate_rejects_duplicate_or_missing_markers(self):
        readme = self.repo / "README.md"
        original = readme.read_text(encoding="utf-8")
        malformed_values = (
            original.replace("<!-- repository-metrics-badge:end -->", ""),
            original + "<!-- repository-metrics-badge:start -->\n<!-- repository-metrics-badge:end -->\n",
        )
        for malformed in malformed_values:
            readme.write_text(malformed, encoding="utf-8")
            subprocess.run(["git", "add", "README.md"], cwd=self.repo, check=True)
            before = readme.read_bytes()
            result = self.run_metrics("generate", check=False)
            self.assertNotEqual(0, result.returncode)
            self.assertEqual(before, readme.read_bytes())

    def test_generate_rejects_inconsistent_or_malformed_cloc_tables(self):
        readme = self.repo / "README.md"
        report = self.repo / "docs" / "reviewer" / "repository-metrics.md"
        before_readme = readme.read_bytes()
        before_report = report.read_bytes()

        for mode in ("duplicate", "malformed", "mismatched-sum"):
            result = self.run_metrics("generate", check=False, fake_cloc_mode=mode)
            self.assertNotEqual(0, result.returncode, mode)
            self.assertEqual(before_readme, readme.read_bytes(), mode)
            self.assertEqual(before_report, report.read_bytes(), mode)

    def test_generate_rejects_uncommitted_authored_source(self):
        readme = self.repo / "README.md"
        report = self.repo / "docs" / "reviewer" / "repository-metrics.md"
        before_readme = readme.read_bytes()
        before_report = report.read_bytes()
        (self.repo / "main.py").write_text(
            "print('changed but not committed')\n", encoding="utf-8"
        )

        result = self.run_metrics("generate", check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(before_readme, readme.read_bytes())
        self.assertEqual(before_report, report.read_bytes())

    def test_counter_override_requires_test_mode_and_cannot_be_release_evidence(self):
        readme = self.repo / "README.md"
        report = self.repo / "docs" / "reviewer" / "repository-metrics.md"
        before_readme = readme.read_bytes()
        before_report = report.read_bytes()

        result = self.run_metrics("generate", check=False, test_mode=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("permitted only", result.stderr)
        self.assertEqual(before_readme, readme.read_bytes())
        self.assertEqual(before_report, report.read_bytes())

        self.run_metrics("generate")
        self.assertIn("TEST MODE OUTPUT", report.read_text(encoding="utf-8"))
        result = self.run_metrics(
            "check", check=False, test_mode=False, use_override=False
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("cannot be used as release evidence", result.stderr)


if __name__ == "__main__":
    unittest.main()
