import test from 'node:test';
import assert from 'node:assert/strict';
import {evaluateReport} from './image-audit-policy.mjs';

const report = vulnerabilities => ({SchemaVersion:2, Metadata:{OS:{Family:'ubuntu', Name:'24.04', EOSL:false}},
  Results:[{Class:'os-pkgs', Packages:[{Name:'libc6'}], Vulnerabilities:vulnerabilities}]});

test('complete clean inventory passes; medium findings remain counted', () => {
  assert.equal(evaluateReport(report([])).passed, true);
  const result = evaluateReport(report([{VulnerabilityID:'CVE-test', Severity:'MEDIUM'}]));
  assert.equal(result.passed, true);
  assert.equal(result.findings, 1);
});
test('high and critical vulnerabilities block even without a vendor fix', () => {
  for (const severity of ['HIGH', 'CRITICAL']) {
    const result = evaluateReport(report([{VulnerabilityID:'CVE-test', Severity:severity, PkgName:'libc6'}]));
    assert.equal(result.passed, false);
    assert.equal(result.blockers[0].fixed, null);
  }
});
test('unknown severity and end-of-support OS cannot silently pass', () => {
  for (const severity of ['UNKNOWN', undefined]) assert.equal(evaluateReport(report([{Severity:severity}])).passed, false);
  const input = report([]); input.Metadata.OS.EOSL = true;
  assert.equal(evaluateReport(input).passed, false);
});
test('empty, incompatible or missing inventories fail closed', () => {
  for (const input of [{}, {...report([]), SchemaVersion:99}, {...report([]), Results:[]},
    {...report([]), Metadata:{}}, {...report([]), Results:[{Class:'os-pkgs', Packages:[]}]}]) {
    assert.throws(() => evaluateReport(input));
  }
});
