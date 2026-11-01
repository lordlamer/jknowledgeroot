package org.knowledgeroot.app.page.ui;

import com.jayway.jsonpath.JsonPath;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The content tools of the MCP server (Markdown, tree, labels, recent changes, attachments, history,
 * move, delete and restore), resources, prompts, the audit log and the administrators' page.
 */
class McpFeaturesIntegrationTest extends McpTestSupport {

    @Test void assistantsUseTheContentToolsResourcesAndPromptsAndAdministratorsSeeTheAudit() throws Exception {
        switchMcp(true, "claude.ai, *.claude.ai");
        String clientId = register(browser(), "Claude Features", REDIRECT_URI);
        var editor = connect(browser(), this.editor, clientId, true);
        int md, target, fileId;
        try (var mcp = mcp(editor.access())) {
            var tools = mcp.listTools().tools().stream().map(McpSchema.Tool::name).toList();
            assertTrue(tools.containsAll(List.of("get_space", "get_tree", "recent_changes", "list_labels", "pages_by_label", "set_labels",
                    "delete_comment", "star_page", "delete_page", "restore_page", "move_page", "list_attachments", "get_attachment",
                    "delete_attachment", "get_history", "get_revision", "compare_revisions")), tools.toString());

            // Markdown in, Markdown out; HTML on request.
            String created = ok(call(mcp, "create_page", Map.of("name", "Markdown page", "content", "# Heading\n\nHello **bold** world.\n\n- one\n- two",
                    "parent", pageId)));
            md = JsonPath.read(created, "$.id");
            String stored = jdbc.queryForObject("SELECT content FROM page WHERE id=?", String.class, md);
            assertTrue(stored.contains("<h1>Heading</h1>") && stored.contains("<strong>bold</strong>") && stored.contains("<li>two</li>"), stored);
            String page = ok(call(mcp, "get_page", Map.of("id", md)));
            assertEquals("markdown", JsonPath.read(page, "$.format"));
            assertTrue(JsonPath.<String>read(page, "$.content").contains("# Heading") && JsonPath.<String>read(page, "$.content").contains("**bold**"), page);
            assertTrue(JsonPath.<String>read(ok(call(mcp, "get_page", Map.of("id", md, "format", "html"))), "$.content").contains("<h1>"));
            assertTrue(error(call(mcp, "get_page", Map.of("id", md, "format", "pdf"))).contains("format"));

            // Labels, favourites, tree, recent changes and search filters.
            String labelled = ok(call(mcp, "set_labels", Map.of("id", md, "revision", 0, "labels", List.of("howto", "Team"))));
            assertEquals(1, JsonPath.<Integer>read(labelled, "$.revision"));
            assertEquals(List.of("howto", "Team"), jdbc.queryForList("SELECT t.name FROM tag_content tc JOIN tag t ON t.id=tc.tag_id WHERE tc.page_id=? ORDER BY t.name", String.class, md));
            String labels = ok(call(mcp, "list_labels", Map.of()));
            assertTrue(JsonPath.<List<String>>read(labels, "$[*].name").contains("howto"), labels);
            String byLabel = ok(call(mcp, "pages_by_label", Map.of("label", "howto")));
            assertEquals(List.of(md), JsonPath.read(byLabel, "$.pages[*].id"));
            ok(call(mcp, "star_page", Map.of("id", md)));
            assertTrue(JsonPath.<List<Integer>>read(ok(call(mcp, "list_starred", Map.of())), "$[*].id").contains(md));
            ok(call(mcp, "star_page", Map.of("id", md, "starred", false)));
            assertFalse(JsonPath.<List<Integer>>read(ok(call(mcp, "list_starred", Map.of())), "$[*].id").contains(md));
            String tree = ok(call(mcp, "get_tree", Map.of("parent", pageId, "depth", 3)));
            assertTrue(JsonPath.<List<Integer>>read(tree, "$.pages[*].id").contains(md), tree);
            assertEquals(pageId, JsonPath.<Integer>read(tree, "$.root.id"));
            String recent = ok(call(mcp, "recent_changes", Map.of()));
            assertEquals(md, JsonPath.<Integer>read(recent, "$.pages[0].id"), recent);
            assertTrue(JsonPath.<List<Integer>>read(ok(call(mcp, "search_pages", Map.of("query", "Heading", "label", "howto"))), "$.hits[*].id").contains(md));
            assertTrue(JsonPath.<List<Integer>>read(ok(call(mcp, "search_pages", Map.of("query", "Heading", "changed_since", "2099-01-01"))), "$.hits[*].id").isEmpty());
            assertTrue(JsonPath.<List<Integer>>read(ok(call(mcp, "search_pages", Map.of("query", "Heading", "changed_by", this.editor))), "$.hits[*].id").contains(md));
            assertTrue(JsonPath.<List<Integer>>read(ok(call(mcp, "search_pages", Map.of("query", "Heading", "changed_by", "nobody-" + this.editor))), "$.hits[*].id").isEmpty());
            assertTrue(error(call(mcp, "search_pages", Map.of("query", "Heading", "changed_since", "yesterday"))).contains("YYYY-MM-DD"));
            String space = ok(call(mcp, "get_space", Map.of("id", JsonPath.<Integer>read(page, "$.space.id"))));
            assertEquals("KR", JsonPath.read(space, "$.key"));

            // History: versions, an old version, a comparison.
            ok(call(mcp, "update_page", Map.of("id", md, "revision", 1, "content", "## Second\n\nChanged text.")));
            String history = ok(call(mcp, "get_history", Map.of("page", md)));
            assertEquals(2, JsonPath.<Integer>read(history, "$.currentRevision"));
            List<Integer> historyIds = JsonPath.read(history, "$.revisions[*].historyId");
            assertEquals(2, historyIds.size(), history);
            int oldest = historyIds.getLast();
            String revision = ok(call(mcp, "get_revision", Map.of("page", md, "history_id", oldest)));
            assertTrue(JsonPath.<String>read(revision, "$.content").contains("# Heading"), revision);
            String comparison = ok(call(mcp, "compare_revisions", Map.of("page", md, "from", oldest)));
            assertEquals(true, JsonPath.read(comparison, "$.changed"));
            assertTrue(JsonPath.<List<String>>read(comparison, "$.textDiff[*].kind").containsAll(List.of("removed", "added")), comparison);

            // Attachments: text of a text file, nothing for binaries.
            var upload = login(this.editor);
            upload.perform(multipart("/api/v1/pages/" + md + "/files")
                    .file(new MockMultipartFile("file", "notes.txt", "text/plain", "Hello attachment".getBytes()))
                    .file(new MockMultipartFile("file", "photo.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'})))
                    .andExpect(status().isCreated());
            String attachments = ok(call(mcp, "list_attachments", Map.of("page", md)));
            assertEquals(List.of("notes.txt", "photo.png"), JsonPath.read(attachments, "$[*].name"));
            fileId = JsonPath.read(attachments, "$[0].id");
            String content = ok(call(mcp, "get_attachment", Map.of("id", fileId)));
            assertEquals("Hello attachment", JsonPath.read(content, "$.text"));
            String binary = ok(call(mcp, "get_attachment", Map.of("id", JsonPath.<Integer>read(attachments, "$[1].id"))));
            assertNull(JsonPath.read(binary, "$.text"));
            assertTrue(JsonPath.<String>read(binary, "$.note").contains("No text"));
            ok(call(mcp, "delete_attachment", Map.of("id", fileId)));
            assertEquals(List.of("photo.png"), JsonPath.read(ok(call(mcp, "list_attachments", Map.of("page", md))), "$[*].name"));

            // Comments can be removed again.
            int commentId = JsonPath.read(ok(call(mcp, "add_comment", Map.of("page", md, "text", "temporary"))), "$.id");
            ok(call(mcp, "delete_comment", Map.of("page", md, "comment_id", commentId)));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM page_comment WHERE page_id=?", Integer.class, md));

            // Move below a sibling, delete, restore.
            target = JsonPath.read(ok(call(mcp, "create_page", Map.of("name", "Target", "content", "Target page", "parent", pageId))), "$.id");
            long current = jdbc.queryForObject("SELECT revision FROM page WHERE id=?", Long.class, md);
            ok(call(mcp, "move_page", Map.of("id", md, "parent", target, "revision", current)));
            assertEquals(target, jdbc.queryForObject("SELECT parent FROM page WHERE id=?", Integer.class, md));
            assertTrue(error(call(mcp, "move_page", Map.of("id", md, "parent", target, "revision", 0))).contains("changed"));
            current = jdbc.queryForObject("SELECT revision FROM page WHERE id=?", Long.class, md);
            assertTrue(ok(call(mcp, "delete_page", Map.of("id", md, "revision", current))).contains("administrator can bring it back"));
            assertEquals(1, jdbc.queryForObject("SELECT deleted FROM page WHERE id=?", Integer.class, md));
            error(call(mcp, "get_page", Map.of("id", md)));
            long deletedRevision = jdbc.queryForObject("SELECT revision FROM page WHERE id=?", Long.class, md);
            // Deleted pages are the administrators' business, as in the web UI.
            error(call(mcp, "get_history", Map.of("page", md)));
            assertTrue(error(call(mcp, "restore_page", Map.of("id", md, "revision", deletedRevision))).contains("administrator"));
            try (var adminMcp = mcp(connect(browser(), "integration.admin", clientId, true).access())) {
                assertEquals(deletedRevision, JsonPath.<Integer>read(ok(call(adminMcp, "get_history", Map.of("page", md))), "$.currentRevision").longValue());
                ok(call(adminMcp, "restore_page", Map.of("id", md, "revision", deletedRevision)));
            }
            assertEquals(0, jdbc.queryForObject("SELECT deleted FROM page WHERE id=?", Integer.class, md));
            assertTrue(JsonPath.<String>read(ok(call(mcp, "get_page", Map.of("id", md))), "$.content").contains("Changed text"));

            // Resources and prompts.
            var templates = mcp.listResourceTemplates().resourceTemplates().stream().map(McpSchema.ResourceTemplate::uriTemplate).toList();
            assertTrue(templates.containsAll(List.of("knowledgeroot://page/{id}", "knowledgeroot://space/{id}")), templates.toString());
            var resource = mcp.readResource(new McpSchema.ReadResourceRequest("knowledgeroot://page/" + md));
            String markdown = ((McpSchema.TextResourceContents) resource.contents().getFirst()).text();
            assertTrue(markdown.startsWith("# Markdown page") && markdown.contains("## Second") && markdown.contains("Labels: howto, Team"), markdown);
            var spaceResource = mcp.readResource(new McpSchema.ReadResourceRequest("knowledgeroot://space/" + JsonPath.<Integer>read(page, "$.space.id")));
            assertTrue(((McpSchema.TextResourceContents) spaceResource.contents().getFirst()).text().contains("## Top-level pages"));
            assertTrue(mcp.listPrompts().prompts().stream().map(McpSchema.Prompt::name).toList().containsAll(List.of("summarize_space", "find_stale_pages", "draft_page")));
            var prompt = mcp.getPrompt(new McpSchema.GetPromptRequest("draft_page", Map.of("topic", "Onboarding", "parent", String.valueOf(pageId))));
            String promptText = ((McpSchema.TextContent) prompt.messages().getFirst().content()).text();
            assertTrue(promptText.contains("\"Onboarding\"") && promptText.contains("with parent " + pageId), promptText);
        }

        // The audit knows every call with user, assistant, parameters and outcome; page contents stay out.
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM mcp_audit WHERE login=? AND kind='tool' AND name='get_page' AND outcome='ok'", Integer.class, this.editor) >= 2);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mcp_audit WHERE login=? AND name='search_pages' AND outcome='error'", Integer.class, this.editor));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mcp_audit WHERE login=? AND kind='resource' AND name='space'", Integer.class, this.editor));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mcp_audit WHERE login=? AND kind='prompt' AND name='draft_page'", Integer.class, this.editor));
        String parameters = jdbc.queryForObject("SELECT parameters FROM mcp_audit WHERE login=? AND name='create_page' ORDER BY id LIMIT 1", String.class, this.editor);
        assertTrue(parameters.contains("name=Markdown page") && parameters.contains("content=[") && !parameters.contains("Heading"), parameters);
        String registeredId = jdbc.queryForObject("SELECT id FROM oauth2_registered_client WHERE client_id=?", String.class, clientId);
        assertEquals(registeredId, jdbc.queryForObject("SELECT client_id FROM mcp_audit WHERE login=? ORDER BY id DESC LIMIT 1", String.class, this.editor));

        // Administrators see connections and the log and can disconnect an assistant; members cannot.
        var admin = login("integration.admin");
        String overview = page(admin, "/admin/mcp?user=" + this.editor);
        assertTrue(overview.contains("Claude Features") && overview.contains("<b>" + this.editor + "</b>") && overview.contains("get_attachment")
                && overview.contains("name=Markdown page"), overview);
        login(this.editor).perform(get("/admin/mcp")).andExpect(status().isForbidden());
        admin.perform(post("/admin/mcp/connections/revoke").param("userId", String.valueOf(editorId)).param("client", registeredId))
                .andExpect(redirectedUrl("/admin/mcp?disconnected"));
        assertEquals(400, refresh(clientId, editor.refresh()).statusCode(), "the disconnected assistant cannot refresh");
        assertFalse(page(admin, "/admin/mcp").contains("<b>" + this.editor + "</b>"), "the editor's connection is gone; the admin's own stays");
        switchMcp(false, "claude.ai\n*.claude.ai");
    }
}
