import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {createReadStream, mkdirSync, mkdtempSync, readFileSync, writeFileSync, copyFileSync, rmSync} from 'node:fs';
import {devNull, tmpdir} from 'node:os';
import {basename, join, resolve} from 'node:path';
import {evaluateReport} from './image-audit-policy.mjs';

// Official immutable v0.74.0 release, pinned independently of mutable tags.
const scanner = 'ghcr.io/aquasecurity/trivy:0.74.0@sha256:62b1e65e8869bc4b4c6aa4fa2b21595256c7c2f6018a9d9ad61caf87187c1969';
const run = args => execFileSync('docker', args, {encoding:'utf8'}).trim();
const database = process.argv[2] === '--database';
assert.equal(process.argv.length, 3, 'Pass a local application image or --database');
const reportPrefix = database ? 'database-image-audit' : 'image-audit';
// A failed resolution or pull must also invalidate previous success evidence.
mkdirSync('target', {recursive:true});
for (const suffix of ['.json', '-summary.json']) rmSync(join('target', reportPrefix + suffix), {force:true});
let image = process.argv[2];
if (database) {
  // Resolve the exact production default without reading an operator env file.
  const config = JSON.parse(execFileSync('docker', ['compose', '--env-file', devNull,
    '-f', 'deploy/compose.production.yaml', 'config', '--format', 'json'], {
    encoding:'utf8', env:{...process.env, KR_DB_IMAGE:'', KR_APP_IMAGE:'knowledgeroot:audit-placeholder',
      KR_DB_ROOT_PASSWORD:'audit-placeholder', KR_DB_PASSWORD:'audit-placeholder',
      KR_MIGRATION_PASSWORD:'audit-placeholder', KR_BOOTSTRAP_LOGIN:'', KR_BOOTSTRAP_PASSWORD:'',
      KR_APP_PORT:'8081', KR_FORWARD_HEADERS_STRATEGY:'none', KR_TRUSTED_PROXY_PATTERN:'(?!)'}
  }));
  image = config.services.database.image;
  assert.match(image, /^mariadb:[0-9.]+@sha256:[a-f0-9]{64}$/, 'Pin the production database image digest');
  execFileSync('docker', ['pull', image], {stdio:'inherit'});
}
assert(image && !image.startsWith('-'), 'Pass a locally built image');
const inspected = JSON.parse(run(['image', 'inspect', image]))[0];
assert.match(inspected.Id, /^sha256:[a-f0-9]{64}$/);
const work = mkdtempSync(join(tmpdir(), 'knowledgeroot-image-audit-'));
const containerName = basename(work).toLowerCase();
const input = join(work, 'input'), output = join(work, 'output');
mkdirSync(input); mkdirSync(output); mkdirSync('target', {recursive:true});
try {
  const archive = join(input, 'image.tar');
  execFileSync('docker', ['save', '--output', archive, inspected.Id], {stdio:'inherit'});
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(archive)) hash.update(chunk);
  const archiveSha256 = hash.digest('hex');
  const container = ['run', '--name', containerName, '--rm', '--read-only', '--cap-drop=ALL', '--security-opt=no-new-privileges',
    '--user', `${process.getuid?.() ?? 1000}:${process.getgid?.() ?? 1000}`,
    '--tmpfs', '/tmp:rw,nosuid,nodev,size=512m',
    '--mount', `type=bind,source=${resolve(input)},target=/input,readonly`,
    '--mount', `type=bind,source=${resolve(output)},target=/output`, scanner];
  // No Docker socket, repository, operator environment or credentials enter the scanner.
  execFileSync('docker', [...container, '--cache-dir', '/output/cache', 'image',
    '--scanners', 'vuln', '--pkg-types', 'os', '--list-all-pkgs', '--ignorefile', '/dev/null',
    '--timeout', '10m', '--no-progress', '--format', 'json', '--output', '/output/report.json',
    '--exit-code', '0', '--input', '/input/image.tar'], {stdio:'inherit', timeout:720_000});
  const reportFile = join(output, 'report.json');
  copyFileSync(reportFile, join('target', reportPrefix + '.json'));
  const report = JSON.parse(readFileSync(reportFile, 'utf8'));
  const assessment = evaluateReport(report);
  const dbMetadata = JSON.parse(readFileSync(join(output, 'cache', 'db', 'metadata.json'), 'utf8'));
  const summary = {scannedAt:new Date().toISOString(), scanner, image, imageId:inspected.Id,
    imageLabels:inspected.Config.Labels, archiveSha256, database:dbMetadata, ...assessment};
  writeFileSync(join('target', reportPrefix + '-summary.json'), JSON.stringify(summary, null, 2) + '\n');
  console.log(`Container OS audit: ${summary.packages} packages; ${summary.blockers.length} blocking findings; ${summary.passed ? 'PASS' : 'FAIL'}.`);
  for (const finding of summary.blockers) console.error(`${finding.severity}: ${finding.id} (${finding.package})`);
  if (!summary.passed) process.exitCode = 1;
} finally {
  // A killed Docker client does not necessarily stop its container.
  try { execFileSync('docker', ['rm', '--force', containerName], {stdio:'ignore', timeout:30_000}); } catch {}
  // Only the directory returned by mkdtemp above is removed, never caller-supplied paths.
  rmSync(work, {recursive:true, force:true});
}
