import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';

export function validateReleaseProtection(environment, policies) {
  assert(environment?.name === 'release', 'The release environment must exist');
  const review = environment.protection_rules?.find(rule => rule.type === 'required_reviewers');
  assert(Array.isArray(review?.reviewers) && review.reviewers.length > 0
    && review.reviewers.every(entry => ['User', 'Team'].includes(entry.type)
      && Number.isSafeInteger(entry.reviewer?.id) && entry.reviewer.id > 0),
  'The release environment must require at least one reviewer');
  assert(typeof review.prevent_self_review === 'boolean', 'Missing self-review policy');
  assert(environment.deployment_branch_policy?.custom_branch_policies === true
    && environment.deployment_branch_policy.protected_branches === false,
  'The release environment must restrict deployment refs explicitly');
  assert(policies?.total_count === 1 && policies.branch_policies?.length === 1
    && policies.branch_policies[0].type === 'tag' && policies.branch_policies[0].name === 'v*',
  'The release environment must allow only the v* tag policy');
  return {environment: 'release', reviewers: review.reviewers.length,
    preventSelfReview: review.prevent_self_review, tagPattern: 'v*'};
}

export async function checkReleaseProtection(env, request = fetch) {
  assert(/^[A-Za-z0-9][A-Za-z0-9-]*\/[\w.-]+$/.test(env.GITHUB_REPOSITORY)
    && !['.', '..'].includes(env.GITHUB_REPOSITORY.split('/')[1]), 'Invalid GitHub repository');
  assert(env.GITHUB_TOKEN, 'Missing GitHub token (actions: read is required)');
  const base = `https://api.github.com/repos/${env.GITHUB_REPOSITORY}/environments/release`;
  async function read(url) {
    const response = await request(url, {method: 'GET', redirect: 'error', signal: AbortSignal.timeout(30_000),
      headers: {Authorization: `Bearer ${env.GITHUB_TOKEN}`, Accept: 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2026-03-10', 'User-Agent': 'Knowledgeroot-release-protection'}});
    assert(response.status === 200, `Cannot verify release protection: HTTP ${response.status}. See docs/repository-protection.md`);
    return response.json();
  }
  const environment = await read(base);
  const policies = await read(`${base}/deployment-branch-policies?per_page=100`);
  return validateReleaseProtection(environment, policies);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  console.log(JSON.stringify(await checkReleaseProtection(process.env)));
}
