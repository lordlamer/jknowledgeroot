import test from 'node:test';
import assert from 'node:assert/strict';
import {createSnapshot, submitSnapshot} from './dependency-snapshot.mjs';

const env = {GITHUB_SHA: 'a'.repeat(40), GITHUB_REF: 'refs/heads/master',
  GITHUB_REPOSITORY: 'lordlamer/jknowledgeroot', GITHUB_RUN_ID: '123',
  GITHUB_EVENT_NAME: 'push', GITHUB_TOKEN: 'test-token'};
const node = (artifactId, scope = 'compile', children = []) =>
  ({groupId: 'org.example', artifactId, version: '1.0', scope, type: 'jar', children});
const purl = name => `pkg:maven/org.example/${name}@1.0`;
const fixture = () => createSnapshot({children: [node('app', 'compile', [node('shared')]),
  node('tests', 'test', [node('shared', 'test')]), node('shared')]}, env, '2026-09-19T12:00:00Z');

test('replaces the legacy source with a repository-relative manifest and resolved graph', () => {
  const snapshot = fixture();
  assert.equal(snapshot.job.correlator, 'build');
  assert.equal(snapshot.detector.name, 'maven-dependency-tree-action');
  assert.deepEqual(Object.keys(snapshot.manifests), ['pom.xml']);
  assert.equal(snapshot.manifests['pom.xml'].file.source_location, 'pom.xml');
  const packages = snapshot.manifests['pom.xml'].resolved;
  assert.equal(Object.keys(packages).length, 3);
  assert.equal(packages[purl('shared')].scope, 'runtime');
  assert.equal(packages[purl('shared')].relationship, 'direct');
  assert.equal(packages[purl('tests')].scope, 'development');
  assert.deepEqual(packages[purl('app')].dependencies, [purl('shared')]);
});

test('preserves indirect relationships and distinct Maven classifiers', () => {
  const classified = {...node('library'), classifier: 'tests', type: 'test-jar'};
  const packages = createSnapshot({children: [node('app', 'compile', [node('library'), classified])]}, env)
    .manifests['pom.xml'].resolved;
  assert.equal(Object.keys(packages).length, 3);
  assert.equal(packages[purl('library')].relationship, 'indirect');
  assert.ok(packages[purl('library') + '?classifier=tests&type=test-jar']);
});

test('incomplete trees and unresolved versions cannot erase or poison the graph', () => {
  for (const tree of [{}, {children: []}, {children: [{...node('app'), version: '${version}'}]},
    {children: [{...node('app'), scope: 'unknown'}]}, {children: [{...node('app'), groupId: ''}]}]) {
    assert.throws(() => createSnapshot(tree, env));
  }
});

test('rejects PRs, tags, foreign repositories and mismatched artifacts before network access', async () => {
  const forbidden = () => { throw new Error('Network must not be called'); };
  for (const overrides of [{GITHUB_EVENT_NAME: 'pull_request'}, {GITHUB_REF: 'refs/tags/v1'},
    {GITHUB_REPOSITORY: 'fork/project'}, {GITHUB_SHA: 'b'.repeat(40)}, {GITHUB_RUN_ID: '124'}, {GITHUB_TOKEN: ''}]) {
    await assert.rejects(submitSnapshot(fixture(), {...env, ...overrides}, forbidden),
      error => !error.message.includes('Network must not'));
  }
});

test('an older completed build cannot replace a newer master snapshot', async () => {
  const calls = [];
  const result = await submitSnapshot(fixture(), env, async (url, options) => {
    calls.push({url, options});
    return {ok: true, json: async () => ({object: {sha: 'b'.repeat(40)}})};
  });
  assert.equal(result.skipped, true);
  assert.equal(calls.length, 1);
});

test('submits the verified snapshot and fails on API rejection', async () => {
  const snapshot = fixture();
  const calls = [];
  const request = async (url, options) => {
    calls.push({url, options});
    return options.method === 'POST' ? {status: 201, json: async () => ({result: 'SUCCESS', id: 42})}
      : {ok: true, json: async () => ({object: {sha: env.GITHUB_SHA}})};
  };
  assert.deepEqual(await submitSnapshot(snapshot, env, request), {skipped: false, id: 42});
  assert.equal(calls[1].url, 'https://api.github.com/repos/lordlamer/jknowledgeroot/dependency-graph/snapshots');
  assert.equal(calls[1].options.redirect, 'error');
  assert.deepEqual(JSON.parse(calls[1].options.body), snapshot);
  await assert.rejects(submitSnapshot(snapshot, env, async (_, options) => options.method === 'POST'
    ? {status: 403} : {ok: true, json: async () => ({object: {sha: env.GITHUB_SHA}})}), /HTTP 403/);
});
