"""Tests the immutable container reference manifest and repository scanner."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from scripts.verify_container_images import audit_sources, audit_testcontainers_helpers, load_manifest


DIGEST = "sha256:" + "a" * 64
AMD64 = "sha256:" + "b" * 64
ARM64 = "sha256:" + "c" * 64


class ContainerImageAuditTests(unittest.TestCase):
    def write(self, root: Path, relative: str, text: str) -> None:
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def manifest(self, root: Path, reference: str = f"alpine:3.22@{DIGEST}") -> Path:
        path = root / "images.tsv"
        path.write_text(
            f"{reference}\tlinux/amd64,linux/arm64\t{AMD64}\t{ARM64}\tfixture utility\n",
            encoding="utf-8",
        )
        return path

    def test_repository_manifest_is_structurally_complete(self):
        root = Path(__file__).resolve().parents[1]
        images, errors = load_manifest(root / "scripts/ci/container-images.tsv")
        self.assertEqual(errors, [])
        self.assertGreaterEqual(len(images), 18)
        self.assertIn("moby/buildkit:buildx-stable-1", images)
        self.assertIn("testcontainers/ryuk:0.14.0", images)

    def test_scans_workflow_compose_dockerfile_shell_java_and_properties(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reference = f"alpine:3.22@{DIGEST}"
            images, errors = load_manifest(self.manifest(root, reference))
            self.assertEqual(errors, [])
            for relative, text in {
                ".github/workflows/ci.yml": f"run: docker run --rm {reference} true\n",
                "compose.yaml": f"services:\n  db:\n    image: {reference}\n",
                "docker/app.Dockerfile": f"FROM {reference}\n",
                "scripts/tool.sh": f"IMAGE={reference}\n",
                "backend/src/test/Test.java": f'new GenericContainer("{reference}");\n',
                "backend/src/test/resources/testcontainers.properties": f"tinyimage.container.image={reference}\n",
            }.items():
                self.write(root, relative, text)
            checked, findings = audit_sources(root, images)
            self.assertEqual(checked, 6)
            self.assertEqual(findings, [])

    def test_rejects_unpinned_and_unmanifested_external_images(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            images, _ = load_manifest(self.manifest(root))
            self.write(root, "compose.yaml", "services:\n  a:\n    image: alpine:3.22\n  b:\n    image: vendor/tool:1\n")
            _, findings = audit_sources(root, images)
            self.assertEqual(len(findings), 2)
            self.assertTrue(any("expected alpine:3.22@" in finding for finding in findings))
            self.assertTrue(any("absent from manifest: vendor/tool:1" in finding for finding in findings))

    def test_reads_shell_defaults_but_ignores_network_endpoints(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reference = f"alpine:3.22@{DIGEST}"
            images, _ = load_manifest(self.manifest(root, reference))
            self.write(root, "scripts/tool.sh", f'IMAGE="${{IMAGE:-{reference}}}"\ncurl http://postgres:5432/ready\n')
            checked, findings = audit_sources(root, images)
            self.assertEqual(checked, 1)
            self.assertEqual(findings, [])

    def test_allows_only_explicit_first_party_and_local_image_names(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            images, _ = load_manifest(self.manifest(root))
            self.write(root, "compose.yaml", """services:
  local:
    image: specgraph-reference-app:r4
  published:
    image: ghcr.io/jdoe-dev-159753/specgraph-reference-app:r5
""")
            checked, findings = audit_sources(root, images)
            self.assertEqual(checked, 0)
            self.assertEqual(findings, [])

    def test_required_platform_without_manifest_digest_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.manifest(root)
            manifest.write_text(
                f"alpine:3.22@{DIGEST}\tlinux/amd64,linux/arm64\t{AMD64}\t-\tfixture utility\n",
                encoding="utf-8",
            )
            _, errors = load_manifest(manifest)
            self.assertTrue(any("required linux/arm64" in error for error in errors))

    def test_repository_configures_every_testcontainers_helper(self):
        root = Path(__file__).resolve().parents[1]
        images, errors = load_manifest(root / "scripts/ci/container-images.tsv")
        self.assertEqual(errors, [])
        self.assertEqual(audit_testcontainers_helpers(root, images), [])


if __name__ == "__main__":
    unittest.main()
