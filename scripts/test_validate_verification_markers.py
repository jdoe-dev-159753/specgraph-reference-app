import tempfile
import unittest
from pathlib import Path

from scripts.validate_verification_markers import CONTROLLED_OBLIGATION_IDS
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

        self.assertEqual(frozenset(CONTROLLED), CONTROLLED_OBLIGATION_IDS)
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

    def test_coordinated_catalogue_and_evidence_removal_is_rejected(self):
        reduced = CONTROLLED[:-1]
        with self.fixture(reduced, reduced) as root:
            result = inventory(root)

            self.assertFalse(result.unknown)
            self.assertFalse(result.missing)
            with self.assertRaisesRegex(
                ValueError,
                "controlled verification catalogue drift: missing stable IDs: VFY-DELIVERY-001",
            ):
                validate(root)

    def test_coordinated_unexpected_catalogue_and_evidence_addition_is_rejected(self):
        expanded = CONTROLLED + ("VFY-UNEXPECTED-001",)
        with self.fixture(expanded, expanded) as root:
            result = inventory(root)

            self.assertFalse(result.unknown)
            self.assertFalse(result.missing)
            with self.assertRaisesRegex(
                ValueError,
                "controlled verification catalogue drift: unexpected IDs: VFY-UNEXPECTED-001",
            ):
                validate(root)

    def test_only_junit_tags_and_playwright_test_titles_are_markers(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            java = root / "Evidence.java"
            java.write_text(
                '// VFY-NOT-A-TAG-001\n'
                '@Tag("VFY-HISTORY-001")\n'
                'class EvidenceTests { @Test void executes() {} }\n',
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
                'class EvidenceTests {\n'
                '  String annotation = "@Disabled";\n'
                '  String endpoint = "https://example.test/*";\n'
                '  @Test void executes() {}\n'
                '}\n',
                encoding="utf-8",
            )

            self.assertEqual(frozenset({"VFY-HISTORY-001"}), evidence_markers(java))

    def test_junit_scanner_ignores_marker_shaped_strings_and_text_blocks(self):
        with tempfile.TemporaryDirectory() as directory:
            java = Path(directory) / "Evidence.java"
            java.write_text(
                'class MarkerShapedText {\n'
                '  String normal = "@Tag(\\"VFY-NOT-CONTROLLED-001\\")";\n'
                '  String block = """\n'
                '      @Tag("VFY-NOT-CONTROLLED-002")\n'
                '      """;\n'
                '}\n'
                '@Tag("VFY-HISTORY-001")\n'
                'class RealEvidenceTests { @Test void executes() {} }\n'
                'class QualifiedRealEvidenceTests {\n'
                '  @org.junit.jupiter.api.Tag("VFY-DELIVERY-001")\n'
                '  @org.junit.jupiter.api.Test void executes() {}\n'
                '}\n',
                encoding="utf-8",
            )

            self.assertEqual(
                frozenset({"VFY-HISTORY-001", "VFY-DELIVERY-001"}),
                evidence_markers(java),
            )

    def test_tagged_class_without_discoverable_tests_is_not_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            java = Path(directory) / "Evidence.java"
            java.write_text(
                '@Tag("VFY-HISTORY-001")\n'
                'class EmptyTaggedClass {}\n'
                'class ExecutedButUntaggedClass { @Test void executes() {} }\n',
                encoding="utf-8",
            )

            self.assertEqual(frozenset(), evidence_markers(java))

    def test_commented_out_playwright_tests_are_not_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            playwright = Path(directory) / "evidence.spec.ts"
            playwright.write_text(
                "// test('VFY-COMMENTED-LINE-001 disabled', async () => {});\n"
                "/* test('VFY-COMMENTED-BLOCK-001 disabled', async () => {}); */\n"
                "test('VFY-DELIVERY-001 preserves https://, /*, test.skip(, and test.fail( in a title', async () => {});\n",
                encoding="utf-8",
            )

            self.assertEqual(frozenset({"VFY-DELIVERY-001"}), evidence_markers(playwright))

    def test_playwright_scanner_ignores_marker_shaped_strings_and_member_calls(self):
        with tempfile.TemporaryDirectory() as directory:
            playwright = Path(directory) / "evidence.spec.ts"
            playwright.write_text(
                'const quoted = "test(\'VFY-NOT-CONTROLLED-001 quoted\')";\n'
                "const templated = `test('VFY-NOT-CONTROLLED-002 templated')`;\n"
                "matcher.test('VFY-NOT-CONTROLLED-003 member call');\n"
                "test('VFY-HISTORY-001 real single-quoted test', async () => {});\n"
                'test /* retained comment */ ("VFY-DELIVERY-001 real double-quoted test.skip( title", '
                "async () => {});\n",
                encoding="utf-8",
            )

            self.assertEqual(
                frozenset({"VFY-HISTORY-001", "VFY-DELIVERY-001"}),
                evidence_markers(playwright),
            )

    def test_playwright_scanner_ignores_regex_literals(self):
        with tempfile.TemporaryDirectory() as directory:
            playwright = Path(directory) / "evidence.spec.ts"
            playwright.write_text(
                "const markerPattern = /test('VFY-NOT-CONTROLLED-001 shaped')/;\n"
                r"const failPattern = /testInfo\.fail\(/;" "\n"
                "test('VFY-DELIVERY-001 real test', async () => {});\n",
                encoding="utf-8",
            )

            self.assertEqual(
                frozenset({"VFY-DELIVERY-001"}),
                evidence_markers(playwright),
            )

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
            (e2e / "playwright.config.ts").write_text(
                "export default { testDir: '.', testMatch: /.*\\.spec\\.ts/ };\n",
                encoding="utf-8",
            )
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

    def test_inventory_uses_playwright_configured_test_match(self):
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
            (e2e / "playwright.config.ts").write_text(
                "export default { testDir: '.', testMatch: /.*\\.evidence\\.ts/ };\n",
                encoding="utf-8",
            )
            (e2e / "executed.evidence.ts").write_text(
                "test('VFY-DELIVERY-001 configured evidence', async () => {});\n",
                encoding="utf-8",
            )
            (e2e / "ignored.spec.ts").write_text(
                "test('VFY-NOT-CONTROLLED-001 stale convention', async () => {});\n",
                encoding="utf-8",
            )

            result = inventory(root)

            self.assertFalse(result.unknown)
            self.assertFalse(result.missing)
            self.assertEqual(
                (Path("e2e/executed.evidence.ts"),),
                result.sources["VFY-DELIVERY-001"],
            )

    def test_inherited_contract_tests_make_concrete_subclass_discoverable(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            catalogue = root / "docs/assignment/VV/verification.yaml"
            catalogue.parent.mkdir(parents=True)
            catalogue.write_text(
                "obligations:\n  VFY-CUSTOMER-READ-001:\n    covers: []\n",
                encoding="utf-8",
            )
            tests = root / "backend/src/test"
            tests.mkdir(parents=True)
            (tests / "CustomerPortContract.java").write_text(
                '@Tag("VFY-CUSTOMER-READ-001")\n'
                "abstract class CustomerPortContract { @Test void inheritedTest() {} }\n",
                encoding="utf-8",
            )
            (tests / "ConcreteCustomerPortTests.java").write_text(
                "final class ConcreteCustomerPortTests extends CustomerPortContract {}\n",
                encoding="utf-8",
            )
            e2e = root / "e2e"
            e2e.mkdir()
            (e2e / "playwright.config.ts").write_text(
                "export default { testDir: '.', testMatch: /.*\\.spec\\.ts/ };\n",
                encoding="utf-8",
            )

            result = inventory(root)

            self.assertFalse(result.missing)
            self.assertEqual(
                (Path("backend/src/test/CustomerPortContract.java"),),
                result.sources["VFY-CUSTOMER-READ-001"],
            )

    def test_java_type_identity_is_package_qualified(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            catalogue = root / "docs/assignment/VV/verification.yaml"
            catalogue.parent.mkdir(parents=True)
            catalogue.write_text(
                "obligations:\n  VFY-DELIVERY-001:\n    covers: []\n",
                encoding="utf-8",
            )
            tests = root / "backend/src/test"
            (tests / "a").mkdir(parents=True)
            (tests / "b").mkdir()
            (tests / "a/EvidenceTests.java").write_text(
                "package a;\n"
                '@Tag("VFY-NOT-CONTROLLED-001")\n'
                "class EvidenceTests {}\n",
                encoding="utf-8",
            )
            (tests / "b/EvidenceTests.java").write_text(
                "package b;\nclass EvidenceTests { @Test void executes() {} }\n",
                encoding="utf-8",
            )
            (tests / "DeliveryTests.java").write_text(
                '@Tag("VFY-DELIVERY-001")\n'
                "class DeliveryTests { @Test void executes() {} }\n",
                encoding="utf-8",
            )
            e2e = root / "e2e"
            e2e.mkdir()
            (e2e / "playwright.config.ts").write_text(
                "export default { testDir: '.', testMatch: /.*\\.spec\\.ts/ };\n",
                encoding="utf-8",
            )

            result = inventory(root)

            self.assertFalse(result.unknown)
            self.assertFalse(result.missing)

    def test_explicit_import_resolves_inherited_contract_provenance(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            catalogue = root / "docs/assignment/VV/verification.yaml"
            catalogue.parent.mkdir(parents=True)
            catalogue.write_text(
                "obligations:\n  VFY-CUSTOMER-READ-001:\n    covers: []\n",
                encoding="utf-8",
            )
            tests = root / "backend/src/test"
            (tests / "contracts").mkdir(parents=True)
            (tests / "adapters").mkdir()
            (tests / "contracts/CustomerContract.java").write_text(
                "package contracts;\n"
                '@Tag("VFY-CUSTOMER-READ-001")\n'
                "abstract class CustomerContract { @Test void inheritedTest() {} }\n",
                encoding="utf-8",
            )
            (tests / "adapters/CustomerAdapterTests.java").write_text(
                "package adapters;\n"
                "import contracts.CustomerContract;\n"
                "class CustomerAdapterTests extends CustomerContract {}\n",
                encoding="utf-8",
            )
            e2e = root / "e2e"
            e2e.mkdir()
            (e2e / "playwright.config.ts").write_text(
                "export default { testDir: '.', testMatch: /.*\\.spec\\.ts/ };\n",
                encoding="utf-8",
            )

            result = inventory(root)

            self.assertFalse(result.missing)
            self.assertEqual(
                (Path("backend/src/test/contracts/CustomerContract.java"),),
                result.sources["VFY-CUSTOMER-READ-001"],
            )

    def test_wildcard_import_does_not_guess_inherited_contract_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            catalogue = root / "docs/assignment/VV/verification.yaml"
            catalogue.parent.mkdir(parents=True)
            catalogue.write_text(
                "obligations:\n  VFY-CUSTOMER-READ-001:\n    covers: []\n",
                encoding="utf-8",
            )
            tests = root / "backend/src/test"
            (tests / "contracts").mkdir(parents=True)
            (tests / "adapters").mkdir()
            (tests / "contracts/CustomerContract.java").write_text(
                "package contracts;\n"
                '@Tag("VFY-CUSTOMER-READ-001")\n'
                "abstract class CustomerContract { @Test void inheritedTest() {} }\n",
                encoding="utf-8",
            )
            (tests / "adapters/CustomerAdapterTests.java").write_text(
                "package adapters;\n"
                "import contracts.*;\n"
                "class CustomerAdapterTests extends CustomerContract {}\n",
                encoding="utf-8",
            )
            e2e = root / "e2e"
            e2e.mkdir()
            (e2e / "playwright.config.ts").write_text(
                "export default { testDir: '.', testMatch: /.*\\.spec\\.ts/ };\n",
                encoding="utf-8",
            )

            result = inventory(root)

            self.assertEqual(frozenset({"VFY-CUSTOMER-READ-001"}), result.missing)

    def test_surefire_default_type_names_gate_executable_junit_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            catalogue = root / "docs/assignment/VV/verification.yaml"
            catalogue.parent.mkdir(parents=True)
            catalogue.write_text(
                "obligations:\n  VFY-DELIVERY-001:\n    covers: []\n",
                encoding="utf-8",
            )
            tests = root / "backend/src/test"
            tests.mkdir(parents=True)
            (tests / "EvidenceTests.java").write_text(
                '@Tag("VFY-NOT-CONTROLLED-001")\n'
                "class Evidence { @Test void executes() {} }\n",
                encoding="utf-8",
            )
            (tests / "Delivery.java").write_text(
                '@Tag("VFY-DELIVERY-001")\n'
                "class DeliveryTests { @Test void executes() {} }\n",
                encoding="utf-8",
            )
            e2e = root / "e2e"
            e2e.mkdir()
            (e2e / "playwright.config.ts").write_text(
                "export default { testDir: '.', testMatch: /.*\\.spec\\.ts/ };\n",
                encoding="utf-8",
            )

            result = inventory(root)

            self.assertFalse(result.unknown)
            self.assertFalse(result.missing)
            self.assertEqual(
                (Path("backend/src/test/Delivery.java"),),
                result.sources["VFY-DELIVERY-001"],
            )

    def test_surefire_default_type_name_conventions_are_supported(self):
        with tempfile.TemporaryDirectory() as directory:
            java = Path(directory) / "ArbitrarySourceName.java"
            java.write_text(
                '@Tag("VFY-PREFIX-001") class TestPrefix { @Test void executes() {} }\n'
                '@Tag("VFY-TEST-001") class SingularTest { @Test void executes() {} }\n'
                '@Tag("VFY-TESTS-001") class PluralTests { @Test void executes() {} }\n'
                '@Tag("VFY-CASE-001") class LegacyTestCase { @Test void executes() {} }\n'
                '@Tag("VFY-IGNORED-001") class Evidence { @Test void executes() {} }\n',
                encoding="utf-8",
            )

            self.assertEqual(
                frozenset({
                    "VFY-PREFIX-001",
                    "VFY-TEST-001",
                    "VFY-TESTS-001",
                    "VFY-CASE-001",
                }),
                evidence_markers(java),
            )

    def test_inventory_applies_playwright_test_dir_before_test_match(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            catalogue = root / "docs/assignment/VV/verification.yaml"
            catalogue.parent.mkdir(parents=True)
            catalogue.write_text(
                "obligations:\n  VFY-DELIVERY-001:\n    covers: []\n",
                encoding="utf-8",
            )
            e2e = root / "e2e"
            selected = e2e / "specs"
            selected.mkdir(parents=True)
            (e2e / "playwright.config.ts").write_text(
                "export default { testDir: './specs', testMatch: /.*\\.spec\\.ts/ };\n",
                encoding="utf-8",
            )
            (selected / "executed.spec.ts").write_text(
                "test('VFY-DELIVERY-001 configured evidence', async () => {});\n",
                encoding="utf-8",
            )
            (e2e / "outside.spec.ts").write_text(
                "test('VFY-NOT-CONTROLLED-001 outside testDir', async () => {});\n",
                encoding="utf-8",
            )

            result = inventory(root)

            self.assertFalse(result.unknown)
            self.assertFalse(result.missing)
            self.assertEqual(
                (Path("e2e/specs/executed.spec.ts"),),
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
            "testInfo.skip(true, 'disabled dynamically');",
            "testInfo.fixme(true, 'disabled dynamically');",
            "test.fail(true, 'expected to fail');",
            "testInfo.fail(true, 'expected to fail dynamically');",
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
                "\n".join(f'@Tag("{marker}")' for marker in self.discovered)
                + "\nclass EvidenceTests { @Test void executes() {} }\n",
                encoding="utf-8",
            )
            e2e = root / "e2e"
            e2e.mkdir()
            (e2e / "playwright.config.ts").write_text(
                "export default { testDir: '.', testMatch: /.*\\.spec\\.ts/ };\n",
                encoding="utf-8",
            )
            return root

        def __exit__(self, exc_type, exc_value, traceback):
            self.temporary.cleanup()


if __name__ == "__main__":
    unittest.main()
