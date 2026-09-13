package org.knowledgeroot.app.page.ui;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PageRevisionIntegrationTest extends IsolatedApplicationTest {
    long revision(int id) { return jdbc.queryForObject("SELECT revision FROM page WHERE id=?", Long.class, id); }
    int latest(int id) { return jdbc.queryForObject("SELECT MAX(id) FROM page_history WHERE page_id=?", Integer.class,id); }

    @Test void staleOrMissingRevisionCannotChangeContentLabelsOrHistory() throws Exception {
        var session = login(editor);
        session.perform(post("/ui/page/"+pageId+"/edit").param("name","first").param("content","winner")
                .param("labels","new").param("revision","0")).andExpect(status().is3xxRedirection());
        session.perform(post("/ui/page/"+pageId+"/edit").param("name","stale").param("content","loser")
                .param("labels","lost").param("revision","0")).andExpect(status().isConflict());
        session.perform(post("/ui/page/"+pageId+"/edit").param("name","missing")).andExpect(status().is(428));
        assertEquals("winner",jdbc.queryForObject("SELECT content FROM page WHERE id=?",String.class,pageId));
        assertEquals(1,revision(pageId));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM page_history WHERE page_id=?",Integer.class,pageId));
        assertEquals(List.of("new"),jdbc.queryForList("SELECT t.name FROM tag t JOIN tag_content tc ON t.id=tc.tag_id WHERE tc.page_id=?",String.class,pageId));
    }

    @Test void simultaneousSavesProduceOneWinnerAndOneSnapshot() throws Exception {
        var first=login(editor); var second=login(editor);
        var ready=new CountDownLatch(2); var go=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var jobs=List.of(first,second).stream().map(client -> executor.submit(() -> {
                ready.countDown(); assertTrue(go.await(10,TimeUnit.SECONDS));
                return client.perform(post("/ui/page/"+pageId+"/edit").param("name","concurrent")
                        .param("content","saved").param("revision","0")).andReturn().getResponse().getStatus();
            })).toList();
            assertTrue(ready.await(10,TimeUnit.SECONDS)); go.countDown();
            var statuses=List.of(jobs.get(0).get(20,TimeUnit.SECONDS),jobs.get(1).get(20,TimeUnit.SECONDS));
            assertTrue(statuses.contains(302), statuses.toString()); assertTrue(statuses.contains(409), statuses.toString());
        }
        assertEquals(1,revision(pageId));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM page_history WHERE page_id=?",Integer.class,pageId));
    }

    @Test void historyAndRestoreUseCurrentEditRightsAndKeepSharing() throws Exception {
        jdbc.update("INSERT INTO tag(name) VALUES (?)","old-"+pageId);
        jdbc.update("INSERT INTO tag_content(tag_id,page_id) SELECT id,? FROM tag WHERE name=?",pageId,"old-"+pageId);
        var session=login(editor);
        session.perform(post("/ui/page/"+pageId+"/edit").param("name","changed").param("content","changed")
                .param("labels","new").param("revision","0")).andExpect(status().is3xxRedirection());
        int saved=latest(pageId);
        login(reader).perform(get("/ui/page/"+pageId+"/history")).andExpect(status().isForbidden());
        login(outsider).perform(get("/ui/page/"+pageId+"/history/"+saved)).andExpect(status().isForbidden());
        session.perform(get("/ui/page/"+pageId+"/history")).andExpect(status().isOk());
        session.perform(get("/ui/page/"+pageId+"/history/"+saved)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("original")));
        session.perform(get("/ui/page/"+otherPageId+"/history/"+saved)).andExpect(status().isForbidden());
        login("integration.admin").perform(get("/ui/page/"+otherPageId+"/history/"+saved)).andExpect(status().isNotFound());
        var sharing=jdbc.queryForList("SELECT * FROM page_permission WHERE page_id=? ORDER BY id",pageId);
        mvc.perform(post("/ui/page/"+pageId+"/history/"+saved+"/restore").cookie(session.cookies).param("revision","1"))
                .andExpect(status().isForbidden());
        session.perform(post("/ui/page/"+pageId+"/history/"+saved+"/restore").param("revision","0")).andExpect(status().isConflict());
        session.perform(post("/ui/page/"+pageId+"/history/"+saved+"/restore").param("revision","1")).andExpect(status().is3xxRedirection());
        assertEquals("original",jdbc.queryForObject("SELECT content FROM page WHERE id=?",String.class,pageId));
        assertEquals(sharing,jdbc.queryForList("SELECT * FROM page_permission WHERE page_id=? ORDER BY id",pageId));
        assertEquals(List.of("old-"+pageId),jdbc.queryForList("SELECT t.name FROM tag t JOIN tag_content tc ON t.id=tc.tag_id WHERE tc.page_id=?",String.class,pageId));
        assertEquals(2,revision(pageId));
        jdbc.update("DELETE FROM page_permission WHERE page_id=? AND role_id=?",pageId,editorId);
        session.perform(post("/ui/page/"+pageId+"/history/"+saved+"/restore").param("revision","2")).andExpect(status().isForbidden());
    }

    @Test void restoredLegacyHtmlIsSanitizedAndMissingCapturedLabelsAreKept() throws Exception {
        var session=login(editor);
        session.perform(post("/ui/page/"+pageId+"/edit").param("name","snapshot").param("content","new")
                .param("labels","keep").param("revision","0")).andExpect(status().is3xxRedirection());
        int saved=latest(pageId);
        jdbc.update("UPDATE page_history SET content='<script>alert(1)</script><p>safe</p>',labels_captured=FALSE WHERE id=?",saved);
        session.perform(get("/ui/page/"+pageId+"/history/"+saved)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>alert(1)</script>"))));
        session.perform(post("/ui/page/"+pageId+"/history/"+saved+"/restore").param("revision","1")).andExpect(status().is3xxRedirection());
        assertFalse(jdbc.queryForObject("SELECT content FROM page WHERE id=?",String.class,pageId).contains("<script"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tag_content WHERE page_id=?",Integer.class,pageId));
    }

    @Test void deletionProtectsChildrenAndRetainsAttachmentsForAdministratorRestore() throws Exception {
        var session=login(editor); var admin=login("integration.admin");
        session.perform(multipart("/ui/file").file(new org.springframework.mock.web.MockMultipartFile("file","keep.txt","text/plain",new byte[]{4,2}))
                .param("pageId",Integer.toString(pageId))).andExpect(status().is3xxRedirection());
        int file=jdbc.queryForObject("SELECT id FROM file WHERE page_id=?",Integer.class,pageId);
        int child=page("child-delete-"+pageId);
        jdbc.update("UPDATE page SET parent=?,inherit_permissions=TRUE WHERE id=?",pageId,child);
        session.perform(delete("/ui/page/"+pageId).param("revision","0")).andExpect(status().isConflict());
        session.perform(delete("/ui/page/"+child).param("revision","0")).andExpect(status().is3xxRedirection());
        int childSaved=latest(child);
        session.perform(delete("/ui/page/"+pageId).param("revision","0")).andExpect(status().is3xxRedirection());
        int saved=latest(pageId);
        session.perform(get("/ui/page/"+pageId+"/history")).andExpect(status().isForbidden());
        session.perform(get("/ui/file/"+file+"/download/keep.txt")).andExpect(status().isForbidden());
        admin.perform(get("/ui/page/deleted")).andExpect(status().isOk());
        admin.perform(post("/ui/page/"+child+"/history/"+childSaved+"/restore").param("revision","1")).andExpect(status().isConflict());
        admin.perform(post("/ui/page/"+pageId+"/history/"+saved+"/restore").param("revision","1")).andExpect(status().is3xxRedirection());
        admin.perform(post("/ui/page/"+child+"/history/"+childSaved+"/restore").param("revision","1")).andExpect(status().is3xxRedirection());
        session.perform(get("/ui/file/"+file+"/download/keep.txt")).andExpect(status().isOk()).andExpect(content().bytes(new byte[]{4,2}));
        admin.perform(delete("/page")).andExpect(status().isMethodNotAllowed());
    }

    @Test void restRequiresVersionAndCannotBypassDeletionPolicy() throws Exception {
        var admin=login("integration.admin");
        String url="/page/"+pageId;
        admin.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("{\"id\":"+pageId+",\"name\":\"new\"}"))
                .andExpect(status().is(428));
        admin.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("{\"id\":"+pageId+",\"name\":\"new\",\"revision\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));
        admin.perform(delete(url).param("revision","0")).andExpect(status().isConflict());
        admin.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("{\"id\":"+pageId+",\"name\":\"new\",\"revision\":1,\"deleted\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test void separateSharingChangeInvalidatesAnAlreadyOpenedEditForm() throws Exception {
        var admin=login("integration.admin");
        admin.perform(post("/ui/page/"+pageId+"/permission").param("roleType","guest").param("permissionLevel","view"))
                .andExpect(status().isOk());
        admin.perform(post("/ui/page/"+pageId+"/edit").param("name","stale").param("revision","0"))
                .andExpect(status().isConflict());
        assertEquals("original",jdbc.queryForObject("SELECT content FROM page WHERE id=?",String.class,pageId));
    }

    @Test void creatingAChildConcurrentlyWithParentDeletionCannotOrphanIt() throws Exception {
        var creator=login(editor); var deleter=login(editor);
        var go=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var create=executor.submit(() -> {
                assertTrue(go.await(10,TimeUnit.SECONDS));
                return creator.perform(post("/ui/page/new").param("parent",Integer.toString(pageId))
                        .param("name","racing-child-"+pageId)).andReturn().getResponse().getStatus();
            });
            var delete=executor.submit(() -> {
                assertTrue(go.await(10,TimeUnit.SECONDS));
                return deleter.perform(delete("/ui/page/"+pageId).param("revision","0")).andReturn().getResponse().getStatus();
            });
            go.countDown();
            assertTrue(List.of(302,403,409).contains(create.get(20,TimeUnit.SECONDS)));
            assertTrue(List.of(302,409).contains(delete.get(20,TimeUnit.SECONDS)));
        }
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM page child JOIN page parent ON child.parent=parent.id WHERE child.parent=? AND child.deleted=FALSE AND parent.deleted=TRUE",Integer.class,pageId));
    }
}
