"""Exercises fail-closed discovery of controlled V&V markers in executable Java and Playwright tests."""

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
JUNIT_TAG_IMPORT = (
    "import org.junit.jupiter.api.Tag;\n"
    "import org.junit.jupiter.api.Test;\n"
)
PLAYWRIGHT_TEST_IMPORT = "import { test } from '@playwright/test';\n"
PLAYWRIGHT_CONFIG_IMPORT = "import { defineConfig } from '@playwright/test';\n"


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
                JUNIT_TAG_IMPORT
                + '// VFY-NOT-A-TAG-001\n'
                '@Tag("VFY-HISTORY-001")\n'
                'class EvidenceTests { @Test void executes() {} }\n',
                encoding="utf-8",
            )
            playwright = root / "evidence.spec.ts"
            playwright.write_text(
                PLAYWRIGHT_TEST_IMPORT
                + "// VFY-NOT-A-TEST-001\n"
                "test('VFY-DELIVERY-001 executable slice', async () => {});\n",
                encoding="utf-8",
            )

            self.assertEqual(frozenset({"VFY-HISTORY-001"}), evidence_markers(java))
            self.assertEqual(frozenset({"VFY-DELIVERY-001"}), evidence_markers(playwright))

    def test_commented_out_junit_tags_are_not_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            java = Path(directory) / "Evidence.java"
            java.write_text(
                JUNIT_TAG_IMPORT
                + '// @Tag("VFY-COMMENTED-LINE-001")\n'
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
                JUNIT_TAG_IMPORT
                + 'class MarkerShapedText {\n'
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

    def test_short_junit_tag_requires_jupiter_import_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            java = Path(directory) / "Evidence.java"
            java.write_text(
                "import com.acme.Tag;\n"
                "import org.junit.jupiter.api.Test;\n"
                '@Tag("VFY-NOT-CONTROLLED-001")\n'
                "class ForeignTagTests { @Test void executes() {} }\n"
                '@org.junit.jupiter.api.Tag("VFY-DELIVERY-001")\n'
                "class QualifiedJunitTagTests { @Test void executes() {} }\n",
                encoding="utf-8",
            )

            self.assertEqual(
                frozenset({"VFY-DELIVERY-001"}),
                evidence_markers(java),
            )

    def test_junit_test_annotations_require_resolved_jupiter_identity(self):
        identities = {
            "Test": "org.junit.jupiter.api.Test",
            "ParameterizedTest": "org.junit.jupiter.params.ParameterizedTest",
            "RepeatedTest": "org.junit.jupiter.api.RepeatedTest",
            "TestFactory": "org.junit.jupiter.api.TestFactory",
            "TestTemplate": "org.junit.jupiter.api.TestTemplate",
        }
        for simple, qualified in identities.items():
            with self.subTest(annotation=simple), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                return_type = (
                    "org.junit.jupiter.api.DynamicTest"
                    if simple == "TestFactory"
                    else "void"
                )
                method = (
                    f"{return_type} executes() {{ return null; }}"
                    if simple == "TestFactory"
                    else "void executes() {}"
                )
                explicit = root / "Explicit.java"
                explicit.write_text(
                    "import org.junit.jupiter.api.Tag;\n"
                    f"import {qualified};\n"
                    '@Tag("VFY-HISTORY-001")\n'
                    f"class ExplicitTests {{ @{simple} {method} }}\n",
                    encoding="utf-8",
                )
                fully_qualified = root / "FullyQualified.java"
                fully_qualified.write_text(
                    "import org.junit.jupiter.api.Tag;\n"
                    '@Tag("VFY-DELIVERY-001")\n'
                    f"class FullyQualifiedTests {{ @{qualified} {method} }}\n",
                    encoding="utf-8",
                )
                foreign = root / "Foreign.java"
                foreign.write_text(
                    "import org.junit.jupiter.api.Tag;\n"
                    f"import com.acme.{simple};\n"
                    '@Tag("VFY-NOT-CONTROLLED-001")\n'
                    f"class ForeignTests {{ @{simple} void doesNotExecute() {{}} }}\n",
                    encoding="utf-8",
                )

                self.assertEqual(
                    frozenset({"VFY-HISTORY-001"}),
                    evidence_markers(explicit),
                )
                self.assertEqual(
                    frozenset({"VFY-DELIVERY-001"}),
                    evidence_markers(fully_qualified),
                )
                self.assertEqual(frozenset(), evidence_markers(foreign))

    def test_ambiguous_wildcard_test_annotation_is_not_junit_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            java = Path(directory) / "Evidence.java"
            java.write_text(
                "import org.junit.jupiter.api.*;\n"
                "import com.acme.*;\n"
                '@org.junit.jupiter.api.Tag("VFY-NOT-CONTROLLED-001")\n'
                "class EvidenceTests { @Test void doesNotExecute() {} }\n",
                encoding="utf-8",
            )

            self.assertEqual(frozenset(), evidence_markers(java))

    def test_same_package_test_type_shadows_jupiter_wildcard_import(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            catalogue = root / "docs/assignment/VV/verification.yaml"
            catalogue.parent.mkdir(parents=True)
            catalogue.write_text(
                "obligations:\n  VFY-DELIVERY-001:\n    covers: []\n",
                encoding="utf-8",
            )
            tests = root / "backend/src/test/p"
            tests.mkdir(parents=True)
            (tests / "Test.java").write_text(
                "package p;\npublic @interface Test {}\n",
                encoding="utf-8",
            )
            (tests / "EvidenceTests.java").write_text(
                "package p;\n"
                "import org.junit.jupiter.api.*;\n"
                '@org.junit.jupiter.api.Tag("VFY-DELIVERY-001")\n'
                "class EvidenceTests { @Test void doesNotExecute() {} }\n",
                encoding="utf-8",
            )
            e2e = root / "e2e"
            e2e.mkdir()
            (e2e / "playwright.config.ts").write_text(
                PLAYWRIGHT_CONFIG_IMPORT
                + "export default defineConfig({ testDir: '.', testMatch: /.*\\.spec\\.ts/ });\n",
                encoding="utf-8",
            )

            result = inventory(root)

            self.assertEqual(frozenset({"VFY-DELIVERY-001"}), result.missing)

    def test_same_package_tag_shadows_jupiter_wildcard_import(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            catalogue = root / "docs/assignment/VV/verification.yaml"
            catalogue.parent.mkdir(parents=True)
            catalogue.write_text(
                "obligations:\n  VFY-DELIVERY-001:\n    covers: []\n",
                encoding="utf-8",
            )
            tests = root / "backend/src/test/p"
            tests.mkdir(parents=True)
            (tests / "Tag.java").write_text(
                "package p;\npublic @interface Tag { String value(); }\n",
                encoding="utf-8",
            )
            (tests / "EvidenceTests.java").write_text(
                "package p;\n"
                "import org.junit.jupiter.api.*;\n"
                '@Tag("VFY-NOT-CONTROLLED-001")\n'
                "class ShadowedTests { @Test void executes() {} }\n"
                '@org.junit.jupiter.api.Tag("VFY-DELIVERY-001")\n'
                "class QualifiedTests { @Test void executes() {} }\n",
                encoding="utf-8",
            )
            e2e = root / "e2e"
            e2e.mkdir()
            (e2e / "playwright.config.ts").write_text(
                PLAYWRIGHT_CONFIG_IMPORT
                + "export default defineConfig({ testDir: '.', testMatch: /.*\\.spec\\.ts/ });\n",
                encoding="utf-8",
            )

            result = inventory(root)

            self.assertFalse(result.unknown)
            self.assertFalse(result.missing)

    def test_nested_tag_does_not_shadow_jupiter_wildcard_import(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            catalogue = root / "docs/assignment/VV/verification.yaml"
            catalogue.parent.mkdir(parents=True)
            catalogue.write_text(
                "obligations:\n  VFY-DELIVERY-001:\n    covers: []\n",
                encoding="utf-8",
            )
            tests = root / "backend/src/test/p"
            tests.mkdir(parents=True)
            (tests / "Holder.java").write_text(
                "package p;\nclass Holder { static class Tag {} }\n",
                encoding="utf-8",
            )
            evidence = tests / "EvidenceTests.java"
            evidence.write_text(
                "package p;\n"
                "import org.junit.jupiter.api.*;\n"
                '@Tag("VFY-DELIVERY-001")\n'
                "class EvidenceTests { @Test void executes() {} }\n",
                encoding="utf-8",
            )
            e2e = root / "e2e"
            e2e.mkdir()
            (e2e / "playwright.config.ts").write_text(
                PLAYWRIGHT_CONFIG_IMPORT
                + "export default defineConfig({ testDir: '.', testMatch: /.*\\.spec\\.ts/ });\n",
                encoding="utf-8",
            )

            result = inventory(root)

            self.assertFalse(result.unknown)
            self.assertFalse(result.missing)
            self.assertEqual(
                (Path("backend/src/test/p/EvidenceTests.java"),),
                result.sources["VFY-DELIVERY-001"],
            )

    def test_tagged_class_without_discoverable_tests_is_not_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            java = Path(directory) / "Evidence.java"
            java.write_text(
                JUNIT_TAG_IMPORT
                + '@Tag("VFY-HISTORY-001")\n'
                'class EmptyTaggedClass {}\n'
                'class ExecutedButUntaggedClass { @Test void executes() {} }\n',
                encoding="utf-8",
            )

            self.assertEqual(frozenset(), evidence_markers(java))

    def test_commented_out_playwright_tests_are_not_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            playwright = Path(directory) / "evidence.spec.ts"
            playwright.write_text(
                PLAYWRIGHT_TEST_IMPORT
                + "// test('VFY-COMMENTED-LINE-001 disabled', async () => {});\n"
                "/* test('VFY-COMMENTED-BLOCK-001 disabled', async () => {}); */\n"
                "test('VFY-DELIVERY-001 preserves https://, /*, test.skip(, and test.fail( in a title', async () => {});\n",
                encoding="utf-8",
            )

            self.assertEqual(frozenset({"VFY-DELIVERY-001"}), evidence_markers(playwright))

    def test_playwright_scanner_ignores_marker_shaped_strings_and_member_calls(self):
        with tempfile.TemporaryDirectory() as directory:
            playwright = Path(directory) / "evidence.spec.ts"
            playwright.write_text(
                PLAYWRIGHT_TEST_IMPORT
                + 'const quoted = "test(\'VFY-NOT-CONTROLLED-001 quoted\')";\n'
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
                PLAYWRIGHT_TEST_IMPORT
                + "const markerPattern = /test('VFY-NOT-CONTROLLED-001 shaped')/;\n"
                r"const failPattern = /testInfo\.fail\(/;" "\n"
                "test('VFY-DELIVERY-001 real test', async () => {});\n",
                encoding="utf-8",
            )

            self.assertEqual(
                frozenset({"VFY-DELIVERY-001"}),
                evidence_markers(playwright),
            )

    def test_playwright_scanner_resolves_test_import_identity_and_alias(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            foreign = root / "foreign.spec.ts"
            foreign.write_text(
                "import { test } from '@acme/not-playwright';\n"
                "test('VFY-NOT-CONTROLLED-001 foreign binding', async () => {});\n",
                encoding="utf-8",
            )
            local = root / "local.spec.ts"
            local.write_text(
                "function test(title, callback) { callback(); }\n"
                "test('VFY-NOT-CONTROLLED-002 local function', () => {});\n",
                encoding="utf-8",
            )
            aliased = root / "aliased.spec.ts"
            aliased.write_text(
                "import { test as scenario } from '@playwright/test';\n"
                "scenario.describe('suite', () => {\n"
                "  scenario('VFY-HISTORY-001 aliased nested test', async () => {});\n"
                "});\n"
                "scenario('VFY-DELIVERY-001 aliased test', async () => {});\n",
                encoding="utf-8",
            )

            self.assertEqual(frozenset(), evidence_markers(foreign))
            self.assertEqual(frozenset(), evidence_markers(local))
            self.assertEqual(
                frozenset({"VFY-HISTORY-001", "VFY-DELIVERY-001"}),
                evidence_markers(aliased),
            )

    def test_playwright_scanner_only_accepts_top_level_registrations(self):
        with tempfile.TemporaryDirectory() as directory:
            playwright = Path(directory) / "evidence.spec.ts"
            playwright.write_text(
                PLAYWRIGHT_TEST_IMPORT
                + "function neverCalled() {\n"
                "  test('VFY-NOT-CONTROLLED-001 hidden function', async () => {});\n"
                "}\n"
                "if (false) {\n"
                "  test('VFY-NOT-CONTROLLED-002 unreachable branch', async () => {});\n"
                "}\n"
                "const neverCalledArrow = () => test('VFY-NOT-CONTROLLED-003 hidden arrow', async () => {});\n"
                "if (false) test('VFY-NOT-CONTROLLED-004 unbraced branch', async () => {});\n"
                "if (\n"
                "  false\n"
                ")\n"
                "\n"
                "test('VFY-NOT-CONTROLLED-005 multiline unreachable branch', async () => {});\n"
                "register(() => {\n"
                "  test('VFY-NOT-CONTROLLED-006 ordinary callback', async () => {});\n"
                "});\n"
                "test.describe('registered suite', () => {\n"
                "  test('VFY-HISTORY-001 registered describe test', async () => {});\n"
                "});\n"
                "test('VFY-DELIVERY-001 registered test', async () => {});\n",
                encoding="utf-8",
            )

            self.assertEqual(
                frozenset({"VFY-HISTORY-001", "VFY-DELIVERY-001"}),
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
                PLAYWRIGHT_CONFIG_IMPORT
                + "export default defineConfig({ testDir: '.', testMatch: /.*\\.spec\\.ts/ });\n",
                encoding="utf-8",
            )
            (e2e / "executed.spec.ts").write_text(
                PLAYWRIGHT_TEST_IMPORT
                + "test('VFY-DELIVERY-001 executable slice', async () => {});\n",
                encoding="utf-8",
            )
            (e2e / "helper.ts").write_text(
                PLAYWRIGHT_TEST_IMPORT
                + "test('VFY-NOT-CONTROLLED-001 helper text', async () => {});\n",
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
                PLAYWRIGHT_CONFIG_IMPORT
                + "export default defineConfig({ testDir: '.', testMatch: /.*\\.evidence\\.ts/ });\n",
                encoding="utf-8",
            )
            (e2e / "executed.evidence.ts").write_text(
                PLAYWRIGHT_TEST_IMPORT
                + "test('VFY-DELIVERY-001 configured evidence', async () => {});\n",
                encoding="utf-8",
            )
            (e2e / "ignored.spec.ts").write_text(
                PLAYWRIGHT_TEST_IMPORT
                + "test('VFY-NOT-CONTROLLED-001 stale convention', async () => {});\n",
                encoding="utf-8",
            )

            result = inventory(root)

            self.assertFalse(result.unknown)
            self.assertFalse(result.missing)
            self.assertEqual(
                (Path("e2e/executed.evidence.ts"),),
                result.sources["VFY-DELIVERY-001"],
            )

    def test_playwright_regex_matches_normalized_absolute_file_path(self):
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
                PLAYWRIGHT_CONFIG_IMPORT
                + "export default defineConfig({\n"
                "  testDir: '.',\n"
                "  testMatch: /^.*\\/e2e\\/selected\\.spec\\.ts$/,\n"
                "});\n",
                encoding="utf-8",
            )
            (e2e / "selected.spec.ts").write_text(
                PLAYWRIGHT_TEST_IMPORT
                + "test('VFY-DELIVERY-001 selected absolute path', async () => {});\n",
                encoding="utf-8",
            )
            (e2e / "ignored.spec.ts").write_text(
                PLAYWRIGHT_TEST_IMPORT
                + "test('VFY-NOT-CONTROLLED-001 ignored absolute path', async () => {});\n",
                encoding="utf-8",
            )

            result = inventory(root)

            self.assertFalse(result.unknown)
            self.assertFalse(result.missing)
            self.assertEqual(
                (Path("e2e/selected.spec.ts"),),
                result.sources["VFY-DELIVERY-001"],
            )

    def test_inventory_reads_only_the_object_exported_through_define_config(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            catalogue = root / "docs/assignment/VV/verification.yaml"
            catalogue.parent.mkdir(parents=True)
            catalogue.write_text(
                "obligations:\n  VFY-DELIVERY-001:\n    covers: []\n",
                encoding="utf-8",
            )
            e2e = root / "e2e"
            selected = e2e / "selected"
            decoy = e2e / "decoy"
            selected.mkdir(parents=True)
            decoy.mkdir()
            (e2e / "playwright.config.ts").write_text(
                "import { defineConfig as configure } from '@playwright/test';\n"
                "const lexicalDecoy = { testDir: './decoy', testMatch: /.*\\.decoy\\.ts/ };\n"
                "export default configure({\n"
                "  testDir: './selected',\n"
                "  testMatch: /.*\\.spec\\.ts/,\n"
                "});\n",
                encoding="utf-8",
            )
            (selected / "executed.spec.ts").write_text(
                PLAYWRIGHT_TEST_IMPORT
                + "test('VFY-DELIVERY-001 exported config', async () => {});\n",
                encoding="utf-8",
            )
            (decoy / "ignored.decoy.ts").write_text(
                PLAYWRIGHT_TEST_IMPORT
                + "test('VFY-NOT-CONTROLLED-001 lexical decoy', async () => {});\n",
                encoding="utf-8",
            )

            result = inventory(root)

            self.assertFalse(result.unknown)
            self.assertFalse(result.missing)
            self.assertEqual(
                (Path("e2e/selected/executed.spec.ts"),),
                result.sources["VFY-DELIVERY-001"],
            )

    def test_inventory_fails_closed_when_define_config_object_is_indirect(self):
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
                PLAYWRIGHT_CONFIG_IMPORT
                + "const config = defineConfig({ testDir: '.', testMatch: /.*\\.spec\\.ts/ });\n"
                "export default config;\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                ValueError,
                "default export must call defineConfig",
            ):
                inventory(root)

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
                JUNIT_TAG_IMPORT
                + '@Tag("VFY-CUSTOMER-READ-001")\n'
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
                PLAYWRIGHT_CONFIG_IMPORT
                + "export default defineConfig({ testDir: '.', testMatch: /.*\\.spec\\.ts/ });\n",
                encoding="utf-8",
            )

            result = inventory(root)

            self.assertFalse(result.missing)
            self.assertEqual(
                (Path("backend/src/test/CustomerPortContract.java"),),
                result.sources["VFY-CUSTOMER-READ-001"],
            )

    def test_generic_argument_commas_do_not_create_java_parents(self):
        with tempfile.TemporaryDirectory() as directory:
            java = Path(directory) / "GenericEvidence.java"
            java.write_text(
                JUNIT_TAG_IMPORT
                + "@interface Ann {}\n"
                "class EmptyContract<Left, Right> {}\n"
                "class WrongParent { @Test void executes() {} }\n"
                '@Tag("VFY-NOT-CONTROLLED-001")\n'
                "class PhantomTests extends @Ann EmptyContract<Map<String, ? extends WrongParent>> {}\n"
                "class RealContract<Left, Right> { @Test void executes() {} }\n"
                '@Tag("VFY-DELIVERY-001")\n'
                "class ConcreteTests extends @Ann RealContract<Map<String, ? extends WrongParent>> {}\n",
                encoding="utf-8",
            )

            self.assertEqual(
                frozenset({"VFY-DELIVERY-001"}),
                evidence_markers(java),
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
                + JUNIT_TAG_IMPORT
                + '@Tag("VFY-NOT-CONTROLLED-001")\n'
                "class EvidenceTests {}\n",
                encoding="utf-8",
            )
            (tests / "b/EvidenceTests.java").write_text(
                "package b;\nclass EvidenceTests { @Test void executes() {} }\n",
                encoding="utf-8",
            )
            (tests / "DeliveryTests.java").write_text(
                JUNIT_TAG_IMPORT
                + '@Tag("VFY-DELIVERY-001")\n'
                "class DeliveryTests { @Test void executes() {} }\n",
                encoding="utf-8",
            )
            e2e = root / "e2e"
            e2e.mkdir()
            (e2e / "playwright.config.ts").write_text(
                PLAYWRIGHT_CONFIG_IMPORT
                + "export default defineConfig({ testDir: '.', testMatch: /.*\\.spec\\.ts/ });\n",
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
                + JUNIT_TAG_IMPORT
                + '@Tag("VFY-CUSTOMER-READ-001")\n'
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
                PLAYWRIGHT_CONFIG_IMPORT
                + "export default defineConfig({ testDir: '.', testMatch: /.*\\.spec\\.ts/ });\n",
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
                + JUNIT_TAG_IMPORT
                + '@Tag("VFY-CUSTOMER-READ-001")\n'
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
                PLAYWRIGHT_CONFIG_IMPORT
                + "export default defineConfig({ testDir: '.', testMatch: /.*\\.spec\\.ts/ });\n",
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
                JUNIT_TAG_IMPORT
                + '@Tag("VFY-NOT-CONTROLLED-001")\n'
                "class Evidence { @Test void executes() {} }\n",
                encoding="utf-8",
            )
            (tests / "Delivery.java").write_text(
                JUNIT_TAG_IMPORT
                + '@Tag("VFY-DELIVERY-001")\n'
                "class DeliveryTests { @Test void executes() {} }\n",
                encoding="utf-8",
            )
            e2e = root / "e2e"
            e2e.mkdir()
            (e2e / "playwright.config.ts").write_text(
                PLAYWRIGHT_CONFIG_IMPORT
                + "export default defineConfig({ testDir: '.', testMatch: /.*\\.spec\\.ts/ });\n",
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
                JUNIT_TAG_IMPORT
                + '@Tag("VFY-PREFIX-001") class TestPrefix { @Test void executes() {} }\n'
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

    def test_junit_nested_tests_are_reached_through_selected_outer(self):
        with tempfile.TemporaryDirectory() as directory:
            java = Path(directory) / "NestedEvidence.java"
            java.write_text(
                JUNIT_TAG_IMPORT
                + "import org.junit.jupiter.api.Nested;\n"
                "class EvidenceTests {\n"
                "  @Nested\n"
                '  @Tag("VFY-HISTORY-001")\n'
                "  class ImportedNested { @Test void executes() {} }\n"
                "  @org.junit.jupiter.api.Nested\n"
                '  @Tag("VFY-DELIVERY-001")\n'
                "  class QualifiedNested { @Test void executes() {} }\n"
                "}\n",
                encoding="utf-8",
            )

            self.assertEqual(
                frozenset({"VFY-HISTORY-001", "VFY-DELIVERY-001"}),
                evidence_markers(java),
            )

    def test_nested_types_are_not_selected_by_their_simple_names(self):
        with tempfile.TemporaryDirectory() as directory:
            java = Path(directory) / "NestedFalsePositives.java"
            java.write_text(
                "import org.junit.jupiter.api.Test;\n"
                "import com.acme.Nested;\n"
                "class Outer {\n"
                "  @org.junit.jupiter.api.Nested\n"
                '  @org.junit.jupiter.api.Tag("VFY-NOT-CONTROLLED-001")\n'
                "  static class NestedTests { @Test void executes() {} }\n"
                "  @org.junit.jupiter.api.Nested\n"
                '  @org.junit.jupiter.api.Tag("VFY-NOT-CONTROLLED-004")\n'
                "  class NonStaticNestedTests { @Test void executes() {} }\n"
                "}\n"
                "class StaticOuterTests {\n"
                "  @org.junit.jupiter.api.Nested\n"
                '  @org.junit.jupiter.api.Tag("VFY-NOT-CONTROLLED-002")\n'
                "  static class StaticNested { @Test void executes() {} }\n"
                "}\n"
                "class ForeignOuterTests {\n"
                "  @Nested\n"
                '  @org.junit.jupiter.api.Tag("VFY-NOT-CONTROLLED-003")\n'
                "  class ForeignNested { @Test void executes() {} }\n"
                "}\n",
                encoding="utf-8",
            )

            self.assertEqual(frozenset(), evidence_markers(java))

    def test_member_annotation_types_shadow_junit_only_in_their_lexical_scope(self):
        with tempfile.TemporaryDirectory() as directory:
            java = Path(directory) / "MemberAnnotationScopes.java"
            java.write_text(
                "import org.junit.jupiter.api.*;\n"
                "class ShadowedNestedTests {\n"
                "  @interface Nested {}\n"
                "  @Nested @Tag(\"VFY-NOT-CONTROLLED-001\")\n"
                "  class Child { @Test void ignored() {} }\n"
                "}\n"
                "class ShadowedTestTests {\n"
                "  @interface Test {}\n"
                "  @Tag(\"VFY-NOT-CONTROLLED-002\") @Test void ignored() {}\n"
                "}\n"
                "class ShadowedTagTests {\n"
                "  @interface Tag { String value(); }\n"
                "  @Tag(\"VFY-NOT-CONTROLLED-003\") @Test void ignored() {}\n"
                "}\n"
                "class LocalDisabledTests {\n"
                "  @interface Disabled {}\n"
                "  @Disabled @Tag(\"VFY-DELIVERY-001\") @Test void executes() {}\n"
                "}\n"
                "class OtherOuterTests {\n"
                "  @Nested @Tag(\"VFY-HISTORY-001\")\n"
                "  class Child { @Test void executes() {} }\n"
                "}\n",
                encoding="utf-8",
            )

            self.assertEqual(
                frozenset({"VFY-HISTORY-001", "VFY-DELIVERY-001"}),
                evidence_markers(java),
            )

    def test_junit_test_annotations_require_executable_method_declarations(self):
        with tempfile.TemporaryDirectory() as directory:
            java = Path(directory) / "MethodShapes.java"
            java.write_text(
                JUNIT_TAG_IMPORT
                + "import org.junit.jupiter.params.ParameterizedTest;\n"
                "import org.junit.jupiter.api.RepeatedTest;\n"
                "import org.junit.jupiter.api.TestTemplate;\n"
                "class MethodShapeTests {\n"
                "  @Tag(\"VFY-NOT-CONTROLLED-001\") @Test private void hidden() {}\n"
                "  @Tag(\"VFY-NOT-CONTROLLED-002\") @Test static void staticTest() {}\n"
                "  @Tag(\"VFY-NOT-CONTROLLED-003\") @Test int value() { return 1; }\n"
                "  @Tag(\"VFY-NOT-CONTROLLED-004\") @ParameterizedTest Object parameterized() { return null; }\n"
                "  @Tag(\"VFY-NOT-CONTROLLED-005\") @RepeatedTest(2) String repeated() { return \"\"; }\n"
                "  @Tag(\"VFY-NOT-CONTROLLED-006\") @TestTemplate long template() { return 0; }\n"
                "  @Test @Tag(\"VFY-NOT-CONTROLLED-007\") Runnable field = () -> {};\n"
                "  @Tag(\"VFY-DELIVERY-001\") @Test void executes() {}\n"
                "}\n"
                "abstract class AbstractContract {\n"
                "  @Tag(\"VFY-NOT-CONTROLLED-008\") @Test abstract void absent();\n"
                "}\n"
                "class ConcreteTests extends AbstractContract { @Test void executes() {} }\n"
                "interface DefaultContract {\n"
                "  @Tag(\"VFY-NOT-CONTROLLED-009\") @Test void declarationOnly();\n"
                "  @Tag(\"VFY-AUTH-001\") @Test default void inheritedTest() {}\n"
                "}\n"
                "class DefaultContractTests implements DefaultContract {}\n",
                encoding="utf-8",
            )

            self.assertEqual(
                frozenset({"VFY-AUTH-001", "VFY-DELIVERY-001"}),
                evidence_markers(java),
            )

    def test_junit_test_factory_requires_a_supported_dynamic_node_return_shape(self):
        with tempfile.TemporaryDirectory() as directory:
            java = Path(directory) / "FactoryShapes.java"
            java.write_text(
                "import java.util.*;\n"
                "import java.util.stream.Stream;\n"
                "import org.junit.jupiter.api.DynamicNode;\n"
                "import org.junit.jupiter.api.DynamicTest;\n"
                "import org.junit.jupiter.api.Tag;\n"
                "import org.junit.jupiter.api.TestFactory;\n"
                "class FactoryTests {\n"
                "  @Tag(\"VFY-NOT-CONTROLLED-001\") @TestFactory void none() {}\n"
                "  @Tag(\"VFY-NOT-CONTROLLED-002\") @TestFactory String scalar() { return \"\"; }\n"
                "  @Tag(\"VFY-NOT-CONTROLLED-003\") @TestFactory Stream<String> wrongStream() { return null; }\n"
                "  @Tag(\"VFY-NOT-CONTROLLED-004\") @TestFactory Object[] wrongArray() { return null; }\n"
                "  @Tag(\"VFY-NOT-CONTROLLED-005\") @TestFactory static DynamicTest staticFactory() { return null; }\n"
                "  @Tag(\"VFY-HISTORY-001\") @TestFactory DynamicTest node() { return null; }\n"
                "  @Tag(\"VFY-AUTH-001\") @TestFactory Stream<? extends DynamicNode> stream() { return null; }\n"
                "  @Tag(\"VFY-DELIVERY-001\") @TestFactory DynamicTest[] array() { return null; }\n"
                "}\n",
                encoding="utf-8",
            )

            self.assertEqual(
                frozenset({"VFY-AUTH-001", "VFY-HISTORY-001", "VFY-DELIVERY-001"}),
                evidence_markers(java),
            )

    def test_inherited_junit_guard_disables_subclass_evidence_but_tag_is_inherited(self):
        with tempfile.TemporaryDirectory() as directory:
            java = Path(directory) / "InheritedAnnotations.java"
            java.write_text(
                JUNIT_TAG_IMPORT
                + "import org.junit.jupiter.api.Disabled;\n"
                "@Disabled @Tag(\"VFY-NOT-CONTROLLED-001\")\n"
                "class DisabledBase { @Test void inherited() {} }\n"
                "class DisabledChildTests extends DisabledBase {\n"
                "  @Tag(\"VFY-NOT-CONTROLLED-002\") @Test void ownTest() {}\n"
                "}\n"
                "@Tag(\"VFY-HISTORY-001\")\n"
                "class TaggedBase {}\n"
                "class TaggedChildTests extends TaggedBase { @Test void executes() {} }\n",
                encoding="utf-8",
            )

            self.assertEqual(frozenset({"VFY-HISTORY-001"}), evidence_markers(java))

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
                PLAYWRIGHT_CONFIG_IMPORT
                + "export default defineConfig({ testDir: './specs', testMatch: /.*\\.spec\\.ts/ });\n",
                encoding="utf-8",
            )
            (selected / "executed.spec.ts").write_text(
                PLAYWRIGHT_TEST_IMPORT
                + "test('VFY-DELIVERY-001 configured evidence', async () => {});\n",
                encoding="utf-8",
            )
            (e2e / "outside.spec.ts").write_text(
                PLAYWRIGHT_TEST_IMPORT
                + "test('VFY-NOT-CONTROLLED-001 outside testDir', async () => {});\n",
                encoding="utf-8",
            )

            result = inventory(root)

            self.assertFalse(result.unknown)
            self.assertFalse(result.missing)
            self.assertEqual(
                (Path("e2e/specs/executed.spec.ts"),),
                result.sources["VFY-DELIVERY-001"],
            )

    def test_inventory_rejects_uninterpreted_playwright_filters(self):
        filters = {
            "testIgnore": "testIgnore: /ignored/",
            "grep": "grep: /only/",
            "grepInvert": "grepInvert: /excluded/",
            "projects": "projects: [{ name: 'chromium' }]",
            "grep-shorthand": "grep,",
        }
        for option, configured_filter in filters.items():
            with self.subTest(option=option), tempfile.TemporaryDirectory() as directory:
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
                    PLAYWRIGHT_CONFIG_IMPORT
                    + "export default defineConfig({ testDir: '.', testMatch: /.*\\.spec\\.ts/, "
                    + configured_filter
                    + " });\n",
                    encoding="utf-8",
                )

                with self.assertRaisesRegex(
                    ValueError,
                    rf"unsupported Playwright evidence filter {option.split('-', 1)[0]}",
                ):
                    inventory(root)

    def test_inventory_rejects_focused_playwright_tests_across_the_suite(self):
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
                PLAYWRIGHT_CONFIG_IMPORT
                + "export default defineConfig({ testDir: '.', testMatch: /.*\\.spec\\.ts/ });\n",
                encoding="utf-8",
            )
            (e2e / "focused.spec.ts").write_text(
                PLAYWRIGHT_TEST_IMPORT + "test.only('focused', async () => {});\n",
                encoding="utf-8",
            )
            (e2e / "evidence.spec.ts").write_text(
                PLAYWRIGHT_TEST_IMPORT
                + "test('VFY-DELIVERY-001 normally executable', async () => {});\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ValueError, "focused Playwright .only call"):
                inventory(root)

    def test_disabled_junit_element_does_not_hide_colocated_active_marker(self):
        sources = {
            "class": (
                JUNIT_TAG_IMPORT
                + 'import org.junit.jupiter.api.Disabled;\n'
                '@Disabled\n@Tag("VFY-HISTORY-001")\n'
                'class DisabledTests { @Test void disabledEvidence() {} }\n'
                '@Tag("VFY-DELIVERY-001")\n'
                'class ActiveTests { @Test void activeEvidence() {} }\n'
            ),
            "method": (
                JUNIT_TAG_IMPORT
                + 'import org.junit.jupiter.api.Disabled;\n'
                'class EvidenceTests {\n'
                '  @Disabled\n  @Tag("VFY-HISTORY-001")\n'
                '  @Test void disabledEvidence() {}\n'
                '  @Tag("VFY-DELIVERY-001")\n  @Test void activeEvidence() {}\n'
                '}\n'
            ),
        }
        for scope, source in sources.items():
            with self.subTest(scope=scope), tempfile.TemporaryDirectory() as directory:
                java = Path(directory) / "Evidence.java"
                java.write_text(source, encoding="utf-8")

                self.assertEqual(
                    frozenset({"VFY-DELIVERY-001"}),
                    evidence_markers(java),
                )

    def test_linux_junit_conditions_only_invalidate_attached_elements(self):
        sources = {
            "disabled-method": (
                JUNIT_TAG_IMPORT
                + "import org.junit.jupiter.api.condition.DisabledOnOs;\n"
                "import org.junit.jupiter.api.condition.OS;\n"
                "class EvidenceTests {\n"
                "  @DisabledOnOs(OS.LINUX)\n"
                '  @Tag("VFY-HISTORY-001")\n  @Test void linuxDisabled() {}\n'
                '  @Tag("VFY-DELIVERY-001")\n  @Test void active() {}\n'
                "}\n"
            ),
            "windows-only-class": (
                JUNIT_TAG_IMPORT
                + "@org.junit.jupiter.api.condition.EnabledOnOs("
                "org.junit.jupiter.api.condition.OS.WINDOWS)\n"
                '@Tag("VFY-HISTORY-001")\n'
                "class WindowsOnlyTests { @Test void windowsOnly() {} }\n"
                '@Tag("VFY-DELIVERY-001")\n'
                "class ActiveTests { @Test void active() {} }\n"
            ),
            "guarded-outer": (
                JUNIT_TAG_IMPORT
                + "import org.junit.jupiter.api.Nested;\n"
                "import org.junit.jupiter.api.condition.DisabledOnOs;\n"
                "import org.junit.jupiter.api.condition.OS;\n"
                "@DisabledOnOs(OS.LINUX)\n"
                "class GuardedOuterTests {\n"
                "  @Nested\n"
                '  @Tag("VFY-HISTORY-001")\n'
                "  class Child { @Test void linuxDisabled() {} }\n"
                "}\n"
                '@Tag("VFY-DELIVERY-001")\n'
                "class ActiveTests { @Test void active() {} }\n"
            ),
        }
        for scope, source in sources.items():
            with self.subTest(scope=scope), tempfile.TemporaryDirectory() as directory:
                java = Path(directory) / "Evidence.java"
                java.write_text(source, encoding="utf-8")

                self.assertEqual(
                    frozenset({"VFY-DELIVERY-001"}),
                    evidence_markers(java),
                )

    def test_dynamic_junit_conditions_fail_closed_per_element(self):
        guards = (
            "@org.junit.jupiter.api.condition.DisabledOnJre("
            "org.junit.jupiter.api.condition.JRE.JAVA_21)",
            "@org.junit.jupiter.api.condition.EnabledForJreRange("
            "min = org.junit.jupiter.api.condition.JRE.JAVA_22)",
            "@org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable("
            'named = "CI", matches = "true")',
            "@org.junit.jupiter.api.condition.EnabledIfSystemProperty("
            'named = "feature", matches = "enabled")',
            "@org.junit.jupiter.api.condition.DisabledInNativeImage",
        )
        for guard in guards:
            with self.subTest(guard=guard), tempfile.TemporaryDirectory() as directory:
                java = Path(directory) / "Evidence.java"
                java.write_text(
                    JUNIT_TAG_IMPORT
                    + "class EvidenceTests {\n"
                    f"  {guard}\n"
                    '  @Tag("VFY-HISTORY-001")\n  @Test void conditional() {}\n'
                    '  @Tag("VFY-DELIVERY-001")\n  @Test void active() {}\n'
                    "}\n",
                    encoding="utf-8",
                )

                self.assertEqual(
                    frozenset({"VFY-DELIVERY-001"}),
                    evidence_markers(java),
                )

    def test_local_composed_junit_guards_are_resolved_transitively(self):
        with tempfile.TemporaryDirectory() as directory:
            java = Path(directory) / "Evidence.java"
            java.write_text(
                JUNIT_TAG_IMPORT
                + "import org.junit.jupiter.api.Disabled;\n"
                "@Disabled @interface DirectGuard {}\n"
                "@DirectGuard @interface TransitiveGuard {}\n"
                "class Guards { @Disabled @interface LocalGuard {} }\n"
                '@Guards.LocalGuard @Tag("VFY-RAG-001")\n'
                "class NestedGuardTests { @Test void skipped() {} }\n"
                "@TransitiveGuard\n"
                '@Tag("VFY-HISTORY-001")\n'
                "class DisabledTests { @Test void skipped() {} }\n"
                "class ActiveTests {\n"
                "  @TransitiveGuard\n"
                '  @Tag("VFY-AUTH-001") @Test void skipped() {}\n'
                '  @Tag("VFY-DELIVERY-001") @Test void executes() {}\n'
                "}\n",
                encoding="utf-8",
            )

            self.assertEqual(
                frozenset({"VFY-DELIVERY-001"}),
                evidence_markers(java),
            )

    def test_cyclic_and_unresolved_local_junit_guards_fail_closed(self):
        sources = {
            "cycle": (
                "@SecondGuard @interface FirstGuard {}\n"
                "@FirstGuard @interface SecondGuard {}\n"
                "@FirstGuard\n"
            ),
            "unresolved": (
                "@MissingGuard @interface LocalGuard {}\n"
                "@LocalGuard\n"
            ),
            "unresolved-explicit-import": (
                "import guards.MissingGuard;\n"
                "@MissingGuard @interface LocalGuard {}\n"
                "@LocalGuard\n"
            ),
            "unresolved-wildcard-import": (
                "import guards.*;\n"
                "@MissingGuard @interface LocalGuard {}\n"
                "@LocalGuard\n"
            ),
        }
        for case, annotations in sources.items():
            with self.subTest(case=case), tempfile.TemporaryDirectory() as directory:
                java = Path(directory) / "Evidence.java"
                java.write_text(
                    JUNIT_TAG_IMPORT
                    + annotations
                    + '@Tag("VFY-HISTORY-001")\n'
                    "class DisabledTests { @Test void skipped() {} }\n"
                    '@Tag("VFY-DELIVERY-001")\n'
                    "class ActiveTests { @Test void executes() {} }\n",
                    encoding="utf-8",
                )

                self.assertEqual(
                    frozenset({"VFY-DELIVERY-001"}),
                    evidence_markers(java),
                )

    def test_inventory_resolves_composed_junit_guard_across_local_sources(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            catalogue = root / "docs/assignment/VV/verification.yaml"
            catalogue.parent.mkdir(parents=True)
            catalogue.write_text(
                "obligations:\n"
                "  VFY-HISTORY-001:\n    covers: []\n"
                "  VFY-DELIVERY-001:\n    covers: []\n",
                encoding="utf-8",
            )
            tests = root / "backend/src/test"
            (tests / "guards").mkdir(parents=True)
            (tests / "proof").mkdir()
            (tests / "guards/BaseGuard.java").write_text(
                "package guards;\nimport org.junit.jupiter.api.Disabled;\n"
                "@Disabled @interface BaseGuard {}\n",
                encoding="utf-8",
            )
            (tests / "guards/ComposedGuard.java").write_text(
                "package guards;\n@BaseGuard public @interface ComposedGuard {}\n",
                encoding="utf-8",
            )
            (tests / "proof/EvidenceTests.java").write_text(
                "package proof;\nimport guards.ComposedGuard;\n"
                + JUNIT_TAG_IMPORT
                + '@ComposedGuard @Tag("VFY-HISTORY-001")\n'
                "class SkippedTests { @Test void skipped() {} }\n"
                '@Tag("VFY-DELIVERY-001")\n'
                "class ActiveTests { @Test void executes() {} }\n",
                encoding="utf-8",
            )
            e2e = root / "e2e"
            e2e.mkdir()
            (e2e / "playwright.config.ts").write_text(
                PLAYWRIGHT_CONFIG_IMPORT
                + "export default defineConfig({ testDir: '.', testMatch: /.*\\.spec\\.ts/ });\n",
                encoding="utf-8",
            )

            result = inventory(root)

            self.assertEqual(frozenset({"VFY-HISTORY-001"}), result.missing)
            self.assertFalse(result.unknown)

    def test_foreign_condition_annotation_does_not_disable_junit_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            java = Path(directory) / "Evidence.java"
            java.write_text(
                JUNIT_TAG_IMPORT
                + "import com.acme.DisabledOnOs;\n"
                "import com.acme.OS;\n"
                "class EvidenceTests {\n"
                "  @DisabledOnOs(OS.LINUX)\n"
                '  @Tag("VFY-HISTORY-001")\n  @Test void executes() {}\n'
                "}\n",
                encoding="utf-8",
            )

            self.assertEqual(
                frozenset({"VFY-HISTORY-001"}),
                evidence_markers(java),
            )

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
                    PLAYWRIGHT_TEST_IMPORT
                    + disabled_call
                    + "\ntest('VFY-DELIVERY-001 active evidence', async () => {});\n",
                    encoding="utf-8",
                )

                self.assertEqual(frozenset(), evidence_markers(playwright))

    def test_computed_playwright_controls_fail_closed_outside_quoted_decoys(self):
        controls = (
            "testInfo['skip']();",
            'testInfo["fixme"]();',
            "testInfo[`skip`]();",
            "test['fail'](true);",
            "const stop = testInfo['skip']; stop();",
            "const member = 'skip'; testInfo[member]();",
            "const member = 'skip'; const stop = testInfo[member]; stop();",
            "const member = 'fixme'; const stop = testInfo[member]; stop();",
            "const member = 'fail'; const stop = testInfo[member]; stop();",
            "const { skip: stop } = testInfo; stop();",
            "const member = 'skip'; const { [member]: stop } = testInfo; stop();",
            "const member = 'skip'; const stop = Reflect.get(testInfo, member); stop();",
        )
        for control in controls:
            with self.subTest(control=control), tempfile.TemporaryDirectory() as directory:
                playwright = Path(directory) / "evidence.spec.ts"
                playwright.write_text(
                    PLAYWRIGHT_TEST_IMPORT
                    + 'const decoy = "testInfo[\\\'skip\\\']()";\n'
                    + control
                    + "\ntest('VFY-DELIVERY-001 active evidence', async () => {});\n",
                    encoding="utf-8",
                )

                self.assertEqual(frozenset(), evidence_markers(playwright))

    def test_inventory_rejects_computed_only_on_imported_test_alias(self):
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
                PLAYWRIGHT_CONFIG_IMPORT
                + "export default defineConfig({ testDir: '.', testMatch: /.*\\.spec\\.ts/ });\n",
                encoding="utf-8",
            )
            focused = e2e / "focused.spec.ts"
            focused.write_text(
                "import { test as scenario } from '@playwright/test';\n"
                + "const focused = scenario['only'];\n"
                "focused('focused', async () => {});\n",
                encoding="utf-8",
            )
            (e2e / "evidence.spec.ts").write_text(
                PLAYWRIGHT_TEST_IMPORT
                + "test('VFY-DELIVERY-001 normally executable', async () => {});\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ValueError, "focused Playwright .only call"):
                inventory(root)
            for computed in (
                "const member = 'only'; test[member]('focused', async () => {});",
                "test[`only`]('focused', async () => {});",
                "const { only: focused } = test; focused('focused', async () => {});",
                "const member = 'only'; const { [member]: focused } = test; "
                "focused('focused', async () => {});",
                "const focused = Reflect.get(test, 'only'); "
                "focused('focused', async () => {});",
            ):
                focused.write_text(PLAYWRIGHT_TEST_IMPORT + computed + "\n", encoding="utf-8")
                with self.subTest(computed=computed), self.assertRaisesRegex(
                    ValueError, "focused Playwright .only call"
                ):
                    inventory(root)
            focused.write_text(
                PLAYWRIGHT_TEST_IMPORT + "test['only']('focused', async () => {});\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "focused Playwright .only call"):
                inventory(root)

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
                JUNIT_TAG_IMPORT
                + "\n".join(f'@Tag("{marker}")' for marker in self.discovered)
                + "\nclass EvidenceTests { @Test void executes() {} }\n",
                encoding="utf-8",
            )
            e2e = root / "e2e"
            e2e.mkdir()
            (e2e / "playwright.config.ts").write_text(
                PLAYWRIGHT_CONFIG_IMPORT
                + "export default defineConfig({ testDir: '.', testMatch: /.*\\.spec\\.ts/ });\n",
                encoding="utf-8",
            )
            return root

        def __exit__(self, exc_type, exc_value, traceback):
            self.temporary.cleanup()


if __name__ == "__main__":
    unittest.main()
