package org.knowledgeroot.app.page.ui;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Page templates of a space (R65): management by space administrators, "New page" and the client API. */
class PageTemplateIntegrationTest extends IsolatedApplicationTest {

    private int space(String key) {
        jdbc.update("INSERT INTO space (space_key, name, description, create_date, change_date) VALUES (?, ?, '', NOW(3), NOW(3))", key, key + " space");
        return jdbc.queryForObject("SELECT id FROM space WHERE space_key = ?", Integer.class, key);
    }

    private void spaceGrant(int spaceId, String role, Integer roleId, String level) {
        jdbc.update("INSERT INTO space_permission (space_id, role_type, role_id, permission_level, create_date, change_date) VALUES (?, ?, ?, ?, NOW(3), NOW(3))",
                spaceId, role, roleId, level);
    }

    /** A root page that takes its permissions from the space. */
    private int rootIn(int spaceId, String name) {
        jdbc.update("INSERT INTO page (space_id, parent, name, content, active, inherit_permissions, created_by, create_date, changed_by, change_date) VALUES (?, 0, ?, 'text', TRUE, TRUE, ?, NOW(), ?, NOW())",
                spaceId, name, adminId, adminId);
        return jdbc.queryForObject("SELECT id FROM page WHERE name = ?", Integer.class, name);
    }

    private int template(int spaceId, String name, String content, String labels) {
        jdbc.update("INSERT INTO page_template (space_id, name, content, labels, created_by, create_date, changed_by, change_date) VALUES (?, ?, ?, ?, ?, NOW(3), ?, NOW(3))",
                spaceId, name, content, labels, adminId, adminId);
        return jdbc.queryForObject("SELECT id FROM page_template WHERE space_id = ? AND name = ?", Integer.class, spaceId, name);
    }

    private List<String> labelsOf(int pageId) {
        return jdbc.queryForList("SELECT t.name FROM tag_content tc JOIN tag t ON t.id = tc.tag_id WHERE tc.page_id = ? ORDER BY t.name", String.class, pageId);
    }

    @Test void spaceAdministratorsManageTemplatesAndNewPagesStartFromThem() throws Exception {
        int team = space("TPL" + pageId);
        spaceGrant(team, "user", editorId, "admin");
        spaceGrant(team, "user", readerId, "edit");
        var manager = login(editor);
        manager.perform(get("/spaces/" + team + "/settings")).andExpect(status().isOk())
                .andExpect(content().string(containsString("No templates yet.")));
        // The edit level is not enough to manage templates.
        login(reader).perform(get("/spaces/" + team + "/templates/new")).andExpect(status().isForbidden());
        login(reader).perform(post("/spaces/" + team + "/templates").param("name", "Nope").param("content", "")).andExpect(status().isForbidden());

        // Validation stays on the form; a valid template is stored with its labels and cleaned content.
        manager.perform(get("/spaces/" + team + "/templates/new")).andExpect(status().isOk()).andExpect(content().string(containsString("New template")));
        manager.perform(post("/spaces/" + team + "/templates").param("name", "   ").param("content", "<p>x</p>"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Enter a template name")));
        manager.perform(post("/spaces/" + team + "/templates").param("name", "Meeting notes").param("labels", "meeting, notes, meeting")
                        .param("content", "<h2>Agenda</h2><script>alert(1)</script><p>Topics</p>"))
                .andExpect(redirectedUrl("/spaces/" + team + "/settings?template=saved"));
        int id = jdbc.queryForObject("SELECT id FROM page_template WHERE space_id = ? AND name = 'Meeting notes'", Integer.class, team);
        assertEquals("meeting\nnotes", jdbc.queryForObject("SELECT labels FROM page_template WHERE id = ?", String.class, id));
        String stored = jdbc.queryForObject("SELECT content FROM page_template WHERE id = ?", String.class, id);
        assertTrue(stored.contains("<h2>Agenda</h2>") && !stored.contains("script"), stored);
        manager.perform(post("/spaces/" + team + "/templates").param("name", "meeting notes").param("content", ""))
                .andExpect(status().isOk()).andExpect(content().string(containsString("already exists")));
        String settings = manager.perform(get("/spaces/" + team + "/settings?template=saved")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(settings.contains("Template saved.") && settings.contains("Meeting notes") && settings.contains("meeting, notes"), settings);

        // Editing: the form shows the stored values; a template of another space is refused under this one.
        manager.perform(get("/spaces/" + team + "/templates/" + id)).andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Meeting notes\"")))
                .andExpect(content().string(containsString("value=\"meeting, notes\"")))
                .andExpect(content().string(containsString("Agenda")));
        manager.perform(post("/spaces/" + team + "/templates/" + id).param("name", "Meeting minutes").param("labels", "meeting")
                        .param("content", "<h2>Agenda</h2><p>Decisions</p>"))
                .andExpect(redirectedUrl("/spaces/" + team + "/settings?template=saved"));
        assertEquals("Meeting minutes", jdbc.queryForObject("SELECT name FROM page_template WHERE id = ?", String.class, id));
        int kr = jdbc.queryForObject("SELECT id FROM space WHERE space_key = 'KR'", Integer.class);
        login("integration.admin").perform(get("/spaces/" + kr + "/templates/" + id)).andExpect(status().isBadRequest());

        // "New page" in the space offers the template; choosing it fills the form, a typed name stays.
        var creator = login(reader);
        creator.perform(get("/ui/page/new?space=" + team)).andExpect(status().isOk())
                .andExpect(content().string(containsString("<option value=\"" + id + "\"")))
                .andExpect(content().string(containsString("Blank page")));
        String form = creator.perform(get("/ui/page/new?space=" + team + "&template=" + id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(form.contains("value=\"Meeting minutes\"") && form.contains("value=\"meeting\"") && form.contains("Decisions")
                && form.contains("<option value=\"" + id + "\" selected"), form);
        String typed = creator.perform(get("/ui/page/new?space=" + team + "&template=" + id + "&name=Standup")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(typed.contains("value=\"Standup\"") && !typed.contains("value=\"Meeting minutes\"") && typed.contains("Decisions"), typed);
        int root = rootIn(team, "tpl-root-" + pageId);
        creator.perform(get("/ui/page/" + root + "/new?template=" + id)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Decisions")));
        // A space without templates shows no picker.
        jdbc.update("DELETE FROM page_template WHERE space_id = ?", kr);
        assertFalse(login("integration.admin").perform(get("/ui/page/" + pageId + "/new")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().contains("Blank page"));
        int other = template(kr, "Other space " + pageId, "<p>other</p>", "");
        creator.perform(get("/ui/page/new?space=" + team + "&template=" + other)).andExpect(status().isBadRequest());

        manager.perform(post("/spaces/" + team + "/templates/" + id + "/delete")).andExpect(redirectedUrl("/spaces/" + team + "/settings?template=deleted"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM page_template WHERE id = ?", Integer.class, id));
        jdbc.update("DELETE FROM page_template WHERE id = ?", other);
    }

    @Test void theClientApiListsTemplatesAndCreatesPagesFromThem() throws Exception {
        int team = space("API" + pageId);
        spaceGrant(team, "user", readerId, "edit");
        int id = template(team, "Decision record", "<h2>Context</h2><p>Why</p>", "decision\narchitecture");
        int kr = jdbc.queryForObject("SELECT id FROM space WHERE space_key = 'KR'", Integer.class);
        int foreign = template(kr, "Foreign " + pageId, "<p>x</p>", "");

        var app = login(reader);
        String listed = app.perform(get("/api/v1/spaces/" + team + "/templates")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id)).andExpect(jsonPath("$[0].name").value("Decision record"))
                .andExpect(jsonPath("$[0].labels[1]").value("architecture"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(JsonPath.<String>read(listed, "$[0].content").contains("<h2>Context</h2>"), listed);
        login(outsider).perform(get("/api/v1/spaces/" + team + "/templates")).andExpect(status().isForbidden());

        // Name, content and labels come from the template where the request leaves them out.
        String created = app.perform(post("/api/v1/pages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"space\":" + team + ",\"template\":" + id + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int page = JsonPath.read(created, "$.id");
        assertEquals("Decision record", jdbc.queryForObject("SELECT name FROM page WHERE id = ?", String.class, page));
        assertTrue(jdbc.queryForObject("SELECT content FROM page WHERE id = ?", String.class, page).contains("<h2>Context</h2>"));
        assertEquals(List.of("architecture", "decision"), labelsOf(page));
        assertEquals(team, jdbc.queryForObject("SELECT space_id FROM page WHERE id = ?", Integer.class, page));
        String named = app.perform(post("/api/v1/pages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parent\":" + page + ",\"template\":" + id + ",\"name\":\"ADR-1\",\"labels\":[\"adr\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int child = JsonPath.read(named, "$.id");
        assertEquals("ADR-1", jdbc.queryForObject("SELECT name FROM page WHERE id = ?", String.class, child));
        assertEquals(List.of("adr"), labelsOf(child));
        assertTrue(jdbc.queryForObject("SELECT content FROM page WHERE id = ?", String.class, child).contains("Why"));
        // Templates of another space are refused, and a page still needs a name without one.
        app.perform(post("/api/v1/pages").contentType(MediaType.APPLICATION_JSON).content("{\"space\":" + team + ",\"template\":" + foreign + "}"))
                .andExpect(status().isBadRequest());
        app.perform(post("/api/v1/pages").contentType(MediaType.APPLICATION_JSON).content("{\"space\":" + team + ",\"template\":999999}"))
                .andExpect(status().isNotFound());
        jdbc.update("DELETE FROM page_template WHERE id = ?", foreign);
    }
}
