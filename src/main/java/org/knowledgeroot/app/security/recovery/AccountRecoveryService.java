package org.knowledgeroot.app.security.recovery;

import org.knowledgeroot.app.security.auth.CredentialRepository;
import org.knowledgeroot.app.security.auth.LoginAttemptLimiter;
import org.knowledgeroot.app.security.auth.PasswordService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;

@Service
public class AccountRecoveryService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final RecoverySettings settings;
    private final RecoveryDelivery delivery;
    private final LoginAttemptLimiter limiter;
    private final PasswordService passwords;
    private final CredentialRepository credentials;
    private final org.knowledgeroot.app.i18n.UiMessages messages;

    public AccountRecoveryService(JdbcTemplate jdbc, PlatformTransactionManager manager,
            RecoverySettings settings, RecoveryDelivery delivery, LoginAttemptLimiter limiter,
            PasswordService passwords, CredentialRepository credentials, org.knowledgeroot.app.i18n.UiMessages messages) {
        this.jdbc = jdbc; this.settings = settings; this.delivery = delivery;
        this.limiter = limiter; this.passwords = passwords; this.credentials = credentials; this.messages = messages;
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public void requireEnabled() {
        if (!settings.isEnabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    public void requestReset(String address, String remote) {
        requireEnabled();
        String email = RecoverySettings.email(address);
        if (email == null || !reserve("request", email, remote)) return;
        java.util.Locale requested = org.springframework.context.i18n.LocaleContextHolder.getLocale();
        // Both known and unknown addresses take this same bounded asynchronous path.
        delivery.submit(() -> {
            var ids = jdbc.queryForList("""
                    SELECT u.id FROM user u JOIN recovery_email e ON e.user_id=u.id
                    WHERE e.email=? AND BINARY LOWER(TRIM(u.email))=? AND u.active=1 AND u.deleted=0
                    """, Integer.class, email, email);
            if (ids.size() == 1) issue(ids.getFirst(), "reset", email, null, requested);
        });
    }

    public void requestVerification(int id, String currentPassword, String remote) {
        requireEnabled();
        if (!reserve("verify-request", Integer.toString(id), remote)) invalid();
        var credential = credentials.findById(Integer.toString(id)).orElseThrow(AccountRecoveryService::invalidLink);
        if (!passwords.matches(currentPassword, credential.hash()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Check your current password and try again.");
        String email = RecoverySettings.email(jdbc.queryForObject("SELECT email FROM user WHERE id=?", String.class, id));
        if (email == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Save a valid email address in your profile first.");
        String tag = PasswordService.credentialTag(credential.hash());
        java.util.Locale requested = org.springframework.context.i18n.LocaleContextHolder.getLocale();
        delivery.submit(() -> issue(id, "verify", email, tag, requested));
    }

    /** The recipient's chosen language, otherwise the language of the request that caused the mail. */
    private java.util.Locale mailLocale(int id, java.util.Locale requested) {
        String stored = jdbc.query("SELECT language FROM user WHERE id=?", (rs, row) -> rs.getString(1), id)
                .stream().findFirst().orElse(null);
        return org.knowledgeroot.app.i18n.Languages.find(stored)
                .map(org.knowledgeroot.app.i18n.Languages.Language::locale)
                .orElse(requested == null ? org.knowledgeroot.app.i18n.Languages.DEFAULT.locale() : requested);
    }

    private void issue(int id, String purpose, String email, String expectedTag, java.util.Locale requested) {
        byte[] entropy = new byte[32]; RANDOM.nextBytes(entropy);
        String raw = HexFormat.of().formatHex(entropy);
        String hash = PasswordService.credentialTag(raw);
        boolean created = Boolean.TRUE.equals(transaction.execute(status -> {
            var account = account(id);
            if (account == null || !email.equals(RecoverySettings.email(account.email()))
                    || (expectedTag != null && !expectedTag.equals(PasswordService.credentialTag(account.password())))
                    || !uniqueAddress(email)) return false;
            if (purpose.equals("reset") && !verified(id, email)) return false;
            jdbc.update("DELETE FROM recovery_token WHERE user_id=? AND purpose=?", id, purpose);
            jdbc.update("""
                    INSERT INTO recovery_token(token_hash,user_id,purpose,email,credential_tag,expires_at)
                    VALUES (?,?,?,?,?,TIMESTAMPADD(MINUTE,30,CURRENT_TIMESTAMP(3)))
                    """, hash, id, purpose, email, PasswordService.credentialTag(account.password()));
            return true;
        }));
        if (!created) return;
        try {
            String path = purpose.equals("reset") ? "/account/reset-password" : "/account/verify-email";
            java.util.Locale locale = mailLocale(id, requested);
            String action = messages.get(locale, purpose.equals("reset") ? "mail.reset.subject" : "mail.verify.subject");
            delivery.send(email, action, messages.get(locale, "mail.link.body", action,
                    settings.getPublicUrl() + path + "?token=" + raw));
        } catch (RuntimeException ex) {
            jdbc.update("DELETE FROM recovery_token WHERE token_hash=?", hash);
            throw ex;
        }
        // Bounded cleanup outside the user transaction; raw tokens are never persisted.
        jdbc.update("DELETE FROM recovery_token WHERE expires_at<CURRENT_TIMESTAMP(3) LIMIT 100");
    }

    public void reset(String token, String replacement, String confirmation, String remote) {
        resetFromSession(validToken(token) ? PasswordService.credentialTag(token) : null, replacement, confirmation, remote);
    }

    // Package-private: HTTP input must be hashed before reaching this API. JDBC sessions
    // retain only the digest, so a stored value cannot be reused as an emailed bearer link.
    void resetFromSession(String token, String replacement, String confirmation, String remote) {
        requireEnabled();
        checkTokenAttempt(token, "reset", remote);
        if (replacement == null || !replacement.equals(confirmation))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The new passwords do not match.");
        String encoded = passwords.encodeNewPassword(replacement);
        java.util.Locale requested = org.springframework.context.i18n.LocaleContextHolder.getLocale();
        int[] account = new int[1];
        String email = transaction.execute(status -> {
            Token claim = claim(token, "reset");
            account[0] = claim.id();
            if (!verified(claim.id(), claim.email())) invalid();
            jdbc.update("UPDATE user SET password=?,changed_by=id,change_date=NOW() WHERE id=?", encoded, claim.id());
            jdbc.update("DELETE FROM recovery_token WHERE user_id=?", claim.id());
            return claim.email();
        });
        delivery.submit(() -> {
            java.util.Locale locale = mailLocale(account[0], requested);
            delivery.send(email, messages.get(locale, "mail.changed.subject"), messages.get(locale, "mail.changed.body"));
        });
    }

    public void verify(String token, String remote) {
        verifyFromSession(validToken(token) ? PasswordService.credentialTag(token) : null, remote);
    }

    void verifyFromSession(String token, String remote) {
        requireEnabled(); checkTokenAttempt(token, "verify", remote);
        try {
            transaction.executeWithoutResult(status -> {
                Token claim = claim(token, "verify");
                jdbc.update("DELETE FROM recovery_email WHERE user_id=?", claim.id());
                jdbc.update("INSERT INTO recovery_email(user_id,email) VALUES (?,?)", claim.id(), claim.email());
                jdbc.update("DELETE FROM recovery_token WHERE token_hash=?", token);
            });
        } catch (org.springframework.dao.DuplicateKeyException ex) { invalid(); }
    }

    private Token claim(String hash, String purpose) {
        var ids = jdbc.queryForList("SELECT user_id FROM recovery_token WHERE token_hash=?", Integer.class, hash);
        if (ids.size() != 1) throw invalidLink();
        // All issuance/consumption and email edits lock the account before its tokens.
        Account account = account(ids.getFirst());
        if (account == null) throw invalidLink();
        var tokens = jdbc.query("""
                SELECT user_id,email,credential_tag FROM recovery_token
                WHERE token_hash=? AND purpose=? AND expires_at>CURRENT_TIMESTAMP(3) FOR UPDATE
                """, (rs, row) -> new Token(rs.getInt(1), rs.getString(2), rs.getString(3)), hash, purpose);
        if (tokens.size() != 1) throw invalidLink();
        Token token = tokens.getFirst();
        if (!Objects.equals(token.email(), RecoverySettings.email(account.email()))
                || !token.tag().equals(PasswordService.credentialTag(account.password())) || !uniqueAddress(token.email())) invalid();
        return token;
    }

    private Account account(int id) {
        return jdbc.query("SELECT email,password FROM user WHERE id=? AND active=1 AND deleted=0 FOR UPDATE",
                (rs, row) -> new Account(rs.getString(1), rs.getString(2)), id).stream().findFirst().orElse(null);
    }
    private boolean uniqueAddress(String email) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE BINARY LOWER(TRIM(email))=? AND active=1 AND deleted=0",
                Integer.class, email) == 1;
    }
    private boolean verified(int id, String email) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM recovery_email WHERE user_id=? AND email=?", Integer.class, id, email) == 1;
    }
    private void checkTokenAttempt(String token, String purpose, String remote) {
        if (!validToken(token) || !reserve(purpose, token, remote)) invalid();
    }
    public static boolean validToken(String token) { return token != null && token.matches("[0-9a-f]{64}"); }
    private boolean reserve(String purpose, String identity, String remote) {
        try { limiter.requireRecoveryAttempt(purpose, identity, remote); return true; }
        catch (BadCredentialsException ex) { return false; }
    }
    private static void invalid() { throw invalidLink(); }
    private static ResponseStatusException invalidLink() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "This link is invalid or expired. Request a new link.");
    }
    private record Account(String email, String password) {
        @Override public String toString() { return "Account[redacted]"; }
    }
    private record Token(int id, String email, String tag) {
        @Override public String toString() { return "Token[redacted]"; }
    }
}
