# jknowledgeroot
Knowledgeroot Knowledgebase written in Java

## Roadmap

The [production readiness roadmap](docs/roadmap.md) tracks priorities, acceptance
criteria, open decisions, and progress toward a production release.

## Build and tests

Install JDK 25 and set `JAVA_HOME` to its installation directory. The Maven
Wrapper downloads the pinned Maven version on its first invocation; a separate
Maven installation is not required.

On Linux or macOS:

```sh
sh ./mvnw -B --no-transfer-progress verify
```

On Windows (PowerShell):

```powershell
.\mvnw.cmd -B --no-transfer-progress verify
```

The build compiles the application, runs the enabled tests, and creates the
executable JAR in `target/`. The two existing disabled integration test classes
are tracked in roadmap item R13; a successful build does not yet verify a real
database or storage deployment.

## Login and sessions

Each authenticated request reloads the account by its stable user ID. Disabling
or deleting an account, or changing its USER/ADMIN role, invalidates each existing
session on its next request. A role change requires a new login. Renaming an
account keeps its identity and refreshes the login name in the session.

Logout invalidates the current session. Login and logout require a CSRF token;
a successful login rotates an existing session ID. If account validation cannot
reach the database, the request fails before reaching the application endpoint.
Previously stored unauthenticated application tokens require a new login.

These rules are covered by tests using the real authentication provider and
security filter chain with a mocked account database. Database-backed session
integration tests remain part of roadmap item R13.

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
