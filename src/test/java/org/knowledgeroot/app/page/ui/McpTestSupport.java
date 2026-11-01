package org.knowledgeroot.app.page.ui;

import com.jayway.jsonpath.JsonPath;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Shared plumbing of the MCP tests against a real port: an HTTP client with cookies, dynamic
 * client registration, the authorization code flow through login and consent, and an MCP client.
 */
@SpringBootTest(classes = org.knowledgeroot.KnowledgerootApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=classpath:/application.properties", "spring.config.import=", "spring.profiles.active=test",
        "knowledgeroot.demo-data.enabled=false", "knowledgeroot.bootstrap.login=integration.admin",
        "knowledgeroot.bootstrap.password=integration-password-2026", "knowledgeroot.storage.driver=file",
        "knowledgeroot.login.source-attempts=10000", "knowledgeroot.login.account-attempts=10000"})
@AutoConfigureMockMvc
abstract class McpTestSupport extends IsolatedApplicationTest {
    static final String REDIRECT_URI = "https://claude.ai/api/mcp/auth_callback";
    @LocalServerPort int port;

    String base() { return "http://localhost:" + port; }

    /** A browser-like client: cookies, no automatic redirects. */
    HttpClient browser() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build();
    }

    HttpResponse<String> send(HttpClient client, HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> fetch(HttpClient client, String url, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).GET();
        for (int i = 0; i + 1 < headers.length; i += 2) request.header(headers[i], headers[i + 1]);
        return send(client, request.build());
    }

    HttpResponse<String> postForm(HttpClient client, String url, Map<String, List<String>> fields) throws Exception {
        var body = new StringBuilder();
        fields.forEach((name, values) -> values.forEach(value -> body.append(body.isEmpty() ? "" : "&")
                .append(URLEncoder.encode(name, StandardCharsets.UTF_8)).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8))));
        return send(client, HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build());
    }

    HttpResponse<String> postJson(HttpClient client, String url, String json, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream").POST(HttpRequest.BodyPublishers.ofString(json));
        for (int i = 0; i + 1 < headers.length; i += 2) request.header(headers[i], headers[i + 1]);
        return send(client, request.build());
    }

    static Map<String, List<String>> form(String... pairs) {
        var fields = new LinkedHashMap<String, List<String>>();
        for (int i = 0; i + 1 < pairs.length; i += 2) fields.computeIfAbsent(pairs[i], key -> new java.util.ArrayList<>()).add(pairs[i + 1]);
        return fields;
    }

    static String location(HttpResponse<String> response) {
        assertEquals(302, response.statusCode(), "expected a redirect but got " + response.statusCode() + ": " + response.body());
        return response.headers().firstValue("Location").orElseThrow();
    }

    static String csrf(String html) {
        var matcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertTrue(matcher.find(), "no CSRF token in page");
        return matcher.group(1);
    }

    static String verifier() {
        byte[] random = new byte[48];
        new SecureRandom().nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    static String challenge(String verifier) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    }

    String page(Client client, String path) throws Exception {
        return client.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    void switchMcp(boolean enabled, String hosts) throws Exception {
        var admin = login("integration.admin");
        var request = post("/admin/settings").param("mfaPolicy", "off").param("mcpRedirectHosts", hosts);
        if (enabled) request.param("mcpEnabled", "on");
        admin.perform(request).andExpect(redirectedUrl("/admin/settings?saved"));
    }

    String register(HttpClient client, String name, String redirectUri) throws Exception {
        var response = postJson(client, base() + "/oauth2/register", "{\"client_name\":\"" + name + "\",\"redirect_uris\":[\"" + redirectUri
                + "\"],\"token_endpoint_auth_method\":\"none\",\"grant_types\":[\"authorization_code\",\"refresh_token\"],\"response_types\":[\"code\"]}");
        assertEquals(201, response.statusCode(), response.body());
        assertEquals("none", JsonPath.read(response.body(), "$.token_endpoint_auth_method"));
        return JsonPath.read(response.body(), "$.client_id");
    }

    /** The authorization code flow of an assistant: sign-in form, consent page, code. */
    record Tokens(String access, String refresh, String scope) {}

    Tokens connect(HttpClient browser, String login, String clientId, boolean allowWrite) throws Exception {
        String verifier = verifier();
        String authorize = base() + "/oauth2/authorize?response_type=code&client_id=" + clientId + "&redirect_uri="
                + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8) + "&scope=mcp%3Aread%20mcp%3Awrite&state=st4te"
                + "&code_challenge=" + challenge(verifier) + "&code_challenge_method=S256&resource="
                + URLEncoder.encode(base() + "/mcp", StandardCharsets.UTF_8);
        String next = location(fetch(browser, authorize, "Accept", "text/html"));
        if (next.contains("/login")) {
            var loginPage = fetch(browser, base() + "/login", "Accept", "text/html");
            assertEquals(200, loginPage.statusCode());
            assertEquals(base() + "/login/success", location(postForm(browser, base() + "/logmein",
                    form("username", login, "password", PASSWORD, "_csrf", csrf(loginPage.body())))));
            next = location(fetch(browser, base() + "/login/success", "Accept", "text/html"));
            assertTrue(next.contains("/oauth2/authorize"), "the sign-in continues the assistant's flow: " + next);
            next = location(fetch(browser, next, "Accept", "text/html"));
        }
        assertTrue(next.contains("/oauth2/consent"), "expected the consent page: " + next);
        var consent = fetch(browser, next, "Accept", "text/html");
        assertEquals(200, consent.statusCode());
        assertTrue(consent.body().contains("Allow access?") && consent.body().contains("wants to use Knowledgeroot as <strong>" + login + "</strong>"), consent.body());
        // The consent form carries the server-side state of the pending authorization, not the client's.
        String consentState = java.net.URLDecoder.decode(Pattern.compile("[?&]state=([^&]+)").matcher(next).results().findFirst().orElseThrow().group(1), StandardCharsets.UTF_8);
        assertTrue(consent.body().contains("name=\"state\" value=\"" + consentState.replace("&", "&amp;") + "\""), consent.body());
        var approved = allowWrite ? form("client_id", clientId, "state", consentState, "scope", "mcp:read", "scope", "mcp:write")
                : form("client_id", clientId, "state", consentState, "scope", "mcp:read");
        String callback = location(postForm(browser, base() + "/oauth2/authorize", approved));
        assertTrue(callback.startsWith(REDIRECT_URI + "?") && callback.contains("state=st4te"), callback);
        String code = Pattern.compile("code=([^&]+)").matcher(callback).results().findFirst().orElseThrow().group(1);
        var token = postForm(browser(), base() + "/oauth2/token", form("grant_type", "authorization_code", "code", code,
                "redirect_uri", REDIRECT_URI, "client_id", clientId, "code_verifier", verifier));
        assertEquals(200, token.statusCode(), token.body());
        return new Tokens(JsonPath.read(token.body(), "$.access_token"), JsonPath.read(token.body(), "$.refresh_token"),
                JsonPath.read(token.body(), "$.scope"));
    }

    HttpResponse<String> refresh(String clientId, String refreshToken) throws Exception {
        return postForm(browser(), base() + "/oauth2/token", form("grant_type", "refresh_token", "refresh_token", refreshToken, "client_id", clientId));
    }

    McpSyncClient mcp(String accessToken) {
        var transport = HttpClientStreamableHttpTransport.builder(base()).endpoint("/mcp")
                .httpRequestCustomizer((builder, method, uri, body, context) -> builder.header("Authorization", "Bearer " + accessToken))
                .build();
        var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).build();
        client.initialize();
        return client;
    }

    static String text(McpSchema.CallToolResult result) {
        return result.content().stream().filter(McpSchema.TextContent.class::isInstance)
                .map(content -> ((McpSchema.TextContent) content).text()).findFirst().orElse("");
    }

    static McpSchema.CallToolResult call(McpSyncClient client, String tool, Map<String, Object> arguments) {
        return client.callTool(new McpSchema.CallToolRequest(tool, arguments));
    }

    /** The result of a call that must succeed, as JSON text. */
    static String ok(McpSchema.CallToolResult result) {
        assertFalse(Boolean.TRUE.equals(result.isError()), text(result));
        return text(result);
    }

    static String error(McpSchema.CallToolResult result) {
        assertTrue(Boolean.TRUE.equals(result.isError()), "expected a tool error but got: " + text(result));
        return text(result);
    }
}
