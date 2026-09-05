import tempfile
import unittest
from pathlib import Path

from scripts.validate_verification_markers import evidence_markers, inventory, validate


CONTROLLED = (
    "VFY-CUSTOMER-READ-001",
    "VFY-AUTH-001",
    "VFY-ANALYSIS-CONTRACT-001",
    "VFY-RAG-001",
    "VFY-HISTORY-001",
    "VFY-REPRODUCIBILITY-001",
    "VFY-DETERMINISM-001",
    "VFY-FAILURE-PATHS-001",
    "VFY-CONFIDENTIALITY-001",
    "VFY-DELIVERY-001",
)


class VerificationMarkerTests(unittest.TestCase):
    def test_repository_inventory_resolves_exactly_ten_controlled_obligations(self):
        result = validate()

        self.assertEqual(frozenset(CONTROLLED), result.catalogue_ids)
        self.assertFalse(result.unknown)
        self.assertFalse(result.missing)

    def test_unknown_marker_is_rejected(self):
        with self.fixture(CONTROLLED, ["VFY-NOT-CONTROLLED-001"]) as root:
            result = inventory(root)

            self.assertEqual(frozenset({"VFY-NOT-CONTROLLED-001"}), result.unknown)
            with self.assertRaisesRegex(ValueError, "unknown executable V&V markers"):
                validate(root)

    def test_obligation_without_executable_marker_is_exposed(self):
        with self.fixture(CONTROLLED, CONTROLLED[:-1]) as root:
            result = inventory(root)

            self.assertEqual(frozenset({"VFY-DELIVERY-001"}), result.missing)
            with self.assertRaisesRegex(ValueError, "obligations without discoverable executable evidence"):
                validate(root)

    def test_only_junit_tags_and_playwright_test_titles_are_markers(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            java = root / "Evidence.java"
            java.write_text(
                '// VFY-NOT-A-TAG-001\n@Tag("VFY-HISTORY-001")\n',
                encoding="utf-8",
            )
            playwright = root / "evidence.spec.ts"
            playwright.write_text(
                "// VFY-NOT-A-TEST-001\n"
                "test('VFY-DELIVERY-001 executable slice', async () => {});\n",
                encoding="utf-8",
            )

            self.assertEqual(frozenset({"VFY-HISTORY-001"}), evidence_markers(java))
            self.assertEqual(frozenset({"VFY-DELIVERY-001"}), evidence_markers(playwright))

    def test_commented_out_junit_tags_are_not_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            java = Path(directory) / "Evidence.java"
            java.write_text(
                '// @Tag("VFY-COMMENTED-LINE-001")\n'
                '/* @Tag("VFY-COMMENTED-BLOCK-001") */\n'
                '@Tag("VFY-HISTORY-001")\n'
                'String annotation = "@Disabled";\n'
                'String endpoint = "https://example.test/*";\n',
                encoding="utf-8",
            )

            self.assertEqual(frozenset({"VFY-HISTORY-001"}), evidence_markers(java))

    def test_commented_out_playwright_tests_are_not_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            playwright = Path(directory) / "evidence.spec.ts"
            playwright.write_text(
                "// test('VFY-COMMENTED-LINE-001 disabled', async () => {});\n"
                "/* test('VFY-COMMENTED-BLOCK-001 disabled', async () => {}); */\n"
                "test('VFY-DELIVERY-001 preserves https://, /*, and test.skip( in a title', async () => {});\n",
                encoding="utf-8",
            )

            self.assertEqual(frozenset({"VFY-DELIVERY-001"}), evidence_markers(playwright))

    def test_non_spec_typescript_helper_is_not_executable_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            catalogue = root / "docs/assignment/VV/verification.yaml"
            catalogue.parent.mkdir(parents=True)
            catalogue.write_text(
                "obligations:\n  VFY-DELIVERY-001:\n    covers: []\n",
                encoding="utf-8",
            )
            e2e = root / "e2e"
            e2e.mkdir()
            (e2e / "executed.spec.ts").write_text(
                "test('VFY-DELIVERY-001 executable slice', async () => {});\n",
                encoding="utf-8",
            )
            (e2e / "helper.ts").write_text(
                "test('VFY-NOT-CONTROLLED-001 helper text', async () => {});\n",
                encoding="utf-8",
            )

            result = inventory(root)

            self.assertFalse(result.unknown)
            self.assertFalse(result.missing)
            self.assertEqual(
                (Path("e2e/executed.spec.ts"),),
                result.sources["VFY-DELIVERY-001"],
            )

    def test_disabled_junit_source_cannot_certify_colocated_active_marker(self):
        sources = {
            "class": (
                '@Disabled\n@Tag("VFY-HISTORY-001")\nclass DisabledEvidence {}\n'
                '@Tag("VFY-DELIVERY-001")\nclass ActiveEvidence {}\n'
            ),
            "method": (
                'class Evidence {\n'
                '  @Disabled\n  @Tag("VFY-HISTORY-001")\n  void disabledEvidence() {}\n'
                '  @Tag("VFY-DELIVERY-001")\n  void activeEvidence() {}\n'
                '}\n'
            ),
        }
        for scope, source in sources.items():
            with self.subTest(scope=scope), tempfile.TemporaryDirectory() as directory:
                java = Path(directory) / "Evidence.java"
                java.write_text(source, encoding="utf-8")

                self.assertEqual(frozenset(), evidence_markers(java))

    def test_disabled_playwright_source_cannot_certify_colocated_active_marker(self):
        disabled_calls = (
            "test.skip('VFY-HISTORY-001 disabled test', async () => {});",
            "test.fixme('VFY-HISTORY-001 disabled test', async () => {});",
            "describe.skip('disabled suite', () => {});",
            "describe.fixme('disabled suite', () => {});",
            "test.describe.skip('disabled suite', () => {});",
            "test.describe.fixme('disabled suite', () => {});",
        )
        for disabled_call in disabled_calls:
            with self.subTest(disabled_call=disabled_call), tempfile.TemporaryDirectory() as directory:
                playwright = Path(directory) / "evidence.spec.ts"
                playwright.write_text(
                    disabled_call
                    + "\ntest('VFY-DELIVERY-001 active evidence', async () => {});\n",
                    encoding="utf-8",
                )

                self.assertEqual(frozenset(), evidence_markers(playwright))

    class fixture:
        def __init__(self, controlled, discovered):
            self.controlled = controlled
            self.discovered = discovered
            self.temporary = None

        def __enter__(self):
            self.temporary = tempfile.TemporaryDirectory()
            root = Path(self.temporary.name)
            catalogue = root / "docs/assignment/VV/verification.yaml"
            catalogue.parent.mkdir(parents=True)
            catalogue.write_text(
                "obligations:\n" + "".join(f"  {marker}:\n    covers: []\n" for marker in self.controlled),
                encoding="utf-8",
            )
            test = root / "backend/src/test/Evidence.java"
            test.parent.mkdir(parents=True)
            test.write_text(
                "\n".join(f'@Tag("{marker}")' for marker in self.discovered),
                encoding="utf-8",
            )
            return root

        def __exit__(self, exc_type, exc_value, traceback):
            self.temporary.cleanup()


if __name__ == "__main__":
    unittest.main()
