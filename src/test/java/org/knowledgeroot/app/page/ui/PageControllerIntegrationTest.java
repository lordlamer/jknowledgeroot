package org.knowledgeroot.app.page.ui;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PageControllerIntegrationTest extends IsolatedApplicationTest {
    @Test void directoryEndpointsRequireLoginAndReturnRealData() throws Exception {
        mvc.perform(get("/api/users")).andExpect(status().is3xxRedirection());
        var admin = login("integration.admin");
        admin.perform(get("/api/users")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        admin.perform(get("/api/groups")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        login(reader).perform(get("/user")).andExpect(status().isForbidden());
    }

    @Test void editingUsesActualGroupMembershipOnEveryRequest() throws Exception {
        String name = "team-" + pageId;
        jdbc.update("INSERT INTO `group` (name,active,created_by,create_date,changed_by,change_date) VALUES (?,TRUE,?,NOW(),?,NOW())",name,adminId,adminId);
        int group = jdbc.queryForObject("SELECT id FROM `group` WHERE name=?",Integer.class,name);
        grant(pageId,"group",group,"edit");
        var member = login(reader);
        member.perform(get("/ui/page/" + pageId + "/edit")).andExpect(status().isForbidden());
        jdbc.update("INSERT INTO group_member (group_id,member_id,member_type) VALUES (?,?,'user')",group,readerId);
        member.perform(get("/ui/page/" + pageId + "/edit")).andExpect(status().isOk());
        jdbc.update("DELETE FROM group_member WHERE group_id=?",group);
        member.perform(get("/ui/page/" + pageId + "/edit")).andExpect(status().isForbidden());
    }

    @Test void commentsEscapeHtmlAndEnforceOwnershipAndPageAccess() throws Exception {
        var author = login(reader);
        author.perform(post("/ui/page/" + pageId + "/comments").param("content","<img src=x onerror=alert(1)>"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;img")));
        int comment = jdbc.queryForObject("SELECT id FROM page_comment WHERE page_id=?",Integer.class,pageId);
        login(editor).perform(delete("/ui/page/" + pageId + "/comments/" + comment)).andExpect(status().isForbidden());
        login(outsider).perform(get("/ui/page/" + pageId + "/comments")).andExpect(status().isForbidden());
        author.perform(delete("/ui/page/" + pageId + "/comments/" + comment)).andExpect(status().isOk());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM page_comment WHERE id=?",Integer.class,comment));
    }

    @Test void starsBelongToTheirUser() throws Exception {
        var owner = login(reader);
        owner.perform(post("/ui/page/" + pageId + "/star")).andExpect(status().isOk());
        login(editor).perform(delete("/ui/page/" + pageId + "/star")).andExpect(status().isOk());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_page_star WHERE user_id=? AND page_id=?",Integer.class,readerId,pageId));
        owner.perform(delete("/ui/page/" + pageId + "/star")).andExpect(status().isOk());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM user_page_star WHERE page_id=?",Integer.class,pageId));
    }

    @Test void uploadAndDownloadUseRealStorageAndObjectPermissions() throws Exception {
        byte[] bytes = new byte[]{1,2,3,4};
        login(editor).perform(multipart("/ui/file").file(new MockMultipartFile("file","attachment.txt","text/plain",bytes))
                .param("pageId",Integer.toString(pageId))).andExpect(status().is3xxRedirection());
        int file = jdbc.queryForObject("SELECT id FROM file WHERE page_id=?",Integer.class,pageId);
        assertEquals(editorId,jdbc.queryForObject("SELECT created_by FROM file WHERE id=?",Integer.class,file));
        login(reader).perform(get("/ui/file/" + file + "/download/attachment.txt"))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes));
        login(outsider).perform(get("/ui/file/" + file + "/download/attachment.txt")).andExpect(status().isForbidden());
        login(reader).perform(multipart("/ui/file").file(new MockMultipartFile("file","blocked.txt","text/plain",bytes))
                .param("pageId",Integer.toString(pageId))).andExpect(status().isForbidden());
    }

    @Test void uploadedFilesAreListedOnThePageAndCanBeDeleted() throws Exception {
        var client = login(editor);
        client.perform(get("/ui/page/" + pageId)).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"krUploadDialog\"")))
                .andExpect(content().string(containsString("No attachments yet")));
        client.perform(multipart("/ui/file").file(new MockMultipartFile("file","first notes.txt","text/plain",new byte[]{1}))
                .file(new MockMultipartFile("file","second.pdf","application/pdf",new byte[2048]))
                .param("pageId",Integer.toString(pageId))).andExpect(status().is3xxRedirection());
        int first = jdbc.queryForObject("SELECT id FROM file WHERE page_id=? AND name='first notes.txt'",Integer.class,pageId);
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM file WHERE page_id=? AND deleted=FALSE",Integer.class,pageId));
        login(reader).perform(get("/ui/page/" + pageId)).andExpect(status().isOk())
                .andExpect(content().string(containsString("/ui/file/" + first + "/download/first%20notes.txt")))
                .andExpect(content().string(containsString("2.0 KB")))
                .andExpect(content().string(not(containsString("krUploadDialog"))))
                .andExpect(content().string(not(containsString("hx-delete=\"/ui/file/"))));
        login(reader).perform(delete("/ui/file/" + first)).andExpect(status().isForbidden());
        client.perform(delete("/ui/file/" + first)).andExpect(status().isOk());
        client.perform(get("/ui/page/" + pageId)).andExpect(content().string(not(containsString("first notes.txt"))))
                .andExpect(content().string(containsString("second.pdf")));
    }

    @Test void searchRendersHighlightedHitsWithVisiblePathAndEscapesTheQuery() throws Exception {
        String name = jdbc.queryForObject("SELECT name FROM page WHERE id=?", String.class, pageId);
        jdbc.update("UPDATE page SET parent=?, content='<p>Findme &amp; more</p>' WHERE id=?", otherPageId, pageId);
        String parentName = jdbc.queryForObject("SELECT name FROM page WHERE id=?", String.class, otherPageId);
        var client = login(editor);
        client.perform(get("/search").param("q", "findme")).andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"kr-result\"")))
                .andExpect(content().string(containsString("<mark>Findme</mark> &amp; more")))
                .andExpect(content().string(containsString(name)))
                .andExpect(content().string(not(containsString(parentName))));
        grant(otherPageId, "user", editorId, "view");
        client.perform(get("/search").param("q", "findme")).andExpect(content().string(containsString(parentName)));
        client.perform(get("/search").param("q", "<script>x</script>")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>x</script>"))))
                .andExpect(content().string(containsString("No results for")));
        client.perform(get("/search").param("q", " ")).andExpect(status().isOk())
                .andExpect(content().string(containsString("What are you looking for?")));
    }

    @Test void historyLinksToDeletedPagesOnlyForDeletedPagesAndAdministrators() throws Exception {
        String url = "/ui/page/" + pageId + "/history";
        var admin = login("integration.admin");
        admin.perform(get(url).header("HX-Request", "true")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/ui/page/deleted"))))
                .andExpect(content().string(containsString("Current page")));
        jdbc.update("UPDATE page SET deleted=TRUE WHERE id=?", pageId);
        admin.perform(get(url).header("HX-Request", "true")).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/ui/page/deleted\"")))
                .andExpect(content().string(not(containsString("Current page"))));
    }

    @Test void probesExposeOnlyStatusAndMetricsRequireCurrentAdminRole() throws Exception {
        for (String endpoint : new String[]{"/actuator/health","/actuator/health/liveness","/actuator/health/readiness"}) {
            mvc.perform(get(endpoint)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components").doesNotExist()).andExpect(jsonPath("$.details").doesNotExist());
        }
        mvc.perform(get("/actuator/metrics")).andExpect(status().is3xxRedirection());
        var member = login(reader);
        member.perform(get("/actuator/metrics")).andExpect(status().isForbidden());
        var admin = login("integration.admin");
        admin.perform(get("/actuator/metrics/jvm.memory.used")).andExpect(status().isOk());
        admin.perform(get("/actuator/env")).andExpect(status().isNotFound());
        admin.perform(get("/actuator/heapdump")).andExpect(status().isNotFound());
        jdbc.update("UPDATE user SET admin=TRUE WHERE id=?",readerId);
        var promoted = login(reader);
        promoted.perform(get("/actuator/metrics")).andExpect(status().isOk());
        jdbc.update("UPDATE user SET admin=FALSE WHERE id=?",readerId);
        promoted.perform(get("/actuator/metrics")).andExpect(status().is3xxRedirection());
    }
}