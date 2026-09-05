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
