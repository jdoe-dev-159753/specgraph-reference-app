from pathlib import Path

path = Path('.github/workflows/demo-images.yml')
source = path.read_text()

old_cleanup = '''          docker buildx prune -af || true
          docker builder prune -af || true
          docker container prune -f || true
          docker network prune -f || true
          docker image prune -af || true
'''
new_cleanup = '''          # The runner is shared with other exact-head jobs. Reclaim only publication-owned
          # resources here; the capacity gate below fails safely rather than deleting another
          # job's freshly built image or stopped diagnostic container.
          docker image prune -f --filter 'until=24h' || true
'''
if source.count(old_cleanup) != 1:
    raise SystemExit(f'expected one aggressive startup cleanup block, found {source.count(old_cleanup)}')
source = source.replace(old_cleanup, new_cleanup, 1)

old_final_cleanup = '''          docker buildx prune -af || true
          docker builder prune -af || true
          docker logout ghcr.io || true
'''
new_final_cleanup = '''          docker logout ghcr.io || true
'''
if source.count(old_final_cleanup) != 1:
    raise SystemExit(f'expected one aggressive final cleanup block, found {source.count(old_final_cleanup)}')
source = source.replace(old_final_cleanup, new_final_cleanup, 1)

prepublish_anchor = '''          docker run --rm --network host --ipc=host \\
            --user "$(id -u):$(id -g)" \\
            -e HOME=/tmp -e npm_config_cache=/tmp/.npm \\
            -e BASE_URL=http://127.0.0.1:8085 \\
            -e EVIDENCE_NAME=r4-bayesian \\
            -e EXPECT_DETECTOR=beta-binomial-review-elevation-v1 \\
            -v "$PWD/e2e:/work" -w /work \\
            mcr.microsoft.com/playwright:v1.55.0-noble \\
            bash -lc 'npm install --no-package-lock --ignore-scripts && npx playwright test r4-complete-flow.spec.ts'

          docker compose -f compose.oci.yaml down -v --remove-orphans
'''
prepublish_replacement = '''          docker run --rm --network host --ipc=host \\
            --user "$(id -u):$(id -g)" \\
            -e HOME=/tmp -e npm_config_cache=/tmp/.npm \\
            -e BASE_URL=http://127.0.0.1:8085 \\
            -e EVIDENCE_NAME=r4-bayesian \\
            -e EXPECT_DETECTOR=beta-binomial-review-elevation-v1 \\
            -v "$PWD/e2e:/work" -w /work \\
            mcr.microsoft.com/playwright:v1.55.0-noble \\
            bash -lc 'npm install --no-package-lock --ignore-scripts && npx playwright test r4-complete-flow.spec.ts'

          docker run --rm --network host --ipc=host \\
            --user "$(id -u):$(id -g)" \\
            -e HOME=/tmp -e npm_config_cache=/tmp/.npm \\
            -e R4_BASELINE_URL=http://127.0.0.1:8084 \\
            -e R4_BAYESIAN_URL=http://127.0.0.1:8085 \\
            -v "$PWD/e2e:/work" -w /work \\
            mcr.microsoft.com/playwright:v1.55.0-noble \\
            bash -lc 'npm install --no-package-lock --ignore-scripts && npx playwright test r4-side-by-side-session-isolation.spec.ts'

          docker compose -f compose.oci.yaml down -v --remove-orphans
'''
if source.count(prepublish_anchor) != 1:
    raise SystemExit(f'expected one prepublication Bayesian anchor, found {source.count(prepublish_anchor)}')
source = source.replace(prepublish_anchor, prepublish_replacement, 1)

remote_anchor = '''          docker run --rm --network host --ipc=host \\
            --user "$(id -u):$(id -g)" \\
            -e HOME=/tmp -e npm_config_cache=/tmp/.npm \\
            -e BASE_URL=http://127.0.0.1:8085 \\
            -e EVIDENCE_NAME=r4-bayesian-remote \\
            -e EXPECT_DETECTOR=beta-binomial-review-elevation-v1 \\
            -v "$PWD/e2e:/work" -w /work \\
            mcr.microsoft.com/playwright:v1.55.0-noble \\
            bash -lc 'npm install --no-package-lock --ignore-scripts && npx playwright test r4-complete-flow.spec.ts'

          docker compose -f "$compose_ref" ps
'''
remote_replacement = '''          docker run --rm --network host --ipc=host \\
            --user "$(id -u):$(id -g)" \\
            -e HOME=/tmp -e npm_config_cache=/tmp/.npm \\
            -e BASE_URL=http://127.0.0.1:8085 \\
            -e EVIDENCE_NAME=r4-bayesian-remote \\
            -e EXPECT_DETECTOR=beta-binomial-review-elevation-v1 \\
            -v "$PWD/e2e:/work" -w /work \\
            mcr.microsoft.com/playwright:v1.55.0-noble \\
            bash -lc 'npm install --no-package-lock --ignore-scripts && npx playwright test r4-complete-flow.spec.ts'

          docker run --rm --network host --ipc=host \\
            --user "$(id -u):$(id -g)" \\
            -e HOME=/tmp -e npm_config_cache=/tmp/.npm \\
            -e R4_BASELINE_URL=http://127.0.0.1:8084 \\
            -e R4_BAYESIAN_URL=http://127.0.0.1:8085 \\
            -v "$PWD/e2e:/work" -w /work \\
            mcr.microsoft.com/playwright:v1.55.0-noble \\
            bash -lc 'npm install --no-package-lock --ignore-scripts && npx playwright test r4-side-by-side-session-isolation.spec.ts'

          docker compose -f "$compose_ref" ps
'''
if source.count(remote_anchor) != 1:
    raise SystemExit(f'expected one remote Bayesian anchor, found {source.count(remote_anchor)}')
source = source.replace(remote_anchor, remote_replacement, 1)

Path('scripts/demo-images-shared-host-safe.yml').write_text(source)
print('staged shared-host-safe full demo publication workflow')
