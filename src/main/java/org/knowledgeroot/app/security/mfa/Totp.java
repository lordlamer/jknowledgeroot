package org.knowledgeroot.app.security.mfa;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * TOTP (RFC 6238) with HMAC-SHA1, six digits and 30-second steps, the parameters common
 * authenticator apps expect. Verification tolerates one step of clock drift either way.
 */
public final class Totp {
    public static final String ISSUER = "Knowledgeroot";
    static final int PERIOD_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int WINDOW = 1;

    private Totp() {}

    public static long step(Instant now) {
        return now.getEpochSecond() / PERIOD_SECONDS;
    }

    public static String code(TotpSecret secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.bytes(), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%0" + DIGITS + "d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 unavailable", e);
        }
    }

    /** Six digits, spaces allowed; anything else is treated as a recovery code by the caller. */
    public static boolean looksLikeCode(String input) {
        return input != null && input.replaceAll("\\s", "").matches("\\d{" + DIGITS + "}");
    }

    /** The matching time step when the code is valid within the drift window. */
    public static OptionalLong verify(TotpSecret secret, String code, Instant now) {
        if (!looksLikeCode(code)) return OptionalLong.empty();
        String normalized = code.replaceAll("\\s", "");
        long current = step(now);
        for (long s = current - WINDOW; s <= current + WINDOW; s++) {
            if (MessageDigest.isEqual(code(secret, s).getBytes(StandardCharsets.US_ASCII),
                    normalized.getBytes(StandardCharsets.US_ASCII))) return OptionalLong.of(s);
        }
        return OptionalLong.empty();
    }

    /** otpauth URI for the QR code and for manual entry in an authenticator app. */
    public static String otpauthUri(String account, TotpSecret secret) {
        return "otpauth://totp/" + encode(ISSUER) + ":" + encode(account) + "?secret=" + secret.base32()
                + "&issuer=" + encode(ISSUER) + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
