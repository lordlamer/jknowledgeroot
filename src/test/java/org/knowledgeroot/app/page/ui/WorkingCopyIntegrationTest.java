package org.knowledgeroot.app.page.ui;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Working copies, autosave, publishing and draft pages (R62) in the web UI and the client API. */
class WorkingCopyIntegrationTest extends IsolatedApplicationTest {
    long revision(int id) { return jdbc.queryForObject("SELECT revision FROM page WHERE id=?", Long.class, id); }
    String stored(int id) { return jdbc.queryForObject("SELECT content FROM page WHERE id=?", String.class, id); }
    int versions(int id) { return jdbc.queryForObject("SELECT COUNT(*) FROM page_history WHERE page_id=?", Integer.class, id); }
    int copies(int id) { return jdbc.queryForObject("SELECT COUNT(*) FROM page_draft WHERE page_id=?", Integer.class, id); }
    List<String> labels(int id) {
        return jdbc.queryForList("SELECT t.name FROM tag t JOIN tag_content tc ON t.id=tc.tag_id WHERE tc.page_id=? ORDER BY t.name", String.class, id);
    }

    @Test void autosaveKeepsAWorkingCopyWithoutANewVersion() throws Exception {
        var session = login(editor);
        var saved = session.perform(post("/ui/page/" + pageId + "/draft").param("name", "typed title")
                .param("content", "<p>typed</p><script>x()</script>").param("labels", "wip, later").param("revision", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stale").value(false))
                .andExpect(jsonPath("$.savedBy").value(editor)).andReturn().getResponse().getContentAsString();
        assertNotNull(JsonPath.read(saved, "$.savedAt"));
        assertEquals("original", stored(pageId));
        assertEquals(0, revision(pageId));
        assertEquals(0, versions(pageId));
        assertEquals("<p>typed</p>", jdbc.queryForObject("SELECT content FROM page_draft WHERE page_id=?", String.class, pageId));
        assertEquals(List.of(), labels(pageId), "labels are published with the page, not with the working copy");

        // The editor starts from the working copy; the page view announces it to editors only.
        session.perform(get("/ui/page/" + pageId + "/edit")).andExpect(status().isOk())
                .andExpect(content().string(containsString("typed title")))
                .andExpect(content().string(containsString("wip, later")))
                .andExpect(content().string(containsString("Discard working copy")));
        session.perform(get("/ui/page/" + pageId)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Unpublished changes saved")))
                .andExpect(content().string(containsString(">original<")));
        login(reader).perform(get("/ui/page/" + pageId)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Unpublished changes"))))
                .andExpect(content().string(not(containsString("typed"))));

        // Only editors save; a missing revision is 428, one from the future 409.
        login(reader).perform(post("/ui/page/" + pageId + "/draft").param("name", "x").param("content", "y").param("revision", "0"))
                .andExpect(status().isForbidden());
        session.perform(post("/ui/page/" + pageId + "/draft").param("name", "x").param("content", "y"))
                .andExpect(status().is(428));
        session.perform(post("/ui/page/" + pageId + "/draft").param("name", "x").param("content", "y").param("revision", "5"))
                .andExpect(status().isConflict());
        assertEquals(1, copies(pageId));
    }

    @Test void publishingFromTheFormWritesOneVersionAndDropsTheWorkingCopy() throws Exception {
        var session = login(editor);
        session.perform(post("/ui/page/" + pageId + "/draft").param("name", "wip").param("content", "<p>wip</p>").param("revision", "0"))
                .andExpect(status().isOk());
        session.perform(post("/ui/page/" + pageId + "/edit").param("name", "final").param("content", "<p>final</p>")
                .param("labels", "done").param("revision", "0")).andExpect(status().is3xxRedirection());
        assertEquals("<p>final</p>", stored(pageId));
        assertEquals(1, revision(pageId));
        assertEquals(1, versions(pageId));
        assertEquals(0, copies(pageId));
        assertEquals(List.of("done"), labels(pageId));
    }

    @Test void thePublishActionPublishesTheWorkingCopyWithItsLabels() throws Exception {
        var session = login(editor);
        session.perform(post("/ui/page/" + pageId + "/draft").param("name", "from copy").param("content", "<p>copy</p>")
                .param("labels", "beta").param("revision", "0")).andExpect(status().isOk());
        login(reader).perform(post("/ui/page/" + pageId + "/publish").param("revision", "0")).andExpect(status().isForbidden());
        session.perform(post("/ui/page/" + pageId + "/publish").param("revision", "0")).andExpect(status().is3xxRedirection());
        assertEquals("<p>copy</p>", stored(pageId));
        assertEquals("from copy", jdbc.queryForObject("SELECT name FROM page WHERE id=?", String.class, pageId));
        assertEquals(List.of("beta"), labels(pageId));
        assertEquals(1, revision(pageId));
        assertEquals(1, versions(pageId));
        assertEquals(0, copies(pageId));
        // Nothing left to publish: 409 instead of an empty version.
        session.perform(post("/ui/page/" + pageId + "/publish").param("revision", "1")).andExpect(status().isConflict());
        assertEquals(1, revision(pageId));
    }

    @Test void discardingReturnsToThePublishedPage() throws Exception {
        var session = login(editor);
        session.perform(post("/ui/page/" + pageId + "/draft").param("name", "gone").param("content", "<p>gone</p>").param("revision", "0"))
                .andExpect(status().isOk());
        login(reader).perform(post("/ui/page/" + pageId + "/draft/discard")).andExpect(status().isForbidden());
        session.perform(post("/ui/page/" + pageId + "/draft/discard")).andExpect(redirectedUrl("/ui/page/" + pageId + "/edit"));
        assertEquals(0, copies(pageId));
        session.perform(get("/ui/page/" + pageId + "/edit")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("gone"))))
                .andExpect(content().string(containsString("original")));
    }

    @Test void aWorkingCopyStartedBeforeAPublicationIsFlaggedAsStale() throws Exception {
        var first = login(editor);
        var second = login(editor);
        first.perform(post("/ui/page/" + pageId + "/edit").param("name", "published").param("content", "<p>v1</p>").param("revision", "0"))
                .andExpect(status().is3xxRedirection());
        // The second tab was opened at revision 0 and only now starts typing.
        second.perform(post("/ui/page/" + pageId + "/draft").param("name", "late").param("content", "<p>late</p>").param("revision", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stale").value(true));
        second.perform(get("/ui/page/" + pageId + "/edit")).andExpect(status().isOk())
                .andExpect(content().string(containsString("published by someone else")));
        // Publishing the stale form is refused as before; the working copy stays for comparison.
        second.perform(post("/ui/page/" + pageId + "/edit").param("name", "late").param("content", "<p>late</p>").param("revision", "0"))
                .andExpect(status().isConflict());
        assertEquals(1, copies(pageId));
        assertEquals("<p>v1</p>", stored(pageId));
    }

    @Test void draftPagesAreVisibleToEditorsOnlyUntilPublished() throws Exception {
        var session = login(editor);
        String name = "draft-child-" + pageId;
        session.perform(post("/ui/page/new").param("parent", String.valueOf(pageId)).param("name", name)
                .param("content", "<p>not yet</p>").param("published", "false")).andExpect(status().is3xxRedirection());
        int draft = jdbc.queryForObject("SELECT id FROM page WHERE name=?", Integer.class, name);
        assertEquals(false, jdbc.queryForObject("SELECT published FROM page WHERE id=?", Boolean.class, draft));

        login(reader).perform(get("/ui/page/" + draft)).andExpect(status().isForbidden());
        login(reader).perform(get("/api/v1/pages/" + draft)).andExpect(status().isForbidden());
        mvc.perform(get("/ui/page/" + draft)).andExpect(status().isForbidden());
        assertFalse(login(reader).perform(get("/api/v1/tree?parent=" + pageId)).andReturn().getResponse().getContentAsString().contains(name));
        assertFalse(login(reader).perform(get("/search?q=" + name)).andReturn().getResponse().getContentAsString().contains("/ui/page/" + draft + "\""));
        var tree = session.perform(get("/api/v1/tree?parent=" + pageId)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(List.of(true), JsonPath.read(tree, "$.items[?(@.name=='" + name + "')].draft"), tree);
        session.perform(get("/search?q=" + name)).andExpect(status().isOk())
                .andExpect(content().string(containsString("/ui/page/" + draft + "\""))).andExpect(content().string(containsString(">Draft<")));
        login("integration.admin").perform(get("/ui/page/" + draft)).andExpect(status().isOk());
        session.perform(get("/ui/page/" + draft)).andExpect(status().isOk())
                .andExpect(content().string(containsString("This page is a draft")));
        session.perform(get("/ui/page/" + draft + "/edit")).andExpect(status().isOk())
                .andExpect(content().string(containsString(">Publish<")));

        // The draft keeps its working copy; publishing shows the working copy's state to readers.
        session.perform(post("/ui/page/" + draft + "/draft").param("name", name).param("content", "<p>ready</p>").param("revision", "0"))
                .andExpect(status().isOk());
        session.perform(get("/ui/page/" + draft)).andExpect(content().string(containsString("<p>ready</p>")));
        session.perform(post("/ui/page/" + draft + "/publish").param("revision", "0")).andExpect(status().is3xxRedirection());
        assertEquals(true, jdbc.queryForObject("SELECT published FROM page WHERE id=?", Boolean.class, draft));
        assertEquals("<p>ready</p>", stored(draft));
        login(reader).perform(get("/ui/page/" + draft)).andExpect(status().isOk())
                .andExpect(content().string(containsString("<p>ready</p>")))
                .andExpect(content().string(not(containsString("This page is a draft"))));
        assertTrue(login(reader).perform(get("/api/v1/tree?parent=" + pageId)).andReturn().getResponse().getContentAsString().contains(name));
    }

    @Test void theClientApiHandlesWorkingCopiesAndDrafts() throws Exception {
        var session = login(editor);
        var copy = session.perform(put("/api/v1/pages/" + pageId + "/draft").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"api copy\",\"content\":\"<p>api</p>\",\"labels\":[\"a\"],\"revision\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stale").value(false))
                .andExpect(jsonPath("$.changedByLogin").value(editor)).andReturn().getResponse().getContentAsString();
        assertEquals(List.of("a"), JsonPath.read(copy, "$.labels"));
        session.perform(get("/api/v1/pages/" + pageId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true)).andExpect(jsonPath("$.hasWorkingCopy").value(true))
                .andExpect(jsonPath("$.content").value("original"));
        session.perform(get("/api/v1/pages/" + pageId + "/draft")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("<p>api</p>"));
        login(reader).perform(get("/api/v1/pages/" + pageId + "/draft")).andExpect(status().isForbidden());
        login(reader).perform(get("/api/v1/pages/" + pageId)).andExpect(jsonPath("$.hasWorkingCopy").value(false));

        session.perform(post("/api/v1/pages/" + pageId + "/publish").contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.content").value("<p>api</p>")).andExpect(jsonPath("$.hasWorkingCopy").value(false));
        session.perform(get("/api/v1/pages/" + pageId + "/draft")).andExpect(status().isNotFound());
        assertEquals(List.of("a"), labels(pageId));

        // A draft page through the API, then published with the ordinary update.
        var created = session.perform(post("/api/v1/pages").contentType(MediaType.APPLICATION_JSON)
                .content("{\"parent\":" + pageId + ",\"name\":\"api draft\",\"content\":\"<p>soon</p>\",\"published\":false}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int draft = JsonPath.read(created, "$.id");
        login(reader).perform(get("/api/v1/pages/" + draft)).andExpect(status().isForbidden());
        session.perform(get("/api/v1/pages/" + draft)).andExpect(jsonPath("$.published").value(false));
        session.perform(put("/api/v1/pages/" + draft + "/draft").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"api draft\",\"content\":\"<p>soon</p>\",\"revision\":0}")).andExpect(status().isOk());
        session.perform(delete("/api/v1/pages/" + draft + "/draft")).andExpect(status().isNoContent());
        assertEquals(0, copies(draft));
        session.perform(put("/api/v1/pages/" + draft).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"api draft\",\"content\":\"<p>now</p>\",\"revision\":0,\"published\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.published").value(true));
        login(reader).perform(get("/api/v1/pages/" + draft)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("<p>now</p>"));
    }
}
