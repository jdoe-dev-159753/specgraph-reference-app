"""Verifies the deterministic cross-platform frontend dependency lock contract."""

from __future__ import annotations

import json
import unittest
from pathlib import Path


class FrontendLockContractTests(unittest.TestCase):
    ROOT = Path(__file__).resolve().parents[1]

    @classmethod
    def setUpClass(cls) -> None:
        cls.manifest = json.loads(
            (cls.ROOT / "frontend" / "package.json").read_text(encoding="utf-8")
        )
        cls.lock = json.loads(
            (cls.ROOT / "frontend" / "package-lock.json").read_text(encoding="utf-8")
        )

    def test_lock_root_exactly_matches_dependency_manifest(self) -> None:
        self.assertEqual(3, self.lock["lockfileVersion"])
        root = self.lock["packages"][""]
        self.assertEqual(self.manifest["name"], root["name"])
        self.assertEqual(self.manifest["version"], root["version"])
        self.assertEqual(self.manifest["dependencies"], root["dependencies"])
        self.assertEqual(self.manifest["devDependencies"], root["devDependencies"])
        self.assertNotIn("yaml", self.manifest["devDependencies"])
        self.assertNotIn("node_modules/yaml", self.lock["packages"])
        vite = self.lock["packages"]["node_modules/vite"]
        self.assertEqual("^2.4.2", vite["peerDependencies"]["yaml"])
        self.assertTrue(vite["peerDependenciesMeta"]["yaml"]["optional"])

    def test_lock_covers_supported_rolldown_linux_libcs(self) -> None:
        packages = self.lock["packages"]
        rolldown = packages["node_modules/rolldown"]
        version = rolldown["version"]
        for architecture in ("x64", "arm64"):
            for libc in ("gnu", "musl"):
                name = f"@rolldown/binding-linux-{architecture}-{libc}"
                with self.subTest(name=name):
                    self.assertEqual(version, rolldown["optionalDependencies"][name])
                    binding = packages[f"node_modules/{name}"]
                    self.assertEqual(version, binding["version"])
                    self.assertTrue(binding["optional"])
                    self.assertEqual(["linux"], binding["os"])
                    self.assertEqual([architecture], binding["cpu"])
                    self.assertEqual(
                        ["glibc" if libc == "gnu" else "musl"], binding["libc"]
                    )

    def test_registry_artifacts_have_integrity_and_no_alternate_source(self) -> None:
        for name, package in self.lock["packages"].items():
            resolved = package.get("resolved")
            if not resolved:
                continue
            with self.subTest(name=name):
                self.assertTrue(resolved.startswith("https://registry.npmjs.org/"))
                self.assertTrue(package.get("integrity", "").startswith("sha512-"))

    def test_docker_build_reuses_lock_only_after_exact_manifest_match(self) -> None:
        dockerfile = (self.ROOT / "docker" / "app.Dockerfile").read_text(
            encoding="utf-8"
        )
        self.assertIn(
            "COPY frontend/package.json frontend/package-lock.json "
            "/frontend-dependencies/",
            dockerfile,
        )
        self.assertIn(
            "COPY ${SOURCE_ROOT}/frontend/package.json ./package.json", dockerfile
        )
        self.assertIn(
            "cmp -s package.json /frontend-dependencies/package.json", dockerfile
        )
        self.assertIn(
            "cp /frontend-dependencies/package-lock.json ./package-lock.json",
            dockerfile,
        )
        self.assertIn("npm ci --prefer-offline --no-audit --no-fund", dockerfile)

    def test_ci_uses_attempt_scoped_clean_installs(self) -> None:
        workflow = (self.ROOT / ".github" / "workflows" / "application-ci.yml").read_text(
            encoding="utf-8"
        )
        self.assertEqual(2, workflow.count("npm ci --prefer-offline --no-audit --no-fund"))
        self.assertNotIn("npm install --prefer-offline", workflow)
        self.assertIn(
            "specgraph-npm-${{ github.run_id }}-${{ github.run_attempt }}", workflow
        )
        self.assertIn(
            "specgraph-npm-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}", workflow
        )

    def test_image_recipe_identity_includes_shared_dependency_inputs(self) -> None:
        helper = (self.ROOT / "scripts" / "ci" / "ensure-app-image.sh").read_text(
            encoding="utf-8"
        )
        for path in ("frontend/package.json", "frontend/package-lock.json"):
            with self.subTest(path=path):
                self.assertIn(f"printf '%s\\0' '{path}'", helper)
                self.assertIn(f"cat {path}", helper)


if __name__ == "__main__":
    unittest.main()
