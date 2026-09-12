package org.knowledgeroot.app.security.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Versioned password format; new-password rules do not lock out legacy users. */
@Component
public final class PasswordService implements PasswordEncoder {
    public static final String PREFIX = "{pbkdf2-sha256-v1}";
    public static final String DEMO_HASH = "$2$1000$E556111996CF452E$B3EB80BC13DED8B02A2F09E505AED75737D4AF45D9A599623D39CD518771C306";
    private static final PasswordEncoder ENCODER = new Pbkdf2PasswordEncoder("", 16, 600_000,
            Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
    private static final String DUMMY_HASH = ENCODER.encode("dummy-authentication-work");

    public String encodeNewPassword(String password) {
        if (password == null || password.length() < 16 || password.length() > 128 || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain 16-128 characters");
        }
        return encode(password);
    }

    /** Empty/masked update values preserve the password; spaces in real passwords are significant. */
    public String encodeReplacement(String password) {
        return password == null || password.isEmpty() || password.equals("***") ? null : encodeNewPassword(password);
    }

    @Override
    public String encode(CharSequence password) {
        if (password == null || password.length() > 1024) throw new IllegalArgumentException("Invalid password length");
        return PREFIX + ENCODER.encode(password);
    }

    @Override
    public boolean matches(CharSequence password, String hash) {
        if (password == null || password.length() > 1024) return false;
        if (hash != null && hash.startsWith(PREFIX)
                && hash.substring(PREFIX.length()).matches("[0-9a-fA-F]{96}")) {
            return ENCODER.matches(password, hash.substring(PREFIX.length()));
        }
        // Unknown accounts, malformed hashes and fast legacy hashes all do modern hash work.
        ENCODER.matches(password, DUMMY_HASH);
        if (hash == null || !hash.matches("\\$[1-46]\\$[1-9][0-9]{0,5}\\$[0-9A-F]{16}\\$[0-9A-F]{32,128}")) return false;
        try {
            return PasswordHasher.verify(password.toString(), hash);
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            return false;
        }
    }

    @Override
    public boolean upgradeEncoding(String hash) {
        // Preserve the production startup guard until known demo credentials are explicitly changed.
        return hash != null && !hash.startsWith(PREFIX) && !DEMO_HASH.equals(hash);
    }

    /** A session change marker, not the stored password verifier. */
    public static String credentialTag(String hash) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((hash == null ? "" : hash).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
