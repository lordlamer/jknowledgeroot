# jknowledgeroot
Knowledgeroot Knowledgebase written in Java

## Local configuration

The project uses environment variables for credentials and runtime settings.

1. Copy `.env.example` to `.env`
2. Set secure values in `.env`
3. Start dependencies:
   `docker compose --env-file .env up -d`
4. Start app:
   `mvn spring-boot:run`

The Spring Boot app reads values from `src/main/resources/application.properties` using `${...}` placeholders.

## Local override file (without exporting env vars)

You can also use a local override file that is not committed:

1. Copy `config/application-local.properties.example` to `config/application-local.properties`
2. Set your local values in `config/application-local.properties`
3. Start app normally:
   `mvn spring-boot:run`

`application.properties` imports this file via:
`spring.config.import=optional:file:./config/application-local.properties`
