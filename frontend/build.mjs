import { build } from 'esbuild';
import { mkdir, writeFile, readFile, readdir } from 'node:fs/promises';
import { dirname, resolve, join } from 'node:path';
import { existsSync } from 'node:fs';

const directory = '../target/generated-resources/static/resources/editor';
await mkdir(directory, { recursive: true });
const result = await build({
  entryPoints: ['editor.js'], bundle: true, minify: true, format: 'iife',
  target: ['es2022'], outfile: `${directory}/editor.js`, metafile: true,
  legalComments: 'external'
});
await writeFile('../target/editor-metafile.json', JSON.stringify(result.metafile, null, 2));

// Ship the license texts of every package included in the generated browser bundle.
const packages = new Map();
for (const input of Object.keys(result.metafile.inputs)) {
  if (!input.startsWith('node_modules/')) continue;
  let path = dirname(resolve(input));
  while (!existsSync(join(path, 'package.json'))) {
    const parent = dirname(path);
    if (parent === path) throw new Error(`Missing package metadata for ${input}`);
    path = parent;
  }
  const metadata = JSON.parse(await readFile(join(path, 'package.json'), 'utf8'));
  if (packages.has(metadata.name)) continue;
  const licenses = (await readdir(path)).filter(name => /^(license|copying)(\.|$|-)/i.test(name));
  if (!licenses.length) throw new Error(`Missing license notice for ${metadata.name}`);
  packages.set(metadata.name, `${metadata.name} ${metadata.version}\n${metadata.license}\n\n`
    + (await Promise.all(licenses.sort().map(name => readFile(join(path, name), 'utf8')))).join('\n'));
}
await writeFile(`${directory}/THIRD-PARTY.txt`, [...packages.entries()].sort(([a], [b]) => a.localeCompare(b))
  .map(([, notice]) => notice).join('\n\n----------------------------------------\n\n'));
