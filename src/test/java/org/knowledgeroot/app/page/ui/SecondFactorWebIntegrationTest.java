package org.knowledgeroot.app.page.ui;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.knowledgeroot.app.security.mfa.MfaPolicy;
import org.knowledgeroot.app.security.mfa.MfaSettings;
import org.knowledgeroot.app.security.mfa.SecondFactorService;
import org.knowledgeroot.app.security.mfa.Totp;
import org.knowledgeroot.app.security.mfa.TotpSecret;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Two-step sign-in, profile enrolment, admin policy and reset, and the client API second step. */
class SecondFactorWebIntegrationTest extends IsolatedApplicationTest {
    private static final Pattern SECRET = Pattern.compile("font-family:monospace[^>]*>([A-Z2-7 ]{32,})<");
    @Autowired SecondFactorService factors;
    @Autowired MfaSettings settings;

    @AfterEach void resetPolicy() {
        jdbc.update("DELETE FROM app_setting WHERE name='mfa.policy'");
    }

    /**
     * Cookie jar with the real CSRF cookie and header, like a browser or the app. Sessions live in
     * the JDBC session store and travel as the SESSION cookie; a rotated XSRF-TOKEN cookie is
     * followed by a fresh masked token from {@code /api/v1/session}.
     */
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
            var response = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();
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

        ResultActions json(String path, String body) throws Exception {
            if (stale) refreshCsrf();
            return perform(MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(body));
        }

        String page(String path) throws Exception {
            return perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        }
    }

    private Browser passwordLogin(String login, String expectedRedirect) throws Exception {
        var browser = new Browser();
        browser.post("/logmein", "username", login, "password", PASSWORD)
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(expectedRedirect));
        assertTrue(browser.cookies.containsKey("SESSION"));
        return browser;
    }

    private String code(String secret, long offset) {
        return Totp.code(TotpSecret.of(secret), Totp.step(Instant.now()) + offset);
    }

    private String secretOn(String html) {
        var matcher = SECRET.matcher(html);
        assertTrue(matcher.find(), "setup page shows the manual key");
        return matcher.group(1).replace(" ", "");
    }

    /** Enrols the user directly through the service and returns the Base32 secret and the recovery codes. */
    private Object[] enrol(int userId, String login) {
        var enrolment = factors.begin(login);
        List<String> codes = factors.confirm(userId, enrolment.secret(), code(enrolment.secret(), 0));
        return new Object[]{enrolment.secret(), codes};
    }

    @Test void policyOffSignsInWithThePasswordOnlyEvenWhenEnrolled() throws Exception {
        enrol(editorId, editor);
        var browser = passwordLogin(editor, "/login/success");
        browser.perform(get("/profile")).andExpect(status().isOk());
        browser.perform(get("/mfa")).andExpect(redirectedUrl("/"));
    }

    @Test void enrolledUsersMustEnterACodeAndStayOnTheMfaPageUntilThen() throws Exception {
        settings.update(MfaPolicy.OPTIONAL, adminId);
        String secret = (String) enrol(editorId, editor)[0];
        var browser = passwordLogin(editor, "/mfa");
        assertTrue(browser.page("/mfa").contains("Enter your code"));
        // Nothing but the MFA pages is reachable with the pending session.
        browser.perform(get("/profile")).andExpect(redirectedUrl("/mfa"));
        browser.perform(get("/ui/welcome")).andExpect(redirectedUrl("/mfa"));
        browser.perform(get("/api/v1/pages/" + pageId)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Second factor required."));
        browser.post("/mfa/verify", "code", "000000").andExpect(redirectedUrl("/mfa"));
        assertTrue(browser.page("/mfa").contains("4 attempts left"));
        browser.post("/mfa/verify", "code", code(secret, 1)).andExpect(redirectedUrl("/login/success"));
        browser.perform(get("/profile")).andExpect(status().isOk());
        browser.perform(get("/mfa")).andExpect(redirectedUrl("/"));
    }

    @Test void fiveWrongCodesEndTheSession() throws Exception {
        settings.update(MfaPolicy.OPTIONAL, adminId);
        enrol(editorId, editor);
        var browser = passwordLogin(editor, "/mfa");
        for (int i = 0; i < 4; i++) browser.post("/mfa/verify", "code", "000000").andExpect(redirectedUrl("/mfa"));
        browser.post("/mfa/verify", "code", "000000").andExpect(redirectedUrl("/login?mfaLocked"));
        assertFalse(browser.cookies.containsKey("SESSION"), "the session cookie is cleared");
        browser.perform(get("/profile")).andExpect(redirectedUrl("/login"));
    }

    @Test void recoveryCodesSignInOnce() throws Exception {
        settings.update(MfaPolicy.OPTIONAL, adminId);
        @SuppressWarnings("unchecked") List<String> codes = (List<String>) enrol(editorId, editor)[1];
        var browser = passwordLogin(editor, "/mfa");
        browser.post("/mfa/verify", "code", codes.get(2)).andExpect(redirectedUrl("/login/success?recovery"));
        assertTrue(browser.page("/profile/mfa").contains("Unused recovery codes: <strong>9</strong>"));
        var again = passwordLogin(editor, "/mfa");
        again.post("/mfa/verify", "code", codes.get(2)).andExpect(redirectedUrl("/mfa"));
    }

    @Test void requiredPolicyForcesEnrolmentAtSignIn() throws Exception {
        settings.update(MfaPolicy.REQUIRED, adminId);
        var browser = passwordLogin(reader, "/mfa");
        String setup = browser.page("/mfa");
        assertTrue(setup.contains("Set up two-factor authentication") && setup.contains("<svg"));
        String secret = secretOn(setup);
        browser.post("/mfa/setup", "code", "123456").andExpect(redirectedUrl("/mfa"));
        assertEquals(secret, secretOn(browser.page("/mfa")), "the pending secret survives a wrong code");
        browser.post("/mfa/setup", "code", code(secret, 0)).andExpect(redirectedUrl("/mfa/recovery-codes"));
        String codesPage = browser.page("/mfa/recovery-codes");
        assertTrue(codesPage.contains("Your recovery codes"));
        assertTrue(Pattern.compile("[a-hjkmnp-z2-9]{4}-[a-hjkmnp-z2-9]{4}").matcher(codesPage).find());
        assertTrue(factors.enrolled(readerId));
        browser.perform(get("/profile")).andExpect(status().isOk());
        browser.perform(get("/mfa/recovery-codes")).andExpect(redirectedUrl("/"));
        // The next sign-in asks for a code instead of the setup.
        assertTrue(passwordLogin(reader, "/mfa").page("/mfa").contains("Enter your code"));
    }

    @Test void requiredForAdminsLeavesOtherUsersAlone() throws Exception {
        settings.update(MfaPolicy.REQUIRED_FOR_ADMINS, adminId);
        passwordLogin(editor, "/login/success");
        assertTrue(passwordLogin("integration.admin", "/mfa").page("/mfa").contains("Set up two-factor authentication"));
    }

    @Test void profileEnrolmentAndRemoval() throws Exception {
        settings.update(MfaPolicy.OPTIONAL, adminId);
        var browser = passwordLogin(editor, "/login/success");
        assertTrue(browser.page("/profile").contains("Not set up"));
        String setup = browser.post("/profile/mfa/setup").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(setup.contains("<svg"));
        String secret = secretOn(setup);
        browser.post("/profile/mfa/confirm", "code", "000000").andExpect(status().isOk())
                .andExpect(content().string(containsString("not valid")));
        browser.post("/profile/mfa/confirm", "code", code(secret, 0)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Two-factor authentication is active")));
        assertEquals(10, factors.status(editorId).recoveryCodesLeft());
        assertTrue(browser.page("/profile/mfa").contains("Active"));

        browser.post("/profile/mfa/remove", "currentPassword", "wrong", "code", code(secret, 1))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Password or code is not valid")));
        assertTrue(factors.enrolled(editorId));
        browser.post("/profile/mfa/remove", "currentPassword", PASSWORD, "code", code(secret, 1))
                .andExpect(redirectedUrl("/profile/mfa?removed"));
        assertFalse(factors.enrolled(editorId));
    }

    @Test void removalIsRefusedWhileThePolicyRequiresAToken() throws Exception {
        settings.update(MfaPolicy.REQUIRED, adminId);
        String secret = (String) enrol(editorId, editor)[0];
        var browser = passwordLogin(editor, "/mfa");
        browser.post("/mfa/verify", "code", code(secret, 1)).andExpect(redirectedUrl("/login/success"));
        browser.post("/profile/mfa/remove", "currentPassword", PASSWORD, "code", code(secret, 2))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Ask an administrator")));
        assertTrue(factors.enrolled(editorId));
    }

    @Test void administratorsChangeThePolicyAndResetTokens() throws Exception {
        var admin = passwordLogin("integration.admin", "/login/success");
        assertTrue(admin.page("/admin/settings").contains("id=\"mfa-off\" value=\"off\" checked"));
        admin.post("/admin/settings", "mfaPolicy", "required-for-admins").andExpect(redirectedUrl("/admin/settings?saved"));
        assertEquals(MfaPolicy.REQUIRED_FOR_ADMINS, settings.policy());
        admin.post("/admin/settings", "mfaPolicy", "nonsense").andExpect(status().isBadRequest());

        enrol(editorId, editor);
        assertTrue(admin.page("/admin/users/" + editorId).contains("Reset two-factor authentication"));
        admin.post("/admin/users/" + editorId + "/mfa/reset").andExpect(redirectedUrl("/admin/users/" + editorId));
        assertFalse(factors.enrolled(editorId));

        var member = passwordLogin(editor, "/login/success");
        member.perform(get("/admin/settings")).andExpect(status().isForbidden());
        member.post("/admin/users/" + readerId + "/mfa/reset").andExpect(status().isForbidden());
    }

    @Test void clientApiLoginOwesTheSecondFactorUntilTheCodeArrives() throws Exception {
        settings.update(MfaPolicy.OPTIONAL, adminId);
        String secret = (String) enrol(editorId, editor)[0];
        var api = new Browser();
        api.perform(get("/api/v1/session")).andExpect(status().isOk()).andExpect(jsonPath("$.mfa.policy").value("optional"));
        var login = api.json("/api/v1/auth/login", "{\"username\":\"" + editor + "\",\"password\":\"" + PASSWORD + "\"}")
                .andReturn().getResponse();
        assertEquals(200, login.getStatus(), login.getContentAsString());
        assertEquals(false, JsonPath.read(login.getContentAsString(), "$.authenticated"));
        assertEquals(true, JsonPath.read(login.getContentAsString(), "$.mfa.pending"));
        assertEquals("verify", JsonPath.read(login.getContentAsString(), "$.mfa.requirement"));
        assertNull(JsonPath.read(login.getContentAsString(), "$.user"));
        api.perform(get("/api/v1/pages/" + pageId)).andExpect(status().isUnauthorized());
        api.json("/api/v1/auth/mfa", "{\"code\":\"000000\"}")
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("The code is not valid."));
        api.json("/api/v1/auth/mfa", "{\"code\":\"" + code(secret, 1) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.login").value(editor))
                .andExpect(jsonPath("$.mfa.pending").value(false))
                .andExpect(jsonPath("$.mfa.enrolled").value(true));
        api.perform(get("/api/v1/pages/" + pageId)).andExpect(status().isOk());
        api.json("/api/v1/auth/mfa", "{\"code\":\"000000\"}").andExpect(status().isConflict());
    }

    @Test void clientApiEnrolsAtSignInWhenThePolicyRequiresIt() throws Exception {
        settings.update(MfaPolicy.REQUIRED, adminId);
        var api = new Browser();
        api.json("/api/v1/auth/login", "{\"username\":\"" + reader + "\",\"password\":\"" + PASSWORD + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.mfa.pending").value(true))
                .andExpect(jsonPath("$.mfa.requirement").value("enrol"));
        api.json("/api/v1/auth/mfa", "{\"code\":\"000000\"}").andExpect(status().isConflict());
        api.json("/api/v1/auth/mfa/confirm", "{\"code\":\"000000\"}").andExpect(status().isBadRequest());
        String setup = api.json("/api/v1/auth/mfa/setup", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.otpauthUri").value(startsWith("otpauth://totp/Knowledgeroot:" + reader)))
                .andExpect(jsonPath("$.qrSvg").value(startsWith("<svg")))
                .andReturn().getResponse().getContentAsString();
        String secret = JsonPath.read(setup, "$.secret");
        api.json("/api/v1/auth/mfa/confirm", "{\"code\":\"000000\"}").andExpect(status().isBadRequest());
        api.json("/api/v1/auth/mfa/confirm", "{\"code\":\"" + code(secret, 0) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes", hasSize(10)))
                .andExpect(jsonPath("$.authenticated").value(true));
        api.perform(get("/api/v1/session")).andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.login").value(reader)).andExpect(jsonPath("$.mfa.enrolled").value(true));
        api.perform(get("/api/v1/pages/" + pageId)).andExpect(status().isOk());
        api.json("/api/v1/auth/mfa/setup", "").andExpect(status().isConflict());
        assertTrue(factors.enrolled(readerId));
    }

    @Test void clientApiProfileManagesTheToken() throws Exception {
        settings.update(MfaPolicy.OPTIONAL, adminId);
        var api = new Browser();
        api.json("/api/v1/auth/login", "{\"username\":\"" + editor + "\",\"password\":\"" + PASSWORD + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true));
        api.perform(get("/api/v1/profile/mfa")).andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolled").value(false)).andExpect(jsonPath("$.required").value(false))
                .andExpect(jsonPath("$.policy").value("optional"));
        String setup = api.json("/api/v1/auth/mfa/setup", "").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String secret = JsonPath.read(setup, "$.secret");
        api.json("/api/v1/auth/mfa/confirm", "{\"code\":\"" + code(secret, 0) + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true));
        api.perform(get("/api/v1/profile/mfa")).andExpect(jsonPath("$.enrolled").value(true))
                .andExpect(jsonPath("$.recoveryCodesLeft").value(10)).andExpect(jsonPath("$.enrolledAt").isString());
        api.perform(MockMvcRequestBuilders.delete("/api/v1/profile/mfa").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"code\":\"" + code(secret, 1) + "\"}"))
                .andExpect(status().isBadRequest());
        api.perform(MockMvcRequestBuilders.delete("/api/v1/profile/mfa").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"code\":\"" + code(secret, 1) + "\"}"))
                .andExpect(status().isNoContent());
        api.perform(get("/api/v1/profile/mfa")).andExpect(jsonPath("$.enrolled").value(false));
        new Browser().perform(get("/api/v1/profile/mfa")).andExpect(status().isUnauthorized());
    }
}
