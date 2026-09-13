import assert from 'node:assert/strict';
import test from 'node:test';
import http from 'node:http';
import {settings, summarize, measure} from './load-measurement.mjs';

test('rejects unbounded and ambiguous workloads', () => {
  for (const args of [['--pages=999999'], ['--pages=999'], ['--requests=10'], ['--concurrency=0'], ['--concurrency=1,1'],
    ['--concurrency=17'], ['--branches=3'], ['--url=http://localhost'], ['--pages=1000','--pages=2000'], ['--requests=80,80']]) {
    assert.throws(() => settings(args));
  }
  assert.equal(settings(['--p95-ms=1']).p95Ms, 1);
});

test('percentiles include failures and expected denial is not an error', () => {
  const result = summarize(Array.from({length:100}, (_, index) => ({ms:index + 1, status:403, ok:index !== 99})));
  assert.equal(result.p50Ms, 50);
  assert.equal(result.p95Ms, 95);
  assert.equal(result.p99Ms, 99);
  assert.equal(result.errors, 1);
  assert.equal(result.statuses[403], 100);
});

test('real HTTP workload bounds concurrency, reads full bodies and rejects false successes', async () => {
  let active = 0, peak = 0;
  const server = http.createServer((req, res) => {
    active++; peak = Math.max(peak, active);
    res.writeHead(req.url === '/denied' ? 403 : 200);
    res.write('prefix ');
    setTimeout(() => {
      active--;
      res.end(req.url === '/leak' ? 'PRIVATE' : req.url === '/missing' ? 'wrong' : 'PUBLIC');
    }, 30);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    const request = path => fetch(`http://127.0.0.1:${server.address().port}${path}`);
    const result = await measure({count:12, concurrency:3, clients:Array.from({length:3}, () => ({guest:request})),
      scenarios:[{name:'ok', role:'guest', path:'/', status:200, required:'PUBLIC'},
        {name:'denied', role:'guest', path:'/denied', status:403},
        {name:'leak', role:'guest', path:'/leak', status:200, forbidden:'PRIVATE'},
        {name:'missing', role:'guest', path:'/missing', status:200, required:'PUBLIC'}]});
    assert.equal(result.requests, 12);
    assert.equal(result.errors, 6);
    assert(peak <= 3 && peak >= 2);
    assert(result.p50Ms >= 20, 'Latency must include the delayed response body');
    assert.equal(result.scenarios.find(row => row.name === 'denied').errors, 0);
    assert.deepEqual(new Set(result.failures.map(row => row.failure)), new Set(['private-content-exposed', 'expected-content-missing']));
  } finally {
    server.closeAllConnections();
    await new Promise(resolve => server.close(resolve));
  }
});

test('transport failures are reported without response bodies or exception details', async () => {
  const result = await measure({count:1, concurrency:1, clients:[{guest:async () => { throw new Error('sensitive'); }}],
    scenarios:[{name:'unreachable', role:'guest', path:'/', status:200}]});
  assert.equal(result.errors, 1);
  assert.equal(result.statuses['network-error'], 1);
  assert(!JSON.stringify(result).includes('sensitive'));
});
