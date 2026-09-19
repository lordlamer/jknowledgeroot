package org.knowledgeroot.app.page.ui;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knowledgeroot.app.security.auth.PasswordService;
import org.knowledgeroot.app.security.recovery.AccountRecoveryService;
import org.knowledgeroot.app.security.recovery.RecoveryDelivery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = {"knowledgeroot.recovery.enabled=true",
        "knowledgeroot.recovery.public-url=https://knowledge.example", "knowledgeroot.recovery.from=noreply@example.org"})
class AccountRecoveryIntegrationTest extends IsolatedApplicationTest {
    @MockitoBean JavaMailSender sender;
    @Autowired RecoveryDelivery delivery;
    @Autowired AccountRecoveryService recovery;
    @Autowired PasswordService passwords;
    @Autowired org.knowledgeroot.app.security.user.domain.UserDao users;
    final BlockingQueue<SimpleMailMessage> messages = new LinkedBlockingQueue<>();
    String email;
    static final String REPLACEMENT = "recovered-password-2026";

    @BeforeEach void mailbox() {
        email = editor + "@example.org";
        jdbc.update("UPDATE user SET email=? WHERE id=?", email, editorId);
        doAnswer(call -> { messages.add(new SimpleMailMessage(call.getArgument(0))); return null; })
                .when(sender).send(any(SimpleMailMessage.class));
    }
    void drainWorker() throws Exception {
        var done = new CountDownLatch(1); delivery.submit(done::countDown);
        assertTrue(done.await(10, TimeUnit.SECONDS));
    }
    String mailToken() throws Exception {
        var message = messages.poll(10, TimeUnit.SECONDS); assertNotNull(message);
        assertArrayEquals(new String[]{email}, message.getTo());
        assertFalse(message.getText().contains(REPLACEMENT));
        var matcher = Pattern.compile("https://knowledge\\.example/account/[a-z-]+\\?token=([a-f0-9]{64})").matcher(message.getText());
        assertTrue(matcher.find()); return matcher.group(1);
    }
    void verifiedEmail() {
        jdbc.update("INSERT INTO recovery_email(user_id,email) VALUES (?,?)", editorId, email);
    }
    String resetLink() throws Exception {
        recovery.requestReset(email, "127.0.0.1"); return mailToken();
    }
    String hash() { return jdbc.queryForObject("SELECT password FROM user WHERE id=?", String.class, editorId); }

    @Test void verifyThenResetThroughFormsUsesOneTimeLinksAndInvalidatesSessions() throws Exception {
        var first = login(editor); var second = login(editor);
        first.perform(post("/profile/recovery").param("currentPassword", PASSWORD))
                .andExpect(status().isOk()).andExpect(content().string(containsString("confirmation link will be sent")));
        String verification = mailToken();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM recovery_email WHERE user_id=?", Integer.class, editorId));
        var browser = new Browser();
        browser.open("verify-email", verification);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM recovery_token WHERE user_id=?", Integer.class, editorId));
        assertEquals(200, browser.perform(post("/account/verify-email")).getResponse().getStatus());
        assertEquals(email, jdbc.queryForObject("SELECT email FROM recovery_email WHERE user_id=?", String.class, editorId));
        first.perform(get("/profile/recovery")).andExpect(content().string(containsString("Your email is confirmed")));
        browser.perform(get("/account/forgot-password"));
        var requested = browser.perform(post("/account/forgot-password").param("email", email));
        assertEquals(200, requested.getResponse().getStatus());
        String reset = mailToken();
        assertNotEquals(reset, jdbc.queryForObject("SELECT token_hash FROM recovery_token WHERE user_id=?", String.class, editorId));
        browser.open("reset-password", reset);
        var storedAttributes = jdbc.queryForList("SELECT ATTRIBUTE_BYTES FROM SPRING_SESSION_ATTRIBUTES", byte[].class);
        for (byte[] bytes : storedAttributes)
            assertFalse(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1).contains(reset),
                    "JDBC sessions must not persist the emailed bearer token");
        var forged = new Browser(); forged.open("reset-password", PasswordService.credentialTag(reset));
        assertEquals(400, forged.perform(post("/account/reset-password").param("newPassword", REPLACEMENT)
                .param("confirmPassword", REPLACEMENT)).getResponse().getStatus());
        var result = browser.perform(post("/account/reset-password").param("newPassword", REPLACEMENT).param("confirmPassword", REPLACEMENT));
        assertEquals(302, result.getResponse().getStatus());
        assertEquals("/login?passwordChanged=true", result.getResponse().getRedirectedUrl());
        first.perform(get("/profile")).andExpect(status().isFound());
        second.perform(get("/profile")).andExpect(status().isFound());
        login(editor, REPLACEMENT).perform(get("/profile")).andExpect(status().isOk());
        assertFalse(passwords.matches(PASSWORD, hash()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM recovery_token WHERE user_id=?", Integer.class, editorId));
        drainWorker(); assertEquals(1, messages.size());
        assertFalse(messages.take().getText().contains(REPLACEMENT));
        browser.open("reset-password", reset);
        assertEquals(400, browser.perform(post("/account/reset-password").param("newPassword", REPLACEMENT)
                .param("confirmPassword", REPLACEMENT)).getResponse().getStatus());
    }

    @Test void unknownUnverifiedDuplicateAndDisabledAddressesHaveNeutralResponsesAndNoMail() throws Exception {
        var browser = new Browser(); browser.perform(get("/account/forgot-password"));
        for (String address : List.of("absent@example.org", email, "bad\r\nTo: other@example.org")) {
            var response = browser.perform(post("/account/forgot-password").param("email", address)).getResponse();
            assertEquals(200, response.getStatus()); assertTrue(response.getContentAsString().contains("If the address belongs to an eligible account"));
        }
        drainWorker(); assertTrue(messages.isEmpty());
        verifiedEmail(); jdbc.update("UPDATE user SET email=? WHERE id=?", email.toUpperCase(Locale.ROOT), outsiderId);
        recovery.requestReset(email, "127.0.0.1"); drainWorker(); assertTrue(messages.isEmpty());
        jdbc.update("UPDATE user SET email='' WHERE id=?", outsiderId);
        jdbc.update("UPDATE user SET active=0 WHERE id=?", editorId);
        recovery.requestReset(email, "127.0.0.1"); drainWorker(); assertTrue(messages.isEmpty());
    }

    @Test void expiredAndWrongPurposeTokensCannotChangePasswordsAndFreshRequestReplacesOldLink() throws Exception {
        verifiedEmail(); String original = hash(); String old = resetLink(); String fresh = resetLink();
        assertThrows(ResponseStatusException.class, () -> recovery.reset(old, REPLACEMENT, REPLACEMENT, "127.0.0.1"));
        jdbc.update("UPDATE recovery_token SET expires_at=TIMESTAMPADD(SECOND,-1,NOW()) WHERE user_id=?", editorId);
        assertThrows(ResponseStatusException.class, () -> recovery.reset(fresh, REPLACEMENT, REPLACEMENT, "127.0.0.1"));
        login(editor).perform(post("/profile/recovery").param("currentPassword", PASSWORD)).andExpect(status().isOk());
        String verify = mailToken();
        assertThrows(ResponseStatusException.class, () -> recovery.reset(verify, REPLACEMENT, REPLACEMENT, "127.0.0.1"));
        assertEquals(original, hash());
    }

    @Test void emailEditsInvalidateVerificationAndTokensEvenIfAddressIsChangedBack() throws Exception {
        verifiedEmail(); String token = resetLink(); var client = login(editor);
        for (String address : List.of("new-" + email, email)) client.perform(post("/profile").param("login", editor)
                .param("email", address).param("firstName", "Editor").param("surName", "Test")).andExpect(status().isFound());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM recovery_email WHERE user_id=?", Integer.class, editorId));
        assertThrows(ResponseStatusException.class, () -> recovery.reset(token, REPLACEMENT, REPLACEMENT, "127.0.0.1"));
        recovery.requestReset(email, "127.0.0.1"); drainWorker(); assertTrue(messages.isEmpty());
    }

    @Test void administrativeEmailChangeAlsoInvalidatesVerificationAndOutstandingTokens() throws Exception {
        verifiedEmail(); String token = resetLink();
        var account = users.findById(new org.knowledgeroot.app.security.user.domain.UserId(editorId));
        account.setEmail("changed-" + email); users.updateUser(account);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM recovery_email WHERE user_id=?", Integer.class, editorId));
        assertThrows(ResponseStatusException.class, () -> recovery.reset(token, REPLACEMENT, REPLACEMENT, "127.0.0.1"));
    }

    @Test void passwordChangesAndAccountDeactivationInvalidateOutstandingLinks() throws Exception {
        verifiedEmail(); String token = resetLink();
        String replacementHash = passwords.encodeNewPassword(REPLACEMENT);
        jdbc.update("UPDATE user SET password=? WHERE id=?", replacementHash, editorId);
        assertThrows(ResponseStatusException.class, () -> recovery.reset(token, PASSWORD, PASSWORD, "127.0.0.1"));
        assertEquals(replacementHash, hash());
        String second = resetLink(); jdbc.update("UPDATE user SET deleted=1 WHERE id=?", editorId);
        assertThrows(ResponseStatusException.class, () -> recovery.reset(second, PASSWORD, PASSWORD, "127.0.0.1"));
    }

    @Test void weakOrMismatchedPasswordsAndMissingCsrfDoNotConsumeLinksOrEchoPasswords() throws Exception {
        verifiedEmail(); String token = resetLink(); var browser = new Browser(); browser.open("reset-password", token);
        mvc.perform(post("/account/reset-password").cookie(browser.cookies.values().toArray(Cookie[]::new))
                .param("newPassword", REPLACEMENT).param("confirmPassword", REPLACEMENT)).andExpect(status().isForbidden());
        for (var pair : List.of(List.of("short", "short"), List.of(REPLACEMENT, "different"))) {
            var response = browser.perform(post("/account/reset-password").param("newPassword", pair.get(0))
                    .param("confirmPassword", pair.get(1))).getResponse();
            assertEquals(400, response.getStatus()); assertFalse(response.getContentAsString().contains(REPLACEMENT));
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM recovery_token WHERE user_id=?", Integer.class, editorId));
        assertTrue(passwords.matches(PASSWORD, hash()));
        mvc.perform(post("/account/forgot-password").param("email", email)).andExpect(status().isForbidden());
        login(editor).perform(post("/profile/recovery").param("currentPassword", "wrong"))
                .andExpect(content().string(containsString("Check your current password")));
        drainWorker(); assertTrue(messages.isEmpty());
    }

    @Test void quotasPersistAndCannotRevealAccountsOrLockOutNormalLogin() throws Exception {
        verifiedEmail();
        String bucket = PasswordService.credentialTag("recovery-request-account:" + email);
        jdbc.update("INSERT INTO login_attempt VALUES (?,CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS UNSIGNED),10000)", bucket);
        var browser = new Browser(); browser.perform(get("/account/forgot-password"));
        assertEquals(200, browser.perform(post("/account/forgot-password").param("email", email)).getResponse().getStatus());
        drainWorker(); assertTrue(messages.isEmpty());
        assertEquals(10001, jdbc.queryForObject("SELECT attempts FROM login_attempt WHERE bucket_key=?", Integer.class, bucket));
        login(editor).perform(get("/profile")).andExpect(status().isOk());
    }

    @Test void simultaneousConsumptionChangesPasswordExactlyOnce() throws Exception {
        verifiedEmail(); String token = resetLink(); var go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> action = () -> {
                assertTrue(go.await(10, TimeUnit.SECONDS));
                try { recovery.reset(token, REPLACEMENT, REPLACEMENT, "127.0.0.1"); return true; }
                catch (ResponseStatusException ex) { assertEquals(400, ex.getStatusCode().value()); return false; }
            };
            var first = pool.submit(action); var second = pool.submit(action); go.countDown();
            assertNotEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
        drainWorker(); assertEquals(1, messages.size()); assertTrue(passwords.matches(REPLACEMENT, hash()));
    }

    @Test void smtpFailureRevokesUndeliveredLinkAndPublicResponseRemainsNeutral() throws Exception {
        verifiedEmail();
        doThrow(new org.springframework.mail.MailSendException("fixture failure"))
                .when(sender).send(any(SimpleMailMessage.class));
        var browser = new Browser(); browser.perform(get("/account/forgot-password"));
        assertEquals(200, browser.perform(post("/account/forgot-password").param("email", email)).getResponse().getStatus());
        drainWorker(); assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM recovery_token WHERE user_id=?", Integer.class, editorId));
    }

    private class Browser {
        final Map<String,Cookie> cookies = new LinkedHashMap<>(); String csrf;
        MvcResult perform(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
            if (!cookies.isEmpty()) request.cookie(cookies.values().toArray(Cookie[]::new));
            if (csrf != null) request.header("X-XSRF-TOKEN", csrf);
            var result = mvc.perform(request).andReturn();
            for (var cookie : result.getResponse().getCookies()) cookies.put(cookie.getName(), cookie);
            var matcher = Pattern.compile("name=\"_csrf\"[^>]*(?:value|content)=\"([^\"]+)\"").matcher(result.getResponse().getContentAsString());
            if (matcher.find()) csrf = matcher.group(1);
            return result;
        }
        void open(String path, String token) throws Exception {
            var redirect = perform(get("/account/" + path).param("token", token)).getResponse();
            assertEquals(302, redirect.getStatus()); assertEquals("/account/" + path, redirect.getRedirectedUrl());
            assertEquals("no-referrer", redirect.getHeader("Referrer-Policy"));
            var form = perform(get(redirect.getRedirectedUrl())).getResponse();
            assertEquals(200, form.getStatus()); assertFalse(form.getContentAsString().contains(token));
            assertTrue(form.getHeader("Cache-Control").contains("no-store")); assertNotNull(csrf);
        }
    }
}
