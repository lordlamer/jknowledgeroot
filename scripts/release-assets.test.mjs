import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {sha256} from './release-evidence.mjs';
import {EVIDENCE, assembleAssets, formatChecksums, isPrerelease, renderReleaseNotes, validateManifest} from './release-assets.mjs';

const version = '1.0.0-rc.3', tag = `v${version}`, revision = 'a'.repeat(40);
const jarBytes = Buffer.from('not really a jar');
const appDigest = `sha256:${'1'.repeat(64)}`, databaseDigest = `sha256:${'2'.repeat(64)}`;
const manifest = () => ({version, revision, dirty: false, auditsVerified: true, jar: `target/knowledgeroot-${version}.jar`,
  jarSha256: sha256(jarBytes), javaRuntimeVersion: '25.0.4.1+1-LTS',
  database: {serverVersion: 'mariadbd  Ver 12.3.3-MariaDB-ubu2404 for Linux on x86_64 (mariadb.org binary distribution)'}});

function fixture(overrides = {}) {
  const target = mkdtempSync(join(tmpdir(), 'knowledgeroot-release-assets-'));
  mkdirSync(join(target, 'release-assets'));
  writeFileSync(join(target, `knowledgeroot-${version}.jar`), jarBytes);
  writeFileSync(join(target, 'release-manifest.json'), JSON.stringify({...manifest(), ...overrides}));
  for (const name of EVIDENCE.slice(1)) writeFileSync(join(target, name), `{"evidence":"${name}"}`);
  return target;
}

test('assembles the verified JAR, evidence and sha256sum-compatible checksums', () => {
  const target = fixture();
  try {
    const assetDir = join(target, 'release-assets');
    const result = assembleAssets({target, assetDir, releaseTag: tag});
    assert.equal(result.version, version);
    assert.deepEqual(readdirSync(assetDir).sort(), [...result.files].sort());
    const sums = readFileSync(join(assetDir, 'SHA256SUMS'), 'utf8');
    assert.match(sums, new RegExp(`^${sha256(jarBytes)}  knowledgeroot-${version}\\.jar$`, 'm'));
    for (const name of EVIDENCE) {
      assert.match(sums, new RegExp(`^[0-9a-f]{64}  ${name.replace('.', '\\.')}$`, 'm'));
      assert.equal(readFileSync(join(assetDir, name), 'utf8'), readFileSync(join(target, name), 'utf8'));
    }
    assert.equal(sums.split('\n').filter(Boolean).length, EVIDENCE.length + 1);
    assert.equal(formatChecksums([['a', 'x'.repeat(64)]]), `${'x'.repeat(64)}  a\n`);
    assert.equal(assembleAssets({target, assetDir}).version, version, 'untagged builds assemble without a tag check');
  } finally {
    rmSync(target, {recursive: true, force: true});
  }
});

test('dirty builds, unverified audits, tag mismatches and altered JARs are rejected', () => {
  for (const [overrides, releaseTag] of [[{dirty: true}, tag], [{auditsVerified: false}, tag], [{}, 'v1.0.0-rc.2'],
    [{jarSha256: 'f'.repeat(64)}, tag], [{jar: 'target/other.jar'}, tag], [{revision: 'short'}, tag]]) {
    const target = fixture(overrides);
    try {
      assert.throws(() => assembleAssets({target, assetDir: join(target, 'release-assets'), releaseTag}));
    } finally {
      rmSync(target, {recursive: true, force: true});
    }
  }
  const target = fixture();
  try {
    rmSync(join(target, 'dependency-audit.json'));
    assert.throws(() => assembleAssets({target, assetDir: join(target, 'release-assets'), releaseTag: tag}), /Missing release evidence/);
  } finally {
    rmSync(target, {recursive: true, force: true});
  }
  assert.equal(validateManifest(manifest(), '').version, version);
});

test('pre-release detection follows the SemVer tag', () => {
  assert.equal(isPrerelease('v1.0.0-rc.3'), true);
  assert.equal(isPrerelease('v1.0.0-beta.1'), true);
  assert.equal(isPrerelease('v1.0.0'), false);
  assert.equal(isPrerelease('v2.10.3'), false);
  for (const value of ['1.0.0', 'v1.0', 'release-1', 'v1.0.0 ', '']) assert.throws(() => isPrerelease(value));
});

test('release notes name the JAR checksum, both registry digests and the candidate status', () => {
  const notes = renderReleaseNotes({tag, manifest: manifest(), appDigest, databaseDigest});
  assert.match(notes, new RegExp(`knowledgeroot-${version}\\.jar.*${sha256(jarBytes)}`));
  assert.match(notes, new RegExp(`lordlamer/knowledgeroot:${tag}\` = \`lordlamer/knowledgeroot@${appDigest}`));
  assert.match(notes, new RegExp(`database-${tag}\` = \`lordlamer/knowledgeroot@${databaseDigest}`));
  assert.match(notes, /25\.0\.4\.1\+1-LTS, 12\.3\.3-MariaDB/);
  assert.match(notes, /release candidate/);
  const final = renderReleaseNotes({tag: 'v1.0.0', manifest: {...manifest(), version: '1.0.0', jar: 'target/knowledgeroot-1.0.0.jar'}, appDigest, databaseDigest});
  assert.doesNotMatch(final, /release candidate/);
  for (const args of [{appDigest: 'sha256:short'}, {databaseDigest: appDigest}, {tag: 'v1.0.0-rc.2'},
    {manifest: {...manifest(), database: {serverVersion: 'unknown'}}}, {manifest: {...manifest(), dirty: true}}]) {
    assert.throws(() => renderReleaseNotes({tag, manifest: manifest(), appDigest, databaseDigest, ...args}));
  }
});
