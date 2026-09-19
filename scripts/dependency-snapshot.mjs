import assert from 'node:assert/strict';
import {readFileSync, writeFileSync} from 'node:fs';
import {pathToFileURL} from 'node:url';

// Preserve the source identity used by the former Maven submission action.
// A different correlator would leave its obsolete dependencies in GitHub's graph.
export const detector = {
  name: 'maven-dependency-tree-action',
  version: 'knowledgeroot-1',
  url: 'https://github.com/lordlamer/jknowledgeroot/blob/master/scripts/dependency-snapshot.mjs'
};

function coordinate(value) {
  assert(typeof value === 'string' && value.trim() === value && value.length > 0
    && !/[\s${}]/.test(value), 'Unresolved or missing Maven coordinate');
  return encodeURIComponent(value);
}

export function createSnapshot(tree, env, scanned = new Date().toISOString()) {
  assert(/^[a-f0-9]{40}$/.test(env.GITHUB_SHA), 'Invalid commit');
  assert(env.GITHUB_REF === 'refs/heads/master', 'Only master snapshots are supported');
  assert(/^[\w.-]+\/[\w.-]+$/.test(env.GITHUB_REPOSITORY), 'Invalid repository');
  assert(/^\d+$/.test(env.GITHUB_RUN_ID), 'Invalid run ID');
  assert(Array.isArray(tree.children) && tree.children.length > 0, 'Empty dependency tree');
  const resolved = {};
  function visit(node, depth) {
    const qualifiers = new URLSearchParams();
    if (node.classifier) qualifiers.set('classifier', node.classifier);
    if (node.type && node.type !== 'jar') qualifiers.set('type', node.type);
    const suffix = qualifiers.size ? `?${qualifiers}` : '';
    const purl = `pkg:maven/${coordinate(node.groupId)}/${coordinate(node.artifactId)}@${coordinate(node.version)}${suffix}`;
    assert(['compile', 'runtime', 'provided', 'system', 'test'].includes(node.scope), 'Unknown Maven scope');
    const scope = node.scope === 'test' ? 'development' : 'runtime';
    const relationship = depth === 1 ? 'direct' : 'indirect';
    const entry = resolved[purl] ??= {package_url: purl, scope, relationship, dependencies: []};
    if (scope === 'runtime') entry.scope = scope;
    if (relationship === 'direct') entry.relationship = relationship;
    assert(node.children === undefined || Array.isArray(node.children), 'Invalid dependency children');
    for (const child of node.children ?? []) {
      const dependency = visit(child, depth + 1);
      if (!entry.dependencies.includes(dependency)) entry.dependencies.push(dependency);
    }
    return purl;
  }
  for (const node of tree.children) visit(node, 1);
  return {
    version: 0, sha: env.GITHUB_SHA, ref: env.GITHUB_REF,
    job: {correlator: 'build', id: env.GITHUB_RUN_ID,
      html_url: `https://github.com/${env.GITHUB_REPOSITORY}/actions/runs/${env.GITHUB_RUN_ID}`},
    detector, scanned,
    manifests: {'pom.xml': {name: 'pom.xml', file: {source_location: 'pom.xml'}, resolved}}
  };
}

export async function submitSnapshot(snapshot, env, request = fetch) {
  assert(env.GITHUB_EVENT_NAME === 'push' && env.GITHUB_REF === 'refs/heads/master',
    'Submission requires a master push');
  assert(env.GITHUB_REPOSITORY === 'lordlamer/jknowledgeroot', 'Unexpected repository');
  assert(env.GITHUB_TOKEN, 'Missing GitHub token');
  assert(snapshot.sha === env.GITHUB_SHA && /^[a-f0-9]{40}$/.test(snapshot.sha)
    && snapshot.ref === env.GITHUB_REF && snapshot.job.id === env.GITHUB_RUN_ID,
  'Snapshot does not belong to this run');
  assert(snapshot.job.correlator === 'build' && snapshot.detector.name === detector.name,
    'Unexpected snapshot source');
  assert(Object.keys(snapshot.manifests).length === 1
    && snapshot.manifests['pom.xml']?.file.source_location === 'pom.xml'
    && Object.keys(snapshot.manifests['pom.xml'].resolved).length > 0, 'Invalid manifest');
  const api = 'https://api.github.com/repos/lordlamer/jknowledgeroot';
  const headers = {Authorization: `Bearer ${env.GITHUB_TOKEN}`, Accept: 'application/vnd.github+json',
    'X-GitHub-Api-Version': '2026-03-10', 'User-Agent': 'Knowledgeroot-dependency-submission'};
  const options = {headers, redirect: 'error'};
  const head = await request(`${api}/git/ref/heads/master`, {...options, signal: AbortSignal.timeout(30_000)});
  assert(head.ok, `Cannot read master: HTTP ${head.status}`);
  if ((await head.json()).object.sha !== snapshot.sha) return {skipped: true, reason: 'Master has advanced'};
  const response = await request(`${api}/dependency-graph/snapshots`, {...options, method: 'POST',
    headers: {...headers, 'Content-Type': 'application/json'}, body: JSON.stringify(snapshot),
    signal: AbortSignal.timeout(30_000)});
  assert(response.status === 201, `Dependency submission failed: HTTP ${response.status}`);
  const result = await response.json();
  assert(result.result === 'SUCCESS' && result.id, 'Dependency submission was not accepted');
  return {skipped: false, id: result.id};
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [mode, input, output] = process.argv.slice(2);
  assert(['create', 'submit'].includes(mode) && input, 'Usage: dependency-snapshot.mjs create TREE OUTPUT | submit SNAPSHOT');
  const data = JSON.parse(readFileSync(input, 'utf8'));
  if (mode === 'create') {
    assert(output, 'Missing output path');
    const snapshot = createSnapshot(data, process.env);
    writeFileSync(output, JSON.stringify(snapshot, null, 2) + '\n');
    console.log(`Snapshot created: ${Object.keys(snapshot.manifests['pom.xml'].resolved).length} Maven packages`);
  } else {
    console.log(JSON.stringify(await submitSnapshot(data, process.env)));
  }
}
