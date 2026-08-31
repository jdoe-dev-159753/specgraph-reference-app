# ADR-006 — Distribute the reviewer deployment as a Compose OCI artifact with multi-platform images

**Decision date:** 2026-08-31  
**Decision owner:** reviewer deployment distribution and architecture portability  
**Builds on:** `ADR-005`, `NFR-REP-001`, `VFY-DELIVERY-001`

## Context

ADR-005 reduced each checkpoint runtime to one Spring Boot executable JAR and one container. J1 then exposed direct `docker run` commands because R0/R1 have no mandatory database yet. That shortcut is useful, but it is not the durable system entrypoint: R2+ will add persistence services while the reviewer/operator should continue launching the system the same way.

Modern Docker Compose (2.34+) can publish a Compose application itself as an OCI artifact and later load it directly with an `oci://` reference. The deployment manifest therefore no longer needs to be obtained through a private source checkout merely to start already-published images.

The same distribution should also run on both the existing x86-64 Docker hosts and an ARM64 Docker host. Docker Buildx can publish one image reference as a multi-platform OCI manifest. The application build is unusually friendly to cross-platform assembly: React static assets and Java bytecode are platform-neutral.

## Decision

### Compose is the durable operator contract

Publish a runtime-only `compose.oci.yaml` as a versioned OCI artifact in GHCR. It contains no source build contexts, bind mounts or repository-local includes. R0 and R1 remain side-by-side on host ports 8080 and 8081.

The moving reviewer deployment is started from a fresh authenticated Docker host with:

```text
docker compose -f oci://ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:demo up -d --wait
```

The publication workflow uses `docker compose publish --resolve-image-digests`, so the Compose artifact records exact image digests even though the authoring file names the convenient `r0` and `r1` tags. An immutable Compose artifact tag also records the R0 source revision, R1 source revision and packaging revision.

Direct `docker run` remains a useful checkpoint/debug shortcut, not the canonical multi-service deployment abstraction.

### Multi-platform application images

Publish each R0/R1 image as one manifest containing:

```text
linux/amd64
linux/arm64
```

Buildx selects the appropriate runtime image automatically when that image is pulled on an x86-64 VPS/lab host or an ARM64 Docker host.

Node/Vite and Maven stages explicitly execute on `BUILDPLATFORM`. They produce architecture-neutral static assets and Java bytecode. The final stage contains no target-architecture `RUN` instruction; it only selects the target-platform Java 21 JRE and copies the JAR. This avoids emulating the complete Java/Node build merely to publish an ARM64 runtime image.

### Evidence

Trusted publication automation must:

1. verify native R0/R1 side-by-side before publication;
2. publish multi-platform R0/R1 manifests;
3. assert that both `linux/amd64` and `linux/arm64` are present;
4. publish the Compose OCI artifact with resolved image digests;
5. remove locally built checkpoint images;
6. deploy through the remote `oci://...:demo` artifact;
7. re-run browser-level R1 Playwright evidence.

This proves registry distribution rather than local cache success.

## Consequences

- the reviewer command remains Compose when PostgreSQL or other later-ring services are introduced;
- no Git clone is required merely to obtain the deployment manifest;
- one image tag works on both amd64 and arm64 Docker hosts;
- an ARM64 Brume deployment becomes technically possible without an ARM-specific application branch or tag;
- GHCR authentication remains required while the packages are private;
- Docker Compose 2.34+ is required for `oci://` Compose artifacts;
- package visibility and internet/router exposure remain independent deployment decisions.

## Alternatives not selected

### Continue using direct `docker run` as the primary entrypoint

Rejected as the durable abstraction because it stops scaling cleanly when later rings introduce PostgreSQL or additional mandatory services.

### Require a source clone to obtain `compose.yaml`

Rejected for reviewer execution. Source access and runtime distribution are separate concerns.

### Publish separate amd64 and arm64 tags

Rejected. OCI manifest lists already solve platform selection while preserving one logical checkpoint identity.

### Emulate the full ARM64 build under QEMU

Unnecessary for this application. The build outputs are platform-neutral; only the final JRE layer is platform-specific.

## References

- Docker Compose OCI artifacts: `https://docs.docker.com/compose/how-tos/oci-artifact/`
- Docker multi-platform builds: `https://docs.docker.com/build/building/multi-platform/`
- Docker Buildx build: `https://docs.docker.com/reference/cli/docker/buildx/build/`
