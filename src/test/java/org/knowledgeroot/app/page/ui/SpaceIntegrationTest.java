package org.knowledgeroot.app.page.ui;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.space.SpaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.knowledgeroot.app.page.domain.PagePermission.PermissionLevel.EDIT;
import static org.knowledgeroot.app.page.domain.PagePermission.PermissionLevel.VIEW;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Spaces as the outer permission gate, root pages inheriting from their space, and the space UI. */
class SpaceIntegrationTest extends IsolatedApplicationTest {
    @Autowired SpaceRepository spaces;
    @Autowired PagePermissionDao permissions;

    /** Cookie jar with the real CSRF cookie and header; see SecondFactorWebIntegrationTest. */
    class Browser {
        final Map<String, Cookie> cookies = new LinkedHashMap<>();
        String csrf;
        boolean stale = true;

        ResultActions perform(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
            if (!cookies.isEmpty()) request.cookie(cookies.values().toArray(Cookie[]::new));
            if (csrf != null) request.header("X-XSRF-TOKEN", csrf);
            var result = mvc.perform(request);
            for (Cookie cookie : result.andReturn().getResponse().getCookies()) {
                if (cookie.getMaxAge() == 0) cookies.remove(cookie.getName());
                else cookies.put(cookie.getName(), cookie);
                if (cookie.getName().equals("XSRF-TOKEN")) stale = true;
            }
            return result;
        }

        void refreshCsrf() throws Exception {
            var request = get("/api/v1/session");
            if (!cookies.isEmpty()) request.cookie(cookies.values().toArray(Cookie[]::new));
            MockHttpServletResponse response = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();
            for (Cookie cookie : response.getCookies()) if (cookie.getMaxAge() != 0) cookies.put(cookie.getName(), cookie);
            csrf = JsonPath.read(response.getContentAsString(), "$.csrf.token");
            stale = false;
        }

        ResultActions post(String path, String... params) throws Exception {
            if (stale) refreshCsrf();
            var request = MockMvcRequestBuilders.post(path);
            for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
            return perform(request);
        }

        String page(String path) throws Exception {
            return perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        }
    }

    private Browser signIn(String login) throws Exception {
        var browser = new Browser();
        browser.post("/logmein", "username", login, "password", PASSWORD).andExpect(redirectedUrl("/login/success"));
        return browser;
    }

    private int space(String key) {
        jdbc.update("INSERT INTO space (space_key, name, description, create_date, change_date) VALUES (?, ?, '', NOW(3), NOW(3))", key, key + " space");
        return jdbc.queryForObject("SELECT id FROM space WHERE space_key = ?", Integer.class, key);
    }

    private void spaceGrant(int spaceId, String role, Integer roleId, String level) {
        jdbc.update("INSERT INTO space_permission (space_id, role_type, role_id, permission_level, create_date, change_date) VALUES (?, ?, ?, ?, NOW(3), NOW(3))",
                spaceId, role, roleId, level);
    }

    private int rootIn(int spaceId, String name, boolean inheritFromSpace) {
        jdbc.update("INSERT INTO page (space_id, parent, name, content, active, inherit_permissions, created_by, create_date, changed_by, change_date) VALUES (?, 0, ?, 'text', TRUE, ?, ?, NOW(), ?, NOW())",
                spaceId, name, inheritFromSpace, adminId, adminId);
        return jdbc.queryForObject("SELECT id FROM page WHERE name = ?", Integer.class, name);
    }

    @Test void existingPagesLiveInTheDefaultSpaceEveryoneMayEnter() throws Exception {
        var kr = spaces.findByKey("KR").orElseThrow();
        assertEquals(kr.getId(), jdbc.queryForObject("SELECT space_id FROM page WHERE id = ?", Integer.class, pageId));
        assertTrue(spaces.listVisible(null).stream().anyMatch(s -> s.getId().equals(kr.getId())), "guests may enter the default space");
        assertTrue(spaces.listVisible(outsiderId).stream().anyMatch(s -> s.getId().equals(kr.getId())));
        assertTrue(new Browser().page("/spaces").contains("Knowledgeroot"));
        // Page permissions still decide inside the space.
        assertTrue(permissions.hasUserPermission(new PageId(pageId), readerId, VIEW));
        assertFalse(permissions.hasUserPermission(new PageId(pageId), outsiderId, VIEW));
    }

    @Test void withoutAGrantOnTheSpaceNoPageInsideIsReachable() throws Exception {
        int team = space("TEAM" + pageId);
        int page = rootIn(team, "team-root-" + pageId, false);
        jdbc.update("INSERT INTO page_permission (page_id, role_type, role_id, permission_level, created_by, create_date, changed_by, change_date) VALUES (?, 'guest', NULL, 'view', ?, NOW(), ?, NOW())", page, adminId, adminId);
        // The page itself is public, but the space is not.
        assertFalse(permissions.hasUserPermission(new PageId(page), null, VIEW));
        assertFalse(permissions.hasUserPermission(new PageId(page), outsiderId, VIEW));
        assertTrue(permissions.hasUserPermission(new PageId(page), adminId, VIEW));
        assertFalse(spaces.listVisible(outsiderId).stream().anyMatch(s -> s.getId() == team));
        signIn(outsider).perform(get("/ui/page/" + page)).andExpect(status().isForbidden());
        assertFalse(signIn(outsider).page("/spaces").contains("TEAM" + pageId));

        spaceGrant(team, "users", null, "view");
        assertTrue(permissions.hasUserPermission(new PageId(page), outsiderId, VIEW));
        assertFalse(permissions.hasUserPermission(new PageId(page), null, VIEW), "guests still have no grant on the space");
        assertTrue(spaces.listVisible(outsiderId).stream().anyMatch(s -> s.getId() == team));
        signIn(outsider).perform(get("/ui/page/" + page)).andExpect(status().isOk());
        assertTrue(signIn(outsider).page("/spaces").contains("TEAM" + pageId));
        // The space gate never grants more than the page permissions allow.
        assertFalse(permissions.hasUserPermission(new PageId(page), outsiderId, EDIT));
    }

    @Test void rootPagesInheritTheSpaceGrantsUntilSwitchedToLocalPermissions() {
        int team = space("INH" + pageId);
        spaceGrant(team, "user", readerId, "view");
        spaceGrant(team, "user", editorId, "edit");
        spaceGrant(team, "guest", null, "view");
        int root = rootIn(team, "inherit-root-" + pageId, true);
        assertTrue(permissions.inheritsFromSpace(new PageId(root)));
        assertNull(permissions.permissionSource(new PageId(root)));
        assertTrue(permissions.hasUserPermission(new PageId(root), readerId, VIEW));
        assertFalse(permissions.hasUserPermission(new PageId(root), readerId, EDIT));
        assertTrue(permissions.hasUserPermission(new PageId(root), editorId, EDIT));
        assertTrue(permissions.hasUserPermission(new PageId(root), null, VIEW));
        assertTrue(permissions.hasUserPermission(new PageId(root), outsiderId, VIEW), "a guest grant makes the space public to everyone");
        assertFalse(permissions.hasUserPermission(new PageId(root), outsiderId, EDIT));
        // Children inherit through the root from the space, and the set-based listing agrees.
        jdbc.update("INSERT INTO page (space_id, parent, name, content, active, inherit_permissions, created_by, create_date, changed_by, change_date) VALUES (?, ?, ?, 'text', TRUE, TRUE, ?, NOW(), ?, NOW())",
                team, root, "inherit-child-" + pageId, adminId, adminId);
        int child = jdbc.queryForObject("SELECT id FROM page WHERE name = ?", Integer.class, "inherit-child-" + pageId);
        assertTrue(permissions.hasUserPermission(new PageId(child), readerId, VIEW));
        assertTrue(permissions.hasUserPermission(new PageId(child), editorId, EDIT));
        jdbc.update("DELETE FROM space_permission WHERE space_id = ? AND role_type = ?", team, "guest");
        assertFalse(permissions.hasUserPermission(new PageId(child), outsiderId, VIEW));
        assertFalse(permissions.hasUserPermission(new PageId(root), null, VIEW));
        spaceGrant(team, "guest", null, "view");
        assertTrue(pages().stream().anyMatch(id -> id == child), "visible listing includes the inheriting child");

        permissions.setInheriting(new PageId(root), false, editorId);
        var grants = permissions.listPermissionsByPageId(new PageId(root));
        assertTrue(grants.stream().anyMatch(g -> g.getRoleType() == PagePermission.RoleType.USER && g.getRoleId().equals(readerId) && g.getPermissionLevel() == VIEW));
        assertTrue(grants.stream().anyMatch(g -> g.getRoleType() == PagePermission.RoleType.USER && g.getRoleId().equals(editorId) && g.getPermissionLevel() == EDIT));
        assertTrue(grants.stream().anyMatch(g -> g.getRoleType() == PagePermission.RoleType.GUEST && g.getPermissionLevel() == VIEW));
        assertEquals(3, grants.size());
        jdbc.update("DELETE FROM space_permission WHERE space_id = ? AND (role_type = 'guest' OR (role_type = 'user' AND role_id = ?))", team, readerId);
        assertFalse(permissions.hasUserPermission(new PageId(root), readerId, VIEW), "the space gate still applies to local permissions");
        permissions.setInheriting(new PageId(root), true, editorId);
        assertTrue(permissions.inheritsFromSpace(new PageId(root)));
        assertTrue(permissions.listPermissionsByPageId(new PageId(root)).isEmpty());
    }

    private java.util.List<Integer> pages() {
        var filter = new org.knowledgeroot.app.page.domain.PageFilter();
        filter.setLimit(101);
        return jdbc.queryForList("SELECT id FROM page WHERE name LIKE ?", Integer.class, "inherit-child-" + pageId).stream()
                .filter(id -> permissions.hasUserPermission(new PageId(id), readerId, VIEW)).toList();
    }

    @Test void archivedSpacesAreReadOnlyAndHiddenFromMembers() {
        int team = space("ARC" + pageId);
        spaceGrant(team, "user", editorId, "edit");
        spaceGrant(team, "user", readerId, "admin");
        int root = rootIn(team, "archived-root-" + pageId, true);
        assertTrue(permissions.hasUserPermission(new PageId(root), editorId, EDIT));
        jdbc.update("UPDATE space SET archived = TRUE WHERE id = ?", team);
        assertFalse(permissions.hasUserPermission(new PageId(root), editorId, VIEW), "members lose access to an archived space");
        assertTrue(permissions.hasUserPermission(new PageId(root), readerId, VIEW), "space administrators keep reading");
        assertFalse(permissions.hasUserPermission(new PageId(root), readerId, EDIT));
        assertTrue(permissions.hasUserPermission(new PageId(root), adminId, VIEW));
        assertFalse(permissions.hasUserPermission(new PageId(root), adminId, EDIT), "nobody edits an archived space");
        assertFalse(spaces.listVisible(editorId).stream().anyMatch(s -> s.getId() == team));
        assertTrue(spaces.listVisible(readerId).stream().anyMatch(s -> s.getId() == team));
    }

    @Test void spaceCreationIsAGlobalGrantAndTheCreatorAdministersTheSpace() throws Exception {
        var member = signIn(editor);
        member.perform(get("/spaces/new")).andExpect(status().isForbidden());
        member.post("/spaces", "key", "NEW" + pageId % 1000, "name", "Blocked").andExpect(status().isForbidden());

        var admin = signIn("integration.admin");
        assertTrue(admin.page("/admin/permissions").contains("Create spaces"));
        admin.post("/admin/permissions/create-space", "roleType", "user", "roleId", String.valueOf(editorId))
                .andExpect(redirectedUrl("/admin/permissions?saved"));
        admin.post("/admin/permissions/create-space", "roleType", "user", "roleId", String.valueOf(editorId)).andExpect(status().isOk())
                .andExpect(content().string(containsString("may already create spaces")));
        assertTrue(admin.page("/admin/permissions").contains("Remove"));

        String key = "N" + Integer.toString(pageId, 36).toUpperCase();
        member.perform(get("/spaces/new")).andExpect(status().isOk());
        member.post("/spaces", "key", "bad key", "name", "x").andExpect(status().isOk()).andExpect(content().string(containsString("upper-case")));
        var created = member.post("/spaces", "key", key.toLowerCase(), "name", "My space", "description", "Mine")
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        int spaceId = Integer.parseInt(created.replaceAll("\\D+", ""));
        var space = spaces.find(spaceId).orElseThrow();
        assertEquals(key, space.getKey());
        assertNotNull(space.getHomePageId(), "a home page is created with the space");
        assertEquals(spaceId, jdbc.queryForObject("SELECT space_id FROM page WHERE id = ?", Integer.class, space.getHomePageId()));
        assertTrue(permissions.inheritsFromSpace(new PageId(space.getHomePageId())));
        assertTrue(permissions.hasUserPermission(new PageId(space.getHomePageId()), editorId, EDIT), "the creator administers and edits");
        assertFalse(permissions.hasUserPermission(new PageId(space.getHomePageId()), readerId, VIEW));
        member.post("/spaces", "key", key, "name", "Duplicate").andExpect(status().isOk()).andExpect(content().string(containsString("already exists")));

        String settings = member.page("/spaces/" + spaceId + "/settings");
        assertTrue(settings.contains("My space") && settings.contains("Permissions"));
        signIn(reader).perform(get("/spaces/" + spaceId + "/settings")).andExpect(status().isForbidden());
        member.post("/spaces/" + spaceId + "/permissions", "roleType", "users", "level", "view").andExpect(redirectedUrl("/spaces/" + spaceId + "/settings?saved"));
        assertTrue(permissions.hasUserPermission(new PageId(space.getHomePageId()), readerId, VIEW));
        member.post("/spaces/" + spaceId + "/permissions", "roleType", "users", "level", "admin").andExpect(status().isOk())
                .andExpect(content().string(containsString("Only users and groups")));
        int grant = jdbc.queryForObject("SELECT id FROM space_permission WHERE space_id = ? AND role_type = 'users'", Integer.class, spaceId);
        member.post("/spaces/" + spaceId + "/permissions/" + grant, "level", "edit").andExpect(redirectedUrl("/spaces/" + spaceId + "/settings?saved"));
        assertTrue(permissions.hasUserPermission(new PageId(space.getHomePageId()), readerId, EDIT));
        member.post("/spaces/" + spaceId + "/settings", "name", "Renamed", "description", "", "homePageId", "0").andExpect(redirectedUrl("/spaces/" + spaceId + "/settings?saved"));
        assertNull(spaces.find(spaceId).orElseThrow().getHomePageId());
        member.post("/spaces/" + spaceId + "/settings", "name", "Renamed", "homePageId", String.valueOf(pageId)).andExpect(status().isOk())
                .andExpect(content().string(containsString("must be a page of this space")));
        member.post("/spaces/" + spaceId + "/permissions/" + grant + "/delete").andExpect(redirectedUrl("/spaces/" + spaceId + "/settings?saved"));
        assertFalse(permissions.hasUserPermission(new PageId(space.getHomePageId()), readerId, VIEW));
        member.post("/spaces/" + spaceId + "/archive", "archived", "true").andExpect(redirectedUrl("/spaces/" + spaceId + "/settings?saved"));
        assertTrue(spaces.find(spaceId).orElseThrow().isArchived());
        assertFalse(permissions.hasUserPermission(new PageId(space.getHomePageId()), editorId, EDIT));
        member.post("/spaces/" + spaceId + "/archive", "archived", "false").andExpect(redirectedUrl("/spaces/" + spaceId + "/settings?saved"));
        assertTrue(permissions.hasUserPermission(new PageId(space.getHomePageId()), editorId, EDIT));

        int grantId = jdbc.queryForObject("SELECT id FROM global_permission WHERE role_type = 'user' AND role_id = ?", Integer.class, editorId);
        admin.post("/admin/permissions/create-space/" + grantId + "/delete").andExpect(redirectedUrl("/admin/permissions?saved"));
        member.perform(get("/spaces/new")).andExpect(status().isForbidden());
    }

    @Test void movingUnderAParentInAnotherSpaceTakesTheSubtreeAlong() throws Exception {
        int team = space("MOV" + pageId);
        int target = rootIn(team, "move-target-" + pageId, true);
        jdbc.update("INSERT INTO page (space_id, parent, name, content, active, inherit_permissions, created_by, create_date, changed_by, change_date) VALUES (1, ?, ?, 'text', TRUE, TRUE, ?, NOW(), ?, NOW())",
                pageId, "move-child-" + pageId, adminId, adminId);
        int child = jdbc.queryForObject("SELECT id FROM page WHERE name = ?", Integer.class, "move-child-" + pageId);
        var admin = signIn("integration.admin");
        admin.post("/ui/page/" + pageId + "/move", "parent", String.valueOf(target), "revision", "0")
                .andExpect(status().is3xxRedirection());
        assertEquals(team, jdbc.queryForObject("SELECT space_id FROM page WHERE id = ?", Integer.class, pageId));
        assertEquals(team, jdbc.queryForObject("SELECT space_id FROM page WHERE id = ?", Integer.class, child));
        assertFalse(permissions.hasUserPermission(new PageId(pageId), readerId, VIEW), "reader has a page grant but no grant on the new space");
        spaceGrant(team, "user", readerId, "view");
        assertTrue(permissions.hasUserPermission(new PageId(pageId), readerId, VIEW));
    }
}
