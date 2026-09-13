import {execFileSync} from 'node:child_process';
import {utimesSync} from 'node:fs';
import assert from 'node:assert/strict';

const image = process.argv[2];
assert(image && !image.startsWith('-'), 'Pass a local output image tag');
const version = execFileSync(process.execPath, ['scripts/release-metadata.mjs', '--version'], {encoding:'utf8'}).trim();
const revision = execFileSync('git', ['rev-parse', 'HEAD'], {encoding:'utf8'}).trim();
// Maven normalizes the file timestamp as well as archive entries. Docker Desktop's
// incremental context transfer can otherwise reuse same-size files after a rebuild.
// Refresh only the host file metadata; the JAR bytes and their hash stay unchanged.
const now = new Date();
utimesSync(`target/knowledgeroot-${version}.jar`, now, now);
execFileSync('docker', ['build', '--build-arg', `BUILD_VERSION=${version}`,
  '--build-arg', `BUILD_REVISION=${revision}`, '--tag', image, '.'], {stdio:'inherit'});
execFileSync(process.execPath, ['scripts/release-metadata.mjs', image], {stdio:'inherit'});
