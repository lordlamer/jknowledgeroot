package org.knowledgeroot.app.security.mfa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * AES-256-GCM for secrets that must be readable again (TOTP secrets) and a keyed hash for
 * one-time codes. Both keys derive from {@code knowledgeroot.security.master-key}; a blank key
 * is refused in production ({@link org.knowledgeroot.app.config.ProductionConfig}) and replaced
 * by a fixed development key elsewhere. Output is {@code iv(12) || ciphertext || tag(16)}.
 */
@Component
@Slf4j
public class SecretCipher {
    static final String PROPERTY = "knowledgeroot.security.master-key";
    private static final String DEVELOPMENT_KEY = "knowledgeroot-development-master-key-not-for-production";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecretKey cipherKey;
    private final SecretKey hmacKey;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public SecretCipher(@Value("${" + PROPERTY + ":}") String masterKey, Environment environment) {
        this(resolve(masterKey, environment));
    }

    public SecretCipher(String masterKey) {
        if (masterKey == null || masterKey.isBlank()) throw new IllegalStateException("Missing " + PROPERTY);
        cipherKey = new SecretKeySpec(derive("cipher", masterKey), "AES");
        hmacKey = new SecretKeySpec(derive("hmac", masterKey), "HmacSHA256");
    }

    private static String resolve(String masterKey, Environment environment) {
        if (masterKey != null && !masterKey.isBlank()) return masterKey;
        if (environment.acceptsProfiles(Profiles.of("production")))
            throw new IllegalStateException("Production requires " + PROPERTY + " (KR_MASTER_KEY)");
        log.warn("KR_MASTER_KEY is not set; second-factor secrets use a fixed development key");
        return DEVELOPMENT_KEY;
    }

    private static byte[] derive(String purpose, String masterKey) {
        try {
            return MessageDigest.getInstance("SHA-256").digest((purpose + ":" + masterKey).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public byte[] encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, cipherKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, IV_BYTES);
            System.arraycopy(ciphertext, 0, out, IV_BYTES, ciphertext.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /** @throws IllegalStateException when the blob was written with a different master key */
    public String decrypt(byte[] blob) {
        if (blob == null || blob.length < IV_BYTES + TAG_BITS / 8) throw new IllegalArgumentException("Encrypted value is too short");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, cipherKey, new GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES));
            return new String(cipher.doFinal(blob, IV_BYTES, blob.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed; was KR_MASTER_KEY changed?", e);
        }
    }

    /** Keyed SHA-256 (hex) for short one-time codes; useless without the master key. */
    public String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
