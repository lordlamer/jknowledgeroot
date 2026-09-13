import {readFileSync, writeFileSync, existsSync, rmSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {verifyJarBuild, verifyAuditEvidence} from './release-evidence.mjs';
const run = (command, args) => execFileSync(command, args, {encoding:'utf8'}).trim();
if (process.argv[2] !== '--version') rmSync('target/release-manifest.json', {force:true});
const pom = readFileSync('pom.xml','utf8');
const version = pom.match(/<artifactId>knowledgeroot<\/artifactId>\s*<version>([^<]+)<\/version>/)?.[1];
assert(version, 'Project version could not be read');
if (process.argv[2] === '--version') { console.log(version); process.exit(0); }
const image = process.argv[2];
const databaseImage = process.argv[3];
assert(image && databaseImage && !image.startsWith('-') && !databaseImage.startsWith('-') && image !== databaseImage
  && (process.argv.length === 4 || (process.argv.length === 5 && process.argv[4] === '--with-audits')),
  'Pass distinct application and database images, optionally followed by --with-audits');
const withAudits = process.argv[4] === '--with-audits' || Boolean(process.env.RELEASE_TAG);
const revision = run('git',['rev-parse','HEAD']);
const dirty = run('git',['status','--porcelain','--untracked-files=no']) !== '';
if (process.env.RELEASE_TAG) {
  assert.equal(process.env.RELEASE_TAG, `v${version}`, 'Release tag must match the Maven artifact version');
  assert(!version.includes('SNAPSHOT') && !dirty, 'Release requires a clean, non-snapshot build');
}
const jar = `target/knowledgeroot-${version}.jar`;
assert(existsSync(jar), 'Packaged application missing');
const jarBuild = verifyJarBuild(jar, version, revision);
const inspect = JSON.parse(run('docker',['image','inspect',image]))[0];
assert.equal(inspect.Config.Labels['org.opencontainers.image.version'],version);
assert.equal(inspect.Config.Labels['org.opencontainers.image.revision'],revision);
const jarSha256 = createHash('sha256').update(readFileSync(jar)).digest('hex');
const imageJarSha256 = run('docker',['run','--rm','--network','none','--read-only','--entrypoint','sha256sum',inspect.Id,'/app/app.jar']).split(/\s+/)[0];
assert.equal(imageJarSha256,jarSha256,'Image must contain the exact verified JAR');
const jreRelease = run('docker',['run','--rm','--network','none','--read-only','--entrypoint','cat',inspect.Id,'/opt/java/openjdk/release']);
assert.match(jreRelease, /^JAVA_RUNTIME_VERSION="25\.0\.4\.1\+1-LTS"$/m, 'Unexpected application JRE');
assert(inspect.Config.Env.includes('JAVA_VERSION=jdk-25.0.4.1+1'), 'Outdated JRE image environment');
const dbInspect = JSON.parse(run('docker',['image','inspect',databaseImage]))[0];
assert.equal(dbInspect.Config.Labels['org.opencontainers.image.version'],version);
assert.equal(dbInspect.Config.Labels['org.opencontainers.image.revision'],revision);
const databaseVersion = run('docker',['run','--rm','--network','none','--read-only','--entrypoint','mariadbd',dbInspect.Id,'--version']);
assert.match(databaseVersion, /\b12\.3\.3-MariaDB\b/, 'Unexpected database server version');
const databaseGlibc = run('docker',['run','--rm','--network','none','--read-only','--entrypoint','dpkg-query',dbInspect.Id,
  '--show','--showformat=${Package}=${Version}\n','libc6','libc-bin']);
assert.equal(databaseGlibc, 'libc-bin=2.39-0ubuntu8.9\nlibc6=2.39-0ubuntu8.9', 'Database glibc fixes missing');
const baseImage = file => readFileSync(file,'utf8').match(/^FROM\s+(\S+)/m)?.[1];
const audit = (prefix, inspected) => ({report:`target/${prefix}.json`, summary:`target/${prefix}-summary.json`,
  ...verifyAuditEvidence(readFileSync(`target/${prefix}.json`), readFileSync(`target/${prefix}-summary.json`), inspected)});
const audits = withAudits ? {application:audit('image-audit', inspect), database:audit('database-image-audit', dbInspect)} : null;
const metadata = {version, revision, dirty, jar, jarSha256, jarBuild, auditsVerified:withAudits, audits,
  imageId:inspect.Id, imageDigests:inspect.RepoDigests ?? [], baseImage:baseImage('Dockerfile'),
  javaRuntimeVersion:'25.0.4.1+1-LTS',
  database:{image:databaseImage, imageId:dbInspect.Id, imageDigests:dbInspect.RepoDigests ?? [],
    baseImage:baseImage('deploy/Dockerfile.database'), serverVersion:databaseVersion, glibc:databaseGlibc}};
writeFileSync('target/release-manifest.json', JSON.stringify(metadata,null,2)+'\n');
console.log(`Release evidence written for ${version}, revision ${revision}${dirty ? ' (working tree modified)' : ''}`);
