import assert from 'node:assert/strict';

// Official immutable v0.74.0 release, pinned independently of mutable tags.
export const scanner = 'ghcr.io/aquasecurity/trivy:0.74.0@sha256:62b1e65e8869bc4b4c6aa4fa2b21595256c7c2f6018a9d9ad61caf87187c1969';

export function evaluateReport(report) {
  assert.equal(report.SchemaVersion, 2, 'Unsupported Trivy report schema');
  assert(report.Metadata?.OS?.Family && report.Metadata.OS.Name, 'No operating system detected');
  const results = report.Results?.filter(result => result.Class === 'os-pkgs');
  assert(results?.length, 'No OS package scan result');
  const packages = results.reduce((sum, result) => sum + (result.Packages?.length ?? 0), 0);
  assert(packages > 0, 'OS package inventory missing');
  const findings = results.flatMap(result => result.Vulnerabilities ?? []);
  const blockers = findings.filter(item => !['LOW', 'MEDIUM'].includes(item.Severity)).map(item => ({
      id:item.VulnerabilityID, package:item.PkgName, severity:item.Severity ?? 'UNKNOWN',
      installed:item.InstalledVersion, fixed:item.FixedVersion ?? null
    }));
  if (report.Metadata.OS.EOSL === true) blockers.push({id:'OS-END-OF-SUPPORT', package:report.Metadata.OS.Name, severity:'HIGH'});
  return {os:report.Metadata.OS, packages, findings:findings.length, blockers, passed:blockers.length === 0};
}
