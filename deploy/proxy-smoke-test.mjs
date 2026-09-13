// Disposable HTTPS ingress verification; never uses operator configuration or volumes.
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {createHash, randomBytes} from 'node:crypto';
import {mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import http from 'node:http';
import https from 'node:https';
import {setTimeout as delay} from 'node:timers/promises';

const root = fileURLToPath(new URL('../', import.meta.url));
const evidence = join(root, 'target/proxy-smoke.json');
rmSync(evidence, {force:true});
const [appImage, databaseImage] = process.argv.slice(2);
assert(process.argv.length === 4 && appImage && databaseImage && !appImage.startsWith('-') && !databaseImage.startsWith('-'),
  'Usage: node deploy/proxy-smoke-test.mjs APP_IMAGE DATABASE_IMAGE');
// Official stable test fixture; production operators select and audit their own proxy.
const proxyImage = 'nginx:1.30.4-alpine@sha256:dc5069ad14f19660b141b21236140b91656bf89bbc3e2417c70ae650cd66104c';
const project = `knowledgeroot-proxy-${randomBytes(8).toString('hex')}`;
const work = mkdtempSync(join(tmpdir(), project + '-'));
const env = Object.fromEntries(Object.entries(process.env).filter(([key]) => !key.startsWith('KR_')));
const run = (command, args, options = {}) => execFileSync(command, args,
  {cwd:root, env, encoding:'utf8', timeout:180_000, ...options}).trim();
const envfile = join(work, 'test.env'), override = join(work, 'compose.json');
const compose = args => run('docker', ['compose', '--project-name', project, '--env-file', envfile,
  '-f', join(root, 'deploy/compose.production.yaml'), '-f', override, ...args]);
let created = false;
function cleanup() {
  if (created) {
    compose(['down', '--volumes', '--remove-orphans']);
    created = false;
  }
  // Only this invocation's mkdtemp directory and generated Compose project.
  rmSync(work, {recursive:true, force:true});
}
process.once('SIGINT', () => process.exit(130));
process.once('SIGTERM', () => process.exit(143));
process.once('exit', () => { if (created) { try { cleanup(); } catch { console.error(`Cleanup required for ${project}`); } } });
const checks = [];
const checked = name => { checks.push(name); console.log(`PASS: ${name}`); };
const host = 'knowledge.example.org';
const password = randomBytes(32).toString('hex');
let httpsPort, ca;
function client() {
  const cookies = new Map();
  return (path, {secure = true, port = httpsPort, method = 'GET', form, headers = {}} = {}) => new Promise((accept, reject) => {
    const body = form ? new URLSearchParams(form).toString() : undefined;
    const request = (secure ? https : http).request({hostname:'127.0.0.1', port, path, method,
      ...(secure ? {servername:host, ca} : {}),
      headers:{Host:host, ...(secure ? {Cookie:[...cookies].map(([k,v]) => `${k}=${v}`).join('; ')} : {}),
        ...(body ? {'Content-Type':'application/x-www-form-urlencoded', 'Content-Length':Buffer.byteLength(body)} : {}), ...headers}}, response => {
      const chunks = [];
      response.on('data', chunk => chunks.push(chunk));
      response.on('error', reject);
      response.on('end', () => {
        if (secure) for (const cookie of response.headers['set-cookie'] ?? []) {
          const pair = cookie.split(';')[0], split = pair.indexOf('=');
          cookies.set(pair.slice(0, split), pair.slice(split + 1));
        }
        accept({status:response.statusCode, headers:response.headers, body:Buffer.concat(chunks).toString('utf8')});
      });
    });
    request.setTimeout(15_000, () => request.destroy(new Error('Proxy request timed out')));
    request.on('error', reject); request.end(body);
  });
}
const csrf = response => {
  assert.equal(response.status, 200);
  const token = response.body.match(/name="_csrf"[^>]*(?:value|content)="([^"]+)"/)?.[1];
  assert(token, 'CSRF form token missing'); return token;
};
const hostile = attempt => ({Host:'attacker.invalid', Forwarded:'for=203.0.113.99;proto=http;host=attacker.invalid',
  'X-Forwarded-For':`198.51.100.${attempt}`, 'X-Forwarded-Proto':'http', 'X-Forwarded-Port':'81',
  'X-Forwarded-Host':'attacker.invalid', 'X-Forwarded-Prefix':'/injected'});
const redirect = (response, path) => {
  assert.equal(response.status, 302);
  assert.equal(response.headers.location, `https://${host}${path}`, 'Redirect escaped the canonical HTTPS origin');
};
try {
  writeFileSync(envfile, Object.entries({KR_APP_IMAGE:appImage, KR_DB_IMAGE:databaseImage,
    KR_DB_ROOT_PASSWORD:randomBytes(32).toString('hex'), KR_DB_PASSWORD:randomBytes(32).toString('hex'),
    KR_MIGRATION_PASSWORD:randomBytes(32).toString('hex'), KR_BOOTSTRAP_LOGIN:'proxy.admin',
    KR_BOOTSTRAP_PASSWORD:password, KR_APP_PORT:'0'}).map(([k,v]) => `${k}=${v}\n`).join(''), {mode:0o600});
  const certificates = join(work, 'tls');
  mkdirSync(certificates, {mode:0o700});
  run('openssl', ['req', '-x509', '-newkey', 'rsa:2048', '-sha256', '-nodes', '-days', '1',
    '-subj', `/CN=${host}`, '-addext', `subjectAltName=DNS:${host}`,
    '-keyout', join(certificates, 'privkey.pem'), '-out', join(certificates, 'fullchain.pem')], {stdio:'pipe'});
  ca = readFileSync(join(certificates, 'fullchain.pem'));
  const source = readFileSync(join(root, 'deploy/nginx.conf.example'), 'utf8');
  // Only adapt certificate paths and the container's upstream; keep ingress rules intact.
  assert(source.includes('http://127.0.0.1:8081;'), 'Example upstream changed; review fixture adaptation');
  writeFileSync(join(work, 'nginx.conf'), source.replace('http://127.0.0.1:8081;', 'http://app:8081;')
    .replaceAll(`/etc/letsencrypt/live/${host}/`, '/test/'));
  const config = {services:{proxy:{image:proxyImage, command:['sleep', 'infinity'], networks:['backend'],
    ports:['127.0.0.1:0:80', '127.0.0.1:0:443'],
    volumes:[{type:'bind', source:resolve(certificates), target:'/test', read_only:true},
      {type:'bind', source:join(work, 'nginx.conf'), target:'/etc/nginx/conf.d/default.conf', read_only:true}]}}};
  writeFileSync(override, JSON.stringify(config));
  created = true;
  compose(['up', '--detach', 'proxy']);
  const proxyId = compose(['ps', '--quiet', 'proxy']);
  const inspected = JSON.parse(run('docker', ['inspect', proxyId]))[0];
  const address = inspected.NetworkSettings.Networks[`${project}_backend`].IPAddress;
  assert.match(address, /^\d+\.\d+\.\d+\.\d+$/);
  config.services.app = {environment:{KR_FORWARD_HEADERS_STRATEGY:'native', KR_TRUSTED_PROXY_PATTERN:address.replaceAll('.', '[.]'),
    SPRING_APPLICATION_JSON:JSON.stringify({knowledgeroot:{login:{'source-attempts':3, 'window-seconds':600}}})}};
  writeFileSync(override, JSON.stringify(config));
  compose(['up', '--detach', '--wait', '--wait-timeout', '120', 'app']);
  run('docker', ['exec', proxyId, 'nginx', '-t']);
  run('docker', ['exec', '--detach', proxyId, 'nginx', '-g', 'daemon off;']);
  const port = (service, internal) => Number(compose(['port', service, String(internal)]).split(':').at(-1));
  httpsPort = port('proxy', 443);
  const guest = client();
  for (let attempt = 0; ; attempt++) {
    try { assert.equal((await guest('/login')).status, 200); break; }
    catch (error) { if (attempt === 20) throw error; await delay(500); }
  }
  const httpRedirect = await guest('/login?next=%2Fuser', {secure:false, port:port('proxy', 80), headers:hostile(1)});
  assert.equal(httpRedirect.status, 308);
  assert.equal(httpRedirect.headers.location, `https://${host}/login?next=%2Fuser`);
  checked('HTTP redirects to canonical HTTPS, preserving path and query');
  const login = await guest('/login', {headers:hostile(1)});
  assert.match(login.headers['strict-transport-security'] ?? '', /max-age=\d+/);
  const signedIn = await guest('/logmein', {method:'POST', headers:hostile(1),
    form:{username:'proxy.admin', password, _csrf:csrf(login)}});
  redirect(signedIn, '/login/success');
  const session = signedIn.headers['set-cookie']?.find(cookie => cookie.startsWith('SESSION='));
  for (const flag of ['Secure', 'HttpOnly', 'SameSite=Lax']) assert(session?.includes(flag), `Session cookie missing ${flag}`);
  const admin = await guest('/admin/users');
  assert.equal(admin.status, 200);
  checked('Verified TLS, HSTS, secure session cookies and administrator login through nginx');
  assert.equal((await guest('/logout', {method:'POST'})).status, 403);
  redirect(await guest('/logout', {method:'POST', form:{_csrf:csrf(admin)}, headers:hostile(2)}), '/logout/success');
  redirect(await guest('/user', {headers:hostile(2)}), '/login');
  checked('CSRF-protected logout invalidates the session and redirects stay on HTTPS');
  const direct = await client()('/login', {secure:false, port:port('app', 8081),
    headers:{'X-Forwarded-Proto':'https', 'X-Forwarded-For':'203.0.113.99'}});
  assert.equal(direct.status, 200);
  assert.equal(direct.headers['strict-transport-security'], undefined);
  checked('Direct requests outside the trusted proxy cannot claim HTTPS');
  const probe = client();
  for (let attempt = 2; attempt <= 4; attempt++) {
    const token = csrf(await probe('/login'));
    const response = await probe('/logmein', {method:'POST', headers:hostile(attempt),
      form:{username:attempt === 4 ? 'proxy.admin' : `proxy.invalid.${attempt}`,
        password:attempt === 4 ? password : 'incorrect', _csrf:token}});
    redirect(response, '/login?error');
  }
  redirect(await probe('/user'), '/login');
  checked('Changing forged forwarding headers cannot bypass the shared source login quota');
  const identity = service => JSON.parse(run('docker', ['inspect', compose(['ps', '--quiet', service])]))[0].Image;
  const result = {passed:true, checkedAt:new Date().toISOString(), revision:run('git', ['rev-parse', 'HEAD']),
    dirty:run('git', ['status', '--porcelain', '--untracked-files=no']) !== '',
    appImageId:identity('app'), databaseImageId:identity('database'), proxyImage,
    exampleSha256:createHash('sha256').update(source).digest('hex'), checks};
  cleanup();
  mkdirSync(join(root, 'target'), {recursive:true});
  writeFileSync(evidence, JSON.stringify(result, null, 2) + '\n');
  console.log('HTTPS proxy smoke test passed; disposable project removed.');
} finally { cleanup(); }
