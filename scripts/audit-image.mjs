import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {createReadStream, mkdirSync, mkdtempSync, readFileSync, writeFileSync, copyFileSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {basename, join, resolve} from 'node:path';
import {evaluateReport, scanner} from './image-audit-policy.mjs';

const run = args => execFileSync('docker', args, {encoding:'utf8'}).trim();
const database = process.argv[2] === '--database';
assert.equal(process.argv.length, database ? 4 : 3, 'Pass a local application image or --database IMAGE');
const reportPrefix = database ? 'database-image-audit' : 'image-audit';
// A failed image lookup must also invalidate previous success evidence.
mkdirSync('target', {recursive:true});
for (const suffix of ['.json', '-summary.json']) rmSync(join('target', reportPrefix + suffix), {force:true});
const image = process.argv[database ? 3 : 2];
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
  const reportBytes = readFileSync(reportFile);
  const reportSha256 = createHash('sha256').update(reportBytes).digest('hex');
  const report = JSON.parse(reportBytes.toString('utf8'));
  const assessment = evaluateReport(report);
  const dbMetadata = JSON.parse(readFileSync(join(output, 'cache', 'db', 'metadata.json'), 'utf8'));
  const summary = {scannedAt:new Date().toISOString(), scanner, image, imageId:inspected.Id,
    imageLabels:inspected.Config.Labels, archiveSha256, reportSha256, database:dbMetadata, ...assessment};
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
