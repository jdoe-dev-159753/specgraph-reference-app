# ADR-006 — Distribute the reviewer deployment as a Compose OCI artifact with multi-platform images

**Decision date:** 2026-08-31  
**Decision owner:** reviewer deployment distribution and architecture portability  
**Builds on:** `ADR-005`, `NFR-REP-001`, `VFY-DELIVERY-001`

## Context

ADR-005 reduced each checkpoint runtime to one Spring Boot executable JAR and one container. J1 then exposed direct `docker run` commands because R0/R1 have no mandatory database yet. That shortcut is useful, but it is not the durable system entrypoint: R2 adds PostgreSQL-backed persistence while the reviewer/operator should continue launching the system through the same Compose contract.

Modern Docker Compose (2.34+) can publish a Compose application itself as an OCI artifact and later load it directly with an `oci://` reference. The deployment manifest therefore no longer needs to be obtained through a private source checkout merely to start already-published images.

The same distribution should also run on both the existing x86-64 Docker hosts and an ARM64 Docker host. Docker Buildx can publish one application-image reference as a multi-platform OCI manifest. The application build is unusually friendly to cross-platform assembly: React static assets and Java bytecode are platform-neutral.

## Decision

### Compose is the durable operator contract

Publish a runtime-only `compose.oci.yaml` as a versioned OCI artifact in GHCR. It contains no source build contexts, bind mounts or repository-local includes. The canonical reviewer artifact contains four services: R0 on host port 8080, R1 on host port 8081, PostgreSQL 17 on the private Compose network, and the PostgreSQL-backed R2 checkpoint on host port 8082.

The moving reviewer deployment is started from a fresh authenticated Docker host with:

```text
docker compose -f oci://ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:demo up -d --wait
```

The publication workflow authors the Compose artifact from immutable R0/R1/R2 image references and one PostgreSQL digest resolved once for the publication run. `docker compose publish --resolve-image-digests` then records exact image identities. Both the immutable Compose tag and the moving `:demo` tag are mechanically checked to contain the same complete four-image set before browser evidence is accepted. The immutable Compose tag records the R0 source revision, R1 source revision, R2 source revision and packaging revision.

Direct `docker run` remains a useful checkpoint/debug shortcut for single-image rings, not the canonical multi-service deployment abstraction.

### Multi-platform application images

Publish each R0/R1/R2 application image as one manifest containing:

```text
linux/amd64
linux/arm64
```

Buildx selects the appropriate runtime image automatically when that image is pulled on an x86-64 VPS/lab host or an ARM64 Docker host. PostgreSQL remains the official upstream multi-platform image and is pinned by digest inside each published Compose artifact rather than rebuilt by this repository.

Node/Vite and Maven stages explicitly execute on `BUILDPLATFORM`. They produce architecture-neutral static assets and Java bytecode. The final stage contains no target-architecture `RUN` instruction; it only selects the target-platform Java 21 JRE and copies the JAR. This avoids emulating the complete Java/Node build merely to publish an ARM64 runtime image.

### Evidence

Trusted publication automation must:

1. verify native R0/R1 plus PostgreSQL-backed R2 side-by-side before publication;
2. publish multi-platform R0/R1/R2 application manifests;
3. assert that both `linux/amd64` and `linux/arm64` are present for each application checkpoint;
4. resolve and retain one PostgreSQL 17 digest for the publication run;
5. publish both immutable and moving Compose OCI tags from the same immutable R0/R1/R2/PostgreSQL references;
6. assert that both Compose artifacts resolve to the same complete four-image set;
7. remove locally built checkpoint images;
8. deploy through the remote `oci://...:demo` artifact;
9. re-run browser-level R1 and R2 Playwright evidence against that registry-pulled topology.

This proves registry distribution rather than local cache success and prevents mutable upstream or checkpoint tags from making retained provenance disagree with the deployment actually tested.

## Consequences

- the reviewer command remains stable while R2 introduces PostgreSQL-backed persistence;
- no Git clone is required merely to obtain the deployment manifest;
- one application-image tag works on both amd64 and arm64 Docker hosts;
- the canonical `:demo` artifact exposes R0, R1 and R2 simultaneously, with PostgreSQL private to the Compose network;
- the immutable and moving Compose tags are accepted only when their complete image sets match the publication run's recorded identities;
- an ARM64 Brume deployment becomes technically possible without an ARM-specific application branch or tag;
- GHCR authentication remains required while the packages are private;
- Docker Compose 2.34+ is required for `oci://` Compose artifacts;
- package visibility and internet/router exposure remain independent deployment decisions.

## Alternatives not selected

### Continue using direct `docker run` as the primary entrypoint

Rejected as the durable abstraction because it stops scaling cleanly when R2 introduces PostgreSQL or later rings add other mandatory services.

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
