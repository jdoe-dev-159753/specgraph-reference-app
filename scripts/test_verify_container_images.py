"""Tests the immutable container reference manifest and repository scanner."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from scripts.verify_container_images import audit_sources, audit_testcontainers_helpers, load_manifest, source_files


DIGEST = "sha256:" + "a" * 64
AMD64 = "sha256:" + "b" * 64
ARM64 = "sha256:" + "c" * 64


class ContainerImageAuditTests(unittest.TestCase):
    def write(self, root: Path, relative: str, text: str) -> None:
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def manifest(
        self,
        root: Path,
        reference: str = f"alpine:3.22@{DIGEST}",
        kind: str = "index",
        amd64: str = AMD64,
        arm64: str = ARM64,
    ) -> Path:
        path = root / "images.tsv"
        path.write_text(
            f"{reference}\t{kind}\tlinux/amd64,linux/arm64\t{amd64}\t{arm64}\tfixture utility\n",
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
        cloc = images["aldanial/cloc:2.08"]
        self.assertEqual(cloc.kind, "manifest")
        self.assertEqual(cloc.reference.split("@", 1)[1], cloc.amd64)

    def test_scans_workflow_compose_dockerfile_shell_java_and_properties(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reference = f"alpine:3.22@{DIGEST}"
            images, errors = load_manifest(self.manifest(root, reference))
            self.assertEqual(errors, [])
            for relative, text in {
                ".github/workflows/ci.yml": f"run: docker run --rm {reference} true\n",
                "compose.yaml": f"services:\n  db:\n    image: {reference}\n",
                "nested/docker/app.Dockerfile": f"FROM {reference}\n",
                "scripts/tool.sh": f"TOOL_IMAGE={reference}\ndocker run --rm $TOOL_IMAGE true\n",
                "backend/src/test/Test.java": f'new GenericContainer("{reference}");\n',
                "backend/src/test/resources/testcontainers.properties": f"tinyimage.container.image={reference}\n",
            }.items():
                self.write(root, relative, text)
            checked, findings = audit_sources(root, images)
            self.assertGreaterEqual(checked, 6)
            self.assertEqual(findings, [])

    def test_source_discovery_is_recursive_for_yaml_and_dockerfiles(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write(root, "nested/workflows/proof.yaml", "services: {}\n")
            self.write(root, "nested/images/Dockerfile.verify", "FROM scratch\n")
            self.write(root, "nested/images/verify.Dockerfile", "FROM scratch\n")
            relative = {path.relative_to(root).as_posix() for path in source_files(root)}
            self.assertIn("nested/workflows/proof.yaml", relative)
            self.assertIn("nested/images/Dockerfile.verify", relative)
            self.assertIn("nested/images/verify.Dockerfile", relative)

    def test_rejects_unpinned_and_unmanifested_external_images(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            images, _ = load_manifest(self.manifest(root))
            self.write(root, "compose.yaml", "services:\n  a:\n    image: alpine:3.22\n  b:\n    image: vendor/tool:1\n")
            _, findings = audit_sources(root, images)
            self.assertEqual(len(findings), 2)
            self.assertTrue(any("expected alpine:3.22@" in finding for finding in findings))
            self.assertTrue(any("absent from manifest: vendor/tool:1" in finding for finding in findings))

    def test_resolves_static_shell_binding_but_ignores_network_endpoints(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reference = f"alpine:3.22@{DIGEST}"
            images, _ = load_manifest(self.manifest(root, reference))
            self.write(root, "scripts/tool.sh", f'TOOL_IMAGE="{reference}"\ndocker run --rm "$TOOL_IMAGE" true\ncurl http://postgres:5432/ready\n')
            checked, findings = audit_sources(root, images)
            self.assertGreaterEqual(checked, 1)
            self.assertEqual(findings, [])

    def test_rejects_bare_tagless_digest_only_and_dynamic_contexts(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            images, _ = load_manifest(self.manifest(root))
            self.write(root, "nested/compose.test.yml", f"""services:
  bare:
    image: busybox
  tagged:
    image: busybox:1.36
  tagless:
    image: vendor/tool
  digest_only:
    image: busybox@{DIGEST}
  dynamic:
    image: ${{IMAGE}}
""")
            _, findings = audit_sources(root, images)
            self.assertEqual(len(findings), 5)
            self.assertTrue(any("busybox" in finding and "external image must include" in finding for finding in findings))
            self.assertTrue(any("busybox:1.36" in finding and "absent from manifest" in finding for finding in findings))
            self.assertTrue(any("vendor/tool" in finding and "external image must include" in finding for finding in findings))
            self.assertTrue(any(f"busybox@{DIGEST}" in finding for finding in findings))
            self.assertTrue(any("${IMAGE}" in finding and "not explicitly first-party" in finding for finding in findings))

    def test_rejects_unsafe_dockerfile_folded_run_and_buildkit_contexts(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            images, _ = load_manifest(self.manifest(root))
            self.write(root, "nested/Dockerfile", "FROM vendor/tool\n")
            self.write(root, ".github/workflows/proof.yml", """jobs:
  proof:
    runs-on: self-hosted
    steps:
      - run: >-
          docker run --rm
          busybox
          true
      - run: docker buildx create --driver-opt image=${WORKER_IMAGE}
""")
            _, findings = audit_sources(root, images)
            self.assertEqual(len(findings), 3, findings)
            self.assertTrue(any("Dockerfile FROM" in finding and "vendor/tool" in finding for finding in findings))
            self.assertTrue(any("docker run" in finding and "busybox" in finding for finding in findings))
            self.assertTrue(any("BuildKit worker image" in finding and "WORKER_IMAGE" in finding for finding in findings))

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

    def test_first_party_allowlist_does_not_admit_repository_descendants(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            images, _ = load_manifest(self.manifest(root))
            self.write(root, "compose.yaml", """services:
  local_child:
    image: specgraph-reference-app/evil:latest
  ghcr_child:
    image: ghcr.io/jdoe-dev-159753/specgraph-reference-app/evil:latest
""")
            _, findings = audit_sources(root, images)
            self.assertEqual(len(findings), 2)
            self.assertTrue(all("absent from manifest" in finding for finding in findings))

    def test_only_enumerated_first_party_dynamic_selectors_are_allowed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            images, _ = load_manifest(self.manifest(root))
            self.write(root, "compose.yaml", """services:
  accepted:
    image: ${APP_R4_IMAGE:-specgraph-reference-app:r4}
  required:
    image: ${APP_R4_DEGRADATION_IMAGE:?exact-head image required}
  unknown:
    image: ${VENDOR_IMAGE:-vendor/tool:1}
  wrong_default:
    image: ${APP_R5_IMAGE:-vendor/tool:1}
""")
            _, findings = audit_sources(root, images)
            self.assertEqual(len(findings), 2)
            self.assertTrue(any("VENDOR_IMAGE" in finding for finding in findings))
            self.assertTrue(any("APP_R5_IMAGE default" in finding for finding in findings))

    def test_required_platform_without_manifest_digest_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = self.manifest(root)
            manifest.write_text(
                f"alpine:3.22@{DIGEST}\tindex\tlinux/amd64,linux/arm64\t{AMD64}\t-\tfixture utility\n",
                encoding="utf-8",
            )
            _, errors = load_manifest(manifest)
            self.assertTrue(any("required linux/arm64" in error for error in errors))

    def test_single_platform_manifest_requires_top_level_platform_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reference = f"aldanial/cloc:2.08@{DIGEST}"
            manifest = root / "images.tsv"
            manifest.write_text(
                f"{reference}\tmanifest\tlinux/amd64\t{DIGEST}\t-\tmetrics; embedded cloc reports 2.04\n",
                encoding="utf-8",
            )
            images, errors = load_manifest(manifest)
            self.assertEqual(errors, [])
            self.assertEqual(images["aldanial/cloc:2.08"].kind, "manifest")
            manifest.write_text(
                f"{reference}\tmanifest\tlinux/amd64\t{AMD64}\t-\tbroken fixture\n",
                encoding="utf-8",
            )
            _, errors = load_manifest(manifest)
            self.assertTrue(any("manifest top level" in error for error in errors))

    def test_repository_configures_every_testcontainers_helper(self):
        root = Path(__file__).resolve().parents[1]
        images, errors = load_manifest(root / "scripts/ci/container-images.tsv")
        self.assertEqual(errors, [])
        self.assertEqual(audit_testcontainers_helpers(root, images), [])

    def test_repository_workflow_triggers_every_nested_yaml_and_dockerfile(self):
        root = Path(__file__).resolve().parents[1]
        workflow = (root / ".github/workflows/source-reference.yml").read_text(encoding="utf-8")
        for pattern in ("'**/*.yml'", "'**/*.yaml'", "'**/Dockerfile*'", "'**/*.Dockerfile'"):
            self.assertEqual(workflow.count(pattern), 2)

    def test_run_scoped_buildkit_worker_uses_governed_digest(self):
        root = Path(__file__).resolve().parents[1]
        images, errors = load_manifest(root / "scripts/ci/container-images.tsv")
        self.assertEqual(errors, [])
        helper = (root / "scripts/ci/run-scoped-buildx.sh").read_text(encoding="utf-8")
        self.assertIn(images["moby/buildkit:buildx-stable-1"].reference, helper)
        self.assertIn('--driver-opt "image=$BUILDKIT_IMAGE"', helper)


if __name__ == "__main__":
    unittest.main()
