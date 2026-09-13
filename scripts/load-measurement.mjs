import assert from 'node:assert/strict';
import {performance} from 'node:perf_hooks';

export function settings(args) {
  const values = {pages:1000, branches:32, requests:160, concurrency:[1, 8], p95Ms:null};
  const seen = new Set();
  for (const arg of args) {
    const match = /^--(pages|branches|requests|concurrency|p95-ms)=([0-9,]+)$/.exec(arg);
    assert(match && !seen.has(match[1]), `Invalid or duplicate option: ${arg}`);
    seen.add(match[1]);
    const key = match[1] === 'p95-ms' ? 'p95Ms' : match[1];
    if (key === 'concurrency') values[key] = match[2].split(',').map(Number);
    else { assert(/^\d+$/.test(match[2])); values[key] = Number(match[2]); }
  }
  const bounded = (value, min, max) => Number.isInteger(value) && value >= min && value <= max;
  assert(bounded(values.pages, 128, 20000) && values.pages % 2 === 0, 'pages must be even, between 128 and 20000');
  assert(bounded(values.branches, 2, 64) && values.branches % 2 === 0, 'branches must be even, between 2 and 64');
  assert(bounded(values.requests, 80, 8000) && values.requests % 8 === 0, 'requests must be a multiple of 8, between 80 and 8000');
  assert(values.concurrency.length <= 4 && new Set(values.concurrency).size === values.concurrency.length
    && values.concurrency.every(value => bounded(value, 1, 16)), 'Use 1–4 distinct concurrency levels between 1 and 16');
  assert(values.p95Ms === null || bounded(values.p95Ms, 1, 15000), 'p95-ms must be between 1 and 15000');
  return values;
}

export function summarize(samples) {
  assert(samples.length > 0);
  const times = samples.map(sample => sample.ms).sort((a, b) => a - b);
  const percentile = fraction => Math.round(times[Math.ceil(times.length * fraction) - 1] * 100) / 100;
  const errors = samples.filter(sample => !sample.ok).length;
  const statuses = {};
  for (const sample of samples) statuses[sample.status ?? 'network-error'] = (statuses[sample.status ?? 'network-error'] ?? 0) + 1;
  return {requests:samples.length, errors, errorRate:errors / samples.length,
    p50Ms:percentile(0.5), p95Ms:percentile(0.95), p99Ms:percentile(0.99), maxMs:percentile(1), statuses};
}

// Fixed number of closed-loop workers; consume the entire body before measuring latency.
export async function measure({count, concurrency, scenarios, clients}) {
  let next = 0;
  const samples = [];
  const started = performance.now();
  await Promise.all(Array.from({length:concurrency}, async (_, worker) => {
    while (true) {
      const index = next++;
      if (index >= count) return;
      const scenario = scenarios[index % scenarios.length];
      const before = performance.now();
      let status = null, ok = false, failure = null;
      try {
        const response = await clients[worker][scenario.role](scenario.path);
        status = response.status;
        const body = await response.text();
        if (status !== scenario.status) failure = 'unexpected-status';
        else if (scenario.required && !body.includes(scenario.required)) failure = 'expected-content-missing';
        else if (scenario.forbidden && body.includes(scenario.forbidden)) failure = 'private-content-exposed';
        else ok = true;
      } catch { failure = 'request-failed'; }
      samples.push({scenario:scenario.name, ms:performance.now() - before, status, ok, failure});
    }
  }));
  const seconds = (performance.now() - started) / 1000;
  return {seconds, requestsPerSecond:count / seconds, ...summarize(samples),
    scenarios:scenarios.map(scenario => ({name:scenario.name,
      ...summarize(samples.filter(sample => sample.scenario === scenario.name))})),
    failures:samples.filter(sample => !sample.ok).map(({scenario, status, failure}) => ({scenario, status, failure}))};
}
