# jknowledgeroot
Knowledgeroot Knowledgebase written in Java

## Roadmap

The [production readiness roadmap](docs/roadmap.md) tracks priorities, acceptance
criteria, open decisions, and progress toward a production release.

Page edits save content, labels, and submitted permissions in one database
transaction. Multi-file uploads publish their metadata together and use the
actual user's audit identity. See [transactions and storage failures](docs/transactions.md)
for rollback behavior and the handling of unreferenced storage objects.

Search and the page API apply permissions before pagination. See
[search, input limits, and error responses](docs/search-and-validation.md)
for request limits, compatibility changes, and database indexes.

Local storage starts independently of MinIO. New uploads use streaming SHA-256
hashing; existing MD5 objects remain readable. Defaults are 25 MiB per file,
100 MiB per request and ten files per upload. See the [storage guide](docs/storage.md)
for configuration, temporary disk space, safe downloads and manual orphan cleanup.

## Build and tests

Install JDK 25 and set `JAVA_HOME` to its installation directory. Start a Docker
engine for the isolated MariaDB and MinIO tests. The Maven
Wrapper downloads the pinned Maven version on its first invocation; a separate
Maven installation is not required. Maven also installs the pinned Node/npm
toolchain and builds the editor from `frontend/package-lock.json`.
The browser test downloads Chromium on its first run; Linux needs the system
libraries described in the [editor and build guide](docs/editor.md).

On Linux or macOS:

```sh
sh ./mvnw -B --no-transfer-progress verify
```

On Windows (PowerShell):

```powershell
.\mvnw.cmd -B --no-transfer-progress verify
```

The build compiles the application, runs the enabled tests, and creates the
executable JAR in `target/`. The previously disabled integration classes now use
isolated databases, actual login/CSRF and JDBC sessions. Installation tests exercise a real MariaDB in
Testcontainers. During `verify`, Failsafe starts the packaged JAR against
disposable MariaDB/MinIO containers and checks real HTTP login, JPA-backed admin
access, WebJars, JDBC-session persistence across restart, and logout. Chromium
also checks editor creation/saving, repeated HTMX navigation, rich-text preservation,
malicious paste handling, and a real file upload/download.
An additional JAR start uses local storage with an invalid MinIO URL and checks
an upload at the size limit, its download and HTTP 413 above the limit.
Full browser, storage, and deployment coverage remains in the roadmap.

Spring Boot 4.1.1 manages the framework dependencies. See the
[dependency and security audit guide](docs/dependencies.md) for migration details,
the Tomcat security override, repeatable Maven/npm scanning, and the resolved R08 findings.

The rich-text editor uses self-hosted Tiptap with MIT-licensed extensions;
the project license remains BSD-2-Clause. No editor license key is required.
See the [editor guide](docs/editor.md) for supported content and upgrade checks.

## Initial administrator and upgrades

The current release candidate is **1.0.0-rc.2**. See the
[release guide](docs/release.md) for artifact identity and remaining approval checks,
and [backup and recovery](docs/recovery.md) for tested backup, restore, upgrade
and snapshot rollback procedures for the production Compose deployment.
Page edits now detect stale revisions. [Page history and deletion](docs/page-history.md)
explains restoring earlier content, preserving drafts on conflicts, and administrator
recovery of deleted pages. REST update/delete clients must send the current revision.

Status-only liveness/readiness probes and administrator-only metrics are available.
Readiness checks the database and selected file storage; the container has a built-in
readiness healthcheck. See [monitoring and integration tests](docs/monitoring.md)
for endpoint access, failure detection and operational limits.

For the production profile, restricted database accounts, a persistent local file
volume, HTTPS/proxy settings and the release workflow, see the
[production deployment guide](docs/production.md). The Docker image runs as UID
10001. Production Compose is separate from the existing development services.

Fresh databases require individually configured `KR_BOOTSTRAP_LOGIN` and
`KR_BOOTSTRAP_PASSWORD` on the first start. There is no default administrator.
Demo data requires the explicit `development` profile. Existing installations
must rotate or disable remaining demo credentials before upgrading outside
development. See the [installation and upgrade guide](docs/installation.md)
for bootstrap rules, migration checks, and session schema ownership.

## Page permissions

New child pages dynamically inherit their parent's permissions. New root pages
created by signed-in users are private to their creator and administrators.
Set `KR_ALLOW_GUEST_ROOT_CREATION=true` to allow guests to create publicly readable
root pages; the default is `false`. Guests may create children wherever they have
parent-page edit permission. Public read access alone never grants editing.

Only administrators manage sharing and explicitly switch between inherited and
local permissions. Existing pages retain their local grants during upgrade.
See the [access-control guide](docs/access-control.md) for the complete rules.

## Login and sessions

New passwords use PBKDF2-HMAC-SHA-256 with 600,000 iterations. Legacy hashes migrate
after a successful login; known demo credentials still require an explicit password
change. Password updates invalidate existing sessions on their next request.
New passwords have 16–128 characters, with whitespace preserved in both UI and API.

Login quotas are shared through MariaDB: by default, five attempts per account and
30 per source address in 60 seconds. See the [authentication guide](docs/authentication.md)
for configuration, proxy behavior, migration rules, and upgrade implications.

Each authenticated request reloads the account by its stable user ID. Disabling
or deleting an account, or changing its USER/ADMIN role, invalidates each existing
session on its next request. A role change requires a new login. Renaming an
account keeps its identity and refreshes the login name in the session.

Logout invalidates the current session. Login and logout require a CSRF token;
a successful login rotates an existing session ID. If account validation cannot
reach the database, the request fails before reaching the application endpoint.
Previously stored unauthenticated application tokens require a new login.

HTTP/session rules are tested with the real provider and security filter chain
with mocked database access. Hash migration and shared login quotas also have real
MariaDB tests. The packaged-application smoke test also verifies real HTTP login
and JDBC-session reuse after restart. Full application tests also cover groups,
comments, stars, labels, attachments and permission changes.

## Local configuration

The project uses environment variables for credentials and runtime settings.

1. Copy `.env.example` to `.env`
2. Set secure values in `.env`
3. Start dependencies:
   `docker compose --env-file .env up -d`
4. Start app:
   `sh ./mvnw spring-boot:run` (Windows: `.\mvnw.cmd spring-boot:run`)

The Spring Boot app reads values from `src/main/resources/application.properties` using `${...}` placeholders.

## Local override file (without exporting env vars)

You can also use a local override file that is not committed:

1. Copy `config/application-local.properties.example` to `config/application-local.properties`
2. Set your local values in `config/application-local.properties`
3. Start app normally:
   `sh ./mvnw spring-boot:run` (Windows: `.\mvnw.cmd spring-boot:run`)

`application.properties` imports this file via:
`spring.config.import=optional:file:./config/application-local.properties`
