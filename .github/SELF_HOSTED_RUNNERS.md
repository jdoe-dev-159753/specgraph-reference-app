# Private runner trust boundary

Repository jobs execute only on the private pool identified by the complete label set
`[self-hosted, linux, x64, specgraph-reference-app, ci, docker]`. Access to the rootful
Docker socket makes each runner service root-equivalent on its own host. The runner pool
is therefore trusted infrastructure, not a sandbox for arbitrary contributions.

## Registered services

The closeout inventory on 2026-09-06 records two distinct GitHub runner registrations on
two privately controlled VPS hosts:

| GitHub registration | Registration ID | Host identity label | Work-directory identity |
| --- | --- | --- | --- |
| `ci-linux-01` | `21` | `deb13` | host-local `_work` below the `ci-linux-01` runner installation |
| `ci-linux-02` | `22` | `ubu2604` | host-local `_work` below the `ci-linux-02` runner installation |

The distinct registration IDs and host labels are visible through the repository Actions
runner inventory; both registrations were online in the closeout read. The `_work` paths
are intentionally described as host-local identities rather than pretending equal path
strings on separate machines are shared storage. Because the services run on separate VPS
filesystems, their work directories cannot be shared. A replacement service must use a new
registration and an empty service-owned work directory; copying or bind-mounting another
runner's `_work` directory is forbidden.

## Enforced repository controls

- Every job uses the exact private-pool label set. There is no GitHub-managed fallback.
- All workflow runs share `specgraph-repository-queue` with cancellation disabled and
  `queue: max`. GitHub retains up to 100 waiting runs instead of replacing the existing
  pending run, while permitting only one repository workflow to execute at a time. Matrix
  execution is bounded to one variant, and alternative jobs are mutually exclusive.
- Workflows that check out and execute a pull-request head reject fork heads before a
  private runner is allocated. Fork contributions require a trusted maintainer to import
  the change onto a same-repository branch before executable verification.
- Token-bearing reconciliation and WorkGraph workflows check out `github.workflow_sha`,
  never the proposed head. `PROJECTS_TOKEN` is read only from the Actions secret by the
  reconciliation step and is never written to repository or runner files.
- Compose project names, Buildx builders, writable npm/Maven caches, evidence paths and
  temporary directories include the workflow run identity. Cleanup targets the exact
  Compose project, builder, volume, or repository label; global Docker prune is forbidden.
- Runner credentials and filesystem access are limited to the service account and this
  repository. No production credentials or unrestricted network secrets belong on these
  delivery runners.

## Host attestation and replacement checklist

Before registering or returning a service to the pool, the operator records the following
outside the repository because service configuration and credential files are host-local:

1. the unique GitHub registration name and ID;
2. the VPS identity and the service account;
3. the canonical path of the service's exclusive work directory;
4. ownership and permissions for the service, work directory and Docker socket;
5. confirmation that no other runner service references that work directory;
6. confirmation that registration credentials are readable only by the service account;
7. a clean queued workflow proving the expected runner name and source SHA.

Rootless Docker remains the preferred future host configuration where Testcontainers and
Buildx compatibility can be retained. Until that migration is proven, separate VPS hosts,
same-repository trust, least-privilege service accounts, exact resource scoping and the
global queue are the selected compensating controls. Ephemeral VMs are required before
executing code from an untrusted fork.
