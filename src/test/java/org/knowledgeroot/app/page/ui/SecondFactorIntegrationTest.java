package org.knowledgeroot.app.page.ui;

import org.junit.jupiter.api.Test;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.mfa.MfaPolicy;
import org.knowledgeroot.app.security.mfa.MfaSettings;
import org.knowledgeroot.app.security.mfa.SecondFactorService;
import org.knowledgeroot.app.security.mfa.SecondFactorService.Result;
import org.knowledgeroot.app.security.mfa.Totp;
import org.knowledgeroot.app.security.mfa.TotpSecret;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Enrolment, verification and policy of the second factor against the real database. */
class SecondFactorIntegrationTest extends IsolatedApplicationTest {
    @Autowired SecondFactorService factors;
    @Autowired MfaSettings settings;
    @Autowired UserContext users;

    private String currentCode(String secret) {
        return Totp.code(TotpSecret.of(secret), Totp.step(Instant.now()));
    }

    @Test void enrolmentNeedsTheFirstCodeAndYieldsRecoveryCodes() {
        var enrolment = factors.begin(editor);
        assertTrue(enrolment.otpauthUri().startsWith("otpauth://totp/Knowledgeroot:" + editor + "?secret=" + enrolment.secret()));
        assertTrue(enrolment.qrSvg().startsWith("<svg"));
        assertFalse(factors.enrolled(editorId));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> factors.confirm(editorId, enrolment.secret(), "000000")).getStatusCode().value());
        assertFalse(factors.enrolled(editorId));

        var codes = factors.confirm(editorId, enrolment.secret(), currentCode(enrolment.secret()));
        assertEquals(10, codes.size());
        assertTrue(codes.stream().allMatch(code -> Pattern.matches("[a-hjkmnp-z2-9]{4}-[a-hjkmnp-z2-9]{4}", code)));
        var status = factors.status(editorId);
        assertTrue(status.enrolled());
        assertNotNull(status.enrolledAt());
        assertEquals(10, status.recoveryCodesLeft());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> factors.confirm(editorId, enrolment.secret(), currentCode(enrolment.secret()))).getStatusCode().value());
        // The secret is stored encrypted, never as the Base32 value.
        byte[] stored = jdbc.queryForObject("SELECT secret_enc FROM user_second_factor WHERE user_id=?", byte[].class, editorId);
        assertFalse(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1).contains(enrolment.secret()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_recovery_code WHERE code_hash IN (?,?)",
                Integer.class, codes.getFirst(), codes.getLast()));
    }

    @Test void codesAndRecoveryCodesAreSingleUse() {
        var enrolment = factors.begin(editor);
        var codes = factors.confirm(editorId, enrolment.secret(), currentCode(enrolment.secret()));
        // The confirmation step consumed the current step; the next step is accepted once.
        String next = Totp.code(TotpSecret.of(enrolment.secret()), Totp.step(Instant.now()) + 1);
        assertEquals(Result.TOTP_OK, factors.verify(editorId, next));
        assertEquals(Result.INVALID, factors.verify(editorId, next));
        assertEquals(Result.INVALID, factors.verify(editorId, currentCode(enrolment.secret())));
        assertEquals(Result.INVALID, factors.verify(editorId, "000000"));

        assertEquals(Result.RECOVERY_CODE_OK, factors.verify(editorId, codes.get(3).toUpperCase().replace("-", " ")));
        assertEquals(Result.INVALID, factors.verify(editorId, codes.get(3)));
        assertEquals(9, factors.status(editorId).recoveryCodesLeft());
        assertEquals(Result.INVALID, factors.verify(editorId, "zzzz-zzzz"));
        assertEquals(Result.INVALID, factors.verify(editorId, "x".repeat(40)));
        assertEquals(Result.INVALID, factors.verify(readerId, codes.get(0)), "codes belong to their user");

        factors.remove(editorId);
        assertFalse(factors.enrolled(editorId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_recovery_code WHERE user_id=?", Integer.class, editorId));
        assertEquals(Result.INVALID, factors.verify(editorId, codes.get(1)));
    }

    @Test void policyIsStoredAndDecidesWhoMustEnrol() {
        var admin = users.getUserContextForId(String.valueOf(adminId));
        var member = users.getUserContextForId(String.valueOf(editorId));
        try {
            assertEquals(MfaPolicy.OFF, settings.policy());
            assertFalse(settings.enforces());
            assertFalse(settings.requires(admin));

            settings.update(MfaPolicy.OPTIONAL, adminId);
            assertEquals(MfaPolicy.OPTIONAL, settings.policy());
            assertTrue(settings.enforces());
            assertFalse(settings.requires(admin));
            assertFalse(settings.requires(member));

            settings.update(MfaPolicy.REQUIRED_FOR_ADMINS, adminId);
            assertTrue(settings.requires(admin));
            assertFalse(settings.requires(member));

            settings.update(MfaPolicy.REQUIRED, adminId);
            assertTrue(settings.requires(admin));
            assertTrue(settings.requires(member));
            assertEquals(adminId, jdbc.queryForObject("SELECT changed_by FROM app_setting WHERE name='mfa.policy'", Integer.class));

            jdbc.update("UPDATE app_setting SET value='nonsense' WHERE name='mfa.policy'");
            assertEquals(MfaPolicy.OPTIONAL, settings.policy(), "a corrupted setting neither locks out nor disables tokens");
        } finally {
            jdbc.update("DELETE FROM app_setting WHERE name='mfa.policy'");
        }
    }
}
