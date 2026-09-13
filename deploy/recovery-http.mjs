// Synthetic fixture verification on disposable, loopback-only Compose installations.
import assert from 'node:assert/strict';
const base = process.argv[2];
assert.match(base, /^http:\/\/127\.0\.0\.1:\d+$/);
const payload = 'recovery attachment\n';
function client() {
  const cookies = new Map();
  return async (path, options = {}) => {
    const response = await fetch(base + path, {redirect: 'manual', ...options,
      headers: {cookie: [...cookies].map(([k,v]) => `${k}=${v}`).join('; '), ...options.headers},
      signal: AbortSignal.timeout(15000)});
    for (const value of response.headers.getSetCookie()) {
      const pair = value.split(';')[0], split = pair.indexOf('=');
      cookies.set(pair.slice(0, split), pair.slice(split + 1));
    }
    return response;
  };
}
async function login(name) {
  const request = client();
  const html = await (await request('/login')).text();
  const csrf = html.match(/name="_csrf"[^>]*value="([^"]+)"/)[1];
  const response = await request('/logmein', {method:'POST', body: new URLSearchParams({
    username: name, password: process.env.RECOVERY_TEST_PASSWORD, _csrf:csrf})});
  assert.equal(response.status, 302);
  assert.match(response.headers.get('location'), /\/login\/success$/);
  return request;
}
const guest = client();
assert.equal((await guest('/actuator/health/readiness')).status, 200);
assert.match(await (await guest('/ui/page/101')).text(), /recovery public/);
assert.equal((await guest('/ui/page/102')).status, 403);
const reader = await login('recovery.reader');
assert.match(await (await reader('/ui/page/102')).text(), /recovery private/);
const download = await reader('/ui/file/101/download/attachment.txt');
assert.equal(download.status, 200);
assert.equal(await download.text(), payload);
assert.equal((await (await login('recovery.outsider'))('/ui/page/102')).status, 403);
assert.equal((await guest('/ui/file/101/download/attachment.txt')).status, 403);
console.log('Restored login, public/private pages, group access and attachment bytes verified.');
