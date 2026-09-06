# GitHub Project v2 conventions

GitHub's native graph owns lifecycle, hierarchy, dependencies, duplicates, ownership, and PR linkage.
The Project is a planning and reporting projection of those facts.

Create only these independent single-select fields when the repository needs them:

| Field | Values | Applies to |
| --- | --- | --- |
| Delivery priority | `MANDATORY`, `MUST_HAVE`, `NICE_TO_HAVE` | Root planning issues; descendants inherit |
| Discovery disposition | `IN_SCOPE`, `FOLLOW_UP`, `ALREADY_TRACKED`, `NON_ACTIONABLE` | Material discovery issues only |

Project `Status` is derived: closed issue or merged/closed PR is `Done`; open PR and an open issue with
an open owning PR are `In Progress`; other open issues are `Todo`. Do not model a second status in
labels or prose.

Automation should fail visibly on missing cross-repository parents/owners rather than infer from an
incomplete graph. Ordinary pull requests intentionally have no discovery disposition.
