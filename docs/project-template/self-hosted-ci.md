# Self-hosted CI bootstrap

Use a self-hosted runner only for a demonstrated need such as private networking, licensed tooling, or
large container workloads. Prefer a dedicated non-administrator account and a disposable workspace.

## Registration contract

- Give each host a unique runner name and the minimal repository scope.
- Require explicit capability labels such as `self-hosted`, operating system, architecture, project,
  `ci`, and the required runtime (for example `docker`).
- Keep secrets in the platform secret store; never place credentials in runner labels or files.
- Constrain network reachability and do not expose private services to untrusted pull-request code.

## Workflow contract

- Select every required custom label explicitly in `runs-on`.
- Check out the event's exact candidate SHA and compare `git rev-parse HEAD` with it before testing.
- Serialize jobs that share ports, builders, caches, or external services.
- Clean only repository-owned containers, networks, images, and generated outputs.
- Retain immutable source identities in built artifacts and prove remote pulls separately from local builds.

The GitHub runner group name is administration metadata; it does not replace capability labels and does
not prove that a job used GitHub-hosted or self-hosted compute.
