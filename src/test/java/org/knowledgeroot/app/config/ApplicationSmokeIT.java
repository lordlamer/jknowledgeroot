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
    private javax.net.ssl.SSLContext tls;

    @Test
    void applicationStartsAndKeepsAuthenticatedJdbcSessionAfterRestart() throws Exception {
        prepareProductionDatabaseAndTls();
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        try (var client = HttpClient.newBuilder().cookieHandler(cookies)
                .sslContext(tls)
                .connectTimeout(Duration.ofSeconds(10)).build()) {
            try (var context = start(true)) {
                var login = get(client, context, "/login");
                assertEquals(200, login.statusCode());
                assertTrue(login.body().contains("Sign in"));
                assertTrue(login.headers().firstValue("strict-transport-security").isPresent());
                assertEquals(200, get(client, context, "/webjars/htmx.org/dist/htmx.min.js").statusCode());
                assertEquals(200, get(client, context, "/webjars/bootstrap/css/bootstrap.min.css").statusCode());
                String form = "username=smoke.admin&password=smoke-test-password-2026&_csrf="
                        + URLEncoder.encode(csrf(login.body()), StandardCharsets.UTF_8);
                var authenticated = client.send(HttpRequest.newBuilder(uri(context, "/logmein"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(302, authenticated.statusCode());
                assertTrue(authenticated.headers().allValues("set-cookie").stream().anyMatch(value ->
                        value.startsWith("SESSION=") && value.contains("Secure") && value.contains("HttpOnly") && value.contains("SameSite=Lax")));
                assertTrue(authenticated.headers().firstValue("location").orElseThrow().endsWith("/login/success"));
                assertAdministrator(client, context);
                browserEditingAndUploads(context);
                monitoringAndDependencyRecovery(client, context);
                var jdbc = new JdbcTemplate(new DriverManagerDataSource(database.getJdbcUrl(),
                        database.getUsername(), database.getPassword()));
                assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES", Integer.class) > 0);
                assertThrows(org.springframework.dao.DataAccessException.class,
                        () -> jdbc.execute("CREATE TABLE forbidden_runtime_ddl (id INT)"));
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
                assertForwardedAddress(client, restarted, false);
            }
            try (var local = start(false, "file")) {
                assertForwardedAddress(client, local, true);
                localStorageAndUploadLimits(local);
                Path objects = files.resolve("objects");
                Path offline = files.resolve("objects-offline");
                Files.move(objects, offline);
                try {
                    assertProbe(client, local, "readiness", 503, "DOWN");
                    assertProbe(client, local, "liveness", 200, "UP");
                } finally { Files.move(offline, objects); }
                assertProbe(client, local, "readiness", 200, "UP");
            }
        }
    }

    private void monitoringAndDependencyRecovery(HttpClient authenticated, TestServer server) throws Exception {
        assertEquals(200, get(authenticated, server, "/actuator/metrics/jvm.memory.used").statusCode());
        assertEquals(200, get(authenticated, server, "/actuator/metrics/http.server.requests").statusCode());
        assertEquals(404, get(authenticated, server, "/actuator/env").statusCode());
        try (var anonymous = HttpClient.newBuilder().sslContext(tls).connectTimeout(Duration.ofSeconds(5)).build()) {
            assertProbe(anonymous, server, "readiness", 200, "UP");
            assertEquals(302, get(anonymous, server, "/actuator/metrics").statusCode());
            for (var dependency : List.of(storage, database)) {
                dependency.getDockerClient().pauseContainerCmd(dependency.getContainerId()).exec();
                try {
                    assertProbe(anonymous, server, "readiness", 503, "DOWN");
                    assertProbe(authenticated, server, "liveness", 200, "UP");
                } finally { dependency.getDockerClient().unpauseContainerCmd(dependency.getContainerId()).exec(); }
                assertProbe(anonymous, server, "readiness", 200, "UP");
                assertEquals(200, get(authenticated, server, "/actuator/metrics/jvm.memory.used").statusCode());
            }
        }
    }

    private void assertProbe(HttpClient client, TestServer server, String group, int status, String state) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        HttpResponse<String> response;
        do {
            response = get(client, server, "/actuator/health/" + group);
            assertTrue(response.headers().allValues("set-cookie").isEmpty(), "Probes must leave login cookies untouched");
            if (response.statusCode() == status && response.body().equals("{\"status\":\"" + state + "\"}")) return;
            Thread.sleep(300);
        } while (System.nanoTime() < deadline);
        assertEquals(status,response.statusCode(),response.body());
        assertEquals("{\"status\":\"" + state + "\"}",response.body());
    }

    private void assertForwardedAddress(HttpClient client, TestServer server, boolean trusted) throws Exception {
        String source = trusted ? "198.51.100.11" : "198.51.100.10";
        String token = csrf(get(client, server, "/login").body());
        var response = client.send(HttpRequest.newBuilder(uri(server, "/logmein"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Forwarded-For", "203.0.113.7, " + source).header("X-Forwarded-Proto", "https")
                .POST(HttpRequest.BodyPublishers.ofString("username=proxy.probe&password=incorrect&_csrf="
                        + URLEncoder.encode(token, StandardCharsets.UTF_8))).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(302, response.statusCode());
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()));
        assertEquals(trusted ? 1 : 0, jdbc.queryForObject("SELECT COUNT(*) FROM login_attempt WHERE bucket_key = ?", Integer.class,
                org.knowledgeroot.app.security.auth.PasswordService.credentialTag("source:" + source)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM login_attempt WHERE bucket_key = ?", Integer.class,
                org.knowledgeroot.app.security.auth.PasswordService.credentialTag("source:203.0.113.7")));
    }

    private void prepareProductionDatabaseAndTls() throws Exception {
        var root = new JdbcTemplate(new DriverManagerDataSource(database.getJdbcUrl(), "root", database.getPassword()));
        root.execute("CREATE USER 'smoke_migrator'@'%' IDENTIFIED BY 'smoke-migration-password'");
        root.execute("GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES ON test.* TO 'smoke_migrator'@'%'");
        root.execute("REVOKE ALL PRIVILEGES ON test.* FROM 'test'@'%'");
        root.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON test.* TO 'test'@'%'");
        Path keyStore = files.resolve("test-tls.p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool").toString();
        var generator = new ProcessBuilder(keytool, "-genkeypair", "-alias", "localhost", "-keyalg", "RSA",
                "-storetype", "PKCS12", "-keystore", keyStore.toString(), "-storepass", "test-certificate-password",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-validity", "2", "-noprompt")
                .redirectErrorStream(true).redirectOutput(files.resolve("keytool.log").toFile()).start();
        try { assertTrue(generator.waitFor(30, TimeUnit.SECONDS)); assertEquals(0, generator.exitValue()); }
        finally { if (generator.isAlive()) stop(generator); }
        var keys = java.security.KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(keyStore)) { keys.load(input, "test-certificate-password".toCharArray()); }
        var trust = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        trust.init(keys);
        tls = javax.net.ssl.SSLContext.getInstance("TLS");
        tls.init(null, trust.getTrustManagers(), null);
    }

    private void localStorageAndUploadLimits(TestServer server) {
        try (var playwright = Playwright.create(); var browser = playwright.chromium().launch();
             var context = browser.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true))) {
            var page = context.newPage();
            page.navigate(uri(server, "/login").toString());
            page.locator("#loginUsername").fill("smoke.admin");
            page.locator("#loginPassword").fill("smoke-test-password-2026");
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
            page.waitForURL(uri(server, "/").toString());
            String token = page.locator("meta[name=_csrf]").getAttribute("content");
            String header = page.locator("meta[name=_csrf_header]").getAttribute("content");
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword()));
            int pageId = jdbc.queryForObject("SELECT id FROM page WHERE name = 'Browser migration test'", Integer.class);
            int before = jdbc.queryForObject("SELECT COUNT(*) FROM file", Integer.class);
            byte[] content = new byte[1024];
            new java.util.Random(41).nextBytes(content);
            var upload = context.request().post(uri(server, "/file").toString(), RequestOptions.create()
                    .setHeader(header, token).setMultipart(FormData.create().set("parentContent", pageId)
                            .set("file", new FilePayload("local.txt", "text/plain", content))));
            assertEquals(201, upload.status(), upload.text());
            int fileId = jdbc.queryForObject("SELECT id FROM file WHERE name = 'local.txt'", Integer.class);
            var download = context.request().get(uri(server, "/file/" + fileId + "/download/local.txt").toString());
            assertEquals(200, download.status(), download.text());
            assertArrayEquals(content, download.body());
            assertEquals("nosniff", download.headers().get("x-content-type-options"));
            assertTrue(download.headers().get("content-disposition").contains("filename*="));
            var oversized = context.request().post(uri(server, "/file").toString(), RequestOptions.create()
                    .setHeader(header, token).setMultipart(FormData.create().set("parentContent", pageId)
                            .set("file", new FilePayload("oversized.txt", "text/plain", new byte[1025]))));
            assertEquals(413, oversized.status(), oversized.text());
            assertEquals(before + 1, jdbc.queryForObject("SELECT COUNT(*) FROM file", Integer.class));
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
             var context = browser.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true))) {
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

            // Two real editor tabs must not silently overwrite one another.
            page.locator("#content a[hx-get$='/edit']").first().click();
            page.waitForSelector(".tiptap");
            page.locator(".tiptap").fill("Unsaved local draft");
            try (var second = context.newPage()) {
                second.navigate(uri(server, "/ui/page/" + pageId + "/edit").toString());
                second.waitForSelector(".tiptap");
                second.locator(".tiptap").fill("Saved in another tab");
                second.locator("#content form button[type=submit]").first().click();
                second.waitForSelector("#content a[hx-get$='/edit']");
            }
            String localDraft = page.locator(".tiptap").innerHTML();
            page.locator("#content form button[type=submit]").first().click();
            page.waitForSelector(".kr-request-error");
            assertTrue(page.locator(".kr-request-error").innerText().contains("page has changed"));
            assertEquals(localDraft, page.locator(".tiptap").innerHTML());
            assertTrue(page.locator(".tiptap").innerText().contains("Unsaved local draft"));
            assertTrue(jdbc.queryForObject("SELECT content FROM page WHERE id=?",String.class,pageId).contains("Saved in another tab"));
            page.navigate(uri(server, "/ui/page/" + pageId + "/history").toString());
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("View version")).first().click();
            page.waitForSelector("article.kr-content");
            assertTrue(page.locator("article.kr-content").innerText().contains("Existing cell"));
            page.screenshot(new Page.ScreenshotOptions().setFullPage(true)
                    .setPath(Path.of(System.getProperty("application.jar")).getParent().resolve("history-smoke.png")));
            page.onDialog(Dialog::accept);
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Restore this version")).click();
            page.waitForSelector("#content a[hx-get$='/edit']");
            assertTrue(jdbc.queryForObject("SELECT content FROM page WHERE id=?",String.class,pageId).contains("Existing cell"));

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
        return start(bootstrap, "minio");
    }

    private TestServer start(boolean bootstrap, String driver) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-jar", Path.of(System.getProperty("application.jar")).toAbsolutePath().toString(),
                "--spring.config.location=classpath:/application.properties", "--spring.config.import=",
                "--spring.profiles.active=production", "--knowledgeroot.demo-data.enabled=false", "--server.port=0",
                "--spring.datasource.url=" + database.getJdbcUrl(),
                "--spring.datasource.username=" + database.getUsername(),
                "--spring.datasource.password=" + database.getPassword(),
                "--knowledgeroot.migration.username=smoke_migrator", "--knowledgeroot.migration.password=smoke-migration-password",
                "--server.ssl.enabled=true", "--server.ssl.key-store=" + files.resolve("test-tls.p12").toUri(),
                "--server.ssl.key-store-password=test-certificate-password", "--server.ssl.key-store-type=PKCS12",
                "--knowledgeroot.bootstrap.login=" + (bootstrap ? "smoke.admin" : ""),
                "--knowledgeroot.bootstrap.password=" + (bootstrap ? "smoke-test-password-2026" : ""),
                "--knowledgeroot.storage.driver=" + driver, "--file.storage-dir=" + files.resolve("objects"),
                "--minio.url=" + (driver.equals("file") ? "invalid" : "http://" + storage.getHost() + ":" + storage.getMappedPort(9000)),
                "--minio.access-key=smoke-test-user", "--minio.secret-key=smoke-test-password",
                "--minio.bucket=smoke-test", "--spring.main.banner-mode=off"));
        if (driver.equals("file")) command.addAll(List.of("--spring.servlet.multipart.max-file-size=1KB",
                "--spring.servlet.multipart.max-request-size=4KB", "--server.forward-headers-strategy=native",
                "--server.tomcat.remoteip.internal-proxies=127[.]0[.]0[.]1|0:0:0:0:0:0:0:1|::1"));
        Path log = files.resolve(driver + (bootstrap ? "-first-start.log" : "-restart.log"));
        Process process = new ProcessBuilder(command).directory(files.toFile()).redirectErrorStream(true)
                .redirectOutput(log.toFile()).start();
        var portPattern = Pattern.compile("Tomcat started on port ([0-9]+)");
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        try {
            while (process.isAlive() && System.nanoTime() < deadline) {
                var matcher = portPattern.matcher(Files.readString(log));
                if (matcher.find()) {
                    assertFalse(Files.readString(log).contains("password="), "Startup log must not expose a JDBC password");
                    return new TestServer(process, Integer.parseInt(matcher.group(1)), log);
                }
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
        return URI.create("https://localhost:" + context.port() + path);
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

    private record TestServer(Process process, int port, Path log) implements AutoCloseable {
        @Override public void close() throws Exception {
            stop(process);
            Files.copy(log, Path.of(System.getProperty("application.jar")).getParent().resolve("smoke-" + log.getFileName()),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
