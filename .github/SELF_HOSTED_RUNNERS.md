# Private runner trust boundary

Repository jobs execute only on the private pool identified by the complete label set
`[self-hosted, linux, x64, specgraph-reference-app, ci, docker]`. Access to the rootful
Docker socket makes each runner service root-equivalent on its own host. The runner pool
is therefore trusted infrastructure, not a sandbox for arbitrary contributions.

## Registered services

Issue [#367](https://github.com/jdoe-dev-159753/specgraph-reference-app/issues/367)
records the intended two-VPS topology. The repository Actions API independently proves
only two distinct runner registrations:

| GitHub registration | Registration ID | Custom environment label | Pool state at 2026-09-07 snapshot |
| --- | --- | --- | --- |
| `ci-linux-01` | `21` | `deb13` | online and eligible for the exact `ci,docker` pool |
| `ci-linux-02` | `22` | `ubu2604` | offline and quarantined; `ci,docker` labels removed |

At `2026-09-06T19:50:55Z`, [job 101548114256](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/runs/34056079354/job/101548114256)
ran on `ci-linux-01`, while [job 101548113549](https://github.com/jdoe-dev-159753/specgraph-reference-app/actions/runs/34056079067/job/101548113549)
ran simultaneously on `ci-linux-02`. This proves distinct concurrent registrations, not
separate hosts, service accounts, filesystems, or work directories. In the 2026-09-07 API
snapshot, ID 21 was online and eligible while ID
22 was offline. ID 22 now carries `quarantined` and no longer carries `ci` or `docker`, so it
cannot match the exact execution pool. This snapshot does not claim permanent availability. Exclusive
service accounts, credential permissions and non-shared work-directory configuration are
host policy requirements that the repository API cannot attest. Host-level verification
of those controls is required before a registration is eligible. A replacement service
must use a new registration and an empty service-owned work directory.

## Enforced repository controls

- Every job uses the exact private-pool label set. There is no GitHub-managed fallback.
- Durable PR workflows are loaded with `pull_request_target` from the protected base. Their
  job condition rejects non-repository heads before allocation; only then may checkout use
  the exact same-repository PR head.
- Trusted, executable workflow runs share `specgraph-repository-queue` with cancellation disabled and
  `queue: max`. GitHub retains up to 100 waiting runs instead of replacing the existing
  pending run, while permitting only one repository workflow to execute at a time. Matrix
  execution permits at most one variant to run at a time, and alternative jobs are mutually
  exclusive. Fork events use a run-unique group and are rejected before runner allocation,
  so they cannot consume the trusted queue. Public issue and comment triggers are absent.
- Every durable job rejects fork heads before a private runner is allocated for PR events.
  Fork
  contributions require a trusted maintainer to import the change onto a same-repository
  branch before executable verification.
- No comment or issue event allocates the private pool: those payloads cannot prove a PR
  head belongs to this repository before runner allocation. The protected WorkGraph guard
  polls review metadata hourly and the Project reconciler heals issue lifecycle changes
  hourly; trusted dispatches provide an immediate closeout path.
- Token-bearing reconciliation and WorkGraph workflows check out `github.workflow_sha`,
  never the proposed head. `PROJECTS_TOKEN` is read only from the Actions secret by the
  reconciliation step and is never written to repository or runner files.
- Compose project names, Buildx builders, writable npm/Maven caches and Docker credential
  directories include the workflow run and attempt identity. Uploaded artifact names include
  the same provenance. Final cleanup removes exact run resources and static workspace outputs;
  global Docker prune is forbidden.
- Host operators must limit runner credentials and filesystem access to the dedicated
  service and repository. Production credentials and unrestricted network secrets are
  forbidden on these delivery runners; these are requirements, not API-observed facts.

These controls trust repository owners and collaborators who can write same-repository
branches; rootful Docker is not an isolation boundary against them. External action refs
are pinned to commits, but tagged container base/build images remain an upstream
supply-chain dependency. Cleanup is best effort and cannot run after a host crash or hard
kill; an operator must reclaim any orphan before returning that host to service.

## Native merge statuses

The protected WorkGraph guard publishes `work-graph-integrity` and
`codex-review-freshness` separately on the immutable PR head. It first moves both contexts
to pending, then audits every open main PR and the global open issue/PR corpus. A global
integrity failure invalidates that context on every active head; duplicate open PRs sharing
one head SHA cannot pass either context. Review freshness accepts only a Codex review object
whose full 40-hex `commit_id` equals the head, or the authenticated clean Codex bot comment
that explicitly names that same full SHA. It separately requires every review thread to be
resolved, so freshness is not presented as a clean approval or as resolution of findings.

The default-branch `pull_request_target` lifecycle plus the hourly sweep heals edits,
closure, dismissal, and force-push changes without running candidate workflow code. GitHub
API failure is fail-closed when the status channel remains reachable; a total API outage can
also prevent replacement of an earlier success and is the residual limitation of commit
statuses. The final ruleset therefore requires both contexts, strict up-to-date branches,
and exact-head merge rechecks. Merge queue is not enabled or supported by this workflow.

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

Only after all seven checks pass may an operator remove `quarantined` and restore both the
`ci` and `docker` eligibility labels. An offline registration is not silently returned to
the active pool.

Rootless Docker remains the preferred future host configuration where Testcontainers and
Buildx compatibility can be retained. Until that migration is proven, separate VPS hosts,
same-repository trust, least-privilege service accounts, exact resource scoping and the global queue are the
selected compensating controls. Ephemeral VMs are required before
executing code from an untrusted fork.
