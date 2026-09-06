"""Proves the Java documentation ratchet inventories nested types and non-trivial executable intent."""

import tempfile
import unittest
from pathlib import Path

from scripts.source_doc_coverage import inspect, inspect_executables, inspect_types


class SourceDocCoverageTests(unittest.TestCase):
    def test_inventories_nested_non_public_types(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "Example.java"
            source.write_text(
                """package example;
/** Boundary intent. */
final class Boundary {
    /** Closed state intent. */
    private enum State { READY }
    static final class Missing {}
}
""",
                encoding="utf-8",
            )
            declarations = inspect_types(source)
            self.assertEqual(["Boundary", "State", "Missing"], [item.name for item in declarations])
            self.assertEqual([True, True, False], [item.documented for item in declarations])

    def test_requires_docs_for_nontrivial_body_but_accepts_explicit_scenario_name(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "Example.java"
            source.write_text(
                """/** Suite boundary and limits. */
class Example {
    int accessor() { return 1; }

    int missing(int value) {
        if (value > 0) {
            return value;
        }
        if (value < 0) {
            return -value;
        }
        return 0;
    }

    @Test
    void invalidInputStopsBeforePersistingAnyHistoryEntry() {
        if (true) { check(); }
        if (true) { check(); }
    }
}
""",
                encoding="utf-8",
            )
            by_name = {item.name: item for item in inspect_executables(source, {"Example"})}
            self.assertTrue(by_name["accessor"].justified)
            self.assertFalse(by_name["missing"].justified)
            self.assertTrue(by_name["invalidInputStopsBeforePersistingAnyHistoryEntry"].justified)

    def test_combines_production_tests_fixture_and_package_inventory(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            production = base / "main" / "example"
            tests = base / "test" / "example"
            fixture = base / "fixture" / "example"
            for root in (production, tests, fixture):
                root.mkdir(parents=True)
            (production / "package-info.java").write_text(
                "/** Production package boundary. */\npackage example;\n", encoding="utf-8"
            )
            (production / "Main.java").write_text(
                "package example;\n/** Main intent. */\nclass Main {}\n", encoding="utf-8"
            )
            (tests / "MainTests.java").write_text(
                "package example;\n/** Proof and limits. */\nclass MainTests {}\n", encoding="utf-8"
            )
            (fixture / "Fixture.java").write_text(
                "package example;\n/** Failure intent. */\nclass Fixture {}\n", encoding="utf-8"
            )
            coverage = inspect((base / "main", base / "test", base / "fixture"))
            self.assertEqual(4, len(coverage.sources))
            self.assertEqual(3, len(coverage.types))
            self.assertEqual(("example",), coverage.packages)
            self.assertEqual((), coverage.missing_package_docs)


if __name__ == "__main__":
    unittest.main()
