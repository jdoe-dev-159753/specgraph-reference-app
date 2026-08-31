module.exports = async ({ github, context, core }) => {
  const owner = context.repo.owner;
  const repo = context.repo.repo;
  const repoFull = `${owner}/${repo}`;
  const projectTitle = 'SpecGraph Reference App';
  const discoveryLabel = 'discovery';
  const dispositionFieldName = 'Discovery disposition';
  const priorityFieldName = 'Delivery priority';
  const statusFieldName = 'Status';

  const dispositionValues = ['IN_SCOPE', 'FOLLOW_UP', 'ALREADY_TRACKED', 'NON_ACTIONABLE'];
  const priorityValues = ['MANDATORY', 'MUST_HAVE', 'NICE_TO_HAVE'];
  const statusValues = ['Todo', 'In Progress', 'Done'];
  const priorityRank = { MANDATORY: 0, MUST_HAVE: 1, NICE_TO_HAVE: 2 };

  try {
    await github.rest.issues.getLabel({ owner, repo, name: discoveryLabel });
  } catch (error) {
    if (error.status !== 404) throw error;
    await github.rest.issues.createLabel({
      owner, repo, name: discoveryLabel, color: '5319e7',
      description: 'Material discovery requiring Project disposition reconciliation',
    });
  }

  let project = null;
  let cursor = null;
  do {
    const page = await github.graphql(`
      query($login: String!, $after: String) {
        user(login: $login) {
          projectsV2(first: 20, after: $after) {
            nodes {
              id number title
              fields(first: 100) {
                nodes {
                  __typename
                  ... on ProjectV2SingleSelectField { id name options { id name } }
                }
              }
            }
            pageInfo { hasNextPage endCursor }
          }
        }
      }
    `, { login: owner, after: cursor });
    const conn = page.user.projectsV2;
    project = conn.nodes.find(candidate => candidate.title === projectTitle) ?? null;
    if (project || !conn.pageInfo.hasNextPage) break;
    cursor = conn.pageInfo.endCursor;
  } while (true);
  if (!project) throw new Error(`User-owned Project v2 not visible: ${projectTitle}`);

  const singleField = name => project.fields.nodes.find(
    field => field?.__typename === 'ProjectV2SingleSelectField' && field.name === name
  );
  const dispositionField = singleField(dispositionFieldName);
  const priorityField = singleField(priorityFieldName);
  const statusField = singleField(statusFieldName);
  for (const [field, name, required] of [
    [dispositionField, dispositionFieldName, dispositionValues],
    [priorityField, priorityFieldName, priorityValues],
    [statusField, statusFieldName, statusValues],
  ]) {
    if (!field) throw new Error(`Missing Project field: ${name}`);
    const options = new Set(field.options.map(option => option.name));
    for (const value of required) {
      if (!options.has(value)) throw new Error(`Missing ${name} option: ${value}`);
    }
  }
  const optionIds = field => Object.fromEntries(field.options.map(option => [option.name, option.id]));
  const dispositionOption = optionIds(dispositionField);
  const priorityOption = optionIds(priorityField);
  const statusOption = optionIds(statusField);

  const allItems = [];
  cursor = null;
  do {
    const page = await github.graphql(`
      query($projectId: ID!, $after: String) {
        node(id: $projectId) {
          ... on ProjectV2 {
            items(first: 100, after: $after) {
              nodes {
                id
                fieldValues(first: 100) {
                  nodes {
                    __typename
                    ... on ProjectV2ItemFieldSingleSelectValue {
                      name
                      field { ... on ProjectV2SingleSelectField { id name } }
                    }
                  }
                }
                content {
                  __typename
                  ... on Issue {
                    id number state
                    repository { nameWithOwner }
                    labels(first: 100) { nodes { name } }
                    assignees(first: 100) { nodes { login } }
                    milestone { number title dueOn }
                    parent { number repository { nameWithOwner } }
                  }
                  ... on PullRequest {
                    id number state merged mergedAt closedAt
                    repository { nameWithOwner }
                    labels(first: 100) { nodes { name } }
                    assignees(first: 100) { nodes { login } }
                    milestone { number title dueOn }
                  }
                }
              }
              pageInfo { hasNextPage endCursor }
            }
          }
        }
      }
    `, { projectId: project.id, after: cursor });
    const conn = page.node.items;
    allItems.push(...conn.nodes);
    if (!conn.pageInfo.hasNextPage) break;
    cursor = conn.pageInfo.endCursor;
  } while (true);

  const repoItems = allItems.filter(item => item.content?.repository?.nameWithOwner === repoFull);
  const issueItems = repoItems.filter(item => item.content.__typename === 'Issue');
  const prItems = repoItems.filter(item => item.content.__typename === 'PullRequest');
  const issueByNumber = new Map(issueItems.map(item => [item.content.number, item]));

  const getSingle = (item, fieldName) => item.fieldValues.nodes.find(
    value => value?.__typename === 'ProjectV2ItemFieldSingleSelectValue' && value.field?.name === fieldName
  )?.name ?? null;

  const effectiveIssuePriority = (item, seen = new Set()) => {
    if (!item) return null;
    const number = item.content.number;
    if (seen.has(number)) throw new Error(`Cycle in native issue parent chain at #${number}`);
    const next = new Set(seen);
    next.add(number);
    const parent = item.content.parent;
    if (!parent) return getSingle(item, priorityFieldName);
    if (parent.repository.nameWithOwner !== repoFull) {
      core.warning(`Issue #${number}: cross-repository parent ${parent.repository.nameWithOwner}#${parent.number}; priority not inherited.`);
      return null;
    }
    const parentItem = issueByNumber.get(parent.number);
    if (!parentItem) {
      core.warning(`Issue #${number}: native parent #${parent.number} absent from Project; priority not inherited from an incomplete WorkGraph.`);
      return null;
    }
    return effectiveIssuePriority(parentItem, next);
  };

  const updateSingleMutation = `
    mutation($project: ID!, $item: ID!, $field: ID!, $option: String!) {
      updateProjectV2ItemFieldValue(input: {
        projectId: $project, itemId: $item, fieldId: $field,
        value: { singleSelectOptionId: $option }
      }) { projectV2Item { id } }
    }
  `;
  const setSingle = async (item, field, options, desired, reason) => {
    if (!desired || getSingle(item, field.name) === desired) return false;
    await github.graphql(updateSingleMutation, {
      project: project.id, item: item.id, field: field.id, option: options[desired],
    });
    core.info(`${item.content.__typename} #${item.content.number}: ${field.name} -> ${desired} (${reason})`);
    return true;
  };

  const clearSingleMutation = `
    mutation($project: ID!, $item: ID!, $field: ID!) {
      clearProjectV2ItemFieldValue(input: {
        projectId: $project, itemId: $item, fieldId: $field
      }) { projectV2Item { id } }
    }
  `;
  const clearSingle = async (item, field, reason) => {
    if (!getSingle(item, field.name)) return false;
    await github.graphql(clearSingleMutation, {
      project: project.id, item: item.id, field: field.id,
    });
    core.info(`${item.content.__typename} #${item.content.number}: cleared ${field.name} (${reason})`);
    return true;
  };

  const closingIssueRefsForPr = async prNumber => {
    const refs = [];
    let after = null;
    do {
      const page = await github.graphql(`
        query($owner: String!, $repo: String!, $number: Int!, $after: String) {
          repository(owner: $owner, name: $repo) {
            pullRequest(number: $number) {
              closingIssuesReferences(first: 100, after: $after) {
                nodes { number repository { nameWithOwner } }
                pageInfo { hasNextPage endCursor }
              }
            }
          }
        }
      `, { owner, repo, number: prNumber, after });
      const conn = page.repository.pullRequest.closingIssuesReferences;
      refs.push(...conn.nodes.map(i => ({ number: i.number, repository: i.repository.nameWithOwner })));
      if (!conn.pageInfo.hasNextPage) break;
      after = conn.pageInfo.endCursor;
    } while (true);
    return refs;
  };

  const closingByPr = new Map();
  for (const item of prItems) {
    const pr = item.content;
    const refs = await closingIssueRefsForPr(pr.number);
    closingByPr.set(pr.number, refs);
  }

const ownerPrRefsForIssue = async issueNumber => {
  const refs = [];
  let after = null;
  do {
    const page = await github.graphql(`
      query($owner: String!, $repo: String!, $number: Int!, $after: String) {
        repository(owner: $owner, name: $repo) {
          issue(number: $number) {
            closedByPullRequestsReferences(first: 100, after: $after) {
              nodes {
                number state merged
                repository { nameWithOwner }
              }
              pageInfo { hasNextPage endCursor }
            }
          }
        }
      }
    `, { owner, repo, number: issueNumber, after });
    const conn = page.repository.issue.closedByPullRequestsReferences;
    refs.push(...conn.nodes.map(pr => ({
      number: pr.number,
      repository: pr.repository.nameWithOwner,
      state: pr.state,
      merged: pr.merged,
    })));
    if (!conn.pageInfo.hasNextPage) break;
    after = conn.pageInfo.endCursor;
  } while (true);
  return refs;
};

const ownedByOpenPr = new Set();
const ownedByMergedPr = new Set();
for (const item of issueItems) {
  const issue = item.content;
  const refs = await ownerPrRefsForIssue(issue.number);
  if (refs.some(pr => pr.state === 'OPEN')) ownedByOpenPr.add(issue.number);
  if (refs.some(pr => pr.merged)) ownedByMergedPr.add(issue.number);
}

  let nativeUpdates = 0;
  let projectUpdates = 0;

  for (const item of issueItems) {
    const issue = item.content;
    if (issue.parent) {
      const desiredPriority = effectiveIssuePriority(item);
      if (desiredPriority && await setSingle(
        item, priorityField, priorityOption, desiredPriority,
        `native parent chain above #${issue.number}`
      )) projectUpdates += 1;
    } else if (!getSingle(item, priorityFieldName)) {
      core.warning(`Issue #${issue.number}: root issue missing authoritative ${priorityFieldName}; root planning decisions are explicit and are not guessed.`);
    }
  }

  for (const item of prItems) {
    const pr = item.content;
    const refs = closingByPr.get(pr.number) ?? [];
    const foreignRefs = refs.filter(ref => ref.repository !== repoFull);
    const localNumbers = refs.filter(ref => ref.repository === repoFull).map(ref => ref.number);
    const missingOwnerNumbers = localNumbers.filter(number => !issueByNumber.has(number));
    const owningItems = localNumbers.map(number => issueByNumber.get(number)).filter(Boolean);

    if (foreignRefs.length || missingOwnerNumbers.length) {
      const reasons = [];
      if (foreignRefs.length) reasons.push(`cross-repository owner(s) ${foreignRefs.map(r => `${r.repository}#${r.number}`).join(', ')}`);
      if (missingOwnerNumbers.length) reasons.push(`owner(s) absent from Project ${missingOwnerNumbers.map(n => `#${n}`).join(', ')}`);
      if (await clearSingle(item, priorityField, `incomplete native owner graph: ${reasons.join('; ')}`)) projectUpdates += 1;
      core.warning(`PullRequest #${pr.number}: ${reasons.join('; ')}; owner-derived metadata not reconciled from a partial set.`);
    } else if (owningItems.length) {
      const ownerAssignees = [...new Set(owningItems.flatMap(i => i.content.assignees.nodes.map(a => a.login)))];
      const currentAssignees = new Set(pr.assignees.nodes.map(a => a.login));
      const missingAssignees = ownerAssignees.filter(login => !currentAssignees.has(login));
      if (missingAssignees.length) {
        await github.rest.issues.addAssignees({ owner, repo, issue_number: pr.number, assignees: missingAssignees });
        nativeUpdates += 1;
      }

      const scheduledOwners = owningItems.map(i => i.content.milestone).filter(Boolean).sort((a, b) => {
        const ad = a.dueOn ? Date.parse(a.dueOn) : Number.POSITIVE_INFINITY;
        const bd = b.dueOn ? Date.parse(b.dueOn) : Number.POSITIVE_INFINITY;
        return ad - bd || a.number - b.number;
      });
      const desiredMilestone = scheduledOwners[0]?.number ?? null;
      if (desiredMilestone && !pr.milestone) {
        await github.rest.issues.update({ owner, repo, issue_number: pr.number, milestone: desiredMilestone });
        nativeUpdates += 1;
      } else if (desiredMilestone && pr.milestone?.number !== desiredMilestone) {
        core.info(`PullRequest #${pr.number}: preserving explicit milestone #${pr.milestone.number}; owner-derived would be #${desiredMilestone}`);
      }

      const ownerLabels = [...new Set(owningItems.flatMap(i => i.content.labels.nodes.map(l => l.name)))].filter(l => l !== discoveryLabel);
      const currentLabels = new Set(pr.labels.nodes.map(l => l.name));
      const missingLabels = ownerLabels.filter(l => !currentLabels.has(l));
      if (missingLabels.length) {
        await github.rest.issues.addLabels({ owner, repo, issue_number: pr.number, labels: missingLabels });
        nativeUpdates += 1;
      }

      const ownerPriorities = owningItems.map(effectiveIssuePriority);
      if (ownerPriorities.every(Boolean)) {
        const desiredPriority = [...ownerPriorities].sort((a, b) => priorityRank[a] - priorityRank[b])[0];
        if (await setSingle(item, priorityField, priorityOption, desiredPriority,
          `highest urgency among owning issue(s) ${localNumbers.map(n => `#${n}`).join(', ')}`)) projectUpdates += 1;
      } else {
        const missing = owningItems.filter(i => !effectiveIssuePriority(i)).map(i => `#${i.content.number}`);
        if (await clearSingle(item, priorityField, `owner priority unresolved on ${missing.join(', ')}`)) projectUpdates += 1;
        core.warning(`PullRequest #${pr.number}: effective owner priority missing on ${missing.join(', ')}; PR priority cleared rather than inferred.`);
      }
    } else {
      if (await clearSingle(item, priorityField, 'no native closing/Development owner issue')) projectUpdates += 1;
      core.warning(`PullRequest #${pr.number}: no native closing issue from this Project; ${priorityFieldName} is not applicable without an owner.`);
    }

    const desiredStatus = pr.state === 'OPEN' ? 'In Progress' : 'Done';
    if (await setSingle(item, statusField, statusOption, desiredStatus, 'native pull-request lifecycle')) projectUpdates += 1;
  }

  for (const item of issueItems) {
    const issue = item.content;
    const currentDisposition = getSingle(item, dispositionFieldName);
    const labels = new Set(issue.labels.nodes.map(l => l.name));

    if (currentDisposition && !labels.has(discoveryLabel)) {
      await github.rest.issues.addLabels({ owner, repo, issue_number: issue.number, labels: [discoveryLabel] });
      labels.add(discoveryLabel);
      nativeUpdates += 1;
    }

    if (labels.has(discoveryLabel)) {
      const { data: liveIssue } = await github.rest.issues.get({ owner, repo, issue_number: issue.number });
      let desiredDisposition = null;
      if (liveIssue.state_reason === 'duplicate') desiredDisposition = 'ALREADY_TRACKED';
      else if (liveIssue.state_reason === 'not_planned') desiredDisposition = 'NON_ACTIONABLE';
      else if (liveIssue.state === 'open') {
        if (ownedByOpenPr.has(issue.number)) desiredDisposition = 'IN_SCOPE';
        else if (currentDisposition !== 'IN_SCOPE') desiredDisposition = 'FOLLOW_UP';
        else core.warning(`Discovery #${issue.number}: preserving explicit IN_SCOPE while native PR ownership settles.`);
      } else if (ownedByMergedPr.has(issue.number)) desiredDisposition = 'IN_SCOPE';
      else core.warning(`Discovery #${issue.number}: closed without duplicate/not-planned reason or merged owning PR; disposition not guessed.`);

      if (desiredDisposition && await setSingle(item, dispositionField, dispositionOption, desiredDisposition, 'native discovery lifecycle')) {
        projectUpdates += 1;
      }
    }

    const desiredStatus = issue.state === 'CLOSED' ? 'Done' : ownedByOpenPr.has(issue.number) ? 'In Progress' : 'Todo';
    if (await setSingle(item, statusField, statusOption, desiredStatus, 'native issue lifecycle/PR ownership')) projectUpdates += 1;
  }

  core.info(`Reconciled Project #${project.number}: native updates=${nativeUpdates}; project updates=${projectUpdates}`);
};
