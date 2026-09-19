import test from 'node:test';
import assert from 'node:assert/strict';
import {checkReleaseProtection, validateReleaseProtection} from './release-protection.mjs';

const fixture = () => ({
  environment: {name: 'release', deployment_branch_policy: {custom_branch_policies: true, protected_branches: false},
    protection_rules: [{type: 'required_reviewers', prevent_self_review: false,
      reviewers: [{type: 'User', reviewer: {id: 123}}]}]},
  policies: {total_count: 1, branch_policies: [{type: 'tag', name: 'v*'}]}
});

test('requires an explicit approval gate for both solo and independent-review models', () => {
  const {environment, policies} = fixture();
  assert.deepEqual(validateReleaseProtection(environment, policies),
    {environment: 'release', reviewers: 1, preventSelfReview: false, tagPattern: 'v*'});
  environment.protection_rules[0].prevent_self_review = true;
  assert.equal(validateReleaseProtection(environment, policies).preventSelfReview, true);
});

test('missing, empty, timer-only and malformed review gates are rejected', () => {
  const {environment, policies} = fixture();
  for (const value of [null, {...environment, name: 'staging'}, {...environment, protection_rules: []},
    {...environment, protection_rules: [{type: 'wait_timer', wait_timer: 60}]},
    {...environment, protection_rules: [{type: 'required_reviewers', reviewers: []}]},
    {...environment, protection_rules: [{type: 'required_reviewers', reviewers: [{type: 'User', reviewer: {id: 0}}]}]}]) {
    assert.throws(() => validateReleaseProtection(value, policies));
  }
});

test('branches, additional policies and incomplete pagination cannot pass as tag-only access', () => {
  const {environment, policies} = fixture();
  for (const value of [{total_count: 0, branch_policies: []},
    {total_count: 1, branch_policies: [{type: 'branch', name: 'v*'}]},
    {total_count: 1, branch_policies: [{type: 'tag', name: '*'}]},
    {total_count: 2, branch_policies: [...policies.branch_policies, {type: 'branch', name: 'master'}]},
    {...policies, total_count: 101}]) {
    assert.throws(() => validateReleaseProtection(environment, value));
  }
  for (const value of [null, {custom_branch_policies: false, protected_branches: true},
    {custom_branch_policies: true, protected_branches: true}]) {
    assert.throws(() => validateReleaseProtection({...environment, deployment_branch_policy: value}, policies));
  }
});

test('reads only the expected GitHub endpoints and refuses redirects', async () => {
  const {environment, policies} = fixture(), calls = [];
  const result = await checkReleaseProtection({GITHUB_REPOSITORY: 'owner/repo', GITHUB_TOKEN: 'fixture'},
    async (url, options) => {
      calls.push({url, options});
      return {status: 200, json: async () => url.includes('deployment-branch-policies') ? policies : environment};
    });
  assert.equal(result.reviewers, 1);
  assert.deepEqual(calls.map(call => call.url), ['https://api.github.com/repos/owner/repo/environments/release',
    'https://api.github.com/repos/owner/repo/environments/release/deployment-branch-policies?per_page=100']);
  assert.ok(calls.every(call => call.options.method === 'GET' && call.options.redirect === 'error'));
});

test('missing environments, denied access and unavailable API prevent publication', async () => {
  for (const status of [302, 403, 404, 429, 500]) {
    await assert.rejects(checkReleaseProtection({GITHUB_REPOSITORY: 'owner/repo', GITHUB_TOKEN: 'fixture'},
      async () => ({status})), new RegExp(`HTTP ${status}`));
  }
  const noNetwork = () => assert.fail('Network must not be called');
  for (const repository of ['../other', 'owner/..', 'owner/.', 'owner/repo/other']) {
    await assert.rejects(checkReleaseProtection({GITHUB_REPOSITORY: repository, GITHUB_TOKEN: 'fixture'}, noNetwork), /Invalid/);
  }
  await assert.rejects(checkReleaseProtection({GITHUB_REPOSITORY: 'owner/repo'}, noNetwork), /Missing GitHub token/);
});
