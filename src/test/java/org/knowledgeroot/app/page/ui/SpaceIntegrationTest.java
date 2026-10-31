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

    private String name(int id) {
        return jdbc.queryForObject("SELECT name FROM page WHERE id = ?", String.class, id);
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

    @Test void switchingSpacesMovesTheSidebarAndOpensTheHomePage() throws Exception {
        int team = space("SW" + pageId);
        spaceGrant(team, "users", null, "edit");
        int home = rootIn(team, "sw-home-" + pageId, true);
        jdbc.update("UPDATE space SET home_page_id = ? WHERE id = ?", home, team);
        int kr = spaces.findByKey("KR").orElseThrow().getId();
        var member = signIn(editor);
        member.perform(get("/spaces/open").param("space", String.valueOf(team)))
                .andExpect(redirectedUrl("/ui/page/" + home + "?trigger=reload-sidebar"));
        String sidebar = member.page("/ui/sidebar");
        assertTrue(sidebar.contains("sw-home-" + pageId), "the sidebar shows the opened space");
        assertFalse(sidebar.contains(name(pageId)), "root pages of other spaces stay out of the tree");
        member.perform(get("/spaces/open").param("space", String.valueOf(kr)))
                .andExpect(redirectedUrl("/spaces/" + kr + "?trigger=reload-sidebar"));
        String overview = member.perform(get("/spaces/" + kr).param("trigger", "reload-sidebar")).andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", "reload-sidebar")).andReturn().getResponse().getContentAsString();
        assertTrue(overview.contains("Top-level pages"));
        // The default space has hundreds of root pages in the shared test database; it is enough that the team root is gone.
        assertFalse(member.page("/ui/sidebar").contains("sw-home-" + pageId), "the sidebar switched back to the default space");
        assertTrue(member.page("/ui/sidebar?space=" + team).contains("sw-home-" + pageId), "an explicit space wins over the session");
        assertTrue(member.page("/spaces/" + team).contains("sw-home-" + pageId), "the overview lists the root pages of the space");
        // Opening a page moves the sidebar into its space.
        member.perform(get("/ui/page/" + home)).andExpect(status().isOk())
                .andExpect(content().string(containsString("/spaces/" + team)));
        assertTrue(member.page("/ui/sidebar").contains("sw-home-" + pageId));
        // Guests enter public spaces only.
        new Browser().perform(get("/spaces/open").param("space", String.valueOf(team))).andExpect(status().isForbidden());
        new Browser().perform(get("/spaces/" + kr)).andExpect(status().isOk());
        assertTrue(member.page("/").contains("id=\"krSpaceSwitch\""));
    }

    @Test void newRootPagesLandInTheChosenSpace() throws Exception {
        int team = space("NR" + pageId);
        spaceGrant(team, "user", editorId, "edit");
        var member = signIn(editor);
        assertTrue(member.page("/ui/page/new?space=" + team).contains("Top level of NR" + pageId));
        signIn(reader).perform(get("/ui/page/new").param("space", String.valueOf(team))).andExpect(status().isForbidden());
        member.post("/ui/page/new", "spaceId", String.valueOf(team), "name", "nr-root-" + pageId, "content", "<p>x</p>", "revision", "0")
                .andExpect(status().is3xxRedirection());
        int created = jdbc.queryForObject("SELECT id FROM page WHERE name = ?", Integer.class, "nr-root-" + pageId);
        assertEquals(team, jdbc.queryForObject("SELECT space_id FROM page WHERE id = ?", Integer.class, created));
        assertTrue(permissions.inheritsFromSpace(new PageId(created)));
        signIn(reader).post("/ui/page/new", "spaceId", String.valueOf(team), "name", "blocked", "revision", "0").andExpect(status().isForbidden());
    }

    @Test void searchCanBeLimitedToOneSpace() throws Exception {
        int team = space("SE" + pageId);
        spaceGrant(team, "users", null, "view");
        int teamPage = rootIn(team, "se-root-" + pageId, true);
        jdbc.update("UPDATE page SET content = ? WHERE id IN (?, ?)", "<p>needle" + pageId + "</p>", pageId, teamPage);
        var member = signIn(reader);
        String all = member.page("/search?q=needle" + pageId);
        assertTrue(all.contains("se-root-" + pageId) && all.contains(name(pageId)));
        assertTrue(all.contains(">SE" + pageId + "<"), "results show the space key");
        String limited = member.page("/search?q=needle" + pageId + "&space=" + team);
        assertTrue(limited.contains("se-root-" + pageId) && !limited.contains(name(pageId)));
    }

    @Test void pagesCanMoveToTheTopLevelOfAnotherSpace() throws Exception {
        int team = space("MT" + pageId);
        spaceGrant(team, "user", editorId, "edit");
        spaceGrant(team, "user", readerId, "view");
        jdbc.update("INSERT INTO page_permission (page_id, role_type, role_id, permission_level, created_by, create_date, changed_by, change_date) VALUES (?, 'user', ?, 'edit', ?, NOW(), ?, NOW())", otherPageId, editorId, adminId, adminId);
        jdbc.update("INSERT INTO page_permission (page_id, role_type, role_id, permission_level, created_by, create_date, changed_by, change_date) VALUES (?, 'user', ?, 'edit', ?, NOW(), ?, NOW())", otherPageId, readerId, adminId, adminId);
        var member = signIn(editor);
        String form = member.page("/ui/page/" + otherPageId + "/move?parent=0&space=" + team);
        assertTrue(form.contains("Top level of MT" + pageId + " space"));
        assertTrue(form.contains("name=\"space\""));
        // Reader may edit the page but not create root pages in the destination space.
        signIn(reader).post("/ui/page/" + otherPageId + "/move", "parent", "0", "space", String.valueOf(team), "revision", "0")
                .andExpect(status().isForbidden());
        member.post("/ui/page/" + otherPageId + "/move", "parent", "0", "space", String.valueOf(team), "revision", "0")
                .andExpect(status().is3xxRedirection());
        assertEquals(team, jdbc.queryForObject("SELECT space_id FROM page WHERE id = ?", Integer.class, otherPageId));
        assertEquals(0, jdbc.queryForObject("SELECT parent FROM page WHERE id = ?", Integer.class, otherPageId));
        assertTrue(member.page("/ui/sidebar?space=" + team).contains(name(otherPageId)));
    }

    private ResultActions json(Browser client, String path, String body) throws Exception {
        if (client.stale) client.refreshCsrf();
        return client.perform(MockMvcRequestBuilders.post(path).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body));
    }

    @Test void clientApiListsOpensAndUsesSpaces() throws Exception {
        int team = space("AP" + pageId);
        spaceGrant(team, "user", editorId, "edit");
        int teamRoot = rootIn(team, "ap-root-" + pageId, true);
        jdbc.update("UPDATE space SET home_page_id = ? WHERE id = ?", teamRoot, team);
        jdbc.update("INSERT INTO page_permission (page_id, role_type, role_id, permission_level, created_by, create_date, changed_by, change_date) VALUES (?, 'user', ?, 'edit', ?, NOW(), ?, NOW())", otherPageId, editorId, adminId, adminId);
        var api = new Browser();
        json(api, "/api/v1/auth/login", "{\"username\":\"" + editor + "\",\"password\":\"" + PASSWORD + "\"}").andExpect(status().isOk());
        String list = api.perform(get("/api/v1/spaces")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key=='KR')].current").value(true))
                .andExpect(jsonPath("$[?(@.key=='AP" + pageId + "')].canCreateRootPage").value(true))
                .andExpect(jsonPath("$[?(@.key=='AP" + pageId + "')].canManage").value(false))
                .andReturn().getResponse().getContentAsString();
        assertTrue(list.contains("\"homePageId\":" + teamRoot));
        json(api, "/api/v1/spaces/" + team + "/open", "").andExpect(status().isOk()).andExpect(jsonPath("$.current").value(true));
        api.perform(get("/api/v1/spaces")).andExpect(jsonPath("$[?(@.key=='KR')].current").value(false));
        api.perform(get("/api/v1/spaces/" + team)).andExpect(status().isOk())
                .andExpect(jsonPath("$.space.key").value("AP" + pageId))
                .andExpect(jsonPath("$.rootPages[0].id").value(teamRoot));
        // The tree follows the opened space unless a space is named.
        api.perform(get("/api/v1/tree")).andExpect(jsonPath("$.items[0].id").value(teamRoot));
        api.perform(get("/api/v1/tree").param("space", String.valueOf(spaces.findByKey("KR").orElseThrow().getId())))
                .andExpect(jsonPath("$.items[?(@.id==" + teamRoot + ")]").isEmpty());
        // Root pages are created in a named space; the detail names the space.
        String created = json(api, "/api/v1/pages", "{\"space\":" + team + ",\"name\":\"ap-new-" + pageId + "\",\"content\":\"<p>x</p>\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int newPage = JsonPath.read(created, "$.id");
        assertEquals(team, jdbc.queryForObject("SELECT space_id FROM page WHERE id = ?", Integer.class, newPage));
        api.perform(get("/api/v1/pages/" + newPage)).andExpect(jsonPath("$.space.key").value("AP" + pageId));
        json(api, "/api/v1/pages", "{\"space\":" + spaces.findByKey("KR").orElseThrow().getId() + ",\"name\":\"kr-new-" + pageId + "\"}")
                .andExpect(status().isCreated());
        // Search limited to a space and hits naming their space.
        jdbc.update("UPDATE page SET content = ? WHERE id IN (?, ?)", "<p>apneedle" + pageId + "</p>", teamRoot, otherPageId);
        api.perform(get("/api/v1/search").param("q", "apneedle" + pageId)).andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.items[?(@.id==" + teamRoot + ")].space.key").value("AP" + pageId));
        api.perform(get("/api/v1/search").param("q", "apneedle" + pageId).param("space", String.valueOf(team)))
                .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(1)));
        // Moving to the top level of another space.
        api.perform(get("/api/v1/pages/" + otherPageId + "/move-options").param("parent", "0").param("space", String.valueOf(team)))
                .andExpect(jsonPath("$.space.key").value("AP" + pageId)).andExpect(jsonPath("$.validDestination").value(true))
                .andExpect(jsonPath("$.items[?(@.id==" + teamRoot + ")]").isNotEmpty());
        json(api, "/api/v1/pages/" + otherPageId + "/move", "{\"parent\":0,\"space\":" + team + ",\"revision\":0}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.space.key").value("AP" + pageId));
        assertEquals(team, jdbc.queryForObject("SELECT space_id FROM page WHERE id = ?", Integer.class, otherPageId));
        // Without a grant the space does not exist for the client.
        var stranger = new Browser();
        json(stranger, "/api/v1/auth/login", "{\"username\":\"" + outsider + "\",\"password\":\"" + PASSWORD + "\"}").andExpect(status().isOk());
        stranger.perform(get("/api/v1/spaces")).andExpect(jsonPath("$[?(@.key=='AP" + pageId + "')]").isEmpty());
        stranger.perform(get("/api/v1/spaces/" + team)).andExpect(status().isForbidden());
        json(stranger, "/api/v1/spaces/" + team + "/open", "").andExpect(status().isForbidden());
        json(stranger, "/api/v1/pages", "{\"space\":" + team + ",\"name\":\"blocked\"}").andExpect(status().isForbidden());
    }
}
