package org.knowledgeroot.app.page.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PageEditPermissionIntegrationTest extends IsolatedApplicationTest {
    @Test void editorSavesContentLabelsAndServerAuditWithoutChangingGrants() throws Exception {
        var before = jdbc.queryForList("SELECT * FROM page_permission WHERE page_id=? ORDER BY id",pageId);
        login(editor).perform(post("/ui/page/" + pageId + "/edit").param("name","Edited").param("content","<p>Saved</p>")
                .param("labels","one, two").param("changedBy",Integer.toString(outsiderId)))
                .andExpect(status().is3xxRedirection());
        assertEquals("<p>Saved</p>",jdbc.queryForObject("SELECT content FROM page WHERE id=?",String.class,pageId));
        assertEquals(editorId,jdbc.queryForObject("SELECT changed_by FROM page WHERE id=?",Integer.class,pageId));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM tag_content WHERE page_id=?",Integer.class,pageId));
        assertEquals(before,jdbc.queryForList("SELECT * FROM page_permission WHERE page_id=? ORDER BY id",pageId));
    }

    @Test void readerCannotEditContent() throws Exception {
        login(reader).perform(post("/ui/page/" + pageId + "/edit").param("name","Denied").param("content","Denied"))
                .andExpect(status().isForbidden());
        assertEquals("original",jdbc.queryForObject("SELECT content FROM page WHERE id=?",String.class,pageId));
    }

    @Test void editorCannotEscalateSharingThroughTheEditForm() throws Exception {
        login(editor).perform(post("/ui/page/" + pageId + "/edit").param("name","Denied").param("content","Denied")
                .param("permissionAdditions[0][roleType]","guest").param("permissionAdditions[0][permissionLevel]","edit"))
                .andExpect(status().isForbidden());
        assertEquals("original",jdbc.queryForObject("SELECT content FROM page WHERE id=?",String.class,pageId));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM page_permission WHERE page_id=? AND role_type='guest'",Integer.class,pageId));
    }

    @Test void adminUpdatesDeletesAndAddsPermissionsAtomically() throws Exception {
        int readerGrant = jdbc.queryForObject("SELECT id FROM page_permission WHERE page_id=? AND role_id=?",Integer.class,pageId,readerId);
        int editorGrant = jdbc.queryForObject("SELECT id FROM page_permission WHERE page_id=? AND role_id=?",Integer.class,pageId,editorId);
        login("integration.admin").perform(post("/ui/page/" + pageId + "/edit").param("name","Shared").param("content","Updated")
                .param("permissionUpdates[" + readerGrant + "]","edit").param("permissionDeletions[]",Integer.toString(editorGrant))
                .param("permissionAdditions[0][roleType]","guest").param("permissionAdditions[0][roleId]","")
                .param("permissionAdditions[0][permissionLevel]","view")).andExpect(status().is3xxRedirection());
        assertEquals("edit",jdbc.queryForObject("SELECT permission_level FROM page_permission WHERE id=?",String.class,readerGrant));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM page_permission WHERE id=?",Integer.class,editorGrant));
        mvc.perform(get("/ui/page/" + pageId)).andExpect(status().isOk());
        mvc.perform(get("/ui/page/" + pageId + "/edit")).andExpect(status().isForbidden());
    }

    @Test void foreignPermissionIdRollsBackContentAndLabels() throws Exception {
        int foreign = grant(otherPageId,"guest",null,"view");
        login("integration.admin").perform(post("/ui/page/" + pageId + "/edit").param("name","Rollback").param("content","Rollback")
                .param("labels","rollback-label").param("permissionDeletions[]",Integer.toString(foreign)))
                .andExpect(status().isNotFound());
        assertEquals("original",jdbc.queryForObject("SELECT content FROM page WHERE id=?",String.class,pageId));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tag_content WHERE page_id=?",Integer.class,pageId));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM page_permission WHERE id=?",Integer.class,foreign));
    }

    @Test void newChildInheritsAndParentChangesAffectExistingSessions() throws Exception {
        String childName = "child-" + pageId;
        login(editor).perform(post("/ui/page/new").param("parent",Integer.toString(pageId)).param("name",childName)
                .param("content","Child").param("active","true")).andExpect(status().is3xxRedirection());
        int child = jdbc.queryForObject("SELECT id FROM page WHERE name=?",Integer.class,childName);
        assertTrue(jdbc.queryForObject("SELECT inherit_permissions FROM page WHERE id=?",Boolean.class,child));
        var session = login(reader);
        session.perform(get("/ui/page/" + child)).andExpect(status().isOk());
        jdbc.update("DELETE FROM page_permission WHERE page_id=? AND role_type='user' AND role_id=?",pageId,readerId);
        session.perform(get("/ui/page/" + child)).andExpect(status().isForbidden());
    }

    @Test void missingCsrfCannotChangeStoredContent() throws Exception {
        var session = login(editor);
        mvc.perform(post("/ui/page/" + pageId + "/edit").cookie(session.cookies).param("name","No CSRF").param("content","Denied"))
                .andExpect(status().isForbidden());
        assertEquals("original",jdbc.queryForObject("SELECT content FROM page WHERE id=?",String.class,pageId));
    }
}
