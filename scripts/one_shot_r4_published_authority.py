from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1))


replace_once(
    "README.md",
    """The established publication lineage exposes the immutable concentric checkpoints side by side:\n\n```text\nR0: http://<docker-host>:8080/\nR1: http://<docker-host>:8081/\nR2: http://<docker-host>:8082/\nR3: http://<docker-host>:8083/\n```\n\nThe source R4 gallery above is intentionally independent of the last-known-good GHCR publication contract. A source capability is not advertised as remotely published until `demo-images` proves the corresponding pulled artifact.\n""",
    """The accepted reviewer publication topology extends the historical R0-R3 set with two isolated R4 runtime variants. R4 baseline and R4 Bayesian use the same immutable R4 application image but separate pgvector/PostgreSQL services, so browser comparisons do not share retrieval state or analysis history.\n\n```text\nR0:          http://<docker-host>:8080/\nR1:          http://<docker-host>:8081/\nR2:          http://<docker-host>:8082/\nR3:          http://<docker-host>:8083/\nR4 baseline: http://<docker-host>:8084/\nR4 Bayesian: http://<docker-host>:8085/\n```\n\nThe `:demo` tag remains last-known-good rather than source-head-following. Until a publication run for this contract succeeds, the registry may still resolve to the previous accepted R0-R3 artifact. Promotion to the R0-R4 topology occurs only after the remote Compose artifact is re-pulled and all six application endpoints, including baseline/Bayesian detector provenance, pass executable verification.\n""",
)
replace_once(
    "README.md",
    "The reviewer topology uses an ephemeral PostgreSQL container for deterministic fixture state. Removing the deployment resets the fixture database for the next launch.\n",
    "The reviewer topology uses ephemeral database containers for deterministic fixture state. R2/R3 share the historical PostgreSQL service; R4 baseline and R4 Bayesian each use an isolated pgvector/PostgreSQL service. Removing the deployment resets all reviewer fixture state for the next launch.\n",
)

replace_once(
    "docs/assignment/SDD/SDD.md",
    """- R0: host `8080` -> container Tomcat `8080`;\n- R1: host `8081` -> container Tomcat `8080`;\n- R2: host `8082` -> container Tomcat `8080`, private PostgreSQL dependency;\n- R3: host `8083` -> container Tomcat `8080`, same PostgreSQL infrastructure plus analysis history.\n""",
    """- R0: host `8080` -> container Tomcat `8080`;\n- R1: host `8081` -> container Tomcat `8080`;\n- R2: host `8082` -> container Tomcat `8080`, private PostgreSQL dependency;\n- R3: host `8083` -> container Tomcat `8080`, same historical PostgreSQL infrastructure plus analysis history;\n- R4 baseline: host `8084` -> container Tomcat `8080`, profile `r4`, private pgvector/PostgreSQL dependency;\n- R4 Bayesian: host `8085` -> container Tomcat `8080`, profile `r4,bayesian-detector`, separate private pgvector/PostgreSQL dependency.\n\nThe two R4 services are configuration variants of the same accepted R4 capability, not new delivery rings. They run the same immutable R4 application image and deliberately isolate database/retrieval/history state so reviewer comparisons cannot contaminate one another.\n""",
)
replace_once(
    "docs/assignment/SDD/SDD.md",
    "The published Compose OCI tag `ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:demo` is a **last-known-good artifact**. It advances only after publication resolves immutable R0/R1/R2/R3/PostgreSQL image digests, binds the complete five-image set into the retained Compose identity, pulls the remote Compose artifact again and passes executable browser verification.\n",
    "The published Compose OCI tag `ghcr.io/jdoe-dev-159753/specgraph-reference-app-compose:demo` is a **last-known-good artifact**. The accepted R4 reviewer publication resolves immutable R0/R1/R2/R3/R4 application digests plus PostgreSQL and pgvector digests, binds that complete seven-unique-image set into the retained Compose identity, re-pulls the remote Compose artifact, and passes executable browser verification on application ports `8080` through `8085` before promotion. The two R4 processes resolve the same R4 image digest while their two pgvector services resolve the same pinned pgvector image digest.\n",
)
replace_once(
    "docs/assignment/SDD/SDD.md",
    "Accepted source checkpoints are preserved through `demo/r0`, `demo/r1`, `demo/r2` and `demo/r3`. A failed publication leaves the previous `:demo` tag untouched. Repository source state and registry publication state are therefore intentionally not conflated.\n",
    "Accepted source checkpoints are preserved through `demo/r0`, `demo/r1`, `demo/r2`, `demo/r3` and `demo/r4`. A failed publication leaves the previous `:demo` tag untouched. Repository source state and registry publication state are therefore intentionally not conflated, so an accepted source contract can temporarily be newer than the last successfully promoted registry artifact.\n",
)
replace_once(
    "docs/assignment/SDD/SDD.md",
    "The complete J2 reviewer contract publishes R0, R1, PostgreSQL-backed R2 and deterministic analysis/history R3 side by side. J2 publication is complete before R4 work advances the source application. Focused R4 verification profiles may run additional exact-head containers in CI, but they do not become published checkpoints or alter the last-known-good Compose contract until the complete R4 ring is accepted as one coherent reviewer capability.\n",
    "The historical J2 reviewer contract published R0, R1, PostgreSQL-backed R2 and deterministic analysis/history R3 side by side. The current accepted reviewer contract extends that lineage with the coherent R4 capability and exposes baseline and Bayesian Stage-1 configurations side by side without inventing R5/R6 delivery rings. Focused verification-only profiles such as `r4-auth` remain CI concerns and are not separately published checkpoints.\n",
)
replace_once(
    "docs/assignment/SDD/SDD.md",
    "- how source and last-known-good published checkpoint states differ;\n- how the complete J2 publication preserves R0-R3 as independent reviewer checkpoints;\n- how R0-R5 extend one architecture concentrically.",
    "- how source and last-known-good published checkpoint states differ;\n- how the historical J2 R0-R3 publication lineage is extended by the accepted R4 reviewer topology while failed publication leaves the previous `:demo` artifact untouched;\n- why R4 baseline and Bayesian are isolated runtime configurations of one immutable R4 capability rather than new rings;\n- how R0-R5 extend one architecture concentrically.",
)

replace_once(
    "docs/assignment/SDD/design-map.yaml",
    "    responsibility: one prebuilt Spring Boot executable-JAR shape instantiated as concentric reviewer checkpoints, with PostgreSQL backing persistence-bearing rings, and distributed as a Compose OCI artifact\n",
    "    responsibility: one prebuilt Spring Boot executable-JAR shape instantiated as concentric reviewer checkpoints and isolated R4 configuration variants, with PostgreSQL/pgvector backing persistence-bearing services, distributed as a Compose OCI artifact\n",
)
replace_once(
    "docs/assignment/SDD/design-map.yaml",
    "    source_checkout_entrypoint: docker compose up --build -d --wait r3\n",
    "    source_checkout_entrypoint: docker compose -p specgraph-r4-baseline -f compose.r4.yaml up -d --build --wait\n",
)
replace_once(
    "docs/assignment/SDD/design-map.yaml",
    "    distribution: GHCR Compose OCI artifact with published checkpoint images and PostgreSQL resolved to exact digests\n",
    "    distribution: GHCR Compose OCI artifact with published R0-R4 checkpoint images plus PostgreSQL and pgvector resolved to exact digests\n",
)
replace_once(
    "docs/assignment/SDD/design-map.yaml",
    "    published_j2_services: [r0, r1, postgres, r2, r3]\n",
    "    published_j2_services: [r0, r1, postgres, r2, r3]\n    published_reviewer_services: [r0, r1, postgres, r2, r3, r4-postgres-baseline, r4, r4-postgres-bayesian, r4-bayesian]\n    published_unique_runtime_images: [r0, r1, r2, r3, r4, postgres, pgvector]\n    r4_runtime_variants: [baseline profile r4 on host 8084, Bayesian profile r4,bayesian-detector on host 8085]\n    r4_state_isolation: baseline and Bayesian use separate pgvector/PostgreSQL services while resolving the same immutable R4 application image\n",
)
replace_once(
    "docs/assignment/SDD/design-map.yaml",
    "    checkpoint_ports: [R0 host 8080 to container 8080, R1 host 8081 to container 8080, R2 host 8082 to container 8080, R3 host 8083 to container 8080]\n",
    "    checkpoint_ports: [R0 host 8080 to container 8080, R1 host 8081 to container 8080, R2 host 8082 to container 8080, R3 host 8083 to container 8080, R4 baseline host 8084 to container 8080, R4 Bayesian host 8085 to container 8080]\n",
)
replace_once(
    "docs/assignment/SDD/design-map.yaml",
    "    r4_auth_verification_profile: exact-head candidate container on host 8084 with SPRING_PROFILES_ACTIVE=r4-auth; verification-only, not a published checkpoint\n",
    "    r4_auth_verification_profile: exact-head candidate container on an isolated CI host port with SPRING_PROFILES_ACTIVE=r4-auth; verification-only, not a published checkpoint\n",
)

print("R4 published reviewer authority reconciled")
