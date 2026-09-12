package org.knowledgeroot.app.config;

import org.junit.jupiter.api.Test;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.RequestOptions;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.FilePayload;
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
                browserEditingAndUploads(context);
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

    private void browserEditingAndUploads(TestServer server) throws Exception {
        // The CLI calls System.exit, so invoke it in its own process, never inside the test JVM.
        Path arguments = files.resolve("playwright.args");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Files.writeString(arguments, "-cp\n\"" + classpath.replace('\\', '/')
                + "\"\ncom.microsoft.playwright.CLI\ninstall\nchromium\n");
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        Path installLog = files.resolve("playwright-install.log");
        var installer = new ProcessBuilder(java, "@" + arguments).redirectErrorStream(true)
                .redirectOutput(installLog.toFile()).start();
        try {
            assertTrue(installer.waitFor(180, TimeUnit.SECONDS), "Chromium installation timed out");
            assertEquals(0, installer.exitValue(), () -> {
                try { return Files.readString(installLog); }
                catch (Exception e) { return "Chromium installation failed: " + e.getMessage(); }
            });
        } finally { stop(installer); }
        try (var playwright = Playwright.create(); var browser = playwright.chromium().launch();
             var context = browser.newContext()) {
            var page = context.newPage();
            var errors = new ArrayList<String>();
            page.onPageError(errors::add);
            page.onResponse(response -> {
                if (response.status() >= 400 && List.of("script", "stylesheet").contains(response.request().resourceType()))
                    errors.add(response.url() + ": " + response.status());
            });
            context.route("https://fonts.googleapis.com/**", route -> route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/css").setBody("")));
            page.navigate(uri(server, "/login").toString());
            page.locator("#loginUsername").fill("smoke.admin");
            page.locator("#loginPassword").fill("smoke-test-password-2026");
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
            page.waitForURL(uri(server, "/").toString());
            page.navigate(uri(server, "/ui/page/new").toString());
            page.waitForSelector(".tiptap");
            page.locator("#page-name").fill("Browser migration test");
            page.locator(".tiptap").fill("New editor content");
            page.locator("#content form button[type=submit]").first().click();
            page.waitForSelector("#content a[hx-get$='/edit']");
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()));
            int pageId = jdbc.queryForObject("SELECT id FROM page WHERE name = 'Browser migration test'", Integer.class);
            assertTrue(jdbc.queryForObject("SELECT content FROM page WHERE id = ?", String.class, pageId).contains("New editor content"));

            // Load persisted, older rich text into the replacement editor without losing its structure.
            jdbc.update("UPDATE page SET content = ? WHERE id = ?",
                    "<h2>Existing heading</h2><p><strong>Bold</strong> <em>italic</em></p>"
                            + "<table><tbody><tr><td>Existing cell</td></tr></tbody></table>"
                            + "<p style='text-align:center;color:red'>Styled paragraph</p>"
                            + "<p><a href='https://example.org'>Existing link</a></p>"
                            + "<figure><img src='/favicon.ico' alt='Existing picture'></figure>", pageId);
            for (int round = 0; round < 3; round++) {
                page.locator("#content a[hx-get$='/edit']").first().click();
                page.waitForSelector(".tiptap");
                assertEquals(1, page.locator(".tiptap").count());
                assertTrue(page.locator(".tiptap").innerText().contains("Existing cell"));
                if (round == 0) {
                    page.locator("input[name=name]").fill("   ");
                    page.locator("#content form button[type=submit]").first().click();
                    page.waitForSelector(".kr-request-error");
                    assertTrue(page.locator(".kr-request-error").innerText().contains("check your input"));
                    assertEquals(1, page.locator(".tiptap").count());
                    assertEquals("Browser migration test", jdbc.queryForObject("SELECT name FROM page WHERE id = ?", String.class, pageId));
                    page.locator("input[name=name]").fill("Browser migration test");
                }
                if (round == 0) page.screenshot(new Page.ScreenshotOptions().setFullPage(true)
                        .setPath(Path.of(System.getProperty("application.jar")).getParent().resolve("editor-smoke.png")));
                page.locator(".tiptap").evaluate("element => { const data = new DataTransfer(); "
                        + "data.setData('text/html', '<p>Safe paste<img src=x onerror=window.editorXss=1></p><script>window.editorXss=1</script>');"
                        + "element.dispatchEvent(new ClipboardEvent('paste', {clipboardData:data, bubbles:true, cancelable:true})); }");
                assertNull(page.evaluate("window.editorXss"));
                page.locator("#content form button[type=submit]").first().click();
                page.waitForSelector("#content a[hx-get$='/edit']");
                assertEquals(0, page.locator(".tiptap").count());
            }
            String persisted = jdbc.queryForObject("SELECT content FROM page WHERE id = ?", String.class, pageId);
            assertTrue(persisted.contains("Existing cell"), persisted);
            assertTrue(persisted.contains("<strong>Bold</strong>"), persisted);
            assertTrue(persisted.contains("text-align:center"), persisted);
            assertTrue(persisted.contains("color:red"), persisted);
            assertTrue(persisted.contains("https://example.org"), persisted);
            assertTrue(persisted.contains("/favicon.ico"), persisted);
            assertFalse(persisted.contains("onerror"), persisted);
            assertFalse(persisted.contains("<script"), persisted);

            // Real multipart upload through the application and byte-for-byte download through the new SDK.
            String csrfToken = page.locator("meta[name=_csrf]").getAttribute("content");
            String csrfHeader = page.locator("meta[name=_csrf_header]").getAttribute("content");
            byte[] content = "Storage upgrade roundtrip: äöü\n".getBytes(StandardCharsets.UTF_8);
            var upload = context.request().post(uri(server, "/file").toString(), RequestOptions.create()
                    .setHeader(csrfHeader, csrfToken).setMultipart(FormData.create().set("parentContent", pageId)
                            .set("file", new FilePayload("roundtrip.txt", "text/plain", content))));
            assertEquals(201, upload.status(), upload.text());
            int fileId = jdbc.queryForObject("SELECT id FROM file WHERE page_id = ?", Integer.class, pageId);
            var download = context.request().get(uri(server, "/ui/file/" + fileId + "/download/roundtrip.txt").toString());
            assertEquals(200, download.status());
            assertArrayEquals(content, download.body());
            for (int index = 0; index < 22; index++) {
                jdbc.update("INSERT INTO page (name, content, active, create_date, change_date) VALUES (?, ?, TRUE, NOW(), NOW())",
                        "Pagination fixture " + index, "<p>pagination needle</p><img src='/favicon.ico'>");
            }
            page.navigate(uri(server, "/search?q=pagination").toString());
            page.waitForSelector(".kr-result");
            assertEquals(20, page.locator(".kr-result").count());
            assertEquals(0, page.locator(".kr-r-snip img").count());
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Next").setExact(true)).click();
            page.waitForURL("**/search?*start=20*");
            assertEquals(2, page.locator(".kr-result").count());
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Previous").setExact(true)).click();
            page.waitForURL("**/search?*start=0*");
            assertEquals(20, page.locator(".kr-result").count());
            assertTrue(errors.isEmpty(), errors.toString());
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
