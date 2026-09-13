// Synthetic, read-only HTTP workload on a new disposable Compose installation.
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {randomBytes} from 'node:crypto';
import {mkdirSync, mkdtempSync, rmSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {settings, measure} from '../scripts/load-measurement.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const evidence = join(root, 'target/load-smoke.json');
mkdirSync(join(root, 'target'), {recursive:true});
rmSync(evidence, {force:true});
const [appImage, databaseImage, ...args] = process.argv.slice(2);
assert(appImage && databaseImage && !appImage.startsWith('-') && !databaseImage.startsWith('-'),
  'Usage: node deploy/load-smoke-test.mjs APP_IMAGE DATABASE_IMAGE [--pages=1000 --branches=32 --requests=160 --concurrency=1,8 --p95-ms=2000]');
const options = settings(args);
const env = Object.fromEntries(Object.entries(process.env).filter(([key]) => !key.startsWith('KR_')));
const run = (command, args, extra = {}) => execFileSync(command, args,
  {cwd:root, env, encoding:'utf8', timeout:180000, maxBuffer:16 * 1024 * 1024, ...extra}).trim();
const inspectImage = name => JSON.parse(run('docker', ['image', 'inspect', name]))[0];
const app = inspectImage(appImage), database = inspectImage(databaseImage);
assert.notEqual(app.Id, database.Id);
const project = `knowledgeroot-load-${randomBytes(8).toString('hex')}`;
const work = mkdtempSync(join(tmpdir(), project + '-'));
const envfile = join(work, 'test.env');
const compose = (args, extra) => run('docker', ['compose', '--project-name', project, '--env-file', envfile,
  '-f', join(root, 'deploy/compose.production.yaml'), ...args], extra);
const sql = input => compose(['exec', '-T', 'database', 'sh', '-c',
  'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" mariadb --protocol=socket --user=root --batch --skip-column-names knowledgeroot'], {input});
let created = false;
function cleanup() {
  if (created) { compose(['down', '--volumes', '--remove-orphans']); created = false; }
  rmSync(work, {recursive:true, force:true});
}
process.once('SIGINT', () => process.exit(130));
process.once('SIGTERM', () => process.exit(143));
process.once('exit', () => { if (created) { try { cleanup(); } catch { console.error(`Cleanup required for ${project}`); } } });
const password = randomBytes(32).toString('hex');
const report = {passed:false, checkedAt:new Date().toISOString(), options, phases:[],
  revision:run('git', ['rev-parse', 'HEAD']), dirty:run('git', ['status', '--porcelain', '--untracked-files=normal']) !== '',
  application:{imageId:app.Id, revision:app.Config.Labels?.['org.opencontainers.image.revision']},
  database:{imageId:database.Id, revision:database.Config.Labels?.['org.opencontainers.image.revision']},
  limitations:['Synthetic HTTP reads; no browser rendering, writes, uploads, TLS or sustained arrival-rate load.',
    'Latency includes reading the response body. Resource samples are snapshots, not measured peaks.']};
const save = () => writeFileSync(evidence, JSON.stringify(report, null, 2) + '\n');
let base;
function client() {
  const cookies = new Map();
  return async (path, options = {}) => {
    const response = await fetch(base + path, {redirect:'manual', ...options,
      headers:{'HX-Request':'true', cookie:[...cookies].map(([key, value]) => `${key}=${value}`).join('; '), ...options.headers},
      signal:AbortSignal.timeout(15000)});
    for (const cookie of response.headers.getSetCookie()) {
      const pair = cookie.split(';')[0], split = pair.indexOf('=');
      cookies.set(pair.slice(0, split), pair.slice(split + 1));
    }
    return response;
  };
}
try {
  writeFileSync(envfile, Object.entries({KR_APP_IMAGE:app.Id, KR_DB_IMAGE:database.Id,
    KR_DB_ROOT_PASSWORD:randomBytes(32).toString('hex'), KR_DB_PASSWORD:randomBytes(32).toString('hex'),
    KR_MIGRATION_PASSWORD:randomBytes(32).toString('hex'), KR_BOOTSTRAP_LOGIN:'load.admin',
    KR_BOOTSTRAP_PASSWORD:password, KR_APP_PORT:'0'}).map(([key,value]) => `${key}=${value}\n`).join(''), {mode:0o600});
  created = true;
  compose(['up', '--detach', '--wait', '--wait-timeout', '120']);
  compose(['stop', 'app']);
  const maxWorkers = Math.max(...options.concurrency);
  sql(`INSERT INTO \`group\` (id,name,active,created_by,create_date,changed_by,change_date)
    SELECT 1001,'Load readers',TRUE,id,NOW(),id,NOW() FROM user WHERE login='load.admin';\n`);
  for (let worker = 0; worker < maxWorkers; worker++) {
    sql(`INSERT INTO user (id,login,password,active,created_by,create_date,changed_by,change_date)
      SELECT ${1001 + worker},'load.reader.${worker}',password,TRUE,id,NOW(),id,NOW() FROM user WHERE login='load.admin';
      INSERT INTO group_member (group_id,member_id,member_type) VALUES (1001,${1001 + worker},'user');\n`);
  }
  for (let start = 0; start < options.pages; start += 250) {
    const rows = [];
    for (let index = start; index < Math.min(options.pages, start + 250); index++) {
      const branch = index % options.branches, isRoot = index < options.branches;
      const marker = branch % 2 ? 'LOAD_PRIVATE' : 'LOAD_PUBLIC';
      rows.push(`(${1001 + index},${isRoot ? 0 : 1001 + branch},'loadfixture ${marker} ${index}',
        CONCAT('<p>loadfixture ${marker} ${index} ',REPEAT('synthetic content ',64),'</p>'),TRUE,${!isRoot},1001,NOW(),1001,NOW())`);
    }
    sql(`INSERT INTO page (id,parent,name,content,active,inherit_permissions,created_by,create_date,changed_by,change_date)
      VALUES ${rows.join(',')};\n`);
  }
  for (let branch = 0; branch < options.branches; branch++) {
    const grant = branch % 2 ? "'group',1001" : "'guest',NULL";
    sql(`INSERT INTO page_permission (page_id,role_type,role_id,permission_level,created_by,create_date,changed_by,change_date)
      VALUES (${1001 + branch},${grant},'view',1001,NOW(),1001,NOW());\n`);
  }
  assert.equal(Number(sql('SELECT COUNT(*) FROM page;\n')), options.pages);
  compose(['up', '--detach', '--wait', '--wait-timeout', '120']);
  const address = compose(['port', 'app', '8081']);
  assert.match(address, /^127\.0\.0\.1:\d+$/);
  base = `http://${address}`;
  const clients = [];
  for (let worker = 0; worker < maxWorkers; worker++) {
    const guest = client(), reader = client();
    const loginPage = await reader('/login');
    assert.equal(loginPage.status, 200);
    const csrf = (await loginPage.text()).match(/name="_csrf"[^>]*value="([^"]+)"/)?.[1];
    assert(csrf, 'Login CSRF token missing');
    const login = await reader('/logmein', {method:'POST', body:new URLSearchParams({
      username:`load.reader.${worker}`, password, _csrf:csrf})});
    assert.equal(login.status, 302);
    assert.match(login.headers.get('location'), /\/login\/success$/);
    await login.text();
    clients.push({guest, reader});
  }
  const publicPage = 1001 + options.branches, privatePage = publicPage + 1;
  const guest = {role:'guest', status:200, required:'LOAD_PUBLIC', forbidden:'LOAD_PRIVATE'};
  const scenarios = [
    {...guest, name:'guest-search', path:'/search?q=loadfixture'},
    {...guest, name:'guest-search-offset', path:'/search?q=loadfixture&start=20'},
    {...guest, required:'0 results', name:'guest-search-empty', path:'/search?q=absentfixture'},
    {...guest, name:'guest-page', path:`/ui/page/${publicPage}`},
    {...guest, name:'guest-sidebar', path:'/ui/sidebar?parent=1001'},
    {...guest, status:403, required:null, name:'guest-private-denied', path:`/ui/page/${privatePage}`},
    {role:'reader', status:200, required:'LOAD_PRIVATE', name:'reader-page', path:`/ui/page/${privatePage}`},
    {role:'reader', status:200, required:'LOAD_PRIVATE', name:'reader-search', path:'/search?q=LOAD_PRIVATE'}
  ];
  const dockerInfo = JSON.parse(run('docker', ['info', '--format', '{{json .}}']));
  report.engine = {cpus:dockerInfo.NCPU, memoryBytes:dockerInfo.MemTotal, architecture:dockerInfo.Architecture};
  report.fixture = {pages:options.pages, branches:options.branches, depth:2, publicPages:options.pages / 2,
    contentCharacters:1100, readerAccounts:maxWorkers};
  const ids = [compose(['ps', '--quiet', 'app']), compose(['ps', '--quiet', 'database'])];
  for (const concurrency of options.concurrency) {
    const warmup = await measure({count:scenarios.length * 2 * concurrency, concurrency, scenarios, clients});
    if (warmup.errors) report.warmupFailure = warmup;
    assert.equal(warmup.errors, 0, 'Warmup failed; workload would measure incorrect responses');
    const phase = {concurrency, ...await measure({count:options.requests, concurrency, scenarios, clients})};
    phase.resourcesAfter = run('docker', ['stats', '--no-stream', '--format', '{{json .}}', ...ids])
      .split('\n').map(line => JSON.parse(line));
    phase.passed = phase.errors === 0 && (options.p95Ms === null || phase.scenarios.every(row => row.p95Ms <= options.p95Ms));
    report.phases.push(phase); save();
    console.log(`concurrency=${concurrency}: ${phase.requests} requests, ${phase.errors} errors, p95=${phase.p95Ms}ms, ${phase.requestsPerSecond.toFixed(1)} requests/s`);
  }
  report.passed = report.phases.every(phase => phase.passed);
  if (!report.passed) process.exitCode = 1;
} catch (error) {
  report.failure = 'Setup, warmup or measurement failed; inspect the local command log.';
  throw error;
} finally {
  try { cleanup(); } catch (error) { report.passed = false; report.cleanupFailed = true; throw error; }
  finally { save(); }
}
console.log(`Load scenario ${report.passed ? 'passed' : 'failed'}; report: target/load-smoke.json`);
