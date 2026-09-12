package org.knowledgeroot.app.security.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class PasswordServiceTest {
    private final PasswordService passwords = new PasswordService();

    @Test
    void knownDemoVerifierRemainsRecognizableByTheProductionStartupGuard() {
        assertFalse(passwords.upgradeEncoding(PasswordService.DEMO_HASH));
    }

    @Test
    void modernHashesAreSaltedAndPreserveWhitespaceAndUnicode() {
        String password = "  passphrase-with-ä-🙂  ";
        String first = passwords.encodeNewPassword(password);
        assertTrue(first.startsWith(PasswordService.PREFIX));
        assertNotEquals(first, passwords.encodeNewPassword(password));
        assertTrue(passwords.matches(password, first));
        assertFalse(passwords.matches(password.trim(), first));
        assertFalse(passwords.upgradeEncoding(first));
        assertEquals(PasswordService.PREFIX.length() + 96, first.length());
    }

    @Test
    void legacyPasswordsCanMigrateWithoutMeetingNewPasswordLength() {
        String legacy = PasswordHasher.hash("old", PasswordHasher.HASH_METHOD.SHA256, 1000);
        assertTrue(passwords.matches("old", legacy));
        assertTrue(passwords.upgradeEncoding(legacy));
        assertTrue(passwords.matches("old", passwords.encode("old")));
        assertThrows(ResponseStatusException.class, () -> passwords.encodeNewPassword("old"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "***", "short", "                "})
    void newPasswordsRejectInvalidValues(String password) {
        assertThrows(ResponseStatusException.class, () -> passwords.encodeNewPassword(password));
    }

    @Test
    void updateMarkersKeepTheExistingPasswordAndLengthIsBounded() {
        assertNull(passwords.encodeReplacement(null));
        assertNull(passwords.encodeReplacement(""));
        assertNull(passwords.encodeReplacement("***"));
        assertThrows(ResponseStatusException.class, () -> passwords.encodeReplacement("   "));
        assertThrows(ResponseStatusException.class, () -> passwords.encodeNewPassword("x".repeat(129)));
        assertFalse(passwords.matches("x".repeat(1025), "unknown"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-hash", "{unknown}hash", "{pbkdf2-sha256-v1}zz",
            "$2$0$E556111996CF452E$B3EB80BC13DED8B02A2F09E505AED75737D4AF45D9A599623D39CD518771C306",
            "$2$4294967295$E556111996CF452E$B3EB80BC13DED8B02A2F09E505AED75737D4AF45D9A599623D39CD518771C306"})
    void malformedHashesFailClosedWithoutUnboundedLegacyWork(String hash) {
        assertFalse(passwords.matches("password", hash));
    }
}
