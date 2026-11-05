// Takes the README screenshots (docs/screenshots/) from a running Knowledgeroot instance with
// headless Chromium. Seed the instance first with scripts/screenshots/seed.sh, then run from the
// repository root:
//   npm install --no-save playwright-core@1.62.0
//   node scripts/screenshots/shoot.mjs http://localhost:8099 admin PASSWORD docs/screenshots
// The Chromium build of the Maven Playwright test (same version) is reused; set
// KR_CHROMIUM to another chrome executable if the build is missing.
import {chromium} from 'playwright-core';
import {mkdirSync} from 'node:fs';
import {homedir} from 'node:os';
import {join} from 'node:path';

const [base, login, password, out = 'docs/screenshots'] = process.argv.slice(2);
if (!base || !login || !password) {
  console.error('Usage: node scripts/screenshots/shoot.mjs BASE_URL LOGIN PASSWORD [OUT_DIR]');
  process.exit(2);
}
mkdirSync(out, {recursive: true});
const executablePath = process.env.KR_CHROMIUM
  || join(homedir(), 'AppData', 'Local', 'ms-playwright', 'chromium-1234', 'chrome-win64', 'chrome.exe');
const browser = await chromium.launch({executablePath, headless: true});
const context = await browser.newContext({viewport: {width: 1440, height: 900}, deviceScaleFactor: 1.5, locale: 'en-US'});
const page = await context.newPage();
const shot = async name => {
  await page.waitForTimeout(600);
  await page.screenshot({path: join(out, name + '.png')});
  console.log('wrote', join(out, name + '.png'));
};

await page.goto(base + '/login');
await page.fill('input[name=username]', login);
await page.fill('input[name=password]', password);
await page.click('button[type=submit]');
await page.waitForURL(url => !url.toString().includes('/login'));

// Page view with labels, stars and a comment (seed.sh writes the ids to ids.txt; 3 is the review page).
await page.goto(base + '/ui/page/3');
await shot('page');

await page.goto(base + '/ui/page/1');
await page.fill('.kr-search-input', 'backup');
await page.keyboard.press('Enter');
await page.waitForLoadState('networkidle');
await shot('search');

await page.goto(base + '/ui/page/1');
await page.click('#krSpaceMenu');
await shot('spaces');

await page.goto(base + '/admin/settings');
await shot('admin-settings');

await page.goto(base + '/ui/page/6');
await page.click('#krThemeBtn');
await shot('dark-theme');

// GitHub social preview (Settings → Social preview expects 1280×640).
await page.setViewportSize({width: 1280, height: 640});
await page.goto(base + '/ui/page/1');
await shot('social-preview');

await browser.close();
