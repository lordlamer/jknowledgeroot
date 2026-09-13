import {execFileSync} from 'node:child_process';
import {utimesSync, rmSync} from 'node:fs';
import assert from 'node:assert/strict';

const image = process.argv[2];
const databaseImage = process.argv[3];
assert(process.argv.length === 4 && image && databaseImage && !image.startsWith('-') && !databaseImage.startsWith('-')
  && image !== databaseImage, 'Pass distinct local application and database output image tags');
rmSync('target/release-manifest.json', {force:true});
const version = execFileSync(process.execPath, ['scripts/release-metadata.mjs', '--version'], {encoding:'utf8'}).trim();
const revision = execFileSync('git', ['rev-parse', 'HEAD'], {encoding:'utf8'}).trim();
// Maven normalizes the file timestamp as well as archive entries. Docker Desktop's
// incremental context transfer can otherwise reuse same-size files after a rebuild.
// Refresh only the host file metadata; the JAR bytes and their hash stay unchanged.
const now = new Date();
utimesSync(`target/knowledgeroot-${version}.jar`, now, now);
execFileSync('docker', ['build', '--build-arg', `BUILD_VERSION=${version}`,
  '--build-arg', `BUILD_REVISION=${revision}`, '--tag', image, '.'], {stdio:'inherit'});
execFileSync('docker', ['build', '--file', 'deploy/Dockerfile.database', '--build-arg', `BUILD_VERSION=${version}`,
  '--build-arg', `BUILD_REVISION=${revision}`, '--tag', databaseImage, '.'], {stdio:'inherit'});
execFileSync(process.execPath, ['scripts/release-metadata.mjs', image, databaseImage], {stdio:'inherit'});
