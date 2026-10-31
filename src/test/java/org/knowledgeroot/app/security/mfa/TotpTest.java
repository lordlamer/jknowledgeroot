package org.knowledgeroot.app.security.mfa;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TotpTest {
    /** RFC 6238 appendix B secret (SHA1): ASCII "12345678901234567890". */
    private static final TotpSecret RFC_SECRET =
            TotpSecret.of(Base32.encode("12345678901234567890".getBytes(StandardCharsets.US_ASCII)));

    @Test void rfc6238TestVectorsSixDigits() {
        // The RFC lists eight digits; the six-digit code is the last six of them.
        assertEquals("287082", Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(59))));
        assertEquals("081804", Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1111111109))));
        assertEquals("005924", Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1234567890))));
        assertEquals("279037", Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(2000000000))));
    }

    @Test void verifyAcceptsOneStepOfDriftOnly() {
        Instant now = Instant.ofEpochSecond(1_800_000_000L);
        long step = Totp.step(now);
        assertEquals(step, Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step), now).getAsLong());
        assertEquals(step - 1, Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now).getAsLong());
        assertEquals(step + 1, Totp.verify(RFC_SECRET, "  " + Totp.code(RFC_SECRET, step + 1) + " ", now).getAsLong());
        assertTrue(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 2), now).isEmpty());
        assertTrue(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 2), now).isEmpty());
    }

    @Test void verifyRejectsMalformedInput() {
        Instant now = Instant.now();
        assertTrue(Totp.verify(RFC_SECRET, null, now).isEmpty());
        assertTrue(Totp.verify(RFC_SECRET, "12345", now).isEmpty());
        assertTrue(Totp.verify(RFC_SECRET, "abcdef", now).isEmpty());
        assertTrue(Totp.looksLikeCode("123 456"));
        assertFalse(Totp.looksLikeCode("abcd-efgh"));
    }

    @Test void base32RoundTripAndGeneratedSecrets() {
        TotpSecret secret = TotpSecret.generate();
        assertEquals(32, secret.base32().length());
        assertTrue(secret.base32().matches("[A-Z2-7]+"));
        assertEquals(secret.base32(), Base32.encode(Base32.decode(secret.base32())));
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", RFC_SECRET.base32());
        assertFalse(secret.toString().contains(secret.base32()));
        assertThrows(IllegalArgumentException.class, () -> TotpSecret.of("ABC"));
        assertThrows(IllegalArgumentException.class, () -> TotpSecret.of("not base32 !!"));
    }

    @Test void otpauthUriForAuthenticatorApps() {
        assertEquals("otpauth://totp/Knowledgeroot:anna%40example.org?secret=GEZDGNBVGY3TQOJQ"
                + "&issuer=Knowledgeroot&algorithm=SHA1&digits=6&period=30",
                Totp.otpauthUri("anna@example.org", TotpSecret.of("GEZDGNBVGY3TQOJQ")));
    }

    @Test void recoveryCodesAreUnambiguousAndNormalizable() {
        var codes = RecoveryCodes.generate();
        assertEquals(10, codes.size());
        assertTrue(codes.stream().allMatch(code -> code.matches("[a-hjkmnp-z2-9]{4}-[a-hjkmnp-z2-9]{4}")));
        assertEquals(10, codes.stream().distinct().count());
        assertEquals("abcd-efgh", RecoveryCodes.normalize(" ABCD efgh "));
        assertEquals("", RecoveryCodes.normalize("abc"));
    }

    @Test void qrCodeIsInlineSvg() {
        String svg = QrCode.svg(Totp.otpauthUri("anna", RFC_SECRET));
        assertTrue(svg.startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\""));
        assertTrue(svg.contains("<path fill=\"#000\" d=\"M"));
        assertFalse(svg.contains("<script"));
    }
}
