import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {createReadStream, mkdirSync, mkdtempSync, readFileSync, writeFileSync, copyFileSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {basename, join, resolve} from 'node:path';
import {evaluateReport} from './image-audit-policy.mjs';

// Official immutable v0.74.0 release, pinned independently of mutable tags.
const scanner = 'ghcr.io/aquasecurity/trivy:0.74.0@sha256:62b1e65e8869bc4b4c6aa4fa2b21595256c7c2f6018a9d9ad61caf87187c1969';
const image = process.argv[2];
assert(image && !image.startsWith('-'), 'Pass a locally built image');
const run = args => execFileSync('docker', args, {encoding:'utf8'}).trim();
const inspected = JSON.parse(run(['image', 'inspect', image]))[0];
assert.match(inspected.Id, /^sha256:[a-f0-9]{64}$/);
const work = mkdtempSync(join(tmpdir(), 'knowledgeroot-image-audit-'));
const containerName = basename(work).toLowerCase();
const input = join(work, 'input'), output = join(work, 'output');
mkdirSync(input); mkdirSync(output); mkdirSync('target', {recursive:true});
// Remove stale success evidence before starting a new scan, including download failures.
for (const name of ['image-audit.json', 'image-audit-summary.json']) rmSync(join('target', name), {force:true});
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
  copyFileSync(reportFile, 'target/image-audit.json');
  const report = JSON.parse(readFileSync(reportFile, 'utf8'));
  const assessment = evaluateReport(report);
  const dbMetadata = JSON.parse(readFileSync(join(output, 'cache', 'db', 'metadata.json'), 'utf8'));
  const summary = {scannedAt:new Date().toISOString(), scanner, image, imageId:inspected.Id,
    imageLabels:inspected.Config.Labels, archiveSha256, database:dbMetadata, ...assessment};
  writeFileSync('target/image-audit-summary.json', JSON.stringify(summary, null, 2) + '\n');
  console.log(`Container OS audit: ${summary.packages} packages; ${summary.blockers.length} blocking findings; ${summary.passed ? 'PASS' : 'FAIL'}.`);
  for (const finding of summary.blockers) console.error(`${finding.severity}: ${finding.id} (${finding.package})`);
  if (!summary.passed) process.exitCode = 1;
} finally {
  // A killed Docker client does not necessarily stop its container.
  try { execFileSync('docker', ['rm', '--force', containerName], {stdio:'ignore', timeout:30_000}); } catch {}
  // Only the directory returned by mkdtemp above is removed, never caller-supplied paths.
  rmSync(work, {recursive:true, force:true});
}
