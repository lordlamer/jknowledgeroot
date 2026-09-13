import {readFileSync, writeFileSync, existsSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
const run = (command, args) => execFileSync(command, args, {encoding:'utf8'}).trim();
const pom = readFileSync('pom.xml','utf8');
const version = pom.match(/<artifactId>knowledgeroot<\/artifactId>\s*<version>([^<]+)<\/version>/)?.[1];
assert(version, 'Project version could not be read');
if (process.argv[2] === '--version') { console.log(version); process.exit(0); }
const image = process.argv[2];
assert(image, 'Pass the verified container image');
const revision = run('git',['rev-parse','HEAD']);
const dirty = run('git',['status','--porcelain','--untracked-files=no']) !== '';
if (process.env.RELEASE_TAG) {
  assert.equal(process.env.RELEASE_TAG, `v${version}`, 'Release tag must match the Maven artifact version');
  assert(!version.includes('SNAPSHOT') && !dirty, 'Release requires a clean, non-snapshot build');
}
const jar = `target/knowledgeroot-${version}.jar`;
assert(existsSync(jar), 'Packaged application missing');
const inspect = JSON.parse(run('docker',['image','inspect',image]))[0];
assert.equal(inspect.Config.Labels['org.opencontainers.image.version'],version);
assert.equal(inspect.Config.Labels['org.opencontainers.image.revision'],revision);
const jarSha256 = createHash('sha256').update(readFileSync(jar)).digest('hex');
const imageJarSha256 = run('docker',['run','--rm','--network','none','--read-only','--entrypoint','sha256sum',image,'/app/app.jar']).split(/\s+/)[0];
assert.equal(imageJarSha256,jarSha256,'Image must contain the exact verified JAR');
const metadata = {version, revision, dirty, jar, jarSha256,
  imageId:inspect.Id, imageDigests:inspect.RepoDigests ?? [], baseImage:readFileSync('Dockerfile','utf8').split('\n')[0].slice(5).trim()};
writeFileSync('target/release-manifest.json', JSON.stringify(metadata,null,2)+'\n');
console.log(`Release evidence written for ${version}, revision ${revision}${dirty ? ' (working tree modified)' : ''}`);
