import test from 'node:test';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {evaluateReport, scanner} from './image-audit-policy.mjs';
import {sha256, verifyBuildInfo, verifyAuditEvidence} from './release-evidence.mjs';

const version = '1.0.0-rc.2', revision = 'a'.repeat(40);
const properties = `build.version=${version}\r\nbuild.revision=${revision}\r\n`;
const bytes = value => Buffer.from(JSON.stringify(value));
const fixture = () => {
  const labels = {'org.opencontainers.image.version':version, 'org.opencontainers.image.revision':revision};
  const layers = [`sha256:${'b'.repeat(64)}`];
  const inspected = {Id:`sha256:${'c'.repeat(64)}`, Config:{Labels:labels}, RootFS:{Layers:layers}};
  const report = {SchemaVersion:2, Metadata:{OS:{Family:'ubuntu', Name:'24.04'},
    ImageConfig:{rootfs:{diff_ids:layers}, config:{Labels:labels}}},
    Results:[{Class:'os-pkgs', Packages:[{Name:'libc6'}], Vulnerabilities:[]}]};
  const summary = {imageId:inspected.Id, scanner, scannedAt:'2026-09-13T12:00:00Z',
    database:{Version:2, UpdatedAt:'2026-09-13T10:00:00Z', DownloadedAt:'2026-09-13T11:59:00Z', NextUpdate:'2026-09-14T10:00:00Z'},
    archiveSha256:'d'.repeat(64), reportSha256:sha256(bytes(report)), imageLabels:labels, ...evaluateReport(report)};
  return {inspected, report, summary};
};
const verify = ({report, summary, inspected}) => verifyAuditEvidence(bytes(report), bytes(summary), inspected);

test('embedded Maven identity must match both version and current commit', () => {
  assert.deepEqual(verifyBuildInfo(properties, version, revision), {version, revision});
  for (const input of [properties.replace(revision, 'unknown'), properties.replace(revision, 'e'.repeat(40)),
    properties.replace(version, '1.0.0-rc.1'), '', `build.version=${version}\n`,
    properties + `build.revision=${revision}\n`]) {
    assert.throws(() => verifyBuildInfo(input, version, revision), /JAR build\./);
  }
});

test('matching evidence records the hashes of both complete report files', () => {
  const input = fixture(), result = verify(input);
  assert.equal(result.imageId, input.inspected.Id);
  assert.equal(result.reportSha256, sha256(bytes(input.report)));
  assert.equal(result.summarySha256, sha256(bytes(input.summary)));
  assert.equal(result.packages, 1);
});

test('swapped image summary and changed raw report are rejected', () => {
  const input = fixture();
  input.summary.imageId = `sha256:${'e'.repeat(64)}`;
  assert.throws(() => verify(input), /different image/);
  input.summary.imageId = input.inspected.Id;
  input.report.Results[0].Packages.push({Name:'unexpected-package'});
  assert.throws(() => verify(input), /checksum mismatch/);
});

test('a report from different image layers cannot pass by updating its summary hash', () => {
  const input = fixture();
  input.report.Metadata.ImageConfig.rootfs.diff_ids = [`sha256:${'f'.repeat(64)}`];
  input.summary.reportSha256 = sha256(bytes(input.report));
  assert.throws(() => verify(input), /layer inventory/);
});

test('old revision labels in either report or summary are rejected', () => {
  for (const target of ['report', 'summary']) {
    const input = fixture();
    const labels = {...input.inspected.Config.Labels, 'org.opencontainers.image.revision':'e'.repeat(40)};
    if (target === 'report') input.report.Metadata.ImageConfig.config.Labels = labels;
    else input.summary.imageLabels = labels;
    input.summary.reportSha256 = sha256(bytes(input.report));
    assert.throws(() => verify(input), /labels mismatch/);
  }
});

test('claimed success cannot override a blocking raw finding', () => {
  const input = fixture();
  input.report.Results[0].Vulnerabilities.push({VulnerabilityID:'CVE-test', Severity:'HIGH', PkgName:'libc6'});
  input.summary.reportSha256 = sha256(bytes(input.report));
  assert.throws(() => verify(input), /blocking findings/);
});

test('incomplete and contradictory evidence fails closed', () => {
  for (const patch of [{scanner:'unpinned:latest'}, {reportSha256:undefined}, {scannedAt:undefined},
    {archiveSha256:undefined}, {database:undefined}, {database:{Version:2}},
    {passed:false}, {packages:0}, {findings:1}, {blockers:[{}]}]) {
    const input = fixture(); Object.assign(input.summary, patch);
    assert.throws(() => verify(input));
  }
  for (const patch of [{Results:[]}, {Metadata:{}}]) {
    const input = fixture(); Object.assign(input.report, patch);
    input.summary.reportSha256 = sha256(bytes(input.report));
    assert.throws(() => verify(input));
  }
});

test('failed metadata invocation removes a previous success manifest', () => {
  const work = mkdtempSync(join(tmpdir(), 'knowledgeroot-evidence-test-'));
  try {
    mkdirSync(join(work, 'target'));
    const manifest = join(work, 'target/release-manifest.json');
    for (const pom of [null, '<invalid/>', `<artifactId>knowledgeroot</artifactId><version>${version}</version>`]) {
      if (pom !== null) writeFileSync(join(work, 'pom.xml'), pom);
      writeFileSync(manifest, '{"auditsVerified":true}');
      assert.throws(() => execFileSync(process.execPath,
        [fileURLToPath(new URL('./release-metadata.mjs', import.meta.url)), 'app', 'db', '--typo'],
        {cwd:work, stdio:'pipe'}));
      assert.equal(existsSync(manifest), false);
    }
    writeFileSync(manifest, '{"auditsVerified":true}');
    assert.throws(() => execFileSync(process.execPath,
      [fileURLToPath(new URL('./build-container.mjs', import.meta.url)), 'same', 'same'], {cwd:work, stdio:'pipe'}));
    assert.equal(existsSync(manifest), false);
  } finally {
    rmSync(work, {recursive:true, force:true});
  }
});
