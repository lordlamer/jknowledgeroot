package org.knowledgeroot.app.page.ui;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The JSON client API (/api/v1) used by the mobile app, with real sessions, CSRF and permissions. */
class ClientApiIntegrationTest extends IsolatedApplicationTest {

    /** Cookie session plus CSRF token, like the app's HTTP client. */
    class Api {
        final Map<String, Cookie> cookies = new LinkedHashMap<>();
        String csrf;

        Api() throws Exception {
            refresh();
        }

        void refresh() throws Exception {
            var response = perform(get("/api/v1/session")).andExpect(status().isOk()).andReturn().getResponse();
            csrf = JsonPath.read(response.getContentAsString(), "$.csrf.token");
        }

        ResultActions perform(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
            if (!cookies.isEmpty()) request.cookie(cookies.values().toArray(Cookie[]::new));
            if (csrf != null) request.header("X-XSRF-TOKEN", csrf);
            var result = mvc.perform(request);
            for (Cookie cookie : result.andReturn().getResponse().getCookies()) {
                if (cookie.getMaxAge() == 0) cookies.remove(cookie.getName());
                else cookies.put(cookie.getName(), cookie);
            }
            return result;
        }

        ResultActions json(AbstractMockHttpServletRequestBuilder<?> request, String body) throws Exception {
            return perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
        }
    }

    Api signIn(String login) throws Exception {
        var api = new Api();
        var response = api.json(post("/api/v1/auth/login"),
                "{\"username\":\"" + login + "\",\"password\":\"" + PASSWORD + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.login").value(login))
                .andReturn().getResponse();
        api.csrf = JsonPath.read(response.getContentAsString(), "$.csrf.token");
        return api;
    }

    long revision(int id) { return jdbc.queryForObject("SELECT revision FROM page WHERE id=?", Long.class, id); }

    @Test void loginRequiresCsrfAndRejectsWrongPasswordsWithoutRedirect() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + editor + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
        new Api().json(post("/api/v1/auth/login"), "{\"username\":\"" + editor + "\",\"password\":\"wrong-password\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid user name or password."));
        new Api().perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test void loginRotatesTheSessionAndLogoutEndsIt() throws Exception {
        var api = new Api();
        String guestSession = api.cookies.containsKey("SESSION") ? api.cookies.get("SESSION").getValue() : null;
        var signedIn = signIn(editor);
        assertNotEquals(guestSession, signedIn.cookies.get("SESSION").getValue());
        signedIn.perform(get("/api/v1/session")).andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.serverVersion").value(matchesRegex("[0-9]+[.][0-9]+[.][0-9]+.*")));
        signedIn.perform(post("/api/v1/auth/logout")).andExpect(status().isOk());
        signedIn.refresh();
        signedIn.perform(get("/api/v1/session")).andExpect(jsonPath("$.authenticated").value(false));
        signedIn.perform(put("/api/v1/pages/" + pageId + "/star")).andExpect(status().isUnauthorized());
    }

    @Test void pageAccessFollowsPagePermissions() throws Exception {
        var reader = signIn(this.reader);
        reader.perform(get("/api/v1/pages/" + pageId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.content").value("original"));
        reader.json(put("/api/v1/pages/" + pageId), "{\"name\":\"x\",\"content\":\"y\",\"labels\":[],\"revision\":0}")
                .andExpect(status().isForbidden());
        signIn(outsider).perform(get("/api/v1/pages/" + pageId)).andExpect(status().isForbidden());
        new Api().perform(get("/api/v1/pages/" + pageId)).andExpect(status().isForbidden());
        signIn(outsider).perform(get("/api/v1/tree"))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(pageId))));
        reader.perform(get("/api/v1/tree")).andExpect(jsonPath("$.items[*].id", hasItem(pageId)));
    }

    @Test void editorsSaveWithRevisionsAndLabels() throws Exception {
        var editor = signIn(this.editor);
        editor.json(put("/api/v1/pages/" + pageId),
                        "{\"name\":\"Renamed\",\"content\":\"<p>new</p><script>x()</script>\",\"labels\":[\"a\",\" b \"],\"revision\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.changeDate").value(matchesRegex("\\d{4}-\\d{2}-\\d{2}T[\\d:.]+(Z|[+-]\\d{2}:\\d{2})")))
                .andExpect(jsonPath("$.content").value("<p>new</p>"))
                .andExpect(jsonPath("$.labels", contains("a", "b")));
        editor.json(put("/api/v1/pages/" + pageId), "{\"name\":\"Stale\",\"content\":\"\",\"labels\":[],\"revision\":0}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Page has changed"));
        editor.perform(get("/api/v1/pages/" + pageId + "/history"))
                .andExpect(jsonPath("$.items[0].revision").value(0));
        int history = jdbc.queryForObject("SELECT MAX(id) FROM page_history WHERE page_id=?", Integer.class, pageId);
        editor.perform(get("/api/v1/pages/" + pageId + "/history/compare").param("from", String.valueOf(history)))
                .andExpect(jsonPath("$.right.current").value(true))
                .andExpect(jsonPath("$.textDiff[*].kind", hasItems("removed", "added")));
        editor.json(post("/api/v1/pages/" + pageId + "/history/" + history + "/restore"), "{\"revision\":1}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value(startsWith("protected-")));
        assertEquals("original", jdbc.queryForObject("SELECT content FROM page WHERE id=?", String.class, pageId));
    }

    @Test void childrenAreCreatedBelowEditablePagesOnly() throws Exception {
        var editor = signIn(this.editor);
        var created = editor.json(post("/api/v1/pages"), "{\"parent\":" + pageId + ",\"name\":\"Child\",\"content\":\"<p>c</p>\",\"labels\":[\"x\"]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int child = JsonPath.read(created, "$.id");
        editor.perform(get("/api/v1/tree").param("parent", String.valueOf(pageId)))
                .andExpect(jsonPath("$.items[0].id").value(child));
        editor.perform(get("/api/v1/tree")).andExpect(jsonPath("$.items[?(@.id==" + pageId + ")].hasChildren", contains(true)));
        signIn(reader).json(post("/api/v1/pages"), "{\"parent\":" + pageId + ",\"name\":\"No\",\"content\":\"\"}")
                .andExpect(status().isForbidden());
        editor.perform(delete("/api/v1/pages/" + child).param("revision", "0")).andExpect(status().isNoContent());
        editor.perform(get("/api/v1/pages/" + child)).andExpect(status().isForbidden());
        signIn("integration.admin").perform(get("/api/v1/pages/deleted"))
                .andExpect(jsonPath("$.items[*].id", hasItem(child)));
        editor.perform(get("/api/v1/pages/deleted")).andExpect(status().isForbidden());
    }

    @Test void starsAndCommentsBelongToTheirAuthors() throws Exception {
        var reader = signIn(this.reader);
        reader.perform(put("/api/v1/pages/" + pageId + "/star")).andExpect(status().isNoContent());
        reader.perform(get("/api/v1/starred")).andExpect(jsonPath("$[*].id", hasItem(pageId)));
        reader.perform(get("/api/v1/pages/" + pageId)).andExpect(jsonPath("$.starred").value(true));
        signIn(outsider).perform(put("/api/v1/pages/" + pageId + "/star")).andExpect(status().isForbidden());
        String comments = reader.json(post("/api/v1/pages/" + pageId + "/comments"), "{\"content\":\"  Hello  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].content").value("Hello"))
                .andExpect(jsonPath("$[0].canDelete").value(true))
                .andReturn().getResponse().getContentAsString();
        int comment = JsonPath.read(comments, "$[0].id");
        var editor = signIn(this.editor);
        editor.perform(get("/api/v1/pages/" + pageId + "/comments")).andExpect(jsonPath("$[0].canDelete").value(false));
        editor.perform(delete("/api/v1/pages/" + pageId + "/comments/" + comment)).andExpect(status().isForbidden());
        reader.json(post("/api/v1/pages/" + pageId + "/comments"), "{\"content\":\"   \"}").andExpect(status().isBadRequest());
        reader.perform(delete("/api/v1/pages/" + pageId + "/comments/" + comment))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test void attachmentsNeedEditRightsToChangeAndViewRightsToDownload() throws Exception {
        var file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());
        signIn(reader).perform(multipart("/api/v1/pages/" + pageId + "/files").file(file)).andExpect(status().isForbidden());
        var editor = signIn(this.editor);
        String files = editor.perform(multipart("/api/v1/pages/" + pageId + "/files").file(file))
                .andExpect(status().isCreated()).andExpect(jsonPath("$[0].name").value("notes.txt"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(files, "$[0].id");
        signIn(reader).perform(get("/api/v1/files/" + id + "/content"))
                .andExpect(status().isOk()).andExpect(content().string("hello"));
        signIn(outsider).perform(get("/api/v1/files/" + id + "/content")).andExpect(status().isForbidden());
        signIn(reader).perform(delete("/api/v1/files/" + id)).andExpect(status().isForbidden());
        editor.perform(delete("/api/v1/files/" + id)).andExpect(status().isNoContent());
    }

    @Test void onlyAdministratorsManageSharing() throws Exception {
        signIn(editor).perform(get("/api/v1/pages/" + pageId + "/permissions")).andExpect(status().isForbidden());
        signIn(editor).perform(get("/api/v1/admin/principals")).andExpect(status().isForbidden());
        var admin = signIn("integration.admin");
        admin.perform(get("/api/v1/pages/" + pageId + "/permissions"))
                .andExpect(jsonPath("$.inherits").value(false))
                .andExpect(jsonPath("$.entries[*].roleId", hasItems(editorId, readerId)));
        admin.json(post("/api/v1/pages/" + pageId + "/permissions"), "{\"roleType\":\"user\",\"roleId\":" + outsiderId + ",\"level\":\"view\"}")
                .andExpect(status().isCreated());
        admin.json(post("/api/v1/pages/" + pageId + "/permissions"), "{\"roleType\":\"user\",\"roleId\":" + outsiderId + ",\"level\":\"edit\"}")
                .andExpect(status().isConflict());
        admin.json(post("/api/v1/pages/" + pageId + "/permissions"), "{\"roleType\":\"group\",\"roleId\":999999,\"level\":\"view\"}")
                .andExpect(status().isBadRequest());
        signIn(outsider).perform(get("/api/v1/pages/" + pageId)).andExpect(status().isOk());
        int grant = jdbc.queryForObject("SELECT id FROM page_permission WHERE page_id=? AND role_type='user' AND role_id=?",
                Integer.class, pageId, outsiderId);
        admin.perform(delete("/api/v1/pages/" + pageId + "/permissions/" + grant)).andExpect(status().isOk());
        signIn(outsider).perform(get("/api/v1/pages/" + pageId)).andExpect(status().isForbidden());
    }

    @Test void movingKeepsRevisionChecks() throws Exception {
        var admin = signIn("integration.admin");
        admin.perform(get("/api/v1/pages/" + pageId + "/move-options").param("parent", "0"))
                .andExpect(jsonPath("$.items[*].id", hasItem(otherPageId)))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(pageId))));
        admin.json(post("/api/v1/pages/" + pageId + "/move"), "{\"parent\":" + otherPageId + ",\"revision\":5}")
                .andExpect(status().isConflict());
        admin.json(post("/api/v1/pages/" + pageId + "/move"), "{\"parent\":" + otherPageId + ",\"revision\":0}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.parent").value(otherPageId))
                .andExpect(jsonPath("$.breadcrumb[0].id").value(otherPageId));
        assertEquals(1, revision(pageId));
    }

    @Test void administratorsManageUsersAndGroups() throws Exception {
        var admin = signIn("integration.admin");
        String login = "api.user." + pageId;
        String user = admin.json(post("/api/v1/admin/users"), "{\"login\":\"" + login + "\",\"firstName\":\"Api\",\"lastName\":\"User\"," +
                        "\"email\":\"api@example.org\",\"password\":\"" + PASSWORD + "\",\"admin\":false}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.login").value(login))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(user, "$.id");
        admin.json(post("/api/v1/admin/users"), "{\"login\":\"" + login + "\",\"password\":\"" + PASSWORD + "\"}")
                .andExpect(status().isConflict());
        admin.json(post("/api/v1/admin/users"), "{\"login\":\"short." + pageId + "\",\"password\":\"short\"}")
                .andExpect(status().isBadRequest());
        signIn(login).perform(get("/api/v1/session")).andExpect(jsonPath("$.user.id").value(id));
        admin.json(put("/api/v1/admin/users/" + id), "{\"login\":\"" + login + "\",\"firstName\":\"Changed\",\"password\":\"\",\"active\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Changed")).andExpect(jsonPath("$.active").value(false));
        admin.json(put("/api/v1/admin/users/" + id), "{\"login\":\"" + login + "\",\"admin\":true}")
                .andExpect(jsonPath("$.admin").value(true));
        admin.json(put("/api/v1/admin/users/" + id), "{\"login\":\"" + login + "\",\"firstName\":\"Kept\"}")
                .andExpect(jsonPath("$.admin").value(true)).andExpect(jsonPath("$.active").value(false));
        admin.json(put("/api/v1/admin/users/" + adminId), "{\"login\":\"integration.admin\",\"admin\":false}")
                .andExpect(status().isConflict());
        admin.perform(delete("/api/v1/admin/users/" + id)).andExpect(status().isNoContent());
        admin.perform(get("/api/v1/admin/users/" + id)).andExpect(status().isNotFound());

        String name = "Group " + pageId;
        String group = admin.json(post("/api/v1/admin/groups"), "{\"name\":\"" + name + "\",\"description\":\"d\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int groupId = JsonPath.read(group, "$.id");
        admin.json(post("/api/v1/admin/groups"), "{\"name\":\"" + name + "\"}").andExpect(status().isConflict());
        admin.perform(get("/api/v1/admin/principals")).andExpect(jsonPath("$.groups[*].id", hasItem(groupId)));
        admin.perform(delete("/api/v1/admin/groups/" + groupId)).andExpect(status().isNoContent());
    }

    @Test void profileChangesAndPasswordChangeEndsTheSession() throws Exception {
        var editor = signIn(this.editor);
        editor.json(put("/api/v1/profile"), "{\"login\":\"" + this.editor + "\",\"email\":\"not-an-email\"}")
                .andExpect(status().isBadRequest());
        editor.json(put("/api/v1/profile"), "{\"login\":\"" + this.editor + "\",\"email\":\"e@example.org\",\"firstName\":\"Ed\",\"lastName\":\"Itor\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Ed"));
        editor.json(post("/api/v1/profile/password"), "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"x\",\"confirmPassword\":\"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The new password must contain 16–128 characters."));
        String replacement = "replacement-password-2026";
        editor.json(post("/api/v1/profile/password"), "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + replacement
                        + "\",\"confirmPassword\":\"" + replacement + "\"}")
                .andExpect(status().isNoContent());
        editor.refresh();
        editor.perform(get("/api/v1/session")).andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test void emojiFromPhoneKeyboardsAreStored() throws Exception {
        var editor = signIn(this.editor);
        editor.json(put("/api/v1/pages/" + pageId), "{\"name\":\"Release 🚀\",\"content\":\"<p>Done ✅ 👍</p>\",\"labels\":[\"🔥\"],\"revision\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Release 🚀"))
                .andExpect(jsonPath("$.labels[0]").value("🔥"));
        editor.json(post("/api/v1/pages/" + pageId + "/comments"), "{\"content\":\"Super 👍\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$[0].content").value("Super 👍"));
    }

    @Test void searchReturnsOnlyVisiblePagesWithSnippets() throws Exception {
        jdbc.update("UPDATE page SET content='<p>The secret word is pineapple.</p>' WHERE id=?", pageId);
        signIn(reader).perform(get("/api/v1/search").param("q", "pineapple"))
                .andExpect(jsonPath("$.items[0].id").value(pageId))
                .andExpect(jsonPath("$.items[0].snippet").value("The secret word is pineapple."));
        signIn(outsider).perform(get("/api/v1/search").param("q", "pineapple")).andExpect(jsonPath("$.items", hasSize(0)));
    }
}
