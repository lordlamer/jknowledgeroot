package org.knowledgeroot.app.page.ui;

import com.jayway.jsonpath.JsonPath;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The MCP server end to end against a real port: switch, metadata, dynamic client registration,
 * the OAuth flow through the form login and the consent page, the tools as different users, and
 * the ways a connection ends (disconnect, password change, switch-off).
 */
class McpIntegrationTest extends McpTestSupport {

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
            String page = ok(call(mcp, "get_page", Map.of("id", pageId, "format", "html")));
            assertEquals("original", JsonPath.read(page, "$.content"));
            assertEquals("html", JsonPath.read(page, "$.format"));
            assertEquals(false, JsonPath.read(page, "$.canEdit"));
            assertEquals(base() + "/ui/page/" + pageId, JsonPath.read(page, "$.url"));
            var search = ok(call(mcp, "search_pages", Map.of("query", "protected-")));
            assertTrue(JsonPath.<List<Integer>>read(search, "$.hits[*].id").contains(pageId), search);
            assertFalse(JsonPath.<List<Object>>read(ok(call(mcp, "list_spaces", Map.of())), "$[*].key").isEmpty());
            assertTrue(error(call(mcp, "update_page", Map.of("id", pageId, "revision", 0, "content", "x"))).contains("may only read"));
            error(call(mcp, "add_comment", Map.of("page", pageId, "text", "hi")));
        }

        // An outsider sees neither the page nor its existence.
        var outsider = connect(browser(), this.outsider, clientId, true);
        try (var mcp = mcp(outsider.access())) {
            assertTrue(error(call(mcp, "get_page", Map.of("id", pageId))).contains("not found or you may not read"));
            error(call(mcp, "create_page", Map.of("name", "No", "content", "", "parent", pageId)));
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
            String created = ok(call(mcp, "create_page", Map.of("name", "From Claude", "content", "<p>hello</p><script>x()</script>",
                    "parent", pageId, "labels", List.of("ai"), "format", "html")));
            child = JsonPath.read(created, "$.id");
            assertEquals("<p>hello</p>", jdbc.queryForObject("SELECT content FROM page WHERE id=?", String.class, child));
            assertEquals(editorId, jdbc.queryForObject("SELECT created_by FROM page WHERE id=?", Integer.class, child));
            assertTrue(error(call(mcp, "update_page", Map.of("id", child, "revision", 7, "content", "v2"))).contains("changed"));
            // Without publish=true the save only changes the working copy (R62): no new version, readers see nothing.
            String copied = ok(call(mcp, "update_page", Map.of("id", child, "revision", 0, "content", "<p>working copy</p>", "format", "html")));
            assertEquals(0, JsonPath.<Integer>read(copied, "$.revision"));
            assertTrue(JsonPath.<String>read(copied, "$.message").contains("Working copy"), copied);
            assertEquals("<p>hello</p>", jdbc.queryForObject("SELECT content FROM page WHERE id=?", String.class, child));
            assertEquals("<p>working copy</p>", jdbc.queryForObject("SELECT content FROM page_draft WHERE page_id=?", String.class, child));
            assertEquals(true, JsonPath.read(ok(call(mcp, "get_page", Map.of("id", child))), "$.hasWorkingCopy"));
            String published = ok(call(mcp, "publish_page", Map.of("id", child, "revision", 0)));
            assertEquals(1, JsonPath.<Integer>read(published, "$.revision"));
            assertEquals("<p>working copy</p>", jdbc.queryForObject("SELECT content FROM page WHERE id=?", String.class, child));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM page_draft WHERE page_id=?", Integer.class, child));
            String saved = ok(call(mcp, "update_page", Map.of("id", child, "revision", 1, "content", "<p>v2</p>", "format", "html", "publish", true)));
            assertEquals(2, JsonPath.<Integer>read(saved, "$.revision"));
            assertEquals("From Claude", jdbc.queryForObject("SELECT name FROM page WHERE id=?", String.class, child));
            // A draft page is created for editors only and becomes visible with publish_page.
            String drafted = ok(call(mcp, "create_page", Map.of("name", "Draft from Claude", "content", "soon", "parent", pageId, "draft", true)));
            int draftPage = JsonPath.read(drafted, "$.id");
            assertEquals(false, JsonPath.read(ok(call(mcp, "get_page", Map.of("id", draftPage))), "$.published"));
            assertFalse(login(this.reader).perform(get("/api/v1/tree?parent=" + pageId)).andReturn().getResponse().getContentAsString().contains("Draft from Claude"));
            ok(call(mcp, "publish_page", Map.of("id", draftPage, "revision", 0)));
            assertTrue(login(this.reader).perform(get("/api/v1/tree?parent=" + pageId)).andReturn().getResponse().getContentAsString().contains("Draft from Claude"));
            ok(call(mcp, "add_comment", Map.of("page", child, "text", "Looks good")));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM page_comment WHERE page_id=? AND user_id=?", Integer.class, child, editorId));
            var listed = ok(call(mcp, "list_pages", Map.of("parent", pageId)));
            assertTrue(JsonPath.<List<Integer>>read(listed, "$.pages[*].id").contains(child), listed);
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
