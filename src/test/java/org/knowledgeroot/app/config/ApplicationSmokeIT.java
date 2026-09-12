package org.knowledgeroot.app.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP, JPA, migrations, JDBC sessions and storage initialization, without local configuration. */
@Testcontainers
class ApplicationSmokeIT {
    @Container static final MariaDBContainer<?> database = new MariaDBContainer<>("mariadb:12.2.2");
    // Pinned compatibility fixture; the production storage choice remains in R12.
    @Container static final GenericContainer<?> storage = new GenericContainer<>("quay.io/minio/minio:RELEASE.2025-04-22T22-12-26Z")
            .withEnv("MINIO_ROOT_USER", "smoke-test-user")
            .withEnv("MINIO_ROOT_PASSWORD", "smoke-test-password")
            .withCommand("server /data").withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
    @TempDir Path files;

    @Test
    void applicationStartsAndKeepsAuthenticatedJdbcSessionAfterRestart() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        try (var client = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10)).build()) {
            try (var context = start(true)) {
                var login = get(client, context, "/login");
                assertEquals(200, login.statusCode());
                assertTrue(login.body().contains("Sign in"));
                assertEquals(200, get(client, context, "/webjars/htmx.org/dist/htmx.min.js").statusCode());
                assertEquals(200, get(client, context, "/webjars/bootstrap/css/bootstrap.min.css").statusCode());
                String form = "username=smoke.admin&password=smoke-test-password-2026&_csrf="
                        + URLEncoder.encode(csrf(login.body()), StandardCharsets.UTF_8);
                var authenticated = client.send(HttpRequest.newBuilder(uri(context, "/logmein"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(302, authenticated.statusCode());
                assertTrue(authenticated.headers().firstValue("location").orElseThrow().endsWith("/login/success"));
                assertAdministrator(client, context);
                var jdbc = new JdbcTemplate(new DriverManagerDataSource(database.getJdbcUrl(),
                        database.getUsername(), database.getPassword()));
                assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES", Integer.class) > 0);
            }
            try (var restarted = start(false)) {
                assertAdministrator(client, restarted);
                String token = csrf(get(client, restarted, "/admin/users").body());
                var logout = client.send(HttpRequest.newBuilder(uri(restarted, "/logout"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("_csrf=" + URLEncoder.encode(token, StandardCharsets.UTF_8)))
                        .build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(302, logout.statusCode());
                assertEquals(302, get(client, restarted, "/user").statusCode());
            }
        }
    }

    private TestServer start(boolean bootstrap) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-jar", Path.of(System.getProperty("application.jar")).toAbsolutePath().toString(),
                "--spring.config.location=classpath:/application.properties", "--spring.config.import=",
                "--spring.profiles.active=production", "--knowledgeroot.demo-data.enabled=false", "--server.port=0",
                "--spring.datasource.url=" + database.getJdbcUrl(),
                "--spring.datasource.username=" + database.getUsername(),
                "--spring.datasource.password=" + database.getPassword(),
                "--knowledgeroot.bootstrap.login=" + (bootstrap ? "smoke.admin" : ""),
                "--knowledgeroot.bootstrap.password=" + (bootstrap ? "smoke-test-password-2026" : ""),
                "--knowledgeroot.storage.driver=minio", "--file.storage-dir=" + files,
                "--minio.url=http://" + storage.getHost() + ":" + storage.getMappedPort(9000),
                "--minio.access-key=smoke-test-user", "--minio.secret-key=smoke-test-password",
                "--minio.bucket=smoke-test", "--spring.main.banner-mode=off"));
        Path log = files.resolve(bootstrap ? "first-start.log" : "restart.log");
        Process process = new ProcessBuilder(command).directory(files.toFile()).redirectErrorStream(true)
                .redirectOutput(log.toFile()).start();
        var portPattern = Pattern.compile("Tomcat started on port ([0-9]+)");
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        try {
            while (process.isAlive() && System.nanoTime() < deadline) {
                var matcher = portPattern.matcher(Files.readString(log));
                if (matcher.find()) return new TestServer(process, Integer.parseInt(matcher.group(1)));
                Thread.sleep(100);
            }
            fail("Packaged application did not start:\n" + Files.readString(log));
            throw new IllegalStateException("Unreachable");
        } catch (Throwable failure) {
            stop(process);
            throw failure;
        }
    }

    private void assertAdministrator(HttpClient client, TestServer context) throws Exception {
        var users = get(client, context, "/user");
        assertEquals(200, users.statusCode(), users.body());
        assertTrue(users.body().contains("smoke.admin"));
        assertFalse(users.body().contains("password"));
        assertEquals(200, get(client, context, "/admin/users").statusCode());
    }

    private URI uri(TestServer context, String path) {
        return URI.create("http://localhost:" + context.port() + path);
    }

    private HttpResponse<String> get(HttpClient client, TestServer context, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(context, path)).timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String csrf(String html) {
        var matcher = Pattern.compile("name=\"_csrf\"[^>]*(?:value|content)=\"([^\"]+)\"").matcher(html);
        assertTrue(matcher.find(), "Rendered form must contain a CSRF token");
        return matcher.group(1);
    }

    private static void stop(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Application process did not stop");
        }
    }

    private record TestServer(Process process, int port) implements AutoCloseable {
        @Override public void close() throws InterruptedException { stop(process); }
    }
}
