import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {mkdtempSync, readFileSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join, resolve} from 'node:path';
import {evaluateReport, scanner} from './image-audit-policy.mjs';

export const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');

export function verifyBuildInfo(properties, version, revision) {
  for (const [key, expected] of Object.entries({version, revision})) {
    // Maven generates these two values without Java-properties escapes.
    const values = [...properties.matchAll(new RegExp(`^build\\.${key}=([^\\r\\n]*)`, 'gm'))];
    assert.equal(values.length, 1, `JAR build.${key} missing or ambiguous`);
    assert.equal(values[0][1], expected, `JAR build.${key} must match the current build`);
  }
  return {version, revision};
}

export function verifyJarBuild(jar, version, revision) {
  const work = mkdtempSync(join(tmpdir(), 'knowledgeroot-build-info-'));
  try {
    // Extract only the known build-info entry, never arbitrary archive paths.
    execFileSync('jar', ['xf', resolve(jar), 'META-INF/build-info.properties'], {cwd:work});
    return verifyBuildInfo(readFileSync(join(work, 'META-INF/build-info.properties'), 'utf8'), version, revision);
  } finally {
    rmSync(work, {recursive:true, force:true});
  }
}

export function verifyAuditEvidence(reportBytes, summaryBytes, inspected) {
  const report = JSON.parse(reportBytes.toString('utf8'));
  const summary = JSON.parse(summaryBytes.toString('utf8'));
  assert.equal(summary.imageId, inspected.Id, 'Audit belongs to a different image');
  assert.equal(summary.scanner, scanner, 'Unexpected audit scanner');
  assert.equal(summary.reportSha256, sha256(reportBytes), 'Audit report checksum mismatch');
  assert(Number.isFinite(Date.parse(summary.scannedAt)), 'Audit scan time missing');
  assert.match(summary.archiveSha256 ?? '', /^[a-f0-9]{64}$/, 'Audit archive checksum missing');
  assert.equal(summary.database?.Version, 2, 'Audit vulnerability database metadata missing');
  for (const key of ['UpdatedAt', 'DownloadedAt', 'NextUpdate']) {
    assert(Number.isFinite(Date.parse(summary.database[key])), `Audit database ${key} missing`);
  }
  // Trivy reports the config digest, while Docker Desktop may expose an OCI index
  // as image ID. Compare the actual layer inventory and labels as well as the ID
  // captured by our scanner wrapper; these work with both Docker image stores.
  assert(inspected.RootFS?.Layers?.length, 'Image layer inventory missing');
  assert.deepEqual(report.Metadata?.ImageConfig?.rootfs?.diff_ids, inspected.RootFS.Layers,
    'Audit layer inventory belongs to a different image');
  assert.deepEqual(report.Metadata?.ImageConfig?.config?.Labels, inspected.Config.Labels,
    'Audit report image labels mismatch');
  assert.deepEqual(summary.imageLabels, inspected.Config.Labels, 'Audit summary image labels mismatch');
  const assessment = evaluateReport(report);
  assert(assessment.passed, 'Audit report contains blocking findings');
  for (const key of Object.keys(assessment)) {
    assert.deepEqual(summary[key], assessment[key], `Audit summary ${key} disagrees with the report`);
  }
  return {imageId:inspected.Id, scanner, scannedAt:summary.scannedAt,
    reportSha256:sha256(reportBytes), summarySha256:sha256(summaryBytes),
    archiveSha256:summary.archiveSha256, packages:assessment.packages, findings:assessment.findings};
}
