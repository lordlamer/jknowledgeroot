package org.knowledgeroot.app.page.ui;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.knowledgeroot.test.FakeOidcProvider;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sign-in through an OpenID Connect provider (R64) against an embedded provider: first sign-in
 * creates and links an account, groups and the admin flag follow the claims, a verified e-mail
 * address links an existing account, unverified addresses are refused, and administrators control
 * whether such an account may also use its local password.
 */
class OidcLoginIntegrationTest extends IsolatedApplicationTest {
    static final FakeOidcProvider PROVIDER;
    static {
        try { PROVIDER = new FakeOidcProvider("knowledgeroot-test"); }
        catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    @DynamicPropertySource static void oidc(DynamicPropertyRegistry registry) {
        registry.add("knowledgeroot.oidc.issuer", PROVIDER::issuer);
        registry.add("knowledgeroot.oidc.client-id", () -> "knowledgeroot-test");
        registry.add("knowledgeroot.oidc.client-secret", () -> "knowledgeroot-test-secret");
        registry.add("knowledgeroot.oidc.name", () -> "Example ID");
        registry.add("knowledgeroot.oidc.groups-claim", () -> "groups");
        registry.add("knowledgeroot.oidc.admin-group", () -> "kr-admins");
        registry.add("knowledgeroot.oidc.create-groups", () -> "true");
    }
    @AfterAll static void stopProvider() { PROVIDER.close(); }

    /** Runs the whole browser round trip and returns the cookies of the signed-in session. */
    private Cookie[] signInThroughProvider(Map<String, Object> claims) throws Exception {
        PROVIDER.nextUser(claims);
        var cookies = new LinkedHashMap<String, Cookie>();
        var start = mvc.perform(get("/login/oidc")).andExpect(status().isFound())
                .andExpect(redirectedUrl("/oauth2/authorization/oidc")).andReturn().getResponse();
        collect(cookies, start);
        var toProvider = mvc.perform(withCookies(get("/oauth2/authorization/oidc"), cookies))
                .andExpect(status().isFound()).andReturn().getResponse();
        collect(cookies, toProvider);
        String location = toProvider.getRedirectedUrl();
        assertNotNull(location);
        assertTrue(location.startsWith(PROVIDER.issuer() + "/authorize?"), location);
        URI back = PROVIDER.follow(URI.create(location));
        assertEquals("/login/oauth2/code/oidc", back.getPath());
        var finished = mvc.perform(withCookies(callback(back), cookies))
                .andExpect(status().isFound()).andReturn().getResponse();
        collect(cookies, finished);
        return all(cookies);
    }

    private static void collect(Map<String, Cookie> cookies, MockHttpServletResponse response) {
        for (Cookie cookie : response.getCookies()) cookies.put(cookie.getName(), cookie);
    }

    /** The provider sent the browser back: path plus decoded code and state, so MockMvc does not re-encode them. */
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder callback(URI back) {
        var request = get(back.getPath());
        for (String pair : back.getRawQuery().split("&")) {
            int eq = pair.indexOf('=');
            request.param(java.net.URLDecoder.decode(pair.substring(0, eq), java.nio.charset.StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8));
        }
        return request;
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withCookies(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, Map<String, Cookie> cookies) {
        return cookies.isEmpty() ? request : request.cookie(all(cookies));
    }

    private static Cookie[] all(Map<String, Cookie> cookies) {
        return cookies.values().toArray(Cookie[]::new);
    }

    private Integer userId(String login) {
        return jdbc.query("SELECT id FROM user WHERE login=?", (rs, row) -> rs.getInt(1), login).stream().findFirst().orElse(null);
    }

    private List<String> groupsOf(int userId) {
        return jdbc.queryForList("SELECT g.name FROM `group` g JOIN group_member gm ON gm.group_id = g.id WHERE gm.member_id=? AND gm.member_type='user' ORDER BY g.name", String.class, userId);
    }

    @Test void loginPageOffersTheProviderAndUnknownUsersGetAnAccountWithGroups() throws Exception {
        String page = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("Sign in with Example ID"), page);
        assertTrue(page.contains("/login/oidc"), page);

        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO `group` (name, description, active, created_by, create_date, changed_by, change_date, deleted) VALUES (?, '', TRUE, ?, NOW(), ?, NOW(), FALSE)", "docs-" + suffix, adminId, adminId);
        String login = "maria." + suffix;
        var cookies = signInThroughProvider(Map.of("sub", "sub-" + suffix, "preferred_username", "Maria." + suffix.toUpperCase(),
                "email", login + "@example.org", "email_verified", true, "given_name", "Maria", "family_name", "Koch",
                "locale", "de-DE", "groups", List.of("docs-" + suffix, "kr-admins", "unknown-" + suffix)));
        var redirect = mvc.perform(get("/login/success").cookie(cookies)).andExpect(status().isFound()).andReturn().getResponse();
        assertEquals("/", redirect.getRedirectedUrl());
        mvc.perform(get("/profile").cookie(cookies)).andExpect(status().isOk());

        Integer id = userId(login);
        assertNotNull(id, "account created with the login from preferred_username");
        var row = jdbc.queryForMap("SELECT first_name, last_name, email, admin, local_login, language, active FROM user WHERE id=?", id);
        assertEquals("Maria", row.get("first_name"));
        assertEquals("Koch", row.get("last_name"));
        assertEquals(login + "@example.org", row.get("email"));
        assertEquals(true, row.get("admin"), "member of the admin group");
        assertEquals(false, row.get("local_login"), "no local password sign-in for accounts the provider created");
        assertEquals("de", row.get("language"));
        assertEquals(List.of("docs-" + suffix, "kr-admins", "unknown-" + suffix), groupsOf(id), "existing groups joined, missing ones created");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_identity WHERE user_id=? AND issuer=? AND subject=?", Integer.class, id, PROVIDER.issuer(), "sub-" + suffix));
        assertTrue(PROVIDER.lastAuthorization().get("scope").contains("openid"), PROVIDER.lastAuthorization().toString());
        assertTrue(PROVIDER.lastAuthorization().get("redirect_uri").endsWith("/login/oauth2/code/oidc"), PROVIDER.lastAuthorization().toString());

        // The administration shows the account as a single sign-on one.
        var admin = login("integration.admin");
        String list = admin.perform(get("/admin/users")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(list.contains(">SSO<"), list);
        String edit = admin.perform(get("/admin/users/" + id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(edit.contains("Linked to " + PROVIDER.issuer()), edit);

        // Next sign-in without the admin group: admin flag and that membership go, the others stay.
        var again = signInThroughProvider(Map.of("sub", "sub-" + suffix, "preferred_username", "renamed-" + suffix,
                "email", login + "@example.org", "email_verified", true, "groups", List.of("docs-" + suffix)));
        mvc.perform(get("/profile").cookie(again)).andExpect(status().isOk());
        assertEquals(id, userId(login), "same account, login unchanged");
        assertEquals(false, jdbc.queryForMap("SELECT admin FROM user WHERE id=?", id).get("admin"));
        assertEquals(List.of("docs-" + suffix), groupsOf(id));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_identity WHERE user_id=?", Integer.class, id));
    }

    @Test void verifiedEmailLinksAnExistingAccountAndUnverifiedOnesAreRefused() throws Exception {
        String email = editor + "@example.org";
        jdbc.update("UPDATE user SET email=? WHERE id=?", email, editorId);
        int users = jdbc.queryForObject("SELECT COUNT(*) FROM user", Integer.class);

        var cookies = signInThroughProvider(Map.of("sub", "sub-link-" + editor, "preferred_username", "somebody-else",
                "email", email.toUpperCase(), "email_verified", true));
        mvc.perform(get("/profile").cookie(cookies)).andExpect(status().isOk());
        assertEquals(users, jdbc.queryForObject("SELECT COUNT(*) FROM user", Integer.class), "no new account");
        assertEquals(editorId, jdbc.queryForObject("SELECT user_id FROM user_identity WHERE subject=?", Integer.class, "sub-link-" + editor));
        assertEquals(true, jdbc.queryForMap("SELECT local_login FROM user WHERE id=?", editorId).get("local_login"), "a linked local account keeps its password sign-in");
        // The session really belongs to the editor: the protected page is editable.
        mvc.perform(get("/ui/page/" + pageId + "/edit").cookie(cookies)).andExpect(status().isOk());

        // An unverified address neither links nor creates anything.
        PROVIDER.nextUser(Map.of("sub", "sub-unverified-" + editor, "email", "stranger-" + editor + "@example.org", "email_verified", false));
        var jar = new LinkedHashMap<String, Cookie>();
        var toProvider = mvc.perform(get("/oauth2/authorization/oidc")).andExpect(status().isFound()).andReturn().getResponse();
        collect(jar, toProvider);
        URI back = PROVIDER.follow(URI.create(toProvider.getRedirectedUrl()));
        mvc.perform(withCookies(callback(back), jar))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/login?error=oidc"));
        assertEquals(users, jdbc.queryForObject("SELECT COUNT(*) FROM user", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_identity WHERE subject=?", Integer.class, "sub-unverified-" + editor));
        String failed = mvc.perform(get("/login?error=oidc")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(failed.contains("Sign-in with Example ID did not complete"), failed);
    }

    @Test void administratorsDecideWhetherAnAccountMayUseItsLocalPassword() throws Exception {
        jdbc.update("UPDATE user SET local_login = FALSE WHERE id=?", editorId);
        var form = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn().getResponse();
        mvc.perform(post("/logmein").cookie(form.getCookies()).param("username", editor).param("password", PASSWORD)
                        .param("_csrf", csrfOf(form.getContentAsString())))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/login?error"));
        var api = mvc.perform(get("/api/v1/session")).andReturn().getResponse();
        mvc.perform(post("/api/v1/auth/login").cookie(api.getCookies()).header("X-XSRF-TOKEN", tokenOf(api.getContentAsString()))
                        .contentType("application/json").content("{\"username\":\"" + editor + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        var admin = login("integration.admin");
        admin.perform(post("/admin/users/" + editorId).param("login", editor).param("email", "").param("firstName", "Ed")
                        .param("lastName", "Itor").param("language", "").param("localLoginPresent", "true").param("localLogin", "true"))
                .andExpect(status().is3xxRedirection());
        assertEquals(true, jdbc.queryForMap("SELECT local_login FROM user WHERE id=?", editorId).get("local_login"));
        login(editor).perform(get("/profile")).andExpect(status().isOk());

        // A form without the switch (other callers) leaves the setting alone; unchecking the box turns it off again.
        admin.perform(post("/admin/users/" + editorId).param("login", editor).param("email", "").param("firstName", "Ed")
                        .param("lastName", "Itor").param("language", ""))
                .andExpect(status().is3xxRedirection());
        assertEquals(true, jdbc.queryForMap("SELECT local_login FROM user WHERE id=?", editorId).get("local_login"));
        admin.perform(post("/admin/users/" + editorId).param("login", editor).param("email", "").param("firstName", "Ed")
                        .param("lastName", "Itor").param("language", "").param("localLoginPresent", "true"))
                .andExpect(status().is3xxRedirection());
        assertEquals(false, jdbc.queryForMap("SELECT local_login FROM user WHERE id=?", editorId).get("local_login"));
    }

    private static String csrfOf(String html) {
        var matcher = java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*(?:value|content)=\"([^\"]+)\"").matcher(html);
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private static String tokenOf(String json) {
        var matcher = java.util.regex.Pattern.compile("\"token\":\"([^\"]+)\"").matcher(json);
        assertTrue(matcher.find(), json);
        return matcher.group(1);
    }
}
