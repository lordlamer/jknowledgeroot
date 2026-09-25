package org.knowledgeroot.app.page.ui;

import org.junit.jupiter.api.Test;
import org.knowledgeroot.app.security.auth.PasswordService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductFunctionsIntegrationTest extends IsolatedApplicationTest {
    static final String NEW_PASSWORD = "new-profile-password-2026";
    long revision(int id) { return jdbc.queryForObject("SELECT revision FROM page WHERE id=?", Long.class, id); }
    String hash(int id) { return jdbc.queryForObject("SELECT password FROM user WHERE id=?", String.class, id); }
    int parent(int id) { return jdbc.queryForObject("SELECT parent FROM page WHERE id=?", Integer.class, id); }
    int latest(int id) { return jdbc.queryForObject("SELECT MAX(id) FROM page_history WHERE page_id=?", Integer.class, id); }

    @Test void profileRequiresCurrentPasswordAndMatchingStrongReplacementWithoutEchoingSecrets() throws Exception {
        var user = login(editor); String original = hash(editorId);
        user.perform(get("/profile")).andExpect(status().isOk()).andExpect(content().string(containsString("Current password")));
        for (var values : List.of(List.of("wrong-current-secret", NEW_PASSWORD, NEW_PASSWORD),
                List.of(PASSWORD, NEW_PASSWORD, "different-confirmation"), List.of(PASSWORD, "short", "short"))) {
            user.perform(post("/profile/password").param("currentPassword", values.get(0))
                    .param("newPassword", values.get(1)).param("confirmPassword", values.get(2)))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("alert-danger")))
                    .andExpect(content().string(not(containsString(NEW_PASSWORD))))
                    .andExpect(content().string(not(containsString("wrong-current-secret"))));
        }
        assertEquals(original, hash(editorId));
    }

    @Test void passwordChangeAffectsOnlyCurrentUserAndEndsAllPreviousSessions() throws Exception {
        var first = login(editor); var second = login(editor); String outsiderHash = hash(outsiderId);
        first.perform(post("/profile/password").param("currentPassword", PASSWORD).param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD).param("userId", String.valueOf(outsiderId)))
                .andExpect(redirectedUrl("/login?passwordChanged=true"));
        assertTrue(new PasswordService().matches(NEW_PASSWORD, hash(editorId)));
        assertFalse(new PasswordService().matches(PASSWORD, hash(editorId)));
        assertEquals(outsiderHash, hash(outsiderId));
        first.perform(get("/profile")).andExpect(status().is3xxRedirection());
        second.perform(get("/profile")).andExpect(status().is3xxRedirection());
        login(editor, NEW_PASSWORD).perform(get("/profile")).andExpect(status().isOk());
    }

    @Test void passwordChangeRequiresSessionCsrfAndEnforcesPersistentReauthenticationQuota() throws Exception {
        var user = login(editor); String original = hash(editorId);
        mvc.perform(post("/profile/password").cookie(user.cookies).param("currentPassword", PASSWORD)
                .param("newPassword", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD)).andExpect(status().isForbidden());
        String bucket = PasswordService.credentialTag("password-change-account:" + editorId);
        jdbc.update("INSERT INTO login_attempt(bucket_key,window_start,attempts) VALUES (?,CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS UNSIGNED),10000)", bucket);
        user.perform(post("/profile/password").param("currentPassword", PASSWORD).param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD)).andExpect(status().isOk())
                .andExpect(content().string(containsString("try again later")));
        assertEquals(original, hash(editorId));
        assertEquals(10001, jdbc.queryForObject("SELECT attempts FROM login_attempt WHERE bucket_key=?", Integer.class, bucket));
    }

    @Test void aLoginNameCannotConsumeTheSeparatePasswordChangeQuota() throws Exception {
        var user = login(editor);
        String loginBucket = PasswordService.credentialTag("account:password-change:" + editorId);
        jdbc.update("INSERT INTO login_attempt(bucket_key,window_start,attempts) VALUES (?,CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS UNSIGNED),10000)", loginBucket);
        user.perform(post("/profile/password").param("currentPassword", PASSWORD).param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD)).andExpect(redirectedUrl("/login?passwordChanged=true"));
    }

    @Test void movingSubtreeChangesInheritedRightsAndPreservesHistoryAndPageIdentity() throws Exception {
        int destination = page("destination-" + pageId), child = page("child-" + pageId);
        jdbc.update("UPDATE page SET parent=?, inherit_permissions=TRUE WHERE id=?", otherPageId, pageId);
        jdbc.update("UPDATE page SET parent=?, inherit_permissions=TRUE WHERE id=?", pageId, child);
        grant(otherPageId, "user", readerId, "view"); grant(destination, "user", outsiderId, "view");
        var readerClient = login(reader); var outsiderClient = login(outsider);
        readerClient.perform(get("/ui/page/" + child)).andExpect(status().isOk());
        outsiderClient.perform(get("/ui/page/" + child)).andExpect(status().isForbidden());
        var admin = login("integration.admin");
        admin.perform(get("/ui/page/" + pageId + "/move")).andExpect(status().isOk());
        admin.perform(post("/ui/page/" + pageId + "/move").param("parent", String.valueOf(destination)).param("revision", "0"))
                .andExpect(status().is3xxRedirection());
        assertEquals(destination, parent(pageId)); assertEquals(pageId, parent(child));
        assertEquals(1, revision(pageId)); assertEquals(1, revision(child));
        assertEquals(otherPageId, jdbc.queryForObject("SELECT parent FROM page_history WHERE id=?", Integer.class, latest(pageId)));
        readerClient.perform(get("/ui/page/" + child)).andExpect(status().isForbidden());
        outsiderClient.perform(get("/ui/page/" + child)).andExpect(status().isOk());
    }

    @Test void movingToTopLevelMaterializesEffectiveGrants() throws Exception {
        jdbc.update("UPDATE page SET parent=?, inherit_permissions=TRUE WHERE id=?", otherPageId, pageId);
        grant(otherPageId, "user", readerId, "view");
        login("integration.admin").perform(post("/ui/page/" + pageId + "/move").param("parent", "0").param("revision", "0"))
                .andExpect(status().is3xxRedirection());
        assertEquals(0, parent(pageId));
        assertFalse(jdbc.queryForObject("SELECT inherit_permissions FROM page WHERE id=?", Boolean.class, pageId));
        jdbc.update("DELETE FROM page_permission WHERE page_id=?", otherPageId);
        login(reader).perform(get("/ui/page/" + pageId)).andExpect(status().isOk());
    }

    @Test void movingKeepsLocalGrantsAndInvalidatesPreviouslyOpenedChildEditors() throws Exception {
        int child = page("local-child-" + pageId);
        jdbc.update("UPDATE page SET parent=?, inherit_permissions=TRUE WHERE id=?", pageId, child);
        var editorClient = login(editor);
        login("integration.admin").perform(post("/ui/page/" + pageId + "/move").param("parent", String.valueOf(otherPageId)).param("revision", "0"))
                .andExpect(status().is3xxRedirection());
        editorClient.perform(get("/ui/page/" + child)).andExpect(status().isOk());
        editorClient.perform(post("/ui/page/" + child + "/edit").param("name", "stale").param("revision", "0"))
                .andExpect(status().isConflict());
        assertEquals("original", jdbc.queryForObject("SELECT content FROM page WHERE id=?", String.class, child));
    }

    @Test void movePickerStartsAtCurrentLocationAndHidesTheMovedPage() throws Exception {
        jdbc.update("UPDATE page SET parent=? WHERE id=?", otherPageId, pageId);
        var admin = login("integration.admin");
        String url = "/ui/page/" + pageId + "/move";
        admin.perform(get(url)).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"parent\" value=\"" + otherPageId + "\"")))
                .andExpect(content().string(containsString("The page is already here.")));
        admin.perform(get(url).param("parent", "0")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("parent=" + pageId + "\""))))
                .andExpect(content().string(not(containsString("The page is already here."))));
    }

    @Test void movesRejectReadersCsrfCyclesDeletedTargetsAndStaleRevisions() throws Exception {
        String url = "/ui/page/" + pageId + "/move";
        login(reader).perform(get(url)).andExpect(status().isForbidden());
        login(reader).perform(post(url).param("parent", "0").param("revision", "0")).andExpect(status().isForbidden());
        var admin = login("integration.admin");
        mvc.perform(post(url).cookie(admin.cookies).param("parent", "0").param("revision", "0")).andExpect(status().isForbidden());
        admin.perform(post(url).param("parent", "0")).andExpect(status().is(428));
        admin.perform(post(url).param("parent", "0").param("revision", "99")).andExpect(status().isConflict());
        int child = page("cycle-child-" + pageId); jdbc.update("UPDATE page SET parent=? WHERE id=?", pageId, child);
        for (int target : List.of(pageId, child)) admin.perform(post(url).param("parent", String.valueOf(target)).param("revision", "0")).andExpect(status().isConflict());
        jdbc.update("UPDATE page SET deleted=TRUE WHERE id=?", otherPageId);
        admin.perform(post(url).param("parent", String.valueOf(otherPageId)).param("revision", "0")).andExpect(status().isConflict());
        assertEquals(0, parent(pageId)); assertEquals(0, revision(pageId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM page_history WHERE page_id=?", Integer.class, pageId));
    }

    @Test void editorNeedsEditOnBothSourceAndDestinationAndPickerHidesPrivatePages() throws Exception {
        var client = login(editor);
        String url = "/ui/page/" + pageId + "/move";
        String privateName = jdbc.queryForObject("SELECT name FROM page WHERE id=?", String.class, otherPageId);
        client.perform(get("/ui/page/" + pageId)).andExpect(content().string(containsString("Move page")));
        client.perform(get(url).param("query", privateName)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString(">" + privateName + "</a>"))));
        client.perform(get(url).param("parent", "" + otherPageId)).andExpect(status().isForbidden());
        client.perform(post(url).param("parent", "" + otherPageId).param("revision", "0")).andExpect(status().isForbidden());
        int permission = grant(otherPageId, "user", editorId, "view");
        client.perform(get(url).param("parent", "" + otherPageId)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Choose a destination you can edit")));
        client.perform(post(url).param("parent", "" + otherPageId).param("revision", "0")).andExpect(status().isForbidden());
        assertEquals(0, revision(pageId));
        jdbc.update("UPDATE page_permission SET permission_level='edit' WHERE id=?", permission);
        client.perform(post(url).param("parent", "" + otherPageId).param("revision", "0")).andExpect(status().isFound());
        assertEquals(otherPageId, parent(pageId));
        assertEquals(editorId, jdbc.queryForObject("SELECT changed_by FROM page WHERE id=?", Integer.class, pageId));
        assertEquals(0, jdbc.queryForObject("SELECT parent FROM page_history WHERE id=?", Integer.class, latest(pageId)));
    }

    @Test void inheritedDestinationEditRightsAllowMovesAndRevocationIsRechecked() throws Exception {
        int destination = page("inherited-target-" + pageId);
        jdbc.update("UPDATE page SET parent=?,inherit_permissions=TRUE WHERE id=?", otherPageId, destination);
        int grant = grant(otherPageId, "user", editorId, "edit");
        var client = login(editor);
        String url = "/ui/page/" + pageId + "/move";
        client.perform(get(url).param("parent", "" + destination)).andExpect(status().isOk());
        jdbc.update("DELETE FROM page_permission WHERE id=?", grant);
        client.perform(post(url).param("parent", "" + destination).param("revision", "0")).andExpect(status().isForbidden());
        grant(otherPageId, "user", editorId, "edit");
        client.perform(post(url).param("parent", "" + destination).param("revision", "0")).andExpect(status().isFound());
        assertEquals(destination, parent(pageId));
        client.perform(post(url).param("parent", "0").param("revision", "1")).andExpect(status().isFound());
        assertEquals(0, parent(pageId));
    }

    @Test void sourceRevocationAndGuestEditGrantsCannotBypassMoveAuthorization() throws Exception {
        grant(otherPageId, "user", editorId, "edit");
        var client = login(editor);
        String url = "/ui/page/" + pageId + "/move";
        client.perform(get(url)).andExpect(status().isOk());
        jdbc.update("DELETE FROM page_permission WHERE page_id=? AND role_id=?", pageId, editorId);
        client.perform(post(url).param("parent", "" + otherPageId).param("revision", "0")).andExpect(status().isForbidden());
        grant(pageId, "guest", null, "edit"); grant(otherPageId, "guest", null, "edit");
        mvc.perform(get(url)).andExpect(status().isForbidden());
        assertEquals(0, parent(pageId)); assertEquals(0, revision(pageId));
    }

    @Test void destinationGroupEditGrantsAreEffectiveAndMembershipRevocationIsImmediate() throws Exception {
        String groupName = "move-editors-" + pageId;
        jdbc.update("INSERT INTO `group` (name,active,created_by,create_date,changed_by,change_date) VALUES (?,1,?,NOW(),?,NOW())", groupName, adminId, adminId);
        int group = jdbc.queryForObject("SELECT id FROM `group` WHERE name=?", Integer.class, groupName);
        jdbc.update("INSERT INTO group_member(group_id,member_id,member_type) VALUES (?,?,'user')", group, editorId);
        grant(otherPageId, "group", group, "edit");
        var client = login(editor); String url = "/ui/page/" + pageId + "/move";
        client.perform(get(url).param("parent", "" + otherPageId)).andExpect(status().isOk());
        jdbc.update("DELETE FROM group_member WHERE group_id=? AND member_id=?", group, editorId);
        client.perform(post(url).param("parent", "" + otherPageId).param("revision", "0")).andExpect(status().isForbidden());
        jdbc.update("INSERT INTO group_member(group_id,member_id,member_type) VALUES (?,?,'user')", group, editorId);
        client.perform(post(url).param("parent", "" + otherPageId).param("revision", "0")).andExpect(status().isFound());
        assertEquals(otherPageId, parent(pageId));
    }

    @Test void simultaneousOppositeMovesCannotCreateACycle() throws Exception {
        var first = login("integration.admin"); var second = login("integration.admin");
        var go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { assertTrue(go.await(10, TimeUnit.SECONDS)); return first.perform(post("/ui/page/" + pageId + "/move")
                    .param("parent", String.valueOf(otherPageId)).param("revision", "0")).andReturn().getResponse().getStatus(); });
            var b = pool.submit(() -> { assertTrue(go.await(10, TimeUnit.SECONDS)); return second.perform(post("/ui/page/" + otherPageId + "/move")
                    .param("parent", String.valueOf(pageId)).param("revision", "0")).andReturn().getResponse().getStatus(); });
            go.countDown();
            var statuses = List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
            assertTrue(statuses.contains(302), statuses.toString()); assertTrue(statuses.contains(409), statuses.toString());
        }
        assertTrue(parent(pageId) == 0 || parent(otherPageId) == 0);
    }

    @Test void compareSupportsSavedAndCurrentVersionsWithCurrentRightsAndEscapedDiffs() throws Exception {
        var editorClient = login(editor);
        editorClient.perform(post("/ui/page/" + pageId + "/edit").param("name", "Second name").param("content", "<p>Second text</p>")
                .param("revision", "0")).andExpect(status().is3xxRedirection());
        int first = latest(pageId);
        editorClient.perform(post("/ui/page/" + pageId + "/edit").param("name", "Third name").param("content", "<p>Third text</p>")
                .param("revision", "1")).andExpect(status().is3xxRedirection());
        int second = latest(pageId);
        String url = "/ui/page/" + pageId + "/history/compare";
        editorClient.perform(get(url).param("from", String.valueOf(first)).param("to", String.valueOf(second)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Second text")))
                .andExpect(content().string(not(containsString("Third text"))))
                .andExpect(content().string(containsString("&lt;p&gt;")));
        editorClient.perform(get(url).param("from", String.valueOf(second)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Third text")));
        for (int i = 0; i < 50; i++) jdbc.update("""
                INSERT INTO page_history(page_id,version,parent,name,content,active,deleted,created_by,create_date,changed_by,change_date)
                SELECT page_id,version,parent,name,content,active,deleted,created_by,create_date,changed_by,change_date FROM page_history WHERE id=?
                """, first);
        editorClient.perform(get("/ui/page/" + pageId + "/history").param("compareFrom", String.valueOf(second)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("start=50")))
                .andExpect(content().string(containsString("compareFrom=" + second)));
        editorClient.perform(get("/ui/page/" + pageId + "/history").param("start", "50").param("compareFrom", String.valueOf(second)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Compare with selected")));
        login(reader).perform(get(url).param("from", String.valueOf(first))).andExpect(status().isForbidden());
        login(outsider).perform(get(url).param("from", String.valueOf(first))).andExpect(status().isForbidden());
        editorClient.perform(get("/ui/page/" + otherPageId + "/history/compare").param("from", String.valueOf(first))).andExpect(status().isForbidden());
        login("integration.admin").perform(get("/ui/page/" + otherPageId + "/history/compare").param("from", String.valueOf(first))).andExpect(status().isNotFound());
        jdbc.update("UPDATE page_history SET content='<script>alert(1)</script><p>Safe</p>' WHERE id=?", first);
        editorClient.perform(get(url).param("from", String.valueOf(first))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }
}
