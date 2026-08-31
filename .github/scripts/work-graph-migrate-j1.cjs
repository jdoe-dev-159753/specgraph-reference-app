module.exports = async ({ github, context, core }) => {
  const owner = context.repo.owner;
  const repo = context.repo.repo;
  const repoFull = `${owner}/${repo}`;
  const projectTitle = 'SpecGraph Reference App';

  const parentMap = new Map([
    [80, 56],
    [90, 56],
    [83, 77],
    [84, 74],
    [85, 74],
    [86, 77],
    [87, 62],
    [89, 62],
    [92, 8],
    [94, 8],
    [98, 5],
    [49, 102],
    [99, 102],
    [100, 102],
    [81, 97],
  ]);

  const explicitRootPriorities = new Map([
    [97, 'NICE_TO_HAVE'],
    [102, 'NICE_TO_HAVE'],
  ]);

  const issueParent = async number => {
    const page = await github.graphql(`
      query($owner: String!, $repo: String!, $number: Int!) {
        repository(owner: $owner, name: $repo) {
          issue(number: $number) {
            id
            parent { number repository { nameWithOwner } }
          }
        }
      }
    `, { owner, repo, number });
    return page.repository.issue;
  };

  for (const [childNumber, parentNumber] of parentMap) {
    const child = await issueParent(childNumber);
    if (child.parent?.repository?.nameWithOwner === repoFull && child.parent.number === parentNumber) {
      core.info(`#${childNumber} already has native parent #${parentNumber}`);
      continue;
    }

    const { data: childRest } = await github.rest.issues.get({ owner, repo, issue_number: childNumber });
    await github.request('POST /repos/{owner}/{repo}/issues/{issue_number}/sub_issues', {
      owner,
      repo,
      issue_number: parentNumber,
      sub_issue_id: childRest.id,
      replace_parent: true,
      headers: { 'X-GitHub-Api-Version': '2026-03-10' },
    });
    core.info(`Native parent #${parentNumber} -> sub-issue #${childNumber}`);
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

  const priorityField = project.fields.nodes.find(
    field => field?.__typename === 'ProjectV2SingleSelectField' && field.name === 'Delivery priority'
  );
  if (!priorityField) throw new Error('Missing Project field: Delivery priority');
  const priorityOptions = Object.fromEntries(priorityField.options.map(option => [option.name, option.id]));

  const projectItems = [];
  cursor = null;
  do {
    const page = await github.graphql(`
      query($project: ID!, $after: String) {
        node(id: $project) {
          ... on ProjectV2 {
            items(first: 100, after: $after) {
              nodes { id content { ... on Issue { id number repository { nameWithOwner } } } }
              pageInfo { hasNextPage endCursor }
            }
          }
        }
      }
    `, { project: project.id, after: cursor });
    const conn = page.node.items;
    projectItems.push(...conn.nodes);
    if (!conn.pageInfo.hasNextPage) break;
    cursor = conn.pageInfo.endCursor;
  } while (true);

  const ensureIssueProjectItem = async issueNumber => {
    const page = await github.graphql(`
      query($owner: String!, $repo: String!, $number: Int!) {
        repository(owner: $owner, name: $repo) { issue(number: $number) { id } }
      }
    `, { owner, repo, number: issueNumber });
    const issueId = page.repository.issue.id;
    const existing = projectItems.find(item => item.content?.id === issueId);
    if (existing) return existing;

    const added = await github.graphql(`
      mutation($project: ID!, $content: ID!) {
        addProjectV2ItemById(input: { projectId: $project, contentId: $content }) {
          item { id }
        }
      }
    `, { project: project.id, content: issueId });
    const item = { id: added.addProjectV2ItemById.item.id, content: { id: issueId, number: issueNumber } };
    projectItems.push(item);
    core.info(`Added #${issueNumber} to Project`);
    return item;
  };

  for (const [issueNumber, priority] of explicitRootPriorities) {
    if (!priorityOptions[priority]) throw new Error(`Missing Delivery priority option: ${priority}`);
    const item = await ensureIssueProjectItem(issueNumber);
    await github.graphql(`
      mutation($project: ID!, $item: ID!, $field: ID!, $option: String!) {
        updateProjectV2ItemFieldValue(input: {
          projectId: $project,
          itemId: $item,
          fieldId: $field,
          value: { singleSelectOptionId: $option }
        }) { projectV2Item { id } }
      }
    `, {
      project: project.id,
      item: item.id,
      field: priorityField.id,
      option: priorityOptions[priority],
    });
    core.info(`#${issueNumber} Delivery priority -> ${priority}`);
  }

  const { data: followUp } = await github.rest.issues.get({ owner, repo, issue_number: 81 });
  const labels = new Set(followUp.labels.map(label => typeof label === 'string' ? label : label.name));
  if (!labels.has('enhancement')) {
    await github.rest.issues.addLabels({ owner, repo, issue_number: 81, labels: ['enhancement'] });
  } else {
    await github.rest.issues.removeLabel({ owner, repo, issue_number: 81, name: 'enhancement' }).catch(() => {});
    await github.rest.issues.addLabels({ owner, repo, issue_number: 81, labels: ['enhancement'] });
  }
  core.info('Triggered durable Project reconciliation from #81 label refresh');
};
