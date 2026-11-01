package org.knowledgeroot.app.page.ui;

import com.jayway.jsonpath.JsonPath;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
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
 * The MCP server end to end against a real port: switch, metadata, dynamic client registration,
 * the OAuth flow through the form login and the consent page, the tools as different users, and
 * the ways a connection ends (disconnect, password change, switch-off).
 */
@SpringBootTest(classes = org.knowledgeroot.KnowledgerootApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=classpath:/application.properties", "spring.config.import=", "spring.profiles.active=test",
        "knowledgeroot.demo-data.enabled=false", "knowledgeroot.bootstrap.login=integration.admin",
        "knowledgeroot.bootstrap.password=integration-password-2026", "knowledgeroot.storage.driver=file",
        "knowledgeroot.login.source-attempts=10000", "knowledgeroot.login.account-attempts=10000"})
@AutoConfigureMockMvc
class McpIntegrationTest extends IsolatedApplicationTest {
    private static final String REDIRECT_URI = "https://claude.ai/api/mcp/auth_callback";
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

    @Test void switchedOffServerDoesNotExistAndSettingsValidateHosts() throws Exception {
        switchMcp(false, "claude.ai\n*.claude.ai");
        var client = browser();
        assertEquals(404, postJson(client, base() + "/mcp", "{}").statusCode());
        assertEquals(404, fetch(client, base() + "/.well-known/oauth-authorization-server").statusCode());
        assertEquals(404, fetch(client, base() + "/.well-known/oauth-protected-resource/mcp").statusCode());
        assertEquals(404, postJson(client, base() + "/oauth2/register", "{}").statusCode());
        var admin = login("integration.admin");
        admin.perform(post("/admin/settings").param("mfaPolicy", "off").param("mcpEnabled", "on").param("mcpRedirectHosts", "not a host!"))
                .andExpect(status().isBadRequest());
        assertTrue(page(admin, "/admin/settings").contains("id=\"mcp-enabled\" name=\"mcpEnabled\" value=\"on\">"), "the switch stays off after invalid input");
        login(editor).perform(get("/admin/settings")).andExpect(status().isForbidden());
    }

    @Test void assistantsConnectThroughOAuthAndUseTheToolsWithTheUsersPermissions() throws Exception {
        switchMcp(true, "claude.ai, *.claude.ai");
        var client = browser();

        // Discovery: the resource names its authorization server, the server its registration endpoint.
        var resourceMetadata = fetch(client, base() + "/.well-known/oauth-protected-resource/mcp");
        assertEquals(200, resourceMetadata.statusCode());
        assertEquals(base() + "/mcp", JsonPath.read(resourceMetadata.body(), "$.resource"));
        assertEquals(base(), JsonPath.read(resourceMetadata.body(), "$.authorization_servers[0]"));
        assertEquals(List.of("mcp:read", "mcp:write"), JsonPath.read(resourceMetadata.body(), "$.scopes_supported"));
        var serverMetadata = fetch(client, base() + "/.well-known/oauth-authorization-server");
        assertEquals(200, serverMetadata.statusCode());
        assertEquals(base() + "/oauth2/register", JsonPath.read(serverMetadata.body(), "$.registration_endpoint"));
        assertEquals(base() + "/oauth2/token", JsonPath.read(serverMetadata.body(), "$.token_endpoint"));
        assertTrue(JsonPath.<List<String>>read(serverMetadata.body(), "$.code_challenge_methods_supported").contains("S256"));
        var unauthenticated = postJson(client, base() + "/mcp", "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        assertEquals(401, unauthenticated.statusCode());
        assertTrue(unauthenticated.headers().firstValue("WWW-Authenticate").orElse("")
                .contains("resource_metadata=\"" + base() + "/.well-known/oauth-protected-resource/mcp\""));

        // Registration only for allow-listed hosts or localhost, and only as public client.
        var rejected = postJson(client, base() + "/oauth2/register",
                "{\"client_name\":\"Evil\",\"redirect_uris\":[\"https://evil.example.com/cb\"],\"token_endpoint_auth_method\":\"none\"}");
        assertEquals(400, rejected.statusCode(), rejected.body());
        assertEquals("invalid_redirect_uri", JsonPath.read(rejected.body(), "$.error"));
        assertEquals(400, postJson(client, base() + "/oauth2/register",
                "{\"client_name\":\"Secret\",\"redirect_uris\":[\"" + REDIRECT_URI + "\"],\"token_endpoint_auth_method\":\"client_secret_basic\"}").statusCode());
        register(client, "Local Tool", "http://localhost:51234/callback");
        String clientId = register(client, "Claude Test", REDIRECT_URI);

        // A reader connects read-only: reads the protected page, cannot write.
        var reader = connect(browser(), this.reader, clientId, false);
        assertEquals("mcp:read", reader.scope());
        try (var mcp = mcp(reader.access())) {
            assertTrue(mcp.listTools().tools().stream().map(McpSchema.Tool::name).toList().containsAll(
                    List.of("list_spaces", "search_pages", "get_page", "list_pages", "list_starred", "list_comments", "create_page", "update_page", "add_comment")));
            var page = call(mcp, "get_page", Map.of("id", pageId));
            assertFalse(Boolean.TRUE.equals(page.isError()), text(page));
            assertEquals("original", JsonPath.read(text(page), "$.contentHtml"));
            assertEquals(false, JsonPath.read(text(page), "$.canEdit"));
            assertEquals(base() + "/ui/page/" + pageId, JsonPath.read(text(page), "$.url"));
            var search = call(mcp, "search_pages", Map.of("query", "protected-"));
            assertTrue(JsonPath.<List<Integer>>read(text(search), "$.hits[*].id").contains(pageId), text(search));
            assertFalse(JsonPath.<List<Object>>read(text(call(mcp, "list_spaces", Map.of())), "$[*].key").isEmpty());
            var denied = call(mcp, "update_page", Map.of("id", pageId, "revision", 0, "content", "<p>x</p>"));
            assertTrue(Boolean.TRUE.equals(denied.isError()) && text(denied).contains("may only read"), text(denied));
            var comment = call(mcp, "add_comment", Map.of("page", pageId, "text", "hi"));
            assertTrue(Boolean.TRUE.equals(comment.isError()), text(comment));
        }

        // An outsider sees neither the page nor its existence.
        var outsider = connect(browser(), this.outsider, clientId, true);
        try (var mcp = mcp(outsider.access())) {
            var page = call(mcp, "get_page", Map.of("id", pageId));
            assertTrue(Boolean.TRUE.equals(page.isError()) && text(page).contains("not found or you may not read"), text(page));
            var child = call(mcp, "create_page", Map.of("name", "No", "content", "", "parent", pageId));
            assertTrue(Boolean.TRUE.equals(child.isError()), text(child));
        }

        // A public client revokes its refresh token with its client id alone (RFC 7009).
        var revoked = postForm(browser(), base() + "/oauth2/revoke", form("token", outsider.refresh(), "token_type_hint", "refresh_token", "client_id", clientId));
        assertEquals(200, revoked.statusCode(), revoked.body());
        assertEquals(400, refresh(clientId, outsider.refresh()).statusCode(), "a revoked refresh token is dead");

        // An editor who allowed writing creates, edits with revisions and comments.
        var editorBrowser = browser();
        var editor = connect(editorBrowser, this.editor, clientId, true);
        assertTrue(editor.scope().contains("mcp:write"));
        int child;
        try (var mcp = mcp(editor.access())) {
            var created = call(mcp, "create_page", Map.of("name", "From Claude", "content", "<p>hello</p><script>x()</script>",
                    "parent", pageId, "labels", List.of("ai")));
            assertFalse(Boolean.TRUE.equals(created.isError()), text(created));
            child = JsonPath.read(text(created), "$.id");
            assertEquals("<p>hello</p>", jdbc.queryForObject("SELECT content FROM page WHERE id=?", String.class, child));
            assertEquals(editorId, jdbc.queryForObject("SELECT created_by FROM page WHERE id=?", Integer.class, child));
            var stale = call(mcp, "update_page", Map.of("id", child, "revision", 7, "content", "<p>v2</p>"));
            assertTrue(Boolean.TRUE.equals(stale.isError()) && text(stale).contains("changed"), text(stale));
            var saved = call(mcp, "update_page", Map.of("id", child, "revision", 0, "content", "<p>v2</p>"));
            assertFalse(Boolean.TRUE.equals(saved.isError()), text(saved));
            assertEquals(1, JsonPath.<Integer>read(text(saved), "$.revision"));
            assertEquals("From Claude", jdbc.queryForObject("SELECT name FROM page WHERE id=?", String.class, child));
            var comment = call(mcp, "add_comment", Map.of("page", child, "text", "Looks good"));
            assertFalse(Boolean.TRUE.equals(comment.isError()), text(comment));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM page_comment WHERE page_id=? AND user_id=?", Integer.class, child, editorId));
            var listed = call(mcp, "list_pages", Map.of("parent", pageId));
            assertTrue(JsonPath.<List<Integer>>read(text(listed), "$.pages[*].id").contains(child), text(listed));
        }

        // A refreshed token keeps the scopes; the profile lists the connection and disconnecting ends it.
        var refreshed = refresh(clientId, editor.refresh());
        assertEquals(200, refreshed.statusCode(), refreshed.body());
        assertTrue(JsonPath.<String>read(refreshed.body(), "$.scope").contains("mcp:write"));
        assertEquals(400, refresh(clientId, editor.refresh()).statusCode(), "a rotated refresh token is spent");
        var profile = login(this.editor);
        String profilePage = page(profile, "/profile");
        assertTrue(profilePage.contains("Claude Test") && profilePage.contains("Read and write"), profilePage);
        String registeredId = jdbc.queryForObject("SELECT id FROM oauth2_registered_client WHERE client_id=?", String.class, clientId);
        profile.perform(post("/profile/connections/revoke").param("client", registeredId)).andExpect(redirectedUrl("/profile?disconnected"));
        assertFalse(page(profile, "/profile").contains("Claude Test"));
        var afterRevoke = refresh(clientId, JsonPath.read(refreshed.body(), "$.refresh_token"));
        assertEquals(400, afterRevoke.statusCode(), afterRevoke.body());

        // A changed password ends the reader's access and refresh at once.
        jdbc.update("UPDATE user SET password=CONCAT(password, 'x') WHERE id=?", readerId);
        var rejectedToken = postJson(browser(), base() + "/mcp", "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}",
                "Authorization", "Bearer " + reader.access());
        assertEquals(401, rejectedToken.statusCode());
        var rejectedRefresh = refresh(clientId, reader.refresh());
        assertEquals(400, rejectedRefresh.statusCode());
        assertEquals("invalid_grant", JsonPath.read(rejectedRefresh.body(), "$.error"));

        // Switching the server off ends every connection.
        switchMcp(false, "claude.ai\n*.claude.ai");
        assertEquals(404, postJson(browser(), base() + "/mcp", "{}", "Authorization", "Bearer " + outsider.access()).statusCode());
        assertEquals(404, fetch(editorBrowser, base() + "/oauth2/consent?client_id=" + clientId + "&state=x", "Accept", "text/html").statusCode(),
                "the consent page of a signed-in user is gone as well");
    }
}
