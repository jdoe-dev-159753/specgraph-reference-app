import tempfile
import unittest
from pathlib import Path

from scripts.source_doc_coverage import inspect, inspect_public_types


class SourceDocCoverageTests(unittest.TestCase):
    def test_finds_documented_and_undocumented_top_level_types(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "Example.java"
            source.write_text(
                """package example;

/** Maintained contract. */
@Deprecated
public interface Documented {
    public record Nested(String value) {}
}

public final class Missing {}
""",
                encoding="utf-8",
            )

            declarations = inspect_public_types(source)

            self.assertEqual(["Documented", "Missing"], [item.name for item in declarations])
            self.assertEqual([True, False], [item.documented for item in declarations])

    def test_reports_missing_or_undocumented_package_info(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            alpha = root / "example" / "alpha"
            beta = root / "example" / "beta"
            gamma = root / "example" / "gamma"
            alpha.mkdir(parents=True)
            beta.mkdir(parents=True)
            gamma.mkdir(parents=True)
            (alpha / "Alpha.java").write_text(
                "package example.alpha;\n/** Alpha. */\npublic interface Alpha {}\n",
                encoding="utf-8",
            )
            (alpha / "package-info.java").write_text(
                "/** Alpha package. */\npackage example.alpha;\n",
                encoding="utf-8",
            )
            (beta / "Beta.java").write_text(
                "package example.beta;\n/** Beta. */\npublic record Beta(String value) {}\n",
                encoding="utf-8",
            )
            (gamma / "Gamma.java").write_text(
                "package example.gamma;\n/** Gamma. */\npublic interface Gamma {}\n", encoding="utf-8"
            )
            (gamma / "package-info.java").write_text("package example.gamma;\n", encoding="utf-8")

            public_types, _, missing_packages = inspect(root)

            self.assertEqual(3, len(public_types))
            self.assertEqual(["example.beta", "example.gamma"], missing_packages)


if __name__ == "__main__":
    unittest.main()
