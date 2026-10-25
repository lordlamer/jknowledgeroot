import {copyFileSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync} from 'node:fs';
import {basename, join} from 'node:path';
import {pathToFileURL} from 'node:url';
import assert from 'node:assert/strict';
import {sha256} from './release-evidence.mjs';

export const ASSET_DIR = 'target/release-assets';
export const EVIDENCE = ['release-manifest.json', 'dependency-audit.json', 'image-audit-summary.json', 'database-image-audit-summary.json'];
const TAG = /^v(\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?)$/;
const DIGEST = /^sha256:[0-9a-f]{64}$/;

export function isPrerelease(tag) {
  const version = TAG.exec(tag)?.[1];
  assert(version, `Unsupported release tag: ${tag}`);
  return version.includes('-');
}

export function validateManifest(manifest, releaseTag) {
  assert(typeof manifest?.version === 'string' && /^[0-9a-f]{40}$/.test(manifest.revision), 'Malformed release manifest');
  assert(manifest.dirty === false && manifest.auditsVerified === true, 'Release assets require a clean build with verified audits');
  assert(/^[0-9a-f]{64}$/.test(manifest.jarSha256) && manifest.jar === `target/knowledgeroot-${manifest.version}.jar`, 'Malformed JAR evidence');
  if (releaseTag) assert.equal(releaseTag, `v${manifest.version}`, 'Release tag must match the Maven artifact version');
  return manifest;
}

// Same layout as `sha256sum`, so `sha256sum -c SHA256SUMS` verifies a download.
export const formatChecksums = entries => entries.map(([name, digest]) => `${digest}  ${name}\n`).join('');

export function assembleAssets({target = 'target', assetDir = ASSET_DIR, releaseTag = process.env.RELEASE_TAG || ''} = {}) {
  const manifest = validateManifest(JSON.parse(readFileSync(join(target, 'release-manifest.json'), 'utf8')), releaseTag);
  const jar = join(target, basename(manifest.jar));
  assert(existsSync(jar) && sha256(readFileSync(jar)) === manifest.jarSha256, 'JAR must match the verified manifest');
  rmSync(assetDir, {recursive: true, force: true});
  mkdirSync(assetDir, {recursive: true});
  const checksums = [];
  for (const file of [jar, ...EVIDENCE.map(name => join(target, name))]) {
    assert(existsSync(file), `Missing release evidence: ${file}`);
    const bytes = readFileSync(file);
    copyFileSync(file, join(assetDir, basename(file)));
    checksums.push([basename(file), sha256(bytes)]);
  }
  writeFileSync(join(assetDir, 'SHA256SUMS'), formatChecksums(checksums));
  return {version: manifest.version, files: [...checksums.map(([name]) => name), 'SHA256SUMS']};
}

export function renderReleaseNotes({tag, manifest, appDigest, databaseDigest}) {
  validateManifest(manifest, tag);
  assert(DIGEST.test(appDigest) && DIGEST.test(databaseDigest) && appDigest !== databaseDigest, 'Registry digests must be distinct sha256 values');
  const mariadb = /\b\d+\.\d+\.\d+-MariaDB\b/.exec(manifest.database?.serverVersion ?? '')?.[0];
  assert(mariadb && manifest.javaRuntimeVersion, 'Manifest runtime details missing');
  return ['## Artifacts', '',
    `- JAR: \`${basename(manifest.jar)}\` (SHA-256 \`${manifest.jarSha256}\`); needs Java 25 and MariaDB, see docs/installation.md.`,
    `- Application image: \`lordlamer/knowledgeroot:${tag}\` = \`lordlamer/knowledgeroot@${appDigest}\``,
    `- Database image: \`lordlamer/knowledgeroot:database-${tag}\` = \`lordlamer/knowledgeroot@${databaseDigest}\``,
    `- Git revision \`${manifest.revision}\`, Java runtime ${manifest.javaRuntimeVersion}, ${mariadb}`,
    '', 'Attached: release manifest, dependency audit and both OS scan summaries from the verifying CI run; `SHA256SUMS` covers every asset.',
    ...(isPrerelease(tag) ? ['', 'This is a release candidate, not a production release.'] : []), ''].join('\n');
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [mode, ...args] = process.argv.slice(2);
  if (mode === 'assemble' && args.length === 0) {
    const result = assembleAssets();
    console.log(`Release assets for ${result.version}: ${result.files.join(', ')}`);
  } else if (mode === 'notes' && args.length === 4) {
    const [assetDir, tag, appDigest, databaseDigest] = args;
    const manifest = JSON.parse(readFileSync(join(assetDir, 'release-manifest.json'), 'utf8'));
    process.stdout.write(renderReleaseNotes({tag, manifest, appDigest, databaseDigest}));
  } else {
    console.error('Usage: release-assets.mjs assemble | notes <asset-dir> <tag> <app-digest> <database-digest>');
    process.exit(2);
  }
}
